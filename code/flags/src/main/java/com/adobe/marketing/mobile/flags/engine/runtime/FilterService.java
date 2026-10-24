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

package com.adobe.marketing.mobile.flags.engine.runtime;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service for validating user attributes against filter criteria. */
public class FilterService {

    private static final Logger logger = LoggerFactory.getLogger(FilterService.class);

    private final FilterTreeGenerator filterTreeGenerator;
    private final CriteriaFilterCache criteriaFilterCache = new CriteriaFilterCache();

    /**
     * Create a filter service with a field data type cache.
     *
     * @param fieldDataTypeCache Map of field names to data types
     */
    public FilterService(Map<String, String> fieldDataTypeCache) {
        this.filterTreeGenerator = new FilterTreeGenerator(fieldDataTypeCache);
    }

    /**
     * Check if user attributes are valid against a filter.
     *
     * @param userAttributes User attributes to validate
     * @param filter Filter to validate against
     * @return true if filter matches
     */
    boolean isValid(UserAttributes userAttributes, IFilter filter) {
        if (filter == null) {
            return true;
        }
        return filter.isValid(userAttributes);
    }

    /**
     * Generate a filter from a map representation.
     *
     * @param filterMap Map representation of a filter
     * @return Generated IFilter
     */
    @SuppressWarnings("unchecked")
    IFilter getIFilterFromMap(Object filterMap) {
        if (filterMap == null) {
            return new EmptyFilter();
        }

        try {
            if (filterMap instanceof Map) {
                // Convert map to JSON and parse
                String json = mapToJson((Map<String, Object>) filterMap);
                return filterTreeGenerator.getFilterTree(json, false);
            }

            if (filterMap instanceof String) {
                return filterTreeGenerator.getFilterTree((String) filterMap, false);
            }

            if (filterMap instanceof IFilter) {
                return (IFilter) filterMap;
            }

            return new EmptyFilter();
        } catch (Exception e) {
            logger.error("Error generating filter from map: {}", e.getMessage(), e);
            return new EmptyFilter();
        }
    }

    /**
     * Check if user attributes satisfy a criteria JSON string (parses on every call).
     *
     * <p>Uses the same strict compilation as {@link #evaluateCriteriaCached}: top-level parse
     * failures yield a non-matching filter (fail-closed). Errors are logged by {@link
     * FilterTreeGenerator}.
     *
     * @param criteriaJson criteria JSON to compile and evaluate; null or empty means no criteria
     *     (vacuous match)
     * @param userAttributes user attributes to validate against the criteria
     * @return {@code true} if there is no criteria or the criteria matches; {@code false} if
     *     criteria is present but invalid JSON, unparsable, or does not match
     */
    public boolean matchesCriteria(String criteriaJson, UserAttributes userAttributes) {
        if (criteriaJson == null || criteriaJson.isEmpty()) {
            return true;
        }
        return generateFilterTreeForEvaluation(criteriaJson).isValid(userAttributes);
    }

    /**
     * Parses criteria once per {@code logicalId} and {@code criteriaVersion}, then evaluates only
     * the tree. {@code criteriaVersion} is normally the server criteria hash; when null or empty,
     * the full criteria string is used as the version key (same logical id with new criteria still
     * invalidates the slot).
     *
     * @param logicalId stable key for this criteria instance (e.g. feature group or feature id)
     * @param criteriaJson criteria JSON
     * @param criteriaVersion hash from payload, or null to use {@code criteriaJson} as the version
     */
    public boolean evaluateCriteriaCached(
            UserAttributes userAttributes,
            String logicalId,
            String criteriaJson,
            String criteriaVersion) {
        if (criteriaJson == null || criteriaJson.isEmpty()) {
            return true;
        }
        if (logicalId == null || logicalId.trim().isEmpty()) {
            return generateFilterTreeForEvaluation(criteriaJson).isValid(userAttributes);
        }
        IFilter filter =
                criteriaFilterCache.getOrCreate(logicalId, criteriaJson, criteriaVersion, this);
        return isValid(userAttributes, filter);
    }

    /** Build a filter tree for the evaluation path (fail-closed on top-level parse errors). */
    IFilter generateFilterTreeForEvaluation(String criteriaJson) {
        return filterTreeGenerator.getFilterTree(criteriaJson, true, true);
    }

    /** Convert a map to JSON string (simple implementation). */
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }

        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        }
        if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            Object[] arr = (Object[]) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(valueToJson(arr[i]));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class CriteriaFilterCache {
        private final ConcurrentHashMap<String, CachedSlot> map = new ConcurrentHashMap<>();

        /**
         * Lock-free read on hit; on miss, the filter tree is built and stored with a plain put.
         * Concurrent builds for the same key are possible but harmless (equivalent result, last
         * write wins).
         */
        IFilter getOrCreate(
                String logicalId,
                String criteriaJson,
                String criteriaVersion,
                FilterService owner) {
            String versionKey =
                    (criteriaVersion != null && !criteriaVersion.isEmpty())
                            ? criteriaVersion
                            : criteriaJson;
            CachedSlot slot = map.get(logicalId);
            if (slot != null && slot.versionKey.equals(versionKey)) {
                return slot.filter;
            }
            // Cache miss or stale version: build and store. Under a race two threads may both
            // build an equivalent filter for the same criteria; the last write wins, which is
            // harmless for this cache.
            IFilter filter = owner.generateFilterTreeForEvaluation(criteriaJson);
            CachedSlot newSlot = new CachedSlot(versionKey, filter);
            map.put(logicalId, newSlot);
            return newSlot.filter;
        }

        private static final class CachedSlot {
            final String versionKey;
            final IFilter filter;

            CachedSlot(String versionKey, IFilter filter) {
                this.versionKey = versionKey;
                this.filter = filter;
            }
        }
    }
}
