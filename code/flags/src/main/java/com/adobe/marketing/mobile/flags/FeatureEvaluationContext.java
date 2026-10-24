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

package com.adobe.marketing.mobile.flags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable evaluation context for feature flag APIs.
 *
 * <p>Carries the optional targeting attribute map used for rule evaluation. Build one instance and
 * reuse it across multiple API calls that share the same context.
 *
 * <pre>{@code
 * Map<String, List<String>> attrs = new HashMap<>();
 * attrs.put("region", Arrays.asList("US"));
 *
 * FeatureEvaluationContext ctx = FeatureEvaluationContext.builder()
 *         .withAttributes(attrs)
 *         .build();
 * }</pre>
 */
public final class FeatureEvaluationContext {

    @Nullable private final Map<String, List<String>> attributes;

    private FeatureEvaluationContext(@Nullable final Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    /**
     * Returns a new builder.
     *
     * @return builder instance
     */
    @NonNull public static Builder builder() {
        return new Builder();
    }

    /**
     * Targeting attributes for rule evaluation, or {@code null} when not set.
     *
     * @return unmodifiable map, or {@code null}
     */
    @Nullable public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    @Nullable private static Map<String, List<String>> freeze(
            @Nullable final Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        final Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            final List<String> values = entry.getValue();
            out.put(
                    entry.getKey(),
                    values == null ? null : Collections.unmodifiableList(new ArrayList<>(values)));
        }
        return Collections.unmodifiableMap(out);
    }

    /** Builder for {@link FeatureEvaluationContext}. */
    public static final class Builder {

        @Nullable private Map<String, List<String>> attributes;

        private Builder() {}

        /**
         * Sets the targeting attributes map. May be called at most once; calling it a second time
         * is a programming error and throws {@link IllegalStateException}.
         *
         * @param attributes targeting context map (key to list of values); {@code null} is treated
         *     as empty
         * @return this builder
         * @throws IllegalStateException if attributes have already been set on this builder
         */
        public Builder withAttributes(@Nullable final Map<String, List<String>> attributes) {
            if (this.attributes != null) {
                throw new IllegalStateException(
                        "Attributes already set on this builder. Mixing is not allowed.");
            }
            this.attributes = attributes;
            return this;
        }

        /**
         * Builds an immutable {@link FeatureEvaluationContext}. The attributes map is defensively
         * copied and made unmodifiable.
         *
         * @return new context instance
         */
        public FeatureEvaluationContext build() {
            return new FeatureEvaluationContext(freeze(attributes));
        }
    }
}
