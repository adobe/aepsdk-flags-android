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

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.constants.SdkVersion;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.models.FlagApiResponse;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APIProxyEdgeResponseTest {

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
    @DisplayName("200 with body: EdgeResponse parsed, pollInterval from ttl, headers and query set")
    void successResponseParsesEdgeBody() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-200\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));

        FlagApiResponse response = apiProxy.getFeatures("test-client", null, null);

        assertTrue(response.isChanged());
        assertEquals("\"etag-200\"", response.getEtag());
        assertNotNull(response.getEdgeResponse());
        assertEquals(120, response.getPollInterval());
        assertEquals(1, response.getFeaturesResponses().length);
        assertEquals("test-context-v1", response.getEdgeResponse().getContextVersion());

        RecordedRequest request = mockServer.takeRequest();
        assertServiceRequest(request, "test-client", null, null);
    }

    @Test
    @DisplayName("304 Not Modified returns isChanged false without parsed body")
    void notModifiedResponse() throws Exception {
        mockServer.enqueue(
                new MockResponse().setResponseCode(304).setHeader("ETag", "\"etag-unchanged\""));

        FlagApiResponse response =
                apiProxy.getFeatures("test-client", "test-context-v1", "\"etag-unchanged\"");

        assertFalse(response.isChanged());
        assertEquals("\"etag-unchanged\"", response.getEtag());
        assertNull(response.getEdgeResponse());
        assertNull(response.getFeaturesResponses());
        assertNull(response.getPollInterval());

        RecordedRequest request = mockServer.takeRequest();
        assertServiceRequest(request, "test-client", "test-context-v1", "\"etag-unchanged\"");
    }

    @Test
    @DisplayName("Subsequent request sends contextVersion and If-None-Match")
    void subsequentRequestSendsContextVersionAndEtag() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-1\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-2\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));

        apiProxy.getFeatures("test-client", null, null);
        mockServer.takeRequest();

        apiProxy.getFeatures("test-client", "test-context-v1", "\"etag-1\"");
        RecordedRequest second = mockServer.takeRequest();

        assertServiceRequest(second, "test-client", "test-context-v1", "\"etag-1\"");
    }

    @Test
    @DisplayName("200 with unparseable body throws FlagClientException")
    void unparseableBodyThrows() {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-bad\"")
                        .setBody("not json"));

        FlagClientException ex =
                assertThrows(
                        FlagClientException.class,
                        () -> apiProxy.getFeatures("test-client", null, null));
        assertTrue(ex.getMessage().contains("Failed to parse features response"));
    }

    private static void assertServiceRequest(
            RecordedRequest request, String clientId, String contextVersion, String ifNoneMatch) {
        HttpUrl url = request.getRequestUrl();
        assertNotNull(url);
        assertEquals(Constants.FLAGS_FEATURE_PATH, url.encodedPath());
        assertEquals(clientId, url.queryParameter(Constants.QUERY_CLIENT_ID));
        assertTrue(url.queryParameterNames().contains(Constants.QUERY_SDK_VERSION));
        String expectedSdkVersion = SdkVersion.get();
        if (expectedSdkVersion == null || expectedSdkVersion.isEmpty()) {
            assertNull(url.queryParameter(Constants.QUERY_SDK_VERSION));
        } else {
            assertEquals(expectedSdkVersion, url.queryParameter(Constants.QUERY_SDK_VERSION));
        }
        if (contextVersion == null) {
            assertNull(url.queryParameter(Constants.QUERY_CONTEXT_VERSION));
        } else {
            assertEquals(contextVersion, url.queryParameter(Constants.QUERY_CONTEXT_VERSION));
        }
        if (ifNoneMatch == null) {
            assertNull(request.getHeader(Constants.HEADER_IF_NONE_MATCH));
        } else {
            assertEquals(ifNoneMatch, request.getHeader(Constants.HEADER_IF_NONE_MATCH));
        }
        assertFalse(request.getPath().contains("metadata"));
        assertEquals(Constants.ACCEPT_JSON, request.getHeader(Constants.HEADER_ACCEPT));
        assertEquals(Constants.ENCODING_GZIP, request.getHeader(Constants.HEADER_ACCEPT_ENCODING));
        assertEquals(IMS_ORG, request.getHeader(Constants.HEADER_IMS_ORG));
        assertEquals(SANDBOX, request.getHeader(Constants.HEADER_SANDBOX_NAME));
    }

    private String baseUrl() {
        return mockServer.url("/").toString().replaceAll("/$", "");
    }
}
