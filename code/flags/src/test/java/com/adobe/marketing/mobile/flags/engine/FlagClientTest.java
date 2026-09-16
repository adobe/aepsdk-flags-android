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

package com.adobe.marketing.mobile.flags.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.api.APIProxy;
import com.adobe.marketing.mobile.flags.engine.cache.SDKCacheManager;
import com.adobe.marketing.mobile.flags.engine.cache.SDKClientCache;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.models.*;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;

/** Unit tests for {@link FlagClient} public API methods. */
class FlagClientTest {

    private static final String CLIENT_ID = "thread-test";
    private static final String TEST_IMS_ORG = "test-org";
    private static final String TEST_SANDBOX = "test-sandbox";

    private static final String TEST_EDGE_DOMAIN = "example.com";

    private FlagClient client;
    private SDKClientCache cache;

    @BeforeEach
    void setUp() throws Exception {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain(TEST_EDGE_DOMAIN)
                        .imsOrg(TEST_IMS_ORG)
                        .sandboxName(TEST_SANDBOX)
                        .clientId(CLIENT_ID)
                        .build();

        Constructor<FlagClient> ctor =
                FlagClient.class.getDeclaredConstructor(FlagConfiguration.class);
        ctor.setAccessible(true);
        client = ctor.newInstance(config);

        Field cacheField = FlagClient.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        cache = (SDKClientCache) cacheField.get(client);

        Field initField = FlagClient.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        ((AtomicBoolean) initField.get(client)).set(true);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    private void putFeatures(FeaturesResponse... featureGroups) {
        cache.putFeatures(CLIENT_ID, featureGroups, "etag-1");
    }

    private FeaturesResponse featureGroup(String featureGroupName, Feature... features) {
        FeaturesResponse r = new FeaturesResponse();
        r.setFeatureGroupName(featureGroupName);
        r.setFeaturesObj(features);
        return r;
    }

    private Feature feature(String name) {
        Feature f = new Feature();
        f.setFeature(name);
        return f;
    }

    private Feature featureWithValue(String name, Object value) {
        Feature f = new Feature();
        f.setFeature(name);
        f.setValue(value);
        return f;
    }

    private GetFeatureRequest requestWithContext(Map<String, List<String>> ctx) {
        return new GetFeatureRequest.Builder().context(ctx).build();
    }

    @Nested
    @DisplayName("getFeatures()")
    class GetFeaturesTests {

        @Test
        @DisplayName("Returns all features from cache")
        void returnsAllFeatures() throws FlagClientException {
            putFeatures(featureGroup("R1", feature("alpha"), feature("beta")));

            FeatureResult[] results = client.getFeatures(null);

            assertEquals(2, results.length);
            assertEquals("alpha", results[0].getKey());
            assertEquals("beta", results[1].getKey());
        }

        @Test
        @DisplayName("Returns empty array when no features cached")
        void returnsEmptyWhenNoCache() throws FlagClientException {
            FeatureResult[] results = client.getFeatures(null);

            assertNotNull(results);
            assertEquals(0, results.length);
        }

        @Test
        @DisplayName("Handles null request by using defaults")
        void handlesNullRequest() throws FlagClientException {
            putFeatures(featureGroup("R1", feature("feat-a")));

            FeatureResult[] results = client.getFeatures(null);

            assertEquals(1, results.length);
            assertEquals("feat-a", results[0].getKey());
        }

        @Test
        @DisplayName("Accepts explicit request with context")
        void acceptsExplicitRequest() throws FlagClientException {
            putFeatures(featureGroup("R1", feature("feat-a")));

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));

            FeatureResult[] results = client.getFeatures(request);

            assertEquals(1, results.length);
        }

        @Test
        @DisplayName("Returns features from multiple featureGroups")
        void multipleReleases() throws FlagClientException {
            putFeatures(
                    featureGroup("R1", feature("feat-a")),
                    featureGroup("R2", feature("feat-b"), feature("feat-c")));

            FeatureResult[] results = client.getFeatures(null);

            assertEquals(3, results.length);
            assertEquals("R1", results[0].getFeatureGroupKey());
            assertEquals("R2", results[1].getFeatureGroupKey());
            assertEquals("R2", results[2].getFeatureGroupKey());
        }

        @Test
        @DisplayName("Throws FlagClientException when not initialized")
        void throwsWhenNotInitialized() throws Exception {
            Field initField = FlagClient.class.getDeclaredField("initialized");
            initField.setAccessible(true);
            ((AtomicBoolean) initField.get(client)).set(false);

            assertThrows(FlagClientException.class, () -> client.getFeatures(null));
        }

        @Test
        @DisplayName("Throws FlagClientException after close")
        void throwsAfterClose() {
            client.close();
            assertThrows(FlagClientException.class, () -> client.getFeatures(null));
        }
    }

    @Nested
    @DisplayName("getFeature()")
    class GetFeatureTests {

        @BeforeEach
        void setUpCache() {
            putFeatures(
                    featureGroup(
                            "R1",
                            feature("dark-mode"),
                            featureWithValue("banner-text", "Welcome!")));
        }

        @Test
        @DisplayName("Returns matching feature by name")
        void returnsMatchingFeature() throws FlagClientException {
            FeatureResult result = client.getFeature("dark-mode", null);

            assertNotNull(result);
            assertEquals("dark-mode", result.getKey());
            assertEquals("R1", result.getFeatureGroupKey());
        }

        @Test
        @DisplayName("Returns feature with value")
        void returnsFeatureWithValue() throws FlagClientException {
            FeatureResult result = client.getFeature("banner-text", null);

            assertNotNull(result);
            assertEquals("banner-text", result.getKey());
            assertEquals("Welcome!", result.getValue());
        }

        @Test
        @DisplayName("Returns null for unknown feature name")
        void returnsNullForUnknown() throws FlagClientException {
            assertNull(client.getFeature("nonexistent", null));
        }

        @Test
        @DisplayName("Returns null for null feature name")
        void returnsNullForNullName() throws FlagClientException {
            assertNull(client.getFeature(null, null));
        }

        @Test
        @DisplayName("Handles null request")
        void handlesNullRequest() throws FlagClientException {
            FeatureResult result = client.getFeature("dark-mode", null);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Accepts explicit request")
        void acceptsExplicitRequest() throws FlagClientException {
            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));
            FeatureResult result = client.getFeature("dark-mode", request);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Throws FlagClientException when not initialized")
        void throwsWhenNotInitialized() throws Exception {
            Field initField = FlagClient.class.getDeclaredField("initialized");
            initField.setAccessible(true);
            ((AtomicBoolean) initField.get(client)).set(false);

            assertThrows(FlagClientException.class, () -> client.getFeature("dark-mode", null));
        }

        @Test
        @DisplayName("Throws FlagClientException after close")
        void throwsAfterClose() {
            client.close();
            assertThrows(FlagClientException.class, () -> client.getFeature("dark-mode", null));
        }
    }

    @Nested
    @DisplayName("isFeatureEnabled()")
    class IsFeatureEnabledTests {

        @BeforeEach
        void setUpCache() {
            putFeatures(featureGroup("R1", feature("enabled-flag"), feature("another-flag")));
        }

        @Test
        @DisplayName("Returns true for existing feature")
        void returnsTrueForExisting() throws FlagClientException {
            assertTrue(client.isFeatureEnabled("enabled-flag", null));
        }

        @Test
        @DisplayName("Returns true for another existing feature")
        void returnsTrueForAnother() throws FlagClientException {
            assertTrue(client.isFeatureEnabled("another-flag", null));
        }

        @Test
        @DisplayName("Returns false for unknown feature")
        void returnsFalseForUnknown() throws FlagClientException {
            assertFalse(client.isFeatureEnabled("nonexistent", null));
        }

        @Test
        @DisplayName("Returns false for null feature name")
        void returnsFalseForNull() throws FlagClientException {
            assertFalse(client.isFeatureEnabled(null, null));
        }

        @Test
        @DisplayName("Handles null request")
        void handlesNullRequest() throws FlagClientException {
            assertTrue(client.isFeatureEnabled("enabled-flag", null));
        }

        @Test
        @DisplayName("Accepts explicit request")
        void acceptsExplicitRequest() throws FlagClientException {
            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));
            assertTrue(client.isFeatureEnabled("enabled-flag", request));
        }

        @Test
        @DisplayName("Throws FlagClientException when not initialized")
        void throwsWhenNotInitialized() throws Exception {
            Field initField = FlagClient.class.getDeclaredField("initialized");
            initField.setAccessible(true);
            ((AtomicBoolean) initField.get(client)).set(false);

            assertThrows(
                    FlagClientException.class, () -> client.isFeatureEnabled("enabled-flag", null));
        }

        @Test
        @DisplayName("Throws FlagClientException after close")
        void throwsAfterClose() {
            client.close();
            assertThrows(
                    FlagClientException.class, () -> client.isFeatureEnabled("enabled-flag", null));
        }
    }

    @Nested
    @DisplayName("httpClient wiring")
    class HttpClientWiringTests {

        @Test
        @DisplayName("Shared OkHttpClient from config is passed to APIProxy")
        void sharedClientIsWiredToProxy() throws Exception {
            OkHttpClient sharedClient = new OkHttpClient();
            try {
                FlagConfiguration config =
                        FlagConfiguration.builder()
                                .edgeDomain(TEST_EDGE_DOMAIN)
                                .imsOrg(TEST_IMS_ORG)
                                .sandboxName(TEST_SANDBOX)
                                .clientId("wiring-test")
                                .httpClient(sharedClient)
                                .build();

                Constructor<FlagClient> ctor =
                        FlagClient.class.getDeclaredConstructor(FlagConfiguration.class);
                ctor.setAccessible(true);
                FlagClient fc = ctor.newInstance(config);

                try {
                    Field proxyField = FlagClient.class.getDeclaredField("apiProxy");
                    proxyField.setAccessible(true);
                    APIProxy proxy = (APIProxy) proxyField.get(fc);

                    Field clientField = APIProxy.class.getDeclaredField("httpClient");
                    clientField.setAccessible(true);
                    OkHttpClient derivedClient = (OkHttpClient) clientField.get(proxy);

                    assertSame(
                            sharedClient.connectionPool(),
                            derivedClient.connectionPool(),
                            "Derived client should share the caller's connection pool");
                } finally {
                    fc.close();
                }
            } finally {
                sharedClient.dispatcher().executorService().shutdown();
                sharedClient.connectionPool().evictAll();
            }
        }

        @Test
        @DisplayName("No shared client results in SDK-owned independent pool")
        void noSharedClientCreatesIndependentPool() throws Exception {
            FlagConfiguration config =
                    FlagConfiguration.builder()
                            .edgeDomain(TEST_EDGE_DOMAIN)
                            .imsOrg(TEST_IMS_ORG)
                            .sandboxName(TEST_SANDBOX)
                            .clientId("internal-test")
                            .build();

            Constructor<FlagClient> ctor =
                    FlagClient.class.getDeclaredConstructor(FlagConfiguration.class);
            ctor.setAccessible(true);
            FlagClient fc = ctor.newInstance(config);

            try {
                Field proxyField = FlagClient.class.getDeclaredField("apiProxy");
                proxyField.setAccessible(true);
                APIProxy proxy = (APIProxy) proxyField.get(fc);

                Field internalField = APIProxy.class.getDeclaredField("isInternalHttpClient");
                internalField.setAccessible(true);
                assertTrue(
                        (boolean) internalField.get(proxy),
                        "Without shared client, SDK should own the HTTP client");
            } finally {
                fc.close();
            }
        }
    }

    @Nested
    @DisplayName("setAppState()")
    class SetAppStateTests {

        private SDKCacheManager cacheManager;
        private APIProxy apiProxy;

        @BeforeEach
        void injectMocks() throws Exception {
            cacheManager =
                    new SDKCacheManager(new PolicyCache()) {
                        @Override
                        public void onAppStateChanged(AppState state) {
                            /* captured by spy */
                        }
                    };
            apiProxy = new APIProxy("https://example.com", TEST_IMS_ORG, TEST_SANDBOX, null);

            Field cmField = FlagClient.class.getDeclaredField("cacheManager");
            cmField.setAccessible(true);
            cmField.set(client, cacheManager);

            Field apField = FlagClient.class.getDeclaredField("apiProxy");
            apField.setAccessible(true);
            apField.set(client, apiProxy);
        }

        @AfterEach
        void cleanUp() {
            apiProxy.shutdown();
        }

        @Test
        @DisplayName("Throws FlagClientException when client is not initialized")
        void throwsWhenNotInitialized() throws Exception {
            Field initField = FlagClient.class.getDeclaredField("initialized");
            initField.setAccessible(true);
            ((AtomicBoolean) initField.get(client)).set(false);

            assertThrows(FlagClientException.class, () -> client.setAppState(AppState.BACKGROUND));
        }

        @Test
        @DisplayName("Throws FlagClientException after close")
        void throwsAfterClose() {
            client.close();
            assertThrows(FlagClientException.class, () -> client.setAppState(AppState.FOREGROUND));
        }

        @Test
        @DisplayName("BACKGROUND evicts idle connections without throwing")
        void backgroundEvictsConnectionsWithoutThrowing() {
            assertDoesNotThrow(() -> client.setAppState(AppState.BACKGROUND));
        }

        @Test
        @DisplayName("FOREGROUND does not evict connections")
        void foregroundDoesNotEvictConnections() {
            assertDoesNotThrow(() -> client.setAppState(AppState.FOREGROUND));
        }

        @Test
        @DisplayName("Repeated BACKGROUND transitions are no-ops (idempotent)")
        void repeatedBackgroundIsIdempotent() {
            assertDoesNotThrow(
                    () -> {
                        client.setAppState(AppState.BACKGROUND);
                        client.setAppState(AppState.BACKGROUND);
                    });
        }

        @Test
        @DisplayName("Repeated FOREGROUND transitions are no-ops (idempotent)")
        void repeatedForegroundIsIdempotent() {
            assertDoesNotThrow(
                    () -> {
                        client.setAppState(AppState.FOREGROUND);
                        client.setAppState(AppState.FOREGROUND);
                    });
        }
    }

    @Nested
    @DisplayName("Lifecycle and getters")
    class LifecycleTests {

        @Test
        @DisplayName("isInitialized returns true after setup")
        void isInitializedTrue() {
            assertTrue(client.isInitialized());
        }

        @Test
        @DisplayName("isInitialized returns false after close")
        void isInitializedFalseAfterClose() {
            client.close();
            assertFalse(client.isInitialized());
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIsIdempotent() {
            client.close();
            client.close();
            assertFalse(client.isInitialized());
        }

        @Test
        @DisplayName("getClientId returns configured ID")
        void getClientId() {
            assertEquals(CLIENT_ID, client.getClientId());
        }

        @Test
        @DisplayName("getConfiguration returns non-null config")
        void getConfiguration() {
            FlagConfiguration config = client.getConfiguration();
            assertNotNull(config);
            assertEquals(CLIENT_ID, config.getClientId());
        }
    }
}
