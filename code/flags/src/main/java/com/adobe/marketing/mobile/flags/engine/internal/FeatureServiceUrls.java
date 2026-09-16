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

package com.adobe.marketing.mobile.flags.engine.internal;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;

/** Resolves feature-service HTTP URLs from a host-only edge domain. */
public final class FeatureServiceUrls {

    private FeatureServiceUrls() {}

    /**
     * Build the HTTPS base URL used for feature requests.
     *
     * @param edgeDomain validated host-only edge domain
     * @return {@code https://}{@code edgeDomain} with no trailing slash
     */
    public static String baseUrlFromEdgeDomain(String edgeDomain) {
        return Constants.HTTPS_SCHEME + edgeDomain;
    }
}
