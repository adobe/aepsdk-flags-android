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

package com.adobe.marketing.mobile.flags.engine.cache;

import com.adobe.marketing.mobile.flags.engine.models.Feature;
import com.adobe.marketing.mobile.flags.engine.models.FeaturesResponse;
import com.adobe.marketing.mobile.flags.engine.models.MetadataResponse;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory cache for feature flags and metadata. */
public class SDKClientCache {

    /**
     * Compact carrier for the feature group-level fields needed during evaluation. Holds only
     * criteria, policy, and identity fields — the original {@link FeaturesResponse} (with its
     * {@code Feature[]} and {@code String[]} arrays) becomes GC-eligible after indexing.
     */
    public static final class FeatureGroupRef {
        private final int featureGroupId;
        private final String featureGroupName;
        private final String criteria;
        private final String criteriaHash;
        private final Integer policyId;
        private final String cohortingNamespace;

        FeatureGroupRef(FeaturesResponse featureGroup) {
            this.featureGroupId = featureGroup.getFeatureGroupId();
            this.featureGroupName = featureGroup.getFeatureGroupName();
            this.criteria = featureGroup.getCriteria();
            this.criteriaHash = featureGroup.getHash();
            this.policyId = featureGroup.getPolicyId();
            this.cohortingNamespace =
                    PolicyEvaluator.resolveCohortingNamespace(featureGroup.getParams());
        }

        public int getFeatureGroupId() {
            return featureGroupId;
        }

        public String getFeatureGroupName() {
            return featureGroupName;
        }

        public String getCriteria() {
            return criteria;
        }
        /**
         * Server criteria hash; drives invalidation of compiled filter trees for this feature
         * group.
         */
        public String getCriteriaHash() {
            return criteriaHash;
        }

        public Integer getPolicyId() {
            return policyId;
        }

        public String getCohortingNamespace() {
            return cohortingNamespace;
        }
    }

    /**
     * Pre-indexed reference to a feature within its owning feature group. A feature belongs to
     * exactly one feature group (or the default {@code ||features||} container). For Feature-object
     * features, {@code feature} holds the parsed model; for string-only features it is {@code
     * null}.
     *
     * <p>Feature-level bucketing namespace is resolved from the feature's {@code params} when
     * {@code feature} is non-null; string-only features use the feature group namespace.
     */
    public static final class IndexedFeature {
        private final FeatureGroupRef featureGroup;
        private final Feature feature;
        private final String cohortingNamespace;

        IndexedFeature(FeatureGroupRef featureGroup, Feature feature) {
            this.featureGroup = featureGroup;
            this.feature = feature;
            if (feature != null) {
                this.cohortingNamespace =
                        PolicyEvaluator.resolveCohortingNamespace(feature.getParams());
            } else {
                this.cohortingNamespace = featureGroup.getCohortingNamespace();
            }
        }

        public FeatureGroupRef getFeatureGroup() {
            return featureGroup;
        }

        /**
         * @return the Feature model, or {@code null} for string-only features
         */
        public Feature getFeature() {
            return feature;
        }

        public String getCohortingNamespace() {
            return cohortingNamespace;
        }
    }

    /**
     * Immutable cache snapshot indexed by feature name. The index is the sole data store — each
     * entry maps a feature name to its owning feature group and (for Feature-object features) the
     * parsed {@link Feature} model.
     *
     * <p>Insertion order is preserved (via {@link LinkedHashMap}) so that bulk iteration in {@code
     * evaluateAll} returns features in the same order as the server response.
     */
    public static class CacheEntry {
        private final Map<String, IndexedFeature> featureIndex;
        private final String etag;
        private final long timestamp;

        CacheEntry(FeaturesResponse[] featureGroups, String etag) {
            this.etag = etag;
            this.timestamp = System.currentTimeMillis();
            this.featureIndex = buildIndex(featureGroups);
        }

        /**
         * O(1) lookup of a feature by name.
         *
         * @return the indexed feature reference, or {@code null} if not found
         */
        public IndexedFeature findFeature(String featureName) {
            return featureIndex.get(featureName);
        }

        /**
         * Returns all indexed feature entries in server-defined order. Used by {@code evaluateAll}
         * for bulk evaluation.
         */
        public Set<Map.Entry<String, IndexedFeature>> allEntries() {
            return featureIndex.entrySet();
        }

        /**
         * @return {@code true} if the cache contains no features
         */
        public boolean isEmpty() {
            return featureIndex.isEmpty();
        }

        public String getEtag() {
            return etag;
        }

        public long getTimestamp() {
            return timestamp;
        }

        private static Map<String, IndexedFeature> buildIndex(FeaturesResponse[] featureGroups) {
            if (featureGroups == null || featureGroups.length == 0) {
                return Collections.emptyMap();
            }
            Map<String, IndexedFeature> index = new LinkedHashMap<>();
            for (FeaturesResponse featureGroup : featureGroups) {
                FeatureGroupRef ref = new FeatureGroupRef(featureGroup);
                Feature[] featureObjs = featureGroup.getFeaturesObj();
                if (featureObjs != null && featureObjs.length > 0) {
                    for (Feature f : featureObjs) {
                        if (f.getFeature() != null) {
                            index.put(f.getFeature(), new IndexedFeature(ref, f));
                        }
                    }
                } else if (featureGroup.getFeatures() != null) {
                    for (String name : featureGroup.getFeatures()) {
                        if (name != null) {
                            index.put(name, new IndexedFeature(ref, null));
                        }
                    }
                }
            }
            return Collections.unmodifiableMap(index);
        }
    }

    /** Metadata cache entry. */
    public static class MetadataCacheEntry {
        private final Map<String, String> contextVariableMap;
        private final Map<String, String> fieldDataTypeCache;
        private final String contextVersion;
        private final String etag;
        private final long timestamp;

        public MetadataCacheEntry(
                Map<String, String> contextVariableMap,
                Map<String, String> fieldDataTypeCache,
                String contextVersion,
                String etag) {
            this.contextVariableMap = contextVariableMap;
            this.fieldDataTypeCache = fieldDataTypeCache;
            this.contextVersion = contextVersion;
            this.etag = etag;
            this.timestamp = System.currentTimeMillis();
        }

        public Map<String, String> getContextVariableMap() {
            return contextVariableMap;
        }

        public Map<String, String> getFieldDataTypeCache() {
            return fieldDataTypeCache;
        }

        public String getContextVersion() {
            return contextVersion;
        }

        public String getEtag() {
            return etag;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private final Map<String, CacheEntry> featureCache;
    private volatile MetadataCacheEntry metadataCache;

    public SDKClientCache() {
        this.featureCache = new ConcurrentHashMap<>();
    }

    /**
     * Store features for a client ID.
     *
     * @param clientId Client ID
     * @param features Features array
     * @param etag ETag for cache validation
     */
    public void putFeatures(String clientId, FeaturesResponse[] features, String etag) {
        if (clientId != null && features != null) {
            featureCache.put(clientId, new CacheEntry(features, etag));
        }
    }

    /**
     * Get cached features for a client ID.
     *
     * @param clientId Client ID
     * @return Cache entry or null if not found
     */
    public CacheEntry getFeatures(String clientId) {
        return featureCache.get(clientId);
    }

    /**
     * Get ETag for a client ID.
     *
     * @param clientId Client ID
     * @return ETag or null if not found
     */
    public String getFeaturesEtag(String clientId) {
        CacheEntry entry = featureCache.get(clientId);
        return entry != null ? entry.getEtag() : null;
    }

    /**
     * Check if features are cached for a client ID.
     *
     * @param clientId Client ID
     * @return true if cached
     */
    public boolean hasFeatures(String clientId) {
        return featureCache.containsKey(clientId);
    }

    /**
     * Remove cached features for a client ID.
     *
     * @param clientId Client ID
     */
    public void removeFeatures(String clientId) {
        featureCache.remove(clientId);
    }

    /**
     * Store metadata when the logical {@code contextVersion} has changed.
     *
     * @param response metadata from a combined response
     * @return {@code true} when the cache entry was replaced
     */
    public boolean putMetadata(MetadataResponse response) {
        if (response == null || !response.isChanged()) {
            return false;
        }
        if (!hasEdgeContextVersionChanged(response.getContextVersion())) {
            return false;
        }
        this.metadataCache =
                new MetadataCacheEntry(
                        response.getContextVariableMap(),
                        response.getFieldDataTypeCache(),
                        response.getContextVersion(),
                        response.getEtag());
        return true;
    }

    /**
     * Whether the edge {@code contextVersion} differs from the cached metadata version.
     *
     * @param edgeContextVersion logical context version from the combined response body
     * @return {@code true} when {@code edgeContextVersion} is present and differs from the cache
     */
    private boolean hasEdgeContextVersionChanged(String edgeContextVersion) {
        if (edgeContextVersion == null || edgeContextVersion.isEmpty()) {
            return false;
        }
        MetadataCacheEntry cached = metadataCache;
        return cached == null || !edgeContextVersion.equals(cached.getContextVersion());
    }

    /**
     * Get cached metadata.
     *
     * @return Metadata cache entry or null
     */
    public MetadataCacheEntry getMetadata() {
        return metadataCache;
    }

    /**
     * Get metadata ETag.
     *
     * @return ETag or null
     */
    public String getMetadataEtag() {
        MetadataCacheEntry entry = metadataCache;
        return entry != null ? entry.getEtag() : null;
    }

    /**
     * Check if metadata is cached.
     *
     * @return true if cached
     */
    public boolean hasMetadata() {
        return metadataCache != null;
    }

    /** Clear all cached data. */
    public void clear() {
        featureCache.clear();
        metadataCache = null;
    }

    /**
     * Get the number of cached clients.
     *
     * @return Number of cached clients
     */
    public int size() {
        return featureCache.size();
    }
}
