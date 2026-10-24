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

package com.adobe.marketing.mobile.flags.engine.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetFeatureRequestTest {

    @Test
    @DisplayName("build without fields returns empty maps")
    void buildWithoutFields_returnsEmptyMaps() {
        GetFeatureRequest request = new GetFeatureRequest.Builder().build();

        assertTrue(request.getContext().isEmpty());
        assertTrue(request.getIdentityMap().isEmpty());
    }

    @Test
    @DisplayName("DEFAULT is empty and safe to reuse")
    void defaultRequest_isEmpty() {
        assertTrue(GetFeatureRequest.DEFAULT.getContext().isEmpty());
        assertTrue(GetFeatureRequest.DEFAULT.getIdentityMap().isEmpty());
    }

    @Test
    @DisplayName("context is isolated from outer map mutation after build")
    void context_isolatedFromOuterMapMutation() {
        Map<String, List<String>> context = new HashMap<>();
        context.put("country", new ArrayList<>(List.of("US")));

        GetFeatureRequest request = new GetFeatureRequest.Builder().context(context).build();

        context.put("region", List.of("VA7"));
        context.remove("country");

        assertEquals(List.of("US"), request.getContext().get("country"));
        assertEquals(1, request.getContext().size());
    }

    @Test
    @DisplayName("context is isolated from inner list mutation after build")
    void context_isolatedFromInnerListMutation() {
        List<String> countries = new ArrayList<>(List.of("US"));
        Map<String, List<String>> context = Map.of("country", countries);

        GetFeatureRequest request = new GetFeatureRequest.Builder().context(context).build();

        countries.add("CA");

        assertEquals(List.of("US"), request.getContext().get("country"));
    }

    @Test
    @DisplayName("identity map is isolated from outer map mutation after build")
    void identityMap_isolatedFromOuterMapMutation() {
        Map<String, Object> entry = new HashMap<>();
        entry.put(Constants.IDENTITY_ENTRY_KEY_ID, "ecid-123");
        entry.put(Constants.IDENTITY_ENTRY_KEY_PRIMARY, true);
        entry.put(Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE, "ambiguous");

        Map<String, List<Map<String, Object>>> identityMap = new HashMap<>();
        identityMap.put("ECID", new ArrayList<>(List.of(entry)));

        GetFeatureRequest request =
                new GetFeatureRequest.Builder().identityMap(identityMap).build();

        identityMap.put(
                "Email",
                List.of(
                        Map.of(
                                Constants.IDENTITY_ENTRY_KEY_ID,
                                "other",
                                Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                                false)));
        identityMap.remove("ECID");

        assertEquals(1, request.getIdentityMap().size());
        assertEquals(
                "ecid-123",
                request.getIdentityMap().get("ECID").get(0).get(Constants.IDENTITY_ENTRY_KEY_ID));
    }

    @Test
    @DisplayName("identity map is isolated from inner list mutation after build")
    void identityMap_isolatedFromInnerListMutation() {
        Map<String, Object> entry = new HashMap<>();
        entry.put(Constants.IDENTITY_ENTRY_KEY_ID, "ecid-123");
        entry.put(Constants.IDENTITY_ENTRY_KEY_PRIMARY, true);
        entry.put(Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE, "ambiguous");

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry);

        Map<String, List<Map<String, Object>>> identityMap = new HashMap<>();
        identityMap.put("ECID", entries);

        GetFeatureRequest request =
                new GetFeatureRequest.Builder().identityMap(identityMap).build();

        entries.add(
                Map.of(
                        Constants.IDENTITY_ENTRY_KEY_ID,
                        "other",
                        Constants.IDENTITY_ENTRY_KEY_PRIMARY,
                        false));

        assertEquals(1, request.getIdentityMap().get("ECID").size());
        assertEquals(
                "ecid-123",
                request.getIdentityMap().get("ECID").get(0).get(Constants.IDENTITY_ENTRY_KEY_ID));
    }

    @Test
    @DisplayName("identity map is isolated from entry mutation after build")
    void identityMap_isolatedFromEntryMutation() {
        Map<String, Object> entry = new HashMap<>();
        entry.put(Constants.IDENTITY_ENTRY_KEY_ID, "ecid-123");
        entry.put(Constants.IDENTITY_ENTRY_KEY_PRIMARY, true);
        entry.put(Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE, "ambiguous");

        Map<String, List<Map<String, Object>>> identityMap =
                Map.of("ECID", new ArrayList<>(List.of(entry)));

        GetFeatureRequest request =
                new GetFeatureRequest.Builder().identityMap(identityMap).build();

        entry.put(Constants.IDENTITY_ENTRY_KEY_ID, "mutated");

        assertEquals(
                "ecid-123",
                request.getIdentityMap().get("ECID").get(0).get(Constants.IDENTITY_ENTRY_KEY_ID));
    }

    @Test
    @DisplayName("getters return unmodifiable maps")
    void getters_returnUnmodifiableMaps() {
        Map<String, Object> entry =
                Map.of(
                        Constants.IDENTITY_ENTRY_KEY_ID, "ecid-123",
                        Constants.IDENTITY_ENTRY_KEY_PRIMARY, true,
                        Constants.IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE, "ambiguous");
        GetFeatureRequest request =
                new GetFeatureRequest.Builder()
                        .context(Map.of("country", List.of("US")))
                        .identityMap(Map.of("ECID", List.of(entry)))
                        .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> request.getContext().put("x", List.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.getIdentityMap().put("Email", List.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.getContext().get("country").add("CA"));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        request.getIdentityMap()
                                .get("ECID")
                                .get(0)
                                .put(Constants.IDENTITY_ENTRY_KEY_ID, "mutated"));
    }
}
