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

/** Filter comparator types for rule evaluation. */
enum FilterComparator {
    // Relational
    EQ("EQ", "EQUALS"),
    NE("NE", "NOT_EQUALS", "NEQ"),
    LT("LT", "LESS_THAN"),
    LE("LE", "LESS_THAN_OR_EQUALS", "LTE"),
    GT("GT", "GREATER_THAN"),
    GE("GE", "GREATER_THAN_OR_EQUALS", "GTE"),

    // Set operations
    IN("IN"),
    NOT_IN("NOT_IN"),
    BW("BW", "BETWEEN"),

    // String operations
    CT("CT", "CONTAINS"),
    NOT_CONTAINS("NOT_CONTAINS"),
    SW("SW", "STARTS_WITH"),
    EW("EW", "ENDS_WITH"),
    LK("LK", "LIKE"),
    RE("RE", "REGEX", "MATCHES_REGEX"),

    // Special
    EX("EX", "EXISTS"),
    CEQ("CEQ", "COLLECTION_EQUAL"),
    PATCH("PATCH"),

    // Version
    VERSION_EQ("VERSION_EQ", "VERSION_EQUALS"),
    VERSION_GT("VERSION_GT", "VERSION_GREATER_THAN"),
    VERSION_LT("VERSION_LT", "VERSION_LESS_THAN"),
    VERSION_GE("VERSION_GE", "VERSION_GREATER_THAN_OR_EQUALS", "VERSION_GTE"),
    VERSION_LE("VERSION_LE", "VERSION_LESS_THAN_OR_EQUALS", "VERSION_LTE");

    private final String[] aliases;

    FilterComparator(String... aliases) {
        this.aliases = aliases;
    }

    /**
     * Parse a string to FilterComparator.
     *
     * @param value String representation
     * @return FilterComparator or null if not found
     */
    public static FilterComparator fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String upperValue = value.toUpperCase().trim();

        for (FilterComparator comparator : values()) {
            if (comparator.name().equals(upperValue)) {
                return comparator;
            }
            for (String alias : comparator.aliases) {
                if (alias.equals(upperValue)) {
                    return comparator;
                }
            }
        }

        return null;
    }

    /**
     * Check if this is a relational equality operator.
     *
     * @return true if relational equality
     */
    public boolean isRelationalEquality() {
        return this == LT || this == LE || this == GT || this == GE || this == EQ || this == NE;
    }

    /**
     * Check if this is a negation operator.
     *
     * @return true if negation (i.e., absence of the attribute satisfies the condition)
     */
    public boolean isNegation() {
        return this == NE || this == NOT_IN || this == NOT_CONTAINS;
    }

    /**
     * Check if this is a string operator.
     *
     * @return true if string operator
     */
    public boolean isStringOperator() {
        return this == CT || this == SW || this == EW || this == LK || this == RE;
    }

    /**
     * Check if this is a version operator.
     *
     * @return true if version operator
     */
    public boolean isVersionOperator() {
        return this == VERSION_EQ
                || this == VERSION_GT
                || this == VERSION_LT
                || this == VERSION_GE
                || this == VERSION_LE;
    }
}
