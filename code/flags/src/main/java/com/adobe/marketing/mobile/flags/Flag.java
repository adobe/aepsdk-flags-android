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
import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.flags.internal.FlagResponseMapper;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public entry point for feature flag evaluation APIs.
 *
 * <p>Provides APIs to evaluate feature flags through the Adobe Experience Platform Mobile SDK.
 * Register {@link #EXTENSION} with {@code MobileCore} before calling these methods.
 */
public final class Flag {

    /**
     * Extension class reference for registration with {@code MobileCore.registerExtensions}.
     *
     * @see com.adobe.marketing.mobile.MobileCore#registerExtensions
     */
    public static final Class<? extends Extension> EXTENSION = FlagExtension.class;

    private static final String SELF_TAG = "Flag";

    private Flag() {}

    /**
     * Returns the installed version of this extension.
     *
     * @return {@link String} semantic version of this extension (for example {@code "1.0.0"})
     */
    @NonNull public static String extensionVersion() {
        return BuildConfig.FLAGS_VERSION;
    }

    /**
     * Evaluates a feature for the given key and returns the full evaluation result when a match
     * exists.
     *
     * @param featureKey {@link String} key of the feature to evaluate
     * @param evaluationContext {@link FeatureEvaluationContext} optional targeting attributes
     * @param callback {@link AdobeCallback} invoked with {@link FeatureEvaluationResult} when the
     *     request succeeds; the value may be {@code null} when no matching feature exists. When an
     *     {@link AdobeCallbackWithError} is provided, an {@link AdobeError} may be returned on
     *     validation failure, extension or transport errors, an empty or malformed response, or if
     *     the default response timeout (10000&nbsp;ms) is reached before a response is received
     */
    public static void getFeature(
            @NonNull final String featureKey,
            @NonNull final FeatureEvaluationContext evaluationContext,
            @NonNull final AdobeCallback<FeatureEvaluationResult> callback) {

        dispatchGetFeatureRequest(featureKey, evaluationContext.getAttributes(), callback);
    }

    private static void dispatchGetFeatureRequest(
            @NonNull final String featureKey,
            @Nullable final Map<String, List<String>> context,
            @NonNull final AdobeCallback<FeatureEvaluationResult> callback) {

        if (featureKey.isEmpty()) {
            Log.warning(
                    FlagConstants.LOG_TAG, SELF_TAG, "Cannot get feature, feature key is empty.");
            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
            return;
        }

        final Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureKey);
        if (context != null) {
            eventData.put(FlagConstants.EventDataKeys.CONTEXT, context);
        }

        final Event event =
                new Event.Builder(
                                FlagConstants.EventNames.GET_FEATURE_REQUEST,
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        MobileCore.dispatchEventWithResponseCallback(
                event,
                FlagConstants.API_TIMEOUT_MS,
                new AdobeCallbackWithError<Event>() {
                    @Override
                    public void fail(final AdobeError adobeError) {
                        failWithError(callback, adobeError);
                    }

                    @Override
                    public void call(final Event responseEvent) {
                        final Map<String, Object> responseData = responseEvent.getEventData();
                        if (responseData == null || responseData.isEmpty()) {
                            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
                            return;
                        }

                        if (responseData.containsKey(FlagConstants.EventDataKeys.RESPONSE_ERROR)) {
                            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
                            return;
                        }

                        try {
                            @SuppressWarnings("unchecked")
                            final Map<String, Object> feature =
                                    (Map<String, Object>)
                                            responseData.get(FlagConstants.EventDataKeys.FEATURE);
                            callback.call(FlagResponseMapper.toFeatureEvaluationResult(feature));
                        } catch (Exception e) {
                            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
                        }
                    }
                });
    }

    /**
     * Evaluates whether a feature is enabled for the given key and evaluation context.
     *
     * @param featureKey {@link String} key of the feature to evaluate
     * @param evaluationContext {@link FeatureEvaluationContext} optional targeting attributes
     * @param callback {@link AdobeCallback} invoked with {@code true} if the feature is enabled,
     *     {@code false} otherwise. When an {@link AdobeCallbackWithError} is provided, an {@link
     *     AdobeError} may be returned on validation failure, extension or transport errors, an
     *     empty or malformed response, or if the default response timeout (10000&nbsp;ms) is
     *     reached before a response is received
     */
    public static void isFeatureEnabled(
            @NonNull final String featureKey,
            @NonNull final FeatureEvaluationContext evaluationContext,
            @NonNull final AdobeCallback<Boolean> callback) {

        dispatchIsFeatureEnabledRequest(featureKey, evaluationContext.getAttributes(), callback);
    }

    private static void dispatchIsFeatureEnabledRequest(
            @NonNull final String featureKey,
            @Nullable final Map<String, List<String>> context,
            @NonNull final AdobeCallback<Boolean> callback) {

        if (featureKey.isEmpty()) {
            Log.warning(
                    FlagConstants.LOG_TAG,
                    SELF_TAG,
                    "Cannot check feature enabled, feature key is empty.");
            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
            return;
        }

        final Map<String, Object> eventData = new HashMap<>();
        eventData.put(
                FlagConstants.EventDataKeys.REQUEST_TYPE,
                FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
        eventData.put(FlagConstants.EventDataKeys.FEATURE_NAME, featureKey);
        if (context != null) {
            eventData.put(FlagConstants.EventDataKeys.CONTEXT, context);
        }

        final Event event =
                new Event.Builder(
                                FlagConstants.EventNames.IS_FEATURE_ENABLED_REQUEST,
                                FlagConstants.EventType.FLAGS,
                                FlagConstants.EventSource.REQUEST_CONTENT)
                        .setEventData(eventData)
                        .build();

        MobileCore.dispatchEventWithResponseCallback(
                event,
                FlagConstants.API_TIMEOUT_MS,
                new AdobeCallbackWithError<Event>() {
                    @Override
                    public void fail(final AdobeError adobeError) {
                        failWithError(callback, adobeError);
                    }

                    @Override
                    public void call(final Event responseEvent) {
                        final Map<String, Object> responseData = responseEvent.getEventData();
                        if (responseData == null || responseData.isEmpty()) {
                            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
                            return;
                        }

                        if (responseData.containsKey(FlagConstants.EventDataKeys.RESPONSE_ERROR)) {
                            failWithError(callback, AdobeError.UNEXPECTED_ERROR);
                            return;
                        }

                        final boolean isEnabled =
                                DataReader.optBoolean(
                                        responseData,
                                        FlagConstants.EventDataKeys.IS_ENABLED,
                                        false);

                        callback.call(isEnabled);
                    }
                });
    }

    /**
     * Invokes fail method with the provided error if the callback is an instance of {@link
     * AdobeCallbackWithError}.
     */
    private static void failWithError(final AdobeCallback<?> callback, final AdobeError error) {
        final AdobeCallbackWithError<?> callbackWithError =
                callback instanceof AdobeCallbackWithError
                        ? (AdobeCallbackWithError<?>) callback
                        : null;

        if (callbackWithError != null) {
            callbackWithError.fail(error);
        }
    }
}
