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

/**
 * MurmurHash implementation for consistent user bucketing.
 *
 * <p>The hash algorithm always uses R=31 as the initial seed value.
 */
final class MurmurHashStrategy implements HashStrategy {

    private static final int M = 10000;
    private static final int R = 31;
    private static final int MULTIPLIER = 100;

    /**
     * Calculate hash value for policy bucketing.
     *
     * @param identifier User identifier
     * @param seed Policy seed (concatenated to identifier, NOT used as hash seed)
     * @return Hash value 0-9999
     */
    @Override
    public int hash(String identifier, String seed) {
        if (identifier == null || identifier.isEmpty()) {
            return 0;
        }

        // Build input string: identifier + seed
        String input = seed != null ? identifier + seed : identifier;

        byte[] data = input.getBytes();
        int length = data.length;

        int m = 0x5bd1e995;
        int r = 24;

        // Use constant R=31 as initial hash value
        int h = R ^ length;
        int len_4 = length >> 2;

        for (int i = 0; i < len_4; i++) {
            int i_4 = i << 2;
            int k = data[i_4 + 3];
            k = k << 8;
            k = k | (data[i_4 + 2] & 0xff);
            k = k << 8;
            k = k | (data[i_4 + 1] & 0xff);
            k = k << 8;
            k = k | (data[i_4 + 0] & 0xff);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int len_m = len_4 << 2;
        int left = length - len_m;

        if (left != 0) {
            if (left >= 3) {
                h ^= (int) data[length - 3] << 16;
            }
            if (left >= 2) {
                h ^= (int) data[length - 2] << 8;
            }
            if (left >= 1) {
                h ^= (int) data[length - 1];
            }
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        h = h % M;
        if (h < 0) {
            h = -h;
        }
        return h;
    }

    @Override
    public int getMultiplier() {
        return MULTIPLIER;
    }
}
