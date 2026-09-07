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

package com.adobe.marketing.mobile.flags.engine.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.common.CohortingType;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for PolicyEvaluator (A/B testing). */
class PolicyEvaluatorTest {

    private PolicyCache policyCache;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        policyCache = new PolicyCache();
    }

    @Test
    @DisplayName("Same identifier should always get same variant (deterministic)")
    void testDeterministicBucketing() {
        String identifier = "user123";
        Integer policyId = 100;

        PolicyEvaluator.PolicyVariantResponse result1 =
                PolicyEvaluator.getPolicyVariant(policyId, identifier, 50);
        PolicyEvaluator.PolicyVariantResponse result2 =
                PolicyEvaluator.getPolicyVariant(policyId, identifier, 50);
        PolicyEvaluator.PolicyVariantResponse result3 =
                PolicyEvaluator.getPolicyVariant(policyId, identifier, 50);

        assertEquals(result1.getVariantId(), result2.getVariantId());
        assertEquals(result2.getVariantId(), result3.getVariantId());
        assertEquals(result1.isControlGroup(), result2.isControlGroup());
    }

    @Test
    @DisplayName("Different identifiers should distribute across variants")
    void testDistribution() {
        Integer policyId = 100;
        int controlPercentage = 50;
        int totalUsers = 10000;
        int controlCount = 0;

        for (int i = 0; i < totalUsers; i++) {
            String identifier = "user_" + i;
            PolicyEvaluator.PolicyVariantResponse result =
                    PolicyEvaluator.getPolicyVariant(policyId, identifier, controlPercentage);
            if (result.isControlGroup()) {
                controlCount++;
            }
        }

        // Should be roughly 50% (allow 5% variance)
        double actualPercentage = (controlCount * 100.0) / totalUsers;
        assertTrue(
                actualPercentage > 45 && actualPercentage < 55,
                "Control group should be ~50%, was: " + actualPercentage + "%");
    }

    @Test
    @DisplayName("Policy bucket evaluation with percentages")
    void testPolicyBuckets() {
        // 30% control, 70% treatment
        var buckets =
                Arrays.asList(
                        new PolicyEvaluator.PercentageSplit(0, 30), // Control
                        new PolicyEvaluator.PercentageSplit(1, 70) // Treatment
                        );

        int controlCount = 0;
        int treatmentCount = 0;
        int totalUsers = 10000;

        for (int i = 0; i < totalUsers; i++) {
            String identifier = "user_" + i;
            PolicyEvaluator.PolicyVariantResponse result =
                    PolicyEvaluator.getPolicyVariant(100, identifier, buckets);

            if ("0".equals(result.getVariantId())) {
                controlCount++;
            } else {
                treatmentCount++;
            }
        }

        // Should be roughly 30/70 (allow 5% variance)
        double controlPct = (controlCount * 100.0) / totalUsers;
        double treatmentPct = (treatmentCount * 100.0) / totalUsers;

        assertTrue(
                controlPct > 25 && controlPct < 35,
                "Control should be ~30%, was: " + controlPct + "%");
        assertTrue(
                treatmentPct > 65 && treatmentPct < 75,
                "Treatment should be ~70%, was: " + treatmentPct + "%");
    }

    @Test
    @DisplayName("MurmurHash should be consistent")
    void testMurmurHashConsistency() {
        MurmurHashStrategy hash = new MurmurHashStrategy();

        int value1 = hash.hash("test_user_123", null);
        int value2 = hash.hash("test_user_123", null);
        int value3 = hash.hash("test_user_123", null);

        assertEquals(value1, value2);
        assertEquals(value2, value3);
    }

    @Test
    @DisplayName("MurmurHash with seed should produce different results")
    void testMurmurHashWithSeed() {
        MurmurHashStrategy hash = new MurmurHashStrategy();

        // Using String seeds (matches the server-side bucketing hash input format)
        int value1 = hash.hash("test_user", "seed1");
        int value2 = hash.hash("test_user", "seed2");
        int value3 = hash.hash("test_user", "seed3");

        // Different seeds should produce different hashes
        assertNotEquals(value1, value2);
        assertNotEquals(value2, value3);
    }

    @Test
    @DisplayName("MurmurHash output should be within expected range")
    void testMurmurHashRange() {
        MurmurHashStrategy hash = new MurmurHashStrategy();

        for (int i = 0; i < 1000; i++) {
            int value = hash.hash("user_" + i, null);
            assertTrue(value >= 0 && value < 10000, "Hash should be 0-9999, was: " + value);
        }
    }

    @Test
    @DisplayName(
            "getPolicyVariantFromCache should fall back to simplified 50/50 when cache is empty")
    void testGetPolicyVariantFromCacheEmptyCache() {
        PolicyEvaluator.PolicyVariantResponse result =
                PolicyEvaluator.getPolicyVariantFromCache(policyCache, 100, "user123");

        assertNotNull(result);
        assertNotNull(result.getVariantId());
        PolicyEvaluator.PolicyVariantResponse result2 =
                PolicyEvaluator.getPolicyVariantFromCache(policyCache, 100, "user123");
        assertEquals(result.getVariantId(), result2.getVariantId());
        assertEquals(result.isControlGroup(), result2.isControlGroup());
    }

    @Test
    @DisplayName("getPolicyVariantFromCache should distribute ~50/50 with empty cache fallback")
    void testGetPolicyVariantFromCacheFallbackDistribution() {
        int controlCount = 0;
        int totalUsers = 10000;

        for (int i = 0; i < totalUsers; i++) {
            PolicyEvaluator.PolicyVariantResponse result =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 100, "user_" + i);
            if (result.isControlGroup()) {
                controlCount++;
            }
        }

        double controlPct = (controlCount * 100.0) / totalUsers;
        assertTrue(
                controlPct > 45 && controlPct < 55,
                "Fallback should produce ~50% control, was: " + controlPct + "%");
    }

    @Test
    @DisplayName("getPolicyVariantFromCache should use cached policy when available")
    void testGetPolicyVariantFromCacheWithPopulatedCache() {
        var buckets =
                Arrays.asList(
                        new PolicyCache.PolicyBucket("", 0, 2999, 30),
                        new PolicyCache.PolicyBucket("1", 3000, 9999, 70));
        policyCache.putPolicy(
                200, new PolicyCache.PolicyDetail(200, "murmur", "200", buckets, null));

        int controlCount = 0;
        int totalUsers = 10000;

        for (int i = 0; i < totalUsers; i++) {
            PolicyEvaluator.PolicyVariantResponse result =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 200, "user_" + i);
            if (result.isControlGroup()) {
                controlCount++;
            }
        }

        double controlPct = (controlCount * 100.0) / totalUsers;
        assertTrue(
                controlPct > 25 && controlPct < 35,
                "Cached policy should produce ~30% control, was: " + controlPct + "%");
    }

    @Test
    @DisplayName("PolicyDetail can be set on FeaturesResponse and Feature models")
    void testPolicyDetailOnModels() {
        var buckets =
                Arrays.asList(
                        new PolicyCache.PolicyBucket("", 0, 4999, 50),
                        new PolicyCache.PolicyBucket("1", 5000, 9999, 50));
        Map<String, String> previewMap = new HashMap<>();
        previewMap.put("testUser", "1");
        PolicyCache.PolicyDetail detail =
                new PolicyCache.PolicyDetail(42, "MURMUR_HASH", "mySeed", buckets, previewMap);

        FeaturesResponse featureGroup = new FeaturesResponse();
        featureGroup.setPolicyId(42);
        featureGroup.setPolicy(detail);
        assertNotNull(featureGroup.getPolicy());
        assertEquals(42, featureGroup.getPolicy().getId());
        assertEquals("mySeed", featureGroup.getPolicy().getSeed());
        assertEquals(2, featureGroup.getPolicy().getBuckets().size());

        Feature feature = new Feature();
        feature.setPolicyId(42);
        feature.setPolicy(detail);
        assertNotNull(feature.getPolicy());
        assertEquals("MURMUR_HASH", feature.getPolicy().getHashAlgorithmType());
        assertEquals("1", feature.getPolicy().getPreviewUserVariantMap().get("testUser"));
    }

    @Test
    @DisplayName("PolicyCache populated from model uses real buckets in evaluation")
    void testPolicyCacheFromModelEndToEnd() {
        var buckets =
                Arrays.asList(
                        new PolicyCache.PolicyBucket("", 0, 4999, 50),
                        new PolicyCache.PolicyBucket("1", 5000, 9999, 50));
        PolicyCache.PolicyDetail detail =
                new PolicyCache.PolicyDetail(300, "MURMUR_HASH", "300", buckets, null);
        policyCache.putPolicy(300, detail);

        int controlCount = 0;
        int totalUsers = 10000;
        for (int i = 0; i < totalUsers; i++) {
            PolicyEvaluator.PolicyVariantResponse result =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 300, "user_" + i);
            if (result.isControlGroup()) {
                controlCount++;
            }
        }

        double controlPct = (controlCount * 100.0) / totalUsers;
        assertTrue(
                controlPct > 45 && controlPct < 55,
                "Real cached policy should produce ~50% control, was: " + controlPct + "%");
    }

    @Test
    @DisplayName("getPolicyVariantFromCache should return control group for null inputs")
    void testGetPolicyVariantFromCacheNullInputs() {
        PolicyEvaluator.PolicyVariantResponse result1 =
                PolicyEvaluator.getPolicyVariantFromCache(policyCache, null, "user123");
        assertTrue(result1.isControlGroup());
        assertEquals("0", result1.getVariantId());

        PolicyEvaluator.PolicyVariantResponse result2 =
                PolicyEvaluator.getPolicyVariantFromCache(policyCache, 100, null);
        assertTrue(result2.isControlGroup());
        assertEquals("0", result2.getVariantId());
    }

    @Nested
    @DisplayName("Control variant id normalization — control cohort always reports \"0\"")
    class ControlVariantIdNormalizationTests {

        @Test
        @DisplayName("Murmur path: bucket with null variantId → control, variantId \"0\"")
        void murmurBucket_nullVariantId() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            801,
                            "MURMUR_HASH",
                            "seed-a",
                            List.of(new PolicyCache.PolicyBucket(null, 0, 9999, 100)),
                            null);
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "any-bucketing-id");
            assertTrue(r.isControlGroup(), r::toString);
            assertEquals(Constants.CONTROL_GROUP_VARIANT_ID, r.getVariantId());
        }

        @Test
        @DisplayName("Murmur path: bucket with empty variantId → control, variantId \"0\"")
        void murmurBucket_emptyVariantId() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            802,
                            "MURMUR_HASH",
                            "seed-b",
                            List.of(new PolicyCache.PolicyBucket("", 0, 9999, 100)),
                            null);
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "another-id");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Murmur path: bucket with explicit \"0\" → control, variantId \"0\"")
        void murmurBucket_explicitZeroString() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            803,
                            "MURMUR_HASH",
                            "seed-c",
                            List.of(new PolicyCache.PolicyBucket("0", 0, 9999, 100)),
                            null);
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "user-z");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Murmur path: treatment bucket returns non-control variant unchanged")
        void murmurBucket_treatmentVariantUnchanged() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            804,
                            "MURMUR_HASH",
                            "seed-d",
                            Arrays.asList(
                                    new PolicyCache.PolicyBucket("", 0, 1, 50),
                                    new PolicyCache.PolicyBucket("7", 2, 9999, 50)),
                            null);
            String treatmentId = null;
            for (int i = 0; i < 100_000; i++) {
                String id = "probe_" + i;
                PolicyEvaluator.PolicyVariantResponse r =
                        PolicyEvaluator.getPolicyVariant(detail, id);
                if (!r.isControlGroup() && "7".equals(r.getVariantId())) {
                    treatmentId = id;
                    break;
                }
            }
            assertNotNull(
                    treatmentId, "expected at least one identifier in treatment range 2–9999");
            PolicyEvaluator.PolicyVariantResponse again =
                    PolicyEvaluator.getPolicyVariant(detail, treatmentId);
            assertFalse(again.isControlGroup());
            assertEquals("7", again.getVariantId());
        }

        @Test
        @DisplayName("Murmur path: no bucket matches hash → fallback control with variantId \"0\"")
        void murmurNoMatchingBucket() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            805,
                            "MURMUR_HASH",
                            "seed-e",
                            List.of(new PolicyCache.PolicyBucket("1", 10001, 10002, 1)),
                            null);
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "any-user");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Preview path: map value \"\" → control, variantId \"0\"")
        void previewMap_emptyStringValue() {
            Map<String, String> map = new HashMap<>();
            map.put("alice", "");
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(806, "PREVIEW_SIMPLE_HASH", "pv", List.of(), map);
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "alice");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Preview path: map value \"0\" → control, variantId \"0\"")
        void previewMap_explicitZero() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            807, "PREVIEW_SIMPLE_HASH", "pv", List.of(), Map.of("bob", "0"));
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "bob");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Preview path: map value non-control → passthrough")
        void previewMap_treatmentPassthrough() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            808, "PREVIEW_SIMPLE_HASH", "pv", List.of(), Map.of("carol", "9"));
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(detail, "carol");
            assertFalse(r.isControlGroup());
            assertEquals("9", r.getVariantId());
        }

        @Test
        @DisplayName(
                "getPolicyVariantFromCache: cached empty control bucket resolves to variant \"0\"")
        void fromCache_emptyControlBucketNormalizes() {
            PolicyCache.PolicyDetail detail =
                    new PolicyCache.PolicyDetail(
                            809,
                            "MURMUR_HASH",
                            "seed-f",
                            Arrays.asList(
                                    new PolicyCache.PolicyBucket("", 0, 4999, 50),
                                    new PolicyCache.PolicyBucket("1", 5000, 9999, 50)),
                            null);
            policyCache.putPolicy(809, detail);
            String controlUser = null;
            for (int i = 0; i < 100_000; i++) {
                String id = "cache-probe_" + i;
                PolicyEvaluator.PolicyVariantResponse r =
                        PolicyEvaluator.getPolicyVariantFromCache(policyCache, 809, id);
                if (r.isControlGroup()) {
                    controlUser = id;
                    assertEquals(
                            "0",
                            r.getVariantId(),
                            "control cohort must never surface empty variant id");
                    break;
                }
            }
            assertNotNull(controlUser, "expected at least one user in control bucket 0–4999");
        }
    }

    @Nested
    @DisplayName("resolveCohortingNamespace — namespace resolution")
    class ResolveCohortingNamespaceTests {

        @Test
        @DisplayName("returns null when params or cohortingType are absent")
        void testReturnsNullWhenCohortingTypeAbsent() {
            assertNull(PolicyEvaluator.resolveCohortingNamespace(null));
            assertNull(PolicyEvaluator.resolveCohortingNamespace(new HashMap<>()));
            assertNull(
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(Constants.JSON_KEY_COHORTING_NAMESPACE_CODE, "loginID")));
        }

        @Test
        @DisplayName("ECID cohortingType resolves to ECID namespace")
        void testEcidCohortingTypeResolvesToEcid() {
            assertEquals(
                    Constants.DEFAULT_COHORTING_NAMESPACE,
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(
                                    Constants.JSON_KEY_COHORTING_TYPE,
                                    CohortingType.ECID.getValue())));
        }

        @Test
        @DisplayName("ECID cohortingType ignores cohortingNamespaceCode")
        void testEcidIgnoresNamespaceCode() {
            assertEquals(
                    Constants.DEFAULT_COHORTING_NAMESPACE,
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(
                                    Constants.JSON_KEY_COHORTING_TYPE,
                                    CohortingType.ECID.getValue(),
                                    Constants.JSON_KEY_COHORTING_NAMESPACE_CODE,
                                    "loginID")));
        }

        @Test
        @DisplayName("STICKY cohortingType uses cohortingNamespaceCode")
        void testStickyUsesNamespaceCode() {
            assertEquals(
                    "loginID",
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(
                                    Constants.JSON_KEY_COHORTING_TYPE,
                                    CohortingType.STICKY.getValue(),
                                    Constants.JSON_KEY_COHORTING_NAMESPACE_CODE,
                                    "loginID")));
        }

        @Test
        @DisplayName("STICKY without cohortingNamespaceCode returns null")
        void testStickyWithoutNamespaceCodeReturnsNull() {
            assertNull(
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(
                                    Constants.JSON_KEY_COHORTING_TYPE,
                                    CohortingType.STICKY.getValue())));
        }

        @Test
        @DisplayName("unrecognized cohortingType returns null")
        void testUnrecognizedCohortingTypeReturnsNull() {
            assertNull(
                    PolicyEvaluator.resolveCohortingNamespace(
                            Map.of(Constants.JSON_KEY_COHORTING_TYPE, "loginID")));
        }
    }

    @Nested
    @DisplayName("getCohortingNamespace — resolved from params")
    class GetCohortingNamespaceTests {

        @Test
        @DisplayName("FeaturesResponse with no params returns null")
        void testFeaturesResponseNoParamsReturnsNull() {
            FeaturesResponse featureGroup = new FeaturesResponse();
            assertNull(featureGroup.getCohortingNamespace());
        }

        @Test
        @DisplayName("FeaturesResponse getCohortingNamespace is consistent across multiple calls")
        void testFeaturesResponseConsistentAcrossCalls() {
            FeaturesResponse featureGroup = new FeaturesResponse();
            featureGroup.setParams(Map.of(Constants.JSON_KEY_COHORTING_TYPE, "ECID"));
            assertEquals(
                    featureGroup.getCohortingNamespace(), featureGroup.getCohortingNamespace());
        }

        @Test
        @DisplayName("Feature with no params returns null")
        void testFeatureNoParamsReturnsNull() {
            Feature feature = new Feature();
            assertNull(feature.getCohortingNamespace());
        }

        @Test
        @DisplayName("Feature getCohortingNamespace is consistent across multiple calls")
        void testFeatureConsistentAcrossCalls() {
            Feature feature = new Feature();
            feature.setParams(Map.of(Constants.JSON_KEY_COHORTING_TYPE, "ECID"));
            assertEquals(feature.getCohortingNamespace(), feature.getCohortingNamespace());
        }
    }

    @Nested
    @DisplayName("getIdentifier — identity map resolution")
    class GetIdentifierIdentityMapTests {

        private Map<String, Object> identityEntry(String id, Boolean primary) {
            Map<String, Object> entry = new HashMap<>();
            entry.put(Constants.IDENTITY_ENTRY_KEY_ID, id);
            if (primary != null) {
                entry.put(Constants.IDENTITY_ENTRY_KEY_PRIMARY, primary);
            }
            entry.put(Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE, "ambiguous");
            return entry;
        }

        private Map<String, List<Map<String, Object>>> identityMapWithNamespace(
                String namespace, String id, boolean primary) {
            return Map.of(namespace, List.of(identityEntry(id, primary)));
        }

        @Test
        @DisplayName("returns id from identity map when ECID namespace is present")
        void identityMapUsedWhenEcidNamespacePresent() {
            Map<String, List<Map<String, Object>>> map =
                    identityMapWithNamespace(CohortingType.ECID.getValue(), "from-map", true);
            assertEquals(
                    "from-map", PolicyEvaluator.getIdentifier(map, CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("returns id when namespace is STICKY")
        void stickyNamespaceReturnsStickyId() {
            Map<String, List<Map<String, Object>>> map =
                    identityMapWithNamespace(
                            CohortingType.STICKY.getValue(), "sticky-bucket-id", true);
            assertEquals(
                    "sticky-bucket-id",
                    PolicyEvaluator.getIdentifier(map, CohortingType.STICKY.getValue()));
        }

        @Test
        @DisplayName("returns primary id for matching namespace")
        void primaryIdReturnedForMatchingNamespace() {
            Map<String, List<Map<String, Object>>> map =
                    Map.of(
                            CohortingType.ECID.getValue(),
                            List.of(
                                    identityEntry("secondary", false),
                                    identityEntry("abc123", true)));
            assertEquals(
                    "abc123", PolicyEvaluator.getIdentifier(map, CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("returns first entry id when no primary is marked")
        void firstEntryWhenNoPrimary() {
            Map<String, List<Map<String, Object>>> map =
                    Map.of(
                            CohortingType.ECID.getValue(),
                            List.of(identityEntry("first-id", false)));
            assertEquals(
                    "first-id", PolicyEvaluator.getIdentifier(map, CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("falls back to session-stable id when identityMap is null")
        void fallbackToSessionIdWhenIdentityMapNull() {
            String sessionId = PolicyEvaluator.getIdentifier(null, CohortingType.ECID.getValue());
            assertNotNull(sessionId);
            assertEquals(
                    sessionId, PolicyEvaluator.getIdentifier(null, CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("falls back to session-stable id when identityMap is empty")
        void fallbackToSessionIdWhenIdentityMapEmpty() {
            String sessionId =
                    PolicyEvaluator.getIdentifier(
                            Collections.emptyMap(), CohortingType.ECID.getValue());
            assertNotNull(sessionId);
            assertEquals(
                    sessionId,
                    PolicyEvaluator.getIdentifier(
                            Collections.emptyMap(), CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("falls back to session-stable id when cohortingNamespace is null")
        void fallbackToSessionIdWhenNamespaceNull() {
            String sessionId =
                    PolicyEvaluator.getIdentifier(
                            identityMapWithNamespace(
                                    CohortingType.ECID.getValue(), "ignored", true),
                            null);
            assertNotNull(sessionId);
            assertEquals(
                    sessionId,
                    PolicyEvaluator.getIdentifier(
                            identityMapWithNamespace(
                                    CohortingType.ECID.getValue(), "ignored", true),
                            null));
        }

        @Test
        @DisplayName(
                "falls back to session-stable id when cohortingNamespace is absent from"
                        + " identityMap")
        void fallbackToSessionIdWhenNamespaceAbsent() {
            String sessionId =
                    PolicyEvaluator.getIdentifier(
                            identityMapWithNamespace(
                                    CohortingType.ECID.getValue(), "ignored", true),
                            CohortingType.STICKY.getValue());
            assertNotNull(sessionId);
            assertEquals(
                    sessionId,
                    PolicyEvaluator.getIdentifier(
                            identityMapWithNamespace(
                                    CohortingType.ECID.getValue(), "ignored", true),
                            CohortingType.STICKY.getValue()));
        }

        @Test
        @DisplayName("falls back to session-stable id when identity entry id is null or empty")
        void fallbackToSessionIdWhenIdMissing() {
            String expectedSessionId =
                    PolicyEvaluator.getIdentifier(null, CohortingType.ECID.getValue());

            Map<String, List<Map<String, Object>>> nullIdMap =
                    Map.of(CohortingType.ECID.getValue(), List.of(identityEntry(null, true)));
            assertEquals(
                    expectedSessionId,
                    PolicyEvaluator.getIdentifier(nullIdMap, CohortingType.ECID.getValue()));

            Map<String, List<Map<String, Object>>> emptyIdMap =
                    Map.of(CohortingType.ECID.getValue(), List.of(identityEntry("", true)));
            assertEquals(
                    expectedSessionId,
                    PolicyEvaluator.getIdentifier(emptyIdMap, CohortingType.ECID.getValue()));
        }

        @Test
        @DisplayName("returns id from custom cohortingNamespaceCode namespace")
        void customNamespaceCodeReturnsMatchingId() {
            Map<String, List<Map<String, Object>>> map =
                    identityMapWithNamespace("loginID", "login-bucket-id", true);
            assertEquals("login-bucket-id", PolicyEvaluator.getIdentifier(map, "loginID"));
        }
    }
}
