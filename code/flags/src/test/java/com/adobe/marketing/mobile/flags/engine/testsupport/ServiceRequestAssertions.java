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

package com.adobe.marketing.mobile.flags.engine.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.constants.SdkVersion;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.RecordedRequest;

/** Shared HTTP request assertions for integration and cache tests. */
public final class ServiceRequestAssertions {

    private ServiceRequestAssertions() {}

    public static void assertFeatureRequest(
            RecordedRequest request, String clientId, String expectedContextVersion) {
        assertFeatureRequest(request, clientId, expectedContextVersion, null);
    }

    public static void assertFeatureRequest(
            RecordedRequest request,
            String clientId,
            String expectedContextVersion,
            String expectedIfNoneMatch) {
        HttpUrl url = request.getRequestUrl();
        assertNotNull(url);
        assertEquals(Constants.FLAGS_FEATURE_PATH, url.encodedPath());
        assertEquals(clientId, url.queryParameter(Constants.QUERY_CLIENT_ID));
        assertTrue(
                url.queryParameterNames().contains(Constants.QUERY_SDK_VERSION),
                "sdkVersion query param must always be present");
        String expectedSdkVersion = SdkVersion.get();
        if (expectedSdkVersion == null || expectedSdkVersion.isEmpty()) {
            assertNull(url.queryParameter(Constants.QUERY_SDK_VERSION));
        } else {
            assertEquals(expectedSdkVersion, url.queryParameter(Constants.QUERY_SDK_VERSION));
        }
        if (expectedContextVersion == null) {
            assertNull(url.queryParameter(Constants.QUERY_CONTEXT_VERSION));
        } else {
            assertEquals(
                    expectedContextVersion, url.queryParameter(Constants.QUERY_CONTEXT_VERSION));
        }
        if (expectedIfNoneMatch == null) {
            assertNull(request.getHeader(Constants.HEADER_IF_NONE_MATCH));
        } else {
            assertEquals(expectedIfNoneMatch, request.getHeader(Constants.HEADER_IF_NONE_MATCH));
        }
    }
}
