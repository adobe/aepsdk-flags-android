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
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.edge.identity.Identity;
import java.util.List;
import java.util.Map;

/**
 * Reads Edge Identity from XDM shared state for synchronous, event-ordered identity resolution.
 *
 * <p>Uses {@link ExtensionApi#getXDMSharedState} with the incoming flag request {@link Event} so
 * the read is consistent with other extensions' views of identity at that point in the event
 * stream. This avoids the EventHub round-trip and blocking latch that {@link
 * Identity#getIdentities} requires.
 *
 * <p>{@link #warmUp()} is called once at client initialization as a fire-and-forget to give Edge
 * Identity an early opportunity to resolve and cache the ECID before the first evaluation.
 *
 * <p>{@link #isIdentityReady} is used by {@link com.adobe.marketing.mobile.flags.FlagExtension
 * #readyForEvent} to hold flag API events until Edge Identity shared state is {@code SET} (when
 * Edge Identity is registered). Identity content is resolved at evaluation time via {@link
 * #fetchIdentityMap}.
 */
public final class EdgeIdentityFetcher implements FlagIdentityFetcher {

    /**
     * Returns the identity map from Edge Identity XDM shared state, or {@code null} when:
     *
     * <ul>
     *   <li>Edge Identity is not registered (no shared state entry).
     *   <li>Shared state is {@code PENDING} (Edge Identity not yet booted).
     *   <li>Shared state value is null or empty.
     *   <li>The {@code identityMap} wrapper is missing or contains no valid namespace entries.
     * </ul>
     *
     * @param extensionApi the extension API; must not be null
     * @param event the incoming flag request event for event-ordered state resolution
     * @return immutable namespace → identity entries map, or {@code null}
     */
    @Override
    @Nullable public Map<String, List<Map<String, Object>>> fetchIdentityMap(
            @NonNull final ExtensionApi extensionApi, @NonNull final Event event) {
        final SharedStateResult result = getEdgeIdentitySharedState(extensionApi, event);

        if (result == null || result.getStatus() != SharedStateStatus.SET) {
            return null;
        }

        final Map<String, Object> stateValue = result.getValue();
        if (stateValue == null || stateValue.isEmpty()) {
            return null;
        }

        return IdentityMapMarshaller.fromXDMStateMap(stateValue);
    }

    /**
     * Returns {@code true} when Edge Identity is absent or has published {@code SET} shared state.
     *
     * @see FlagIdentityFetcher#isIdentityReady(ExtensionApi, Event)
     */
    @Override
    public boolean isIdentityReady(
            @NonNull final ExtensionApi extensionApi, @NonNull final Event event) {
        final SharedStateResult result = getEdgeIdentitySharedState(extensionApi, event);
        if (result == null) {
            return true;
        }
        return result.getStatus() == SharedStateStatus.SET;
    }

    /**
     * Triggers Edge Identity resolution at startup without blocking the caller. Gives Edge Identity
     * an early chance to generate and persist the ECID so that the first evaluation call finds a
     * SET shared state rather than PENDING.
     */
    @Override
    public void warmUp() {
        Identity.getIdentities(identityMap -> {});
    }

    @Nullable private static SharedStateResult getEdgeIdentitySharedState(
            @NonNull final ExtensionApi extensionApi, @NonNull final Event event) {
        return extensionApi.getXDMSharedState(
                FlagConstants.EdgeIdentity.EXTENSION_NAME,
                event,
                false,
                SharedStateResolution.LAST_SET);
    }
}
