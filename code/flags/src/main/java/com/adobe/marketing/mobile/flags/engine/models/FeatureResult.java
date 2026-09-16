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

import com.adobe.marketing.mobile.flags.engine.FlagClient;

/**
 * Immutable representation of an evaluated feature flag.
 *
 * <p>Thread-safe. Returned by {@link FlagClient} feature evaluation methods.
 */
public final class FeatureResult {

    private final int id;
    private final String key;
    private final String featureGroupKey;
    private final Object value;
    private final String meta;
    private final AnalyticsParam analyticsParam;

    public FeatureResult(int id, String key, String featureGroupKey, Object value, String meta) {
        this(id, key, featureGroupKey, value, meta, null);
    }

    public FeatureResult(
            int id,
            String key,
            String featureGroupKey,
            Object value,
            String meta,
            AnalyticsParam analyticsParam) {
        this.id = id;
        this.key = key;
        this.featureGroupKey = featureGroupKey;
        this.value = value;
        this.meta = meta;
        this.analyticsParam = analyticsParam;
    }

    /**
     * Feature flag numeric identifier.
     *
     * @return the feature ID
     */
    public int getId() {
        return id;
    }

    /**
     * Feature flag key (e.g. {@code "dark-mode"}).
     *
     * @return the feature key
     */
    public String getKey() {
        return key;
    }

    /**
     * Key of the feature group this feature belongs to, or {@code null} if not available.
     *
     * @return the feature group key, or {@code null}
     */
    public String getFeatureGroupKey() {
        return featureGroupKey;
    }

    /**
     * The evaluated value / variant for this feature, or {@code null}. The runtime type depends on
     * the flag definition (String, Boolean, Number, or JSON string).
     *
     * @return the evaluated value, or {@code null}
     */
    public Object getValue() {
        return value;
    }

    /**
     * Opaque metadata string associated with the feature, or {@code null}.
     *
     * @return metadata string, or {@code null}
     */
    public String getMeta() {
        return meta;
    }

    /**
     * Analytics parameters associated with this feature evaluation, or {@code null}.
     *
     * @return the analytics param, or {@code null}
     */
    public AnalyticsParam getAnalyticsParam() {
        return analyticsParam;
    }
}
