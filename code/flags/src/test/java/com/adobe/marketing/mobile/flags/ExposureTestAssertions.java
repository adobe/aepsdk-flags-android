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

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.List;
import java.util.Map;

/** Shared assertions helpers for exposure Edge event payloads in tests. */
final class ExposureTestAssertions {

    private ExposureTestAssertions() {}

    static boolean isPropositionDisplayEdgeEvent(final Event event) {
        if (!EventType.EDGE.equals(event.getType())
                || !EventSource.REQUEST_CONTENT.equals(event.getSource())) {
            return false;
        }
        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> xdm = (Map<String, Object>) eventData.get(FlagConstants.Edge.XDM);
        return xdm != null
                && FlagConstants.Edge.EVENT_TYPE_PROPOSITION_DISPLAY.equals(
                        xdm.get(FlagConstants.Edge.EVENT_TYPE));
    }

    static Event findEdgeEvent(final List<Event> events) {
        for (final Event event : events) {
            if (EventType.EDGE.equals(event.getType())) {
                return event;
            }
        }
        return null;
    }

    static int countEdgeEvents(final List<Event> events) {
        int count = 0;
        for (final Event event : events) {
            if (isPropositionDisplayEdgeEvent(event)) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    static int extractDisplayCount(final Event edgeEvent) {
        final Map<String, Object> xdm =
                (Map<String, Object>) edgeEvent.getEventData().get(FlagConstants.Edge.XDM);
        final Map<String, Object> experience =
                (Map<String, Object>) xdm.get(FlagConstants.Edge.EXPERIENCE);
        final Map<String, Object> decisioning =
                (Map<String, Object>) experience.get(FlagConstants.Edge.DECISIONING);
        final Map<String, Object> propositionEventType =
                (Map<String, Object>) decisioning.get(FlagConstants.Edge.PROPOSITION_EVENT_TYPE);
        return ((Number) propositionEventType.get(FlagConstants.Edge.DISPLAY)).intValue();
    }

    @SuppressWarnings("unchecked")
    static String extractCorrelationId(final Event edgeEvent) {
        final Map<String, Object> xdm =
                (Map<String, Object>) edgeEvent.getEventData().get(FlagConstants.Edge.XDM);
        final Map<String, Object> experience =
                (Map<String, Object>) xdm.get(FlagConstants.Edge.EXPERIENCE);
        final Map<String, Object> decisioning =
                (Map<String, Object>) experience.get(FlagConstants.Edge.DECISIONING);
        final List<Map<String, Object>> propositions =
                (List<Map<String, Object>>) decisioning.get(FlagConstants.Edge.PROPOSITIONS);
        final Map<String, Object> scopeDetails =
                (Map<String, Object>) propositions.get(0).get(FlagConstants.Edge.SCOPE_DETAILS);
        return (String) scopeDetails.get(FlagConstants.Edge.CORRELATION_ID);
    }

    @SuppressWarnings("unchecked")
    static String extractTimestamp(final Event edgeEvent) {
        final Map<String, Object> xdm =
                (Map<String, Object>) edgeEvent.getEventData().get(FlagConstants.Edge.XDM);
        return xdm == null ? null : (String) xdm.get(FlagConstants.Edge.TIMESTAMP);
    }

    @SuppressWarnings("unchecked")
    static boolean hasFeatureGroupItems(final Event edgeEvent) {
        final Map<String, Object> xdm =
                (Map<String, Object>) edgeEvent.getEventData().get(FlagConstants.Edge.XDM);
        final Map<String, Object> experience =
                (Map<String, Object>) xdm.get(FlagConstants.Edge.EXPERIENCE);
        final Map<String, Object> decisioning =
                (Map<String, Object>) experience.get(FlagConstants.Edge.DECISIONING);
        final List<Map<String, Object>> propositions =
                (List<Map<String, Object>>) decisioning.get(FlagConstants.Edge.PROPOSITIONS);
        return propositions.get(0).containsKey(FlagConstants.Edge.ITEMS);
    }
}
