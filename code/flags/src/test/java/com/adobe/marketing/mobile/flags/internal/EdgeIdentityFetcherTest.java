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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EdgeIdentityFetcherTest {

    @Mock private ExtensionApi mockExtensionApi;
    @Mock private Event mockEvent;

    private EdgeIdentityFetcher fetcher;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        fetcher = new EdgeIdentityFetcher();
    }

    @Test
    public void fetchIdentityMap_returnsParsedMapWhenSharedStateSet() {
        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(IdentityMapMarshaller.KEY_ID, "ecid-from-shared-state");
        ecidItem.put(IdentityMapMarshaller.KEY_PRIMARY, true);
        ecidItem.put(IdentityMapMarshaller.KEY_AUTHENTICATED_STATE, "ambiguous");

        final Map<String, Object> namespaces = Map.of("ECID", List.of(ecidItem));
        final Map<String, Object> xdmState = new HashMap<>();
        xdmState.put(IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP, namespaces);

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        final Map<String, List<Map<String, Object>>> result =
                fetcher.fetchIdentityMap(mockExtensionApi, mockEvent);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(
                "ecid-from-shared-state",
                result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_ID));
    }

    @Test
    public void fetchIdentityMap_returnsNullWhenSharedStatePending() {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.PENDING, null));

        assertNull(fetcher.fetchIdentityMap(mockExtensionApi, mockEvent));
    }

    @Test
    public void fetchIdentityMap_returnsNullWhenSharedStateMissing() {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(null);

        assertNull(fetcher.fetchIdentityMap(mockExtensionApi, mockEvent));
    }

    @Test
    public void fetchIdentityMap_returnsNullWhenWrappedIdentityMapEmpty() {
        final Map<String, Object> xdmState =
                Map.of(IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP, Map.of());

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        assertNull(fetcher.fetchIdentityMap(mockExtensionApi, mockEvent));
    }

    @Test
    public void isIdentityReady_returnsTrueWhenEdgeIdentityNotRegistered() {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(null);

        assertTrue(fetcher.isIdentityReady(mockExtensionApi, mockEvent));
    }

    @Test
    public void isIdentityReady_returnsFalseWhenSharedStatePending() {
        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.PENDING, null));

        assertFalse(fetcher.isIdentityReady(mockExtensionApi, mockEvent));
    }

    @Test
    public void isIdentityReady_returnsTrueWhenSharedStateSetWithValidMap() {
        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(IdentityMapMarshaller.KEY_ID, "ecid-123");
        ecidItem.put(IdentityMapMarshaller.KEY_PRIMARY, true);
        final Map<String, Object> xdmState =
                Map.of(
                        IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP,
                        Map.of("ECID", List.of(ecidItem)));

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        assertTrue(fetcher.isIdentityReady(mockExtensionApi, mockEvent));
    }

    @Test
    public void isIdentityReady_returnsTrueWhenSharedStateSetWithEmptyMap() {
        final Map<String, Object> xdmState =
                Map.of(IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP, Map.of());

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        assertTrue(fetcher.isIdentityReady(mockExtensionApi, mockEvent));
        assertNull(fetcher.fetchIdentityMap(mockExtensionApi, mockEvent));
    }

    @Test
    public void fetchIdentityMap_returnsNullWhenFlatNamespacesAtRootWithoutWrapper() {
        // Real-world regression: Edge Identity always wraps under identityMap; flat root must not
        // parse as valid identity (would silently produce wrong cohorts if misread).
        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(IdentityMapMarshaller.KEY_ID, "ecid-flat-root");
        ecidItem.put(IdentityMapMarshaller.KEY_PRIMARY, true);
        final Map<String, Object> flatState = Map.of("ECID", List.of(ecidItem));

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, flatState));

        assertNull(fetcher.fetchIdentityMap(mockExtensionApi, mockEvent));
        assertTrue(fetcher.isIdentityReady(mockExtensionApi, mockEvent));
    }

    @Test
    public void fetchIdentityMap_returnsMultipleNamespacesWhenWrapped() {
        final Map<String, Object> ecidItem = new HashMap<>();
        ecidItem.put(IdentityMapMarshaller.KEY_ID, "ecid-123");
        ecidItem.put(IdentityMapMarshaller.KEY_PRIMARY, true);
        ecidItem.put(IdentityMapMarshaller.KEY_AUTHENTICATED_STATE, "ambiguous");

        final Map<String, Object> loginItem = new HashMap<>();
        loginItem.put(IdentityMapMarshaller.KEY_ID, "user@example.com");
        loginItem.put(IdentityMapMarshaller.KEY_PRIMARY, false);
        loginItem.put(IdentityMapMarshaller.KEY_AUTHENTICATED_STATE, "authenticated");

        final Map<String, Object> namespaces = new HashMap<>();
        namespaces.put("ECID", List.of(ecidItem));
        namespaces.put("Email", List.of(loginItem));
        final Map<String, Object> xdmState =
                Map.of(IdentityMapMarshaller.XDM_KEY_IDENTITY_MAP, namespaces);

        when(mockExtensionApi.getXDMSharedState(
                        eq(FlagConstants.EdgeIdentity.EXTENSION_NAME),
                        eq(mockEvent),
                        eq(false),
                        eq(SharedStateResolution.LAST_SET)))
                .thenReturn(new SharedStateResult(SharedStateStatus.SET, xdmState));

        final Map<String, List<Map<String, Object>>> result =
                fetcher.fetchIdentityMap(mockExtensionApi, mockEvent);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ecid-123", result.get("ECID").get(0).get(IdentityMapMarshaller.KEY_ID));
        assertEquals(
                "user@example.com", result.get("Email").get(0).get(IdentityMapMarshaller.KEY_ID));
    }
}
