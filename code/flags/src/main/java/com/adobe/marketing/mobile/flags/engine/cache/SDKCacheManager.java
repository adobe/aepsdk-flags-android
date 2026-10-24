/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.flags.engine.cache;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.EdgeResponse;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.models.FlagApiResponse;
import com.adobe.marketing.mobile.flags.engine.models.MetadataResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages caching and polling of feature data for the configured client. */
public class SDKCacheManager {

    /** Listener invoked when cached metadata changes ({@code contextVersion} gate). */
    @FunctionalInterface
    public interface MetadataUpdatedListener {
        void onMetadataUpdated(MetadataResponse metadata);
    }

    private static final Logger logger = LoggerFactory.getLogger(SDKCacheManager.class);

    private final SDKClientCache clientCache;
    private final PolicyCache policyCache;
    private APIProxy apiProxy;

    private String clientId;
    private volatile ResponseHeaders responseHeaders;
    private ScheduledFuture<?> scheduledFuture;

    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean initialized;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile MetadataUpdatedListener onMetadataUpdated;

    private static class ResponseHeaders {
        private final Integer pollInterval;

        private ResponseHeaders(Integer pollInterval) {
            this.pollInterval = pollInterval;
        }

        private Integer getPollInterval() {
            return pollInterval;
        }
    }

    public SDKCacheManager(PolicyCache policyCache) {
        this.clientCache = new SDKClientCache();
        this.policyCache = policyCache;
        this.initialized = new AtomicBoolean(false);
        this.scheduledExecutorService =
                Executors.newScheduledThreadPool(
                        1,
                        r -> {
                            Thread t = new Thread(r);
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Register a callback invoked when metadata changes ({@code contextVersion} gate).
     *
     * @param listener callback, or {@code null} to clear
     */
    public void setOnMetadataUpdated(MetadataUpdatedListener listener) {
        this.onMetadataUpdated = listener;
    }

    /**
     * Configure and initialize the cache manager for a single client.
     *
     * @param clientId Client ID to cache
     * @param apiProxy API proxy for fetching data
     * @throws FlagInitException if initialization fails
     */
    public void configure(String clientId, APIProxy apiProxy) throws FlagInitException {
        this.clientId = clientId;
        this.apiProxy = apiProxy;

        scheduledExecutorService.execute(
                () -> Thread.currentThread().setName("Flags-CacheManager-" + clientId));

        try {
            initializeClient();
            startPolling();
            initialized.set(true);
            logger.debug("[CacheManager] Initialized for client: {}", clientId);
        } catch (FlagClientException | RuntimeException e) {
            shutdown();
            throw new FlagInitException("Failed to initialize cache manager", e);
        }
    }

    /** Initialize cache for the client with retry mechanism. */
    private void initializeClient() throws FlagClientException {
        if (clientId == null || clientId.isEmpty()) {
            throw new FlagClientException("Client ID cannot be null or empty");
        }

        boolean success = retryWithBackoff(Constants.DEFAULT_MAX_RETRY_ATTEMPTS);

        if (!success) {
            throw new FlagClientException("Failed to initialize client: " + clientId);
        }

        logger.debug("[CacheManager] Client {} initialized", clientId);
    }

    /**
     * Retry fetching data with exponential backoff.
     *
     * @param maxRetries Maximum retry attempts
     * @return true if successful
     */
    private boolean retryWithBackoff(int maxRetries) {
        int attempt = 0;
        long delay = Constants.DEFAULT_RETRY_DELAY_MS;

        while (attempt < maxRetries) {
            try {
                return updateClientCache();

            } catch (Exception e) {
                logger.warn(
                        "[CacheManager] Attempt {} failed for client {}: {}",
                        attempt + 1,
                        clientId,
                        e.getMessage());
            }

            attempt++;
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, Constants.MAX_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        logger.error("[CacheManager] All {} retries failed for client {}", maxRetries, clientId);
        return false;
    }

    private boolean updateClientCache() throws FlagClientException {
        try {
            String contextVersion = null;
            SDKClientCache.MetadataCacheEntry metadataEntry = clientCache.getMetadata();
            if (metadataEntry != null) {
                contextVersion = metadataEntry.getContextVersion();
            }

            String etag = clientCache.getFeaturesEtag(clientId);
            FlagApiResponse response = apiProxy.getFeatures(clientId, contextVersion, etag);

            if (!response.isChanged()) {
                return true;
            }

            Integer serverInterval = response.getPollInterval();
            this.responseHeaders =
                    new ResponseHeaders(
                            serverInterval != null
                                    ? serverInterval
                                    : getDefaultPollIntervalSeconds());

            FeaturesResponse[] featureGroups = response.getFeaturesResponses();
            if (featureGroups != null) {
                clientCache.putFeatures(clientId, featureGroups, response.getEtag());
                cachePolicies(featureGroups);
            }

            EdgeResponse edgeResponse = response.getEdgeResponse();
            if (edgeResponse != null) {
                MetadataResponse metadata =
                        edgeResponse.toMetadataResponse(response.getEtag(), true);
                if (clientCache.putMetadata(metadata)) {
                    MetadataUpdatedListener listener = onMetadataUpdated;
                    if (listener != null) {
                        listener.onMetadataUpdated(metadata);
                    }
                }
            }

            return true;

        } catch (Exception e) {
            throw new FlagClientException(
                    "Failed to update client cache for client " + clientId, e);
        }
    }

    /** Start polling for cache updates. */
    private void startPolling() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        long pollIntervalSeconds = getDefaultPollIntervalSeconds();
        if (responseHeaders != null && responseHeaders.getPollInterval() != null) {
            pollIntervalSeconds = responseHeaders.getPollInterval();
        }

        scheduledFuture =
                scheduledExecutorService.scheduleWithFixedDelay(
                        new CacheUpdateTask(),
                        pollIntervalSeconds,
                        pollIntervalSeconds,
                        TimeUnit.SECONDS);

        logger.debug(
                "[CacheManager] Polling started with interval {} seconds", pollIntervalSeconds);
    }

    /** Refresh cache manually. */
    public void refreshClientCache() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.execute(new CacheUpdateTask());
        }
    }

    /**
     * Adjust polling behavior based on the host application's lifecycle state.
     *
     * <p>{@link AppState#BACKGROUND} cancels the polling schedule. {@link AppState#FOREGROUND}
     * triggers an immediate refresh and restarts polling. No-op if not initialized or the state
     * hasn't changed.
     *
     * @param state current application lifecycle state (not null)
     */
    public void onAppStateChanged(AppState state) {
        if (!initialized.get() || state == null) {
            return;
        }
        if (state == AppState.BACKGROUND && paused.compareAndSet(false, true)) {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
        } else if (state == AppState.FOREGROUND && paused.compareAndSet(true, false)) {
            scheduledExecutorService.execute(new ForegroundResumeTask());
        }
    }

    /**
     * Get the client cache.
     *
     * @return SDKClientCache
     */
    public SDKClientCache getClientCache() {
        return clientCache;
    }

    /** Shutdown the cache manager. */
    public void shutdown() {
        try {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
            scheduledExecutorService.shutdown();
            try {
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } finally {
            clientCache.clear();
            policyCache.clear();
            responseHeaders = null;
            initialized.set(false);
            logger.debug("[CacheManager] Shutdown complete");
        }
    }

    private int getDefaultPollIntervalSeconds() {
        return Constants.DEFAULT_POLL_INTERVAL_SECONDS;
    }

    private void cachePolicies(FeaturesResponse[] featureGroups) {
        if (featureGroups == null) {
            return;
        }
        for (FeaturesResponse featureGroup : featureGroups) {
            cachePolicy(featureGroup.getPolicyId(), featureGroup.getPolicy());
            Feature[] features = featureGroup.getFeaturesObj();
            if (features != null) {
                for (Feature feature : features) {
                    cachePolicy(feature.getPolicyId(), feature.getPolicy());
                }
            }
        }
    }

    private void cachePolicy(Integer policyId, PolicyCache.PolicyDetail detail) {
        if (policyId != null && detail != null) {
            policyCache.putPolicy(policyId, detail);
        }
    }

    /**
     * Cache update task for polling.
     *
     * <p>Exceptions must not propagate out of {@link #run()}; an uncaught failure would suppress
     * all future executions of {@code scheduleWithFixedDelay}.
     */
    private class CacheUpdateTask implements Runnable {
        @Override
        public void run() {
            try {
                Integer oldInterval =
                        responseHeaders != null ? responseHeaders.getPollInterval() : null;
                updateClientCache();
                Integer newInterval =
                        responseHeaders != null ? responseHeaders.getPollInterval() : null;
                if (newInterval != null && !newInterval.equals(oldInterval) && !paused.get()) {
                    startPolling();
                }
            } catch (Exception e) {
                logger.error("[CacheManager] Polling failed for client: {}", clientId, e);
            }
        }
    }

    /**
     * Performs an immediate cache refresh on foreground resume, then restarts the polling schedule.
     * Running both steps sequentially inside a single task ensures the first scheduled poll does
     * not overlap with the refresh.
     */
    private class ForegroundResumeTask implements Runnable {
        @Override
        public void run() {
            try {
                updateClientCache();
            } catch (Exception e) {
                logger.error(
                        "[CacheManager] Foreground refresh failed for client: {}", clientId, e);
            }
            startPolling();
        }
    }
}
