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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for policy configurations. Stores policy details fetched from the server for local
 * evaluation.
 */
public class PolicyCache {

    private final Map<Integer, PolicyDetail> policyDetailCache;

    /** Policy detail containing bucket configuration for A/B assignment. */
    public static class PolicyDetail {
        private final Integer id;
        private final String hashAlgorithmType;
        private final String seed;
        private final List<PolicyBucket> buckets;
        private final Map<String, String> previewUserVariantMap;
        private final long cachedAt;

        public PolicyDetail(
                Integer id,
                String hashAlgorithmType,
                String seed,
                List<PolicyBucket> buckets,
                Map<String, String> previewUserVariantMap) {
            this.id = id;
            this.hashAlgorithmType = hashAlgorithmType;
            this.seed = seed;
            this.buckets = buckets;
            this.previewUserVariantMap = previewUserVariantMap;
            this.cachedAt = System.currentTimeMillis();
        }

        public Integer getId() {
            return id;
        }

        public String getHashAlgorithmType() {
            return hashAlgorithmType;
        }

        public String getSeed() {
            return seed;
        }

        public List<PolicyBucket> getBuckets() {
            return buckets;
        }

        public Map<String, String> getPreviewUserVariantMap() {
            return previewUserVariantMap;
        }

        public long getCachedAt() {
            return cachedAt;
        }
    }

    /** Policy bucket with range. */
    public static class PolicyBucket {
        private final String variantId;
        private final int startRange;
        private final int endRange;
        private final int percentage;

        public PolicyBucket(String variantId, int startRange, int endRange, int percentage) {
            this.variantId = variantId;
            this.startRange = startRange;
            this.endRange = endRange;
            this.percentage = percentage;
        }

        public String getVariantId() {
            return variantId;
        }

        public int getStartRange() {
            return startRange;
        }

        public int getEndRange() {
            return endRange;
        }

        public int getPercentage() {
            return percentage;
        }

        /**
         * Check if a value falls within this bucket's range (inclusive).
         *
         * @param value hash value to check
         * @return true if value is within range
         */
        public boolean contains(int value) {
            return value >= startRange && value <= endRange;
        }
    }

    public PolicyCache() {
        this.policyDetailCache = new ConcurrentHashMap<>();
    }

    /**
     * Get policy by ID.
     *
     * @param policyId Policy ID
     * @return PolicyDetail or null if not found
     */
    public PolicyDetail getPolicy(Integer policyId) {
        return policyDetailCache.get(policyId);
    }

    /**
     * Put policy in cache.
     *
     * @param policyId Policy ID
     * @param detail Policy detail
     */
    public void putPolicy(Integer policyId, PolicyDetail detail) {
        if (policyId != null && detail != null) {
            policyDetailCache.put(policyId, detail);
        }
    }

    /**
     * Remove policy from cache.
     *
     * @param policyId Policy ID
     */
    public void removePolicy(Integer policyId) {
        policyDetailCache.remove(policyId);
    }

    /**
     * Check if policy is cached.
     *
     * @param policyId Policy ID
     * @return true if cached
     */
    public boolean contains(Integer policyId) {
        return policyDetailCache.containsKey(policyId);
    }

    /**
     * Get all cached policies.
     *
     * @return Map of policy ID to detail
     */
    public Map<Integer, PolicyDetail> getAllPolicies() {
        return new ConcurrentHashMap<>(policyDetailCache);
    }

    /** Clear all cached policies. */
    public void clear() {
        policyDetailCache.clear();
    }

    /**
     * Get cache size.
     *
     * @return Number of cached policies
     */
    public int size() {
        return policyDetailCache.size();
    }
}
