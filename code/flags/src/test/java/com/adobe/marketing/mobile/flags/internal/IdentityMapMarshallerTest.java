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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.edge.identity.AuthenticatedState;
import com.adobe.marketing.mobile.edge.identity.IdentityItem;
import com.adobe.marketing.mobile.edge.identity.IdentityMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class IdentityMapMarshallerTest {

    /** ECID entry matching Edge Identity {@code IdentityItem.toObjectMap()} output. */
    private static Map<String, Object> ecidItem(
            final String id, final boolean primary, final String authenticatedState) {
        final Map<String, Object> item = new HashMap<>();
        item.put(IdentityMapMarshaller.KEY_ID, id);
        item.put(IdentityMapMarshaller.KEY_PRIMARY, primary);
        item.put(IdentityMapMarshaller.KEY_AUTHENTICATED_STATE, authenticatedState);
        return item;
    }

    /**
     * Wraps namespace entries under {@code identityMap}, matching Edge Identity {@code
     * IdentityMap.asXDMMap} / {@code IdentityProperties.toXDMData} shared-state shape.
     */
    private static Map<String, Object> edgeIdentityXdmSharedState(
            final Map<String, Object> namespaces) {
        final Map<String, Object> state = new HashMap<>();
        state.put(IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP, namespaces);
        return state;
    }

    @Test
    public void toRequestMap_nullOrEmpty_returnsNull() {
        assertNull(IdentityMapMarshaller.toRequestMap(null));

        IdentityMap empty = new IdentityMap();
        assertNull(IdentityMapMarshaller.toRequestMap(empty));
    }

    @Test
    public void toRequestMap_serializesNamespaceEntries() {
        IdentityMap identityMap = new IdentityMap();
        identityMap.addItem(
                new IdentityItem("ecid-123", AuthenticatedState.AMBIGUOUS, true), "ECID");
        identityMap.addItem(
                new IdentityItem("user@example.com", AuthenticatedState.AUTHENTICATED, false),
                "Email");

        Map<String, List<Map<String, Object>>> result =
                IdentityMapMarshaller.toRequestMap(identityMap);

        assertEquals(2, result.size());

        List<Map<String, Object>> ecidEntries = result.get("ECID");
        assertEquals(1, ecidEntries.size());
        assertEquals("ecid-123", ecidEntries.get(0).get(IdentityMapMarshaller.KEY_ID));
        assertEquals(true, ecidEntries.get(0).get(IdentityMapMarshaller.KEY_PRIMARY));
        assertEquals(
                AuthenticatedState.AMBIGUOUS.getName(),
                ecidEntries.get(0).get(IdentityMapMarshaller.KEY_AUTHENTICATED_STATE));

        List<Map<String, Object>> emailEntries = result.get("Email");
        assertEquals(1, emailEntries.size());
        assertEquals("user@example.com", emailEntries.get(0).get(IdentityMapMarshaller.KEY_ID));
        assertEquals(false, emailEntries.get(0).get(IdentityMapMarshaller.KEY_PRIMARY));
    }

    @Test
    public void toRequestMap_resultIsUnmodifiable() {
        IdentityMap identityMap = new IdentityMap();
        identityMap.addItem(new IdentityItem("ecid-123"), "ECID");

        Map<String, List<Map<String, Object>>> result =
                IdentityMapMarshaller.toRequestMap(identityMap);

        try {
            result.put("CRMID", List.of());
            org.junit.Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    @Test
    public void fromXDMStateMap_parsesEdgeIdentityWrappedSharedState() {
        final Map<String, Object> namespaces =
                Map.of(
                        "ECID",
                        List.of(
                                ecidItem(
                                        "44809014977647551167356491107014304096",
                                        true,
                                        "ambiguous")));

        final Map<String, List<Map<String, Object>>> result =
                IdentityMapMarshaller.fromXDMStateMap(edgeIdentityXdmSharedState(namespaces));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(
                "44809014977647551167356491107014304096",
                result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_ID));
        assertEquals(true, result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_PRIMARY));
    }

    @Test
    public void fromXDMStateMap_flatNamespaceAtRoot_returnsNull() {
        // Regression: pre-fix parser incorrectly expected namespaces at root level.
        final Map<String, Object> flatState =
                Map.of("ECID", List.of(ecidItem("ecid-123", true, "ambiguous")));

        assertNull(IdentityMapMarshaller.fromXDMStateMap(flatState));
    }

    @Test
    public void fromXDMStateMap_nullOrEmpty_returnsNull() {
        assertNull(IdentityMapMarshaller.fromXDMStateMap(null));
        assertNull(IdentityMapMarshaller.fromXDMStateMap(Map.of()));
        assertNull(IdentityMapMarshaller.fromXDMStateMap(Map.of("identityMap", Map.of())));
    }

    @Test
    public void fromXDMStateMap_missingIdentityMapKey_returnsNull() {
        assertNull(IdentityMapMarshaller.fromXDMStateMap(Map.of("ECID", List.of())));
    }

    @Test
    public void fromXDMStateMap_multipleNamespaces() {
        final Map<String, Object> namespaces = new HashMap<>();
        namespaces.put("ECID", List.of(ecidItem("ecid-a", true, "ambiguous")));
        namespaces.put("Email", List.of(ecidItem("user@example.com", false, "authenticated")));

        final Map<String, List<Map<String, Object>>> result =
                IdentityMapMarshaller.fromXDMStateMap(edgeIdentityXdmSharedState(namespaces));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ecid-a", result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_ID));
        assertEquals(
                "user@example.com", result.get("Email").get(0).get(IdentityMapMarshaller.KEY_ID));
    }

    @Test
    public void fromXDMStateMap_skipsItemsWithMissingOrEmptyId() {
        final Map<String, Object> badItem = new HashMap<>();
        badItem.put(IdentityMapMarshaller.KEY_PRIMARY, true);
        final Map<String, Object> emptyIdItem = new HashMap<>();
        emptyIdItem.put(IdentityMapMarshaller.KEY_ID, "");
        final Map<String, Object> namespaces =
                Map.of(
                        "ECID",
                        List.of(badItem, emptyIdItem, ecidItem("valid-ecid", true, "ambiguous")));

        final Map<String, List<Map<String, Object>>> result =
                IdentityMapMarshaller.fromXDMStateMap(edgeIdentityXdmSharedState(namespaces));

        assertNotNull(result);
        assertEquals(1, result.get("ECID").size());
        assertEquals("valid-ecid", result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_ID));
    }

    @Test
    public void fromXDMStateMap_matchesToRequestMapForSameIdentityData() {
        IdentityMap identityMap = new IdentityMap();
        identityMap.addItem(
                new IdentityItem("ecid-parity", AuthenticatedState.AUTHENTICATED, true), "ECID");
        identityMap.addItem(
                new IdentityItem("user@adobe.com", AuthenticatedState.AMBIGUOUS, false), "Email");

        final Map<String, List<Map<String, Object>>> fromObject =
                IdentityMapMarshaller.toRequestMap(identityMap);

        final Map<String, Object> namespaces = new HashMap<>();
        for (final Map.Entry<String, List<Map<String, Object>>> entry : fromObject.entrySet()) {
            namespaces.put(entry.getKey(), entry.getValue());
        }

        final Map<String, List<Map<String, Object>>> fromXdm =
                IdentityMapMarshaller.fromXDMStateMap(edgeIdentityXdmSharedState(namespaces));

        assertEquals(fromObject, fromXdm);
    }
}
