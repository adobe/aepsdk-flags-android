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
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.*;

class APIProxyTest {

    private static final String EDGE_BASE_URL = "https://example.com";
    private static final String IMS_ORG = "test-org";
    private static final String SANDBOX = "test-sandbox";

    @Nested
    @DisplayName("Internal HTTP client (no shared client)")
    class InternalClientTests {

        private APIProxy proxy;

        @BeforeEach
        void setUp() {
            proxy = new APIProxy(EDGE_BASE_URL, IMS_ORG, SANDBOX, null);
        }

        @AfterEach
        void tearDown() {
            proxy.shutdown();
        }

        @Test
        @DisplayName("Creates an internal HTTP client when sharedClient is null")
        void createsInternalClient() throws Exception {
            OkHttpClient client = getHttpClient(proxy);
            assertNotNull(client);
        }

        @Test
        @DisplayName("Internal client uses SDK timeout settings")
        void internalClientHasSdkTimeouts() throws Exception {
            OkHttpClient client = getHttpClient(proxy);

            assertEquals(
                    Constants.HTTP_CONNECT_TIMEOUT_SECONDS, client.connectTimeoutMillis() / 1000);
            assertEquals(Constants.HTTP_READ_TIMEOUT_SECONDS, client.readTimeoutMillis() / 1000);
            assertEquals(Constants.HTTP_WRITE_TIMEOUT_SECONDS, client.writeTimeoutMillis() / 1000);
        }

        @Test
        @DisplayName("shutdown() shuts down dispatcher for internal client")
        void shutdownClosesInternalClient() throws Exception {
            OkHttpClient client = getHttpClient(proxy);

            proxy.shutdown();

            assertTrue(client.dispatcher().executorService().isShutdown());
        }
    }

    @Nested
    @DisplayName("Shared HTTP client")
    class SharedClientTests {

        private OkHttpClient sharedClient;
        private APIProxy proxy;

        @BeforeEach
        void setUp() {
            sharedClient =
                    new OkHttpClient.Builder()
                            .connectTimeout(1, TimeUnit.SECONDS)
                            .readTimeout(2, TimeUnit.SECONDS)
                            .writeTimeout(3, TimeUnit.SECONDS)
                            .build();
            proxy = new APIProxy(EDGE_BASE_URL, IMS_ORG, SANDBOX, sharedClient);
        }

        @AfterEach
        void tearDown() {
            proxy.shutdown();
            sharedClient.dispatcher().executorService().shutdown();
            sharedClient.connectionPool().evictAll();
        }

        @Test
        @DisplayName("Derived client shares the same connection pool as the shared client")
        void sharesConnectionPool() throws Exception {
            OkHttpClient derived = getHttpClient(proxy);

            assertSame(sharedClient.connectionPool(), derived.connectionPool());
        }

        @Test
        @DisplayName("Derived client shares the same dispatcher as the shared client")
        void sharesDispatcher() throws Exception {
            OkHttpClient derived = getHttpClient(proxy);

            assertSame(sharedClient.dispatcher(), derived.dispatcher());
        }

        @Test
        @DisplayName("Derived client overrides timeouts with SDK values")
        void overridesTimeoutsWithSdkValues() throws Exception {
            OkHttpClient derived = getHttpClient(proxy);

            assertEquals(
                    Constants.HTTP_CONNECT_TIMEOUT_SECONDS, derived.connectTimeoutMillis() / 1000);
            assertEquals(Constants.HTTP_READ_TIMEOUT_SECONDS, derived.readTimeoutMillis() / 1000);
            assertEquals(Constants.HTTP_WRITE_TIMEOUT_SECONDS, derived.writeTimeoutMillis() / 1000);
        }

        @Test
        @DisplayName("Original shared client retains its own timeout settings")
        void originalClientRetainsTimeouts() {
            assertEquals(1000, sharedClient.connectTimeoutMillis());
            assertEquals(2000, sharedClient.readTimeoutMillis());
            assertEquals(3000, sharedClient.writeTimeoutMillis());
        }

        @Test
        @DisplayName("shutdown() does NOT shut down shared client dispatcher")
        void shutdownDoesNotCloseSharedClient() {
            proxy.shutdown();

            assertFalse(sharedClient.dispatcher().executorService().isShutdown());
        }

        @Test
        @DisplayName("Derived client is a distinct instance from the shared client")
        void derivedClientIsDistinctInstance() throws Exception {
            OkHttpClient derived = getHttpClient(proxy);

            assertNotSame(sharedClient, derived);
        }
    }

    @Nested
    @DisplayName("Shared client with custom connection pool")
    class SharedClientPoolTests {

        @Test
        @DisplayName("Custom pool is inherited by the derived client")
        void customPoolIsInherited() throws Exception {
            ConnectionPool customPool = new ConnectionPool(10, 1, TimeUnit.MINUTES);
            OkHttpClient sharedClient =
                    new OkHttpClient.Builder().connectionPool(customPool).build();

            APIProxy proxy = new APIProxy(EDGE_BASE_URL, IMS_ORG, SANDBOX, sharedClient);

            try {
                OkHttpClient derived = getHttpClient(proxy);
                assertSame(customPool, derived.connectionPool());
            } finally {
                proxy.shutdown();
                sharedClient.dispatcher().executorService().shutdown();
                customPool.evictAll();
            }
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Blank imsOrg throws IllegalArgumentException")
        void blankImsOrgThrows() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new APIProxy(EDGE_BASE_URL, "  ", SANDBOX, null));
        }

        @Test
        @DisplayName("Null edgeBaseUrl throws IllegalArgumentException")
        void nullEdgeBaseUrlThrows() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new APIProxy(null, IMS_ORG, SANDBOX, null));
        }

        @Test
        @DisplayName("Blank edgeBaseUrl throws IllegalArgumentException")
        void blankEdgeBaseUrlThrows() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new APIProxy("   ", IMS_ORG, SANDBOX, null));
        }

        @Test
        @DisplayName("Trailing slash is stripped from edgeBaseUrl")
        void trailingSlashIsStripped() throws Exception {
            APIProxy proxy = new APIProxy(EDGE_BASE_URL + "/", IMS_ORG, SANDBOX, null);
            try {
                Field field = APIProxy.class.getDeclaredField("edgeBaseUrl");
                field.setAccessible(true);
                assertEquals(EDGE_BASE_URL, field.get(proxy));
            } finally {
                proxy.shutdown();
            }
        }
    }

    private static OkHttpClient getHttpClient(APIProxy proxy) throws Exception {
        Field field = APIProxy.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(proxy);
    }
}
