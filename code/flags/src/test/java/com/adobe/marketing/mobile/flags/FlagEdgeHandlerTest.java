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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FlagEdgeHandlerTest {

    private static final long DEFAULT_EVALUATED_AT_MILLIS = 1_700_000_000_000L;

    @Mock private ExtensionApi mockExtensionApi;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractXdm(Event event) {
        return (Map<String, Object>) event.getEventData().get(FlagConstants.Edge.XDM);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDecisioning(Event event) {
        Map<String, Object> xdm = extractXdm(event);
        Map<String, Object> experience =
                (Map<String, Object>) xdm.get(FlagConstants.Edge.EXPERIENCE);
        return (Map<String, Object>) experience.get(FlagConstants.Edge.DECISIONING);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractScopeDetails(Event event) {
        Map<String, Object> decisioning = extractDecisioning(event);
        List<Map<String, Object>> propositions =
                (List<Map<String, Object>>) decisioning.get(FlagConstants.Edge.PROPOSITIONS);
        return (Map<String, Object>) propositions.get(0).get(FlagConstants.Edge.SCOPE_DETAILS);
    }

    private static FeatureResult featureResultWithAnalytics(
            int featureGroupId,
            String featureGroupKey,
            int featureId,
            String featureKey,
            String variantId) {
        AnalyticsParam analytics =
                new AnalyticsParam(featureGroupId, featureId, featureKey, variantId);
        return new FeatureResult(featureId, featureKey, featureGroupKey, null, null, analytics);
    }

    private void dispatchExposure(@Nullable final FeatureResult feature, final int displayCount) {
        if (feature == null) {
            return;
        }

        final String aggregationKey = ExposureEventIdGenerator.generateAggregationKey(feature);
        if (aggregationKey == null) {
            return;
        }

        final AggregatedExposureEvent event =
                new AggregatedExposureEvent(
                        aggregationKey, feature, DEFAULT_EVALUATED_AT_MILLIS, displayCount);
        FlagEdgeHandler.dispatchExposureEvent(mockExtensionApi, event);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void dispatchExposureEvent_includesCollectRequestPath() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> eventData = captor.getValue().getEventData();
        Map<String, Object> request =
                (Map<String, Object>) eventData.get(FlagConstants.Edge.REQUEST);
        assertNotNull(request);
        assertEquals(FlagConstants.Edge.COLLECT_PATH, request.get(FlagConstants.Edge.REQUEST_PATH));
    }

    @Test
    public void dispatchExposureEvent_dispatchesEdgeEvent() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(1)).dispatch(captor.capture());

        Event dispatched = captor.getValue();
        assertEquals(EventType.EDGE, dispatched.getType());
        assertEquals(EventSource.REQUEST_CONTENT, dispatched.getSource());
        assertEquals(FlagConstants.EventNames.EDGE_FEATURE_EXPOSURE_REQUEST, dispatched.getName());
    }

    @Test
    public void dispatchExposureEvent_xdmContainsPropositionDisplayEventType() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> xdm = extractXdm(captor.getValue());
        assertEquals(
                FlagConstants.Edge.EVENT_TYPE_PROPOSITION_DISPLAY,
                xdm.get(FlagConstants.Edge.EVENT_TYPE));
    }

    @Test
    public void dispatchExposureEvent_standaloneFeature_buildsFeatureScopeDetails() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> scopeDetails = extractScopeDetails(captor.getValue());
        assertEquals(
                FlagConstants.Edge.DECISION_PROVIDER_FLAGS,
                scopeDetails.get(FlagConstants.Edge.DECISION_PROVIDER));
        assertEquals("F-173226-10283012", scopeDetails.get(FlagConstants.Edge.CORRELATION_ID));

        @SuppressWarnings("unchecked")
        Map<String, Object> activity =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.ACTIVITY);
        assertEquals("F-173226", activity.get(FlagConstants.Edge.IDENTITY_ID));
        assertEquals("checkout-flag", activity.get(FlagConstants.Edge.NAME));

        @SuppressWarnings("unchecked")
        Map<String, Object> experience =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.SCOPE_EXPERIENCE);
        assertEquals("Variant-10283012", experience.get(FlagConstants.Edge.IDENTITY_ID));

        @SuppressWarnings("unchecked")
        Map<String, Object> characteristics =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.CHARACTERISTICS);
        assertEquals(
                FlagConstants.Edge.ENTITY_TYPE_FEATURE,
                characteristics.get(FlagConstants.Edge.ENTITY_TYPE));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void dispatchExposureEvent_featureGroup_buildsFeatureGroupScopeDetailsWithItems() {
        FeatureResult feature =
                featureResultWithAnalytics(
                        456789, "checkout-experiment", 173226, "checkout-flag", "10283013");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> scopeDetails = extractScopeDetails(captor.getValue());
        assertEquals("FG-456789-10283013", scopeDetails.get(FlagConstants.Edge.CORRELATION_ID));

        Map<String, Object> activity =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.ACTIVITY);
        assertEquals("FG-456789", activity.get(FlagConstants.Edge.IDENTITY_ID));
        assertEquals("checkout-experiment", activity.get(FlagConstants.Edge.NAME));

        Map<String, Object> characteristics =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.CHARACTERISTICS);
        assertEquals(
                FlagConstants.Edge.ENTITY_TYPE_FEATURE_GROUP,
                characteristics.get(FlagConstants.Edge.ENTITY_TYPE));

        Map<String, Object> decisioning = extractDecisioning(captor.getValue());
        List<Map<String, Object>> propositions =
                (List<Map<String, Object>>) decisioning.get(FlagConstants.Edge.PROPOSITIONS);
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) propositions.get(0).get(FlagConstants.Edge.ITEMS);
        assertEquals(1, items.size());
        assertEquals("173226", items.get(0).get(FlagConstants.Edge.IDENTITY_ID));
        assertEquals("checkout-flag", items.get(0).get(FlagConstants.Edge.NAME));
    }

    @Test
    public void dispatchExposureEvent_controlCohort_dispatchesUsingAnalyticsParam() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "0");
        FeatureResult feature = new FeatureResult(-1, null, "||features||", null, null, analytics);

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> scopeDetails = extractScopeDetails(captor.getValue());
        assertEquals("F-173226-0", scopeDetails.get(FlagConstants.Edge.CORRELATION_ID));
    }

    @Test
    public void dispatchExposureEvent_usesAnalyticsParamNotFeatureResultFields() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 999, "analytics-key", "10283012");
        FeatureResult feature =
                new FeatureResult(173226, "result-key", "||features||", null, null, analytics);

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> scopeDetails = extractScopeDetails(captor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> activity =
                (Map<String, Object>) scopeDetails.get(FlagConstants.Edge.ACTIVITY);
        assertEquals("F-999", activity.get(FlagConstants.Edge.IDENTITY_ID));
        assertEquals("analytics-key", activity.get(FlagConstants.Edge.NAME));
    }

    @Test
    public void dispatchExposureEvent_noAnalyticsParam_doesNotDispatch() {
        FeatureResult feature = new FeatureResult(42, "feature-x", null, null, null);

        dispatchExposure(feature, 1);

        verify(mockExtensionApi, never()).dispatch(any(Event.class));
    }

    @Test
    public void dispatchExposureEvent_nullVariantId_doesNotDispatch() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", null);
        FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);

        dispatchExposure(feature, 1);

        verify(mockExtensionApi, never()).dispatch(any(Event.class));
    }

    @Test
    public void dispatchExposureEvent_nullFeature_doesNotDispatch() {
        dispatchExposure(null, 1);

        verify(mockExtensionApi, never()).dispatch(any(Event.class));
    }

    @Test
    public void dispatchExposureEvent_doesNotIncludeProfileIdentityMap() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> xdm = extractXdm(captor.getValue());
        assertFalse(xdm.containsKey(FlagConstants.Edge.IDENTITY_MAP));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void dispatchExposureEvent_propositionEventTypeContainsDisplay() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");

        dispatchExposure(feature, 1);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> decisioning = extractDecisioning(captor.getValue());
        Map<String, Object> propositionEventType =
                (Map<String, Object>) decisioning.get(FlagConstants.Edge.PROPOSITION_EVENT_TYPE);
        assertEquals(1, propositionEventType.get(FlagConstants.Edge.DISPLAY));
    }

    @Test
    public void dispatchExposureEvent_includesAggregatedDisplayCount() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        AggregatedExposureEvent event =
                new AggregatedExposureEvent("F-173226-10283012", feature, 1_700_000_000_000L, 7);

        FlagEdgeHandler.dispatchExposureEvent(mockExtensionApi, event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> decisioning = extractDecisioning(captor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> propositionEventType =
                (Map<String, Object>) decisioning.get(FlagConstants.Edge.PROPOSITION_EVENT_TYPE);
        assertEquals(7, propositionEventType.get(FlagConstants.Edge.DISPLAY));
    }

    @Test
    public void dispatchExposureEvent_includesLastEvaluationTimestamp() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        AggregatedExposureEvent event =
                new AggregatedExposureEvent("F-173226-10283012", feature, 1_700_000_000_000L, 1);

        FlagEdgeHandler.dispatchExposureEvent(mockExtensionApi, event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> xdm = extractXdm(captor.getValue());
        assertEquals("2023-11-14T22:13:20Z", xdm.get(FlagConstants.Edge.TIMESTAMP));
    }

    @Test
    public void dispatchExposureEvent_includesLastEvaluationTimestamp_withNonZeroMillis() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        // 1_700_000_000_123L = 2023-11-14T22:13:20.123Z — exercises the non-.000Z branch of
        // formatTimestamp, which was previously untested.
        AggregatedExposureEvent event =
                new AggregatedExposureEvent("F-173226-10283012", feature, 1_700_000_000_123L, 1);

        FlagEdgeHandler.dispatchExposureEvent(mockExtensionApi, event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi).dispatch(captor.capture());

        Map<String, Object> xdm = extractXdm(captor.getValue());
        assertEquals("2023-11-14T22:13:20.123Z", xdm.get(FlagConstants.Edge.TIMESTAMP));
    }

    @Test
    public void dispatchExposureEvent_zeroDisplayCount_doesNotDispatch() {
        FeatureResult feature =
                featureResultWithAnalytics(-1, "||features||", 173226, "checkout-flag", "10283012");
        AggregatedExposureEvent event =
                new AggregatedExposureEvent("F-173226-10283012", feature, 1_700_000_000_000L, 0);

        FlagEdgeHandler.dispatchExposureEvent(mockExtensionApi, event);

        verify(mockExtensionApi, never()).dispatch(any(Event.class));
    }

    @Test
    public void dispatchExposureEvents_continuesAfterSingleEventFailure() {
        FeatureResult featureOne =
                featureResultWithAnalytics(-1, "||features||", 1, "feature-one", "10283012");
        FeatureResult featureTwo =
                featureResultWithAnalytics(-1, "||features||", 2, "feature-two", "10283013");
        AggregatedExposureEvent eventOne =
                new AggregatedExposureEvent("F-1-10283012", featureOne, 1_700_000_000_000L, 1);
        AggregatedExposureEvent eventTwo =
                new AggregatedExposureEvent("F-2-10283013", featureTwo, 1_700_000_000_001L, 3);

        doThrow(new RuntimeException("simulated dispatch failure"))
                .doNothing()
                .when(mockExtensionApi)
                .dispatch(any(Event.class));

        FlagEdgeHandler.dispatchExposureEvents(mockExtensionApi, Arrays.asList(eventOne, eventTwo));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(2)).dispatch(captor.capture());

        // eventOne threw; verify the survivor (eventTwo) carries the correct payload
        // so a failure on one item cannot corrupt or suppress another item's data.
        Event survivor = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> propositionEventType =
                (Map<String, Object>)
                        extractDecisioning(survivor).get(FlagConstants.Edge.PROPOSITION_EVENT_TYPE);
        assertEquals(3, propositionEventType.get(FlagConstants.Edge.DISPLAY));
        assertEquals(
                "F-2-10283013",
                extractScopeDetails(survivor).get(FlagConstants.Edge.CORRELATION_ID));
    }
}
