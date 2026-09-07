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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolver;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagIdentityFetcher;
import com.adobe.marketing.mobile.flags.internal.FlagResponseMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class FlagClientManagerTest {

    @Mock private ExtensionApi mockExtensionApi;
    @Mock private com.adobe.marketing.mobile.flags.engine.FlagClient mockFlagClient;
    @Mock private SharedStateResolver mockResolver;
    @Mock private FlagIdentityFetcher mockIdentityFetcher;

    private FlagClientManager clientManager;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        clientManager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi,
                        TestCallerThreadExecutorService.INSTANCE,
                        mockIdentityFetcher);
    }

    @After
    public void tearDown() {
        clientManager.closeClient();
    }

    private void initializeWithClient() {
        FlagTestFixtures.initializeClientManager(clientManager, mockFlagClient);
    }

    private void initializeManagerWithClient(final FlagClientManager manager) {
        FlagTestFixtures.initializeClientManager(manager, mockFlagClient);
    }

    private void flushQueuedExposure() {
        clientManager.handleAppStateChange(AppState.BACKGROUND);
    }

    private void flushQueuedExposure(final FlagClientManager manager) {
        manager.handleAppStateChange(AppState.BACKGROUND);
    }

    private Event buildRequestEvent(final String requestType, final String featureName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(FlagConstants.EventDataKeys.REQUEST_TYPE, requestType);
        if (featureName != null) {
            eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureName);
        }
        return new Event.Builder(
                        "Test Event",
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT)
                .setEventData(eventData)
                .build();
    }

    private static Map<String, Object> validConfigData() {
        return FlagTestFixtures.validConfigData();
    }

    private static FeatureResult featureResultWithAnalytics(
            final int featureGroupId,
            final String featureGroupKey,
            final int featureId,
            final String featureKey,
            final String variantId) {
        final AnalyticsParam analytics =
                new AnalyticsParam(featureGroupId, featureId, featureKey, variantId);
        return new FeatureResult(featureId, featureKey, featureGroupKey, null, null, analytics);
    }

    @SuppressWarnings("unchecked")
    private static boolean isPropositionDisplayEdgeEvent(final Event event) {
        return ExposureTestAssertions.isPropositionDisplayEdgeEvent(event);
    }

    @Test
    public void startAsyncInitialization_success_resolvesReady() throws FlagInitException {
        try (MockedStatic<com.adobe.marketing.mobile.flags.engine.FlagClient> flagClientStatic =
                mockStatic(com.adobe.marketing.mobile.flags.engine.FlagClient.class)) {
            flagClientStatic
                    .when(
                            () ->
                                    com.adobe.marketing.mobile.flags.engine.FlagClient.create(
                                            any(
                                                    com.adobe.marketing.mobile.flags.engine.models
                                                            .FlagConfiguration.class)))
                    .thenReturn(mockFlagClient);
            when(mockFlagClient.isInitialized()).thenReturn(true);

            clientManager.startAsyncInitialization(validConfigData(), mockResolver);

            verify(mockResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_READY.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));
        }
    }

    @Test
    public void startAsyncInitialization_initException_resolvesFailed() {
        try (MockedStatic<com.adobe.marketing.mobile.flags.engine.FlagClient> flagClientStatic =
                mockStatic(com.adobe.marketing.mobile.flags.engine.FlagClient.class)) {
            flagClientStatic
                    .when(
                            () ->
                                    com.adobe.marketing.mobile.flags.engine.FlagClient.create(
                                            any(
                                                    com.adobe.marketing.mobile.flags.engine.models
                                                            .FlagConfiguration.class)))
                    .thenThrow(new FlagInitException("init failed"));

            clientManager.startAsyncInitialization(validConfigData(), mockResolver);

            verify(mockResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_FAILED.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));
        }
    }

    @Test
    public void startAsyncInitialization_invalidConfig_resolvesFailedSynchronously() {
        Map<String, Object> partialConfig = new HashMap<>();
        partialConfig.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");

        clientManager.startAsyncInitialization(partialConfig, mockResolver);

        verify(mockResolver)
                .resolve(
                        argThat(
                                (Map<String, Object> map) ->
                                        FlagConstants.SharedState.STATUS_FAILED.equals(
                                                map.get(
                                                        FlagConstants.SharedState
                                                                .INITIALIZATION_STATUS))));
    }

    @Test
    public void startAsyncInitialization_whileInProgress_doesNotResolveTwice()
            throws FlagInitException {
        try (MockedStatic<com.adobe.marketing.mobile.flags.engine.FlagClient> flagClientStatic =
                mockStatic(com.adobe.marketing.mobile.flags.engine.FlagClient.class)) {
            flagClientStatic
                    .when(
                            () ->
                                    com.adobe.marketing.mobile.flags.engine.FlagClient.create(
                                            any(
                                                    com.adobe.marketing.mobile.flags.engine.models
                                                            .FlagConfiguration.class)))
                    .thenReturn(mockFlagClient);
            when(mockFlagClient.isInitialized()).thenReturn(true);

            clientManager.startAsyncInitialization(validConfigData(), mockResolver);
            clientManager.startAsyncInitialization(validConfigData(), mockResolver);

            verify(mockResolver, timeout(3000).times(1))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_READY.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));
        }
    }

    @Test
    public void processRequestEvent_getFeature_noClient_dispatchesError() {
        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event ->
                                        event.getEventData() != null
                                                && event.getEventData()
                                                        .containsKey(
                                                                FlagConstants.EventDataKeys
                                                                        .RESPONSE_ERROR)));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> "com.adobe.eventType.edge".equals(event.getType())));
    }

    @Test
    public void processRequestEvent_isEnabled_noClient_dispatchesError() {
        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event ->
                                        event.getEventData() != null
                                                && event.getEventData()
                                                        .containsKey(
                                                                FlagConstants.EventDataKeys
                                                                        .RESPONSE_ERROR)));
    }

    @Test
    public void appStateChange_whenEvaluationClientNotReady_doesNotCallCoreClient()
            throws FlagClientException {
        clientManager.handleAppStateChange(AppState.FOREGROUND);

        verify(mockFlagClient, never()).setAppState(any());
    }

    @Test
    public void appStateChange_foreground_updatesCoreClient() throws FlagClientException {
        initializeWithClient();

        clientManager.handleAppStateChange(AppState.FOREGROUND);

        verify(mockFlagClient).setAppState(AppState.FOREGROUND);
    }

    @Test
    public void appStateChange_background_updatesCoreClient() throws FlagClientException {
        initializeWithClient();

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        verify(mockFlagClient).setAppState(AppState.BACKGROUND);
    }

    @Test
    public void appStateChange_whenCoreClientThrows_doesNotPropagate() throws FlagClientException {
        initializeWithClient();
        doThrow(new FlagClientException("closed")).when(mockFlagClient).setAppState(any());

        clientManager.handleAppStateChange(AppState.FOREGROUND);
    }

    @Test
    public void processRequestEvent_getFeature_passesIdentityMapFromFetcher()
            throws FlagClientException {
        initializeWithClient();
        final Map<String, List<Map<String, Object>>> identityMap =
                Map.of(
                        "ECID",
                        List.of(
                                Map.of(
                                        FlagConstants.Edge.IDENTITY_ID,
                                        "ecid-123",
                                        FlagConstants.Edge.IDENTITY_PRIMARY,
                                        true)));
        when(mockIdentityFetcher.fetchIdentityMap(eq(mockExtensionApi), any(Event.class)))
                .thenReturn(identityMap);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("feature-a"), captor.capture());
        assertEquals(identityMap, captor.getValue().getIdentityMap());
    }

    @Test
    public void processRequestEvent_isFeatureEnabled_passesIdentityMapFromFetcher()
            throws FlagClientException {
        initializeWithClient();
        final Map<String, List<Map<String, Object>>> identityMap =
                Map.of(
                        "ECID",
                        List.of(
                                Map.of(
                                        FlagConstants.Edge.IDENTITY_ID,
                                        "ecid-456",
                                        FlagConstants.Edge.IDENTITY_PRIMARY,
                                        true)));
        when(mockIdentityFetcher.fetchIdentityMap(eq(mockExtensionApi), any(Event.class)))
                .thenReturn(identityMap);
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(
                        featureResultWithAnalytics(23261, "fg-group", 1, "dark-mode", "10283012"));

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "dark-mode"));

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("dark-mode"), captor.capture());
        assertEquals(identityMap, captor.getValue().getIdentityMap());
    }

    @Test
    public void processRequestEvent_getFeature_nullIdentityMap_omitsIdentityFromRequest()
            throws FlagClientException {
        initializeWithClient();
        when(mockIdentityFetcher.fetchIdentityMap(eq(mockExtensionApi), any(Event.class)))
                .thenReturn(null);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("feature-a"), captor.capture());
        assertTrue(captor.getValue().getIdentityMap().isEmpty());
    }

    @Test
    public void processRequestEvent_isFeatureEnabled_nullIdentityMap_stillEvaluates()
            throws FlagClientException {
        initializeWithClient();
        when(mockIdentityFetcher.fetchIdentityMap(eq(mockExtensionApi), any(Event.class)))
                .thenReturn(null);
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "dark-mode"));

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("dark-mode"), captor.capture());
        assertTrue(captor.getValue().getIdentityMap().isEmpty());
        verify(mockExtensionApi).dispatch(any(Event.class));
    }

    @Test
    public void startAsyncInitialization_invokesIdentityFetcherWarmUp() {
        when(mockIdentityFetcher.fetchIdentityMap(any(), any(Event.class))).thenReturn(null);

        try (MockedStatic<com.adobe.marketing.mobile.flags.engine.FlagClient> flagClientStatic =
                mockStatic(com.adobe.marketing.mobile.flags.engine.FlagClient.class)) {
            flagClientStatic
                    .when(
                            () ->
                                    com.adobe.marketing.mobile.flags.engine.FlagClient.create(
                                            any(
                                                    com.adobe.marketing.mobile.flags.engine.models
                                                            .FlagConfiguration.class)))
                    .thenReturn(mockFlagClient);

            clientManager.startAsyncInitialization(validConfigData(), mockResolver);
        }

        verify(mockIdentityFetcher).warmUp();
    }

    @Test
    public void processRequestEvent_getFeature_metaPassThrough_preservesExactStringInFlagsResponse()
            throws FlagClientException {
        initializeWithClient();
        final String metaJson =
                "{\"allowedLocales\":[\"en_US\",\"fr_FR\"],\"variant\":\"control\"}";
        final FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, metaJson, null);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event -> {
                                    if (!FlagConstants.EventType.FLAGS.equals(event.getType())
                                            || !FlagConstants.EventSource.RESPONSE_CONTENT.equals(
                                                    event.getSource())) {
                                        return false;
                                    }
                                    final Map<String, Object> data = event.getEventData();
                                    if (data == null) {
                                        return false;
                                    }
                                    @SuppressWarnings("unchecked")
                                    final Map<String, Object> featureMap =
                                            (Map<String, Object>)
                                                    data.get(FlagConstants.EventDataKeys.FEATURE);
                                    return featureMap != null
                                            && metaJson.equals(
                                                    featureMap.get(
                                                            FlagConstants.EventDataKeys.META));
                                }));
    }

    @Test
    public void processRequestEvent_getFeature_metaPassThrough_plainStringPreserved()
            throws FlagClientException {
        initializeWithClient();
        final String meta = "ZmxhZ19mb3JfdmFyaWFudDFfZmVhdHVyZV9ncm91cA==";
        final FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, meta, null);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event -> {
                                    if (!FlagConstants.EventType.FLAGS.equals(event.getType())
                                            || !FlagConstants.EventSource.RESPONSE_CONTENT.equals(
                                                    event.getSource())) {
                                        return false;
                                    }
                                    @SuppressWarnings("unchecked")
                                    final Map<String, Object> featureMap =
                                            (Map<String, Object>)
                                                    event.getEventData()
                                                            .get(
                                                                    FlagConstants.EventDataKeys
                                                                            .FEATURE);
                                    return featureMap != null
                                            && meta.equals(
                                                    featureMap.get(
                                                            FlagConstants.EventDataKeys.META));
                                }));
    }

    @Test
    public void processRequestEvent_getFeature_nullMeta_omitsMetaFromEvent()
            throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, null, null);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event -> {
                                    if (!FlagConstants.EventType.FLAGS.equals(event.getType())
                                            || !FlagConstants.EventSource.RESPONSE_CONTENT.equals(
                                                    event.getSource())) {
                                        return false;
                                    }
                                    @SuppressWarnings("unchecked")
                                    final Map<String, Object> featureMap =
                                            (Map<String, Object>)
                                                    event.getEventData()
                                                            .get(
                                                                    FlagConstants.EventDataKeys
                                                                            .FEATURE);
                                    return featureMap != null
                                            && !featureMap.containsKey(
                                                    FlagConstants.EventDataKeys.META);
                                }));
    }

    @Test
    public void processRequestEvent_getFeature_metaRoundTripThroughResponseMapper_unchanged()
            throws FlagClientException {
        initializeWithClient();
        final String metaJson = "{\"tags\":[\"a\",\"b\"],\"variant\":\"control\"}";
        final FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, metaJson, null);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event -> {
                                    if (!FlagConstants.EventType.FLAGS.equals(event.getType())
                                            || !FlagConstants.EventSource.RESPONSE_CONTENT.equals(
                                                    event.getSource())) {
                                        return false;
                                    }
                                    @SuppressWarnings("unchecked")
                                    final Map<String, Object> featureMap =
                                            (Map<String, Object>)
                                                    event.getEventData()
                                                            .get(
                                                                    FlagConstants.EventDataKeys
                                                                            .FEATURE);
                                    if (featureMap == null) {
                                        return false;
                                    }
                                    final FeatureEvaluationResult result =
                                            FlagResponseMapper.toFeatureEvaluationResult(
                                                    featureMap);
                                    return result != null && metaJson.equals(result.getMeta());
                                }));
    }

    @Test
    public void processRequestEvent_getFeature_withMeta_stillDispatchesEdgeAnalyticsEvent()
            throws FlagClientException {
        initializeWithClient();
        final String metaJson = "{\"variant\":\"control\"}";
        final AnalyticsParam analytics = new AnalyticsParam(23261, 1, "feature-a", "10283012");
        final FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, metaJson, analytics);
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));

        flushQueuedExposure();

        verify(mockExtensionApi, times(2)).dispatch(any(Event.class));
        verify(mockExtensionApi)
                .dispatch(
                        argThat(
                                event ->
                                        EventType.EDGE.equals(event.getType())
                                                && EventSource.REQUEST_CONTENT.equals(
                                                        event.getSource())));
    }

    @Test
    public void processRequestEvent_getFeature_eligibleFeature_doesNotDispatchEdgeBeforeFlush()
            throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(23261, "fg-group", 1, "feature-a", "10283012");
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "feature-a"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void processRequestEvent_isFeatureEnabled_enabled_dispatchesEdgeAnalyticsEvent()
            throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(23261, "fg-group", 1, "dark-mode", "10283012");
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "dark-mode"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        flushQueuedExposure();

        verify(mockExtensionApi, times(2)).dispatch(any(Event.class));
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void processRequestEvent_isFeatureEnabled_controlCohort_dispatchesEdgeAfterFlush()
            throws FlagClientException {
        initializeWithClient();
        final AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "0");
        final FeatureResult feature =
                new FeatureResult(-1, null, "||features||", null, null, analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "checkout-flag"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        flushQueuedExposure();

        verify(mockExtensionApi, times(2)).dispatch(any(Event.class));
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void processRequestEvent_getFeature_controlCohort_dispatchesEdgeAnalyticsEvent()
            throws FlagClientException {
        initializeWithClient();
        final AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "0");
        final FeatureResult feature =
                new FeatureResult(-1, null, "||features||", null, null, analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        flushQueuedExposure();

        verify(mockExtensionApi, times(2)).dispatch(any(Event.class));
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void appStateChange_background_flushesQueuedExposure() throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        verify(mockExtensionApi, never())
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void appStateChange_background_pausesPeriodicTimerBeforeFlush()
            throws FlagClientException {
        final ExposureQueueTestSupport.ManualFlushTimer manualFlushTimer =
                new ExposureQueueTestSupport.ManualFlushTimer();
        final FeatureExposureQueue analyticsQueue =
                ExposureQueueTestSupport.createQueue(
                        events -> FlagEdgeHandler.dispatchExposureEvents(mockExtensionApi, events),
                        TestCallerThreadExecutorService.INSTANCE,
                        manualFlushTimer,
                        FlagConstants.ExposureQueue.FLUSH_INTERVAL_MS,
                        FlagConstants.ExposureQueue.BATCH_SIZE);
        final FlagClientManager manager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi, TestCallerThreadExecutorService.INSTANCE, analyticsQueue);
        initializeManagerWithClient(manager);

        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        manager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        assertTrue(manualFlushTimer.isScheduled());

        manager.handleAppStateChange(AppState.BACKGROUND);

        assertFalse(manualFlushTimer.isScheduled());
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void appStateChange_foreground_resumesPeriodicTimerWhenPendingWorkRemains()
            throws FlagClientException {
        final ExposureQueueTestSupport.ManualFlushTimer manualFlushTimer =
                new ExposureQueueTestSupport.ManualFlushTimer();
        final FeatureExposureQueue analyticsQueue =
                ExposureQueueTestSupport.createQueue(
                        events -> FlagEdgeHandler.dispatchExposureEvents(mockExtensionApi, events),
                        TestCallerThreadExecutorService.INSTANCE,
                        manualFlushTimer,
                        FlagConstants.ExposureQueue.FLUSH_INTERVAL_MS,
                        FlagConstants.ExposureQueue.BATCH_SIZE);
        final FlagClientManager manager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi, TestCallerThreadExecutorService.INSTANCE, analyticsQueue);
        initializeManagerWithClient(manager);

        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        manager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));
        analyticsQueue.pausePeriodicFlush();

        assertFalse(manualFlushTimer.isScheduled());

        manager.handleAppStateChange(AppState.FOREGROUND);

        assertTrue(manualFlushTimer.isScheduled());
    }

    @Test
    public void appStateChange_background_flushesWhenClientNotReady() throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        FlagTestFixtures.markClientNotReady(mockFlagClient);

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        verify(mockFlagClient, never()).setAppState(any());
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void processRequestEvent_getFeature_nullVariantId_doesNotDispatchEdgeEvent()
            throws FlagClientException {
        initializeWithClient();
        final AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", null);
        final FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void processRequestEvent_isFeatureEnabled_nullVariantId_doesNotDispatchEdgeEvent()
            throws FlagClientException {
        initializeWithClient();
        final AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", null);
        final FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED, "checkout-flag"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void processRequestEvent_twentyUniqueFeatures_autoFlushesWithoutManualFlush()
            throws FlagClientException {
        initializeWithClient();
        when(mockFlagClient.getFeature(anyString(), any(GetFeatureRequest.class)))
                .thenAnswer(
                        invocation -> {
                            final String featureName = invocation.getArgument(0);
                            final int featureId =
                                    Integer.parseInt(featureName.replace("feature-", ""));
                            return featureResultWithAnalytics(
                                    -1, "||features||", featureId, featureName, "10283012");
                        });

        for (int index = 1; index <= FlagConstants.ExposureQueue.BATCH_SIZE; index++) {
            clientManager.processRequestEvent(
                    buildRequestEvent(
                            FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE,
                            "feature-" + index));
        }

        verify(mockExtensionApi, times(40)).dispatch(any(Event.class));
        verify(mockExtensionApi, times(20))
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void processRequestEvent_repeatedEvaluations_backgroundFlushAggregatesDisplayCount()
            throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        for (int index = 0; index < 10; index++) {
            clientManager.processRequestEvent(
                    buildRequestEvent(
                            FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE,
                            "checkout-flag"));
        }

        verify(mockExtensionApi, never())
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(11)).dispatch(captor.capture());
        final Event edgeEvent = ExposureTestAssertions.findEdgeEvent(captor.getAllValues());
        assertNotNull(edgeEvent);
        assertEquals(10, ExposureTestAssertions.extractDisplayCount(edgeEvent));
    }

    @Test
    public void closeClient_flushesPendingExposureWithoutManualFlush() throws FlagClientException {
        initializeWithClient();
        final FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(
                buildRequestEvent(
                        FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE, "checkout-flag"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));

        clientManager.closeClient();

        verify(mockExtensionApi, times(2)).dispatch(any(Event.class));
        verify(mockExtensionApi)
                .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void scheduledTimerFlush_dispatchesPendingExposureWithoutManualFlush() throws Exception {
        final CountDownLatch flushLatch = new CountDownLatch(1);
        final FeatureExposureQueue scheduledQueue =
                ExposureQueueTestSupport.createWithScheduledTimer(
                        events -> {
                            FlagEdgeHandler.dispatchExposureEvents(mockExtensionApi, events);
                            flushLatch.countDown();
                        },
                        100L,
                        FlagConstants.ExposureQueue.BATCH_SIZE);
        final FlagClientManager scheduledManager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi, TestCallerThreadExecutorService.INSTANCE, scheduledQueue);
        try {
            FlagTestFixtures.initializeClientManager(scheduledManager, mockFlagClient);

            final FeatureResult feature =
                    featureResultWithAnalytics(
                            -1, "||features||", 173226, "checkout-flag", "10283012");
            when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                    .thenReturn(feature);

            scheduledManager.processRequestEvent(
                    buildRequestEvent(
                            FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE,
                            "checkout-flag"));

            assertTrue(
                    "Scheduled timer should flush queued exposure analytics",
                    flushLatch.await(2, TimeUnit.SECONDS));
            verify(mockExtensionApi)
                    .dispatch(argThat(FlagClientManagerTest::isPropositionDisplayEdgeEvent));
        } finally {
            scheduledManager.closeClient();
        }
    }
}
