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
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;

/**
 * Snapshot of an aggregated feature exposure ready for Edge dispatch.
 *
 * <p>{@code displayCount} reflects how many evaluations shared the same {@code aggregationKey} in
 * the flush window. {@code lastEvaluatedAtMillis} is the timestamp of the most recent evaluation
 * for that aggregation key.
 *
 * <p>Edge {@code correlationID} is derived from the stored {@link FeatureResult} at dispatch time
 * (via {@link ExposureEventIdGenerator}), not from {@code aggregationKey}.
 *
 * <p>Profile identity on exposure events is merged by Edge + Edge Identity when the Edge extension
 * processes the dispatched event; this snapshot does not carry identity fields.
 *
 * <p>The {@link FeatureResult} reference is captured at enqueue time. Callers must not mutate the
 * underlying SDK object after enqueue.
 */
final class AggregatedExposureEvent {

    @NonNull private final String aggregationKey;

    @NonNull private final FeatureResult feature;

    private final long lastEvaluatedAtMillis;

    private final int displayCount;

    AggregatedExposureEvent(
            @NonNull final String aggregationKey,
            @NonNull final FeatureResult feature,
            final long lastEvaluatedAtMillis,
            final int displayCount) {
        this.aggregationKey = aggregationKey;
        this.feature = feature;
        this.lastEvaluatedAtMillis = lastEvaluatedAtMillis;
        this.displayCount = displayCount;
    }

    @NonNull String getAggregationKey() {
        return aggregationKey;
    }

    @NonNull FeatureResult getFeature() {
        return feature;
    }

    long getLastEvaluatedAtMillis() {
        return lastEvaluatedAtMillis;
    }

    int getDisplayCount() {
        return displayCount;
    }
}
