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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.testsupport.ServiceRequestAssertions;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Failure-path tests for {@link SDKCacheManager} HTTP fetch and retry behavior. */
class SDKCacheManagerFailureTest {

    private static final String CLIENT_ID = "fail-client";
    private static final String IMS_ORG = "test-org";
    private static final String SANDBOX = "test-sandbox";

    private MockWebServer mockServer;
    private SDKCacheManager cacheManager;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        cacheManager = new SDKCacheManager(new PolicyCache());
    }

    @AfterEach
    void tearDown() throws Exception {
        cacheManager.shutdown();
        mockServer.shutdown();
    }

    @Test
    @DisplayName("refresh with unparseable 200 leaves prior feature snapshot intact")
    void refreshUnparseableBodyPreservesFeatureSnapshot() throws Exception {
        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-bad\"")
                        .setBody("not json"));
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        assertEquals("\"etag-1\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
    }

    @Test
    @DisplayName("refresh with valid empty featureGroups clears feature snapshot")
    void refreshValidEmptyFeatureGroupsClearsSnapshot() throws Exception {
        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        String emptyBody = EdgeResponseJsonFixtures.bodyWithFeatureGroups(120, "ctx-v1", "[]", "");
        mockServer.enqueue(successResponse(emptyBody, "\"etag-empty\""));
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        assertEquals("\"etag-empty\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertTrue(cacheManager.getClientCache().getFeatures(CLIENT_ID).isEmpty());
    }

    @Test
    @DisplayName("configure() throws FlagInitException after exhausting init retries")
    void configureFailsAfterRetries() {
        for (int i = 0; i < Constants.DEFAULT_MAX_RETRY_ATTEMPTS; i++) {
            mockServer.enqueue(new MockResponse().setResponseCode(500));
        }

        FlagInitException ex =
                assertThrows(
                        FlagInitException.class,
                        () -> cacheManager.configure(CLIENT_ID, createApiProxy()));
        assertTrue(ex.getMessage().contains("Failed to initialize cache manager"));
        assertEquals(Constants.DEFAULT_MAX_RETRY_ATTEMPTS, mockServer.getRequestCount());
    }

    @Test
    @DisplayName("configure() succeeds when a later init retry returns 200")
    void configureRecoversOnLaterRetry() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(502));
        mockServer.enqueue(new MockResponse().setResponseCode(502));
        mockServer.enqueue(
                successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-recover\""));

        cacheManager.configure(CLIENT_ID, createApiProxy());

        assertEquals(3, mockServer.getRequestCount());
        assertTrue(cacheManager.getClientCache().hasFeatures(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
    }

    @Test
    @DisplayName("refresh failure leaves prior feature snapshot intact")
    void refreshFailurePreservesFeatureSnapshot() throws Exception {
        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        mockServer.enqueue(new MockResponse().setResponseCode(500));
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        assertEquals("\"etag-1\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
    }

    @Test
    @DisplayName("refresh failure leaves prior metadata snapshot intact")
    void refreshFailurePreservesMetadataSnapshot() throws Exception {
        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        assertEquals("ctx-v1", cacheManager.getClientCache().getMetadata().getContextVersion());

        mockServer.enqueue(new MockResponse().setResponseCode(500));
        cacheManager.refreshClientCache();
        awaitRequestCount(2);

        assertEquals("ctx-v1", cacheManager.getClientCache().getMetadata().getContextVersion());
        assertEquals(
                "STRING",
                cacheManager.getClientCache().getMetadata().getFieldDataTypeCache().get("country"));
    }

    @Test
    @DisplayName("successful refresh after failure sends cached contextVersion on wire")
    void refreshAfterFailureSendsCachedContextVersion() throws Exception {
        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        mockServer.enqueue(new MockResponse().setResponseCode(500));
        cacheServerRefreshOnce();

        mockServer.enqueue(successResponse(combinedBody("ctx-v1", "feature-b"), "\"etag-2\""));
        cacheManager.refreshClientCache();
        awaitRequestCount(3);

        RecordedRequest recovered = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(recovered, CLIENT_ID, "ctx-v1", "\"etag-1\"");
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));
    }

    @Test
    @DisplayName("scheduled poll survives unparseable 200 and recovers on next tick")
    void scheduledPollSurvivesUnparseableBodyAndRecovers() throws Exception {
        mockServer.enqueue(
                successResponse(combinedBodyWithTtl(1, "ctx-v1", "feature-a"), "\"etag-1\""));
        cacheManager.configure(CLIENT_ID, createApiProxy());
        mockServer.takeRequest();

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-bad\"")
                        .setBody("not json"));
        mockServer.enqueue(
                successResponse(combinedBodyWithTtl(1, "ctx-v1", "feature-b"), "\"etag-2\""));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> mockServer.getRequestCount() >= 2);

        assertEquals("\"etag-1\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
        assertNull(cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> mockServer.getRequestCount() >= 3);

        assertEquals("\"etag-2\"", cacheManager.getClientCache().getFeaturesEtag(CLIENT_ID));
        assertNotNull(
                cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-b"));
        assertNull(cacheManager.getClientCache().getFeatures(CLIENT_ID).findFeature("feature-a"));
    }

    private void cacheServerRefreshOnce() throws InterruptedException {
        cacheManager.refreshClientCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);
        mockServer.takeRequest();
    }

    private static MockResponse successResponse(String body, String etag) {
        return new MockResponse().setResponseCode(200).setHeader("ETag", etag).setBody(body);
    }

    private static String combinedBody(String contextVersion, String featureKey) {
        return combinedBodyWithTtl(120, contextVersion, featureKey);
    }

    private static String combinedBodyWithTtl(int ttl, String contextVersion, String featureKey) {
        return EdgeResponseJsonFixtures.body(
                ttl,
                contextVersion,
                "[{\"id\":\"country\",\"type\":\"STRING\"}]",
                EdgeResponseJsonFixtures.featureGroup(
                        1001,
                        "default_feature_group",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
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
