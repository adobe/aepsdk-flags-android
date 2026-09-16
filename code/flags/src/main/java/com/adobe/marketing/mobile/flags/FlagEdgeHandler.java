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
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.TimeUtils;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and dispatches feature evaluation exposure events to Edge.
 *
 * <p>Exposure payloads contain decisioning XDM only. Profile identity is merged by Edge + Edge
 * Identity when the Edge extension processes each dispatched event.
 */
final class FlagEdgeHandler {

    private static final String SELF_TAG = "FlagEdgeHandler";

    private static final int ZERO_MILLIS_UTC_SUFFIX_LENGTH = 5;

    private FlagEdgeHandler() {}

    /**
     * Dispatches a batch of aggregated exposure events.
     *
     * <p>Each event is dispatched independently; a failure for one event does not prevent dispatch
     * of the remaining events in the batch.
     */
    static void dispatchExposureEvents(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final List<AggregatedExposureEvent> events) {
        for (final AggregatedExposureEvent event : events) {
            try {
                dispatchExposureEvent(extensionApi, event);
            } catch (Exception e) {
                Log.warning(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "Failed to dispatch aggregated exposure event for aggregationKey=%s: %s",
                        event.getAggregationKey(),
                        e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatches a single aggregated exposure event.
     *
     * @param extensionApi the {@link ExtensionApi} used to dispatch events
     * @param event aggregated exposure snapshot from the exposure queue
     */
    static void dispatchExposureEvent(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final AggregatedExposureEvent event) {
        if (!ExposureEventIdGenerator.isExposureEligible(event.getFeature())
                || event.getDisplayCount() <= 0) {
            return;
        }

        final FeatureResult feature = event.getFeature();
        final AnalyticsParam analytics = feature.getAnalyticsParam();
        final Map<String, Object> xdm =
                buildDecisioningXdm(feature, analytics, event.getDisplayCount());
        dispatchEdgeEvent(extensionApi, xdm, event.getLastEvaluatedAtMillis());
    }

    @NonNull private static Map<String, Object> buildDecisioningXdm(
            @NonNull final FeatureResult feature,
            @NonNull final AnalyticsParam analytics,
            final int displayCount) {
        final Map<String, Object> scopeDetails = buildScopeDetails(feature, analytics);

        final Map<String, Object> proposition = new HashMap<>(2);
        proposition.put(FlagConstants.Edge.SCOPE_DETAILS, scopeDetails);
        if (!ExposureEventIdGenerator.isStandaloneFeature(
                analytics, feature.getFeatureGroupKey())) {
            proposition.put(FlagConstants.Edge.ITEMS, buildFeatureGroupItems(analytics));
        }

        final Map<String, Object> propositionEventType = new HashMap<>(1);
        propositionEventType.put(FlagConstants.Edge.DISPLAY, displayCount);

        final Map<String, Object> decisioning = new HashMap<>(2);
        decisioning.put(FlagConstants.Edge.PROPOSITION_EVENT_TYPE, propositionEventType);
        decisioning.put(FlagConstants.Edge.PROPOSITIONS, Collections.singletonList(proposition));

        final Map<String, Object> experience = new HashMap<>(1);
        experience.put(FlagConstants.Edge.DECISIONING, decisioning);

        final Map<String, Object> xdm = new HashMap<>(2);
        xdm.put(FlagConstants.Edge.EVENT_TYPE, FlagConstants.Edge.EVENT_TYPE_PROPOSITION_DISPLAY);
        xdm.put(FlagConstants.Edge.EXPERIENCE, experience);
        return xdm;
    }

    @NonNull private static Map<String, Object> buildScopeDetails(
            @NonNull final FeatureResult feature, @NonNull final AnalyticsParam analytics) {
        final String featureGroupKey = feature.getFeatureGroupKey();
        final boolean standalone =
                ExposureEventIdGenerator.isStandaloneFeature(analytics, featureGroupKey);
        final String variantId = analytics.getVariantId();

        // Delegate to the single source of truth for activity ID formatting so
        // FlagEdgeHandler and ExposureEventIdGenerator never diverge.
        final String activityId =
                ExposureEventIdGenerator.resolveCorrelationActivityId(feature, analytics);
        final String activityName = standalone ? analytics.getFeatureKey() : featureGroupKey;
        final String entityType =
                standalone
                        ? FlagConstants.Edge.ENTITY_TYPE_FEATURE
                        : FlagConstants.Edge.ENTITY_TYPE_FEATURE_GROUP;

        final Map<String, Object> activity = new HashMap<>(2);
        activity.put(FlagConstants.Edge.IDENTITY_ID, activityId);
        activity.put(FlagConstants.Edge.NAME, activityName);

        final Map<String, Object> scopeExperience = new HashMap<>(1);
        scopeExperience.put(
                FlagConstants.Edge.IDENTITY_ID, FlagConstants.Edge.VARIANT_PREFIX + variantId);

        final Map<String, Object> strategy = new HashMap<>(1);
        strategy.put(FlagConstants.Edge.ALGORITHM_ID, FlagConstants.Edge.ALGORITHM_MURMUR);

        final Map<String, Object> characteristics = new HashMap<>(1);
        characteristics.put(FlagConstants.Edge.ENTITY_TYPE, entityType);

        final Map<String, Object> scopeDetails = new HashMap<>(7);
        scopeDetails.put(
                FlagConstants.Edge.DECISION_PROVIDER, FlagConstants.Edge.DECISION_PROVIDER_FLAGS);
        scopeDetails.put(
                FlagConstants.Edge.CORRELATION_ID,
                ExposureEventIdGenerator.formatCorrelationId(activityId, variantId));
        scopeDetails.put(FlagConstants.Edge.ACTIVITY, activity);
        scopeDetails.put(FlagConstants.Edge.SCOPE_EXPERIENCE, scopeExperience);
        scopeDetails.put(FlagConstants.Edge.STRATEGIES, Collections.singletonList(strategy));
        scopeDetails.put(FlagConstants.Edge.CHARACTERISTICS, characteristics);
        return scopeDetails;
    }

    @NonNull private static List<Map<String, Object>> buildFeatureGroupItems(
            @NonNull final AnalyticsParam analytics) {
        final Map<String, Object> item = new HashMap<>(2);
        item.put(FlagConstants.Edge.IDENTITY_ID, String.valueOf(analytics.getFeatureId()));
        item.put(FlagConstants.Edge.NAME, analytics.getFeatureKey());
        return Collections.singletonList(item);
    }

    @NonNull private static String formatTimestamp(final long evaluatedAtMillis) {
        final String formatted =
                TimeUtils.getISO8601UTCDateWithMilliseconds(new Date(evaluatedAtMillis));
        if (formatted.endsWith(".000Z")) {
            return formatted.substring(0, formatted.length() - ZERO_MILLIS_UTC_SUFFIX_LENGTH) + "Z";
        }
        return formatted;
    }

    private static Map<String, Object> buildEdgeEventData(
            @NonNull final Map<String, Object> xdm, @Nullable final Long evaluatedAtMillis) {

        final Map<String, Object> xdmPayload = new HashMap<>(xdm);

        if (evaluatedAtMillis != null) {
            xdmPayload.put(FlagConstants.Edge.TIMESTAMP, formatTimestamp(evaluatedAtMillis));
        }

        final Map<String, Object> eventData = new HashMap<>(2);
        eventData.put(FlagConstants.Edge.XDM, xdmPayload);

        final Map<String, Object> request = new HashMap<>(1);
        request.put(FlagConstants.Edge.REQUEST_PATH, FlagConstants.Edge.COLLECT_PATH);
        eventData.put(FlagConstants.Edge.REQUEST, request);

        return eventData;
    }

    private static void dispatchEdgeEvent(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final Map<String, Object> xdm,
            @Nullable final Long evaluatedAtMillis) {
        try {
            final Map<String, Object> eventData = buildEdgeEventData(xdm, evaluatedAtMillis);

            final Event edgeEvent =
                    new Event.Builder(
                                    FlagConstants.EventNames.EDGE_FEATURE_EXPOSURE_REQUEST,
                                    EventType.EDGE,
                                    EventSource.REQUEST_CONTENT)
                            .setEventData(eventData)
                            .build();

            extensionApi.dispatch(edgeEvent);
        } catch (Exception e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to dispatch feature exposure event: %s",
                    e.getLocalizedMessage());
        }
    }
}
