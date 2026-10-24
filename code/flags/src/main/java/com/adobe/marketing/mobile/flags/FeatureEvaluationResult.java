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

import androidx.annotation.Nullable;

/** Immutable feature payload returned by the getFeature API. */
public final class FeatureEvaluationResult {
    private final int id;
    private final String key;
    @Nullable private final String featureGroupKey;
    @Nullable private final String meta;
    @Nullable private final AnalyticsParam analyticsParam;

    public FeatureEvaluationResult(
            final int id,
            final String key,
            @Nullable final String featureGroupKey,
            @Nullable final String meta,
            @Nullable final AnalyticsParam analyticsParam) {
        this.id = id;
        this.key = key;
        this.featureGroupKey = featureGroupKey;
        this.meta = meta;
        this.analyticsParam = analyticsParam;
    }

    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    @Nullable public String getFeatureGroupKey() {
        return featureGroupKey;
    }

    @Nullable public String getMeta() {
        return meta;
    }

    @Nullable public AnalyticsParam getAnalyticsParam() {
        return analyticsParam;
    }
}
