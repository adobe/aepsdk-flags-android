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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive operator × data-type coverage for {@link Filter} and {@link FilterTreeGenerator}.
 *
 * <p>Each nested class covers one operator group. Data types tested per operator: STRING, INTEGER,
 * LONG, DECIMAL, BOOLEAN, DATE, DATE_TIME, VERSION.
 */
class FilterOperatorCoverageTest {

    private FilterTreeGenerator generator;

    @BeforeEach
    void setUp() {
        Map<String, String> fieldDataTypes = new HashMap<>();
        fieldDataTypes.put("country", "STRING");
        fieldDataTypes.put("ageInt", "INTEGER");
        fieldDataTypes.put("ageLong", "LONG");
        fieldDataTypes.put("score", "DECIMAL");
        fieldDataTypes.put("active", "BOOLEAN");
        fieldDataTypes.put("startDate", "DATE");
        fieldDataTypes.put("updatedAt", "DATE_TIME");
        fieldDataTypes.put("appVersion", "VERSION");
        generator = new FilterTreeGenerator(fieldDataTypes);
    }

    // --- helpers ---

    private IFilter parse(String criteria) {
        return generator.getFilterTree(criteria);
    }

    private UserAttributes attrs(String key, String value) {
        return new UserAttributes().addAttribute(key, value);
    }

    // =========================================================================
    // LT
    // =========================================================================

    @Nested
    class LessThan {

        @Test
        void integer_lt_matchesLower() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"LT\",\"val\":18}}");
            assertTrue(f.isValid(attrs("ageInt", "17")));
            assertFalse(f.isValid(attrs("ageInt", "18")));
            assertFalse(f.isValid(attrs("ageInt", "19")));
        }

        @Test
        void long_lt_matchesLower() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"LT\",\"val\":1000000}}");
            assertTrue(f.isValid(attrs("ageLong", "999999")));
            assertFalse(f.isValid(attrs("ageLong", "1000000")));
        }

        @Test
        void decimal_lt_matchesLower() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"LT\",\"val\":9.5}}");
            assertTrue(f.isValid(attrs("score", "9.4")));
            assertFalse(f.isValid(attrs("score", "9.5")));
            assertFalse(f.isValid(attrs("score", "10.0")));
        }

        @Test
        void date_lt_matchesEarlier() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"LT\",\"val\":\"2025-06-01\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-05-31")));
            assertFalse(f.isValid(attrs("startDate", "2025-06-01")));
            assertFalse(f.isValid(attrs("startDate", "2025-07-01")));
        }

        @Test
        void dateTime_lt_matchesEarlier() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"updatedAt\",\"operator\":\"LT\",\"val\":\"2025-06-01T12:00:00\"}}");
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T11:59:59")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-06-01T12:00:00")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-06-01T13:00:00")));
        }

        @Test
        void string_lt_lexicographic() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"LT\",\"val\":\"US\"}}");
            assertTrue(f.isValid(attrs("country", "FR")));
            assertFalse(f.isValid(attrs("country", "US")));
            assertFalse(f.isValid(attrs("country", "ZA")));
        }

        @Test
        void missingAttribute_returnsFalse() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"LT\",\"val\":18}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("country", "US")));
        }
    }

    // =========================================================================
    // LE
    // =========================================================================

    @Nested
    class LessThanOrEquals {

        @Test
        void integer_le_includesBoundary() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"LE\",\"val\":18}}");
            assertTrue(f.isValid(attrs("ageInt", "17")));
            assertTrue(f.isValid(attrs("ageInt", "18")));
            assertFalse(f.isValid(attrs("ageInt", "19")));
        }

        @Test
        void long_le_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"LE\",\"val\":1000000}}");
            assertTrue(f.isValid(attrs("ageLong", "999999")));
            assertTrue(f.isValid(attrs("ageLong", "1000000")));
            assertFalse(f.isValid(attrs("ageLong", "1000001")));
        }

        @Test
        void decimal_le_includesBoundary() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"LE\",\"val\":9.5}}");
            assertTrue(f.isValid(attrs("score", "9.4")));
            assertTrue(f.isValid(attrs("score", "9.5")));
            assertFalse(f.isValid(attrs("score", "9.6")));
        }

        @Test
        void date_le_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"LE\",\"val\":\"2025-06-01\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-05-31")));
            assertTrue(f.isValid(attrs("startDate", "2025-06-01")));
            assertFalse(f.isValid(attrs("startDate", "2025-06-02")));
        }

        @Test
        void dateTime_le_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"updatedAt\",\"operator\":\"LE\",\"val\":\"2025-06-01T12:00:00\"}}");
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T11:59:59")));
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T12:00:00")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-06-01T12:00:01")));
        }
    }

    // =========================================================================
    // EQ / NE — numeric and boolean gaps
    // =========================================================================

    @Nested
    class EqualsAndNotEquals {

        @Test
        void integer_eq() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"EQ\",\"val\":25}}");
            assertTrue(f.isValid(attrs("ageInt", "25")));
            assertFalse(f.isValid(attrs("ageInt", "26")));
        }

        @Test
        void integer_ne() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"NE\",\"val\":25}}");
            assertTrue(f.isValid(attrs("ageInt", "26")));
            assertFalse(f.isValid(attrs("ageInt", "25")));
        }

        @Test
        void long_eq() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"EQ\",\"val\":9999999999}}");
            assertTrue(f.isValid(attrs("ageLong", "9999999999")));
            assertFalse(f.isValid(attrs("ageLong", "9999999998")));
        }

        @Test
        void long_ne() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"NE\",\"val\":9999999999}}");
            assertTrue(f.isValid(attrs("ageLong", "9999999998")));
            assertFalse(f.isValid(attrs("ageLong", "9999999999")));
        }

        @Test
        void decimal_eq() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"EQ\",\"val\":7.5}}");
            assertTrue(f.isValid(attrs("score", "7.5")));
            assertFalse(f.isValid(attrs("score", "7.6")));
        }

        @Test
        void decimal_ne() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"NE\",\"val\":7.5}}");
            assertTrue(f.isValid(attrs("score", "7.6")));
            assertFalse(f.isValid(attrs("score", "7.5")));
        }

        @Test
        void boolean_eq_true() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"active\",\"operator\":\"EQ\",\"val\":true}}");
            assertTrue(f.isValid(attrs("active", "true")));
            assertFalse(f.isValid(attrs("active", "false")));
        }

        @Test
        void boolean_ne() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"active\",\"operator\":\"NE\",\"val\":true}}");
            assertTrue(f.isValid(attrs("active", "false")));
            assertFalse(f.isValid(attrs("active", "true")));
        }

        @Test
        void date_eq() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"EQ\",\"val\":\"2025-06-15\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-06-15")));
            assertFalse(f.isValid(attrs("startDate", "2025-06-16")));
        }

        @Test
        void date_ne() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"NE\",\"val\":\"2025-06-15\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-06-16")));
            assertFalse(f.isValid(attrs("startDate", "2025-06-15")));
        }

        @Test
        void ne_missingAttribute_returnsTrue() {
            // NE is a negation operator — missing attribute should return true
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"NE\",\"val\":25}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("country", "US")));
        }
    }

    // =========================================================================
    // GT / GE — date and dateTime gaps
    // =========================================================================

    @Nested
    class GreaterThan {

        @Test
        void date_gt_matchesLater() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"GT\",\"val\":\"2025-06-01\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-06-02")));
            assertFalse(f.isValid(attrs("startDate", "2025-06-01")));
            assertFalse(f.isValid(attrs("startDate", "2025-05-31")));
        }

        @Test
        void date_ge_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"GE\",\"val\":\"2025-06-01\"}}");
            assertTrue(f.isValid(attrs("startDate", "2025-06-01")));
            assertTrue(f.isValid(attrs("startDate", "2025-06-02")));
            assertFalse(f.isValid(attrs("startDate", "2025-05-31")));
        }

        @Test
        void dateTime_gt_matchesLater() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"updatedAt\",\"operator\":\"GT\",\"val\":\"2025-06-01T12:00:00\"}}");
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T12:00:01")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-06-01T12:00:00")));
        }

        @Test
        void dateTime_ge_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"updatedAt\",\"operator\":\"GE\",\"val\":\"2025-06-01T12:00:00\"}}");
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T12:00:00")));
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T12:00:01")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-06-01T11:59:59")));
        }
    }

    // =========================================================================
    // BW (BETWEEN)
    // =========================================================================

    @Nested
    class Between {

        @Test
        void long_bw_inclusive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"BW\",\"val\":[1000,9000]}}");
            assertTrue(f.isValid(attrs("ageLong", "5000")));
            assertTrue(f.isValid(attrs("ageLong", "1000")));
            assertTrue(f.isValid(attrs("ageLong", "9000")));
            assertFalse(f.isValid(attrs("ageLong", "999")));
            assertFalse(f.isValid(attrs("ageLong", "9001")));
        }

        @Test
        void decimal_bw_inclusive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"score\",\"operator\":\"BW\",\"val\":[5.0,9.5]}}");
            assertTrue(f.isValid(attrs("score", "7.0")));
            assertTrue(f.isValid(attrs("score", "5.0")));
            assertTrue(f.isValid(attrs("score", "9.5")));
            assertFalse(f.isValid(attrs("score", "4.9")));
            assertFalse(f.isValid(attrs("score", "9.6")));
        }

        @Test
        void date_bw_inclusive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"startDate\",\"operator\":\"BW\",\"val\":[\"2025-01-01\",\"2025-12-31\"]}}");
            assertTrue(f.isValid(attrs("startDate", "2025-06-15")));
            assertTrue(f.isValid(attrs("startDate", "2025-01-01")));
            assertTrue(f.isValid(attrs("startDate", "2025-12-31")));
            assertFalse(f.isValid(attrs("startDate", "2024-12-31")));
            assertFalse(f.isValid(attrs("startDate", "2026-01-01")));
        }

        @Test
        void dateTime_bw_inclusive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"updatedAt\",\"operator\":\"BW\","
                                + "\"val\":[\"2025-06-01T00:00:00\",\"2025-06-30T23:59:59\"]}}");
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-15T12:00:00")));
            assertTrue(f.isValid(attrs("updatedAt", "2025-06-01T00:00:00")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-05-31T23:59:59")));
            assertFalse(f.isValid(attrs("updatedAt", "2025-07-01T00:00:00")));
        }
    }

    // =========================================================================
    // IN / NOT_IN
    // =========================================================================

    @Nested
    class InAndNotIn {

        @Test
        void string_notIn_matchesAbsent() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_IN\",\"val\":[\"US\",\"CA\"]}}");
            assertTrue(f.isValid(attrs("country", "UK")));
            assertFalse(f.isValid(attrs("country", "US")));
            assertFalse(f.isValid(attrs("country", "CA")));
        }

        @Test
        void integer_in() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"IN\",\"val\":[18,21,25]}}");
            assertTrue(f.isValid(attrs("ageInt", "21")));
            assertFalse(f.isValid(attrs("ageInt", "20")));
        }

        @Test
        void integer_notIn() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"NOT_IN\",\"val\":[18,21,25]}}");
            assertTrue(f.isValid(attrs("ageInt", "20")));
            assertFalse(f.isValid(attrs("ageInt", "21")));
        }

        @Test
        void boolean_in() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"active\",\"operator\":\"IN\",\"val\":[true]}}");
            assertTrue(f.isValid(attrs("active", "true")));
            assertFalse(f.isValid(attrs("active", "false")));
        }

        @Test
        void notIn_missingAttribute_returnsTrue() {
            // NOT_IN is a negation operator — missing attribute should return true
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_IN\",\"val\":[\"US\",\"CA\"]}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }
    }

    // =========================================================================
    // String operators: NOT_CONTAINS, EW, LK, RE
    // =========================================================================

    @Nested
    class StringOperators {

        @Test
        void notContains_matchesAbsent() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_CONTAINS\",\"val\":\"US\"}}");
            assertTrue(f.isValid(attrs("country", "France")));
            assertFalse(f.isValid(attrs("country", "US_WEST")));
        }

        @Test
        void notContains_missingAttribute_returnsTrue() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_CONTAINS\",\"val\":\"US\"}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void endsWith_matchesSuffix() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EW\",\"val\":\".com\"}}");
            assertTrue(f.isValid(attrs("country", "adobe.com")));
            assertFalse(f.isValid(attrs("country", "adobe.org")));
        }

        @Test
        void endsWith_caseInsensitive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EW\",\"val\":\".COM\"}}");
            assertTrue(f.isValid(attrs("country", "adobe.com")));
        }

        @Test
        void like_percentWildcard() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"LK\",\"val\":\"adobe%\"}}");
            assertTrue(f.isValid(attrs("country", "adobe_express")));
            assertTrue(f.isValid(attrs("country", "adobe")));
            assertFalse(f.isValid(attrs("country", "my_adobe")));
        }

        @Test
        void like_underscoreWildcard() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"LK\",\"val\":\"US_\"}}");
            assertTrue(f.isValid(attrs("country", "USA")));
            assertFalse(f.isValid(attrs("country", "US")));
            assertFalse(f.isValid(attrs("country", "USAA")));
        }

        @Test
        void like_caseInsensitive() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"LK\",\"val\":\"ADOBE%\"}}");
            assertTrue(f.isValid(attrs("country", "adobe_express")));
        }

        @Test
        void regex_matchesPattern() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"RE\",\"val\":\"[A-Z]{2}\"}}");
            assertTrue(f.isValid(attrs("country", "US")));
            assertTrue(f.isValid(attrs("country", "CA")));
            assertFalse(f.isValid(attrs("country", "USA")));
            assertFalse(f.isValid(attrs("country", "us")));
        }

        @Test
        void regex_complexPattern() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"RE\",\"val\":\"^\\\\d{3}-\\\\d{4}$\"}}");
            assertTrue(f.isValid(attrs("country", "123-4567")));
            assertFalse(f.isValid(attrs("country", "12-4567")));
        }

        @Test
        void regex_invalidPattern_returnsFalse() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"RE\",\"val\":\"[invalid\"}}");
            assertFalse(f.isValid(attrs("country", "US")));
        }
    }

    // =========================================================================
    // CEQ (COLLECTION_EQUAL)
    // =========================================================================

    @Nested
    class CollectionEqual {

        @Test
        void ceq_exactSetMatch() {
            Filter filter =
                    new Filter(
                            "tags",
                            new Object[] {"a", "b", "c"},
                            FilterComparator.CEQ,
                            FieldDataType.STRING);
            UserAttributes user =
                    new UserAttributes().addAttribute("tags", java.util.List.of("a", "b", "c"));
            assertTrue(filter.isValid(user));
        }

        @Test
        void ceq_differentSize_returnsFalse() {
            Filter filter =
                    new Filter(
                            "tags",
                            new Object[] {"a", "b"},
                            FilterComparator.CEQ,
                            FieldDataType.STRING);
            UserAttributes user =
                    new UserAttributes().addAttribute("tags", java.util.List.of("a", "b", "c"));
            assertFalse(filter.isValid(user));
        }

        @Test
        void ceq_differentValues_returnsFalse() {
            Filter filter =
                    new Filter(
                            "tags",
                            new Object[] {"a", "b", "d"},
                            FilterComparator.CEQ,
                            FieldDataType.STRING);
            UserAttributes user =
                    new UserAttributes().addAttribute("tags", java.util.List.of("a", "b", "c"));
            assertFalse(filter.isValid(user));
        }

        @Test
        void ceq_orderIndependent() {
            Filter filter =
                    new Filter(
                            "tags",
                            new Object[] {"c", "a", "b"},
                            FilterComparator.CEQ,
                            FieldDataType.STRING);
            UserAttributes user =
                    new UserAttributes().addAttribute("tags", java.util.List.of("a", "b", "c"));
            assertTrue(filter.isValid(user));
        }

        @Test
        void ceq_caseInsensitive() {
            Filter filter =
                    new Filter(
                            "tags",
                            new Object[] {"US", "CA"},
                            FilterComparator.CEQ,
                            FieldDataType.STRING);
            UserAttributes user =
                    new UserAttributes().addAttribute("tags", java.util.List.of("us", "ca"));
            assertTrue(filter.isValid(user));
        }
    }

    // =========================================================================
    // VERSION operators (VERSION_EQ, VERSION_LT, VERSION_GE, VERSION_LE)
    // =========================================================================

    @Nested
    class VersionOperators {

        @Test
        void versionEq_exact() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_EQ\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.0.0")));
            assertFalse(f.isValid(attrs("appVersion", "2.0.1")));
            assertFalse(f.isValid(attrs("appVersion", "1.9.9")));
        }

        @Test
        void versionEq_paddedSegments() {
            // "2.0" should equal "2.0.0" (missing segments treated as 0)
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_EQ\",\"val\":\"2.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.0.0")));
        }

        @Test
        void versionLt_matchesLower() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_LT\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "1.9.9")));
            assertTrue(f.isValid(attrs("appVersion", "1.0.0")));
            assertFalse(f.isValid(attrs("appVersion", "2.0.0")));
            assertFalse(f.isValid(attrs("appVersion", "2.0.1")));
        }

        @Test
        void versionGe_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_GE\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.0.0")));
            assertTrue(f.isValid(attrs("appVersion", "2.0.1")));
            assertTrue(f.isValid(attrs("appVersion", "3.0.0")));
            assertFalse(f.isValid(attrs("appVersion", "1.9.9")));
        }

        @Test
        void versionLe_includesBoundary() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_LE\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.0.0")));
            assertTrue(f.isValid(attrs("appVersion", "1.9.9")));
            assertFalse(f.isValid(attrs("appVersion", "2.0.1")));
        }

        @Test
        void versionGt_alreadyCoveredByExistingTest_confirmedHere() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_GT\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.0.1")));
            assertFalse(f.isValid(attrs("appVersion", "2.0.0")));
        }

        @Test
        void version_numericSuffixStripped() {
            // "2.1-SNAPSHOT" should parse the numeric part "2.1" → 2.1.0
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"appVersion\",\"operator\":\"VERSION_GT\",\"val\":\"2.0.0\"}}");
            assertTrue(f.isValid(attrs("appVersion", "2.1-SNAPSHOT")));
        }
    }

    // =========================================================================
    // EX (EXISTS)
    // =========================================================================

    @Nested
    class ExistsOperator {

        @Test
        void ex_trueExpectsPresence_present() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"country\",\"operator\":\"EX\",\"val\":true}}");
            assertTrue(f.isValid(attrs("country", "US")));
        }

        @Test
        void ex_trueExpectsPresence_absent() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"country\",\"operator\":\"EX\",\"val\":true}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void ex_falseExpectsAbsence_absent() {
            // val:false means "attribute must NOT exist"
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EX\",\"val\":false}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void ex_falseExpectsAbsence_present() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EX\",\"val\":false}}");
            assertFalse(f.isValid(attrs("country", "US")));
        }
    }

    // =========================================================================
    // Missing-attribute semantics (isNegation contract)
    // =========================================================================

    @Nested
    class MissingAttributeSemantics {

        @Test
        void eq_missingAttribute_returnsFalse() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"US\"}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void gt_missingAttribute_returnsFalse() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageInt\",\"operator\":\"GT\",\"val\":18}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("country", "US")));
        }

        @Test
        void in_missingAttribute_returnsFalse() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"IN\",\"val\":[\"US\",\"CA\"]}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void ne_missingAttribute_returnsTrue() {
            // NE is a negation operator: missing attr satisfies the condition
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NE\",\"val\":\"US\"}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void notIn_missingAttribute_returnsTrue() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_IN\",\"val\":[\"US\",\"CA\"]}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void notContains_missingAttribute_returnsTrue() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"NOT_CONTAINS\",\"val\":\"US\"}}");
            assertTrue(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void ct_missingAttribute_returnsFalse() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"CT\",\"val\":\"US\"}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }

        @Test
        void sw_missingAttribute_returnsFalse() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"country\",\"operator\":\"SW\",\"val\":\"US\"}}");
            assertFalse(f.isValid(new UserAttributes().addAttribute("age", "25")));
        }
    }

    // =========================================================================
    // LONG and DECIMAL — comprehensive numeric coverage
    // =========================================================================

    @Nested
    class LongAndDecimal {

        @Test
        void long_gt() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"GT\",\"val\":100}}");
            assertTrue(f.isValid(attrs("ageLong", "101")));
            assertFalse(f.isValid(attrs("ageLong", "100")));
        }

        @Test
        void long_ge() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"GE\",\"val\":100}}");
            assertTrue(f.isValid(attrs("ageLong", "100")));
            assertFalse(f.isValid(attrs("ageLong", "99")));
        }

        @Test
        void long_in() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"ageLong\",\"operator\":\"IN\",\"val\":[10,20,30]}}");
            assertTrue(f.isValid(attrs("ageLong", "20")));
            assertFalse(f.isValid(attrs("ageLong", "25")));
        }

        @Test
        void decimal_gt() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"GT\",\"val\":5.5}}");
            assertTrue(f.isValid(attrs("score", "5.6")));
            assertFalse(f.isValid(attrs("score", "5.5")));
        }

        @Test
        void decimal_ge() {
            IFilter f =
                    parse("{\"criteria\":{\"attr\":\"score\",\"operator\":\"GE\",\"val\":5.5}}");
            assertTrue(f.isValid(attrs("score", "5.5")));
            assertFalse(f.isValid(attrs("score", "5.4")));
        }

        @Test
        void decimal_in() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"score\",\"operator\":\"IN\",\"val\":[1.5,2.5,3.5]}}");
            assertTrue(f.isValid(attrs("score", "2.5")));
            assertFalse(f.isValid(attrs("score", "2.6")));
        }

        @Test
        void decimal_notIn() {
            IFilter f =
                    parse(
                            "{\"criteria\":{\"attr\":\"score\",\"operator\":\"NOT_IN\",\"val\":[1.5,2.5]}}");
            assertTrue(f.isValid(attrs("score", "3.0")));
            assertFalse(f.isValid(attrs("score", "1.5")));
        }
    }
}
