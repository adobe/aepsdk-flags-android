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

/** Field data types for rule evaluation. Used to properly parse and compare values in criteria. */
public enum FieldDataType {
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATE_TIME,
    VERSION,
    LIST_STRING,
    LIST_INTEGER,
    LIST_LONG,
    LIST_DECIMAL,
    LIST_VERSION,
    SET_STRING,
    SET_INTEGER,
    MAP_STRING_STRING,
    FILTER;

    /**
     * Parse a string to FieldDataType.
     *
     * @param value String representation
     * @return FieldDataType or STRING if not found
     */
    public static FieldDataType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return STRING;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STRING;
        }
    }

    /**
     * Check if this is a list type.
     *
     * @return true if list type
     */
    public boolean isList() {
        return this == LIST_STRING
                || this == LIST_INTEGER
                || this == LIST_LONG
                || this == LIST_DECIMAL
                || this == LIST_VERSION;
    }

    /**
     * Check if this is a numeric type.
     *
     * @return true if numeric
     */
    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DECIMAL;
    }

    /**
     * Check if this is a date type.
     *
     * @return true if date type
     */
    public boolean isDate() {
        return this == DATE || this == DATE_TIME;
    }
}
