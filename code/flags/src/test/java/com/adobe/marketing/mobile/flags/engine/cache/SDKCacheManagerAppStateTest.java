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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.testsupport.ServiceRequestAssertions;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link SDKCacheManager#onAppStateChanged(AppState)} lifecycle transitions. */
class SDKCacheManagerAppStateTest {

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

    private void enqueueEmptyFeaturesResponse() {
        mockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", "\"etag-1\"")
                        .setBody(
                                EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                                        120, "ctx-v1", "[]", "")));
    }

    private void initializeCacheManager() throws FlagInitException {
        enqueueEmptyFeaturesResponse();
        String baseUrl = mockServer.url("/").toString().replaceAll("/$", "");
        APIProxy proxy = new APIProxy(baseUrl, "test-org", "test-sandbox", null);
        cacheManager.configure("test-client", proxy);
    }

    @Nested
    @DisplayName("Before initialization")
    class BeforeInitTests {

        @Test
        @DisplayName("BACKGROUND before init is a no-op")
        void backgroundBeforeInitIsNoOp() {
            assertDoesNotThrow(() -> cacheManager.onAppStateChanged(AppState.BACKGROUND));
        }

        @Test
        @DisplayName("FOREGROUND before init is a no-op")
        void foregroundBeforeInitIsNoOp() {
            assertDoesNotThrow(() -> cacheManager.onAppStateChanged(AppState.FOREGROUND));
        }

        @Test
        @DisplayName("null state before init is a no-op")
        void nullStateBeforeInitIsNoOp() {
            assertDoesNotThrow(() -> cacheManager.onAppStateChanged(null));
        }
    }

    @Nested
    @DisplayName("BACKGROUND transition")
    class BackgroundTransitionTests {

        @BeforeEach
        void init() throws FlagInitException {
            initializeCacheManager();
        }

        @Test
        @DisplayName("BACKGROUND sets paused flag to true")
        void backgroundSetsPausedFlag() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            assertTrue(getPaused(cacheManager).get());
        }

        @Test
        @DisplayName("BACKGROUND cancels scheduled polling future")
        void backgroundCancelsScheduledFuture() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            assertNull(getScheduledFuture(cacheManager));
        }

        @Test
        @DisplayName("Repeated BACKGROUND calls are idempotent (paused stays true)")
        void repeatedBackgroundIsIdempotent() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            assertTrue(getPaused(cacheManager).get());
        }

        @Test
        @DisplayName("null state after initialization is a no-op")
        void nullStateAfterInitIsNoOp() throws Exception {
            assertDoesNotThrow(() -> cacheManager.onAppStateChanged(null));
            assertFalse(getPaused(cacheManager).get());
        }
    }

    @Nested
    @DisplayName("FOREGROUND transition")
    class ForegroundTransitionTests {

        @BeforeEach
        void init() throws FlagInitException {
            initializeCacheManager();
        }

        @Test
        @DisplayName("FOREGROUND after BACKGROUND clears paused flag")
        void foregroundClearsPausedFlag() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            enqueueEmptyFeaturesResponse();
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            assertFalse(getPaused(cacheManager).get());
        }

        @Test
        @DisplayName("FOREGROUND after BACKGROUND restarts polling schedule")
        void foregroundRestartsPolling() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            assertNull(
                    getScheduledFuture(cacheManager),
                    "Polling should be cancelled after BACKGROUND");

            enqueueEmptyFeaturesResponse();
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            Awaitility.await("Polling should be restarted after FOREGROUND")
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> getScheduledFuture(cacheManager) != null);
        }

        @Test
        @DisplayName("FOREGROUND refresh sends cached contextVersion query param")
        void foregroundRefreshSendsCachedContextVersion() throws Exception {
            mockServer.takeRequest();

            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            mockServer.enqueue(
                    new MockResponse()
                            .setResponseCode(200)
                            .setHeader("ETag", "\"etag-fg-refresh\"")
                            .setBody(
                                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                                            120, "ctx-v1", "[]", "")));

            int requestsBefore = mockServer.getRequestCount();
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> mockServer.getRequestCount() > requestsBefore);

            RecordedRequest refresh = mockServer.takeRequest();
            ServiceRequestAssertions.assertFeatureRequest(
                    refresh, "test-client", "ctx-v1", "\"etag-1\"");
        }

        @Test
        @DisplayName("FOREGROUND triggers an immediate cache refresh")
        void foregroundTriggersImmediateRefresh() {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            mockServer.enqueue(
                    new MockResponse()
                            .setResponseCode(200)
                            .setHeader("ETag", "\"etag-fg-refresh\"")
                            .setBody(
                                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                                            120, "ctx-v1", "[]", "")));

            int requestsBefore = mockServer.getRequestCount();
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            Awaitility.await("An immediate HTTP refresh should be triggered on FOREGROUND")
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> mockServer.getRequestCount() > requestsBefore);
        }

        @Test
        @DisplayName("Repeated FOREGROUND calls are idempotent (only one refresh submitted)")
        void repeatedForegroundIsIdempotent() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            enqueueEmptyFeaturesResponse();

            // First call transitions paused → false and submits refresh.
            // Second call is a no-op because compareAndSet(true, false) fails.
            cacheManager.onAppStateChanged(AppState.FOREGROUND);
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            assertFalse(getPaused(cacheManager).get());
        }
    }

    @Nested
    @DisplayName("Round-trip transitions")
    class RoundTripTests {

        @BeforeEach
        void init() throws FlagInitException {
            initializeCacheManager();
        }

        @Test
        @DisplayName("BACKGROUND → FOREGROUND → BACKGROUND cycle is handled correctly")
        void fullCycleIsHandledCorrectly() throws Exception {
            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            assertTrue(getPaused(cacheManager).get());

            enqueueEmptyFeaturesResponse();
            cacheManager.onAppStateChanged(AppState.FOREGROUND);

            Awaitility.await("paused flag should be cleared on FOREGROUND")
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> !getPaused(cacheManager).get());

            cacheManager.onAppStateChanged(AppState.BACKGROUND);
            assertTrue(getPaused(cacheManager).get());
        }

        @Test
        @DisplayName("Evaluation cache survives BACKGROUND (cache is not cleared)")
        void cacheIsPreservedThroughBackground() {
            SDKClientCache clientCache = cacheManager.getClientCache();
            FeaturesResponse featureGroup = new FeaturesResponse();
            featureGroup.setFeatureGroupName("R-persist");
            featureGroup.setFeatures(new String[] {"persist-feature"});
            clientCache.putFeatures(
                    "test-client", new FeaturesResponse[] {featureGroup}, "etag-persist");

            cacheManager.onAppStateChanged(AppState.BACKGROUND);

            SDKClientCache.CacheEntry entry = clientCache.getFeatures("test-client");
            assertNotNull(entry, "Cache must not be cleared on BACKGROUND");
            assertFalse(entry.isEmpty(), "Cache entry must not be empty after BACKGROUND");
            assertNotNull(
                    entry.findFeature("persist-feature"),
                    "Feature must be retrievable after BACKGROUND");
        }
    }

    private static AtomicBoolean getPaused(SDKCacheManager mgr) throws Exception {
        Field f = SDKCacheManager.class.getDeclaredField("paused");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(mgr);
    }

    private static java.util.concurrent.ScheduledFuture<?> getScheduledFuture(SDKCacheManager mgr)
            throws Exception {
        Field f = SDKCacheManager.class.getDeclaredField("scheduledFuture");
        f.setAccessible(true);
        return (java.util.concurrent.ScheduledFuture<?>) f.get(mgr);
    }
}
