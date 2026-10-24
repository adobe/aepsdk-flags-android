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

package com.adobe.marketing.mobile.flags.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.flags.engine.models.GetFeatureRequest;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GetFeatureRequestFactoryTest {

    @Test
    public void create_withContextAndIdentityMap_setsBothFields() {
        Map<String, List<String>> context = Map.of("region", List.of("US"));
        Map<String, List<Map<String, Object>>> identityMap =
                Map.of(
                        "ECID",
                        List.of(
                                Map.of(
                                        IdentityMapMarshaller.KEY_ID,
                                        "ecid-123",
                                        IdentityMapMarshaller.KEY_PRIMARY,
                                        true)));

        GetFeatureRequest request = GetFeatureRequestFactory.create(context, identityMap);

        assertEquals(context, request.getContext());
        assertEquals(identityMap, request.getIdentityMap());
    }

    @Test
    public void create_withoutIdentityMap_omitsIdentityMap() {
        GetFeatureRequest request = GetFeatureRequestFactory.create(null, null);

        assertTrue(request.getContext().isEmpty());
        assertTrue(request.getIdentityMap().isEmpty());
    }

    @Test
    public void create_withEmptyIdentityMap_omitsIdentityMap() {
        GetFeatureRequest request = GetFeatureRequestFactory.create(null, Map.of());

        assertTrue(request.getIdentityMap().isEmpty());
    }

    @Test
    public void create_withContextOnly_preservesContextAndOmitsIdentity() {
        Map<String, List<String>> context = Map.of("region", List.of("US", "EU"));

        GetFeatureRequest request = GetFeatureRequestFactory.create(context, null);

        assertEquals(context, request.getContext());
        assertTrue(request.getIdentityMap().isEmpty());
    }
}
