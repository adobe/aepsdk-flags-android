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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.edge.identity.AuthenticatedState;
import com.adobe.marketing.mobile.edge.identity.IdentityItem;
import com.adobe.marketing.mobile.edge.identity.IdentityMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Edge Identity data into the XDM identityMap shape required by {@code GetFeatureRequest}.
 *
 * <p>Provides two entry points:
 *
 * <ul>
 *   <li>{@link #fromXDMStateMap} — parses the raw XDM shared state map published by the Edge
 *       Identity extension.
 *   <li>{@link #toRequestMap} — converts an {@link IdentityMap} object (from {@code getIdentities}
 *       callback). Used by the warm-up path and unit tests.
 * </ul>
 */
final class IdentityMapMarshaller {

    static final String KEY_ID = "id";
    static final String KEY_PRIMARY = "primary";
    static final String KEY_AUTHENTICATED_STATE = "authenticatedState";

    /**
     * Top-level key in Edge Identity XDM shared state. Matches {@code IdentityMap.asXDMMap}/{@code
     * IdentityMap.fromXDMMap} in edgeidentity.
     */
    static final String XDM_KEY_IDENTITY_MAP = "identityMap";

    private static final String AUTHENTICATED_STATE_AMBIGUOUS = "ambiguous";

    private IdentityMapMarshaller() {}

    /**
     * Parses an XDM shared state map (from {@code ExtensionApi.getXDMSharedState}) into the {@code
     * GetFeatureRequest} identity map shape.
     *
     * <p>Edge Identity publishes XDM shared state via {@code IdentityProperties.toXDMData()} which
     * wraps namespaces under {@link #XDM_KEY_IDENTITY_MAP}:
     *
     * <pre>
     * {
     *   "identityMap": {
     *     "ECID": [{ "id": "...", "primary": true, "authenticatedState": "ambiguous" }],
     *     "Email": [...]
     *   }
     * }
     * </pre>
     *
     * @param xdmStateValue value from {@link
     *     com.adobe.marketing.mobile.SharedStateResult#getValue()}
     * @return immutable namespace → identity entries map, or {@code null} when empty or unparseable
     */
    @Nullable static Map<String, List<Map<String, Object>>> fromXDMStateMap(
            @Nullable final Map<String, Object> xdmStateValue) {
        if (xdmStateValue == null || xdmStateValue.isEmpty()) {
            return null;
        }

        final Object identityMapValue = xdmStateValue.get(XDM_KEY_IDENTITY_MAP);
        if (!(identityMapValue instanceof Map)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> namespacesMap = (Map<String, Object>) identityMapValue;
        return parseNamespacesMap(namespacesMap);
    }

    /**
     * Converts an {@link IdentityMap} object into the request map shape. Used by the warm-up path
     * and unit tests.
     */
    @Nullable static Map<String, List<Map<String, Object>>> toRequestMap(
            @Nullable final IdentityMap identityMap) {
        if (identityMap == null || identityMap.isEmpty()) {
            return null;
        }

        final Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (final String namespace : identityMap.getNamespaces()) {
            final List<IdentityItem> items = identityMap.getIdentityItemsForNamespace(namespace);
            if (items == null || items.isEmpty()) {
                continue;
            }
            final List<Map<String, Object>> serializedItems = new ArrayList<>(items.size());
            for (final IdentityItem item : items) {
                serializedItems.add(toEntryMap(item));
            }
            result.put(namespace, Collections.unmodifiableList(serializedItems));
        }

        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    @Nullable private static Map<String, List<Map<String, Object>>> parseNamespacesMap(
            @Nullable final Map<String, Object> namespacesMap) {
        if (namespacesMap == null || namespacesMap.isEmpty()) {
            return null;
        }

        final Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (final Map.Entry<String, Object> nsEntry : namespacesMap.entrySet()) {
            final String namespace = nsEntry.getKey();
            final Object value = nsEntry.getValue();
            if (!(value instanceof List)) {
                continue;
            }
            final List<?> rawItems = (List<?>) value;
            final List<Map<String, Object>> parsedItems = new ArrayList<>(rawItems.size());
            for (final Object rawItem : rawItems) {
                if (!(rawItem instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                final Map<String, Object> itemMap = (Map<String, Object>) rawItem;
                final Map<String, Object> entry = parseIdentityItem(itemMap);
                if (entry != null) {
                    parsedItems.add(entry);
                }
            }
            if (!parsedItems.isEmpty()) {
                result.put(namespace, Collections.unmodifiableList(parsedItems));
            }
        }

        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    @Nullable private static Map<String, Object> parseIdentityItem(
            @NonNull final Map<String, Object> itemMap) {
        final Object rawId = itemMap.get(KEY_ID);
        if (rawId == null) {
            return null;
        }
        final String id = String.valueOf(rawId);
        if (id.isEmpty()) {
            return null;
        }

        final Map<String, Object> entry = new HashMap<>(3);
        entry.put(KEY_ID, id);
        entry.put(KEY_PRIMARY, Boolean.TRUE.equals(itemMap.get(KEY_PRIMARY)));
        final Object authState = itemMap.get(KEY_AUTHENTICATED_STATE);
        entry.put(
                KEY_AUTHENTICATED_STATE,
                authState instanceof String ? (String) authState : AUTHENTICATED_STATE_AMBIGUOUS);
        return Collections.unmodifiableMap(entry);
    }

    @NonNull private static Map<String, Object> toEntryMap(@NonNull final IdentityItem item) {
        final Map<String, Object> entry = new HashMap<>(3);
        entry.put(KEY_ID, item.getId());
        entry.put(KEY_PRIMARY, item.isPrimary());
        entry.put(KEY_AUTHENTICATED_STATE, toAuthenticatedStateValue(item.getAuthenticatedState()));
        return Collections.unmodifiableMap(entry);
    }

    @NonNull private static String toAuthenticatedStateValue(
            @Nullable final AuthenticatedState authenticatedState) {
        if (authenticatedState == null) {
            return AuthenticatedState.AMBIGUOUS.getName();
        }
        return authenticatedState.getName();
    }
}
