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
import java.util.List;
import java.util.Map;

/**
 * Reads the current Edge Identity map for feature evaluation requests.
 *
 * <p>Implementations read Edge Identity synchronously from XDM shared state (event-ordered, no
 * blocking callback) and do not maintain a separate identity cache in the Flags extension.
 */
public interface FlagIdentityFetcher {

    /**
     * Returns the current identity map in XDM {@code identityMap} shape, or {@code null} when
     * identities are unavailable (Edge Identity not registered, shared state pending, or empty).
     *
     * <p>The call is synchronous and non-blocking: it reads the XDM shared state published by Edge
     * Identity for the given {@code event}, which is event-ordered relative to other extensions'
     * shared state reads.
     *
     * @param extensionApi the extension API used to read XDM shared state
     * @param event the incoming flag API event, used for event-ordered state resolution
     * @return namespace → identity entries, or {@code null}
     */
    @Nullable Map<String, List<Map<String, Object>>> fetchIdentityMap(
            @NonNull ExtensionApi extensionApi, @NonNull Event event);

    /**
     * Returns whether flag API events may be delivered for evaluation.
     *
     * <p>When Edge Identity is not registered (no XDM shared state entry), returns {@code true} so
     * evaluations without identity remain supported. When Edge Identity is registered, returns
     * {@code true} only after its XDM shared state is {@code SET}. Identity map content is read
     * separately via {@link #fetchIdentityMap}.
     */
    boolean isIdentityReady(@NonNull ExtensionApi extensionApi, @NonNull Event event);

    /** Optional warm-up hook called once at initialization; default is no-op. */
    default void warmUp() {}
}
