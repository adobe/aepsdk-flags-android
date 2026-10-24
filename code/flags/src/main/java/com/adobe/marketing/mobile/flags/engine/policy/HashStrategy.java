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

/** Hash strategy interface for policy evaluation. */
interface HashStrategy {

    /**
     * Calculate hash value for an identifier with seed.
     *
     * <p>The seed is concatenated to the identifier to form the hash input, NOT used as the hash
     * algorithm seed.
     *
     * @param identifier bucketing identifier
     * @param seed Policy seed string (concatenated to identifier)
     * @return Hash value (0-9999)
     */
    int hash(String identifier, String seed);

    /**
     * Get the multiplier for bucket calculation.
     *
     * @return Multiplier value (typically 100)
     */
    int getMultiplier();
}
