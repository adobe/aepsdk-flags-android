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

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.models.EdgeResponse;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses JSON responses from the Flags service into SDK model objects.
 *
 * <p>It may be changed or removed without notice in any release.
 */
public final class ResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(ResponseParser.class);

    private static final String KEY_ID = "id";
    private static final String KEY_TYPE = "type";
    private static final String KEY_POLICY = "policy";
    private static final String KEY_SEED = "seed";
    private static final String KEY_VARIANT_ID = "variantId";
    private static final String KEY_START = "start";
    private static final String KEY_END = "end";
    private static final String KEY_PERCENTAGE = "percentage";
    private static final String KEY_PREVIEW_USER_VARIANT_MAP = "previewUserVariantMap";
    private static final String KEY_META = "meta";

    private ResponseParser() {
        // Prevent instantiation
    }

    /**
     * Parse a combined response body into an {@link EdgeResponse}.
     *
     * @param body combined features response root object
     * @return parsed response
     * @throws ResponseParseException when the body is not valid combined-response JSON
     */
    public static EdgeResponse parseEdgeResponse(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            int version = 0;
            if (json.has(Constants.JSON_KEY_VERSION)
                    && !json.get(Constants.JSON_KEY_VERSION).isJsonNull()) {
                version = json.get(Constants.JSON_KEY_VERSION).getAsInt();
            }

            Integer ttl = null;
            if (json.has(Constants.JSON_TTL) && !json.get(Constants.JSON_TTL).isJsonNull()) {
                ttl = json.get(Constants.JSON_TTL).getAsInt();
            }

            String contextVersion = null;
            if (json.has(Constants.JSON_KEY_CONTEXT_VERSION)
                    && !json.get(Constants.JSON_KEY_CONTEXT_VERSION).isJsonNull()) {
                contextVersion = json.get(Constants.JSON_KEY_CONTEXT_VERSION).getAsString();
            }

            Map<String, String> contextVariableMap = new HashMap<>();
            Map<String, String> fieldDataTypeCache = new HashMap<>();
            if (json.has(Constants.JSON_KEY_CONTEXTS)
                    && json.get(Constants.JSON_KEY_CONTEXTS).isJsonArray()) {
                parseContextsArray(
                        json.getAsJsonArray(Constants.JSON_KEY_CONTEXTS),
                        contextVariableMap,
                        fieldDataTypeCache);
            }

            FeaturesResponse[] featureGroups = new FeaturesResponse[0];
            JsonArray groups = json.getAsJsonArray(Constants.JSON_KEY_FEATURE_GROUPS);
            if (groups != null) {
                List<FeaturesResponse> parsedGroups = new ArrayList<>(groups.size());
                for (JsonElement element : groups) {
                    if (element.isJsonObject()) {
                        parsedGroups.add(parseFeatureGroup(element.getAsJsonObject()));
                    }
                }
                featureGroups = parsedGroups.toArray(new FeaturesResponse[0]);
            }

            return new EdgeResponse(
                    version,
                    ttl,
                    contextVersion,
                    contextVariableMap,
                    fieldDataTypeCache,
                    featureGroups);
        } catch (ResponseParseException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to parse Edge response", e);
            throw new ResponseParseException("Failed to parse Edge response", e);
        }
    }

    /** Criteria can arrive as either a pre-serialized JSON string or a JSON object/array. */
    private static String extractCriteria(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return element.toString();
    }

    private static String extractNullableString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static void parseContextsArray(
            JsonArray contextsArray,
            Map<String, String> contextVariableMap,
            Map<String, String> fieldDataTypeCache) {
        for (JsonElement element : contextsArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject context = element.getAsJsonObject();
            if (!context.has(KEY_ID) || !context.has(KEY_TYPE)) {
                continue;
            }

            String contextId = context.get(KEY_ID).getAsString();
            String dataType = context.get(KEY_TYPE).getAsString().toUpperCase();
            if (Constants.CONTEXT_DATA_TYPE_COMPLEX.equals(dataType)) {
                dataType = Constants.CONTEXT_DATA_TYPE_STRING;
            }

            contextVariableMap.put(contextId.toUpperCase(), contextId);
            fieldDataTypeCache.put(contextId, dataType);
        }
    }

    private static FeaturesResponse parseFeatureGroup(JsonObject groupJson) {
        FeaturesResponse response = new FeaturesResponse();

        if (groupJson.has(KEY_ID) && !groupJson.get(KEY_ID).isJsonNull()) {
            response.setFeatureGroupId(groupJson.get(KEY_ID).getAsInt());
        }

        if (groupJson.has(Constants.JSON_KEY_KEY)
                && !groupJson.get(Constants.JSON_KEY_KEY).isJsonNull()) {
            response.setFeatureGroupName(groupJson.get(Constants.JSON_KEY_KEY).getAsString());
        }

        if (groupJson.has(Constants.JSON_KEY_CRITERIA)) {
            response.setCriteria(extractCriteria(groupJson.get(Constants.JSON_KEY_CRITERIA)));
        }

        if (groupJson.has(Constants.JSON_KEY_POLICY_ID)
                && !groupJson.get(Constants.JSON_KEY_POLICY_ID).isJsonNull()) {
            response.setPolicyId(groupJson.get(Constants.JSON_KEY_POLICY_ID).getAsInt());
        }

        if (groupJson.has(KEY_POLICY) && !groupJson.get(KEY_POLICY).isJsonNull()) {
            response.setPolicy(parsePolicyDetail(groupJson.getAsJsonObject(KEY_POLICY)));
        }

        if (groupJson.has(Constants.JSON_KEY_HASH)) {
            response.setHash(extractNullableString(groupJson.get(Constants.JSON_KEY_HASH)));
        }

        Map<String, Object> params = parseCohortingParams(groupJson);
        if (params != null) {
            response.setParams(params);
        }

        if (groupJson.has(Constants.JSON_KEY_FEATURES)) {
            parseFeaturesArray(groupJson.getAsJsonArray(Constants.JSON_KEY_FEATURES), response);
        }

        return response;
    }

    private static void parseFeaturesArray(JsonArray featuresArray, FeaturesResponse response) {
        List<String> featureNames = new ArrayList<>(featuresArray.size());
        List<Feature> featuresObj = new ArrayList<>(featuresArray.size());

        for (JsonElement element : featuresArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            Feature feature = parseFeature(element.getAsJsonObject());
            featuresObj.add(feature);
            featureNames.add(feature.getFeature());
        }

        response.setFeatures(featureNames.toArray(new String[0]));
        if (!featuresObj.isEmpty()) {
            response.setFeaturesObj(featuresObj.toArray(new Feature[0]));
        }
    }

    private static Feature parseFeature(JsonObject featureJson) {
        Feature feature = new Feature();

        if (featureJson.has(KEY_ID) && !featureJson.get(KEY_ID).isJsonNull()) {
            feature.setId(featureJson.get(KEY_ID).getAsInt());
        }

        if (featureJson.has(Constants.JSON_KEY_KEY)
                && !featureJson.get(Constants.JSON_KEY_KEY).isJsonNull()) {
            feature.setFeature(featureJson.get(Constants.JSON_KEY_KEY).getAsString());
        }

        if (featureJson.has(Constants.JSON_KEY_CRITERIA)) {
            feature.setCriteria(extractCriteria(featureJson.get(Constants.JSON_KEY_CRITERIA)));
        }

        if (featureJson.has(Constants.JSON_KEY_POLICY_ID)
                && !featureJson.get(Constants.JSON_KEY_POLICY_ID).isJsonNull()) {
            feature.setPolicyId(featureJson.get(Constants.JSON_KEY_POLICY_ID).getAsInt());
        }

        if (featureJson.has(KEY_POLICY) && !featureJson.get(KEY_POLICY).isJsonNull()) {
            feature.setPolicy(parsePolicyDetail(featureJson.getAsJsonObject(KEY_POLICY)));
        }

        if (featureJson.has(Constants.JSON_KEY_HASH)) {
            feature.setHash(extractNullableString(featureJson.get(Constants.JSON_KEY_HASH)));
        }

        Map<String, Object> params = parseCohortingParams(featureJson);
        if (params != null) {
            feature.setParams(params);
        }

        if (featureJson.has(KEY_META)) {
            feature.setMeta(parseMetaBase64(featureJson.get(KEY_META)));
        }

        boolean analyticsEnabled = true;
        if (featureJson.has(Constants.JSON_KEY_ANALYTICS_ENABLED)
                && !featureJson.get(Constants.JSON_KEY_ANALYTICS_ENABLED).isJsonNull()) {
            analyticsEnabled = featureJson.get(Constants.JSON_KEY_ANALYTICS_ENABLED).getAsBoolean();
        }
        feature.setAnalyticsEnabled(analyticsEnabled);

        return feature;
    }

    private static Map<String, Object> parseCohortingParams(JsonObject json) {
        Map<String, Object> params = new HashMap<>();
        if (json.has(Constants.JSON_KEY_PARAMS)
                && json.get(Constants.JSON_KEY_PARAMS).isJsonObject()) {
            mergeCohortingFields(params, json.getAsJsonObject(Constants.JSON_KEY_PARAMS));
        }
        mergeCohortingFields(params, json);
        return params.isEmpty() ? null : params;
    }

    private static void mergeCohortingFields(Map<String, Object> target, JsonObject source) {
        mergeStringField(target, source, Constants.JSON_KEY_COHORTING_TYPE);
        mergeStringField(target, source, Constants.JSON_KEY_COHORTING_NAMESPACE_CODE);
    }

    private static void mergeStringField(
            Map<String, Object> target, JsonObject source, String key) {
        if (!target.containsKey(key) && source.has(key) && !source.get(key).isJsonNull()) {
            target.put(key, source.get(key).getAsString());
        }
    }

    private static PolicyCache.PolicyDetail parsePolicyDetail(JsonObject policyJson) {
        try {
            Integer id =
                    policyJson.has(KEY_ID) && !policyJson.get(KEY_ID).isJsonNull()
                            ? policyJson.get(KEY_ID).getAsInt()
                            : null;
            String hashAlgorithm =
                    policyJson.has(Constants.JSON_KEY_HASH_ALGORITHM)
                                    && !policyJson
                                            .get(Constants.JSON_KEY_HASH_ALGORITHM)
                                            .isJsonNull()
                            ? policyJson.get(Constants.JSON_KEY_HASH_ALGORITHM).getAsString()
                            : null;
            String seed =
                    policyJson.has(KEY_SEED) && !policyJson.get(KEY_SEED).isJsonNull()
                            ? policyJson.get(KEY_SEED).getAsString()
                            : null;

            List<PolicyCache.PolicyBucket> buckets = new ArrayList<>();
            if (policyJson.has(Constants.JSON_KEY_BUCKETS)
                    && policyJson.get(Constants.JSON_KEY_BUCKETS).isJsonArray()) {
                for (JsonElement bucketElement :
                        policyJson.getAsJsonArray(Constants.JSON_KEY_BUCKETS)) {
                    JsonObject bucketJson = bucketElement.getAsJsonObject();
                    String variantId = parseVariantId(bucketJson.get(KEY_VARIANT_ID));
                    int start =
                            bucketJson.has(KEY_START) ? bucketJson.get(KEY_START).getAsInt() : 0;
                    int end = bucketJson.has(KEY_END) ? bucketJson.get(KEY_END).getAsInt() : 0;
                    int percentage =
                            bucketJson.has(KEY_PERCENTAGE)
                                    ? bucketJson.get(KEY_PERCENTAGE).getAsInt()
                                    : 0;
                    buckets.add(new PolicyCache.PolicyBucket(variantId, start, end, percentage));
                }
            }

            Map<String, String> previewUserVariantMap = null;
            if (policyJson.has(KEY_PREVIEW_USER_VARIANT_MAP)
                    && policyJson.get(KEY_PREVIEW_USER_VARIANT_MAP).isJsonObject()) {
                previewUserVariantMap = new HashMap<>();
                JsonObject previewMap = policyJson.getAsJsonObject(KEY_PREVIEW_USER_VARIANT_MAP);
                for (String key : previewMap.keySet()) {
                    if (!previewMap.get(key).isJsonNull()) {
                        previewUserVariantMap.put(key, previewMap.get(key).getAsString());
                    }
                }
            }

            return new PolicyCache.PolicyDetail(
                    id, hashAlgorithm, seed, buckets, previewUserVariantMap);
        } catch (Exception e) {
            logger.warn("Failed to parse policy detail: {}", e.getMessage());
            return null;
        }
    }

    private static String parseVariantId(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean() ? "true" : "false";
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsJsonPrimitive().getAsNumber().toString();
            }
            return element.getAsString();
        }
        return "";
    }

    private static String parseMetaBase64(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String wire = element.getAsString();
        if (wire.isEmpty()) {
            return null;
        }

        byte[] decoded = decodeBase64(wire);
        if (decoded == null) {
            logger.warn("Failed to decode feature meta from Base64");
            return null;
        }

        String utf8 = new String(decoded, StandardCharsets.UTF_8);
        if (utf8.isEmpty()) {
            return null;
        }
        return utf8;
    }

    private static byte[] decodeBase64(String wire) {
        // Strip any whitespace/newlines (covers MIME-style wrapped Base64), then decode with okio
        // (API 21-safe; java.util.Base64 requires API 26). Returns null on invalid input.
        okio.ByteString decoded = okio.ByteString.decodeBase64(wire.replaceAll("\\s", ""));
        return decoded == null ? null : decoded.toByteArray();
    }
}
