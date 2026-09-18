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

package com.adobe.marketing.mobile.flags.engine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.cache.SDKClientCache;
import com.adobe.marketing.mobile.flags.engine.common.CohortingType;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import com.adobe.marketing.mobile.flags.engine.runtime.FilterService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for FeatureEvaluator — the core evaluation engine. */
class FeatureEvaluatorTest {

    private static final String CLIENT_ID = "test-client";

    private static final String DEFAULT_COHORTING_NAMESPACE = CohortingType.ECID.getValue();

    private static Map<String, Object> ecidBucketingParams() {
        return Map.of(Constants.JSON_KEY_COHORTING_TYPE, DEFAULT_COHORTING_NAMESPACE);
    }

    private static void enableReleaseBucketing(FeaturesResponse featureGroup) {
        featureGroup.setParams(ecidBucketingParams());
    }

    private SDKClientCache cache;
    private PolicyCache policyCache;
    private FeatureEvaluator evaluator;

    @BeforeEach
    void setUp() {
        cache = new SDKClientCache();
        policyCache = new PolicyCache();
        evaluator = new FeatureEvaluator(CLIENT_ID, cache, policyCache);
    }

    private GetFeatureRequest emptyRequest() {
        return GetFeatureRequest.DEFAULT;
    }

    private GetFeatureRequest requestWithContext(Map<String, List<String>> context) {
        return new GetFeatureRequest.Builder().context(context).build();
    }

    private Map<String, List<Map<String, Object>>> identityMapWithNamespace(
            String namespace, String id) {
        Map<String, Object> entry =
                Map.of(
                        Constants.IDENTITY_ENTRY_KEY_ID,
                        id,
                        Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                        true,
                        Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                        "ambiguous");
        return Map.of(namespace, List.of(entry));
    }

    private GetFeatureRequest requestWithIdentityMap(
            Map<String, List<Map<String, Object>>> identityMap) {
        return new GetFeatureRequest.Builder().identityMap(identityMap).build();
    }

    private GetFeatureRequest requestWithNamespaceIdentity(String namespace, String id) {
        return requestWithIdentityMap(identityMapWithNamespace(namespace, id));
    }

    private FeaturesResponse releaseWithStringFeatures(String featureGroupName, String... names) {
        FeaturesResponse featureGroup = new FeaturesResponse();
        featureGroup.setFeatureGroupName(featureGroupName);
        featureGroup.setFeatures(names);
        return featureGroup;
    }

    private FeaturesResponse releaseWithFeatureObjects(
            String featureGroupName, Feature... features) {
        FeaturesResponse featureGroup = new FeaturesResponse();
        featureGroup.setFeatureGroupName(featureGroupName);
        featureGroup.setFeaturesObj(features);
        return featureGroup;
    }

    private FeaturesResponse releaseWithIdAndFeatures(
            int featureGroupId, String featureGroupName, Feature... features) {
        FeaturesResponse featureGroup = new FeaturesResponse();
        featureGroup.setFeatureGroupId(featureGroupId);
        featureGroup.setFeatureGroupName(featureGroupName);
        featureGroup.setFeaturesObj(features);
        return featureGroup;
    }

    private FeaturesResponse releaseWithIdAndStringFeatures(
            int featureGroupId, String featureGroupName, String... names) {
        FeaturesResponse featureGroup = new FeaturesResponse();
        featureGroup.setFeatureGroupId(featureGroupId);
        featureGroup.setFeatureGroupName(featureGroupName);
        featureGroup.setFeatures(names);
        return featureGroup;
    }

    private Feature featureWith(String name) {
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

    private Feature featureWithCriteria(String name, String criteria) {
        Feature f = new Feature();
        f.setFeature(name);
        f.setCriteria(criteria);
        return f;
    }

    private Feature featureWithId(String name, int id) {
        Feature f = new Feature();
        f.setId(id);
        f.setFeature(name);
        return f;
    }

    private Feature featureWithPolicy(String name, int policyId) {
        Feature f = new Feature();
        f.setFeature(name);
        f.setPolicyId(policyId);
        f.setParams(Map.of(Constants.JSON_KEY_COHORTING_TYPE, CohortingType.ECID.getValue()));
        return f;
    }

    private Feature featureWithIdAndPolicy(String name, int id, int policyId) {
        Feature f = new Feature();
        f.setId(id);
        f.setFeature(name);
        f.setPolicyId(policyId);
        f.setParams(ecidBucketingParams());
        return f;
    }

    private void putReleases(FeaturesResponse... featureGroups) {
        cache.putFeatures(CLIENT_ID, featureGroups, "etag-1");
    }

    /** Brute-force an identifier that lands in a control cohort for the given cached policy. */
    private String findIdentifierWithPolicyControlOutcome(int policyId) {
        for (int i = 0; i < 200_000; i++) {
            String identifier = "ctrl-seek_" + i;
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, policyId, identifier);
            if (r.isControlGroup()) {
                return identifier;
            }
        }
        return null;
    }

    private FilterService filterServiceWithTypes(String... keyTypePairs) {
        Map<String, String> fieldDataTypes = new HashMap<>();
        for (int i = 0; i < keyTypePairs.length; i += 2) {
            fieldDataTypes.put(keyTypePairs[i], keyTypePairs[i + 1]);
        }
        return new FilterService(fieldDataTypes);
    }

    @Nested
    @DisplayName("Empty / missing cache")
    class EmptyCacheTests {

        @Test
        @DisplayName("evaluateAll returns empty array when no cache entry exists")
        void testEvaluateAllNoCache() {
            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertNotNull(result);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("evaluate returns null when no cache entry exists")
        void testEvaluateNoCache() {
            assertNull(evaluator.evaluate("any-feature", emptyRequest()));
        }

        @Test
        @DisplayName("isEnabled returns false when no cache entry exists")
        void testIsEnabledNoCache() {
            assertFalse(evaluator.isEnabled("any-feature", emptyRequest()));
        }
    }

    @Nested
    @DisplayName("String-only features (no Feature objects)")
    class StringFeatureTests {

        @Test
        @DisplayName("Returns features created from string names")
        void testStringFeatures() {
            putReleases(releaseWithStringFeatures("R1", "feat-a", "feat-b"));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(2, result.length);
            assertEquals("feat-a", result[0].getKey());
            assertEquals("feat-b", result[1].getKey());
        }

        @Test
        @DisplayName("evaluate finds feature by string name")
        void testEvaluateByName() {
            putReleases(releaseWithStringFeatures("R1", "feat-a", "feat-b"));

            FeatureResult found = evaluator.evaluate("feat-b", emptyRequest());
            assertNotNull(found);
            assertEquals("feat-b", found.getKey());
        }

        @Test
        @DisplayName("isEnabled returns true for existing string feature")
        void testIsEnabledStringFeature() {
            putReleases(releaseWithStringFeatures("R1", "feat-a"));
            assertTrue(evaluator.isEnabled("feat-a", emptyRequest()));
        }

        @Test
        @DisplayName("isEnabled returns false for unknown feature")
        void testIsEnabledUnknownFeature() {
            putReleases(releaseWithStringFeatures("R1", "feat-a"));
            assertFalse(evaluator.isEnabled("nonexistent", emptyRequest()));
        }
    }

    @Nested
    @DisplayName("Feature objects (no criteria, no policy)")
    class BasicFeatureObjectTests {

        @Test
        @DisplayName("Returns all Feature objects when no criteria or policy")
        void testAllFeaturesReturned() {
            putReleases(
                    releaseWithFeatureObjects(
                            "R1", featureWith("dark-mode"), featureWith("new-nav")));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(2, result.length);
            assertEquals("dark-mode", result[0].getKey());
            assertEquals("new-nav", result[1].getKey());
        }

        @Test
        @DisplayName("FeatureResult carries value from internal Feature")
        void testFeatureValueCarried() {
            putReleases(
                    releaseWithFeatureObjects("R1", featureWithValue("dark-mode", "variant-a")));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
            assertEquals("variant-a", result[0].getValue());
        }
    }

    @Nested
    @DisplayName("Release name stamping")
    class ReleaseNameTests {

        @Test
        @DisplayName("FeatureResult carries the featureGroup key")
        void testReleaseNameStamped() {
            putReleases(releaseWithFeatureObjects("Release_V2", featureWith("dark-mode")));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
            assertEquals("Release_V2", result[0].getFeatureGroupKey());
        }

        @Test
        @DisplayName("String features also carry the featureGroup name")
        void testReleaseNameOnStringFeatures() {
            putReleases(releaseWithStringFeatures("Beta_Release", "feat-a"));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
            assertEquals("Beta_Release", result[0].getFeatureGroupKey());
        }

        @Test
        @DisplayName(
                "Features from different featureGroups carry their respective featureGroup names")
        void testMultipleReleasesStamped() {
            putReleases(
                    releaseWithStringFeatures("R1", "feat-a"),
                    releaseWithFeatureObjects("R2", featureWith("feat-b")));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(2, result.length);
            assertEquals("R1", result[0].getFeatureGroupKey());
            assertEquals("R2", result[1].getFeatureGroupKey());
        }
    }

    @Nested
    @DisplayName("Multiple featureGroups")
    class MultipleReleaseTests {

        @Test
        @DisplayName("Features from all matching featureGroups are aggregated")
        void testAggregation() {
            putReleases(
                    releaseWithStringFeatures("R1", "feat-a"),
                    releaseWithFeatureObjects("R2", featureWith("feat-b")),
                    releaseWithStringFeatures("R3", "feat-c"));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(3, result.length);
            assertEquals("feat-a", result[0].getKey());
            assertEquals("feat-b", result[1].getKey());
            assertEquals("feat-c", result[2].getKey());
        }
    }

    @Nested
    @DisplayName("Release-level criteria evaluation")
    class ReleaseCriteriaTests {

        @BeforeEach
        void setUpFilterService() {
            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));
        }

        @Test
        @DisplayName("Release included when criteria match user context")
        void testCriteriaMatch() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            putReleases(featureGroup);

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(1, result.length);
            assertEquals("feat-a", result[0].getKey());
        }

        @Test
        @DisplayName("Release excluded when criteria do not match")
        void testCriteriaNoMatch() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            putReleases(featureGroup);

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("UK")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("Release with empty criteria is always included")
        void testEmptyCriteria() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria("");
            putReleases(featureGroup);

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
        }

        @Test
        @DisplayName("Release with null criteria is always included")
        void testNullCriteria() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(null);
            putReleases(featureGroup);

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
        }
    }

    @Nested
    @DisplayName("Feature-level criteria evaluation")
    class FeatureCriteriaTests {

        @BeforeEach
        void setUpFilterService() {
            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));
        }

        @Test
        @DisplayName("Only features matching criteria are returned")
        void testFeatureCriteriaFiltering() {
            Feature matchingFeature =
                    featureWithCriteria(
                            "us-only",
                            "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                                    + " \"US\"}}");
            Feature nonMatchingFeature =
                    featureWithCriteria(
                            "uk-only",
                            "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                                    + " \"UK\"}}");
            Feature noCriteriaFeature = featureWith("global");

            putReleases(
                    releaseWithFeatureObjects(
                            "R1", matchingFeature, nonMatchingFeature, noCriteriaFeature));

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(2, result.length);
            assertEquals("us-only", result[0].getKey());
            assertEquals("global", result[1].getKey());
        }

        @Test
        @DisplayName("Feature with criteria excluded when context does not match")
        void testFeatureCriteriaExclusion() {
            Feature feature =
                    featureWithCriteria(
                            "premium-only",
                            "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                                    + " \"JP\"}}");
            putReleases(releaseWithFeatureObjects("R1", feature));

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(0, result.length);
        }
    }

    /**
     * One focused test per relational operator, exercising {@link FeatureEvaluator} with {@link
     * SDKClientCache}, {@link FilterService} field types, and {@link GetFeatureRequest} context
     * (the same path production uses, not {@link FilterService#matchesCriteria} alone).
     */
    @Nested
    @DisplayName("Relational operators (GT/LT/GE/LE) via evaluator + cache + GetFeatureRequest")
    class RelationalOperatorEndToEndTests {

        @BeforeEach
        void setUpFilterService() {
            evaluator.setFilterService(
                    filterServiceWithTypes(
                            "metric", "DECIMAL",
                            "days", "INTEGER"));
        }

        @Test
        @DisplayName("GT: DECIMAL metric — included only when strictly greater than threshold")
        void gt_decimal_throughEvaluator() {
            Feature f =
                    featureWithCriteria(
                            "rel-gt",
                            "{\"criteria\":{\"attr\":\"metric\",\"operator\":\"GT\",\"val\":10}}");
            f.setId(501);
            putReleases(releaseWithFeatureObjects("R-rel", f));

            GetFeatureRequest above = requestWithContext(Map.of("metric", List.of("16.4")));
            assertEquals(1, evaluator.evaluateAll(above).length);
            assertNotNull(evaluator.evaluate("rel-gt", above));
            assertTrue(evaluator.isEnabled("rel-gt", above));

            GetFeatureRequest atThreshold = requestWithContext(Map.of("metric", List.of("10")));
            assertEquals(0, evaluator.evaluateAll(atThreshold).length);
            assertNull(evaluator.evaluate("rel-gt", atThreshold));
            assertFalse(evaluator.isEnabled("rel-gt", atThreshold));
        }

        @Test
        @DisplayName("LT: INTEGER days — included only when strictly less than threshold")
        void lt_integer_throughEvaluator() {
            Feature f =
                    featureWithCriteria(
                            "rel-lt",
                            "{\"criteria\":{\"attr\":\"days\",\"operator\":\"LT\",\"val\":20}}");
            f.setId(502);
            putReleases(releaseWithFeatureObjects("R-rel", f));

            GetFeatureRequest below = requestWithContext(Map.of("days", List.of("5")));
            assertEquals(1, evaluator.evaluateAll(below).length);
            assertNotNull(evaluator.evaluate("rel-lt", below));
            assertTrue(evaluator.isEnabled("rel-lt", below));

            GetFeatureRequest atThreshold = requestWithContext(Map.of("days", List.of("20")));
            assertEquals(0, evaluator.evaluateAll(atThreshold).length);
            assertNull(evaluator.evaluate("rel-lt", atThreshold));
            assertFalse(evaluator.isEnabled("rel-lt", atThreshold));
        }

        @Test
        @DisplayName("GE (GTE): DECIMAL metric — includes equality at threshold")
        void ge_decimal_throughEvaluator() {
            Feature f =
                    featureWithCriteria(
                            "rel-ge",
                            "{\"criteria\":{\"attr\":\"metric\",\"operator\":\"GE\",\"val\":15.8}}");
            f.setId(503);
            putReleases(releaseWithFeatureObjects("R-rel", f));

            GetFeatureRequest equal = requestWithContext(Map.of("metric", List.of("15.8")));
            assertEquals(1, evaluator.evaluateAll(equal).length);
            assertNotNull(evaluator.evaluate("rel-ge", equal));
            assertTrue(evaluator.isEnabled("rel-ge", equal));

            GetFeatureRequest below = requestWithContext(Map.of("metric", List.of("15.7")));
            assertEquals(0, evaluator.evaluateAll(below).length);
            assertNull(evaluator.evaluate("rel-ge", below));
            assertFalse(evaluator.isEnabled("rel-ge", below));
        }

        @Test
        @DisplayName("LE (LTE): INTEGER days — includes equality at threshold")
        void le_integer_throughEvaluator() {
            Feature f =
                    featureWithCriteria(
                            "rel-le",
                            "{\"criteria\":{\"attr\":\"days\",\"operator\":\"LE\",\"val\":10}}");
            f.setId(504);
            putReleases(releaseWithFeatureObjects("R-rel", f));

            GetFeatureRequest equal = requestWithContext(Map.of("days", List.of("10")));
            assertEquals(1, evaluator.evaluateAll(equal).length);
            assertNotNull(evaluator.evaluate("rel-le", equal));
            assertTrue(evaluator.isEnabled("rel-le", equal));

            GetFeatureRequest above = requestWithContext(Map.of("days", List.of("11")));
            assertEquals(0, evaluator.evaluateAll(above).length);
            assertNull(evaluator.evaluate("rel-le", above));
            assertFalse(evaluator.isEnabled("rel-le", above));
        }
    }

    @Nested
    @DisplayName("No filter service set")
    class NoFilterServiceTests {

        @Test
        @DisplayName("Criteria block evaluation when no filter service is configured")
        void testCriteriaIgnoredWithoutFilterService() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            putReleases(featureGroup);

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(0, result.length, "Criteria with no filter service must fail-closed");
        }

        @Test
        @DisplayName("Feature-level criteria block evaluation when no filter service is configured")
        void testFeatureCriteriaIgnoredWithoutFilterService() {
            Feature feature =
                    featureWithCriteria(
                            "feat-a",
                            "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                                    + " \"US\"}}");
            putReleases(releaseWithFeatureObjects("R1", feature));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(
                    0, result.length, "Feature criteria with no filter service must fail-closed");
        }
    }

    @Nested
    @DisplayName("Release-level A/B policy evaluation")
    class ReleasePolicyTests {

        @Test
        @DisplayName("Release excluded when bucketing identifier is in control group")
        void testReleasePolicyControlGroup() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setPolicyId(999);
            enableReleaseBucketing(featureGroup);
            putReleases(featureGroup);

            boolean anyExcluded = false;
            for (int i = 0; i < 100; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, "control-test-" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                if (result.length == 0) {
                    anyExcluded = true;
                    break;
                }
            }
            assertTrue(anyExcluded, "At least one identifier should be in control group");
        }

        @Test
        @DisplayName("Release included when bucketing identifier is in treatment group")
        void testReleasePolicyTreatmentGroup() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setPolicyId(999);
            enableReleaseBucketing(featureGroup);
            putReleases(featureGroup);

            boolean anyIncluded = false;
            for (int i = 0; i < 100; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, "treatment-test-" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                if (result.length > 0) {
                    anyIncluded = true;
                    break;
                }
            }
            assertTrue(anyIncluded, "At least one identifier should be in treatment group");
        }

        @Test
        @DisplayName("Release with null policyId is always included")
        void testNullPolicyAlwaysIncluded() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setPolicyId(null);
            putReleases(featureGroup);

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
        }
    }

    @Nested
    @DisplayName("Feature-level A/B policy evaluation")
    class FeaturePolicyTests {

        @Test
        @DisplayName(
                "Feature-level control returns sentinel FeatureResult (null key) + AnalyticsParam")
        void testFeaturePolicyControlGroup() {
            Feature feature = featureWithPolicy("ab-feature", 888);
            putReleases(releaseWithFeatureObjects("R1", feature));

            boolean anyControlSentinel = false;
            for (int i = 0; i < 200; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, "fp-control-" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                assertEquals(1, result.length);
                FeatureResult row = result[0];
                if (row.getKey() == null && row.getId() == -1) {
                    assertNotNull(row.getAnalyticsParam());
                    assertFalse(evaluator.isEnabled("ab-feature", req));
                    anyControlSentinel = true;
                    break;
                }
            }
            assertTrue(
                    anyControlSentinel,
                    "At least one identifier should get feature-policy control sentinel row");
        }

        @Test
        @DisplayName("Feature with null policyId is always included")
        void testFeatureNullPolicyAlwaysIncluded() {
            Feature feature = featureWith("no-policy");
            putReleases(releaseWithFeatureObjects("R1", feature));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);
        }
    }

    @Nested
    @DisplayName("Cached PolicyDetail end-to-end evaluation")
    class CachedPolicyEvaluationTests {

        @Test
        @DisplayName(
                "Release with cached 30/70 policy produces correct bucketing through evaluator")
        void testReleasePolicyFromCache() {
            var buckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 2999, 30),
                            new PolicyCache.PolicyBucket("1", 3000, 9999, 70));
            policyCache.putPolicy(
                    500, new PolicyCache.PolicyDetail(500, "MURMUR_HASH", "500", buckets, null));

            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setPolicyId(500);
            enableReleaseBucketing(featureGroup);
            putReleases(featureGroup);

            int controlCount = 0;
            int totalUsers = 10000;
            for (int i = 0; i < totalUsers; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "user_" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                if (result.length == 0) {
                    controlCount++;
                }
            }

            double controlPct = (controlCount * 100.0) / totalUsers;
            assertTrue(
                    controlPct > 25 && controlPct < 35,
                    "Cached 30/70 policy should exclude ~30% (control), was: " + controlPct + "%");
        }

        @Test
        @DisplayName(
                "Feature with cached 50/50 policy produces correct bucketing through evaluator")
        void testFeaturePolicyFromCache() {
            var buckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 4999, 50),
                            new PolicyCache.PolicyBucket("1", 5000, 9999, 50));
            policyCache.putPolicy(
                    600, new PolicyCache.PolicyDetail(600, "MURMUR_HASH", "600", buckets, null));

            Feature feature = featureWithPolicy("ab-feature", 600);
            putReleases(releaseWithFeatureObjects("R1", feature));

            int controlCount = 0;
            int totalUsers = 10000;
            for (int i = 0; i < totalUsers; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "user_" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                assertEquals(1, result.length);
                if (result[0].getKey() == null) {
                    controlCount++;
                }
            }

            double controlPct = (controlCount * 100.0) / totalUsers;
            assertTrue(
                    controlPct > 45 && controlPct < 55,
                    "Cached 50/50 feature policy should bucket ~50% to control sentinel, was: "
                            + controlPct
                            + "%");
        }

        @Test
        @DisplayName(
                "Policy set on model simulates ResponseParser → SDKCacheManager.cachePolicies()"
                        + " pipeline")
        void testPolicyCachePipelineEndToEnd() {
            var buckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 2999, 30),
                            new PolicyCache.PolicyBucket("1", 3000, 9999, 70));
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(700, "MURMUR_HASH", "700", buckets, null);

            FeaturesResponse featureGroup = releaseWithFeatureObjects("R1", featureWith("feat-a"));
            featureGroup.setPolicyId(700);
            featureGroup.setPolicy(detail);
            enableReleaseBucketing(featureGroup);

            if (featureGroup.getPolicyId() != null && featureGroup.getPolicy() != null) {
                policyCache.putPolicy(featureGroup.getPolicyId(), featureGroup.getPolicy());
            }

            putReleases(featureGroup);

            int controlCount = 0;
            int totalUsers = 10000;
            for (int i = 0; i < totalUsers; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "user_" + i);
                FeatureResult[] result = evaluator.evaluateAll(req);
                if (result.length == 0) {
                    controlCount++;
                }
            }

            double controlPct = (controlCount * 100.0) / totalUsers;
            assertTrue(
                    controlPct > 25 && controlPct < 35,
                    "Pipeline should produce ~30% control, was: " + controlPct + "%");
        }
    }

    @Nested
    @DisplayName("Identity map bucketing consistency")
    class IdentityMapBucketingTests {

        @Test
        @DisplayName("Same identity id always produces same evaluation result")
        void testDeterministicBucketing() {
            Feature feature = featureWithPolicy("ab-test", 777);
            putReleases(releaseWithFeatureObjects("R1", feature));

            String identityId = "stable-bucket-42";
            GetFeatureRequest req =
                    requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, identityId);

            boolean firstResult = evaluator.isEnabled("ab-test", req);

            for (int i = 0; i < 50; i++) {
                assertEquals(
                        firstResult,
                        evaluator.isEnabled("ab-test", req),
                        "Same identity id must always produce the same bucketing result");
            }
        }

        @Test
        @DisplayName("Different identity ids can produce different results")
        void testDifferentIdentityIdsCanDiffer() {
            Feature feature = featureWithPolicy("ab-test", 555);
            putReleases(releaseWithFeatureObjects("R1", feature));

            boolean seenTrue = false;
            boolean seenFalse = false;
            for (int i = 0; i < 200; i++) {
                GetFeatureRequest req =
                        requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "bucket-" + i);
                if (evaluator.isEnabled("ab-test", req)) {
                    seenTrue = true;
                } else {
                    seenFalse = true;
                }
                if (seenTrue && seenFalse) break;
            }
            assertTrue(
                    seenTrue && seenFalse,
                    "Different identity ids should distribute across treatment and control");
        }

        @Test
        @DisplayName("Feature policy bucketing uses id from matching namespace in identityMap")
        void testBucketingUsesIdentityMapId() {
            policyCache.putPolicy(
                    801,
                    new PolicyCache.PolicyDetail(
                            801,
                            "MURMUR_HASH",
                            "801",
                            List.of(
                                    new PolicyCache.PolicyBucket("", 0, 4999, 50),
                                    new PolicyCache.PolicyBucket("1", 5000, 9999, 50)),
                            null));
            Feature feature = featureWithPolicy("id-map-test", 801);
            putReleases(releaseWithFeatureObjects("R1", feature));

            String identityId = "abc123";
            GetFeatureRequest req =
                    requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, identityId);
            PolicyEvaluator.PolicyVariantResponse expected =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 801, identityId);

            assertEquals(!expected.isControlGroup(), evaluator.isEnabled("id-map-test", req));
        }

        @Test
        @DisplayName(
                "Feature policy uses feature cohortingNamespaceCode, not featureGroup"
                        + " cohortingNamespaceCode")
        void testFeaturePolicyUsesFeatureCohortingNamespace() {
            policyCache.putPolicy(
                    902,
                    new PolicyCache.PolicyDetail(
                            902,
                            "MURMUR_HASH",
                            "902",
                            List.of(
                                    new PolicyCache.PolicyBucket("", 0, 4999, 50),
                                    new PolicyCache.PolicyBucket("1", 5000, 9999, 50)),
                            null));

            String stickyId = null;
            String ecidId = null;
            for (int i = 0; i < 200_000; i++) {
                String candidate = "cohort-seek_" + i;
                PolicyEvaluator.PolicyVariantResponse response =
                        PolicyEvaluator.getPolicyVariantFromCache(policyCache, 902, candidate);
                if (response.isControlGroup() && ecidId == null) {
                    ecidId = candidate;
                } else if (!response.isControlGroup() && stickyId == null) {
                    stickyId = candidate;
                }
                if (stickyId != null && ecidId != null) {
                    break;
                }
            }
            assertNotNull(stickyId, "expected a treatment identifier for policy 902");
            assertNotNull(ecidId, "expected a control identifier for policy 902");

            Feature feature = featureWithPolicy("cohort-ns-test", 902);
            feature.setParams(
                    Map.of(
                            Constants.JSON_KEY_COHORTING_TYPE, CohortingType.STICKY.getValue(),
                            Constants.JSON_KEY_COHORTING_NAMESPACE_CODE,
                                    CohortingType.STICKY.getValue()));

            FeaturesResponse featureGroup = releaseWithFeatureObjects("R1", feature);
            featureGroup.setParams(
                    Map.of(
                            Constants.JSON_KEY_COHORTING_TYPE, CohortingType.ECID.getValue(),
                            Constants.JSON_KEY_COHORTING_NAMESPACE_CODE,
                                    CohortingType.ECID.getValue()));
            putReleases(featureGroup);

            Map<String, List<Map<String, Object>>> identityMap =
                    Map.of(
                            CohortingType.ECID.getValue(),
                                    List.of(
                                            Map.of(
                                                    Constants.IDENTITY_ENTRY_KEY_ID,
                                                    ecidId,
                                                    Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                                                    true,
                                                    Constants
                                                            .IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                                                    "ambiguous")),
                            CohortingType.STICKY.getValue(),
                                    List.of(
                                            Map.of(
                                                    Constants.IDENTITY_ENTRY_KEY_ID,
                                                    stickyId,
                                                    Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                                                    true,
                                                    Constants
                                                            .IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                                                    "ambiguous")));

            GetFeatureRequest req = requestWithIdentityMap(identityMap);
            PolicyEvaluator.PolicyVariantResponse expectedFromSticky =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 902, stickyId);
            PolicyEvaluator.PolicyVariantResponse wouldBeFromEcid =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 902, ecidId);

            assertTrue(
                    wouldBeFromEcid.isControlGroup(), "ECID id must land in control for this test");
            assertFalse(
                    expectedFromSticky.isControlGroup(),
                    "STICKY id must land in treatment for this test");
            assertEquals(true, evaluator.isEnabled("cohort-ns-test", req));
        }

        @Test
        @DisplayName(
                "Feature policy uses cohortingNamespaceCode when cohortingType differs (loginID)")
        void testFeaturePolicyUsesCustomCohortingNamespaceCode() {
            policyCache.putPolicy(
                    903,
                    new PolicyCache.PolicyDetail(
                            903,
                            "MURMUR_HASH",
                            "903",
                            List.of(
                                    new PolicyCache.PolicyBucket("", 0, 4999, 50),
                                    new PolicyCache.PolicyBucket("1", 5000, 9999, 50)),
                            null));

            String loginId = null;
            for (int i = 0; i < 200_000; i++) {
                String candidate = "login-seek_" + i;
                PolicyEvaluator.PolicyVariantResponse response =
                        PolicyEvaluator.getPolicyVariantFromCache(policyCache, 903, candidate);
                if (!response.isControlGroup()) {
                    loginId = candidate;
                    break;
                }
            }
            assertNotNull(loginId, "expected a treatment identifier for policy 903");

            Feature feature = featureWithPolicy("login-id-test", 903);
            feature.setParams(
                    Map.of(
                            Constants.JSON_KEY_COHORTING_TYPE,
                            CohortingType.STICKY.getValue(),
                            Constants.JSON_KEY_COHORTING_NAMESPACE_CODE,
                            "loginID"));

            putReleases(releaseWithFeatureObjects("R1", feature));

            Map<String, List<Map<String, Object>>> identityMap =
                    Map.of(
                            "loginID",
                            List.of(
                                    Map.of(
                                            Constants.IDENTITY_ENTRY_KEY_ID,
                                            loginId,
                                            Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                                            true,
                                            Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                                            "authenticated")),
                            CohortingType.STICKY.getValue(),
                            List.of(
                                    Map.of(
                                            Constants.IDENTITY_ENTRY_KEY_ID, "wrong-sticky-id",
                                            Constants.IDENTITY_ENTRY_KEY_PRIMARY, true,
                                            Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                                                    "ambiguous")));

            GetFeatureRequest req = requestWithIdentityMap(identityMap);
            assertEquals(true, evaluator.isEnabled("login-id-test", req));
        }
    }

    @Nested
    @DisplayName("evaluate() and isEnabled() by name")
    class NameLookupTests {

        @BeforeEach
        void setUpCache() {
            putReleases(
                    releaseWithFeatureObjects(
                            "R1", featureWith("alpha"), featureWith("beta"), featureWith("gamma")));
        }

        @Test
        @DisplayName("evaluate finds correct feature by name")
        void testFindByName() {
            FeatureResult result = evaluator.evaluate("beta", emptyRequest());
            assertNotNull(result);
            assertEquals("beta", result.getKey());
        }

        @Test
        @DisplayName("evaluate returns null for unknown feature name")
        void testUnknownName() {
            assertNull(evaluator.evaluate("nonexistent", emptyRequest()));
        }

        @Test
        @DisplayName("evaluate returns null for null feature name")
        void testNullName() {
            assertNull(evaluator.evaluate(null, emptyRequest()));
        }

        @Test
        @DisplayName("isEnabled returns true for existing feature")
        void testIsEnabledExisting() {
            assertTrue(evaluator.isEnabled("alpha", emptyRequest()));
        }

        @Test
        @DisplayName("isEnabled returns false for unknown feature")
        void testIsEnabledUnknown() {
            assertFalse(evaluator.isEnabled("nonexistent", emptyRequest()));
        }
    }

    @Nested
    @DisplayName("Targeted evaluation (evaluate/isEnabled skip unrelated features)")
    class TargetedEvaluationTests {

        @Test
        @DisplayName("evaluate returns same result as evaluateAll for matching feature")
        void testEvaluateConsistentWithEvaluateAll() {
            putReleases(
                    releaseWithIdAndFeatures(
                            100,
                            "R1",
                            featureWithId("alpha", 1),
                            featureWithId("beta", 2),
                            featureWithId("gamma", 3)));

            FeatureResult targeted = evaluator.evaluate("beta", emptyRequest());
            FeatureResult[] all = evaluator.evaluateAll(emptyRequest());

            assertNotNull(targeted);
            assertEquals("beta", targeted.getKey());
            assertEquals(2, targeted.getId());
            assertEquals("R1", targeted.getFeatureGroupKey());

            FeatureResult fromAll = null;
            for (FeatureResult r : all) {
                if ("beta".equals(r.getKey())) {
                    fromAll = r;
                    break;
                }
            }
            assertNotNull(fromAll);
            assertEquals(targeted.getId(), fromAll.getId());
            assertEquals(targeted.getFeatureGroupKey(), fromAll.getFeatureGroupKey());
        }

        @Test
        @DisplayName("evaluate finds feature across multiple featureGroups")
        void testEvaluateAcrossReleases() {
            putReleases(
                    releaseWithIdAndFeatures(10, "R1", featureWithId("alpha", 1)),
                    releaseWithIdAndFeatures(20, "R2", featureWithId("beta", 2)),
                    releaseWithIdAndFeatures(30, "R3", featureWithId("gamma", 3)));

            FeatureResult result = evaluator.evaluate("gamma", emptyRequest());
            assertNotNull(result);
            assertEquals("gamma", result.getKey());
            assertEquals(30, result.getAnalyticsParam().getFeatureGroupId());
        }

        @Test
        @DisplayName("evaluate skips featureGroups that do not contain the target feature")
        void testEvaluateSkipsUnrelatedReleases() {
            putReleases(
                    releaseWithIdAndFeatures(10, "R1", featureWithId("alpha", 1)),
                    releaseWithIdAndFeatures(20, "R2", featureWithId("beta", 2)),
                    releaseWithIdAndFeatures(30, "R3", featureWithId("gamma", 3)));

            FeatureResult result = evaluator.evaluate("gamma", emptyRequest());
            assertNotNull(result);
            assertEquals(30, result.getAnalyticsParam().getFeatureGroupId());
            assertEquals(3, result.getId());
        }

        @Test
        @DisplayName("evaluate respects featureGroup-level criteria")
        void testEvaluateRespectsReleaseCriteria() {
            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));

            FeaturesResponse featureGroup =
                    releaseWithIdAndFeatures(10, "R1", featureWithId("feat-a", 1));
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            putReleases(featureGroup);

            assertNull(
                    evaluator.evaluate(
                            "feat-a", requestWithContext(Map.of("country", List.of("UK")))));
            assertNotNull(
                    evaluator.evaluate(
                            "feat-a", requestWithContext(Map.of("country", List.of("US")))));
        }

        @Test
        @DisplayName("evaluate respects feature-level criteria")
        void testEvaluateRespectsFeatureCriteria() {
            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));

            Feature feat =
                    featureWithCriteria(
                            "us-only",
                            "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                                    + " \"US\"}}");
            feat.setId(42);
            putReleases(releaseWithFeatureObjects("R1", feat, featureWith("global")));

            assertNull(
                    evaluator.evaluate(
                            "us-only", requestWithContext(Map.of("country", List.of("UK")))));
            assertNotNull(
                    evaluator.evaluate(
                            "us-only", requestWithContext(Map.of("country", List.of("US")))));
            assertNotNull(
                    evaluator.evaluate(
                            "global", requestWithContext(Map.of("country", List.of("UK")))));
        }

        @Test
        @DisplayName("evaluate finds string feature by name")
        void testEvaluateStringFeature() {
            putReleases(releaseWithIdAndStringFeatures(50, "R1", "feat-a", "feat-b", "feat-c"));

            FeatureResult result = evaluator.evaluate("feat-b", emptyRequest());
            assertNotNull(result);
            assertEquals("feat-b", result.getKey());
            assertEquals(50, result.getAnalyticsParam().getFeatureGroupId());
        }

        @Test
        @DisplayName("evaluate returns null for missing string feature")
        void testEvaluateStringFeatureMissing() {
            putReleases(releaseWithIdAndStringFeatures(50, "R1", "feat-a", "feat-b"));
            assertNull(evaluator.evaluate("feat-c", emptyRequest()));
        }

        @Test
        @DisplayName("evaluate populates AnalyticsParam on targeted path")
        void testEvaluateAnalyticsParam() {
            putReleases(
                    releaseWithIdAndFeatures(
                            100,
                            "R1",
                            featureWithId("dark-mode", 42),
                            featureWithId("new-nav", 43)));

            FeatureResult result = evaluator.evaluate("dark-mode", emptyRequest());
            assertNotNull(result);
            AnalyticsParam param = result.getAnalyticsParam();
            assertNotNull(param);
            assertEquals(100, param.getFeatureGroupId());
            assertEquals(42, param.getFeatureId());
            assertEquals("dark-mode", param.getFeatureKey());
        }

        @Test
        @DisplayName("isEnabled returns false when no cache exists")
        void testIsEnabledNoCache() {
            assertFalse(evaluator.isEnabled("any-feature", emptyRequest()));
        }

        @Test
        @DisplayName("isEnabled returns false for null feature name")
        void testIsEnabledNullName() {
            putReleases(releaseWithFeatureObjects("R1", featureWith("alpha")));
            assertFalse(evaluator.isEnabled(null, emptyRequest()));
        }
    }

    @Nested
    @DisplayName("Combined criteria and policy on same featureGroup")
    class CombinedCriteriaAndPolicyTests {

        @BeforeEach
        void setUpFilterService() {
            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));
        }

        @Test
        @DisplayName("Release excluded by criteria even if policy would allow")
        void testCriteriaBlocksBeforePolicy() {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            featureGroup.setPolicyId(null);
            putReleases(featureGroup);

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("UK")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName(
                "Mixed featureGroup: criteria-matching featureGroup + criteria-blocked"
                        + " featureGroup")
        void testMixedReleases() {
            FeaturesResponse matchingRelease = releaseWithStringFeatures("R1", "visible");
            matchingRelease.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");

            FeaturesResponse blockedRelease = releaseWithStringFeatures("R2", "hidden");
            blockedRelease.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"UK\"}}");

            putReleases(matchingRelease, blockedRelease);

            GetFeatureRequest request = requestWithContext(Map.of("country", List.of("US")));

            FeatureResult[] result = evaluator.evaluateAll(request);
            assertEquals(1, result.length);
            assertEquals("visible", result[0].getKey());
        }
    }

    @Nested
    @DisplayName("AnalyticsParam population")
    class AnalyticsParamTests {

        @Test
        @DisplayName(
                "Feature objects carry featureGroupId, featureId, and featureKey in AnalyticsParam")
        void testAnalyticsParamOnFeatureObjects() {
            putReleases(releaseWithIdAndFeatures(100, "R1", featureWithId("dark-mode", 42)));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);

            AnalyticsParam param = result[0].getAnalyticsParam();
            assertNotNull(param);
            assertEquals(100, param.getFeatureGroupId());
            assertEquals(42, param.getFeatureId());
            assertEquals("dark-mode", param.getFeatureKey());
            assertNull(param.getVariantId(), "No policy → null variantId");
        }

        @Test
        @DisplayName("String features carry featureGroupId and featureKey in AnalyticsParam")
        void testAnalyticsParamOnStringFeatures() {
            putReleases(releaseWithIdAndStringFeatures(200, "R2", "feat-a"));

            FeatureResult[] result = evaluator.evaluateAll(emptyRequest());
            assertEquals(1, result.length);

            AnalyticsParam param = result[0].getAnalyticsParam();
            assertNotNull(param);
            assertEquals(200, param.getFeatureGroupId());
            assertEquals(0, param.getFeatureId());
            assertEquals("feat-a", param.getFeatureKey());
            assertNull(param.getVariantId(), "No policy → null variantId");
        }

        @Test
        @DisplayName(
                "Release-level policy populates variantId on all features in that featureGroup")
        void testReleasePolicyVariantId() {
            var buckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 0, 0),
                            new PolicyCache.PolicyBucket("1", 1, 9999, 100));
            policyCache.putPolicy(
                    300, new PolicyCache.PolicyDetail(300, "MURMUR_HASH", "300", buckets, null));

            FeaturesResponse featureGroup = releaseWithIdAndStringFeatures(50, "R1", "feat-a");
            featureGroup.setPolicyId(300);
            putReleases(featureGroup);

            GetFeatureRequest req =
                    requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "test-user-abc");
            FeatureResult[] result = evaluator.evaluateAll(req);
            assertEquals(1, result.length);

            AnalyticsParam param = result[0].getAnalyticsParam();
            assertNotNull(param);
            assertNotNull(param.getVariantId(), "Policy should populate variantId");
        }

        @Test
        @DisplayName("Feature-level policy overrides featureGroup-level variantId")
        void testFeaturePolicyOverridesReleaseVariant() {
            var releaseBuckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 0, 0),
                            new PolicyCache.PolicyBucket("1", 1, 9999, 100));
            policyCache.putPolicy(
                    400,
                    new PolicyCache.PolicyDetail(400, "MURMUR_HASH", "400", releaseBuckets, null));

            var featureBuckets =
                    java.util.Arrays.asList(
                            new PolicyCache.PolicyBucket("", 0, 0, 0),
                            new PolicyCache.PolicyBucket("5", 1, 9999, 100));
            policyCache.putPolicy(
                    401,
                    new PolicyCache.PolicyDetail(401, "MURMUR_HASH", "401", featureBuckets, null));

            FeaturesResponse featureGroup =
                    releaseWithIdAndFeatures(60, "R1", featureWithIdAndPolicy("feat-a", 10, 401));
            featureGroup.setPolicyId(400);
            putReleases(featureGroup);

            GetFeatureRequest req =
                    requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "test-user-xyz");
            FeatureResult[] result = evaluator.evaluateAll(req);
            assertEquals(1, result.length);

            AnalyticsParam param = result[0].getAnalyticsParam();
            assertNotNull(param);
            assertEquals(
                    "5",
                    param.getVariantId(),
                    "Feature-level policy variant should override featureGroup-level");
        }
    }

    @Nested
    @DisplayName("Control cohort variantId — feature policy → AnalyticsParam")
    class ControlVariantAnalyticsTests {

        @Test
        @DisplayName(
                "Empty-string control bucket: control sentinel uses variantId \"0\" in analytics")
        void emptyControlBucket_analyticsUsesZero() {
            policyCache.putPolicy(
                    9101,
                    new PolicyCache.PolicyDetail(
                            9101,
                            "MURMUR_HASH",
                            "feat-seed-9101",
                            List.of(
                                    new PolicyCache.PolicyBucket("", 0, 4999, 50),
                                    new PolicyCache.PolicyBucket("3", 5000, 9999, 50)),
                            null));
            Feature feature = featureWithIdAndPolicy("gated-feat", 50, 9101);
            putReleases(releaseWithIdAndFeatures(200, "R1", feature));

            String controlIdentifier = findIdentifierWithPolicyControlOutcome(9101);
            assertNotNull(controlIdentifier, "expected an identifier in the control bucket");

            FeatureResult[] results =
                    evaluator.evaluateAll(
                            requestWithNamespaceIdentity(
                                    DEFAULT_COHORTING_NAMESPACE, controlIdentifier));
            assertEquals(1, results.length);
            FeatureResult row = results[0];
            assertEquals(-1, row.getId(), "feature-level control uses sentinel id -1");
            assertNull(row.getKey());
            AnalyticsParam param = row.getAnalyticsParam();
            assertNotNull(param);
            assertEquals(
                    "0",
                    param.getVariantId(),
                    "control cohort must report variant id 0, not empty string");
        }

        @Test
        @DisplayName("Explicit \"0\" control bucket: analytics variantId is \"0\"")
        void explicitZeroControlBucket_analyticsUsesZero() {
            policyCache.putPolicy(
                    9102,
                    new PolicyCache.PolicyDetail(
                            9102,
                            "MURMUR_HASH",
                            "feat-seed-9102",
                            List.of(new PolicyCache.PolicyBucket("0", 0, 9999, 100)),
                            null));
            Feature feature = featureWithIdAndPolicy("flag-z", 99, 9102);
            putReleases(releaseWithIdAndFeatures(201, "R1", feature));

            FeatureResult[] results =
                    evaluator.evaluateAll(
                            requestWithNamespaceIdentity(
                                    DEFAULT_COHORTING_NAMESPACE, "any-fixed-identity"));
            assertEquals(1, results.length);
            FeatureResult row = results[0];
            assertEquals(-1, row.getId());
            assertNull(row.getKey());
            assertEquals("0", row.getAnalyticsParam().getVariantId());
        }
    }

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent evaluateAll calls return consistent results")
        void testConcurrentEvaluateAll() throws Exception {
            putReleases(
                    releaseWithFeatureObjects(
                            "R1",
                            featureWith("feat-a"),
                            featureWith("feat-b"),
                            featureWith("feat-c")));

            int threadCount = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int t = 0; t < threadCount; t++) {
                new Thread(
                                () -> {
                                    try {
                                        startLatch.await();
                                        for (int i = 0; i < 500; i++) {
                                            FeatureResult[] results =
                                                    evaluator.evaluateAll(emptyRequest());
                                            assertEquals(3, results.length);
                                            assertEquals("feat-a", results[0].getKey());
                                            assertEquals("feat-b", results[1].getKey());
                                            assertEquals("feat-c", results[2].getKey());
                                        }
                                    } catch (Throwable e) {
                                        failure.compareAndSet(null, e);
                                    } finally {
                                        doneLatch.countDown();
                                    }
                                })
                        .start();
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
        }

        @Test
        @DisplayName("Concurrent evaluate and isEnabled are safe")
        void testConcurrentEvaluateAndIsEnabled() throws Exception {
            putReleases(releaseWithFeatureObjects("R1", featureWith("alpha"), featureWith("beta")));

            int threadCount = 16;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                new Thread(
                                () -> {
                                    try {
                                        startLatch.await();
                                        for (int i = 0; i < 500; i++) {
                                            if (threadIdx % 2 == 0) {
                                                FeatureResult result =
                                                        evaluator.evaluate("alpha", emptyRequest());
                                                assertNotNull(result);
                                                assertEquals("alpha", result.getKey());
                                            } else {
                                                assertTrue(
                                                        evaluator.isEnabled(
                                                                "beta", emptyRequest()));
                                                assertFalse(
                                                        evaluator.isEnabled(
                                                                "nonexistent", emptyRequest()));
                                            }
                                        }
                                    } catch (Throwable e) {
                                        failure.compareAndSet(null, e);
                                    } finally {
                                        doneLatch.countDown();
                                    }
                                })
                        .start();
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
        }

        @Test
        @DisplayName("setFilterService is visible to concurrent evaluations")
        void testFilterServiceVisibilityAcrossThreads() throws Exception {
            FeaturesResponse featureGroup = releaseWithStringFeatures("R1", "feat-a");
            featureGroup.setCriteria(
                    "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\":"
                            + " \"US\"}}");
            putReleases(featureGroup);

            GetFeatureRequest usRequest = requestWithContext(Map.of("country", List.of("US")));
            GetFeatureRequest ukRequest = requestWithContext(Map.of("country", List.of("UK")));

            FeatureResult[] beforeEngine = evaluator.evaluateAll(usRequest);
            assertEquals(
                    0, beforeEngine.length, "Without filter service, criteria must fail-closed");

            evaluator.setFilterService(filterServiceWithTypes("country", "STRING"));

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger usMatches = new AtomicInteger();
            AtomicInteger ukMatches = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int t = 0; t < threadCount; t++) {
                final boolean useUS = (t % 2 == 0);
                new Thread(
                                () -> {
                                    try {
                                        startLatch.await();
                                        for (int i = 0; i < 200; i++) {
                                            FeatureResult[] results =
                                                    evaluator.evaluateAll(
                                                            useUS ? usRequest : ukRequest);
                                            if (useUS) {
                                                assertEquals(1, results.length);
                                                usMatches.incrementAndGet();
                                            } else {
                                                assertEquals(0, results.length);
                                                ukMatches.incrementAndGet();
                                            }
                                        }
                                    } catch (Throwable e) {
                                        failure.compareAndSet(null, e);
                                    } finally {
                                        doneLatch.countDown();
                                    }
                                })
                        .start();
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertNull(failure.get(), () -> "Thread failure: " + failure.get());
            assertTrue(usMatches.get() > 0, "US threads should have matched");
            assertTrue(ukMatches.get() > 0, "UK threads should have been excluded");
        }
    }
}
