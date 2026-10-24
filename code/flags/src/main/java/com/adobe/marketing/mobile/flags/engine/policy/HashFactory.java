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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for hash strategy implementations. Supports different hash algorithms for policy
 * evaluation.
 */
final class HashFactory {

    /** Hash algorithm types. */
    public enum HashAlgorithmType {
        MURMUR_HASH,
        PREVIEW_SIMPLE_HASH
    }

    private static final Map<HashAlgorithmType, HashStrategy> strategies =
            new ConcurrentHashMap<>();

    static {
        // Register default strategies
        strategies.put(HashAlgorithmType.MURMUR_HASH, new MurmurHashStrategy());
        strategies.put(HashAlgorithmType.PREVIEW_SIMPLE_HASH, new SimpleHashStrategy());
    }

    /**
     * Get hash strategy by type.
     *
     * @param type Hash algorithm type
     * @return HashStrategy implementation
     */
    public static HashStrategy getStrategy(HashAlgorithmType type) {
        HashStrategy strategy = strategies.get(type);
        if (strategy == null) {
            // Default to MurmurHash
            return strategies.get(HashAlgorithmType.MURMUR_HASH);
        }
        return strategy;
    }

    /**
     * Get hash strategy by type name.
     *
     * @param typeName Hash algorithm type name
     * @return HashStrategy implementation
     */
    public static HashStrategy getStrategy(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return strategies.get(HashAlgorithmType.MURMUR_HASH);
        }

        try {
            HashAlgorithmType type = HashAlgorithmType.valueOf(typeName.toUpperCase());
            return getStrategy(type);
        } catch (IllegalArgumentException e) {
            return strategies.get(HashAlgorithmType.MURMUR_HASH);
        }
    }

    /**
     * Register a custom hash strategy.
     *
     * @param type Hash algorithm type
     * @param strategy Strategy implementation
     */
    public static void registerStrategy(HashAlgorithmType type, HashStrategy strategy) {
        strategies.put(type, strategy);
    }

    /** Simple hash strategy for preview mode. */
    private static class SimpleHashStrategy implements HashStrategy {
        private static final int M = 10000;
        private static final int R = 31;
        private static final int MULTIPLIER = 100;

        @Override
        public int hash(String identifier, String seed) {
            if (identifier == null || identifier.isEmpty()) {
                return 0;
            }

            // Build input: identifier + seed
            String input = seed != null ? identifier + seed : identifier;

            int hash = 0;
            for (int i = 0; i < input.length(); i++) {
                hash = (R * hash + input.charAt(i)) % M;
            }

            return hash;
        }

        @Override
        public int getMultiplier() {
            return MULTIPLIER;
        }
    }
}
