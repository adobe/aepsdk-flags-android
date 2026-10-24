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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResolver;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.flags.engine.models.AppState;
import com.adobe.marketing.mobile.flags.internal.EdgeIdentityFetcher;
import com.adobe.marketing.mobile.flags.internal.FlagConfigurationProvider;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagIdentityFetcher;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.Map;

/** Extension implementation for feature flag evaluation APIs. */
class FlagExtension extends Extension {

    private static final String SELF_TAG = "FlagExtension";

    private final FlagClientManager clientManager;
    private final FlagIdentityFetcher identityFetcher;

    protected FlagExtension(final ExtensionApi extensionApi) {
        super(extensionApi);
        this.identityFetcher = new EdgeIdentityFetcher();
        this.clientManager = new FlagClientManager(extensionApi, identityFetcher);
    }

    @Override
    protected void onRegistered() {
        getApi().registerEventListener(
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT,
                        this::handleFlagRequestContent);

        getApi().registerEventListener(
                        EventType.CONFIGURATION,
                        EventSource.RESPONSE_CONTENT,
                        this::handleConfigurationResponse);

        getApi().registerEventListener(
                        EventType.GENERIC_LIFECYCLE,
                        EventSource.REQUEST_CONTENT,
                        this::handleLifecycleEvent);

        final Map<String, Object> configData = getConfigurationData(null);
        if (configData != null && !configData.isEmpty()) {
            tryStartInitialization(null, configData);
        }
    }

    @Override
    protected void onUnregistered() {
        clientManager.closeClient();
    }

    @Override
    public boolean readyForEvent(@NonNull final Event event) {
        if (!isFlagApiRequest(event)) {
            return true;
        }

        if (!isConfigurationReady(event)) {
            return false;
        }

        final Map<String, Object> configData = getConfigurationData(event);
        if (configData == null || !FlagConfigurationProvider.hasRequiredConfig(configData)) {
            return true;
        }

        if (!isFlagInitializationComplete(event)) {
            tryStartInitialization(event, configData);
            return false;
        }

        // Hold flag API events while Edge Identity shared state is PENDING (when registered).
        // An empty identityMap after SET is valid; identity is read at evaluation time and may be
        // omitted from GetFeatureRequest when unavailable.
        return identityFetcher.isIdentityReady(getApi(), event);
    }

    @NonNull @Override
    protected String getName() {
        return FlagConstants.EXTENSION_NAME;
    }

    @NonNull @Override
    protected String getVersion() {
        return BuildConfig.FLAGS_VERSION;
    }

    @NonNull @Override
    protected String getFriendlyName() {
        return FlagConstants.FRIENDLY_NAME;
    }

    void handleConfigurationResponse(@NonNull final Event event) {
        final Map<String, Object> configData = event.getEventData();
        if (configData == null || configData.isEmpty()) {
            return;
        }

        if (clientManager.isClientReady()) {
            return;
        }

        tryStartInitialization(event, configData);
    }

    void handleFlagRequestContent(@NonNull final Event event) {
        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            return;
        }

        if (clientManager.isClientReady()) {
            clientManager.processRequestEvent(event);
        } else {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "handleFlagRequestContent - Feature SDK client is not ready.");
            clientManager.dispatchErrorResponse(event, AdobeError.UNEXPECTED_ERROR);
        }
    }

    void handleLifecycleEvent(@NonNull final Event event) {
        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null) {
            return;
        }

        final String action =
                DataReader.optString(eventData, FlagConstants.Lifecycle.ACTION_KEY, null);
        if (action == null) {
            return;
        }

        if (FlagConstants.Lifecycle.ACTION_START.equals(action)) {
            clientManager.handleAppStateChange(AppState.FOREGROUND);
        } else if (FlagConstants.Lifecycle.ACTION_PAUSE.equals(action)) {
            clientManager.handleAppStateChange(AppState.BACKGROUND);
        }
    }

    private void tryStartInitialization(
            @Nullable final Event event, @NonNull final Map<String, Object> configData) {
        if (clientManager.isClientReady() || clientManager.isInitializationInProgress()) {
            return;
        }

        if (!FlagConfigurationProvider.hasRequiredConfig(configData)) {
            return;
        }

        final SharedStateResolver resolver = getApi().createPendingSharedState(event);
        if (resolver == null) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "tryStartInitialization - Failed to create pending shared state.");
            return;
        }

        clientManager.startAsyncInitialization(configData, resolver);
    }

    private static boolean isFlagApiRequest(@NonNull final Event event) {
        return FlagConstants.EventType.FLAGS.equalsIgnoreCase(event.getType())
                && FlagConstants.EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource());
    }

    private boolean isConfigurationReady(@Nullable final Event event) {
        final SharedStateResult configurationSharedState =
                getApi().getSharedState(
                                FlagConstants.Configuration.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.ANY);
        return configurationSharedState != null
                && configurationSharedState.getStatus() == SharedStateStatus.SET;
    }

    @Nullable private Map<String, Object> getConfigurationData(@Nullable final Event event) {
        final SharedStateResult configurationSharedState =
                getApi().getSharedState(
                                FlagConstants.Configuration.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.ANY);
        return configurationSharedState != null ? configurationSharedState.getValue() : null;
    }

    private boolean isFlagInitializationComplete(@NonNull final Event event) {
        final SharedStateResult flagSharedState =
                getApi().getSharedState(
                                FlagConstants.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.LAST_SET);
        if (flagSharedState == null || flagSharedState.getStatus() != SharedStateStatus.SET) {
            return false;
        }

        final Map<String, Object> stateData = flagSharedState.getValue();
        if (stateData == null || stateData.isEmpty()) {
            return false;
        }

        final String status =
                DataReader.optString(
                        stateData, FlagConstants.SharedState.INITIALIZATION_STATUS, null);
        return FlagConstants.SharedState.STATUS_READY.equals(status)
                || FlagConstants.SharedState.STATUS_FAILED.equals(status);
    }
}
