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
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;

/**
 * Generates exposure event identifiers for the batched exposure pipeline.
 *
 * <p>Produces both queue {@code aggregationKey} values (deduplication within a flush window) and
 * Edge {@code correlationID} values (scope details on dispatch). Standalone features use the same
 * formatted id for both; feature-group features use a per-feature aggregation key while preserving
 * the group-level correlation id for Edge dispatch.
 */
final class ExposureEventIdGenerator {

    private ExposureEventIdGenerator() {}

    /**
     * Returns the queue deduplication key, or {@code null} when the feature is not eligible for
     * exposure dispatch.
     */
    @Nullable static String generateAggregationKey(@Nullable final FeatureResult feature) {
        if (feature == null) {
            return null;
        }

        final AnalyticsParam analytics = feature.getAnalyticsParam();
        if (analytics == null || analytics.getVariantId() == null) {
            return null;
        }

        if (isStandaloneFeature(analytics, feature.getFeatureGroupKey())) {
            return formatCorrelationId(
                    FlagConstants.Edge.FEATURE_ACTIVITY_PREFIX + analytics.getFeatureId(),
                    analytics.getVariantId());
        }

        return formatFeatureGroupAggregationKey(
                analytics.getFeatureGroupId(), analytics.getFeatureId(), analytics.getVariantId());
    }

    /**
     * Returns the Edge {@code correlationID}, or {@code null} when the feature is not eligible for
     * exposure dispatch.
     */
    @Nullable static String generateCorrelationId(@Nullable final FeatureResult feature) {
        if (feature == null) {
            return null;
        }

        final AnalyticsParam analytics = feature.getAnalyticsParam();
        if (analytics == null || analytics.getVariantId() == null) {
            return null;
        }

        final String activityId = resolveCorrelationActivityId(feature, analytics);
        return formatCorrelationId(activityId, analytics.getVariantId());
    }

    static boolean isExposureEligible(@Nullable final FeatureResult feature) {
        if (feature == null) {
            return false;
        }

        final AnalyticsParam analytics = feature.getAnalyticsParam();
        return analytics != null && analytics.getVariantId() != null;
    }

    static boolean isStandaloneFeature(
            @NonNull final AnalyticsParam analytics, @Nullable final String featureGroupKey) {
        return analytics.getFeatureGroupId()
                        == FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_ID
                || FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY.equals(featureGroupKey);
    }

    @NonNull static String formatCorrelationId(
            @NonNull final String activityId, @NonNull final String variantId) {
        return activityId + FlagConstants.Edge.CORRELATION_ID_SEPARATOR + variantId;
    }

    @NonNull private static String formatFeatureGroupAggregationKey(
            final int featureGroupId, final int featureId, @NonNull final String variantId) {
        return FlagConstants.Edge.FEATURE_GROUP_ACTIVITY_PREFIX
                + featureGroupId
                + FlagConstants.Edge.CORRELATION_ID_SEPARATOR
                + featureId
                + FlagConstants.Edge.CORRELATION_ID_SEPARATOR
                + variantId;
    }

    @NonNull static String resolveCorrelationActivityId(
            @NonNull final FeatureResult feature, @NonNull final AnalyticsParam analytics) {
        if (isStandaloneFeature(analytics, feature.getFeatureGroupKey())) {
            return FlagConstants.Edge.FEATURE_ACTIVITY_PREFIX + analytics.getFeatureId();
        }
        return FlagConstants.Edge.FEATURE_GROUP_ACTIVITY_PREFIX + analytics.getFeatureGroupId();
    }
}
