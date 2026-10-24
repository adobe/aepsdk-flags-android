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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Load-style comparison between {@link RuleProcessor} and {@link FilterService}.
 *
 * <p>Default {@code mvn test} skips this class. Run explicitly: {@code mvn test -DrunLoadTests=true
 * -Dtest=RuleProcessorVsFilterServiceLoadTest}
 */
@EnabledIfSystemProperty(named = "runLoadTests", matches = "true")
class RuleProcessorVsFilterServiceLoadTest {

    private static final int WARMUP = 10_000;
    private static final int ITERATIONS = 200_000;

    private static final Map<String, String> FIELD_TYPES = new HashMap<>();
    private static final String CRITERIA =
            "{\"criteria\":{\"and\":["
                    + "{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"US\"},"
                    + "{\"attr\":\"tier\",\"operator\":\"IN\",\"val\":[\"gold\",\"platinum\"]}"
                    + "]}}";

    @BeforeAll
    static void types() {
        FIELD_TYPES.put("country", "STRING");
        FIELD_TYPES.put("tier", "STRING");
    }

    @Test
    void ruleProcessorMatchesFilterServiceOutcomes() {
        RuleProcessor ruleProcessor = new RuleProcessor(FIELD_TYPES);
        FilterService filterService = new FilterService(FIELD_TYPES);
        UserAttributes matching =
                UserAttributes.fromContext(
                        Map.of(
                                "country", List.of("US"),
                                "tier", List.of("gold")));
        UserAttributes nonMatching =
                UserAttributes.fromContext(
                        Map.of(
                                "country", List.of("US"),
                                "tier", List.of("bronze")));

        assertEquals(
                ruleProcessor.evaluate(CRITERIA, matching),
                filterService.matchesCriteria(CRITERIA, matching));
        assertEquals(
                ruleProcessor.evaluate(CRITERIA, nonMatching),
                filterService.matchesCriteria(CRITERIA, nonMatching));
        assertEquals(
                ruleProcessor.evaluate(CRITERIA, matching),
                filterService.evaluateCriteriaCached(matching, "load:logical", CRITERIA, "h1"));
    }

    @Test
    void throughputComparisonPrinted() {
        RuleProcessor ruleProcessor = new RuleProcessor(FIELD_TYPES);
        FilterService filterService = new FilterService(FIELD_TYPES);
        UserAttributes user =
                UserAttributes.fromContext(
                        Map.of(
                                "country", List.of("US"),
                                "tier", List.of("platinum")));

        for (int i = 0; i < WARMUP; i++) {
            ruleProcessor.evaluate(CRITERIA, user);
            filterService.matchesCriteria(CRITERIA, user);
            filterService.evaluateCriteriaCached(user, "bench:id", CRITERIA, "bench-hash");
        }

        long t0 = System.nanoTime();
        boolean acc = false;
        for (int i = 0; i < ITERATIONS; i++) {
            acc ^= ruleProcessor.evaluate(CRITERIA, user);
        }
        long ruleProcessorNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc ^= filterService.matchesCriteria(CRITERIA, user);
        }
        long filterColdNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc ^= filterService.evaluateCriteriaCached(user, "bench:id", CRITERIA, "bench-hash");
        }
        long filterCachedNs = System.nanoTime() - t0;

        assertTrue(acc || !acc);

        long reMs = TimeUnit.NANOSECONDS.toMillis(ruleProcessorNs);
        long fcMs = TimeUnit.NANOSECONDS.toMillis(filterColdNs);
        long fcacheMs = TimeUnit.NANOSECONDS.toMillis(filterCachedNs);

        System.out.println("[load] RuleProcessor " + ITERATIONS + " evals: " + reMs + " ms");
        System.out.println(
                "[load] FilterService.matchesCriteria (parse each call) "
                        + ITERATIONS
                        + " evals: "
                        + fcMs
                        + " ms");
        System.out.println(
                "[load] FilterService.evaluateCriteriaCached (warm) "
                        + ITERATIONS
                        + " evals: "
                        + fcacheMs
                        + " ms");
    }
}
