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

package com.adobe.marketing.mobile.flags.engine.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.constants.SdkVersion;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.models.FlagApiResponse;
import com.adobe.marketing.mobile.flags.engine.testsupport.ServiceRequestAssertions;
import java.lang.reflect.Method;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APIProxySdkVersionQueryParamTest {

    private static final String IMS_ORG = "test-org";
    private static final String SANDBOX = "test-sandbox";

    private MockWebServer mockServer;
    private APIProxy apiProxy;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        apiProxy = new APIProxy(baseUrl(), IMS_ORG, SANDBOX, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        apiProxy.shutdown();
        mockServer.shutdown();
    }

    @Test
    @DisplayName("sdkVersion query param is always present on feature requests")
    void sdkVersionQueryParamAlwaysPresent() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-1\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));

        FlagApiResponse response = apiProxy.getFeatures("client-a", null, null);
        assertTrue(response.isChanged());

        RecordedRequest request = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(request, "client-a", null);
    }

    @Test
    @DisplayName("buildFeatureRequestUrl always includes sdkVersion query key")
    void buildFeatureRequestUrlIncludesSdkVersionKey() throws Exception {
        Method method =
                APIProxy.class.getDeclaredMethod(
                        "buildFeatureRequestUrl", String.class, String.class);
        method.setAccessible(true);
        Object url = method.invoke(apiProxy, "client-a", "ctx-v1");

        Method queryNames = url.getClass().getMethod("queryParameterNames");
        @SuppressWarnings("unchecked")
        java.util.Set<String> names = (java.util.Set<String>) queryNames.invoke(url);
        assertTrue(names.contains(Constants.QUERY_SDK_VERSION));

        Method queryParam = url.getClass().getMethod("queryParameter", String.class);
        String sdkVersion = SdkVersion.get();
        if (sdkVersion == null || sdkVersion.isEmpty()) {
            assertTrue(queryParam.invoke(url, Constants.QUERY_SDK_VERSION) == null);
        } else {
            assertNotNull(queryParam.invoke(url, Constants.QUERY_SDK_VERSION));
        }
    }

    private String baseUrl() {
        return mockServer.url("/").toString().replaceAll("/$", "");
    }
}
