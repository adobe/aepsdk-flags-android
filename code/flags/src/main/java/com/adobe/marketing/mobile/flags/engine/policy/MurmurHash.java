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
 * MurmurHash implementation for consistent user bucketing. This is used for A/B testing to ensure
 * users get consistent experiences.
 *
 * <p>This implementation matches the server-side MurmurHash algorithm to ensure consistent
 * bucketing across client and server.
 */
public final class MurmurHash {

    private static final int M = 10000;
    private static final int SEED = 31;

    private MurmurHash() {
        // Utility class
    }

    /**
     * Calculate MurmurHash for a string value. Returns a value between 0 and 9999.
     *
     * @param value String to hash
     * @return Hash value (0-9999)
     */
    public static int hash(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }

        byte[] data = value.getBytes();
        int length = data.length;

        int m = 0x5bd1e995;
        int r = 24;

        int h = SEED ^ length;
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

    /**
     * Get the multiplier used for bucket calculation.
     *
     * @return Multiplier (100)
     */
    public static int getMultiplier() {
        return 100;
    }
}
