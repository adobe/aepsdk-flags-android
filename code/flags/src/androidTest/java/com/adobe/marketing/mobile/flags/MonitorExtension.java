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
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Functional-test extension that intercepts dispatched events for assertion. */
final class MonitorExtension extends Extension {

    private static final String SELF_TAG = "MonitorExtension";

    private static final Map<EventSpec, List<Event>> receivedEvents = new HashMap<>();
    private static final Map<EventSpec, ADBCountDownLatch> expectedEvents = new HashMap<>();

    protected MonitorExtension(final ExtensionApi extensionApi) {
        super(extensionApi);
    }

    @NonNull @Override
    protected String getName() {
        return "MonitorExtension";
    }

    static void registerExtension() {
        MobileCore.registerExtensions(Collections.singletonList(MonitorExtension.class), null);
    }

    @Override
    protected void onRegistered() {
        getApi().registerEventListener(
                        EventType.WILDCARD, EventSource.WILDCARD, this::wildcardProcessor);
    }

    static void setExpectedEvent(final String type, final String source, final int count) {
        EventSpec eventSpec = new EventSpec(source, type);
        expectedEvents.put(eventSpec, new ADBCountDownLatch(count));
    }

    static Map<EventSpec, ADBCountDownLatch> getExpectedEvents() {
        return expectedEvents;
    }

    static Map<EventSpec, List<Event>> getReceivedEvents() {
        return receivedEvents;
    }

    static void reset() {
        Log.trace(
                FlagFunctionalTestConstants.LOG_TAG,
                SELF_TAG,
                "Reset expected and received events.");
        receivedEvents.clear();
        expectedEvents.clear();
    }

    public void wildcardProcessor(final Event event) {
        if (FlagFunctionalTestConstants.MonitorEventType.MONITOR.equalsIgnoreCase(
                event.getType())) {
            if (FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_REQUEST
                    .equalsIgnoreCase(event.getSource())) {
                processSharedStateRequest(event);
            } else if (FlagFunctionalTestConstants.MonitorEventSource.XDM_SHARED_STATE_REQUEST
                    .equalsIgnoreCase(event.getSource())) {
                processXdmSharedStateRequest(event);
            } else if (FlagFunctionalTestConstants.MonitorEventSource.UNREGISTER.equalsIgnoreCase(
                    event.getSource())) {
                processUnregisterRequest(event);
            }
            return;
        }

        EventSpec eventSpec = new EventSpec(event.getSource(), event.getType());

        Log.debug(
                FlagFunctionalTestConstants.LOG_TAG,
                SELF_TAG,
                "Received and processing event " + eventSpec);

        if (!receivedEvents.containsKey(eventSpec)) {
            receivedEvents.put(eventSpec, new ArrayList<>());
        }

        receivedEvents.get(eventSpec).add(event);

        if (expectedEvents.containsKey(eventSpec)) {
            expectedEvents.get(eventSpec).countDown();
        }
    }

    private void processUnregisterRequest(final Event event) {
        Log.debug(
                FlagFunctionalTestConstants.LOG_TAG,
                SELF_TAG,
                "Unregistering the Monitor Extension.");
        getApi().unregisterExtension();
    }

    private void processSharedStateRequest(final Event event) {
        Map<String, Object> eventData = event.getEventData();

        if (eventData == null) {
            return;
        }

        String stateOwner =
                DataReader.optString(
                        eventData,
                        FlagFunctionalTestConstants.MonitorEventDataKeys.STATE_OWNER,
                        null);

        if (stateOwner == null) {
            return;
        }

        SharedStateResult sharedState =
                getApi().getSharedState(stateOwner, event, false, SharedStateResolution.LAST_SET);

        Event responseEvent =
                new Event.Builder(
                                "Get Shared State Response",
                                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                                FlagFunctionalTestConstants.MonitorEventSource
                                        .SHARED_STATE_RESPONSE)
                        .setEventData(sharedState == null ? null : sharedState.getValue())
                        .inResponseToEvent(event)
                        .build();

        MobileCore.dispatchEvent(responseEvent);
    }

    private void processXdmSharedStateRequest(final Event event) {
        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null) {
            return;
        }

        final String stateOwner =
                DataReader.optString(
                        eventData,
                        FlagFunctionalTestConstants.MonitorEventDataKeys.STATE_OWNER,
                        null);
        if (stateOwner == null) {
            return;
        }

        final SharedStateResult sharedState =
                getApi().getXDMSharedState(
                                stateOwner, event, false, SharedStateResolution.LAST_SET);

        final Map<String, Object> responseData = new HashMap<>();
        if (sharedState != null) {
            responseData.put(
                    FlagFunctionalTestConstants.MonitorEventDataKeys.STATUS,
                    sharedState.getStatus().name());
            if (sharedState.getValue() != null) {
                responseData.put(
                        FlagFunctionalTestConstants.MonitorEventDataKeys.VALUE,
                        sharedState.getValue());
            }
        }

        final Event responseEvent =
                new Event.Builder(
                                "Get XDM Shared State Response",
                                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                                FlagFunctionalTestConstants.MonitorEventSource
                                        .SHARED_STATE_RESPONSE)
                        .setEventData(responseData)
                        .inResponseToEvent(event)
                        .build();

        MobileCore.dispatchEvent(responseEvent);
    }

    static final class EventSpec {
        final String source;
        final String type;

        EventSpec(final String source, final String type) {
            if (source == null || source.isEmpty()) {
                throw new IllegalArgumentException("Event Source cannot be null or empty.");
            }
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("Event Type cannot be null or empty.");
            }
            this.source = source.toLowerCase();
            this.type = type.toLowerCase();
        }

        @NonNull @Override
        public String toString() {
            return "type '" + type + "' and source '" + source + "'";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EventSpec eventSpec = (EventSpec) o;
            return Objects.equals(source, eventSpec.source) && Objects.equals(type, eventSpec.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, type);
        }
    }
}
