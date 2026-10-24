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

package com.adobe.marketing.mobile.flags.internal;

import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import java.util.List;
import java.util.Map;

/** Builds {@link GetFeatureRequest} instances for the Flags SDK. */
public final class GetFeatureRequestFactory {

    private GetFeatureRequestFactory() {}

    public static GetFeatureRequest create(
            @Nullable final Map<String, List<String>> context,
            @Nullable final Map<String, List<Map<String, Object>>> identityMap) {
        final GetFeatureRequest.Builder builder = new GetFeatureRequest.Builder();
        if (context != null) {
            builder.context(context);
        }
        if (identityMap != null && !identityMap.isEmpty()) {
            builder.identityMap(identityMap);
        }
        return builder.build();
    }
}
