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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResolver;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class FlagExtensionTest {

    @Mock private ExtensionApi mockExtensionApi;
    @Mock private com.adobe.marketing.mobile.flags.engine.FlagClient mockFlagClient;
    @Mock private SharedStateResolver mockSharedStateResolver;

    private FlagExtension extension;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        extension = FlagTestFixtures.createExtension(mockExtensionApi);
    }

    private void initializeExtensionWithClient() {
        FlagTestFixtures.initializeExtension(extension, mockFlagClient);
    }

    @Test
    public void testGetName() {
        assertEquals("com.adobe.flags", extension.getName());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", extension.getVersion());
    }

    @Test
    public void testGetFriendlyName() {
        assertEquals("Flags", extension.getFriendlyName());
    }

    @Test
    public void testOnRegistered_registersEventListener() {
        extension.onRegistered();

        verify(mockExtensionApi, times(1))
                .registerEventListener(
                        eq(FlagConstants.EventType.FLAGS),
                        eq(FlagConstants.EventSource.REQUEST_CONTENT),
                        any());
    }

    @Test
    public void readyForEvent_TR1_configPending_returnsFalse() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.PENDING, null);

        assertFalse(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR2_configReady_flagInitPending_returnsFalse() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(event, SharedStateStatus.PENDING, null);
        when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                .thenReturn(mockSharedStateResolver);

        assertFalse(extension.readyForEvent(event));
        verify(mockExtensionApi, atLeastOnce()).createPendingSharedState(event);
    }

    @Test
    public void readyForEvent_TR3_configReady_flagReady_returnsTrue() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR4_configReady_flagFailed_returnsTrue() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_FAILED));

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR5_configReady_missingRequiredKeys_returnsTrue() {
        Event event = flagApiEvent();
        Map<String, Object> partialConfig = new HashMap<>();
        partialConfig.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");
        stubConfigurationSharedState(event, SharedStateStatus.SET, partialConfig);

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR6_nonFlagEvent_returnsTrue() {
        Event event =
                new Event.Builder(
                                "Test Event",
                                "com.adobe.eventType.other",
                                "com.adobe.eventSource.other")
                        .build();

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR7_configResponseWhileFlagPending_returnsTrue() {
        Event configEvent =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .setEventData(validConfigData())
                        .build();
        stubFlagSharedState(configEvent, SharedStateStatus.PENDING, null);

        assertTrue(extension.readyForEvent(configEvent));
    }

    @Test
    public void readyForEvent_TR8_configReady_flagReady_edgeIdentityPending_returnsFalse() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));
        stubEdgeIdentitySharedState(event, SharedStateStatus.PENDING, null);

        assertFalse(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR9_configReady_flagReady_edgeIdentityNotRegistered_returnsTrue() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));
        stubEdgeIdentityNotRegistered(event);

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR10_configReady_flagReady_edgeIdentityReady_returnsTrue() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));
        stubEdgeIdentityReady(event);

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void readyForEvent_TR11_configReady_flagReady_edgeIdentitySetEmpty_returnsTrue() {
        Event event = flagApiEvent();
        stubConfigurationSharedState(event, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                event,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));
        stubEdgeIdentitySharedState(
                event, SharedStateStatus.SET, Map.of(FlagConstants.Edge.IDENTITY_MAP, Map.of()));

        assertTrue(extension.readyForEvent(event));
    }

    @Test
    public void flagRequest_nullEventData_completesWithoutError() {
        Event event =
                new Event.Builder(
                                "Test Event",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .build();

        extension.handleFlagRequestContent(event);
    }

    @Test
    public void flagRequest_emptyEventData_completesWithoutError() {
        Event event =
                new Event.Builder(
                                "Test Event",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(new HashMap<>())
                        .build();

        extension.handleFlagRequestContent(event);
    }

    @Test
    public void flagRequest_getFeatureSuccess_dispatchesEdgeExposureEvent() throws Exception {
        initializeExtensionWithClient();
        FeatureResult feature =
                featureResultWithExposure(23261, "fg-group-v1", 1, "feature-a", "10283012");
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(feature);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");
        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));

        dispatchLifecyclePause();

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
    public void flagRequest_getFeatureReturnsNull_doesNotDispatchEdgeEvent() throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");
        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));

        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void flagRequest_getFeatureThrows_doesNotDispatchEdgeEvent() throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenThrow(new FlagClientException("test error"));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");
        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void flagRequest_getFeature_passesIdentityMapFromEdgeIdentitySharedState()
            throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(FlagConstants.Edge.IDENTITY_ID, "ecid-from-shared-state");
        ecidItem.put(FlagConstants.Edge.IDENTITY_PRIMARY, true);
        final Map<String, Object> xdmState = new HashMap<>();
        xdmState.put(FlagConstants.Edge.IDENTITY_MAP, Map.of("ECID", List.of(ecidItem)));

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        any(Event.class),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");

        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature Identity Map",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("feature-a"), captor.capture());
        assertNotNull(captor.getValue().getIdentityMap());
        assertEquals(
                "ecid-from-shared-state",
                captor.getValue()
                        .getIdentityMap()
                        .get("ECID")
                        .get(0)
                        .get(FlagConstants.Edge.IDENTITY_ID));
    }

    @Test
    public void flagRequest_isFeatureEnabled_passesIdentityMapFromEdgeIdentitySharedState()
            throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(
                        featureResultWithExposure(23261, "fg-group", 1, "dark-mode", "10283012"));

        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(FlagConstants.Edge.IDENTITY_ID, "ecid-is-enabled");
        ecidItem.put(FlagConstants.Edge.IDENTITY_PRIMARY, true);
        final Map<String, Object> xdmState = new HashMap<>();
        xdmState.put(FlagConstants.Edge.IDENTITY_MAP, Map.of("ECID", List.of(ecidItem)));

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        any(Event.class),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "dark-mode");
        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled Identity Map",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("dark-mode"), captor.capture());
        assertEquals(
                "ecid-is-enabled",
                captor.getValue()
                        .getIdentityMap()
                        .get("ECID")
                        .get(0)
                        .get(FlagConstants.Edge.IDENTITY_ID));
    }

    @Test
    public void flagRequest_withoutEdgeIdentityRegistered_evaluatesWithEmptyIdentityMap()
            throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("feature-a"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        any(Event.class),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");

        Event requestEvent =
                new Event.Builder(
                                "Test Without Edge Identity",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        stubConfigurationSharedState(requestEvent, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                requestEvent,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_READY));

        assertTrue(extension.readyForEvent(requestEvent));
        extension.handleFlagRequestContent(requestEvent);

        ArgumentCaptor<GetFeatureRequest> captor = ArgumentCaptor.forClass(GetFeatureRequest.class);
        verify(mockFlagClient).getFeature(eq("feature-a"), captor.capture());
        assertTrue(captor.getValue().getIdentityMap().isEmpty());
    }

    // --- isFeatureEnabled request: response and optional exposure dispatch ---

    @Test
    public void flagRequest_isFeatureEnabledSuccess_dispatchesEdgeExposureEvent() throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(
                        featureResultWithExposure(23261, "fg-group", 1, "dark-mode", "10283012"));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "dark-mode");
        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));

        dispatchLifecyclePause();

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
    public void flagRequest_isFeatureEnabledReturnsFalse_doesNotDispatchEdgeEvent()
            throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenReturn(null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "dark-mode");
        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));

        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void flagRequest_isFeatureEnabledThrows_doesNotDispatchEdgeEvent() throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("dark-mode"), any(GetFeatureRequest.class)))
                .thenThrow(new FlagClientException("test error"));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "dark-mode");
        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void flagRequest_isFeatureEnabled_missingClientId_doesNotDispatchEdgeEvent() {
        initializeExtensionWithClient();
        when(mockFlagClient.getClientId()).thenReturn(null);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "dark-mode");
        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    // --- Configuration response / initialization (T-H1, T-H2) ---

    @Test
    public void configurationResponse_TH1_validConfig_createsPendingStateAndStartsInit()
            throws Exception {
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
            when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                    .thenReturn(mockSharedStateResolver);

            Event event =
                    new Event.Builder(
                                    "Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                            .setEventData(validConfigData())
                            .build();

            extension.handleConfigurationResponse(event);

            verify(mockExtensionApi).createPendingSharedState(event);
            verify(mockSharedStateResolver, timeout(3000))
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
    public void configurationResponse_TH2_whenAlreadyReady_skipsPendingState() {
        initializeExtensionWithClient();

        Event event =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .setEventData(validConfigData())
                        .build();

        extension.handleConfigurationResponse(event);

        verify(mockExtensionApi, never()).createPendingSharedState(any());
    }

    @Test
    public void configurationResponse_nullEventData_doesNotInitializeClient() {
        Event event =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .build();

        extension.handleConfigurationResponse(event);

        assertFalse(FlagTestFixtures.getClientManager(extension).isClientReady());
    }

    @Test
    public void configurationResponse_emptyEventData_doesNotInitializeClient() {
        Event event =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .setEventData(new HashMap<>())
                        .build();

        extension.handleConfigurationResponse(event);

        assertFalse(FlagTestFixtures.getClientManager(extension).isClientReady());
    }

    @Test
    public void configurationResponse_missingRequiredKeys_doesNotInitializeClient() {
        Map<String, Object> configData = new HashMap<>();
        configData.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");

        Event event =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .setEventData(configData)
                        .build();

        extension.handleConfigurationResponse(event);

        assertFalse(FlagTestFixtures.getClientManager(extension).isClientReady());
    }

    @Test
    public void configurationResponse_whenAlreadyInitialized_remainsReady() {
        initializeExtensionWithClient();

        Map<String, Object> configData = validConfigData();
        Event event =
                new Event.Builder("Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                        .setEventData(configData)
                        .build();

        extension.handleConfigurationResponse(event);

        assertTrue(FlagTestFixtures.getClientManager(extension).isClientReady());
    }

    // --- Lifecycle events ---

    @Test
    public void lifecycleEvent_nullEventData_doesNotUpdateAppState() throws FlagClientException {
        initializeExtensionWithClient();

        Event event =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .build();

        extension.handleLifecycleEvent(event);

        verify(mockFlagClient, never()).setAppState(any());
    }

    @Test
    public void lifecycleEvent_emptyEventData_doesNotUpdateAppState() throws FlagClientException {
        initializeExtensionWithClient();

        Event event =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(new HashMap<>())
                        .build();

        extension.handleLifecycleEvent(event);

        verify(mockFlagClient, never()).setAppState(any());
    }

    @Test
    public void lifecycleEvent_unknownAction_doesNotUpdateAppState() throws FlagClientException {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(FlagConstants.Lifecycle.ACTION_KEY, "resume");

        Event event =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleLifecycleEvent(event);

        verify(mockFlagClient, never()).setAppState(any());
    }

    @Test
    public void lifecycleEvent_startAction_setsForeground() throws FlagClientException {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(FlagConstants.Lifecycle.ACTION_KEY, FlagConstants.Lifecycle.ACTION_START);

        Event event =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleLifecycleEvent(event);

        verify(mockFlagClient).setAppState(AppState.FOREGROUND);
    }

    @Test
    public void lifecycleEvent_pauseAction_setsBackground() throws FlagClientException {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(FlagConstants.Lifecycle.ACTION_KEY, FlagConstants.Lifecycle.ACTION_PAUSE);

        Event event =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleLifecycleEvent(event);

        verify(mockFlagClient).setAppState(AppState.BACKGROUND);
    }

    @Test
    public void lifecycleEvent_pauseAction_flushesQueuedExposure() throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(
                        featureResultWithExposure(
                                -1, "||features||", 173226, "checkout-flag", "10283012"));

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        requestData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "checkout-flag");
        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(requestData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1)).dispatch(any(Event.class));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));

        Map<String, Object> lifecycleData = new HashMap<>();
        lifecycleData.put(FlagConstants.Lifecycle.ACTION_KEY, FlagConstants.Lifecycle.ACTION_PAUSE);
        Event lifecycleEvent =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(lifecycleData)
                        .build();

        extension.handleLifecycleEvent(lifecycleEvent);

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
    public void
            flagRequest_getFeature_tenEvaluationsThenBackground_dispatchesSingleAggregatedEdgeEvent()
                    throws Exception {
        initializeExtensionWithClient();
        when(mockFlagClient.getFeature(eq("checkout-flag"), any(GetFeatureRequest.class)))
                .thenReturn(
                        featureResultWithExposure(
                                -1, "||features||", 173226, "checkout-flag", "10283012"));

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        requestData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "checkout-flag");
        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(requestData)
                        .build();

        for (int index = 0; index < 10; index++) {
            extension.handleFlagRequestContent(requestEvent);
        }

        verify(mockExtensionApi, never())
                .dispatch(argThat(ExposureTestAssertions::isPropositionDisplayEdgeEvent));

        dispatchLifecyclePause();

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(mockExtensionApi, times(11)).dispatch(captor.capture());
        Event edgeEvent = ExposureTestAssertions.findEdgeEvent(captor.getAllValues());
        assertNotNull(edgeEvent);
        assertEquals(10, ExposureTestAssertions.extractDisplayCount(edgeEvent));
    }

    // --- Flag request when client not ready (T-H4) ---

    @Test
    public void flagRequest_TH4_clientNotReady_dispatchesErrorWithoutQueue() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");

        Event requestEvent =
                new Event.Builder(
                                "Test",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        stubConfigurationSharedState(requestEvent, SharedStateStatus.SET, validConfigData());
        stubFlagSharedState(
                requestEvent,
                SharedStateStatus.SET,
                flagStatusMap(FlagConstants.SharedState.STATUS_FAILED));

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1))
                .dispatch(
                        argThat(
                                event ->
                                        event.getEventData() != null
                                                && event.getEventData()
                                                        .containsKey(
                                                                FlagConstants.EventDataKeys
                                                                        .RESPONSE_ERROR)));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    // --- Component ordering (COMP-1) ---

    @Test
    public void comp1_configResponseThenReadyThenRequest_succeeds() throws Exception {
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
            when(mockFlagClient.getClientId()).thenReturn("test-client");
            when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                    .thenReturn(mockSharedStateResolver);

            Event configEvent =
                    new Event.Builder(
                                    "Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                            .setEventData(validConfigData())
                            .build();
            extension.handleConfigurationResponse(configEvent);

            verify(mockSharedStateResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_READY.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));

            Map<String, Object> eventData = new HashMap<>();
            eventData.put(
                    FlagConstants.EventDataKeys.REQUEST_TYPE,
                    FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
            eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");
            Event requestEvent =
                    new Event.Builder(
                                    "Test Get Feature",
                                    FlagConstants.EventType.FLAGS,
                                    FlagConstants.EventSource.RESPONSE_CONTENT)
                            .setEventData(eventData)
                            .build();

            extension.handleFlagRequestContent(requestEvent);

            verify(mockExtensionApi, atLeastOnce())
                    .dispatch(
                            argThat(
                                    event ->
                                            FlagConstants.EventType.FLAGS.equals(event.getType())
                                                    && FlagConstants.EventSource.RESPONSE_CONTENT
                                                            .equals(event.getSource())
                                                    && event.getEventData() != null
                                                    && !event.getEventData()
                                                            .containsKey(
                                                                    FlagConstants.EventDataKeys
                                                                            .RESPONSE_ERROR)));
        }
    }

    @Test
    public void comp2_requestBeforeConfigThenInitThenResolve_succeeds() throws Exception {
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
            when(mockFlagClient.getClientId()).thenReturn("test-client");
            when(mockFlagClient.getFeature(anyString(), any(GetFeatureRequest.class)))
                    .thenReturn(
                            featureResultWithExposure(
                                    23261, "fg-group", 1, "feature-a", "10283012"));
            when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                    .thenReturn(mockSharedStateResolver);

            final Event requestEvent = isEnabledFlagRequest("feature-a");

            stubConfigurationSharedState(requestEvent, SharedStateStatus.PENDING, null);
            assertFalse(extension.readyForEvent(requestEvent));
            verify(mockExtensionApi, never()).dispatch(any(Event.class));

            stubConfigurationSharedState(requestEvent, SharedStateStatus.SET, validConfigData());
            stubFlagSharedState(requestEvent, SharedStateStatus.PENDING, null);
            assertFalse(extension.readyForEvent(requestEvent));

            final Event configEvent =
                    new Event.Builder(
                                    "Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                            .setEventData(validConfigData())
                            .build();
            extension.handleConfigurationResponse(configEvent);

            verify(mockSharedStateResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_READY.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));
            verify(mockExtensionApi, never())
                    .dispatch(
                            argThat(
                                    event ->
                                            FlagConstants.EventType.FLAGS.equals(event.getType())
                                                    && FlagConstants.EventSource.RESPONSE_CONTENT
                                                            .equals(event.getSource())));

            stubFlagSharedState(
                    requestEvent,
                    SharedStateStatus.SET,
                    flagStatusMap(FlagConstants.SharedState.STATUS_READY));
            assertTrue(extension.readyForEvent(requestEvent));

            extension.handleFlagRequestContent(requestEvent);

            verify(mockExtensionApi, atLeastOnce())
                    .dispatch(
                            argThat(
                                    event ->
                                            FlagConstants.EventType.FLAGS.equals(event.getType())
                                                    && FlagConstants.EventSource.RESPONSE_CONTENT
                                                            .equals(event.getSource())
                                                    && event.getEventData() != null
                                                    && !event.getEventData()
                                                            .containsKey(
                                                                    FlagConstants.EventDataKeys
                                                                            .RESPONSE_ERROR)));
        }
    }

    @Test
    public void comp3_initFailedThenRequestDelivered_dispatchesError() {
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
            when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                    .thenReturn(mockSharedStateResolver);

            final Event configEvent =
                    new Event.Builder(
                                    "Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                            .setEventData(validConfigData())
                            .build();
            extension.handleConfigurationResponse(configEvent);

            verify(mockSharedStateResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_FAILED.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));

            final Event requestEvent = isEnabledFlagRequest("feature-a");
            stubConfigurationSharedState(requestEvent, SharedStateStatus.SET, validConfigData());
            stubFlagSharedState(
                    requestEvent,
                    SharedStateStatus.SET,
                    flagStatusMap(FlagConstants.SharedState.STATUS_FAILED));

            assertTrue(extension.readyForEvent(requestEvent));
            extension.handleFlagRequestContent(requestEvent);

            verify(mockExtensionApi, times(1))
                    .dispatch(
                            argThat(
                                    event ->
                                            event.getEventData() != null
                                                    && event.getEventData()
                                                            .containsKey(
                                                                    FlagConstants.EventDataKeys
                                                                            .RESPONSE_ERROR)));
            verify(mockExtensionApi, never())
                    .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
        }
    }

    @Test
    public void comp4_onRegisteredWithConfigAlreadySet_startsInitWithoutConfigEvent()
            throws Exception {
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
            when(mockExtensionApi.createPendingSharedState(isNull()))
                    .thenReturn(mockSharedStateResolver);
            when(mockExtensionApi.getSharedState(
                            eq(FlagConstants.Configuration.EXTENSION_NAME),
                            isNull(),
                            anyBoolean(),
                            eq(SharedStateResolution.ANY)))
                    .thenReturn(new SharedStateResult(SharedStateStatus.SET, validConfigData()));

            extension.onRegistered();

            verify(mockExtensionApi).createPendingSharedState(null);
            verify(mockSharedStateResolver, timeout(3000))
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
    public void comp5_fiveRequestsWhileInitPending_allSucceedAfterResolve() throws Exception {
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
            when(mockFlagClient.getClientId()).thenReturn("test-client");
            when(mockFlagClient.getFeature(anyString(), any(GetFeatureRequest.class)))
                    .thenReturn(
                            featureResultWithExposure(
                                    23261, "fg-group", 1, "feature-a", "10283012"));
            when(mockExtensionApi.createPendingSharedState(any(Event.class)))
                    .thenReturn(mockSharedStateResolver);

            final Event[] requests = new Event[5];
            for (int i = 0; i < 5; i++) {
                requests[i] = isEnabledFlagRequest("feature-" + i);
                stubConfigurationSharedState(requests[i], SharedStateStatus.SET, validConfigData());
                stubFlagSharedState(requests[i], SharedStateStatus.PENDING, null);
                assertFalse(extension.readyForEvent(requests[i]));
            }

            final Event configEvent =
                    new Event.Builder(
                                    "Config", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                            .setEventData(validConfigData())
                            .build();
            extension.handleConfigurationResponse(configEvent);

            verify(mockSharedStateResolver, timeout(3000))
                    .resolve(
                            argThat(
                                    (Map<String, Object> map) ->
                                            FlagConstants.SharedState.STATUS_READY.equals(
                                                    map.get(
                                                            FlagConstants.SharedState
                                                                    .INITIALIZATION_STATUS))));

            for (final Event requestEvent : requests) {
                stubFlagSharedState(
                        requestEvent,
                        SharedStateStatus.SET,
                        flagStatusMap(FlagConstants.SharedState.STATUS_READY));
                assertTrue(extension.readyForEvent(requestEvent));
                extension.handleFlagRequestContent(requestEvent);
            }

            verify(mockExtensionApi, times(5))
                    .dispatch(
                            argThat(
                                    event ->
                                            FlagConstants.EventType.FLAGS.equals(event.getType())
                                                    && FlagConstants.EventSource.RESPONSE_CONTENT
                                                            .equals(event.getSource())
                                                    && event.getEventData() != null
                                                    && !event.getEventData()
                                                            .containsKey(
                                                                    FlagConstants.EventDataKeys
                                                                            .RESPONSE_ERROR)));
        }
    }

    @Test
    public void comp6_flagReadyButEdgeIdentityPendingThenReady_succeedsWithIdentity()
            throws Exception {
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
            when(mockFlagClient.getClientId()).thenReturn("test-client");
            when(mockFlagClient.getFeature(anyString(), any(GetFeatureRequest.class)))
                    .thenReturn(null);

            FlagTestFixtures.getClientManager(extension)
                    .startAsyncInitialization(
                            FlagTestFixtures.validConfigData(), mockSharedStateResolver);

            final Event requestEvent = getFeatureFlagRequest("feature-a");
            stubConfigurationSharedState(requestEvent, SharedStateStatus.SET, validConfigData());
            stubFlagSharedState(
                    requestEvent,
                    SharedStateStatus.SET,
                    flagStatusMap(FlagConstants.SharedState.STATUS_READY));
            stubEdgeIdentitySharedState(requestEvent, SharedStateStatus.PENDING, null);

            assertFalse(extension.readyForEvent(requestEvent));
            verify(mockExtensionApi, never()).dispatch(any(Event.class));

            stubEdgeIdentityReady(requestEvent);
            assertTrue(extension.readyForEvent(requestEvent));

            extension.handleFlagRequestContent(requestEvent);

            ArgumentCaptor<GetFeatureRequest> captor =
                    ArgumentCaptor.forClass(GetFeatureRequest.class);
            verify(mockFlagClient).getFeature(eq("feature-a"), captor.capture());
            assertNotNull(captor.getValue().getIdentityMap());
            assertEquals(
                    "ecid-ready",
                    captor.getValue()
                            .getIdentityMap()
                            .get("ECID")
                            .get(0)
                            .get(FlagConstants.Edge.IDENTITY_ID));
        }
    }

    // --- Flag request: incomplete Data Collection configuration (T-H5) ---

    @Test
    public void flagRequest_missingRequiredConfig_dispatchesError() {
        Map<String, Object> configData = new HashMap<>();
        configData.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");

        SharedStateResult sharedState = new SharedStateResult(SharedStateStatus.SET, configData);
        when(mockExtensionApi.getSharedState(
                        eq(FlagConstants.Configuration.EXTENSION_NAME),
                        any(Event.class),
                        anyBoolean(),
                        any(SharedStateResolution.class)))
                .thenReturn(sharedState);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, "feature-a");

        Event requestEvent =
                new Event.Builder(
                                "Test",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1))
                .dispatch(
                        argThat(
                                event ->
                                        FlagConstants.EventType.FLAGS.equals(event.getType())
                                                && FlagConstants.EventSource.RESPONSE_CONTENT
                                                        .equals(event.getSource())));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    // --- Flag request: unknown request type ---

    @Test
    public void flagRequest_unknownRequestType_noDispatch() {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(FlagConstants.EventDataKeys.REQUEST_TYPE, "unknowntype");

        Event requestEvent =
                new Event.Builder(
                                "Unknown",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, never()).dispatch(any(Event.class));
    }

    // --- Flag request: missing feature name ---

    @Test
    public void flagRequest_getFeature_missingName_dispatchesError() {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);

        Event requestEvent =
                new Event.Builder(
                                "Test Get Feature",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1))
                .dispatch(
                        argThat(
                                event ->
                                        FlagConstants.EventType.FLAGS.equals(event.getType())
                                                && FlagConstants.EventSource.RESPONSE_CONTENT
                                                        .equals(event.getSource())));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    @Test
    public void flagRequest_isFeatureEnabled_missingName_dispatchesError() {
        initializeExtensionWithClient();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);

        Event requestEvent =
                new Event.Builder(
                                "Test Is Feature Enabled",
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        extension.handleFlagRequestContent(requestEvent);

        verify(mockExtensionApi, times(1))
                .dispatch(
                        argThat(
                                event ->
                                        FlagConstants.EventType.FLAGS.equals(event.getType())
                                                && FlagConstants.EventSource.RESPONSE_CONTENT
                                                        .equals(event.getSource())));
        verify(mockExtensionApi, never())
                .dispatch(argThat(event -> EventType.EDGE.equals(event.getType())));
    }

    // --- Helpers ---

    private static FeatureResult featureResultWithExposure(
            final int featureGroupId,
            final String featureGroupKey,
            final int featureId,
            final String featureKey,
            final String variantId) {
        final AnalyticsParam exposureParam =
                new AnalyticsParam(featureGroupId, featureId, featureKey, variantId);
        return new FeatureResult(featureId, featureKey, featureGroupKey, null, null, exposureParam);
    }

    private void dispatchLifecyclePause() throws FlagClientException {
        Map<String, Object> lifecycleData = new HashMap<>();
        lifecycleData.put(FlagConstants.Lifecycle.ACTION_KEY, FlagConstants.Lifecycle.ACTION_PAUSE);
        Event lifecycleEvent =
                new Event.Builder(
                                "Lifecycle",
                                EventType.GENERIC_LIFECYCLE,
                                EventSource.REQUEST_CONTENT)
                        .setEventData(lifecycleData)
                        .build();
        extension.handleLifecycleEvent(lifecycleEvent);
    }

    private static Event flagApiEvent() {
        return new Event.Builder(
                        "Test Event",
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT)
                .build();
    }

    private static Event isEnabledFlagRequest(final String featureName) {
        final Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureName);
        return new Event.Builder(
                        "Test Is Feature Enabled",
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT)
                .setEventData(eventData)
                .build();
    }

    private static Event getFeatureFlagRequest(final String featureName) {
        final Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureName);
        return new Event.Builder(
                        "Test Get Feature",
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT)
                .setEventData(eventData)
                .build();
    }

    private static Map<String, Object> validConfigData() {
        Map<String, Object> config = new HashMap<>();
        config.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");
        config.put(FlagConstants.Configuration.FLAGS_SANDBOX, "prod");
        config.put(FlagConstants.Configuration.FLAGS_CLIENT_ID, "test-client");
        return config;
    }

    private static Map<String, Object> flagStatusMap(final String status) {
        Map<String, Object> map = new HashMap<>();
        map.put(FlagConstants.SharedState.INITIALIZATION_STATUS, status);
        return map;
    }

    private void stubConfigurationSharedState(
            final Event event, final SharedStateStatus status, final Map<String, Object> value) {
        when(mockExtensionApi.getSharedState(
                        eq(FlagConstants.Configuration.EXTENSION_NAME),
                        eq(event),
                        anyBoolean(),
                        eq(SharedStateResolution.ANY)))
                .thenReturn(new SharedStateResult(status, value));
    }

    private void stubFlagSharedState(
            final Event event, final SharedStateStatus status, final Map<String, Object> value) {
        when(mockExtensionApi.getSharedState(
                        eq(FlagConstants.EXTENSION_NAME),
                        eq(event),
                        anyBoolean(),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(status, value));
    }

    private void stubEdgeIdentityNotRegistered(final Event event) {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(event),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(null);
    }

    private void stubEdgeIdentitySharedState(
            final Event event,
            final SharedStateStatus status,
            @Nullable final Map<String, Object> value) {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(event),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(status, value));
    }

    private void stubEdgeIdentityReady(final Event event) {
        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(FlagConstants.Edge.IDENTITY_ID, "ecid-ready");
        ecidItem.put(FlagConstants.Edge.IDENTITY_PRIMARY, true);
        ecidItem.put("authenticatedState", "ambiguous");

        final Map<String, Object> xdmState = new HashMap<>();
        xdmState.put(FlagConstants.Edge.IDENTITY_MAP, Map.of("ECID", List.of(ecidItem)));
        stubEdgeIdentitySharedState(event, SharedStateStatus.SET, xdmState);
    }
}
