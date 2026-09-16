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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class FeatureEvaluationContextTest {

    @Test
    public void build_emptyBuilder_attributesNull() {
        FeatureEvaluationContext ctx = FeatureEvaluationContext.builder().build();
        assertNull(ctx.getAttributes());
    }

    @Test
    public void withAttributes_setsAttributes() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("region", Arrays.asList("US", "EU"));

        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(attrs).build();

        assertEquals(attrs, ctx.getAttributes());
    }

    @Test(expected = IllegalStateException.class)
    public void withAttributes_calledTwice_throwsIllegalStateException() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("region", Arrays.asList("US"));

        FeatureEvaluationContext.builder()
                .withAttributes(attrs)
                .withAttributes(new HashMap<>())
                .build();
    }

    @Test
    public void withAttributes_emptyMap_storedAsNull() {
        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(new HashMap<>()).build();
        assertNull(ctx.getAttributes());
    }

    @Test
    public void withAttributes_null_treatedAsEmpty() {
        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(null).build();
        assertNull(ctx.getAttributes());
    }

    @Test
    public void withAttributes_returnsUnmodifiableMap() {
        Map<String, List<String>> mutable = new HashMap<>();
        mutable.put("region", Arrays.asList("US"));

        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(mutable).build();

        mutable.put("platform", Arrays.asList("android"));

        assertEquals(1, ctx.getAttributes().size());
        assertTrue(ctx.getAttributes().containsKey("region"));
    }

    @Test
    public void withAttributes_listValuesAreUnmodifiable() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("region", Arrays.asList("US"));

        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(attrs).build();

        try {
            ctx.getAttributes().get("region").add("EU");
            org.junit.Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void builder_withAttributes_allFieldsSet() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("tier", Arrays.asList("premium"));

        FeatureEvaluationContext ctx =
                FeatureEvaluationContext.builder().withAttributes(attrs).build();

        assertEquals("premium", ctx.getAttributes().get("tier").get(0));
    }
}
