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

package com.adobe.marketing.mobile.flags.engine;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.cache.SDKCacheManager;
import com.adobe.marketing.mobile.flags.engine.cache.SDKClientCache;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.feature.FeatureEvaluator;
import com.adobe.marketing.mobile.flags.engine.internal.FeatureServiceUrls;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.runtime.FilterService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flags Android SDK client for feature evaluation.
 *
 * <p>Thread-safe. Implements {@link AutoCloseable} for resource cleanup.
 *
 * <pre>{@code
 * FlagConfiguration config = FlagConfiguration.builder()
 *     .edgeDomain("your-edge-domain.example.com")
 *     .imsOrg("my-org-id")
 *     .sandboxName("prod")
 *     .clientId("my-app")
 *     .build();
 *
 * FlagClient client = FlagClient.create(config);
 *
 * GetFeatureRequest request = new GetFeatureRequest.Builder()
 *     .context(Map.of("userId", List.of("user123"), "country", List.of("US")))
 *     .identityMap(Map.of("MyNamespace", List.of(Map.of(
 *         "id", "user-bucket-id-123",
 *         "primary", true,
 *         "authenticatedState", "ambiguous"))))
 *     .build();
 *
 * FeatureResult[] features = client.getFeatures(request);
 * FeatureResult feature = client.getFeature("dark-mode", request);
 * boolean enabled = client.isFeatureEnabled("dark-mode", request);
 *
 * client.close();
 * }</pre>
 */
public final class FlagClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FlagClient.class);
    public static final String URL_PATH_SEPARATOR = "/";

    private final FlagConfiguration configuration;
    private final String clientId;
    private final APIProxy apiProxy;
    private final SDKCacheManager cacheManager;
    private final SDKClientCache cache;
    private final FeatureEvaluator evaluator;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FlagClient(FlagConfiguration configuration) {
        this.configuration = configuration;
        this.clientId = configuration.getClientId();

        this.apiProxy =
                new APIProxy(
                        FeatureServiceUrls.baseUrlFromEdgeDomain(configuration.getEdgeDomain()),
                        configuration.getImsOrg(),
                        configuration.getSandboxName(),
                        configuration.getHttpClient());

        PolicyCache policyCache = new PolicyCache();
        this.cacheManager = new SDKCacheManager(policyCache);
        this.cache = cacheManager.getClientCache();
        this.evaluator = new FeatureEvaluator(clientId, cache, policyCache);

        logger.debug("[Flags] SDK instance created for client: {}", clientId);
    }

    /**
     * Create and initialize a client.
     *
     * <p>Organization and sandbox identifiers are consumed during initialization and are not
     * accessible after construction.
     *
     * @param configuration SDK configuration (not null)
     * @return initialized client
     * @throws FlagInitException if initialization fails
     */
    public static FlagClient create(FlagConfiguration configuration) throws FlagInitException {
        if (configuration == null) {
            throw new FlagInitException("Configuration is required");
        }
        FlagClient client = new FlagClient(configuration);
        try {
            client.initialize();
            return client;
        } catch (Exception e) {
            client.close();
            throw new FlagInitException("Failed to initialize FlagClient", e);
        }
    }

    /**
     * Check if this client instance is initialized and ready.
     *
     * @return {@code true} if initialized and not yet closed
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Release all resources held by this client. Safe to call multiple times; subsequent calls are
     * no-ops.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            initialized.set(false);
            shutdown();
            logger.debug("[Flags] Client closed for: {}", clientId);
        }
    }

    /**
     * Returns all evaluated features.
     *
     * @param request evaluation context and parameters, or {@code null} for defaults
     * @return Array of evaluated features (never null)
     * @throws FlagClientException if operation fails
     */
    public FeatureResult[] getFeatures(GetFeatureRequest request) throws FlagClientException {
        ensureInitialized();
        if (request == null) {
            request = GetFeatureRequest.DEFAULT;
        }
        return evaluator.evaluateAll(request);
    }

    /**
     * Returns evaluation result for a single feature.
     *
     * @param featureName feature name to look up
     * @param request evaluation context and parameters, or {@code null} for defaults
     * @return the matching result, or {@code null} if not found
     * @throws FlagClientException if operation fails
     */
    public FeatureResult getFeature(String featureName, GetFeatureRequest request)
            throws FlagClientException {
        ensureInitialized();
        if (request == null) {
            request = GetFeatureRequest.DEFAULT;
        }
        return evaluator.evaluate(featureName, request);
    }

    /**
     * Returns whether the named feature is enabled (treatment / non-control cohort for feature
     * policy).
     *
     * <p>Returns {@code false} for unknown features and for feature-level A/B control cohort rows
     * ({@link FeatureResult} with {@code null} key from evaluation).
     *
     * @param featureName feature name to check
     * @param request evaluation context and parameters, or {@code null} for defaults
     * @return {@code true} if the feature is enabled; {@code false} otherwise
     * @throws FlagClientException if operation fails
     */
    public boolean isFeatureEnabled(String featureName, GetFeatureRequest request)
            throws FlagClientException {
        ensureInitialized();
        if (request == null) {
            request = GetFeatureRequest.DEFAULT;
        }
        return evaluator.isEnabled(featureName, request);
    }

    /**
     * Refresh the feature cache.
     *
     * @throws FlagClientException if operation fails
     */
    public void refreshCache() throws FlagClientException {
        ensureInitialized();
        cacheManager.refreshClientCache();
        logger.debug("[Flags] Cache refresh triggered for client: {}", clientId);
    }

    /**
     * Notify the SDK of the host application's lifecycle state.
     *
     * <p>{@link AppState#BACKGROUND} pauses polling and evicts idle HTTP connections to conserve
     * battery, data, and sockets. Evaluation continues against the last cached snapshot.
     *
     * <p>{@link AppState#FOREGROUND} triggers an immediate cache refresh and restarts the polling
     * schedule.
     *
     * <p>Safe to call repeatedly with the same state; redundant transitions are no-ops.
     *
     * @param state current application lifecycle state (not null)
     * @throws FlagClientException if {@code state} is null, or the client is not initialized or has
     *     been closed
     */
    public void setAppState(AppState state) throws FlagClientException {
        if (state == null) {
            throw new FlagClientException("AppState must not be null");
        }
        ensureInitialized();
        cacheManager.onAppStateChanged(state);
        if (state == AppState.BACKGROUND) {
            apiProxy.evictIdleConnections();
        }
    }

    /**
     * @return the configuration this client was created with
     */
    public FlagConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @return the client ID this instance is configured for
     */
    public String getClientId() {
        return clientId;
    }

    private void initialize() throws FlagInitException {
        cacheManager.setOnMetadataUpdated(
                metadataResponse ->
                        evaluator.setFilterService(
                                new FilterService(metadataResponse.getFieldDataTypeCache())));
        cacheManager.configure(clientId, apiProxy);

        initialized.set(true);
        logger.debug("[Flags] SDK initialized successfully");
    }

    private void ensureInitialized() throws FlagClientException {
        if (!initialized.get()) {
            throw new FlagClientException("Client is not initialized or has been closed");
        }
    }

    private void shutdown() {
        evaluator.clearFilterService();
        cacheManager.shutdown();
        apiProxy.shutdown();
        logger.debug("[Flags] SDK shutdown complete");
    }
}
