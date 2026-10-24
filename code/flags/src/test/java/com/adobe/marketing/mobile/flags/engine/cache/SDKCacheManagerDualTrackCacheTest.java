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

package com.adobe.marketing.mobile.flags.engine.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.constants.SdkVersion;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SDKCacheManagerDualTrackCacheTest {

    private static final String CLIENT_ID = "test-client";
    private static final String IMS_ORG = "test-org";
    private static final String SANDBOX = "test-sandbox";

    private MockWebServer mockServer;
    private SDKCacheManager cacheManager;
    private AtomicInteger metadataCallbackCount;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        cacheManager = new SDKCacheManager(new PolicyCache());
        metadataCallbackCount = new AtomicInteger();
        cacheManager.setOnMetadataUpdated(metadata -> metadataCallbackCount.incrementAndGet());
    }

    @AfterEach
    void tearDown() throws Exception {
        cacheManager.shutdown();
        mockServer.shutdown();
    }

    @Test
    @DisplayName("Init issues one feature request and populates both caches")
    void initSingleRequestPopulatesBothTracks() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");

        cacheManager.configure(CLIENT_ID, createApiProxy());

        assertEquals(1, mockServer.getRequestCount());
        RecordedRequest request = mockServer.takeRequest();
        assertServiceRequest(request, null, null);

        assertTrue(cacheManager.getClientCache().hasFeatures(CLIENT_ID));
        assertTrue(cacheManager.getClientCache().hasMetadata());
        assertEquals(1, metadataCallbackCount.get());
        assertEquals("ctx-v1", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(
                "STRING",
                cacheManager.getClientCache().getMetadata().getFieldDataTypeCache().get("country"));
    }

    @Test
    @DisplayName("Poll 200 without contexts updates features and ETag but not metadata callback")
    void poll200WithoutContextsUpdatesFeaturesOnly() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        enqueue200(featuresOnlyBody("ctx-v1", "feature-b"), "\"etag-2\"");
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        RecordedRequest second = mockServer.takeRequest();
        assertServiceRequest(second, "ctx-v1", "\"etag-1\"");

        assertEquals("\"etag-2\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));
        assertEquals("ctx-v1", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(1, metadataCallbackCount.get());
    }

    @Test
    @DisplayName("Poll 200 with new features and contexts updates both tracks")
    void poll200NewFeaturesAndContextsUpdatesBothTracks() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        String body =
                EdgeResponseJsonFixtures.body(
                        90,
                        "ctx-v2",
                        "[{\"id\":\"age\",\"type\":\"INTEGER\"}]",
                        EdgeResponseJsonFixtures.featureGroup(
                                1001,
                                "default_feature_group",
                                EdgeResponseJsonFixtures.feature(1001, "feature-b", "")));
        enqueue200(body, "\"etag-2\"");
        cacheManager.refreshClientCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> metadataCallbackCount.get() == 2);

        RecordedRequest second = mockServer.takeRequest();
        assertServiceRequest(second, "ctx-v1", "\"etag-1\"");

        assertEquals("\"etag-2\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));
        assertEquals("ctx-v2", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(
                "INTEGER",
                cacheManager.getClientCache().getMetadata().getFieldDataTypeCache().get("age"));
        assertEquals(2, metadataCallbackCount.get());
    }

    @Test
    @DisplayName("Poll 200 with contexts updates metadata and fires callback again")
    void poll200WithContextsUpdatesMetadata() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        String body =
                EdgeResponseJsonFixtures.body(
                        120,
                        "ctx-v2",
                        "[{\"id\":\"age\",\"type\":\"INTEGER\"}]",
                        EdgeResponseJsonFixtures.featureGroup(
                                1001,
                                "default_feature_group",
                                EdgeResponseJsonFixtures.feature(1001, "feature-a", "")));
        enqueue200(body, "\"etag-2\"");
        cacheManager.refreshClientCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> metadataCallbackCount.get() == 2);

        RecordedRequest second = mockServer.takeRequest();
        assertServiceRequest(second, "ctx-v1", "\"etag-1\"");

        assertEquals("ctx-v2", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(
                "INTEGER",
                cacheManager.getClientCache().getMetadata().getFieldDataTypeCache().get("age"));
        assertEquals(2, metadataCallbackCount.get());
    }

    @Test
    @DisplayName("Poll 304 is a full no-op for features, metadata, and ETag")
    void poll304IsFullNoOp() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        mockServer.enqueue(
                new MockResponse().setResponseCode(304).setHeader("ETag", "\"etag-unchanged\""));

        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        RecordedRequest second = mockServer.takeRequest();
        assertServiceRequest(second, "ctx-v1", "\"etag-1\"");

        assertEquals("\"etag-1\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
        assertNull(cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));
        assertEquals("ctx-v1", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(1, metadataCallbackCount.get());
    }

    @Test
    @DisplayName("Subsequent fetch sends contextVersion and If-None-Match")
    void subsequentFetchSendsContextVersionAndEtag() throws Exception {
        enqueue200(combinedBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        enqueue200(featuresOnlyBody("ctx-v1", "feature-a"), "\"etag-1\"");
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        RecordedRequest second = mockServer.takeRequest();
        assertServiceRequest(second, "ctx-v1", "\"etag-1\"");
    }

    private void assertServiceRequest(
            RecordedRequest request, String contextVersion, String ifNoneMatch) {
        HttpUrl url = request.getRequestUrl();
        assertNotNull(url);
        assertEquals(Constants.FLAGS_FEATURE_PATH, url.encodedPath());
        assertEquals(CLIENT_ID, url.queryParameter(Constants.QUERY_CLIENT_ID));
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
        assertEquals(IMS_ORG, request.getHeader(Constants.HEADER_IMS_ORG));
        assertEquals(SANDBOX, request.getHeader(Constants.HEADER_SANDBOX_NAME));
        assertEquals(Constants.ENCODING_GZIP, request.getHeader(Constants.HEADER_ACCEPT_ENCODING));
    }

    private static String combinedBody(String contextVersion, String featureKey) {
        return EdgeResponseJsonFixtures.body(
                120,
                contextVersion,
                "[{\"id\":\"country\",\"type\":\"STRING\"}]",
                EdgeResponseJsonFixtures.featureGroup(
                        1001,
                        "default_feature_group",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
    }

    private static String featuresOnlyBody(String contextVersion, String featureKey) {
        return EdgeResponseJsonFixtures.bodyFeaturesOnly(
                120,
                contextVersion,
                EdgeResponseJsonFixtures.featureGroup(
                        1001,
                        "default_feature_group",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
    }

    private void enqueue200(String body, String etag) {
        mockServer.enqueue(
                new MockResponse().setResponseCode(200).setHeader("ETag", etag).setBody(body));
    }

    private APIProxy createApiProxy() {
        String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");
        return new APIProxy(baseUrl, IMS_ORG, SANDBOX, null);
    }

    private void awaitRequestCount(int expected) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == expected);
    }
}
