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

import static com.adobe.marketing.mobile.flags.engine.constants.Constants.CONTROL_GROUP_VARIANT_ID;
import static com.adobe.marketing.mobile.flags.engine.constants.Constants.DEFAULT_COHORTING_NAMESPACE;
import static com.adobe.marketing.mobile.flags.engine.constants.Constants.IDENTITY_ENTRY_KEY_ID;
import static com.adobe.marketing.mobile.flags.engine.constants.Constants.IDENTITY_ENTRY_KEY_PRIMARY;
import static com.adobe.marketing.mobile.flags.engine.constants.Constants.JSON_KEY_COHORTING_NAMESPACE_CODE;
import static com.adobe.marketing.mobile.flags.engine.constants.Constants.JSON_KEY_COHORTING_TYPE;

import com.adobe.marketing.mobile.flags.engine.common.CohortingType;
import com.adobe.marketing.mobile.flags.engine.utils.Utils;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Evaluates A/B policy assignments for consistent user bucketing. */
public final class PolicyEvaluator {

    private static final HashStrategy DEFAULT_HASH_STRATEGY = new MurmurHashStrategy();
    private static final PolicyVariantResponse CONTROL_GROUP =
            new PolicyVariantResponse(CONTROL_GROUP_VARIANT_ID, true);

    /**
     * Session-stable fallback identifier used when no stable bucketing identifier is available.
     * Generated once per SDK lifecycle instead of per-call to ensure consistent A/B bucketing.
     */
    private static final class SessionFallbackHolder {
        private static final String ID = UUID.randomUUID().toString();
    }

    private PolicyEvaluator() {}

    /** Policy variant response. */
    public static final class PolicyVariantResponse {
        private final String variantId;
        private final boolean controlGroup;

        public PolicyVariantResponse(String variantId, boolean controlGroup) {
            this.variantId = variantId;
            this.controlGroup = controlGroup;
        }

        public String getVariantId() {
            return variantId;
        }

        public boolean isControlGroup() {
            return controlGroup;
        }

        @Override
        public String toString() {
            return "PolicyVariantResponse{variantId='"
                    + variantId
                    + "', controlGroup="
                    + controlGroup
                    + "}";
        }
    }

    /**
     * Resolves the bucketing identifier from an identity map entry for the given namespace.
     *
     * @param identityMap namespace → identity entries (optional; read-only)
     * @param cohortingNamespace identity namespace key for lookup
     * @return non-null bucketing identifier
     */
    public static String getIdentifier(
            Map<String, List<Map<String, Object>>> identityMap, String cohortingNamespace) {
        if (cohortingNamespace != null
                && Utils.hasLength(cohortingNamespace.trim())
                && identityMap != null
                && !identityMap.isEmpty()) {
            List<Map<String, Object>> entries = identityMap.get(cohortingNamespace);
            if (entries != null && !entries.isEmpty()) {
                Map<String, Object> selected = null;
                for (Map<String, Object> entry : entries) {
                    if (entry != null
                            && Boolean.TRUE.equals(entry.get(IDENTITY_ENTRY_KEY_PRIMARY))) {
                        selected = entry;
                        break;
                    }
                }
                if (selected == null) {
                    selected = entries.get(0);
                }
                if (selected != null) {
                    Object id = selected.get(IDENTITY_ENTRY_KEY_ID);
                    if (id instanceof String && Utils.hasLength((String) id)) {
                        return (String) id;
                    }
                }
            }
        }
        return SessionFallbackHolder.ID;
    }

    /**
     * Get the policy variant using cached policy configuration. Falls back to a simplified 50/50
     * evaluation when the policy is not in cache.
     *
     * @param policyCache per-client policy cache; returns control group if null
     * @param policyId policy ID; returns control group if null
     * @param identifier user identifier; returns control group if null
     * @return policy variant response; never null
     */
    public static PolicyVariantResponse getPolicyVariantFromCache(
            PolicyCache policyCache, Integer policyId, String identifier) {
        if (policyCache == null || policyId == null || identifier == null) {
            return CONTROL_GROUP;
        }

        PolicyCache.PolicyDetail policyDetail = policyCache.getPolicy(policyId);
        if (policyDetail != null) {
            return getPolicyVariant(policyDetail, identifier);
        }

        // Policy not in cache — fall back to simplified 50/50 evaluation using policyId as seed.
        return getPolicyVariant(policyId, identifier, 50);
    }

    /**
     * Get the policy variant for a given policy detail.
     *
     * @param policyDetail policy detail with bucket configuration
     * @param identifier user identifier for bucketing
     * @return policy variant response
     */
    public static PolicyVariantResponse getPolicyVariant(
            PolicyCache.PolicyDetail policyDetail, String identifier) {
        if (policyDetail == null || identifier == null) {
            return CONTROL_GROUP;
        }

        // Preview mode: look up identifier in the preview user-variant map
        String hashAlgorithmType = policyDetail.getHashAlgorithmType();
        if ("PREVIEW_SIMPLE_HASH".equals(hashAlgorithmType)) {
            Map<String, String> previewMap = policyDetail.getPreviewUserVariantMap();
            if (previewMap != null) {
                String variantId = previewMap.get(identifier);
                if (variantId != null) {
                    boolean isControl = isControlVariantId(variantId);
                    return new PolicyVariantResponse(
                            isControl ? CONTROL_GROUP_VARIANT_ID : variantId, isControl);
                }
            }
            // Identifier not found in preview map — treat as control
            return CONTROL_GROUP;
        }

        // Normal mode: hash(identifier, seed)
        HashStrategy hashStrategy = HashFactory.getStrategy(hashAlgorithmType);
        int hashValue = hashStrategy.hash(identifier, policyDetail.getSeed());

        // Find matching bucket
        List<PolicyCache.PolicyBucket> buckets = policyDetail.getBuckets();
        if (buckets != null) {
            for (PolicyCache.PolicyBucket bucket : buckets) {
                if (bucket.contains(hashValue)) {
                    String variantId = bucket.getVariantId();
                    boolean isControl = isControlVariantId(variantId);
                    return new PolicyVariantResponse(
                            isControl ? CONTROL_GROUP_VARIANT_ID : variantId, isControl);
                }
            }
        }

        // No matching bucket — default to control group for safety.
        return CONTROL_GROUP;
    }

    /**
     * Get the policy variant for a given identifier and bucket list.
     *
     * @param policyId policy ID (used as seed)
     * @param identifier user identifier
     * @param buckets percentage-based split configuration
     * @return policy variant response
     */
    public static PolicyVariantResponse getPolicyVariant(
            Integer policyId, String identifier, List<PercentageSplit> buckets) {
        if (policyId == null || identifier == null || buckets == null || buckets.isEmpty()) {
            return CONTROL_GROUP;
        }

        // Use policyId as seed (hash input = identifier + policyId)
        String seed = String.valueOf(policyId);
        int hashValue = DEFAULT_HASH_STRATEGY.hash(identifier, seed);
        int bucket = hashValue / DEFAULT_HASH_STRATEGY.getMultiplier(); // 0-99

        // Find matching variant
        int cumulativePercentage = 0;
        for (PercentageSplit split : buckets) {
            cumulativePercentage += split.getPercentage();
            if (bucket < cumulativePercentage) {
                boolean isControl = split.getVariantId() == 0;
                return new PolicyVariantResponse(String.valueOf(split.getVariantId()), isControl);
            }
        }

        // Default to control group if no match
        return CONTROL_GROUP;
    }

    /**
     * Simple policy evaluation when bucket configuration is not available. Uses control percentage
     * to determine if user is in control group.
     *
     * @param policyId policy ID (used as seed)
     * @param identifier user identifier
     * @param controlPercentage control group percentage (0-100)
     * @return policy variant response
     */
    public static PolicyVariantResponse getPolicyVariant(
            Integer policyId, String identifier, int controlPercentage) {
        if (policyId == null || identifier == null) {
            return CONTROL_GROUP;
        }

        // Use policyId as seed (hash input = identifier + policyId)
        String seed = String.valueOf(policyId);
        int hashValue = DEFAULT_HASH_STRATEGY.hash(identifier, seed);
        int bucket = hashValue / DEFAULT_HASH_STRATEGY.getMultiplier(); // 0-99

        boolean isControl = bucket < controlPercentage;
        String variantId = isControl ? CONTROL_GROUP_VARIANT_ID : "1";

        return new PolicyVariantResponse(variantId, isControl);
    }

    /**
     * Simple percentage-based split for ad-hoc policy evaluation. Distinct from {@link
     * PolicyCache.PolicyBucket} which uses hash-range bucketing.
     */
    public static final class PercentageSplit {
        private final int variantId;
        private final int percentage;

        public PercentageSplit(int variantId, int percentage) {
            this.variantId = variantId;
            this.percentage = percentage;
        }

        public int getVariantId() {
            return variantId;
        }

        public int getPercentage() {
            return percentage;
        }
    }

    /**
     * Resolves the identity-map namespace for policy bucketing from feature or feature group
     * params.
     *
     * @param params feature or feature group params map; may be null
     * @return namespace key for {@code identityMap} lookup, or {@code null} when identity-map
     *     lookup should not be attempted
     */
    public static String resolveCohortingNamespace(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object typeValue = params.get(JSON_KEY_COHORTING_TYPE);
        if (!(typeValue instanceof String)) {
            return null;
        }
        String cohortingType = ((String) typeValue).trim();
        if (!Utils.hasLength(cohortingType)) {
            return null;
        }
        if (CohortingType.ECID.getValue().equals(cohortingType)) {
            return DEFAULT_COHORTING_NAMESPACE;
        }
        if (CohortingType.STICKY.getValue().equals(cohortingType)) {
            Object namespaceCode = params.get(JSON_KEY_COHORTING_NAMESPACE_CODE);
            if (namespaceCode instanceof String) {
                String trimmed = ((String) namespaceCode).trim();
                if (Utils.hasLength(trimmed)) {
                    return trimmed;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Control cohort is always reported as variant id {@code "0"}. Server payloads may use an empty
     * variant id, {@code null}, or explicit {@code "0"} for the control bucket; all are treated as
     * control for bucketing and normalized to {@code "0"}.
     */
    private static boolean isControlVariantId(String variantId) {
        return variantId == null
                || variantId.isEmpty()
                || CONTROL_GROUP_VARIANT_ID.equals(variantId);
    }
}
