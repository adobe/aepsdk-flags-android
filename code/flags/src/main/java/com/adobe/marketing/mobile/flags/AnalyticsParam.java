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

/** analytics metadata associated with a feature evaluation. */
public final class AnalyticsParam {
    private final int featureGroupId;
    private final int featureId;
    @Nullable private final String variantId;

    public AnalyticsParam(
            final int featureGroupId, final int featureId, @Nullable final String variantId) {
        this.featureGroupId = featureGroupId;
        this.featureId = featureId;
        this.variantId = variantId;
    }

    public int getFeatureGroupId() {
        return featureGroupId;
    }

    public int getFeatureId() {
        return featureId;
    }

    @Nullable public String getVariantId() {
        return variantId;
    }
}
