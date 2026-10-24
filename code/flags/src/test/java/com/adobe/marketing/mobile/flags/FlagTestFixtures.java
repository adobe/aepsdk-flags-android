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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolver;
import com.adobe.marketing.mobile.flags.engine.FlagClient;
import com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration;
import com.adobe.marketing.mobile.flags.internal.EdgeIdentityFetcher;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagIdentityFetcher;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Test factories and initialization helpers for Flags extension components. */
final class FlagTestFixtures {

    private FlagTestFixtures() {}

    @NonNull static Map<String, Object> validConfigData() {
        final Map<String, Object> config = new HashMap<>();
        config.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");
        config.put(FlagConstants.Configuration.FLAGS_SANDBOX, "prod");
        config.put(FlagConstants.Configuration.FLAGS_CLIENT_ID, "test-client");
        return config;
    }

    @NonNull static FlagClientManager createClientManager(@NonNull final ExtensionApi extensionApi) {
        return createClientManager(extensionApi, TestCallerThreadExecutorService.INSTANCE);
    }

    @NonNull static FlagClientManager createClientManager(
            @NonNull final ExtensionApi extensionApi, @NonNull final ExecutorService initExecutor) {
        return createClientManager(extensionApi, initExecutor, new EdgeIdentityFetcher());
    }

    @NonNull static FlagClientManager createClientManager(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final ExecutorService initExecutor,
            @NonNull final FlagIdentityFetcher identityFetcher) {
        final FeatureExposureQueue queue =
                ExposureQueueTestSupport.createDeterministicQueue(
                        events -> FlagEdgeHandler.dispatchExposureEvents(extensionApi, events));
        return createClientManager(extensionApi, initExecutor, queue, identityFetcher);
    }

    @NonNull static FlagClientManager createClientManager(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final ExecutorService initExecutor,
            @NonNull final FeatureExposureQueue exposureQueue) {
        return createClientManager(
                extensionApi, initExecutor, exposureQueue, new EdgeIdentityFetcher());
    }

    @NonNull static FlagClientManager createClientManager(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final ExecutorService initExecutor,
            @NonNull final FeatureExposureQueue exposureQueue,
            @NonNull final FlagIdentityFetcher identityFetcher) {
        final FlagClientManager clientManager =
                new FlagClientManager(extensionApi, identityFetcher);
        setField(clientManager, "initExecutor", initExecutor);
        setField(clientManager, "exposureQueue", exposureQueue);
        return clientManager;
    }

    @NonNull static FlagExtension createExtension(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final FlagClientManager clientManager) {
        return createExtension(extensionApi, getIdentityFetcher(clientManager), clientManager);
    }

    @NonNull static FlagExtension createExtension(
            @NonNull final ExtensionApi extensionApi,
            @NonNull final FlagIdentityFetcher identityFetcher,
            @NonNull final FlagClientManager clientManager) {
        try {
            final Constructor<FlagExtension> constructor =
                    FlagExtension.class.getDeclaredConstructor(ExtensionApi.class);
            constructor.setAccessible(true);
            final FlagExtension extension = constructor.newInstance(extensionApi);
            setField(extension, "identityFetcher", identityFetcher);
            setField(extension, "clientManager", clientManager);
            return extension;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to construct FlagExtension for tests", e);
        }
    }

    @NonNull static FlagExtension createExtension(@NonNull final ExtensionApi extensionApi) {
        final FlagIdentityFetcher identityFetcher = new EdgeIdentityFetcher();
        return createExtension(
                extensionApi,
                identityFetcher,
                createClientManager(
                        extensionApi, TestCallerThreadExecutorService.INSTANCE, identityFetcher));
    }

    @NonNull private static FlagIdentityFetcher getIdentityFetcher(
            @NonNull final FlagClientManager clientManager) {
        try {
            final Field identityFetcherField =
                    FlagClientManager.class.getDeclaredField("identityFetcher");
            identityFetcherField.setAccessible(true);
            return (FlagIdentityFetcher) identityFetcherField.get(clientManager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read FlagClientManager identityFetcher", e);
        }
    }

    static void initializeClientManager(
            @NonNull final FlagClientManager clientManager,
            @NonNull final FlagClient mockFlagClient) {
        initializeClientManager(clientManager, mockFlagClient, null);
    }

    static void initializeClientManager(
            @NonNull final FlagClientManager clientManager,
            @NonNull final FlagClient mockFlagClient,
            @Nullable final SharedStateResolver resolver) {
        when(mockFlagClient.isInitialized()).thenReturn(true);
        when(mockFlagClient.getClientId()).thenReturn("test-client");

        final SharedStateResolver initResolver =
                resolver != null ? resolver : Mockito.mock(SharedStateResolver.class);

        try (MockedStatic<FlagClient> flagClientStatic = mockStatic(FlagClient.class)) {
            flagClientStatic
                    .when(() -> FlagClient.create(any(FlagConfiguration.class)))
                    .thenReturn(mockFlagClient);
            clientManager.startAsyncInitialization(validConfigData(), initResolver);
        }
    }

    static void markClientNotReady(@NonNull final FlagClient mockFlagClient) {
        when(mockFlagClient.isInitialized()).thenReturn(false);
    }

    @NonNull static FlagClientManager getClientManager(@NonNull final FlagExtension extension) {
        try {
            final Field clientManagerField = FlagExtension.class.getDeclaredField("clientManager");
            clientManagerField.setAccessible(true);
            return (FlagClientManager) clientManagerField.get(extension);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read FlagExtension clientManager", e);
        }
    }

    static void initializeExtension(
            @NonNull final FlagExtension extension, @NonNull final FlagClient mockFlagClient) {
        initializeClientManager(getClientManager(extension), mockFlagClient);
    }

    private static void setField(
            @NonNull final Object target,
            @NonNull final String fieldName,
            @Nullable final Object value) {
        try {
            final Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set field " + fieldName, e);
        }
    }
}
