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

package com.adobe.marketing.mobile.flags.engine.internal.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.models.EdgeResponse;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EdgeResponseParserTest {

    @Nested
    @DisplayName("parseEdgeResponse — root fields")
    class RootFieldTests {

        @Test
        @DisplayName("Parses minimal v2 body from spec")
        void minimalBody() {
            EdgeResponse response =
                    ResponseParser.parseEdgeResponse(EdgeResponseJsonFixtures.minimalBody());

            assertEquals(2, response.getVersion());
            assertEquals(120, response.getPollInterval());
            assertEquals("test-context-v1", response.getContextVersion());
            assertEquals(1, response.getFeatureGroups().length);
            assertEquals(1001, response.getFeatureGroups()[0].getFeatureGroupId());
            assertEquals(
                    "default_feature_group", response.getFeatureGroups()[0].getFeatureGroupName());

            Feature feature = response.getFeatureGroups()[0].getFeaturesObj()[0];
            assertEquals(1001, feature.getId());
            assertEquals("my-feature", feature.getFeature());
            assertEquals("ECID", feature.getCohortingNamespace());
            assertTrue(feature.isAnalyticsEnabled());
        }

        @Test
        @DisplayName("Parses cohortingNamespaceCode from params object")
        void parsesCohortingNamespaceCodeFromParams() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1002,
                                    "ns_group",
                                    "{"
                                            + "\"id\":1002,"
                                            + "\"key\":\"login-feature\","
                                            + "\"params\":{"
                                            + "\"cohortingType\":\"STICKY\","
                                            + "\"cohortingNamespaceCode\":\"loginID\""
                                            + "}"
                                            + "}"));

            Feature feature =
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0];
            assertEquals(
                    "loginID",
                    feature.getParams().get(Constants.JSON_KEY_COHORTING_NAMESPACE_CODE));
            assertEquals("STICKY", feature.getParams().get(Constants.JSON_KEY_COHORTING_TYPE));
            assertEquals("loginID", feature.getCohortingNamespace());
        }

        @Test
        @DisplayName("ECID cohortingType resolves to ECID namespace when namespace code differs")
        void ecidCohortingTypeIgnoresMismatchedNamespaceCode() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1003,
                                    "mixed_group",
                                    EdgeResponseJsonFixtures.feature(
                                            1003,
                                            "mixed-feature",
                                            "\"cohortingType\":\"ECID\","
                                                    + "\"cohortingNamespaceCode\":\"loginID\"")));

            Feature feature =
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0];
            assertEquals("ECID", feature.getParams().get(Constants.JSON_KEY_COHORTING_TYPE));
            assertEquals(
                    "loginID",
                    feature.getParams().get(Constants.JSON_KEY_COHORTING_NAMESPACE_CODE));
            assertEquals("ECID", feature.getCohortingNamespace());
        }

        @Test
        @DisplayName("Parses top-level cohortingNamespaceCode for backward compatibility")
        void parsesTopLevelCohortingNamespaceCode() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1004,
                                    "top_level_group",
                                    EdgeResponseJsonFixtures.feature(
                                            1004,
                                            "top-level-feature",
                                            "\"cohortingType\":\"STICKY\","
                                                    + "\"cohortingNamespaceCode\":\"loginID\"")));

            Feature feature =
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0];
            assertEquals("STICKY", feature.getParams().get(Constants.JSON_KEY_COHORTING_TYPE));
            assertEquals(
                    "loginID",
                    feature.getParams().get(Constants.JSON_KEY_COHORTING_NAMESPACE_CODE));
        }

        @Test
        @DisplayName("Missing featureGroups key returns empty groups")
        void missingFeatureGroups() {
            EdgeResponse response =
                    ResponseParser.parseEdgeResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(60));
            assertEquals(0, response.getFeatureGroups().length);
            assertEquals(60, response.getPollInterval());
        }

        @Test
        @DisplayName("Non-positive ttl yields null poll interval")
        void nonPositiveTtl() {
            EdgeResponse zero =
                    ResponseParser.parseEdgeResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(0));
            assertNull(zero.getPollInterval());

            EdgeResponse negative =
                    ResponseParser.parseEdgeResponse(EdgeResponseJsonFixtures.bodyWithTtlOnly(-1));
            assertNull(negative.getPollInterval());
        }

        @Test
        @DisplayName("Invalid JSON throws ResponseParseException")
        void invalidJson() {
            assertThrows(
                    ResponseParseException.class,
                    () -> ResponseParser.parseEdgeResponse("not json"));
        }

        @Test
        @DisplayName("Valid JSON with empty featureGroups array succeeds")
        void emptyFeatureGroupsArray() {
            String json =
                    "{\"v\":2,\"ttl\":90,\"contextVersion\":\"ctx-v1\","
                            + "\"contexts\":[],\"featureGroups\":[]}";
            EdgeResponse response = ResponseParser.parseEdgeResponse(json);
            assertEquals(2, response.getVersion());
            assertEquals("ctx-v1", response.getContextVersion());
            assertEquals(0, response.getFeatureGroups().length);
            assertTrue(response.getContextVariableMap().isEmpty());
        }

        @Test
        @DisplayName("Absent v defaults to 0")
        void absentVersionDefaultsToZero() {
            String json = "{\"ttl\":90,\"featureGroups\":[]}";
            assertEquals(0, ResponseParser.parseEdgeResponse(json).getVersion());
        }
    }

    @Nested
    @DisplayName("parseEdgeResponse — contexts")
    class ContextTests {

        @Test
        @DisplayName("Absent contexts key yields empty context maps")
        void absentContextsKey() {
            String json =
                    EdgeResponseJsonFixtures.bodyFeaturesOnly(
                            120,
                            "ctx-v1",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            EdgeResponse response = ResponseParser.parseEdgeResponse(json);
            assertTrue(response.getContextVariableMap().isEmpty());
            assertTrue(response.getFieldDataTypeCache().isEmpty());
            assertEquals("ctx-v1", response.getContextVersion());
            assertEquals(1, response.getFeatureGroups().length);
        }

        @Test
        @DisplayName("Empty contexts array yields empty context maps")
        void emptyContextsArray() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            EdgeResponse response = ResponseParser.parseEdgeResponse(json);
            assertTrue(response.getContextVariableMap().isEmpty());
            assertTrue(response.getFieldDataTypeCache().isEmpty());
            assertEquals("ctx-v1", response.getContextVersion());
            assertEquals(1, response.getFeatureGroups().length);
        }

        @Test
        @DisplayName("Preserves wire casing in fieldDataTypeCache")
        void fieldDataTypeCachePreservesCasing() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[{\"id\":\"country\",\"type\":\"STRING\"},{\"id\":\"imsOrg\",\"type\":\"STRING\"}]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            Map<String, String> types =
                    ResponseParser.parseEdgeResponse(json).getFieldDataTypeCache();
            assertEquals("STRING", types.get("country"));
            assertEquals("STRING", types.get("imsOrg"));
            assertNull(types.get("COUNTRY"));
        }

        @Test
        @DisplayName("contextVariableMap maps uppercase key to wire id casing")
        void contextVariableMapUppercaseKey() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[{\"id\":\"country\",\"type\":\"STRING\"}]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            Map<String, String> map =
                    ResponseParser.parseEdgeResponse(json).getContextVariableMap();
            assertEquals("country", map.get("COUNTRY"));
        }

        @Test
        @DisplayName("COMPLEX type normalizes to STRING")
        void complexTypeNormalizesToString() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[{\"id\":\"attrs\",\"type\":\"COMPLEX\"}]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            assertEquals(
                    "STRING",
                    ResponseParser.parseEdgeResponse(json).getFieldDataTypeCache().get("attrs"));
        }

        @Test
        @DisplayName("Skips context entries missing id or type")
        void skipsIncompleteContextEntries() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[{\"id\":\"ok\",\"type\":\"STRING\"},{\"id\":\"missing-type\"},{\"type\":\"STRING\"}]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            Map<String, String> types =
                    ResponseParser.parseEdgeResponse(json).getFieldDataTypeCache();
            assertEquals(1, types.size());
            assertEquals("STRING", types.get("ok"));
        }
    }

    @Nested
    @DisplayName("parseEdgeResponse — feature groups and features")
    class FeatureGroupTests {

        @Test
        @DisplayName("Parses criteria as pre-serialized string")
        void criteriaAsString() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroupWithFields(
                                    10,
                                    "grp",
                                    "\"criteria\":\"{\\\"operator\\\":\\\"IN\\\"}\"",
                                    ""));

            assertEquals(
                    "{\"operator\":\"IN\"}",
                    ResponseParser.parseEdgeResponse(json).getFeatureGroups()[0].getCriteria());
        }

        @Test
        @DisplayName("Parses criteria as JSON object")
        void criteriaAsObject() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroupWithFields(
                                    10, "grp", "\"criteria\":{\"operator\":\"IN\"}", ""));

            assertEquals(
                    "{\"operator\":\"IN\"}",
                    ResponseParser.parseEdgeResponse(json).getFeatureGroups()[0].getCriteria());
        }

        @Test
        @DisplayName("Ignores string primitives in features array")
        void ignoresStringPrimitivesInFeaturesArray() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            "{\"id\":1,\"key\":\"g\",\"features\":[\"legacy-name\","
                                    + EdgeResponseJsonFixtures.feature(2, "object-feature", "")
                                    + "]}");

            FeaturesResponse group = ResponseParser.parseEdgeResponse(json).getFeatureGroups()[0];
            assertEquals(1, group.getFeaturesObj().length);
            assertEquals("object-feature", group.getFeaturesObj()[0].getFeature());
            assertArrayEquals(new String[] {"object-feature"}, group.getFeatures());
        }

        @Test
        @DisplayName("analyticsEnabled defaults to true when absent")
        void analyticsEnabledDefaultsTrue() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1, "g", EdgeResponseJsonFixtures.feature(1, "f", "")));

            assertTrue(
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .isAnalyticsEnabled());
        }

        @Test
        @DisplayName("analyticsEnabled parses explicit false")
        void analyticsEnabledExplicitFalse() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroup(
                                    1,
                                    "g",
                                    EdgeResponseJsonFixtures.feature(
                                            1, "f", "\"analyticsEnabled\":false")));

            assertFalse(
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .isAnalyticsEnabled());
        }
    }

    @Nested
    @DisplayName("parseEdgeResponse — meta Base64")
    class MetaTests {

        @Test
        @DisplayName("Base64 JSON text stored as literal decoded string")
        void metaBase64JsonText() {
            String decoded = "{\"buttonColor\":\"#FF6B35\"}";
            String wire = "eyJidXR0b25Db2xvciI6IiNGRjZCMzUifQ==";
            String json = featureWithMeta(wire);

            assertEquals(
                    decoded,
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .getMeta());
        }

        @Test
        @DisplayName("Base64 plain text stored as decoded string")
        void metaBase64PlainText() {
            String decoded = "six_context_ff";
            String wire = "c2l4X2NvbnRleHRfZmY=";
            String json = featureWithMeta(wire);

            assertEquals(
                    decoded,
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .getMeta());
        }

        @Test
        @DisplayName("Invalid Base64 yields null meta")
        void invalidBase64YieldsNull() {
            String json = featureWithMeta("!!!not-base64!!!");
            assertNull(
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .getMeta());
        }

        @Test
        @DisplayName("Empty decoded string yields null meta")
        void emptyDecodedYieldsNull() {
            String wire = Base64.getEncoder().encodeToString(new byte[0]);
            String json = featureWithMeta(wire);
            assertNull(
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .getMeta());
        }
    }

    @Nested
    @DisplayName("parseEdgeResponse — v2 policy")
    class PolicyTests {

        @Test
        @DisplayName("Parses hashAlgorithm and buckets wire keys")
        void v2PolicyKeys() {
            String json =
                    EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                            120,
                            "ctx-v1",
                            "[]",
                            EdgeResponseJsonFixtures.featureGroupWithFields(
                                    1,
                                    "g",
                                    "",
                                    EdgeResponseJsonFixtures.feature(
                                            1,
                                            "f",
                                            "\"policyId\":500,\"policy\":{\"id\":500,"
                                                + "\"hashAlgorithm\":\"MURMUR_HASH\","
                                                + "\"seed\":\"seed-1\","
                                                + "\"buckets\":[{\"variantId\":\"9\",\"start\":0,\"end\":9999,\"percentage\":100}]"
                                                + "}")));

            PolicyCache.PolicyDetail policy =
                    ResponseParser.parseEdgeResponse(json)
                            .getFeatureGroups()[0]
                            .getFeaturesObj()[0]
                            .getPolicy();
            assertNotNull(policy);
            assertEquals("MURMUR_HASH", policy.getHashAlgorithmType());
            assertEquals("9", policy.getBuckets().get(0).getVariantId());
        }

        @Test
        @DisplayName("Numeric variantId coerces to string")
        void numericVariantId() {
            String json = policyBucketJson("\"variantId\":0");
            assertEquals("0", parsedPolicy(json).getBuckets().get(0).getVariantId());

            PolicyEvaluator.PolicyVariantResponse r =
                    PolicyEvaluator.getPolicyVariant(parsedPolicy(json), "user");
            assertTrue(r.isControlGroup());
            assertEquals("0", r.getVariantId());
        }

        @Test
        @DisplayName("Boolean variantId coerces to true/false strings")
        void booleanVariantId() {
            assertEquals(
                    "true",
                    parsedPolicy(policyBucketJson("\"variantId\":true"))
                            .getBuckets()
                            .get(0)
                            .getVariantId());
            assertEquals(
                    "false",
                    parsedPolicy(policyBucketJson("\"variantId\":false"))
                            .getBuckets()
                            .get(0)
                            .getVariantId());
        }

        @Test
        @DisplayName("Omitted variantId parses as empty string")
        void omittedVariantId() {
            assertEquals("", parsedPolicy(policyBucketJson("")).getBuckets().get(0).getVariantId());
        }
    }

    private static String featureWithMeta(String wireMeta) {
        return EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                120,
                "ctx-v1",
                "[]",
                EdgeResponseJsonFixtures.featureGroup(
                        1,
                        "g",
                        EdgeResponseJsonFixtures.feature(1, "f", "\"meta\":\"" + wireMeta + "\"")));
    }

    private static String policyBucketJson(String variantIdField) {
        String bucket =
                variantIdField.isEmpty()
                        ? "{\"start\":0,\"end\":1,\"percentage\":50}"
                        : "{" + variantIdField + ",\"start\":0,\"end\":1,\"percentage\":50}";
        return EdgeResponseJsonFixtures.bodyWithFeatureGroups(
                120,
                "ctx-v1",
                "[]",
                EdgeResponseJsonFixtures.featureGroupWithFields(
                        1,
                        "g",
                        "",
                        EdgeResponseJsonFixtures.feature(
                                1,
                                "f",
                                "\"policy\":{\"hashAlgorithm\":\"PREVIEW_SIMPLE_HASH\",\"seed\":\"x\","
                                    + "\"buckets\":["
                                        + bucket
                                        + "],"
                                        + "\"previewUserVariantMap\":{\"u1\":\"\"}}")));
    }

    private static PolicyCache.PolicyDetail parsedPolicy(String json) {
        return ResponseParser.parseEdgeResponse(json)
                .getFeatureGroups()[0]
                .getFeaturesObj()[0]
                .getPolicy();
    }
}
