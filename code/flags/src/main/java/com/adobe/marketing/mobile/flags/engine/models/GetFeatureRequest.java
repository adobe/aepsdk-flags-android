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

package com.adobe.marketing.mobile.flags.engine.models;

import com.adobe.marketing.mobile.flags.engine.FlagClient;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request parameters for {@link FlagClient#getFeatures(GetFeatureRequest)} and {@link
 * FlagClient#getFeature(String, GetFeatureRequest)}. Use the builder to set context, identity map,
 * and any future parameters without new API overloads.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Context only
 * GetFeatureRequest request = new GetFeatureRequest.Builder()
 *     .context(Map.of("userId", List.of("user123"), "country", List.of("US")))
 *     .build();
 *
 * // Context and identity map (for A/B bucketing)
 * // Namespace keys must match each feature/feature group params.cohortingNamespaceCode
 * Map<String, Object> identity = Map.of(
 *     "id", "user-bucket-id-123",
 *     "primary", true,
 *     "authenticatedState", "ambiguous");
 * GetFeatureRequest request = new GetFeatureRequest.Builder()
 *     .context(Map.of("country", List.of("US")))
 *     .identityMap(Map.of("ECID", List.of(identity)))
 *     .build();
 *
 * Feature[] features = client.getFeatures(request);
 * Feature feature = client.getFeature("dark-mode", request);
 * }</pre>
 */
public final class GetFeatureRequest {

    /** Shared empty request — no context, no identity map. Safe to reuse (immutable). */
    public static final GetFeatureRequest DEFAULT = new Builder().build();

    private final Map<String, List<String>> context;
    private final Map<String, List<Map<String, Object>>> identityMap;

    private GetFeatureRequest(Builder builder) {
        this.context =
                builder.context == null ? Collections.emptyMap() : copyContext(builder.context);
        this.identityMap =
                builder.identityMap == null
                        ? Collections.emptyMap()
                        : copyIdentityMap(builder.identityMap);
    }

    /**
     * Context map for targeting evaluation (key → list of values). Used for rule/audience
     * evaluation. Never null; empty if not set.
     *
     * @return an unmodifiable context map, never {@code null}
     */
    public Map<String, List<String>> getContext() {
        return context;
    }

    /**
     * Identity map for A/B bucketing (namespace → list of identity entries). Each entry is a map
     * with {@code id}, {@code primary}, and {@code authenticatedState}. The namespace used at
     * evaluation time is resolved from each feature or feature group {@code cohortingType} and
     * {@code cohortingNamespaceCode}; see {@link PolicyEvaluator#resolveCohortingNamespace(Map)}.
     * Never null; empty if not set.
     *
     * @return an unmodifiable identity map, never {@code null}
     */
    public Map<String, List<Map<String, Object>>> getIdentityMap() {
        return identityMap;
    }

    private static Map<String, List<String>> copyContext(Map<String, List<String>> source) {
        final Map<String, List<String>> copy = new HashMap<>(source.size());
        for (final Map.Entry<String, List<String>> entry : source.entrySet()) {
            final List<String> values = entry.getValue();
            copy.put(
                    entry.getKey(),
                    values == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(values)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<Map<String, Object>>> copyIdentityMap(
            Map<String, List<Map<String, Object>>> source) {
        final Map<String, List<Map<String, Object>>> copy = new HashMap<>(source.size());
        for (final Map.Entry<String, List<Map<String, Object>>> nsEntry : source.entrySet()) {
            final List<Map<String, Object>> entries = nsEntry.getValue();
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            final List<Map<String, Object>> copiedEntries = new ArrayList<>(entries.size());
            for (final Map<String, Object> entry : entries) {
                if (entry != null) {
                    copiedEntries.add(Collections.unmodifiableMap(new HashMap<>(entry)));
                }
            }
            if (!copiedEntries.isEmpty()) {
                copy.put(nsEntry.getKey(), Collections.unmodifiableList(copiedEntries));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Builder for {@link GetFeatureRequest}. */
    public static final class Builder {
        private Map<String, List<String>> context;
        private Map<String, List<Map<String, Object>>> identityMap;

        /**
         * Set the context map for targeting evaluation.
         *
         * @param context Key → list of values (e.g. "country" → ["US"], "region" → ["VA7"])
         * @return this builder
         */
        public Builder context(Map<String, List<String>> context) {
            this.context = context;
            return this;
        }

        /**
         * Set the identity map for A/B bucketing.
         *
         * <p>Each namespace maps to a list of identity entries. Each entry must include {@code id}
         * (String), {@code primary} (boolean), and {@code authenticatedState} (String). Which
         * namespace is read for a given feature or feature group is determined by that item's
         * {@code cohortingType} and {@code cohortingNamespaceCode}.
         *
         * @param identityMap namespace → identity entries, or {@code null} to omit
         * @return this builder
         */
        public Builder identityMap(Map<String, List<Map<String, Object>>> identityMap) {
            this.identityMap = identityMap;
            return this;
        }

        /**
         * Build the request.
         *
         * @return immutable {@link GetFeatureRequest}
         */
        public GetFeatureRequest build() {
            return new GetFeatureRequest(this);
        }
    }
}
