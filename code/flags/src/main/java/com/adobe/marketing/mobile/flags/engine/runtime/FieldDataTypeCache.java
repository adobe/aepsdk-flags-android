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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for field data type mappings.
 *
 * <p>Entries are only added or updated via the {@code set*} methods for the lifetime of this
 * instance. This type does not support clearing after initialization; use a new instance if a full
 * reset is required.
 */
class FieldDataTypeCache {

    private final ConcurrentHashMap<String, FieldDataType> fieldDataTypeCache;
    private final ConcurrentHashMap<String, FieldDataType> aepMetadataCache;
    private final ConcurrentHashMap<String, Map<String, String>> fieldMetadataCache;
    private final AtomicLong lastModifiedTime;

    /** Create a new field data type cache. */
    public FieldDataTypeCache() {
        this.fieldDataTypeCache = new ConcurrentHashMap<>();
        this.aepMetadataCache = new ConcurrentHashMap<>();
        this.fieldMetadataCache = new ConcurrentHashMap<>();
        this.lastModifiedTime = new AtomicLong(0L);
    }

    /**
     * Set a field's data type.
     *
     * @param fieldName Field name
     * @param dataType Field data type
     * @return this instance for chaining
     */
    public FieldDataTypeCache setFieldDataType(String fieldName, FieldDataType dataType) {
        if (fieldName != null && dataType != null) {
            fieldDataTypeCache.put(fieldName, dataType);
            lastModifiedTime.set(System.currentTimeMillis());
        }
        return this;
    }

    /**
     * Get a field's data type.
     *
     * @param fieldName Field name
     * @return Field data type or null if not found
     */
    public FieldDataType getFieldDataType(String fieldName) {
        return fieldDataTypeCache.get(fieldName);
    }

    /**
     * Set AEP metadata for a field.
     *
     * @param fieldName Field name
     * @param dataType Field data type
     * @return this instance for chaining
     */
    public FieldDataTypeCache setAepMetadata(String fieldName, FieldDataType dataType) {
        if (fieldName != null && dataType != null) {
            aepMetadataCache.put(fieldName, dataType);
            lastModifiedTime.set(System.currentTimeMillis());
        }
        return this;
    }

    /**
     * Get AEP metadata for a field.
     *
     * @param fieldName Field name
     * @return Field data type from AEP metadata or null if not found
     */
    public FieldDataType getAepMetadataCache(String fieldName) {
        return aepMetadataCache.get(fieldName);
    }

    /**
     * Check if a field has AEP metadata.
     *
     * @param fieldName Field name
     * @return true if AEP metadata exists
     */
    public boolean hasAepMetadata(String fieldName) {
        return aepMetadataCache.containsKey(fieldName);
    }

    /**
     * Set field metadata
     *
     * @param fieldName Field name
     * @param metadata Metadata map
     * @return this instance for chaining
     */
    public FieldDataTypeCache setFieldMetaData(String fieldName, Map<String, String> metadata) {
        if (fieldName != null && metadata != null) {
            fieldMetadataCache.put(fieldName, metadata);
        }
        return this;
    }

    /**
     * Get field metadata.
     *
     * @param fieldName Field name
     * @return Metadata map or null if not found
     */
    public Map<String, String> getFieldMetaData(String fieldName) {
        return fieldMetadataCache.get(fieldName);
    }

    /**
     * Get the last modified time.
     *
     * @return Last modified time in milliseconds
     */
    public AtomicLong getLastModifiedTime() {
        return lastModifiedTime;
    }

    /**
     * Get the number of field data types cached.
     *
     * @return Cache size
     */
    public int size() {
        return fieldDataTypeCache.size();
    }

    /**
     * Get the number of AEP metadata entries.
     *
     * @return AEP metadata cache size
     */
    public int aepMetadataSize() {
        return aepMetadataCache.size();
    }

    /**
     * Convert to a simple map (for compatibility with existing code).
     *
     * @return Map of field name to data type string
     */
    public Map<String, String> toMap() {
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        for (Map.Entry<String, FieldDataType> entry : fieldDataTypeCache.entrySet()) {
            map.put(entry.getKey(), entry.getValue().name());
        }
        return map;
    }
}
