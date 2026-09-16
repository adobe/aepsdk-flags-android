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

package com.adobe.marketing.mobile.flags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolver;
import com.adobe.marketing.mobile.flags.engine.FlagClient;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.internal.FlagConfigurationProvider;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagIdentityFetcher;
import com.adobe.marketing.mobile.flags.internal.GetFeatureRequestFactory;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the feature evaluation SDK client lifecycle and request processing.
 *
 * <p>Owns client initialization, shutdown, app state forwarding, batched exposure dispatch, and
 * routing of API requests to the underlying SDK.
 */
final class FlagClientManager {

    private static final String SELF_TAG = "FlagClientManager";
    private static final long EXPOSURE_QUEUE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ExtensionApi extensionApi;

    private final FlagIdentityFetcher identityFetcher;

    private volatile boolean flagClientInitialized = false;
    private volatile boolean initializationInProgress = false;

    private volatile FlagClient flagClient;

    private final ExecutorService initExecutor;
    private final FeatureExposureQueue exposureQueue;

    FlagClientManager(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final FlagIdentityFetcher identityFetcher) {
        this.extensionApi = extensionApi;
        this.identityFetcher = identityFetcher;
        this.initExecutor = createDefaultInitExecutor();
        this.exposureQueue =
                new FeatureExposureQueue(
                        events -> FlagEdgeHandler.dispatchExposureEvents(extensionApi, events));
    }

    private static ExecutorService createDefaultInitExecutor() {
        return Executors.newSingleThreadExecutor(
                r -> {
                    Thread t = new Thread(r, "FlagClient-init");
                    t.setDaemon(true);
                    return t;
                });
    }

    boolean isClientReady() {
        final com.adobe.marketing.mobile.flags.engine.FlagClient client = flagClient;
        return flagClientInitialized && client != null && client.isInitialized();
    }

    boolean isInitializationInProgress() {
        return initializationInProgress;
    }

    void startAsyncInitialization(
            @NonNull final Map<String, Object> configData,
            @NonNull final SharedStateResolver resolver) {
        if (initializationInProgress || isClientReady()) {
            return;
        }

        final com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration config =
                FlagConfigurationProvider.buildConfiguration(configData);
        if (config == null) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "startAsyncInitialization - Failed to build SDK configuration.");
            resolver.resolve(buildInitializationStatusMap(FlagConstants.SharedState.STATUS_FAILED));
            return;
        }

        initializationInProgress = true;

        initExecutor.submit(
                () -> {
                    identityFetcher.warmUp();
                    try {
                        flagClient =
                                com.adobe.marketing.mobile.flags.engine.FlagClient.create(config);
                        flagClientInitialized = true;
                        Log.debug(
                                FlagConstants.LOG_TAG,
                                SELF_TAG,
                                "Feature SDK client initialized successfully.");
                        resolver.resolve(
                                buildInitializationStatusMap(
                                        FlagConstants.SharedState.STATUS_READY));
                    } catch (FlagInitException e) {
                        Log.warning(
                                FlagConstants.LOG_TAG,
                                SELF_TAG,
                                "Feature SDK client initialization failed: %s",
                                e.getLocalizedMessage());
                        resolver.resolve(
                                buildInitializationStatusMap(
                                        FlagConstants.SharedState.STATUS_FAILED));
                    } catch (Exception e) {
                        Log.warning(
                                FlagConstants.LOG_TAG,
                                SELF_TAG,
                                "Feature SDK client initialization unexpected error: %s",
                                e.getLocalizedMessage());
                        resolver.resolve(
                                buildInitializationStatusMap(
                                        FlagConstants.SharedState.STATUS_FAILED));
                    } finally {
                        initializationInProgress = false;
                    }
                });
    }

    void closeClient() {
        exposureQueue.shutdown();
        try {
            if (!exposureQueue.awaitShutdown(
                    EXPOSURE_QUEUE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "closeClient - Exposure queue did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "closeClient - Interrupted while waiting for exposure queue shutdown.");
        }

        final com.adobe.marketing.mobile.flags.engine.FlagClient client = flagClient;
        if (client != null) {
            client.close();
            flagClient = null;
            flagClientInitialized = false;
        }
        initExecutor.shutdownNow();
    }

    void processRequestEvent(@NonNull final Event event) {
        final Map<String, Object> eventData = event.getEventData();
        final String requestType =
                DataReader.optString(eventData, FlagConstants.EventDataKeys.REQUEST_TYPE, "");

        switch (requestType) {
            case FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE:
                handleGetFeature(event);
                break;
            case FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED:
                handleIsFeatureEnabled(event);
                break;
            default:
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "processRequestEvent - Unknown request type: %s",
                        requestType);
                break;
        }
    }

    void handleAppStateChange(@NonNull final AppState appState) {
        if (isClientReady()) {
            try {
                final com.adobe.marketing.mobile.flags.engine.FlagClient client = flagClient;
                if (client != null) {
                    client.setAppState(appState);
                }
            } catch (FlagClientException e) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleAppStateChange - Failed: %s",
                        e.getLocalizedMessage());
            }
        }

        if (appState == AppState.BACKGROUND) {
            exposureQueue.pausePeriodicFlush();
            exposureQueue.flush();
        } else if (appState == AppState.FOREGROUND) {
            exposureQueue.resumePeriodicFlush();
        }
    }

    private void handleGetFeature(@NonNull final Event event) {
        try {
            final Map<String, Object> eventData = event.getEventData();
            final String featureName =
                    DataReader.optString(eventData, FlagConstants.EventDataKeys.FEATURE_NAME, null);

            if (featureName == null || featureName.isEmpty()) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleGetFeature - Feature name is missing.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            final com.adobe.marketing.mobile.flags.engine.FlagClient client = flagClient;
            if (client == null) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleGetFeature - Feature SDK client instance is null.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            final String clientId = client.getClientId();
            if (clientId == null || clientId.isEmpty()) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleGetFeature - Client ID is missing.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            @SuppressWarnings("unchecked")
            final Map<String, List<String>> context =
                    (Map<String, List<String>>) eventData.get(FlagConstants.EventDataKeys.CONTEXT);

            final GetFeatureRequest request =
                    GetFeatureRequestFactory.create(
                            context, identityFetcher.fetchIdentityMap(extensionApi, event));

            final FeatureResult feature = client.getFeature(featureName, request);

            final Map<String, Object> responseData = new HashMap<>();
            if (feature != null) {
                responseData.put(FlagConstants.EventDataKeys.FEATURE, featureResultToMap(feature));
            }

            final Event responseEvent =
                    new Event.Builder(
                                    FlagConstants.EventNames.FLAGS_RESPONSE,
                                    FlagConstants.EventType.FLAGS,
                                    FlagConstants.EventSource.RESPONSE_CONTENT)
                            .setEventData(responseData)
                            .inResponseToEvent(event)
                            .build();

            extensionApi.dispatch(responseEvent);
            queueExposureEvent(feature);

        } catch (FlagClientException e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "handleGetFeature - Failed: %s",
                    e.getLocalizedMessage());
            dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
        } catch (Exception e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "handleGetFeature - Unexpected error: %s",
                    e.getLocalizedMessage());
            dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
        }
    }

    private void handleIsFeatureEnabled(@NonNull final Event event) {
        try {
            final Map<String, Object> eventData = event.getEventData();
            final String featureName =
                    DataReader.optString(eventData, FlagConstants.EventDataKeys.FEATURE_NAME, null);

            if (featureName == null || featureName.isEmpty()) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleIsFeatureEnabled - Feature name is missing.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            final com.adobe.marketing.mobile.flags.engine.FlagClient client = flagClient;
            if (client == null) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleIsFeatureEnabled - Feature SDK client instance is null.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            final String clientId = client.getClientId();
            if (clientId == null || clientId.isEmpty()) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "handleIsFeatureEnabled - Client ID is missing.");
                dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
                return;
            }

            @SuppressWarnings("unchecked")
            final Map<String, List<String>> context =
                    (Map<String, List<String>>) eventData.get(FlagConstants.EventDataKeys.CONTEXT);

            final GetFeatureRequest request =
                    GetFeatureRequestFactory.create(
                            context, identityFetcher.fetchIdentityMap(extensionApi, event));

            final FeatureResult feature = client.getFeature(featureName, request);
            final boolean isEnabled = isEnabledFeatureResult(feature);

            final Map<String, Object> responseData = new HashMap<>();
            responseData.put(FlagConstants.EventDataKeys.IS_ENABLED, isEnabled);

            final Event responseEvent =
                    new Event.Builder(
                                    FlagConstants.EventNames.FLAGS_RESPONSE,
                                    FlagConstants.EventType.FLAGS,
                                    FlagConstants.EventSource.RESPONSE_CONTENT)
                            .setEventData(responseData)
                            .inResponseToEvent(event)
                            .build();

            extensionApi.dispatch(responseEvent);
            queueExposureEvent(feature);

        } catch (FlagClientException e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "handleIsFeatureEnabled - Failed: %s",
                    e.getLocalizedMessage());
            dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
        } catch (Exception e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "handleIsFeatureEnabled - Unexpected error: %s",
                    e.getLocalizedMessage());
            dispatchErrorResponseInternal(event, AdobeError.UNEXPECTED_ERROR);
        }
    }

    /**
     * Queues an exposure event for batched Edge dispatch. Profile identity is merged by Edge + Edge
     * Identity when the Edge extension processes the dispatched event.
     */
    private void queueExposureEvent(@Nullable final FeatureResult feature) {
        if (!ExposureEventIdGenerator.isExposureEligible(feature)) {
            return;
        }

        final String aggregationKey = ExposureEventIdGenerator.generateAggregationKey(feature);
        if (aggregationKey == null || aggregationKey.isEmpty()) {
            return;
        }

        exposureQueue.enqueue(aggregationKey, feature, System.currentTimeMillis());
    }

    private static boolean isEnabledFeatureResult(@Nullable final FeatureResult feature) {
        return feature != null && feature.getKey() != null && !feature.getKey().isEmpty();
    }

    private Map<String, Object> featureResultToMap(@NonNull final FeatureResult feature) {
        final Map<String, Object> map = new HashMap<>();
        map.put(FlagConstants.EventDataKeys.ID, feature.getId());
        map.put(FlagConstants.EventDataKeys.KEY, feature.getKey());
        if (feature.getFeatureGroupKey() != null) {
            map.put(FlagConstants.EventDataKeys.FEATURE_GROUP_KEY, feature.getFeatureGroupKey());
        }
        final String meta = feature.getMeta();
        if (meta != null && !meta.trim().isEmpty()) {
            map.put(FlagConstants.EventDataKeys.META, meta);
        }
        final com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam analytics =
                feature.getAnalyticsParam();
        if (analytics != null) {
            final Map<String, Object> analyticsMap = new HashMap<>();
            analyticsMap.put(
                    FlagConstants.EventDataKeys.FEATURE_GROUP_ID, analytics.getFeatureGroupId());
            analyticsMap.put(FlagConstants.EventDataKeys.FEATURE_ID, analytics.getFeatureId());
            if (analytics.getVariantId() != null) {
                analyticsMap.put(FlagConstants.EventDataKeys.VARIANT_ID, analytics.getVariantId());
            }
            map.put(FlagConstants.EventDataKeys.ANALYTICS_PARAM, analyticsMap);
        }
        return map;
    }

    @NonNull private static Map<String, Object> buildInitializationStatusMap(@NonNull final String status) {
        final Map<String, Object> map = new HashMap<>(1);
        map.put(FlagConstants.SharedState.INITIALIZATION_STATUS, status);
        return map;
    }

    private void dispatchErrorResponseInternal(
            @NonNull final Event event, @NonNull final AdobeError error) {
        final Map<String, Object> responseData = new HashMap<>();
        responseData.put(FlagConstants.EventDataKeys.RESPONSE_ERROR, error.getErrorCode());

        final Event responseEvent =
                new Event.Builder(
                                FlagConstants.EventNames.FLAGS_RESPONSE,
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.RESPONSE_CONTENT)
                        .setEventData(responseData)
                        .inResponseToEvent(event)
                        .build();

        extensionApi.dispatch(responseEvent);
    }

    void dispatchErrorResponse(@NonNull final Event event, @NonNull final AdobeError error) {
        dispatchErrorResponseInternal(event, error);
    }
}
