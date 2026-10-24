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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.adobe.marketing.mobile.flags.engine.cache.SDKCacheManager;
import com.adobe.marketing.mobile.flags.engine.cache.SDKClientCache;
import com.adobe.marketing.mobile.flags.engine.common.CohortingType;
import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.feature.FeatureEvaluator;
import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.internal.parser.ResponseParser;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache.PolicyDetail;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end pipeline: combined JSON → {@link ResponseParser} → cache + policy cache (same rules as
 * {@link SDKCacheManager}) → {@link FeatureEvaluator} / {@link PolicyEvaluator}.
 */
class PolicyControlVariantPipelineIntegrationTest {

    private static final String CLIENT_ID = "pipeline-client";

    private static void cachePoliciesLikeSdkManager(
            PolicyCache policyCache, FeaturesResponse[] featureGroups) {
        if (featureGroups == null) {
            return;
        }
        for (FeaturesResponse featureGroup : featureGroups) {
            putPolicyIfPresent(policyCache, featureGroup.getPolicyId(), featureGroup.getPolicy());
            Feature[] features = featureGroup.getFeaturesObj();
            if (features != null) {
                for (Feature feature : features) {
                    putPolicyIfPresent(policyCache, feature.getPolicyId(), feature.getPolicy());
                }
            }
        }
    }

    private static void putPolicyIfPresent(
            PolicyCache policyCache, Integer policyId, PolicyDetail detail) {
        if (policyId != null && detail != null) {
            policyCache.putPolicy(policyId, detail);
        }
    }

    private static final String DEFAULT_COHORTING_NAMESPACE = CohortingType.ECID.getValue();

    private static Map<String, List<Map<String, Object>>> identityMapWithNamespace(
            String namespace, String id) {
        return Map.of(
                namespace,
                List.of(
                        Map.of(
                                Constants.IDENTITY_ENTRY_KEY_ID,
                                id,
                                Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                                true,
                                Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE,
                                "ambiguous")));
    }

    private static GetFeatureRequest requestWithNamespaceIdentity(String namespace, String id) {
        return new GetFeatureRequest.Builder()
                .identityMap(identityMapWithNamespace(namespace, id))
                .build();
    }

    @Test
    @DisplayName(
            "Preview policy from JSON: empty map value → evaluateAll control sentinel with"
                    + " variantId \"0\"")
    void previewEmptyThroughParserCacheAndEvaluator() {
        String json =
                EdgeResponseJsonFixtures.body(
                        120,
                        "ctx-pipeline-v1",
                        "[]",
                        EdgeResponseJsonFixtures.featureGroupWithFields(
                                700,
                                "PreviewRel",
                                "",
                                EdgeResponseJsonFixtures.feature(
                                        42,
                                        "gate-a",
                                        "\"cohortingType\":\"ECID\",\"policyId\":10001,\"policy\":{"
                                            + "\"id\":10001,\"hashAlgorithm\":\"PREVIEW_SIMPLE_HASH\",\"seed\":\"s\","
                                            + "\"buckets\":[],\"previewUserVariantMap\":{"
                                            + "\"v-preview-empty\":\"\",\"v-preview-zero\":\"0\","
                                            + "\"v-treat\":\"42\"}}")));

        FeaturesResponse[] featureGroups =
                ResponseParser.parseEdgeResponse(json).getFeatureGroups();
        assertEquals(1, featureGroups.length);
        assertEquals(700, featureGroups[0].getFeatureGroupId());
        assertEquals("PreviewRel", featureGroups[0].getFeatureGroupName());

        SDKClientCache cache = new SDKClientCache();
        PolicyCache policyCache = new PolicyCache();
        cache.putFeatures(CLIENT_ID, featureGroups, "etag-pipeline-1");
        cachePoliciesLikeSdkManager(policyCache, featureGroups);

        FeatureEvaluator evaluator = new FeatureEvaluator(CLIENT_ID, cache, policyCache);

        FeatureResult[] emptyPreview =
                evaluator.evaluateAll(
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, "v-preview-empty"));
        assertEquals(1, emptyPreview.length);
        assertEquals(-1, emptyPreview[0].getId());
        assertNull(emptyPreview[0].getKey());
        assertNotNull(emptyPreview[0].getAnalyticsParam());
        assertEquals("0", emptyPreview[0].getAnalyticsParam().getVariantId());

        FeatureResult[] zeroPreview =
                evaluator.evaluateAll(
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, "v-preview-zero"));
        assertEquals("0", zeroPreview[0].getAnalyticsParam().getVariantId());

        FeatureResult[] treat =
                evaluator.evaluateAll(
                        requestWithNamespaceIdentity(DEFAULT_COHORTING_NAMESPACE, "v-treat"));
        assertEquals(1, treat.length);
        assertEquals("gate-a", treat[0].getKey());
        assertEquals("42", treat[0].getAnalyticsParam().getVariantId());
        assertFalse(treat[0].getId() < 0);
    }

    @Test
    @DisplayName(
            "Murmur policy from JSON: omitted bucket variantId → control users get variantId \"0\"")
    void murmurOmittedVariantIdThroughParserCacheAndEvaluator() {
        String json =
                EdgeResponseJsonFixtures.body(
                        120,
                        "ctx-pipeline-v2",
                        "[]",
                        EdgeResponseJsonFixtures.featureGroupWithFields(
                                701,
                                "MurmurRel",
                                "",
                                EdgeResponseJsonFixtures.feature(
                                        7,
                                        "cohort-x",
                                        "\"cohortingType\":\"ECID\",\"policyId\":10002,\"policy\":{"
                                            + "\"id\":10002,\"hashAlgorithm\":\"MURMUR_HASH\",\"seed\":\"seed-murmur-int\","
                                            + "\"buckets\":["
                                            + "{\"start\":0,\"end\":4999,\"percentage\":50},"
                                            + "{\"variantId\":\"9\",\"start\":5000,\"end\":9999,\"percentage\":50}"
                                            + "],\"previewUserVariantMap\":null}")));

        FeaturesResponse[] featureGroups =
                ResponseParser.parseEdgeResponse(json).getFeatureGroups();
        assertEquals(1, featureGroups.length);
        assertEquals(701, featureGroups[0].getFeatureGroupId());

        PolicyCache.PolicyDetail parsed = featureGroups[0].getFeaturesObj()[0].getPolicy();
        assertEquals("", parsed.getBuckets().get(0).getVariantId());
        assertEquals("9", parsed.getBuckets().get(1).getVariantId());

        SDKClientCache cache = new SDKClientCache();
        PolicyCache policyCache = new PolicyCache();
        cache.putFeatures(CLIENT_ID, featureGroups, "etag-pipeline-2");
        cachePoliciesLikeSdkManager(policyCache, featureGroups);

        FeatureEvaluator evaluator = new FeatureEvaluator(CLIENT_ID, cache, policyCache);

        String controlIdentifier = null;
        for (int i = 0; i < 250_000; i++) {
            String id = "murmur-probe_" + i;
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 10002, id);
            if (r.isControlGroup()) {
                controlIdentifier = id;
                assertEquals("0", r.getVariantId());
                break;
            }
        }
        assertNotNull(
                controlIdentifier, "expected an identifier hashed into control bucket 0–4999");

        FeatureResult[] controlRows =
                evaluator.evaluateAll(
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, controlIdentifier));
        assertEquals(1, controlRows.length);
        assertEquals(-1, controlRows[0].getId());
        assertEquals("0", controlRows[0].getAnalyticsParam().getVariantId());

        String treatmentIdentifier = null;
        for (int i = 0; i < 250_000; i++) {
            String id = "murmur-treat_" + i;
            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariantFromCache(policyCache, 10002, id);
            if (!r.isControlGroup() && "9".equals(r.getVariantId())) {
                treatmentIdentifier = id;
                break;
            }
        }
        assertNotNull(treatmentIdentifier);
        FeatureResult[] treatRows =
                evaluator.evaluateAll(
                        requestWithNamespaceIdentity(
                                DEFAULT_COHORTING_NAMESPACE, treatmentIdentifier));
        assertEquals("cohort-x", treatRows[0].getKey());
        assertEquals("9", treatRows[0].getAnalyticsParam().getVariantId());
    }
}
