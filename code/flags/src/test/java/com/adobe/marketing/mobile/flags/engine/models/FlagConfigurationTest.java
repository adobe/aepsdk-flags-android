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

package com.adobe.marketing.mobile.flags.engine.models;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FlagConfigurationTest {

    private static final String TEST_IMS_ORG = "test-org";
    private static final String TEST_SANDBOX = "test-sandbox";
    private static final String TEST_EDGE_DOMAIN = "edge.int.adobedc.net";

    @Test
    @DisplayName("Valid config builds successfully")
    void testValidBuild() throws FlagInitException {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain(TEST_EDGE_DOMAIN)
                        .imsOrg(TEST_IMS_ORG)
                        .sandboxName(TEST_SANDBOX)
                        .clientId("client-a")
                        .build();

        assertEquals(TEST_EDGE_DOMAIN, config.getEdgeDomain());
        assertEquals("client-a", config.getClientId());
    }

    @Test
    @DisplayName("edgeDomain trims surrounding whitespace")
    void edgeDomainTrimsWhitespace() throws FlagInitException {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain("  edge.int.adobedc.net  ")
                        .imsOrg(TEST_IMS_ORG)
                        .sandboxName(TEST_SANDBOX)
                        .clientId("client-a")
                        .build();

        assertEquals(TEST_EDGE_DOMAIN, config.getEdgeDomain());
    }

    @Test
    @DisplayName("edgeDomain rejects URL scheme")
    void edgeDomainRejectsScheme() {
        FlagInitException ex =
                assertThrows(
                        FlagInitException.class,
                        () ->
                                FlagConfiguration.builder()
                                        .edgeDomain("https://edge.int.adobedc.net")
                                        .imsOrg(TEST_IMS_ORG)
                                        .sandboxName(TEST_SANDBOX)
                                        .clientId("client-a")
                                        .build());
        assertTrue(ex.getMessage().contains("scheme"));
    }

    @Test
    @DisplayName("edgeDomain rejects path")
    void edgeDomainRejectsPath() {
        FlagInitException ex =
                assertThrows(
                        FlagInitException.class,
                        () ->
                                FlagConfiguration.builder()
                                        .edgeDomain("edge.int.adobedc.net/flags")
                                        .imsOrg(TEST_IMS_ORG)
                                        .sandboxName(TEST_SANDBOX)
                                        .clientId("client-a")
                                        .build());
        assertTrue(ex.getMessage().contains("path"));
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Missing edgeDomain throws FlagInitException")
        void testMissingEdgeDomain() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .imsOrg(TEST_IMS_ORG)
                                            .sandboxName(TEST_SANDBOX)
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("Edge domain"));
        }

        @Test
        @DisplayName("Blank edgeDomain throws FlagInitException")
        void testBlankEdgeDomain() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain("   ")
                                            .imsOrg(TEST_IMS_ORG)
                                            .sandboxName(TEST_SANDBOX)
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("Edge domain"));
        }

        @Test
        @DisplayName("Missing clientId throws FlagInitException")
        void testMissingClientId() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .imsOrg(TEST_IMS_ORG)
                                            .sandboxName(TEST_SANDBOX)
                                            .build());
            assertTrue(ex.getMessage().contains("Client ID"));
        }

        @Test
        @DisplayName("Blank clientId throws FlagInitException")
        void testBlankClientId() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .imsOrg(TEST_IMS_ORG)
                                            .sandboxName(TEST_SANDBOX)
                                            .clientId("   ")
                                            .build());
            assertTrue(ex.getMessage().contains("Client ID"));
        }

        @Test
        @DisplayName("Missing imsOrg throws FlagInitException")
        void testMissingImsOrg() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .sandboxName(TEST_SANDBOX)
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("IMS organization"));
        }

        @Test
        @DisplayName("Blank imsOrg throws FlagInitException")
        void testBlankImsOrg() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .imsOrg("   ")
                                            .sandboxName(TEST_SANDBOX)
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("IMS organization"));
        }

        @Test
        @DisplayName("Missing sandboxName throws FlagInitException")
        void testMissingSandboxName() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .imsOrg(TEST_IMS_ORG)
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("Sandbox name"));
        }

        @Test
        @DisplayName("Blank sandboxName throws FlagInitException")
        void testBlankSandboxName() {
            FlagInitException ex =
                    assertThrows(
                            FlagInitException.class,
                            () ->
                                    FlagConfiguration.builder()
                                            .edgeDomain(TEST_EDGE_DOMAIN)
                                            .imsOrg(TEST_IMS_ORG)
                                            .sandboxName("   ")
                                            .clientId("client-a")
                                            .build());
            assertTrue(ex.getMessage().contains("Sandbox name"));
        }
    }

    @Test
    @DisplayName("Getters return configured values")
    void testGetters() throws FlagInitException {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain(TEST_EDGE_DOMAIN)
                        .imsOrg("my-org")
                        .sandboxName("prod")
                        .clientId("client-a")
                        .build();

        assertEquals(TEST_EDGE_DOMAIN, config.getEdgeDomain());
        assertEquals("my-org", config.getImsOrg());
        assertEquals("prod", config.getSandboxName());
        assertEquals("client-a", config.getClientId());
    }

    @Test
    @DisplayName("Whitespace in imsOrg, sandboxName, and clientId is trimmed")
    void testWhitespaceIsTrimmed() throws FlagInitException {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain(TEST_EDGE_DOMAIN)
                        .imsOrg("  my-org  ")
                        .sandboxName("  prod  ")
                        .clientId("  my-app  ")
                        .build();

        assertEquals("my-org", config.getImsOrg());
        assertEquals("prod", config.getSandboxName());
        assertEquals("my-app", config.getClientId());
    }

    @Test
    @DisplayName("toString does not expose sensitive fields")
    void testToStringNoSensitiveFields() throws FlagInitException {
        FlagConfiguration config =
                FlagConfiguration.builder()
                        .edgeDomain(TEST_EDGE_DOMAIN)
                        .imsOrg("secret-org")
                        .sandboxName("secret-sandbox")
                        .clientId("my-app")
                        .build();

        String str = config.toString();
        assertFalse(str.contains("secret-org"), "toString should not expose imsOrg");
        assertFalse(str.contains("secret-sandbox"), "toString should not expose sandboxName");
    }

    @Nested
    @DisplayName("httpClient configuration")
    class HttpClientTests {

        @Test
        @DisplayName("httpClient defaults to null when not set")
        void defaultsToNull() throws FlagInitException {
            FlagConfiguration config =
                    FlagConfiguration.builder()
                            .edgeDomain(TEST_EDGE_DOMAIN)
                            .imsOrg(TEST_IMS_ORG)
                            .sandboxName(TEST_SANDBOX)
                            .clientId("app")
                            .build();

            assertNull(config.getHttpClient());
        }

        @Test
        @DisplayName("httpClient returns the instance set via builder")
        void returnsSuppliedClient() throws FlagInitException {
            OkHttpClient custom = new OkHttpClient();
            try {
                FlagConfiguration config =
                        FlagConfiguration.builder()
                                .edgeDomain(TEST_EDGE_DOMAIN)
                                .imsOrg(TEST_IMS_ORG)
                                .sandboxName(TEST_SANDBOX)
                                .clientId("app")
                                .httpClient(custom)
                                .build();

                assertSame(custom, config.getHttpClient());
            } finally {
                custom.dispatcher().executorService().shutdown();
                custom.connectionPool().evictAll();
            }
        }

        @Test
        @DisplayName("httpClient can be set to null explicitly")
        void allowsExplicitNull() throws FlagInitException {
            FlagConfiguration config =
                    FlagConfiguration.builder()
                            .edgeDomain(TEST_EDGE_DOMAIN)
                            .imsOrg(TEST_IMS_ORG)
                            .sandboxName(TEST_SANDBOX)
                            .clientId("app")
                            .httpClient(null)
                            .build();

            assertNull(config.getHttpClient());
        }
    }
}
