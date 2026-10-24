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
import com.adobe.marketing.mobile.flags.engine.runtime.expression.DateExpressionValidator;
import com.adobe.marketing.mobile.flags.engine.runtime.expression.ExpressionValidator;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for new Flags Android SDK features: - Date expression validation (today+5d) - AEP Context
 * support - PATCH operator - Selected value references - Filter patches cache
 */
class NewFeaturesTest {

    @BeforeEach
    void setUp() {
        // Clear filter patches cache before each test
        FilterPatchesCache.getInstance().refreshCache();
    }

    @Test
    void testDateExpressionTodayPlus() {
        DateExpressionValidator validator = new DateExpressionValidator();

        Long result = (Long) validator.parseExpression("today+5d");
        assertNotNull(result);

        Calendar expected = Calendar.getInstance();
        expected.add(Calendar.DAY_OF_YEAR, 5);

        // Should be within 1 second of expected
        assertTrue(Math.abs(result - expected.getTimeInMillis()) < 1000);
    }

    @Test
    void testDateExpressionTodayMinus() {
        DateExpressionValidator validator = new DateExpressionValidator();

        Long result = (Long) validator.parseExpression("today-3d");
        assertNotNull(result);

        Calendar expected = Calendar.getInstance();
        expected.add(Calendar.DAY_OF_YEAR, -3);

        assertTrue(Math.abs(result - expected.getTimeInMillis()) < 1000);
    }

    @Test
    void testDateExpressionInvalidFormat() {
        DateExpressionValidator validator = new DateExpressionValidator();
        assertThrows(IllegalArgumentException.class, () -> validator.parseExpression("invalid"));
    }

    @Test
    void testExpressionValidatorEnum() {
        ExpressionValidator dateValidator =
                ExpressionValidator.getExpressionValidator(FieldDataType.DATE);
        assertNotNull(dateValidator);
        assertEquals(FieldDataType.DATE, dateValidator.getDataType());

        ExpressionValidator dateTimeValidator =
                ExpressionValidator.getExpressionValidator(FieldDataType.DATE_TIME);
        assertNotNull(dateTimeValidator);

        // STRING type should not have expression validator
        assertNull(ExpressionValidator.getExpressionValidator(FieldDataType.STRING));
    }

    @Test
    void testSupportsExpression() {
        assertTrue(ExpressionValidator.supportsExpression(FieldDataType.DATE));
        assertTrue(ExpressionValidator.supportsExpression(FieldDataType.DATE_TIME));
        assertFalse(ExpressionValidator.supportsExpression(FieldDataType.STRING));
        assertFalse(ExpressionValidator.supportsExpression(FieldDataType.INTEGER));
    }

    @Test
    void testFilterPatchesCacheSingleton() {
        FilterPatchesCache cache1 = FilterPatchesCache.getInstance();
        FilterPatchesCache cache2 = FilterPatchesCache.getInstance();
        assertSame(cache1, cache2);
    }

    @Test
    void testFilterPatchesCacheAddGet() {
        FilterPatchesCache cache = FilterPatchesCache.getInstance();

        Filter testFilter = new Filter("country", "US", FilterComparator.EQ, FieldDataType.STRING);
        cache.addToCache("patch1", testFilter);

        assertTrue(cache.hasPatch("patch1"));
        IFilter retrieved = cache.getPatch("patch1");
        assertSame(testFilter, retrieved);
    }

    @Test
    void testFilterPatchesCacheRemove() {
        FilterPatchesCache cache = FilterPatchesCache.getInstance();

        Filter testFilter = new Filter("country", "US", FilterComparator.EQ, FieldDataType.STRING);
        cache.addToCache("patchToRemove", testFilter);

        assertTrue(cache.hasPatch("patchToRemove"));
        cache.removeFromCache("patchToRemove");
        assertFalse(cache.hasPatch("patchToRemove"));
    }

    @Test
    void testFilterPatchesCacheEmptyFilter() {
        FilterPatchesCache cache = FilterPatchesCache.getInstance();

        // Getting non-existent patch should return EmptyFilter
        IFilter result = cache.getPatch("nonexistent");
        assertTrue(result instanceof EmptyFilter);
    }

    @Test
    void testPatchOperator() {
        // Setup: Create and cache a filter
        Filter cachedFilter =
                new Filter("country", "US", FilterComparator.EQ, FieldDataType.STRING);
        FilterPatchesCache.getInstance().addToCache("countryUSPatch", cachedFilter);

        // Create a PATCH filter that references the cached filter
        Filter patchFilter = new Filter(null, "countryUSPatch", FilterComparator.PATCH, null);
        assertTrue(patchFilter.isPatch());

        // Test evaluation
        UserAttributes usUser = new UserAttributes().addAttribute("country", "US");
        UserAttributes ukUser = new UserAttributes().addAttribute("country", "UK");

        assertTrue(patchFilter.isValid(usUser));
        assertFalse(patchFilter.isValid(ukUser));
    }

    @Test
    void testSelectedValueReference() {
        // Create filter that compares "currentCountry" with the value from "preferredCountry"
        Filter filter =
                new Filter(
                        "currentCountry",
                        "preferredCountry",
                        FilterComparator.EQ,
                        FieldDataType.STRING,
                        0,
                        false,
                        true);

        // User where current matches preferred
        UserAttributes matchingUser =
                new UserAttributes()
                        .addAttribute("currentCountry", "US")
                        .addAttribute("preferredCountry", "US");

        // User where they don't match
        UserAttributes nonMatchingUser =
                new UserAttributes()
                        .addAttribute("currentCountry", "US")
                        .addAttribute("preferredCountry", "UK");

        assertTrue(filter.isValid(matchingUser));
        assertFalse(filter.isValid(nonMatchingUser));
    }

    @Test
    void testExpressionFilter() {
        // Create filter with expression
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("cancelDate", "DATE");

        // Create a date filter that compares cancelDate < today-5d
        Filter filter =
                new Filter(
                        "cancelDate",
                        "today-5d",
                        FilterComparator.LT,
                        FieldDataType.DATE,
                        0,
                        true,
                        false);

        // User with cancel date 10 days ago
        Calendar tenDaysAgo = Calendar.getInstance();
        tenDaysAgo.add(Calendar.DAY_OF_YEAR, -10);

        UserAttributes oldCancelUser =
                new UserAttributes()
                        .addAttribute("cancelDate", String.valueOf(tenDaysAgo.getTimeInMillis()));

        // User with cancel date 2 days ago
        Calendar twoDaysAgo = Calendar.getInstance();
        twoDaysAgo.add(Calendar.DAY_OF_YEAR, -2);

        UserAttributes recentCancelUser =
                new UserAttributes()
                        .addAttribute("cancelDate", String.valueOf(twoDaysAgo.getTimeInMillis()));

        assertTrue(filter.isValid(oldCancelUser)); // 10 days ago < today-5d
        assertFalse(filter.isValid(recentCancelUser)); // 2 days ago is not < today-5d
    }

    @Test
    void testFieldDataTypeCache() {
        FieldDataTypeCache cache = new FieldDataTypeCache();

        cache.setFieldDataType("country", FieldDataType.STRING);
        cache.setFieldDataType("age", FieldDataType.INTEGER);

        assertEquals(FieldDataType.STRING, cache.getFieldDataType("country"));
        assertEquals(FieldDataType.INTEGER, cache.getFieldDataType("age"));
        assertNull(cache.getFieldDataType("nonexistent"));
    }

    @Test
    void testFieldDataTypeCacheAepMetadata() {
        FieldDataTypeCache cache = new FieldDataTypeCache();

        cache.setAepMetadata("aepField", FieldDataType.STRING);

        assertTrue(cache.hasAepMetadata("aepField"));
        assertEquals(FieldDataType.STRING, cache.getAepMetadataCache("aepField"));
        assertFalse(cache.hasAepMetadata("nonexistent"));
    }

    @Test
    void testFieldDataTypeCacheFieldMetadata() {
        FieldDataTypeCache cache = new FieldDataTypeCache();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("isBucketizable", "true");
        cache.setFieldMetaData("bucket_field", metadata);

        assertEquals(Map.of("isBucketizable", "true"), cache.getFieldMetaData("bucket_field"));
        assertNotEquals(Map.of("isBucketizable", "true"), cache.getFieldMetaData("nonexistent"));
    }

    @Test
    void testFilterServiceBasicValidation() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("country", "STRING");

        FilterService filterService = new FilterService(fieldTypes);

        Filter filter = new Filter("country", "US", FilterComparator.EQ, FieldDataType.STRING);
        UserAttributes usUser = new UserAttributes().addAttribute("country", "US");
        UserAttributes ukUser = new UserAttributes().addAttribute("country", "UK");

        assertTrue(filterService.isValid(usUser, filter));
        assertFalse(filterService.isValid(ukUser, filter));
    }

    @Test
    void testFilterServiceNullFilter() {
        Map<String, String> fieldTypes = new HashMap<>();
        FilterService filterService = new FilterService(fieldTypes);

        UserAttributes user = new UserAttributes().addAttribute("country", "US");

        // Null filter should return true
        assertTrue(filterService.isValid(user, null));
    }

    @Test
    void matchesCriteriaVacuousWhenCriteriaAbsent() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("country", "STRING");
        FilterService filterService = new FilterService(fieldTypes);
        UserAttributes user = new UserAttributes().addAttribute("country", "US");
        assertTrue(filterService.matchesCriteria(null, user));
        assertTrue(filterService.matchesCriteria("", user));
    }

    @Test
    void matchesCriteriaFailsClosedOnMalformedJson() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("country", "STRING");
        FilterService filterService = new FilterService(fieldTypes);
        UserAttributes user = new UserAttributes().addAttribute("country", "US");
        assertFalse(filterService.matchesCriteria("not valid json{{{", user));
    }

    @Test
    void testContainsAndFilterDelegatorBasic() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("plan", "STRING");
        fieldTypes.put("status", "STRING");

        FilterService filterService = new FilterService(fieldTypes);
        ContainsAndFilterDelegator delegator = new ContainsAndFilterDelegator(filterService);

        // Create a nested filter that matches subscriptions with status "active"
        Filter nestedFilter =
                new Filter("status", "active", FilterComparator.EQ, FieldDataType.STRING);

        // Create subscriptions (nested UserAttributes)
        UserAttributes sub1 =
                new UserAttributes()
                        .addAttribute("plan", "premium")
                        .addAttribute("status", "inactive");

        UserAttributes sub2 =
                new UserAttributes().addAttribute("plan", "basic").addAttribute("status", "active");

        java.util.List<UserAttributes> subscriptions = Arrays.asList(sub1, sub2);

        // Test delegation - should match because sub2 is active
        boolean result = delegator.delegate(subscriptions, nestedFilter, null);
        assertTrue(result);
    }

    @Test
    void testContainsAndFilterDelegatorNoMatch() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("status", "STRING");

        FilterService filterService = new FilterService(fieldTypes);
        ContainsAndFilterDelegator delegator = new ContainsAndFilterDelegator(filterService);

        // Create a nested filter that matches subscriptions with status "active"
        Filter nestedFilter =
                new Filter("status", "active", FilterComparator.EQ, FieldDataType.STRING);

        // Create subscriptions (all inactive)
        UserAttributes sub1 = new UserAttributes().addAttribute("status", "inactive");
        UserAttributes sub2 = new UserAttributes().addAttribute("status", "cancelled");

        java.util.List<UserAttributes> subscriptions = Arrays.asList(sub1, sub2);

        // Test delegation - should NOT match
        boolean result = delegator.delegate(subscriptions, nestedFilter, null);
        assertFalse(result);
    }

    @Test
    void testContainsAndFilterDelegatorWithReturnValues() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("plan", "STRING");
        fieldTypes.put("status", "STRING");

        FilterService filterService = new FilterService(fieldTypes);
        ContainsAndFilterDelegator delegator = new ContainsAndFilterDelegator(filterService);

        // Create a nested filter
        Filter nestedFilter =
                new Filter("status", "active", FilterComparator.EQ, FieldDataType.STRING);

        // Create subscriptions
        UserAttributes sub1 =
                new UserAttributes()
                        .addAttribute("plan", "premium")
                        .addAttribute("status", "active");

        java.util.List<UserAttributes> subscriptions = Collections.singletonList(sub1);

        // Test delegation with return values
        IFilter.FilterResult result =
                delegator.delegateWithReturnValues(
                        subscriptions, nestedFilter, null, 1, "subscriptions");

        assertTrue(result.isValid());
        assertNotNull(result.getMatchedAttributes());
    }

    @Test
    void testContainsAndFilterDelegatorAllReturnValues() {
        Map<String, String> fieldTypes = new HashMap<>();
        fieldTypes.put("status", "STRING");

        FilterService filterService = new FilterService(fieldTypes);
        ContainsAndFilterDelegator delegator = new ContainsAndFilterDelegator(filterService);

        // Create a nested filter
        Filter nestedFilter =
                new Filter("status", "active", FilterComparator.EQ, FieldDataType.STRING);

        // Create subscriptions (2 active, 1 inactive)
        UserAttributes sub1 = new UserAttributes().addAttribute("status", "active");
        UserAttributes sub2 = new UserAttributes().addAttribute("status", "inactive");
        UserAttributes sub3 = new UserAttributes().addAttribute("status", "active");

        java.util.List<UserAttributes> subscriptions = Arrays.asList(sub1, sub2, sub3);

        // Test delegation - should return 2 matching states
        java.util.List<UserAttributes> validStates =
                delegator.delegateWithAllReturnValues(
                        subscriptions, nestedFilter, null, 1, "subscriptions");

        assertEquals(2, validStates.size());
    }

    @Test
    void testContainsAndFilterDelegatorPreprocess() {
        Map<String, String> fieldTypes = new HashMap<>();
        FilterService filterService = new FilterService(fieldTypes);
        ContainsAndFilterDelegator delegator = new ContainsAndFilterDelegator(filterService);

        // Create parent user with selected attribute
        UserAttributes parent = new UserAttributes().addAttribute("selected_id", "123");

        // Create subscription
        UserAttributes sub = new UserAttributes().addAttribute("plan", "premium");
        java.util.List<UserAttributes> subscriptions = Collections.singletonList(sub);

        // Preprocess - should add selected_id to subscription
        delegator.preprocessUserAttributes(subscriptions, parent);

        // Check that selected attribute was added
        assertEquals("123", sub.getAttribute("selected_id"));
    }

    @Test
    void testUserAttributesMerge() {
        UserAttributes target = new UserAttributes().addAttribute("country", "US");

        UserAttributes source =
                new UserAttributes().addAttribute("city", "NYC").addAttribute("state", "NY");

        target.addOrUpdateUserAttributes(source);

        assertEquals("US", target.getAttribute("country"));
        assertEquals("NYC", target.getAttribute("city"));
        assertEquals("NY", target.getAttribute("state"));
    }
}
