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

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for FilterTreeGenerator and local evaluation. */
class FilterTreeGeneratorTest {

    private FilterTreeGenerator generator;
    private Map<String, String> fieldDataTypes;

    @BeforeEach
    void setUp() {
        fieldDataTypes = new HashMap<>();
        fieldDataTypes.put("country", "STRING");
        fieldDataTypes.put("age", "INTEGER");
        fieldDataTypes.put("appVersion", "VERSION");
        fieldDataTypes.put("premium", "BOOLEAN");
        fieldDataTypes.put("score", "DECIMAL");

        generator = new FilterTreeGenerator(fieldDataTypes);
    }

    @Test
    @DisplayName("EQUALS operator should match exact value")
    void testEqualsOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes matchingUser = new UserAttributes().addAttribute("country", "US");
        UserAttributes nonMatchingUser = new UserAttributes().addAttribute("country", "UK");

        assertTrue(filter.isValid(matchingUser));
        assertFalse(filter.isValid(nonMatchingUser));
    }

    @Test
    @DisplayName("NOT_EQUALS operator should match non-equal values")
    void testNotEqualsOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"country\", \"operator\": \"NE\", \"val\": \"US\"}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes matchingUser = new UserAttributes().addAttribute("country", "UK");
        UserAttributes nonMatchingUser = new UserAttributes().addAttribute("country", "US");

        assertTrue(filter.isValid(matchingUser));
        assertFalse(filter.isValid(nonMatchingUser));
    }

    @Test
    @DisplayName("GREATER_THAN operator should compare numbers")
    void testGreaterThanOperator() {
        String criteria = "{\"criteria\": {\"attr\": \"age\", \"operator\": \"GT\", \"val\": 18}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes adult = new UserAttributes().addAttribute("age", "25");
        UserAttributes minor = new UserAttributes().addAttribute("age", "16");
        UserAttributes exactly18 = new UserAttributes().addAttribute("age", "18");

        assertTrue(filter.isValid(adult));
        assertFalse(filter.isValid(minor));
        assertFalse(filter.isValid(exactly18));
    }

    @Test
    @DisplayName("GREATER_THAN_OR_EQUALS operator should include boundary")
    void testGreaterThanOrEqualsOperator() {
        String criteria = "{\"criteria\": {\"attr\": \"age\", \"operator\": \"GE\", \"val\": 18}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes exactly18 = new UserAttributes().addAttribute("age", "18");
        UserAttributes adult = new UserAttributes().addAttribute("age", "25");

        assertTrue(filter.isValid(exactly18));
        assertTrue(filter.isValid(adult));
    }

    @Test
    @DisplayName("IN operator should match any value in list")
    void testInOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"country\", \"operator\": \"IN\", \"val\": [\"US\","
                        + " \"CA\", \"UK\"]}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes usUser = new UserAttributes().addAttribute("country", "US");
        UserAttributes ukUser = new UserAttributes().addAttribute("country", "UK");
        UserAttributes frUser = new UserAttributes().addAttribute("country", "FR");

        assertTrue(filter.isValid(usUser));
        assertTrue(filter.isValid(ukUser));
        assertFalse(filter.isValid(frUser));
    }

    @Test
    @DisplayName("BETWEEN operator should match range inclusive")
    void testBetweenOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"age\", \"operator\": \"BW\", \"val\": [18, 65]}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes inRange = new UserAttributes().addAttribute("age", "30");
        UserAttributes atLower = new UserAttributes().addAttribute("age", "18");
        UserAttributes atUpper = new UserAttributes().addAttribute("age", "65");
        UserAttributes belowRange = new UserAttributes().addAttribute("age", "17");
        UserAttributes aboveRange = new UserAttributes().addAttribute("age", "66");

        assertTrue(filter.isValid(inRange));
        assertTrue(filter.isValid(atLower));
        assertTrue(filter.isValid(atUpper));
        assertFalse(filter.isValid(belowRange));
        assertFalse(filter.isValid(aboveRange));
    }

    @Test
    @DisplayName("CONTAINS operator should match substring")
    void testContainsOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"email\", \"operator\": \"CT\", \"val\":"
                        + " \"@adobe.com\"}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes adobeUser = new UserAttributes().addAttribute("email", "user@adobe.com");
        UserAttributes gmailUser = new UserAttributes().addAttribute("email", "user@gmail.com");

        assertTrue(filter.isValid(adobeUser));
        assertFalse(filter.isValid(gmailUser));
    }

    @Test
    @DisplayName("STARTS_WITH operator should match prefix")
    void testStartsWithOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"userId\", \"operator\": \"SW\", \"val\":"
                        + " \"PREMIUM_\"}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes premiumUser = new UserAttributes().addAttribute("userId", "PREMIUM_12345");
        UserAttributes regularUser = new UserAttributes().addAttribute("userId", "USER_12345");

        assertTrue(filter.isValid(premiumUser));
        assertFalse(filter.isValid(regularUser));
    }

    @Test
    @DisplayName("EXISTS operator should check field presence")
    void testExistsOperator() {
        String criteria =
                "{\"criteria\": {\"attr\": \"email\", \"operator\": \"EX\", \"val\": true}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes withEmail = new UserAttributes().addAttribute("email", "user@test.com");
        UserAttributes withoutEmail = new UserAttributes().addAttribute("name", "John");

        assertTrue(filter.isValid(withEmail));
        assertFalse(filter.isValid(withoutEmail));
    }

    @Test
    @DisplayName("AND operator should require all conditions")
    void testAndOperator() {
        String criteria =
                "{\"criteria\": {\"and\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"attr\": \"age\", \"operator\": \"GT\", \"val\": 18}"
                        + "]}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes usAdult =
                new UserAttributes().addAttribute("country", "US").addAttribute("age", "25");
        UserAttributes usMinor =
                new UserAttributes().addAttribute("country", "US").addAttribute("age", "16");
        UserAttributes ukAdult =
                new UserAttributes().addAttribute("country", "UK").addAttribute("age", "25");

        assertTrue(filter.isValid(usAdult));
        assertFalse(filter.isValid(usMinor));
        assertFalse(filter.isValid(ukAdult));
    }

    @Test
    @DisplayName("OR operator should require any condition")
    void testOrOperator() {
        String criteria =
                "{\"criteria\": {\"or\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"attr\": \"premium\", \"operator\": \"EQ\", \"val\": true}"
                        + "]}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes usUser = new UserAttributes().addAttribute("country", "US");
        UserAttributes premiumUser =
                new UserAttributes().addAttribute("country", "UK").addAttribute("premium", "true");
        UserAttributes regularUkUser =
                new UserAttributes().addAttribute("country", "UK").addAttribute("premium", "false");

        assertTrue(filter.isValid(usUser));
        assertTrue(filter.isValid(premiumUser));
        assertFalse(filter.isValid(regularUkUser));
    }

    @Test
    @DisplayName("NOT operator should negate condition")
    void testNotOperator() {
        String criteria =
                "{\"criteria\": {\"not\": ["
                        + "{\"attr\": \"banned\", \"operator\": \"EQ\", \"val\": true}"
                        + "]}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes notBanned = new UserAttributes().addAttribute("banned", "false");
        UserAttributes banned = new UserAttributes().addAttribute("banned", "true");

        assertTrue(filter.isValid(notBanned));
        assertFalse(filter.isValid(banned));
    }

    @Test
    @DisplayName("Nested AND/OR should evaluate correctly")
    void testNestedLogicalOperators() {
        String criteria =
                "{\"criteria\": {\"and\": ["
                        + "{\"attr\": \"country\", \"operator\": \"EQ\", \"val\": \"US\"},"
                        + "{\"or\": ["
                        + "  {\"attr\": \"age\", \"operator\": \"GT\", \"val\": 21},"
                        + "  {\"attr\": \"premium\", \"operator\": \"EQ\", \"val\": true}"
                        + "]}"
                        + "]}}";
        IFilter filter = generator.getFilterTree(criteria);

        // US + adult
        UserAttributes usAdult =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "25")
                        .addAttribute("premium", "false");
        assertTrue(filter.isValid(usAdult));

        // US + premium (not adult)
        UserAttributes usPremiumMinor =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "18")
                        .addAttribute("premium", "true");
        assertTrue(filter.isValid(usPremiumMinor));

        // US + not adult + not premium
        UserAttributes usNonQualified =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "18")
                        .addAttribute("premium", "false");
        assertFalse(filter.isValid(usNonQualified));

        // UK + adult
        UserAttributes ukAdult =
                new UserAttributes().addAttribute("country", "UK").addAttribute("age", "25");
        assertFalse(filter.isValid(ukAdult));
    }

    @Test
    @DisplayName("VERSION_GT should compare versions correctly")
    void testVersionGreaterThan() {
        String criteria =
                "{\"criteria\": {\"attr\": \"appVersion\", \"operator\": \"VERSION_GT\", \"val\":"
                        + " \"2.0.0\"}}";
        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes v2_1 = new UserAttributes().addAttribute("appVersion", "2.1.0");
        UserAttributes v2_0_1 = new UserAttributes().addAttribute("appVersion", "2.0.1");
        UserAttributes v2_0 = new UserAttributes().addAttribute("appVersion", "2.0.0");
        UserAttributes v1_9 = new UserAttributes().addAttribute("appVersion", "1.9.9");

        assertTrue(filter.isValid(v2_1));
        assertTrue(filter.isValid(v2_0_1));
        assertFalse(filter.isValid(v2_0));
        assertFalse(filter.isValid(v1_9));
    }

    @Test
    @DisplayName("Empty criteria should return true (no restrictions)")
    void testEmptyCriteria() {
        IFilter filter = generator.getFilterTree("");
        UserAttributes anyUser = new UserAttributes().addAttribute("anything", "value");

        assertTrue(filter.isValid(anyUser));
    }

    @Test
    @DisplayName("Null criteria should return true (no restrictions)")
    void testNullCriteria() {
        IFilter filter = generator.getFilterTree(null);
        UserAttributes anyUser = new UserAttributes().addAttribute("anything", "value");

        assertTrue(filter.isValid(anyUser));
    }

    @Test
    @DisplayName(
            "rejectOnParseError: primitive under criteria key fails closed; lenient stays vacuous")
    void strictModeRejectsPrimitiveCriteriaConfigValue() {
        String criteria = "{\"criteria\": true}";
        IFilter strict = generator.getFilterTree(criteria, true, true);
        IFilter lenient = generator.getFilterTree(criteria, true, false);
        UserAttributes user = new UserAttributes().addAttribute("country", "US");

        assertFalse(strict.isValid(user));
        assertTrue(lenient.isValid(user));
    }

    @Test
    @DisplayName("Local evaluation should be sub-millisecond")
    void testPerformance() {
        String criteria =
                "{\"criteria\": {\"and\": [{\"attr\": \"country\", \"operator\": \"IN\", \"val\":"
                    + " [\"US\", \"CA\", \"UK\", \"DE\", \"FR\"]},{\"attr\": \"age\", \"operator\":"
                    + " \"BW\", \"val\": [18, 65]},{\"attr\": \"appVersion\", \"operator\":"
                    + " \"VERSION_GT\", \"val\": \"1.0.0\"}]}}";

        IFilter filter = generator.getFilterTree(criteria);

        UserAttributes user =
                new UserAttributes()
                        .addAttribute("country", "US")
                        .addAttribute("age", "30")
                        .addAttribute("appVersion", "2.5.0");

        // Warm up
        for (int i = 0; i < 100; i++) {
            filter.isValid(user);
        }

        // Measure
        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            filter.isValid(user);
        }
        long end = System.nanoTime();

        double avgMicroseconds = (end - start) / 1000.0 / iterations;
        System.out.printf("Average evaluation time: %.2f microseconds%n", avgMicroseconds);

        // Should be well under 1 millisecond (1000 microseconds)
        assertTrue(avgMicroseconds < 100, "Evaluation should be under 100 microseconds");
    }
}
