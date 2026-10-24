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
import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.Map;

/** Resolves Data Collection configuration into a {@link FlagConfiguration}. */
public final class FlagConfigurationProvider {

    private static final String SELF_TAG = "FlagConfigurationProvider";

    private FlagConfigurationProvider() {}

    /**
     * Creates a {@link FlagConfiguration} from configuration data.
     *
     * @param configData configuration data from Data Collection.
     * @return a {@link FlagConfiguration} instance, or null if configuration is incomplete.
     */
    @Nullable public static FlagConfiguration buildConfiguration(
            @NonNull final Map<String, Object> configData) {
        try {
            final String edgeDomain = resolveEdgeDomain(configData);

            final String imsOrg =
                    DataReader.optString(
                            configData, FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, null);
            final String sandbox =
                    DataReader.optString(
                            configData, FlagConstants.Configuration.FLAGS_SANDBOX, null);
            final String clientId =
                    DataReader.optString(
                            configData, FlagConstants.Configuration.FLAGS_CLIENT_ID, null);

            if (isNullOrEmpty(imsOrg) || isNullOrEmpty(sandbox) || isNullOrEmpty(clientId)) {
                Log.debug(
                        FlagConstants.LOG_TAG,
                        SELF_TAG,
                        "buildConfiguration - Missing required config"
                                + " (imsOrg=%b, sandbox=%b, clientId=%b).",
                        imsOrg != null,
                        sandbox != null,
                        clientId != null);
                return null;
            }

            return FlagConfiguration.builder()
                    .edgeDomain(edgeDomain)
                    .imsOrg(imsOrg)
                    .sandboxName(sandbox)
                    .clientId(clientId)
                    .build();

        } catch (FlagInitException e) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "buildConfiguration - Failed to build configuration: %s",
                    e.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Resolves the configured service domain, falling back to the default when the configured value
     * is missing or blank.
     *
     * @param configData configuration data from Data Collection.
     * @return a non-empty service domain.
     */
    @NonNull public static String resolveEdgeDomain(@NonNull final Map<String, Object> configData) {
        final String configuredDomain =
                DataReader.optString(configData, FlagConstants.Configuration.EDGE_DOMAIN, null);
        if (configuredDomain == null) {
            Log.debug(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "resolveEdgeDomain - edge.domain is missing; using default domain %s.",
                    FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN);
            return FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN;
        }

        final String trimmedDomain = configuredDomain.trim();
        if (trimmedDomain.isEmpty()) {
            Log.debug(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "resolveEdgeDomain - edge.domain is blank; using default domain %s.",
                    FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN);
            return FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN;
        }

        return trimmedDomain;
    }

    /**
     * Checks whether the required configuration keys are present.
     *
     * @param configData configuration data from Data Collection.
     * @return {@code true} if imsOrg, sandbox, and clientId are all present and non-empty.
     */
    public static boolean hasRequiredConfig(@NonNull final Map<String, Object> configData) {
        final String imsOrg =
                DataReader.optString(
                        configData, FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, null);
        final String sandbox =
                DataReader.optString(configData, FlagConstants.Configuration.FLAGS_SANDBOX, null);
        final String clientId =
                DataReader.optString(configData, FlagConstants.Configuration.FLAGS_CLIENT_ID, null);
        return !isNullOrEmpty(imsOrg) && !isNullOrEmpty(sandbox) && !isNullOrEmpty(clientId);
    }

    private static boolean isNullOrEmpty(@Nullable final String value) {
        return value == null || value.isEmpty();
    }
}
