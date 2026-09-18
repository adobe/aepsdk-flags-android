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

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.models.FlagApiResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies non-positive {@code ttl} values do not propagate into polling schedule ({@code
 * scheduleWithFixedDelay} requires a positive period).
 */
class SDKCacheManagerPollIntervalTest {

    private static MockWebServer mockServer;

    private SDKCacheManager cacheManager;

    @BeforeAll
    static void startServer() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        mockServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        cacheManager = new SDKCacheManager(new PolicyCache());
    }

    @AfterEach
    void tearDown() {
        cacheManager.shutdown();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -120})
    @DisplayName("Non-positive ttl falls back to default poll interval after cache update")
    void nonPositiveTtlFallsBackToDefaultPollInterval(int ttl) throws Exception {
        enqueueFeaturesResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(ttl));
        configureCacheManager();

        assertEquals(
                Constants.DEFAULT_POLL_INTERVAL_SECONDS,
                getStoredPollIntervalSeconds(cacheManager));
        assertNotNull(
                getScheduledFuture(cacheManager),
                "Polling must start with default interval when ttl is non-positive");
    }

    @Test
    @DisplayName("APIProxy returns null poll interval when ttl is zero")
    void apiProxyReturnsNullPollIntervalForZeroTtl() throws Exception {
        enqueueFeaturesResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(0));
        FlagApiResponse response = createApiProxy().getFeatures("test-client", null, null);

        assertNull(response.getPollInterval());
    }

    @Test
    @DisplayName("APIProxy returns null poll interval when ttl is negative")
    void apiProxyReturnsNullPollIntervalForNegativeTtl() throws Exception {
        enqueueFeaturesResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(-60));
        FlagApiResponse response = createApiProxy().getFeatures("test-client", null, null);

        assertNull(response.getPollInterval());
    }

    private void enqueueFeaturesResponse(String body) {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-ttl\"")
                        .setBody(body));
    }

    private void configureCacheManager() throws FlagInitException {
        cacheManager.configure("test-client", createApiProxy());
    }

    private APIProxy createApiProxy() {
        String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");
        return new APIProxy(baseUrl, "test-org", "test-sandbox", null);
    }

    private static int getStoredPollIntervalSeconds(SDKCacheManager mgr) throws Exception {
        Object headers = getResponseHeaders(mgr);
        assertNotNull(headers, "responseHeaders should be set after successful init");
        Method getPollInterval = headers.getClass().getDeclaredMethod("getPollInterval");
        getPollInterval.setAccessible(true);
        return (Integer) getPollInterval.invoke(headers);
    }

    private static Object getResponseHeaders(SDKCacheManager mgr) throws Exception {
        Field field = SDKCacheManager.class.getDeclaredField("responseHeaders");
        field.setAccessible(true);
        return field.get(mgr);
    }

    private static java.util.concurrent.ScheduledFuture<?> getScheduledFuture(SDKCacheManager mgr)
            throws Exception {
        Field field = SDKCacheManager.class.getDeclaredField("scheduledFuture");
        field.setAccessible(true);
        return (java.util.concurrent.ScheduledFuture<?>) field.get(mgr);
    }
}
