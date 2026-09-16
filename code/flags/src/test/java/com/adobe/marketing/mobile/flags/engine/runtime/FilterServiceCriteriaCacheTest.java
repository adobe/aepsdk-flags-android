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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FilterService} criteria compilation cache. */
class FilterServiceCriteriaCacheTest {

    private static final Map<String, String> TYPES = Map.of("country", "STRING");

    @Test
    void cacheInvalidatesWhenCriteriaVersionChanges() {
        FilterService fs = new FilterService(new HashMap<>(TYPES));
        UserAttributes usUser = UserAttributes.fromContext(Map.of("country", List.of("US")));
        UserAttributes ukUser = UserAttributes.fromContext(Map.of("country", List.of("UK")));
        String logicalId = "feature:1:42:flag-a";

        String criteriaUs =
                "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"US\"}}";
        assertTrue(fs.evaluateCriteriaCached(usUser, logicalId, criteriaUs, "hash-us"));
        assertFalse(fs.evaluateCriteriaCached(ukUser, logicalId, criteriaUs, "hash-us"));

        String criteriaUk =
                "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"UK\"}}";
        assertTrue(fs.evaluateCriteriaCached(ukUser, logicalId, criteriaUk, "hash-uk"));
        assertFalse(fs.evaluateCriteriaCached(usUser, logicalId, criteriaUk, "hash-uk"));
    }

    @Test
    void repeatedEvaluationSameLogicalIdUsesStableResult() {
        FilterService fs = new FilterService(new HashMap<>(TYPES));
        UserAttributes usUser = UserAttributes.fromContext(Map.of("country", List.of("US")));
        String logicalId = "featureGroup:9";
        String criteria =
                "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"US\"}}";

        assertTrue(fs.evaluateCriteriaCached(usUser, logicalId, criteria, "v1"));
        assertTrue(fs.evaluateCriteriaCached(usUser, logicalId, criteria, "v1"));
    }

    @Test
    void nullOrBlankLogicalIdSkipsCacheStillEvaluatesStrictly() {
        FilterService fs = new FilterService(new HashMap<>(TYPES));
        UserAttributes usUser = UserAttributes.fromContext(Map.of("country", List.of("US")));
        UserAttributes ukUser = UserAttributes.fromContext(Map.of("country", List.of("UK")));
        String criteriaUs =
                "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"US\"}}";
        String criteriaUk =
                "{\"criteria\":{\"attr\":\"country\",\"operator\":\"EQ\",\"val\":\"UK\"}}";

        assertTrue(fs.evaluateCriteriaCached(usUser, null, criteriaUs, "a"));
        assertFalse(fs.evaluateCriteriaCached(ukUser, null, criteriaUs, "a"));
        assertTrue(fs.evaluateCriteriaCached(usUser, "  \t\n", criteriaUs, "b"));
        assertFalse(fs.evaluateCriteriaCached(ukUser, "", criteriaUs, "c"));

        assertTrue(fs.matchesCriteria(criteriaUs, usUser));
        assertTrue(fs.evaluateCriteriaCached(usUser, null, criteriaUs, "x"));
        assertFalse(fs.evaluateCriteriaCached(usUser, null, criteriaUk, "y"));
    }
}
