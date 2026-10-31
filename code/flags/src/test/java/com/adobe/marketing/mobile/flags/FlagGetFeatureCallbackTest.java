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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Verifies what callers of {@link Flag#getFeature} receive for each response shape. */
public class FlagGetFeatureCallbackTest {

    private MockedStatic<MobileCore> mobileCoreStatic;

    @Before
    public void setup() {
        mobileCoreStatic = Mockito.mockStatic(MobileCore.class);
    }

    @After
    public void tearDown() {
        mobileCoreStatic.close();
    }

    private void respondWith(final Map<String, Object> responseData) {
        mobileCoreStatic
                .when(
                        () ->
                                MobileCore.dispatchEventWithResponseCallback(
                                        any(Event.class), anyLong(), any()))
                .thenAnswer(
                        invocation -> {
                            final AdobeCallbackWithError<Event> cb = invocation.getArgument(2);
                            final Event response =
                                    new Event.Builder(
                                                    "Flags Response",
                                                    FlagConstants.EventType.FLAGS,
                                                    FlagConstants.EventSource.RESPONSE_CONTENT)
                                            .setEventData(responseData)
                                            .build();
                            cb.call(response);
                            return null;
                        });
    }

    private static FeatureEvaluationContext emptyContext() {
        return FeatureEvaluationContext.builder().build();
    }

    @Test
    public void getFeature_emptyResponse_deliversNullWithoutError() {
        respondWith(new HashMap<>());
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<FeatureEvaluationResult> result = new AtomicReference<>();
        final AtomicBoolean failed = new AtomicBoolean(false);

        Flag.getFeature(
                "unknown-flag",
                emptyContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(final AdobeError error) {
                        failed.set(true);
                    }

                    @Override
                    public void call(final FeatureEvaluationResult value) {
                        calls.incrementAndGet();
                        result.set(value);
                    }
                });

        assertEquals(1, calls.get());
        assertNull(result.get());
        assertTrue("Not qualified must not be reported as an error", !failed.get());
    }

    @Test
    public void getFeature_emptyResponse_plainCallbackStillReceivesNull() {
        respondWith(new HashMap<>());
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<FeatureEvaluationResult> result = new AtomicReference<>();

        Flag.getFeature(
                "unknown-flag",
                emptyContext(),
                new AdobeCallback<FeatureEvaluationResult>() {
                    @Override
                    public void call(final FeatureEvaluationResult value) {
                        calls.incrementAndGet();
                        result.set(value);
                    }
                });

        assertEquals("Plain callback must be invoked exactly once", 1, calls.get());
        assertNull(result.get());
    }

    @Test
    public void getFeature_featurePayload_deliversResult() {
        final Map<String, Object> feature = new HashMap<>();
        feature.put(FlagConstants.EventDataKeys.ID, 42);
        feature.put(FlagConstants.EventDataKeys.KEY, "dark-mode");
        final Map<String, Object> response = new HashMap<>();
        response.put(FlagConstants.EventDataKeys.FEATURE, feature);
        respondWith(response);
        final AtomicReference<FeatureEvaluationResult> result = new AtomicReference<>();

        Flag.getFeature(
                "dark-mode",
                emptyContext(),
                new AdobeCallback<FeatureEvaluationResult>() {
                    @Override
                    public void call(final FeatureEvaluationResult value) {
                        result.set(value);
                    }
                });

        assertNotNull(result.get());
        assertEquals(42, result.get().getId());
        assertEquals("dark-mode", result.get().getKey());
    }

    @Test
    public void getFeature_responseError_failsWithError() {
        final Map<String, Object> response = new HashMap<>();
        response.put(
                FlagConstants.EventDataKeys.RESPONSE_ERROR,
                AdobeError.UNEXPECTED_ERROR.getErrorCode());
        respondWith(response);
        final AtomicReference<AdobeError> error = new AtomicReference<>();
        final AtomicInteger successCalls = new AtomicInteger();

        Flag.getFeature(
                "dark-mode",
                emptyContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(final AdobeError e) {
                        error.set(e);
                    }

                    @Override
                    public void call(final FeatureEvaluationResult value) {
                        successCalls.incrementAndGet();
                    }
                });

        assertEquals(AdobeError.UNEXPECTED_ERROR, error.get());
        assertEquals(0, successCalls.get());
    }

    @Test
    public void getFeature_responseError_plainCallbackStaysSilent() {
        final Map<String, Object> response = new HashMap<>();
        response.put(
                FlagConstants.EventDataKeys.RESPONSE_ERROR,
                AdobeError.UNEXPECTED_ERROR.getErrorCode());
        respondWith(response);
        final AtomicInteger calls = new AtomicInteger();

        Flag.getFeature(
                "dark-mode",
                emptyContext(),
                new AdobeCallback<FeatureEvaluationResult>() {
                    @Override
                    public void call(final FeatureEvaluationResult value) {
                        calls.incrementAndGet();
                    }
                });

        // AEP convention: plain callbacks are not invoked on real errors.
        assertEquals(0, calls.get());
    }

    @Test
    public void getFeature_malformedFeaturePayload_failsWithError() {
        final Map<String, Object> feature = new HashMap<>();
        feature.put(FlagConstants.EventDataKeys.ID, 42);
        // "key" intentionally missing.
        final Map<String, Object> response = new HashMap<>();
        response.put(FlagConstants.EventDataKeys.FEATURE, feature);
        respondWith(response);
        final AtomicReference<AdobeError> error = new AtomicReference<>();

        Flag.getFeature(
                "dark-mode",
                emptyContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(final AdobeError e) {
                        error.set(e);
                    }

                    @Override
                    public void call(final FeatureEvaluationResult value) {}
                });

        assertEquals(AdobeError.UNEXPECTED_ERROR, error.get());
    }
}
