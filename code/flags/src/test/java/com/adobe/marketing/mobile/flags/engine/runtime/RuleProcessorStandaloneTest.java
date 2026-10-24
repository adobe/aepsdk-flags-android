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

package com.adobe.marketing.mobile.flags.engine.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rule processor standalone evaluation")
class RuleProcessorStandaloneTest {

    @Test
    @DisplayName("Basic operators: EQ, GT, IN")
    void testBasicOperators() {
        RuleProcessor engine =
                new RuleProcessor(
                        Map.of("country", "STRING", "age", "INTEGER", "platform", "STRING"));

        String eqCriteria =
                "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"}}";
        assertTrue(engine.evaluate(eqCriteria, new UserAttributes().addAttribute("country", "US")));
        assertFalse(
                engine.evaluate(eqCriteria, new UserAttributes().addAttribute("country", "UK")));

        String gtCriteria =
                "{\"criteria\": {\"attr\": \"age\", \"operator\": \"GT\", \"val\": 18}}";
        assertTrue(engine.evaluate(gtCriteria, new UserAttributes().addAttribute("age", "25")));
        assertFalse(engine.evaluate(gtCriteria, new UserAttributes().addAttribute("age", "16")));

        String inCriteria =
                "{\"criteria\": {\"attr\": \"platform\", \"operator\": \"IN\", \"val\": [\"iOS\","
                        + " \"Android\"]}}";
        assertTrue(
                engine.evaluate(
                        inCriteria, new UserAttributes().addAttribute("platform", "Android")));
        assertFalse(
                engine.evaluate(inCriteria, new UserAttributes().addAttribute("platform", "Web")));
    }

    @Test
    @DisplayName("Complex nested AND/OR rules")
    void testComplexNestedRules() {
        RuleProcessor engine =
                new RuleProcessor(
                        Map.of("country", "STRING", "age", "INTEGER", "premium", "BOOLEAN"));

        String criteria =
                "{\"criteria\": {\"and\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"or\": ["
                        + "  {\"attr\": \"age\", \"operator\": \"GT\", \"val\": 21},"
                        + "  {\"attr\": \"premium\", \"operator\": \"EQ\", \"val\": true}"
                        + "]}"
                        + "]}}";

        assertTrue(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "US")
                                .addAttribute("age", "30")
                                .addAttribute("premium", "false")));

        assertTrue(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "US")
                                .addAttribute("age", "18")
                                .addAttribute("premium", "true")));

        assertFalse(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "US")
                                .addAttribute("age", "18")
                                .addAttribute("premium", "false")));

        assertFalse(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "UK")
                                .addAttribute("age", "30")
                                .addAttribute("premium", "true")));
    }

    @Test
    @DisplayName("Policy evaluation is deterministic")
    void testPolicyDeterminism() {
        String userId = "test_user_123";
        var result1 = PolicyEvaluator.getPolicyVariant(100, userId, 50);
        var result2 = PolicyEvaluator.getPolicyVariant(100, userId, 50);

        assertEquals(result1.getVariantId(), result2.getVariantId());
        assertEquals(result1.isControlGroup(), result2.isControlGroup());
    }

    @Test
    @DisplayName("NOT/NEGATE operator inverts child result")
    void testNotNegate() {
        RuleProcessor engine = new RuleProcessor(Map.of("country", "STRING"));

        String criteria =
                "{\"criteria\": {\"not\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"}"
                        + "]}}";

        assertFalse(engine.evaluate(criteria, new UserAttributes().addAttribute("country", "US")));
        assertTrue(engine.evaluate(criteria, new UserAttributes().addAttribute("country", "UK")));
    }

    @Test
    @DisplayName("NOT combined with AND: excludes matching users")
    void testNotWithAnd() {
        RuleProcessor engine = new RuleProcessor(Map.of("country", "STRING", "banned", "BOOLEAN"));

        String criteria =
                "{\"criteria\": {\"and\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"not\": [{\"attr\": \"banned\", \"operator\": \"EQ\", \"val\": true}]}"
                        + "]}}";

        assertTrue(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "US")
                                .addAttribute("banned", "false")));
        assertFalse(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "US")
                                .addAttribute("banned", "true")));
        assertFalse(
                engine.evaluate(
                        criteria,
                        new UserAttributes()
                                .addAttribute("country", "UK")
                                .addAttribute("banned", "false")));
    }

    @Test
    @DisplayName("Performance: 10K evaluations under 1 second")
    void testPerformance() {
        RuleProcessor engine =
                new RuleProcessor(
                        Map.of(
                                "country",
                                "STRING",
                                "age",
                                "INTEGER",
                                "platform",
                                "STRING",
                                "appVersion",
                                "VERSION"));

        String criteria =
                "{\"criteria\": {\"and\": [{\"attr\": \"country\", \"operator\": \"IN\", \"val\":"
                    + " [\"US\", \"CA\", \"UK\", \"DE\", \"FR\"]},{\"attr\": \"age\", \"operator\":"
                    + " \"BW\", \"val\": [18, 65]},{\"attr\": \"platform\", \"operator\": \"EQ\","
                    + " \"val\": \"Android\"},{\"attr\": \"appVersion\", \"operator\":"
                    + " \"VERSION_GT\", \"val\": \"1.0.0\"}]}}";

        UserAttributes user =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "30")
                        .addAttribute("platform", "Android")
                        .addAttribute("appVersion", "2.5.0");

        for (int i = 0; i < 1000; i++) {
            engine.evaluate(criteria, user);
        }

        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            engine.evaluate(criteria, user);
        }
        long elapsed = System.nanoTime() - start;
        double totalMs = elapsed / 1_000_000.0;
        double avgMicros = elapsed / 1000.0 / iterations;

        System.out.printf("Average evaluation time: %.2f microseconds%n", avgMicros);
        assertTrue(totalMs < 1000, "10K evaluations should complete in < 1 second");
    }
}
