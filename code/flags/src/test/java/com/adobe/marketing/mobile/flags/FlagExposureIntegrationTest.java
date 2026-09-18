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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * End-to-end tests for evaluation → queue → flush → Edge dispatch through {@link
 * FlagClientManager}.
 */
public class FlagExposureIntegrationTest {

    @Mock private ExtensionApi mockExtensionApi;
    @Mock private com.adobe.marketing.mobile.flags.engine.FlagClient mockFlagClient;

    private FlagClientManager clientManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        clientManager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi, TestCallerThreadExecutorService.INSTANCE);
        FlagTestFixtures.initializeClientManager(clientManager, mockFlagClient);
    }

    @After
    public void tearDown() {
        clientManager.closeClient();
    }

    @Test
    public void repeatedEvaluations_sameCorrelationId_aggregateDisplayCountOnFlush()
            throws FlagClientException {
        FeatureResult feature = standaloneFeature(173226, "checkout-flag", "10283012");
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        for (int index = 0; index < 5; index++) {
            clientManager.processRequestEvent(buildGetFeatureEvent("checkout-flag"));
        }

        verify(mockExtensionApi, never())
                .dispatch(argThat(ExposureTestAssertions::isPropositionDisplayEdgeEvent));

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(6)).dispatch(captor.capture());

        Event edgeEvent = ExposureTestAssertions.findEdgeEvent(captor.getAllValues());
        assertNotNull(edgeEvent);
        assertEquals(5, ExposureTestAssertions.extractDisplayCount(edgeEvent));
        assertNotNull(ExposureTestAssertions.extractTimestamp(edgeEvent));
    }

    @Test
    public void isFeatureEnabled_controlCohort_queuesExposureUntilFlush()
            throws FlagClientException {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "0");
        FeatureResult feature =
                new FeatureResult(
                        -1,
                        null,
                        FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY,
                        null,
                        null,
                        analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(buildIsEnabledEvent("checkout-flag"));

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        clientManager.handleAppStateChange(AppState.BACKGROUND);

        verify(mockExtensionApi)
                .dispatch(argThat(ExposureTestAssertions::isPropositionDisplayEdgeEvent));
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(2)).dispatch(captor.capture());
        assertEquals(
                "F-173226-0",
                ExposureTestAssertions.extractCorrelationId(
                        ExposureTestAssertions.findEdgeEvent(captor.getAllValues())));
    }

    @Test
    public void nullVariantId_neverQueuesExposureEvenAfterFlush() throws FlagClientException {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", null);
        FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(buildGetFeatureEvent("checkout-flag"));
        clientManager.handleAppStateChange(AppState.BACKGROUND);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(ExposureTestAssertions::isPropositionDisplayEdgeEvent));
    }

    @Test
    public void twentyUniqueFeatures_autoFlushesTwentyEdgeEventsWithoutManualFlush()
            throws FlagClientException {
        when(mockFlagClient.getFeature(anyString(), any(GetFeatureRequest.class)))
                .thenAnswer(
                        invocation -> {
                            final String featureName = invocation.getArgument(0);
                            final int featureId =
                                    Integer.parseInt(featureName.replace("feature-", ""));
                            return standaloneFeature(featureId, featureName, "10283012");
                        });

        for (int index = 1; index <= FlagConstants.ExposureQueue.BATCH_SIZE; index++) {
            clientManager.processRequestEvent(buildGetFeatureEvent("feature-" + index));
        }

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(40)).dispatch(captor.capture());
        assertEquals(
                FlagConstants.ExposureQueue.BATCH_SIZE,
                ExposureTestAssertions.countEdgeEvents(captor.getAllValues()));
    }

    @Test
    public void featureGroup_buildsExpectedEdgePayloadOnBackgroundFlush()
            throws FlagClientException {
        clientManager.closeClient();
        clientManager =
                FlagTestFixtures.createClientManager(
                        mockExtensionApi, TestCallerThreadExecutorService.INSTANCE);
        FlagTestFixtures.initializeClientManager(clientManager, mockFlagClient);
        FeatureResult feature =
                featureGroupFeature(456789, "fg-group-v1", 1, "feature-a", "10283013");
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        clientManager.processRequestEvent(buildGetFeatureEvent("feature-a"));

        clientManager.handleAppStateChange(AppState.BACKGROUND);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(2)).dispatch(captor.capture());
        Event edgeEvent = ExposureTestAssertions.findEdgeEvent(captor.getAllValues());
        assertNotNull(edgeEvent);
        assertEquals("FG-456789-10283013", ExposureTestAssertions.extractCorrelationId(edgeEvent));
        assertTrue(ExposureTestAssertions.hasFeatureGroupItems(edgeEvent));
    }

    @Test
    public void featureGroupDistinctFeatures_sameVariant_flushSeparatelyWithGroupCorrelationId()
            throws FlagClientException {
        FeatureResult featureA =
                featureGroupFeature(456789, "fg-group-v1", 1, "feature-a", "10283013");
        FeatureResult featureB =
                featureGroupFeature(456789, "fg-group-v1", 2, "feature-b", "10283013");
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(featureA);
        when(mockFlagClient.getFeature(eq("feature-b"), any(GetFeatureRequest.class)))
                .thenReturn(featureB);

        clientManager.processRequestEvent(buildGetFeatureEvent("feature-a"));
        clientManager.processRequestEvent(buildGetFeatureEvent("feature-b"));
        clientManager.handleAppStateChange(AppState.BACKGROUND);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(4)).dispatch(captor.capture());
        final List<Event> edgeEvents = new ArrayList<>();
        for (final Event event : captor.getAllValues()) {
            if (ExposureTestAssertions.isPropositionDisplayEdgeEvent(event)) {
                edgeEvents.add(event);
            }
        }
        assertEquals(2, edgeEvents.size());
        assertEquals(
                "FG-456789-10283013",
                ExposureTestAssertions.extractCorrelationId(edgeEvents.get(0)));
        assertEquals(
                "FG-456789-10283013",
                ExposureTestAssertions.extractCorrelationId(edgeEvents.get(1)));
        assertEquals(1, ExposureTestAssertions.extractDisplayCount(edgeEvents.get(0)));
        assertEquals(1, ExposureTestAssertions.extractDisplayCount(edgeEvents.get(1)));
    }

    private static FeatureResult featureGroupFeature(
            final int featureGroupId,
            final String featureGroupKey,
            final int featureId,
            final String featureKey,
            final String variantId) {
        AnalyticsParam analytics =
                new AnalyticsParam(featureGroupId, featureId, featureKey, variantId);
        return new FeatureResult(featureId, featureKey, featureGroupKey, null, null, analytics);
    }

    private Event buildGetFeatureEvent(final String featureName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureName);
        return flagsRequestEvent(eventData);
    }

    private Event buildIsEnabledEvent(final String featureName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureName);
        return flagsRequestEvent(eventData);
    }

    private static Event flagsRequestEvent(final Map<String, Object> eventData) {
        return new Event.Builder(
                        "Test",
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT)
                .setEventData(eventData)
                .build();
    }

    private static FeatureResult standaloneFeature(
            final int featureId, final String featureKey, final String variantId) {
        AnalyticsParam analytics = new AnalyticsParam(-1, featureId, featureKey, variantId);
        return new FeatureResult(
                featureId,
                featureKey,
                FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY,
                null,
                null,
                analytics);
    }
}
