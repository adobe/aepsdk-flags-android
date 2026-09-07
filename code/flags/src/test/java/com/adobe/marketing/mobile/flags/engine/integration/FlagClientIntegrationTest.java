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

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import com.adobe.marketing.mobile.flags.engine.runtime.FilterService;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

/** Integration tests using MockWebServer to simulate Edge/API responses. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlagClientIntegrationTest {

    private static MockWebServer mockServer;

    @BeforeAll
    static void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    @Order(1)
    @DisplayName("MockWebServer is available for integration tests")
    void testMockServerAvailable() {
        assertNotNull(mockServer);
        assertTrue(mockServer.getPort() > 0);
    }

    @Test
    @Order(2)
    @DisplayName("Local evaluation should work with cached data")
    void testLocalEvaluation() {
        var filterService =
                new FilterService(java.util.Map.of("country", "STRING", "age", "INTEGER"));

        String criteria =
                "{\"criteria\": {\"and\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"attr\": \"age\", \"operator\": \"GT\", \"val\": 18}"
                        + "]}}";

        var matchingUser =
                new UserAttributes().addAttribute("country", "US").addAttribute("age", "25");
        assertTrue(filterService.matchesCriteria(criteria, matchingUser));

        var nonMatchingUser =
                new UserAttributes().addAttribute("country", "UK").addAttribute("age", "25");
        assertFalse(filterService.matchesCriteria(criteria, nonMatchingUser));
    }

    @Test
    @Order(3)
    @DisplayName("Policy evaluation should be deterministic")
    void testPolicyEvaluator() {

        // Same user should always get same variant
        String userId = "test_user_123";
        Integer policyId = 100;

        var result1 = PolicyEvaluator.getPolicyVariant(policyId, userId, 50);
        var result2 = PolicyEvaluator.getPolicyVariant(policyId, userId, 50);

        assertEquals(result1.getVariantId(), result2.getVariantId());
        assertEquals(result1.isControlGroup(), result2.isControlGroup());
    }

    @Test
    @Order(4)
    @DisplayName("Performance benchmark - 10K evaluations")
    void testPerformanceBenchmark() {
        var filterService =
                new FilterService(
                        java.util.Map.of(
                                "country", "STRING",
                                "age", "INTEGER",
                                "platform", "STRING",
                                "appVersion", "VERSION"));

        String criteria =
                "{\"criteria\": {\"and\": [{\"attr\": \"country\", \"operator\": \"IN\", \"val\":"
                    + " [\"US\", \"CA\", \"UK\", \"DE\", \"FR\"]},{\"attr\": \"age\", \"operator\":"
                    + " \"BW\", \"val\": [18, 65]},{\"attr\": \"platform\", \"operator\": \"EQ\","
                    + " \"val\": \"Android\"},{\"attr\": \"appVersion\", \"operator\":"
                    + " \"VERSION_GT\", \"val\": \"1.0.0\"}]}}";

        var user =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "30")
                        .addAttribute("platform", "Android")
                        .addAttribute("appVersion", "2.5.0");

        // Warm up
        for (int i = 0; i < 1000; i++) {
            filterService.matchesCriteria(criteria, user);
        }

        // Benchmark
        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            filterService.matchesCriteria(criteria, user);
        }
        long end = System.nanoTime();

        double avgMicroseconds = (end - start) / 1000.0 / iterations;
        double totalMs = (end - start) / 1_000_000.0;

        System.out.printf("Total time for %d evaluations: %.2f ms%n", iterations, totalMs);
        System.out.printf("Average per evaluation: %.2f microseconds%n", avgMicroseconds);
        System.out.printf("Throughput: %.0f evaluations/second%n", iterations / (totalMs / 1000));

        // Assert performance requirements
        assertTrue(avgMicroseconds < 100, "Each evaluation should be < 100 microseconds");
        assertTrue(totalMs < 1000, "10K evaluations should complete in < 1 second");
    }
}
