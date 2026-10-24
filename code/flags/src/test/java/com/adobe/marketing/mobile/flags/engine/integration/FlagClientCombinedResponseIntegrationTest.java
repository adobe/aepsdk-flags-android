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

package com.adobe.marketing.mobile.flags.engine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.FlagClient;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.engine.testsupport.ServiceRequestAssertions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** End-to-end: {@link FlagClient#create} against combined payload via {@link MockWebServer}. */
class FlagClientCombinedResponseIntegrationTest {

    private static final String IMS_ORG = "test-org";
    private static final String SANDBOX = "prod";
    private static final String CLIENT_ID = "e2e-client";
    private static final String CONTEXT_V1 = "e2e-context-v1";

    private MockWebServer mockServer;

    @BeforeEach
    void startServer() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        mockServer.shutdown();
    }

    @Test
    @DisplayName("create() uses single combined fetch, evaluates criteria, refresh updates cache")
    void createEvaluateAndRefresh() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("criteria-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());

        assertEquals(1, mockServer.getRequestCount());
        ServiceRequestAssertions.assertFeatureRequest(mockServer.takeRequest(), CLIENT_ID, null);

        GetFeatureRequest usRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("criteria-feature", usRequest));
        assertFalse(client.isFeatureEnabled("criteria-feature", ukRequest));
        assertNotNull(client.getFeature("criteria-feature", usRequest));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-2\"")
                        .setBody(
                                EdgeResponseJsonFixtures.bodyFeaturesOnly(
                                        120,
                                        CONTEXT_V1,
                                        EdgeResponseJsonFixtures.featureGroupWithFields(
                                                1001,
                                                "e2e-group",
                                                "\"criteria\":\"{\\\"criteria\\\":{\\\"attr\\\":\\\"country\\\","
                                                    + "\\\"operator\\\":\\\"EQ\\\",\\\"val\\\":\\\"US\\\"}}\"",
                                                EdgeResponseJsonFixtures.feature(
                                                        1001, "criteria-feature-v2", "")))));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        assertTrue(client.isFeatureEnabled("criteria-feature-v2", usRequest));
        assertFalse(client.isFeatureEnabled("criteria-feature", usRequest));

        client.close();
    }

    @Test
    @DisplayName("refresh 304 leaves evaluation and cached features unchanged")
    void refresh304LeavesEvaluationUnchanged() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("unchanged-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        mockServer.takeRequest();

        GetFeatureRequest usRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("unchanged-feature", usRequest));
        assertFalse(client.isFeatureEnabled("unchanged-feature", ukRequest));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(304)
                        .setHeader("ETag", "\"e2e-etag-unchanged\""));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        assertTrue(
                client.isFeatureEnabled("unchanged-feature", usRequest),
                "US country match must still evaluate after 304 refresh");
        assertFalse(
                client.isFeatureEnabled("unchanged-feature", ukRequest),
                "UK country mismatch must still evaluate after 304 refresh");
        assertNotNull(client.getFeature("unchanged-feature", usRequest));

        client.close();
    }

    @Test
    @DisplayName("features-only refresh with same contextVersion keeps country criteria evaluation")
    void featuresOnlyRefreshKeepsCountryCriteriaWorking() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("country-gated-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        mockServer.takeRequest();

        GetFeatureRequest usRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("country-gated-feature", usRequest));
        assertFalse(client.isFeatureEnabled("country-gated-feature", ukRequest));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-2\"")
                        .setBody(
                                EdgeResponseJsonFixtures.bodyFeaturesOnly(
                                        120,
                                        CONTEXT_V1,
                                        EdgeResponseJsonFixtures.featureGroupWithFields(
                                                1001,
                                                "e2e-group",
                                                "\"criteria\":\"{\\\"criteria\\\":{\\\"attr\\\":\\\"country\\\","
                                                    + "\\\"operator\\\":\\\"EQ\\\",\\\"val\\\":\\\"US\\\"}}\"",
                                                EdgeResponseJsonFixtures.feature(
                                                        1001, "delta-feature", "")))));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        assertNull(
                client.getFeature("country-gated-feature", usRequest),
                "Features cache is replaced by the features-only delta");
        assertTrue(
                client.isFeatureEnabled("delta-feature", usRequest),
                "Country STRING metadata must remain for criteria after features-only refresh");
        assertFalse(
                client.isFeatureEnabled("delta-feature", ukRequest),
                "Country criteria must still reject non-matching context after features-only"
                        + " refresh");

        client.close();
    }

    @Test
    @DisplayName("304 refresh leaves evaluation unchanged and sends If-None-Match")
    void notModifiedRefreshLeavesEvaluationUnchanged() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("criteria-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        mockServer.takeRequest();

        GetFeatureRequest usRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("criteria-feature", usRequest));
        assertFalse(client.isFeatureEnabled("criteria-feature", ukRequest));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(304)
                        .setHeader("ETag", "\"e2e-etag-unchanged\""));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        assertTrue(
                client.isFeatureEnabled("criteria-feature", usRequest),
                "US country criteria must still match after 304 refresh");
        assertFalse(
                client.isFeatureEnabled("criteria-feature", ukRequest),
                "UK country criteria must still not match after 304 refresh");
        assertNotNull(client.getFeature("criteria-feature", usRequest));

        client.close();
    }

    @Test
    @DisplayName("features-only refresh retains country criteria evaluation schema")
    void featuresOnlyRefreshRetainsCountryCriteria() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("country-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        mockServer.takeRequest();

        GetFeatureRequest usRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukRequest =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("country-feature", usRequest));
        assertFalse(client.isFeatureEnabled("country-feature", ukRequest));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-2\"")
                        .setBody(featuresOnlyBodyWithCountryCriteria("country-feature-v2")));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        assertTrue(
                client.isFeatureEnabled("country-feature-v2", usRequest),
                "Country criteria must still evaluate after features-only refresh");
        assertFalse(
                client.isFeatureEnabled("country-feature-v2", ukRequest),
                "Country criteria schema must be retained for non-matching contexts");
        assertFalse(
                client.isFeatureEnabled("country-feature", usRequest),
                "Prior feature snapshot must be replaced by features-only refresh");
        assertNull(client.getFeature("country-feature", usRequest));

        client.close();
    }

    @Test
    @DisplayName("refresh sends cached contextVersion and new server version rebuilds eval schema")
    void contextVersionChangeRebuildsFilterServiceAndEvaluation() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-1\"")
                        .setBody(combinedBodyWithCountryCriteria("country-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        assertEquals(1, mockServer.getRequestCount());
        ServiceRequestAssertions.assertFeatureRequest(mockServer.takeRequest(), CLIENT_ID, null);

        GetFeatureRequest usCountry =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        GetFeatureRequest ukCountry =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("UK"))).build();

        assertTrue(client.isFeatureEnabled("country-feature", usCountry));
        assertFalse(client.isFeatureEnabled("country-feature", ukCountry));

        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"e2e-etag-2\"")
                        .setBody(bodyWithAgeCriteria("ctx-v2", "age-feature", 17)));

        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        RecordedRequest refreshRequest = mockServer.takeRequest();
        ServiceRequestAssertions.assertFeatureRequest(
                refreshRequest, CLIENT_ID, CONTEXT_V1, "\"e2e-etag-1\"");

        GetFeatureRequest adult =
                new GetFeatureRequest.Builder().context(Map.of("age", List.of("25"))).build();
        GetFeatureRequest minor =
                new GetFeatureRequest.Builder().context(Map.of("age", List.of("10"))).build();

        assertTrue(
                client.isFeatureEnabled("age-feature", adult),
                "FilterService must be rebuilt with INTEGER age after contextVersion change");
        assertFalse(client.isFeatureEnabled("age-feature", minor));
        assertFalse(
                client.isFeatureEnabled("country-feature", usCountry),
                "Stale feature from prior payload must not remain after refresh");

        client.close();
    }

    @Test
    @DisplayName("create() throws FlagInitException when all init HTTP attempts fail")
    void createFailsWhenInitHttpFails() throws Exception {
        for (int i = 0; i < Constants.DEFAULT_MAX_RETRY_ATTEMPTS; i++) {
            mockServer.enqueue(new MockResponse().setResponseCode(500));
        }

        FlagInitException ex =
                assertThrows(
                        FlagInitException.class, () -> FlagClient.create(buildConfiguration()));
        assertTrue(ex.getMessage().contains("Failed to initialize"));
        assertEquals(Constants.DEFAULT_MAX_RETRY_ATTEMPTS, mockServer.getRequestCount());
    }

    @Test
    @DisplayName("create() succeeds after transient HTTP failures")
    void createSucceedsAfterTransientFailures() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"recover-etag\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));

        FlagClient client = FlagClient.create(buildConfiguration());
        assertNotNull(client.getFeature("my-feature", new GetFeatureRequest.Builder().build()));
        assertEquals(3, mockServer.getRequestCount());
        client.close();
    }

    @Test
    @DisplayName("refresh HTTP failure keeps last known feature snapshot for evaluation")
    void refreshFailureKeepsStaleSnapshot() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"stable-etag\"")
                        .setBody(combinedBodyWithCountryCriteria("stable-feature")));

        FlagClient client = FlagClient.create(buildConfiguration());
        mockServer.takeRequest();

        GetFeatureRequest us =
                new GetFeatureRequest.Builder().context(Map.of("country", List.of("US"))).build();
        assertTrue(client.isFeatureEnabled("stable-feature", us));

        mockServer.enqueue(new MockResponse().setResponseCode(500));
        client.refreshCache();
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> mockServer.getRequestCount() == 2);

        assertTrue(
                client.isFeatureEnabled("stable-feature", us),
                "Evaluation must continue against last good cache after refresh failure");
        client.close();
    }

    @Test
    @DisplayName("Never requests /metadata.json and uses combined response path and headers")
    void neverRequestsMetadataEndpoint() throws Exception {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"guard-etag\"")
                        .setBody(EdgeResponseJsonFixtures.minimalBody()));

        FlagClient client = FlagClient.create(buildConfiguration());
        client.close();

        RecordedRequest request = mockServer.takeRequest();
        assertFalse(request.getPath().toLowerCase().contains("metadata"));
        ServiceRequestAssertions.assertFeatureRequest(request, CLIENT_ID, null);
    }

    private FlagConfiguration buildConfiguration() throws Exception {
        OkHttpClient routingClient =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    HttpUrl original = chain.request().url();
                                    HttpUrl routed =
                                            original.newBuilder()
                                                    .scheme("http")
                                                    .host(mockServer.getHostName())
                                                    .port(mockServer.getPort())
                                                    .build();
                                    return chain.proceed(
                                            chain.request().newBuilder().url(routed).build());
                                })
                        .build();

        return FlagConfiguration.builder()
                .edgeDomain("edge.int.adobedc.net")
                .imsOrg(IMS_ORG)
                .sandboxName(SANDBOX)
                .clientId(CLIENT_ID)
                .httpClient(routingClient)
                .build();
    }

    private static String combinedBodyWithCountryCriteria(String featureKey) {
        return EdgeResponseJsonFixtures.body(
                120,
                CONTEXT_V1,
                "[{\"id\":\"country\",\"type\":\"STRING\"}]",
                EdgeResponseJsonFixtures.featureGroupWithFields(
                        1001,
                        "e2e-group",
                        "\"criteria\":\"{\\\"criteria\\\":{\\\"attr\\\":\\\"country\\\","
                                + "\\\"operator\\\":\\\"EQ\\\",\\\"val\\\":\\\"US\\\"}}\"",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
    }

    private static String featuresOnlyBodyWithCountryCriteria(String featureKey) {
        return EdgeResponseJsonFixtures.bodyFeaturesOnly(
                120,
                CONTEXT_V1,
                EdgeResponseJsonFixtures.featureGroupWithFields(
                        1001,
                        "e2e-group",
                        "\"criteria\":\"{\\\"criteria\\\":{\\\"attr\\\":\\\"country\\\","
                                + "\\\"operator\\\":\\\"EQ\\\",\\\"val\\\":\\\"US\\\"}}\"",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
    }

    private static String bodyWithAgeCriteria(
            String contextVersion, String featureKey, int ageThreshold) {
        return EdgeResponseJsonFixtures.body(
                120,
                contextVersion,
                "[{\"id\":\"age\",\"type\":\"INTEGER\"}]",
                EdgeResponseJsonFixtures.featureGroupWithFields(
                        1001,
                        "e2e-group",
                        "\"criteria\":\"{\\\"criteria\\\":{\\\"attr\\\":\\\"age\\\","
                                + "\\\"operator\\\":\\\"GT\\\",\\\"val\\\":"
                                + ageThreshold
                                + "}}\"",
                        EdgeResponseJsonFixtures.feature(1001, featureKey, "")));
    }
}
