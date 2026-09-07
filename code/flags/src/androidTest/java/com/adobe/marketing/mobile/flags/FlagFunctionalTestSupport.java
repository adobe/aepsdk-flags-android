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

import static com.adobe.marketing.mobile.flags.MonitorExtension.EventSpec;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Functional-test helper for reading, writing, resetting and asserting against Event Hub events and
 * shared states. Not shipped in the AAR.
 */
final class FlagFunctionalTestSupport {
    private static final String TAG = "FlagFunctionalTestSupport";
    static final int WAIT_TIMEOUT_MS = 1000;
    static final int WAIT_EVENT_TIMEOUT_MS = 2000;
    /** Backoff between poll attempts in {@link #pollXdmSharedStateSet} and friends. */
    private static final int POLL_INTERVAL_MS = 100;

    static Application defaultApplication;

    private static final List<String> knownThreads = new ArrayList<>();

    static {
        knownThreads.add("pool");
        knownThreads.add("ADB");
    }

    private FlagFunctionalTestSupport() {}

    static final class SetupCoreRule implements TestRule {
        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    if (defaultApplication == null) {
                        Context context =
                                InstrumentationRegistry.getInstrumentation().getTargetContext();
                        defaultApplication =
                                Instrumentation.newApplication(CustomApplication.class, context);
                    }

                    MobileCore.setLogLevel(LoggingMode.VERBOSE);
                    MobileCore.setApplication(defaultApplication);

                    try {
                        base.evaluate();
                    } catch (Throwable e) {
                        Log.debug(
                                FlagFunctionalTestConstants.LOG_TAG,
                                "SetupCoreRule",
                                "Wait after test failure.");
                        throw e;
                    } finally {
                        Log.debug(
                                FlagFunctionalTestConstants.LOG_TAG,
                                "SetupCoreRule",
                                "Finished '" + description.getMethodName() + "'");
                        waitForThreads(5000);
                        resetMobileCore();
                        resetTestExpectations();
                    }
                }
            };
        }
    }

    static final class RegisterMonitorExtensionRule implements TestRule {
        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    MonitorExtension.registerExtension();
                    try {
                        base.evaluate();
                    } finally {
                        MonitorExtension.reset();
                    }
                }
            };
        }
    }

    static void waitForThreads(final int timeoutMillis) {
        int TEST_DEFAULT_TIMEOUT_MS = 1000;
        int TEST_DEFAULT_SLEEP_MS = 50;
        int TEST_INITIAL_SLEEP_MS = 100;

        long startTime = System.currentTimeMillis();
        int timeoutTestMillis = timeoutMillis > 0 ? timeoutMillis : TEST_DEFAULT_TIMEOUT_MS;
        int sleepTime = Math.min(timeoutTestMillis, TEST_DEFAULT_SLEEP_MS);

        sleep(TEST_INITIAL_SLEEP_MS);
        Set<Thread> threadSet = getEligibleThreads();

        while (threadSet.size() > 0
                && ((System.currentTimeMillis() - startTime) < timeoutTestMillis)) {
            for (Thread t : threadSet) {
                boolean done = false;
                boolean timedOut = false;
                while (!done && !timedOut) {
                    if (t.getState().equals(Thread.State.TERMINATED)
                            || t.getState().equals(Thread.State.TIMED_WAITING)
                            || t.getState().equals(Thread.State.WAITING)) {
                        done = true;
                    } else {
                        sleep(sleepTime);
                        timedOut = (System.currentTimeMillis() - startTime) > timeoutTestMillis;
                    }
                }
            }
            threadSet = getEligibleThreads();
        }
    }

    private static Set<Thread> getEligibleThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        Set<Thread> eligibleThreads = new HashSet<>();
        for (Thread t : threadSet) {
            if (isAppThread(t)
                    && !t.getState().equals(Thread.State.WAITING)
                    && !t.getState().equals(Thread.State.TERMINATED)
                    && !t.getState().equals(Thread.State.TIMED_WAITING)) {
                eligibleThreads.add(t);
            }
        }
        return eligibleThreads;
    }

    private static boolean isAppThread(final Thread t) {
        if (t.isDaemon()) return false;
        for (String prefix : knownThreads) {
            if (t.getName().startsWith(prefix)) return true;
        }
        return false;
    }

    static void resetTestExpectations() {
        Log.debug(
                FlagFunctionalTestConstants.LOG_TAG,
                TAG,
                "Resetting functional test expectations for events");
        MonitorExtension.reset();
    }

    static List<Event> getDispatchedEventsWith(final String type, final String source)
            throws InterruptedException {
        return getDispatchedEventsWith(type, source, WAIT_EVENT_TIMEOUT_MS);
    }

    static List<Event> getDispatchedEventsWith(final String type, final String source, int timeout)
            throws InterruptedException {
        EventSpec eventSpec = new EventSpec(source, type);

        Map<EventSpec, List<Event>> receivedEvents = MonitorExtension.getReceivedEvents();
        Map<EventSpec, ADBCountDownLatch> expectedEvents = MonitorExtension.getExpectedEvents();

        ADBCountDownLatch expectedEventLatch = expectedEvents.get(eventSpec);

        if (expectedEventLatch != null) {
            boolean awaitResult = expectedEventLatch.await(timeout, TimeUnit.MILLISECONDS);
            Assert.assertTrue(
                    "Timed out waiting for event type "
                            + eventSpec.type
                            + " and source "
                            + eventSpec.source,
                    awaitResult);
        } else {
            sleep(WAIT_TIMEOUT_MS);
        }

        return receivedEvents.containsKey(eventSpec)
                ? receivedEvents.get(eventSpec)
                : Collections.emptyList();
    }

    static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads Edge Identity (or other) XDM shared state via {@link MonitorExtension}.
     *
     * <p>{@link MobileCore#dispatchEvent(Event)} is asynchronous. This method registers an expected
     * monitor response, dispatches the request, then blocks on {@link #getDispatchedEventsWith}
     * until {@link MonitorExtension} captures the response event (or times out).
     *
     * @return shared state result, or {@code null} when no response is received in time
     */
    @Nullable static SharedStateResult getXdmSharedState(
            @NonNull final String stateOwner, final int timeoutMs) throws InterruptedException {
        final Map<String, Object> requestData = new HashMap<>();
        requestData.put(FlagFunctionalTestConstants.MonitorEventDataKeys.STATE_OWNER, stateOwner);

        final Event request =
                new Event.Builder(
                                "Get XDM Shared State Request",
                                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                                FlagFunctionalTestConstants.MonitorEventSource
                                        .XDM_SHARED_STATE_REQUEST)
                        .setEventData(requestData)
                        .build();

        MonitorExtension.setExpectedEvent(
                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_RESPONSE,
                1);
        MobileCore.dispatchEvent(request);

        final List<Event> responses =
                getDispatchedEventsWith(
                        FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                        FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_RESPONSE,
                        timeoutMs);

        if (responses.isEmpty()) {
            return null;
        }

        return parseXdmSharedStateResponse(responses.get(responses.size() - 1));
    }

    /**
     * Polls until XDM shared state for {@code stateOwner} reaches {@link SharedStateStatus#SET}, or
     * times out. Used only by androidTest functional tests.
     */
    @Nullable static SharedStateResult pollXdmSharedStateSet(
            @NonNull final String stateOwner, final int timeoutMs) throws InterruptedException {
        final long deadlineMs = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadlineMs) {
            final SharedStateResult result = getXdmSharedState(stateOwner, 500);
            if (result != null && result.getStatus() == SharedStateStatus.SET) {
                return result;
            }
            sleep(POLL_INTERVAL_MS);
        }
        return null;
    }

    @Nullable private static SharedStateResult parseXdmSharedStateResponse(final Event response) {
        final Map<String, Object> eventData = response.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            return null;
        }

        final String statusName =
                DataReader.optString(
                        eventData, FlagFunctionalTestConstants.MonitorEventDataKeys.STATUS, null);
        if (statusName == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> value =
                (Map<String, Object>)
                        eventData.get(FlagFunctionalTestConstants.MonitorEventDataKeys.VALUE);
        return new SharedStateResult(SharedStateStatus.valueOf(statusName), value);
    }

    /**
     * Reads extension shared state (non-XDM) via {@link MonitorExtension}.
     *
     * <p>See {@link #getXdmSharedState(String, int)} for the async dispatch + latch wait pattern.
     *
     * @return state value map, or {@code null} when no response is received in time
     */
    @Nullable static Map<String, Object> getSharedStateValue(
            @NonNull final String stateOwner, final int timeoutMs) throws InterruptedException {
        final Map<String, Object> requestData = new HashMap<>();
        requestData.put(FlagFunctionalTestConstants.MonitorEventDataKeys.STATE_OWNER, stateOwner);

        final Event request =
                new Event.Builder(
                                "Get Shared State Request",
                                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                                FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_REQUEST)
                        .setEventData(requestData)
                        .build();

        MonitorExtension.setExpectedEvent(
                FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_RESPONSE,
                1);
        MobileCore.dispatchEvent(request);

        final List<Event> responses =
                getDispatchedEventsWith(
                        FlagFunctionalTestConstants.MonitorEventType.MONITOR,
                        FlagFunctionalTestConstants.MonitorEventSource.SHARED_STATE_RESPONSE,
                        timeoutMs);

        if (responses.isEmpty()) {
            return null;
        }

        return responses.get(responses.size() - 1).getEventData();
    }

    /**
     * Polls until Flags extension shared state reports initialization complete ({@code ready} or
     * {@code failed}). Used only by androidTest functional tests.
     */
    @Nullable static Map<String, Object> pollFlagsInitializationComplete(final int timeoutMs)
            throws InterruptedException {
        final long deadlineMs = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadlineMs) {
            final Map<String, Object> state =
                    getSharedStateValue(FlagConstants.EXTENSION_NAME, 500);
            if (state != null) {
                final String status =
                        DataReader.optString(
                                state, FlagConstants.SharedState.INITIALIZATION_STATUS, null);
                if (FlagConstants.SharedState.STATUS_READY.equals(status)
                        || FlagConstants.SharedState.STATUS_FAILED.equals(status)) {
                    return state;
                }
            }
            sleep(POLL_INTERVAL_MS);
        }
        return null;
    }

    static final class CustomApplication extends Application {
        CustomApplication() {}
    }

    private static void resetMobileCore() {
        try {
            final Method resetMethod = MobileCore.class.getDeclaredMethod("resetSDK");
            resetMethod.setAccessible(true);
            resetMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset MobileCore for functional tests", e);
        }
    }
}
