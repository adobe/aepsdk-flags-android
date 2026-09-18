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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureServiceUrlsTest {

    @Test
    @DisplayName("baseUrlFromEdgeDomain prepends HTTPS scheme")
    void baseUrlFromEdgeDomain() {
        assertEquals(
                Constants.HTTPS_SCHEME + "your-edge-domain.example.com",
                FeatureServiceUrls.baseUrlFromEdgeDomain("your-edge-domain.example.com"));
    }
}
