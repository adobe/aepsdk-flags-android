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

package com.adobe.marketing.mobile.flags.engine.models;

/** Immutable analytics parameters for a single evaluated feature. Thread-safe. */
public final class AnalyticsParam {

    private final int featureGroupId;
    private final int featureId;
    private final String featureKey;
    private final String variantId;

    public AnalyticsParam(int featureGroupId, int featureId, String featureKey, String variantId) {
        this.featureGroupId = featureGroupId;
        this.featureId = featureId;
        this.featureKey = featureKey;
        this.variantId = variantId;
    }

    /**
     * Numeric feature group identifier.
     *
     * @return the feature group ID
     */
    public int getFeatureGroupId() {
        return featureGroupId;
    }

    /**
     * Numeric feature identifier.
     *
     * @return the feature ID
     */
    public int getFeatureId() {
        return featureId;
    }

    /**
     * Feature key (name).
     *
     * @return the feature key
     */
    public String getFeatureKey() {
        return featureKey;
    }

    /**
     * Variant identifier from policy bucketing, or {@code null} if no policy was evaluated.
     *
     * @return the variant ID string, or {@code null}
     */
    public String getVariantId() {
        return variantId;
    }
}
