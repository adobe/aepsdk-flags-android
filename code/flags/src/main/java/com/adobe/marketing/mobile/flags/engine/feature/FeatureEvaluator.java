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

import static com.adobe.marketing.mobile.flags.engine.constants.Constants.CONTROL_GROUP_FEATURE_ID;

import com.adobe.marketing.mobile.flags.engine.cache.SDKClientCache;
import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import com.adobe.marketing.mobile.flags.engine.runtime.FilterService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates feature flags against cached feature group data, targeting criteria, and A/B policies.
 *
 * <p>It may be changed or removed without notice in any release.
 */
public final class FeatureEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(FeatureEvaluator.class);

    private final String clientId;
    private final SDKClientCache cache;
    private final PolicyCache policyCache;
    private volatile FilterService filterService;

    public FeatureEvaluator(String clientId, SDKClientCache cache, PolicyCache policyCache) {
        this.clientId = clientId;
        this.cache = cache;
        this.policyCache = policyCache;
    }

    public void setFilterService(FilterService filterService) {
        this.filterService = filterService;
    }

    public void clearFilterService() {
        this.filterService = null;
    }

    /**
     * Evaluates all features for the current context.
     *
     * @param request evaluation context (user attributes, identity map)
     * @return array of matching features (never {@code null}; empty if none match)
     */
    public FeatureResult[] evaluateAll(GetFeatureRequest request) {
        SDKClientCache.CacheEntry cacheEntry = cache.getFeatures(clientId);
        if (cacheEntry == null || cacheEntry.isEmpty()) {
            return new FeatureResult[0];
        }

        UserAttributes userAttributes = UserAttributes.fromContext(request.getContext());
        Map<String, List<Map<String, Object>>> identityMap = request.getIdentityMap();

        IdentityHashMap<SDKClientCache.FeatureGroupRef, FeatureGroupContext> resolvedFeatureGroups =
                new IdentityHashMap<>();
        List<FeatureResult> results = new ArrayList<>();

        for (Map.Entry<String, SDKClientCache.IndexedFeature> entry : cacheEntry.allEntries()) {
            SDKClientCache.IndexedFeature indexed = entry.getValue();
            SDKClientCache.FeatureGroupRef featureGroup = indexed.getFeatureGroup();

            FeatureGroupContext ctx = resolvedFeatureGroups.get(featureGroup);
            if (ctx == null) {
                ctx =
                        resolveFeatureGroupContextOrExcluded(
                                featureGroup, userAttributes, identityMap);
                resolvedFeatureGroups.put(featureGroup, ctx);
            }
            if (ctx == EXCLUDED_FEATURE_GROUP) {
                continue;
            }

            if (indexed.getFeature() != null) {
                FeatureResult result =
                        evaluateSingleFeature(
                                indexed.getFeature(),
                                ctx.featureGroupId,
                                ctx.featureGroupKey,
                                ctx.featureGroupVariantId,
                                userAttributes,
                                identityMap,
                                indexed.getCohortingNamespace());
                if (result != null) {
                    results.add(result);
                }
            } else {
                String featureKey = entry.getKey();
                AnalyticsParam analytics =
                        new AnalyticsParam(
                                ctx.featureGroupId, 0, featureKey, ctx.featureGroupVariantId);
                results.add(
                        new FeatureResult(
                                0, featureKey, ctx.featureGroupKey, null, null, analytics));
            }
        }
        return results.toArray(new FeatureResult[0]);
    }

    /**
     * Evaluates a single feature by name.
     *
     * @param featureName the feature key to look up
     * @param request evaluation context (user attributes, identity map)
     * @return the matching {@link FeatureResult}, or {@code null} if not found or excluded
     */
    public FeatureResult evaluate(String featureName, GetFeatureRequest request) {
        if (featureName == null) {
            return null;
        }

        SDKClientCache.CacheEntry cacheEntry = cache.getFeatures(clientId);
        if (cacheEntry == null) {
            return null;
        }

        SDKClientCache.IndexedFeature indexed = cacheEntry.findFeature(featureName);
        if (indexed == null) {
            return null;
        }

        UserAttributes userAttributes = UserAttributes.fromContext(request.getContext());
        Map<String, List<Map<String, Object>>> identityMap = request.getIdentityMap();

        FeatureGroupContext ctx =
                resolveFeatureGroupContext(indexed.getFeatureGroup(), userAttributes, identityMap);
        if (ctx == null) {
            return null;
        }

        if (indexed.getFeature() != null) {
            return evaluateSingleFeature(
                    indexed.getFeature(),
                    ctx.featureGroupId,
                    ctx.featureGroupKey,
                    ctx.featureGroupVariantId,
                    userAttributes,
                    identityMap,
                    indexed.getCohortingNamespace());
        }

        AnalyticsParam analytics =
                new AnalyticsParam(ctx.featureGroupId, 0, featureName, ctx.featureGroupVariantId);
        return new FeatureResult(0, featureName, ctx.featureGroupKey, null, null, analytics);
    }

    /**
     * Checks whether a single feature is enabled for the given context.
     *
     * <p>Returns {@code false} when {@link #evaluate} returns {@code null}, or when the result is a
     * feature-level policy <b>control</b> cohort ({@link FeatureResult#getKey()} {@code null},
     * {@link FeatureResult#getId()} {@code -1}). Otherwise, returns {@code true} when the feature
     * key is non-null.
     *
     * @param featureName the feature key to check
     * @param request evaluation context (user attributes, identity map)
     * @return {@code true} if the feature is present and enabled (non-control cohort for feature
     *     policy)
     */
    public boolean isEnabled(String featureName, GetFeatureRequest request) {
        FeatureResult featureResult = evaluate(featureName, request);
        return featureResult != null && featureResult.getKey() != null;
    }

    // --- shared helpers ---

    /** Sentinel for feature groups excluded by criteria or control-group policy. */
    private static final FeatureGroupContext EXCLUDED_FEATURE_GROUP =
            new FeatureGroupContext(0, null, null);

    /**
     * Resolves feature group-level criteria and policy, returning {@link #EXCLUDED_FEATURE_GROUP}
     * instead of {@code null} for excluded feature groups. This allows {@code computeIfAbsent} to
     * cache the result.
     */
    private FeatureGroupContext resolveFeatureGroupContextOrExcluded(
            SDKClientCache.FeatureGroupRef featureGroup,
            UserAttributes userAttributes,
            Map<String, List<Map<String, Object>>> identityMap) {
        FeatureGroupContext ctx =
                resolveFeatureGroupContext(featureGroup, userAttributes, identityMap);
        return ctx != null ? ctx : EXCLUDED_FEATURE_GROUP;
    }

    /**
     * Resolves feature group-level criteria and policy. Returns the feature group context if the
     * feature group passes, or {@code null} if the feature group should be skipped (criteria
     * mismatch or control group).
     */
    private FeatureGroupContext resolveFeatureGroupContext(
            SDKClientCache.FeatureGroupRef featureGroup,
            UserAttributes userAttributes,
            Map<String, List<Map<String, Object>>> identityMap) {
        if (!matchesFeatureGroupCriteria(featureGroup, userAttributes)) {
            return null;
        }

        PolicyEvaluator.PolicyVariantResponse featureGroupVariant =
                resolvePolicy(
                        policyCache,
                        featureGroup.getPolicyId(),
                        identityMap,
                        featureGroup.getCohortingNamespace());
        if (featureGroupVariant != null && featureGroupVariant.isControlGroup()) {
            return null;
        }

        String featureGroupVariantId =
                featureGroupVariant != null ? featureGroupVariant.getVariantId() : null;
        return new FeatureGroupContext(
                featureGroup.getFeatureGroupId(),
                featureGroup.getFeatureGroupName(),
                featureGroupVariantId);
    }

    /**
     * Evaluates a single {@link Feature} against its criteria and policy. Returns the resulting
     * {@link FeatureResult} or {@code null} if excluded.
     *
     * <p>Feature-level policy <b>control</b> cohort: returns a sentinel row ({@code id=-1}, {@code
     * key=null}) with {@link AnalyticsParam} only; {@link #isEnabled} uses {@code key==null} to
     * report {@code false}.
     */
    private FeatureResult evaluateSingleFeature(
            Feature feature,
            int featureGroupId,
            String featureGroupKey,
            String featureGroupVariantId,
            UserAttributes userAttributes,
            Map<String, List<Map<String, Object>>> identityMap,
            String cohortingNamespace) {
        if (!matchesFeatureCriteria(feature, featureGroupId, userAttributes)) {
            return null;
        }

        String variantId = featureGroupVariantId;
        PolicyEvaluator.PolicyVariantResponse featureVariant =
                resolvePolicy(policyCache, feature.getPolicyId(), identityMap, cohortingNamespace);

        if (featureVariant != null) {
            variantId = featureVariant.getVariantId();
            if (featureVariant.isControlGroup()) {
                AnalyticsParam analytics =
                        new AnalyticsParam(
                                featureGroupId, feature.getId(), feature.getFeature(), variantId);
                return new FeatureResult(
                        CONTROL_GROUP_FEATURE_ID, null, null, null, null, analytics);
            }
        }

        AnalyticsParam analytics =
                new AnalyticsParam(
                        featureGroupId, feature.getId(), feature.getFeature(), variantId);
        return new FeatureResult(
                feature.getId(),
                feature.getFeature(),
                featureGroupKey,
                feature.getValue(),
                feature.getMeta(),
                analytics);
    }

    /**
     * Lightweight carrier for resolved feature group-level state, avoiding repeated resolution when
     * the same feature group is processed by both evaluateAll and evaluate paths.
     */
    private static final class FeatureGroupContext {
        final int featureGroupId;
        final String featureGroupKey;
        final String featureGroupVariantId;

        FeatureGroupContext(
                int featureGroupId, String featureGroupKey, String featureGroupVariantId) {
            this.featureGroupId = featureGroupId;
            this.featureGroupKey = featureGroupKey;
            this.featureGroupVariantId = featureGroupVariantId;
        }
    }

    private boolean matchesFeatureGroupCriteria(
            SDKClientCache.FeatureGroupRef featureGroup, UserAttributes userAttributes) {
        String criteria = featureGroup.getCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return true;
        }
        if (filterService == null) {
            logger.warn(
                    "[Flags] Criteria present but filter service is not initialized for client: {}",
                    clientId);
            return false;
        }
        String logicalId = "featureGroup:" + featureGroup.getFeatureGroupId();
        return filterService.evaluateCriteriaCached(
                userAttributes, logicalId, criteria, featureGroup.getCriteriaHash());
    }

    private boolean matchesFeatureCriteria(
            Feature feature, int featureGroupId, UserAttributes userAttributes) {
        String criteria = feature.getCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return true;
        }
        if (filterService == null) {
            logger.warn(
                    "[Flags] Criteria present but filter service is not initialized for client: {}",
                    clientId);
            return false;
        }
        String logicalId = "feature:" + featureGroupId + ":" + feature.getId();
        return filterService.evaluateCriteriaCached(
                userAttributes, logicalId, criteria, feature.getHash());
    }

    /**
     * Resolves the policy variant for a given policy ID.
     *
     * @return the variant response, or {@code null} if no policy is configured
     */
    private static PolicyEvaluator.PolicyVariantResponse resolvePolicy(
            PolicyCache policyCache,
            Integer policyId,
            Map<String, List<Map<String, Object>>> identityMap,
            String cohortingNamespace) {
        if (policyId == null) {
            return null;
        }
        String identifier = PolicyEvaluator.getIdentifier(identityMap, cohortingNamespace);
        return PolicyEvaluator.getPolicyVariantFromCache(policyCache, policyId, identifier);
    }
}
