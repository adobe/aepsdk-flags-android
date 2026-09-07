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

package com.adobe.marketing.mobile.flags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagResponseMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FlagResponseMapperTest {

    @Test
    public void toFeatureEvaluationResult_nullMap_returnsNull() {
        assertNull(FlagResponseMapper.toFeatureEvaluationResult(null));
    }

    @Test
    public void toFeatureEvaluationResult_emptyMap_returnsNull() {
        assertNull(FlagResponseMapper.toFeatureEvaluationResult(new HashMap<>()));
    }

    @Test
    public void toFeatureEvaluationResult_validMinimal_parsesIdAndKey() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 7);
        m.put(FlagConstants.EventDataKeys.KEY, "my-feature");

        FeatureEvaluationResult r = FlagResponseMapper.toFeatureEvaluationResult(m);
        assertNotNull(r);
        assertEquals(7, r.getId());
        assertEquals("my-feature", r.getKey());
        assertNull(r.getFeatureGroupKey());
        assertNull(r.getMeta());
        assertNull(r.getAnalyticsParam());
    }

    @Test
    public void toFeatureEvaluationResult_validFull_includesOptionalFieldsAndAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_GROUP_ID, 100);
        analytics.put(FlagConstants.EventDataKeys.FEATURE_ID, 200);
        analytics.put(FlagConstants.EventDataKeys.VARIANT_ID, "v1");

        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        m.put(FlagConstants.EventDataKeys.FEATURE_GROUP_KEY, "rel-1");
        m.put(FlagConstants.EventDataKeys.META, "{\"a\":1}");
        m.put(FlagConstants.EventDataKeys.ANALYTICS_PARAM, analytics);

        FeatureEvaluationResult r = FlagResponseMapper.toFeatureEvaluationResult(m);
        assertNotNull(r);
        assertEquals(1, r.getId());
        assertEquals("k", r.getKey());
        assertEquals("rel-1", r.getFeatureGroupKey());
        assertEquals("{\"a\":1}", r.getMeta());
        assertNotNull(r.getAnalyticsParam());
        assertEquals(100, r.getAnalyticsParam().getFeatureGroupId());
        assertEquals(200, r.getAnalyticsParam().getFeatureId());
        assertEquals("v1", r.getAnalyticsParam().getVariantId());
    }

    @Test
    public void toFeatureEvaluationResult_missingId_throws() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        try {
            FlagResponseMapper.toFeatureEvaluationResult(m);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(FlagConstants.EventDataKeys.ID));
        }
    }

    @Test
    public void toFeatureEvaluationResult_missingKey_throws() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        try {
            FlagResponseMapper.toFeatureEvaluationResult(m);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(FlagConstants.EventDataKeys.KEY));
        }
    }

    @Test
    public void toFeatureEvaluationResult_emptyKey_throws() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "   ");
        try {
            FlagResponseMapper.toFeatureEvaluationResult(m);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(FlagConstants.EventDataKeys.KEY));
        }
    }

    @Test
    public void toFeatureEvaluationResult_idCoercedFromString() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, " 99 ");
        m.put(FlagConstants.EventDataKeys.KEY, "k");

        FeatureEvaluationResult r = FlagResponseMapper.toFeatureEvaluationResult(m);
        assertEquals(99, r.getId());
    }

    @Test
    public void toFeatureEvaluationResult_idCoercedFromDouble() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 42.9d);
        m.put(FlagConstants.EventDataKeys.KEY, "k");

        assertEquals(42, FlagResponseMapper.toFeatureEvaluationResult(m).getId());
    }

    @Test
    public void toFeatureEvaluationResult_idMalformedString_throws() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, "not-a-number");
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        try {
            FlagResponseMapper.toFeatureEvaluationResult(m);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid numeric"));
        }
    }

    @Test
    public void toFeatureEvaluationResult_idWrongType_throws() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, Collections.singletonList(1));
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        try {
            FlagResponseMapper.toFeatureEvaluationResult(m);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid numeric"));
        }
    }

    @Test
    public void toFeatureEvaluationResult_keyCoercedFromNumber() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, 55L);

        assertEquals("55", FlagResponseMapper.toFeatureEvaluationResult(m).getKey());
    }

    @Test
    public void toFeatureEvaluationResult_optionalFeatureGroupKeyCoercedFromNumber() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        m.put(FlagConstants.EventDataKeys.FEATURE_GROUP_KEY, 3);

        assertEquals("3", FlagResponseMapper.toFeatureEvaluationResult(m).getFeatureGroupKey());
    }

    @Test
    public void toFeatureEvaluationResult_metaString_passThroughUnmodified() {
        final String meta = "{\"tags\":[\"a\",\"b\"],\"flag\":\"ZmxhZ19mb3I=\"}";

        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        m.put(FlagConstants.EventDataKeys.META, meta);

        FeatureEvaluationResult r = FlagResponseMapper.toFeatureEvaluationResult(m);
        assertNotNull(r);
        assertEquals(meta, r.getMeta());
    }

    @Test
    public void toFeatureEvaluationResult_metaMap_ignored() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("a", 1);

        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        m.put(FlagConstants.EventDataKeys.META, meta);

        FeatureEvaluationResult r = FlagResponseMapper.toFeatureEvaluationResult(m);
        assertNotNull(r);
        assertNull(r.getMeta());
    }

    @Test
    public void toFeatureEvaluationResult_optionalMetaCoercedFromBoolean() {
        Map<String, Object> m = new HashMap<>();
        m.put(FlagConstants.EventDataKeys.ID, 1);
        m.put(FlagConstants.EventDataKeys.KEY, "k");
        m.put(FlagConstants.EventDataKeys.META, true);

        assertEquals("true", FlagResponseMapper.toFeatureEvaluationResult(m).getMeta());
    }

    @Test
    public void toAnalyticsParam_null_returnsNull() {
        assertNull(FlagResponseMapper.toAnalyticsParam(null));
    }

    @Test
    public void toAnalyticsParam_notMap_returnsNull() {
        assertNull(FlagResponseMapper.toAnalyticsParam("x"));
    }

    @Test
    public void toAnalyticsParam_emptyMap_returnsNull() {
        assertNull(FlagResponseMapper.toAnalyticsParam(new HashMap<>()));
    }

    @Test
    public void toAnalyticsParam_valid_mapsFields() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_GROUP_ID, 10);
        analytics.put(FlagConstants.EventDataKeys.FEATURE_ID, 20);
        analytics.put(FlagConstants.EventDataKeys.VARIANT_ID, "var");

        AnalyticsParam a = FlagResponseMapper.toAnalyticsParam(analytics);
        assertNotNull(a);
        assertEquals(10, a.getFeatureGroupId());
        assertEquals(20, a.getFeatureId());
        assertEquals("var", a.getVariantId());
    }

    @Test
    public void toAnalyticsParam_missingFeatureGroupId_throws() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_ID, 20);
        try {
            FlagResponseMapper.toAnalyticsParam(analytics);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(FlagConstants.EventDataKeys.FEATURE_GROUP_ID));
        }
    }

    @Test
    public void toAnalyticsParam_missingFeatureId_throws() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_GROUP_ID, 10);
        try {
            FlagResponseMapper.toAnalyticsParam(analytics);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(FlagConstants.EventDataKeys.FEATURE_ID));
        }
    }

    @Test
    public void toAnalyticsParam_numericIdsCoercedFromString() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_GROUP_ID, "1");
        analytics.put(FlagConstants.EventDataKeys.FEATURE_ID, "2");

        AnalyticsParam a = FlagResponseMapper.toAnalyticsParam(analytics);
        assertEquals(1, a.getFeatureGroupId());
        assertEquals(2, a.getFeatureId());
        assertNull(a.getVariantId());
    }

    @Test
    public void toAnalyticsParam_optionalStringsOmitted_nullInModel() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put(FlagConstants.EventDataKeys.FEATURE_GROUP_ID, 1);
        analytics.put(FlagConstants.EventDataKeys.FEATURE_ID, 2);

        AnalyticsParam a = FlagResponseMapper.toAnalyticsParam(analytics);
        assertNull(a.getVariantId());
    }
}
