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

import java.util.Collections;
import java.util.Map;

/** Parsed combined response body. */
public final class EdgeResponse {

    private final int version;
    private final Integer ttl;
    private final String contextVersion;
    private final Map<String, String> contextVariableMap;
    private final Map<String, String> fieldDataTypeCache;
    private final FeaturesResponse[] featureGroups;

    public EdgeResponse(
            int version,
            Integer ttl,
            String contextVersion,
            Map<String, String> contextVariableMap,
            Map<String, String> fieldDataTypeCache,
            FeaturesResponse[] featureGroups) {
        this.version = version;
        this.ttl = ttl;
        this.contextVersion = contextVersion;
        this.contextVariableMap =
                contextVariableMap != null
                        ? Collections.unmodifiableMap(contextVariableMap)
                        : Collections.emptyMap();
        this.fieldDataTypeCache =
                fieldDataTypeCache != null
                        ? Collections.unmodifiableMap(fieldDataTypeCache)
                        : Collections.emptyMap();
        this.featureGroups = featureGroups != null ? featureGroups : new FeaturesResponse[0];
    }

    /**
     * Empty response with no feature groups or context metadata.
     *
     * @return empty {@link EdgeResponse}
     */
    public static EdgeResponse empty() {
        return new EdgeResponse(
                0,
                null,
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                new FeaturesResponse[0]);
    }

    /**
     * @return wire format version; {@code 0} when absent
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return root {@code ttl} value, or {@code null} when absent
     */
    public Integer getTtl() {
        return ttl;
    }

    /**
     * Server-requested poll interval in seconds.
     *
     * @return poll interval when {@code ttl > 0}, otherwise {@code null}
     */
    public Integer getPollInterval() {
        if (ttl != null && ttl > 0) {
            return ttl;
        }
        return null;
    }

    /**
     * @return logical context version string, or {@code null} when absent
     */
    public String getContextVersion() {
        return contextVersion;
    }

    /**
     * @return uppercase context key to original {@code contexts[].id} casing
     */
    public Map<String, String> getContextVariableMap() {
        return contextVariableMap;
    }

    /**
     * @return field name (wire casing) to normalized data type
     */
    public Map<String, String> getFieldDataTypeCache() {
        return fieldDataTypeCache;
    }

    /**
     * @return parsed feature groups; never {@code null}
     */
    public FeaturesResponse[] getFeatureGroups() {
        return featureGroups;
    }

    /**
     * Build a {@link MetadataResponse} for the metadata cache track.
     *
     * @param etag response ETag header value
     * @param isChanged whether the HTTP response carried a new body
     * @return metadata view of this response
     */
    public MetadataResponse toMetadataResponse(String etag, boolean isChanged) {
        return new MetadataResponse(
                contextVariableMap, fieldDataTypeCache, contextVersion, etag, isChanged);
    }
}
