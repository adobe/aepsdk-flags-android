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

import java.util.Map;

/**
 * Response containing metadata for rule evaluation. Includes context variables and field data
 * types.
 */
public class MetadataResponse {

    private final Map<String, String> contextVariableMap;
    private final Map<String, String> fieldDataTypeCache;
    private final String contextVersion;
    private final String etag;
    private final boolean isChanged;

    public MetadataResponse(
            Map<String, String> contextVariableMap,
            Map<String, String> fieldDataTypeCache,
            String etag,
            boolean isChanged) {
        this(contextVariableMap, fieldDataTypeCache, null, etag, isChanged);
    }

    public MetadataResponse(
            Map<String, String> contextVariableMap,
            Map<String, String> fieldDataTypeCache,
            String contextVersion,
            String etag,
            boolean isChanged) {
        this.contextVariableMap = contextVariableMap;
        this.fieldDataTypeCache = fieldDataTypeCache;
        this.contextVersion = contextVersion;
        this.etag = etag;
        this.isChanged = isChanged;
    }

    /**
     * Get the context variable mappings.
     *
     * @return Map of context variable names to their types
     */
    public Map<String, String> getContextVariableMap() {
        return contextVariableMap;
    }

    /**
     * Get the field data type cache.
     *
     * @return Map of field names to their data types
     */
    public Map<String, String> getFieldDataTypeCache() {
        return fieldDataTypeCache;
    }

    /**
     * Logical context version from the combined response.
     *
     * @return context version string, or {@code null} when not present
     */
    public String getContextVersion() {
        return contextVersion;
    }

    /**
     * Get the ETag for cache validation.
     *
     * @return ETag string
     */
    public String getEtag() {
        return etag;
    }

    /**
     * Check if the data has changed since the last request.
     *
     * @return true if data changed, false if 304 Not Modified
     */
    public boolean isChanged() {
        return isChanged;
    }
}
