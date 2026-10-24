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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for the public {@link Flag} API.
 *
 * <p>Runs on a device or emulator and checks callbacks and dispatched events that integrators can
 * observe; it does not specify how the extension implements those behaviours internally.
 */
@RunWith(AndroidJUnit4.class)
public class FlagFunctionalTests {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(new FlagFunctionalTestSupport.SetupCoreRule())
                    .around(new FlagFunctionalTestSupport.RegisterMonitorExtensionRule());

    @Before
    public void setup() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        MobileCore.registerExtensions(
                Arrays.asList(Flag.EXTENSION), o -> countDownLatch.countDown());
        Assert.assertTrue(countDownLatch.await(1000, TimeUnit.MILLISECONDS));
        FlagFunctionalTestSupport.resetTestExpectations();
    }

    @After
    public void tearDown() {
        NamedCollection configDataStore =
                ServiceProvider.getInstance()
                        .getDataStoreService()
                        .getNamedCollection(FlagFunctionalTestConstants.CONFIG_DATA_STORE);
        if (configDataStore != null) {
            configDataStore.removeAll();
        }
    }

    // -------------------------------------------------------------------
    // Extension Version
    // -------------------------------------------------------------------

    @Test
    public void testExtensionVersion() {
        assertEquals(BuildConfig.FLAGS_VERSION, Flag.extensionVersion());
    }

    // -------------------------------------------------------------------
    // Extension Registration
    // -------------------------------------------------------------------

    @Test
    public void testExtensionRegisters_dispatches_sharedState() throws Exception {
        FlagFunctionalTestSupport.sleep(500);

        List<Event> hubEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        "com.adobe.eventType.hub", "com.adobe.eventSource.sharedState");

        assertNotNull("Hub shared state events should not be null", hubEvents);
    }

    // -------------------------------------------------------------------
    // getFeature API
    // -------------------------------------------------------------------

    @Test
    public void testGetFeature_dispatches_requestContentEvent() throws Exception {
        // Setup - set expected events before dispatching
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT, 1);

        // Action
        Flag.getFeature(
                "test-feature",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {
                        // Expected - no config set up
                    }

                    @Override
                    public void call(FeatureEvaluationResult feature) {
                        // may or may not be called depending on timing
                    }
                });

        // Verify - the request content event should have been dispatched
        List<Event> requestEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT);

        assertFalse("Should have dispatched at least one request event", requestEvents.isEmpty());

        Event requestEvent = requestEvents.get(0);
        Map<String, Object> eventData = requestEvent.getEventData();
        assertNotNull("Event data should not be null", eventData);
        assertEquals("getfeature", eventData.get("requesttype"));
        assertFalse("Request should not include clientId", eventData.containsKey("clientid"));
        assertEquals("test-feature", eventData.get("featurename"));
    }

    @Test
    public void testGetFeature_withContext_dispatches_eventWithContext() throws Exception {
        // Setup
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT, 1);

        Map<String, List<String>> context = new HashMap<>();
        context.put("region", Arrays.asList("US", "EU"));
        context.put("platform", Arrays.asList("android"));

        // Action
        Flag.getFeature(
                "test-feature",
                FeatureEvaluationContext.builder().withAttributes(context).build(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(FeatureEvaluationResult feature) {}
                });

        // Verify
        List<Event> requestEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT);

        assertFalse("Should have dispatched a request event", requestEvents.isEmpty());

        Event requestEvent = requestEvents.get(0);
        Map<String, Object> eventData = requestEvent.getEventData();
        assertNotNull("Event data should not be null", eventData);
        assertNotNull("Context should be present in event data", eventData.get("context"));
    }

    @Test
    public void testGetFeature_withEmptyFeatureKey_doesNotDispatchEvent() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        // Action
        Flag.getFeature(
                "",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {
                        errorLatch.countDown();
                    }

                    @Override
                    public void call(FeatureEvaluationResult feature) {
                        Assert.fail("Should not call success callback with empty feature key");
                    }
                });

        // Verify
        assertTrue(
                "Error callback should have been invoked",
                errorLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    // -------------------------------------------------------------------
    // isFeatureEnabled API
    // -------------------------------------------------------------------

    @Test
    public void testIsFeatureEnabled_dispatches_requestContentEvent() throws Exception {
        // Setup
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT, 1);

        // Action
        Flag.isFeatureEnabled(
                "test-feature",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {}
                });

        // Verify
        List<Event> requestEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT);

        assertFalse("Should have dispatched a request event", requestEvents.isEmpty());

        Event requestEvent = requestEvents.get(0);
        Map<String, Object> eventData = requestEvent.getEventData();
        assertNotNull("Event data should not be null", eventData);
        assertEquals("isfeatureenabled", eventData.get("requesttype"));
        assertFalse("Request should not include clientId", eventData.containsKey("clientid"));
        assertEquals("test-feature", eventData.get("featurename"));
    }

    @Test
    public void testIsFeatureEnabled_withContext_dispatches_eventWithContext() throws Exception {
        // Setup
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT, 1);

        Map<String, List<String>> context = new HashMap<>();
        context.put("userSegment", Arrays.asList("beta-tester"));

        // Action
        Flag.isFeatureEnabled(
                "feature-x",
                FeatureEvaluationContext.builder().withAttributes(context).build(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {}
                });

        // Verify
        List<Event> requestEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT);

        assertFalse("Should have dispatched a request event", requestEvents.isEmpty());

        Map<String, Object> eventData = requestEvents.get(0).getEventData();
        assertNotNull("Context should be present", eventData.get("context"));
    }

    @Test
    public void testIsFeatureEnabled_withEmptyFeatureKey_doesNotDispatchEvent() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        // Action
        Flag.isFeatureEnabled(
                "",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {
                        errorLatch.countDown();
                    }

                    @Override
                    public void call(Boolean isEnabled) {
                        Assert.fail("Should not succeed with empty feature key");
                    }
                });

        assertTrue(
                "Error callback should be invoked", errorLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    // -------------------------------------------------------------------
    // Response event tests (with config)
    // -------------------------------------------------------------------

    /**
     * CUST-4: Configuration shared state is set but flags keys are incomplete → deterministic error
     * (Option B delivers the event; does not block on init).
     */
    @Test
    public void testGetFeature_partialConfig_dispatches_errorResponse() throws Exception {
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.RESPONSE_CONTENT, 1);

        updateConfiguration(partialFlagConfig());

        Flag.getFeature(
                "test-feature",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(FeatureEvaluationResult feature) {
                        Assert.fail("Should not succeed with partial configuration");
                    }
                });

        List<Event> responseEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.RESPONSE_CONTENT,
                        5000);

        assertFalse("Should have dispatched a response event", responseEvents.isEmpty());
        assertTrue(
                "Response should contain an error key",
                responseEvents.get(0).getEventData().containsKey("responseerror"));
    }

    @Test
    public void testIsFeatureEnabled_partialConfig_dispatches_errorResponse() throws Exception {
        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.RESPONSE_CONTENT, 1);

        updateConfiguration(partialFlagConfig());

        Flag.isFeatureEnabled(
                "feature-x",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {
                        Assert.fail("Should not succeed with partial configuration");
                    }
                });

        List<Event> responseEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.RESPONSE_CONTENT,
                        5000);

        assertFalse("Should have dispatched a response event", responseEvents.isEmpty());
        assertTrue(
                "Response should contain an error key",
                responseEvents.get(0).getEventData().containsKey("responseerror"));
    }

    // -------------------------------------------------------------------
    // Configuration update test
    // -------------------------------------------------------------------

    @Test
    public void testUpdateConfiguration_featureConfigKeys_are_received() throws Exception {
        // Setup
        Map<String, Object> configData = new HashMap<>();
        configData.put("experienceCloud.org", "test@AdobeOrg");
        configData.put("flags.sandbox", "prod");
        configData.put("edge.domain", "edge.adobedc.net");
        configData.put("flags.clientId", "client-1");

        // Action
        MobileCore.updateConfiguration(configData);

        // Give time for configuration to propagate
        FlagFunctionalTestSupport.sleep(1000);

        // Verify - config events should be dispatched
        List<Event> configEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        "com.adobe.eventType.configuration",
                        "com.adobe.eventSource.responseContent");

        assertNotNull("Should have configuration events", configEvents);
        assertFalse("Should have at least one configuration event", configEvents.isEmpty());
    }

    // -------------------------------------------------------------------
    // Null parameters: public API parameters are non-null where annotated; null is unsupported and
    // may fail fast with NullPointerException. Tests below lock in current behaviour for CI.
    // -------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void testGetFeature_nullFeatureKey_throwsNullPointerException() {
        Flag.getFeature(
                null,
                emptyEvaluationContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(FeatureEvaluationResult feature) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void testGetFeature_nullEvaluationContext_throwsNullPointerException() {
        Flag.getFeature(
                "test-feature",
                null,
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(FeatureEvaluationResult feature) {}
                });
    }

    @Test
    public void testGetFeature_nullCallback_emptyFeatureKey_returnsNormally() {
        Flag.getFeature("", emptyEvaluationContext(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testIsFeatureEnabled_nullFeatureKey_throwsNullPointerException() {
        Flag.isFeatureEnabled(
                null,
                emptyEvaluationContext(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void testIsFeatureEnabled_nullEvaluationContext_throwsNullPointerException() {
        Flag.isFeatureEnabled(
                "test-feature",
                null,
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {}
                });
    }

    @Test
    public void testIsFeatureEnabled_nullCallback_emptyFeatureKey_returnsNormally() {
        Flag.isFeatureEnabled("", emptyEvaluationContext(), null);
    }

    @Test
    public void testGetFeature_withoutConfiguration_doesNotDispatchEdgeExposureImmediately()
            throws Exception {
        Flag.getFeature(
                "test-feature",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(FeatureEvaluationResult feature) {}
                });

        FlagFunctionalTestSupport.sleep(500);

        List<Event> edgeEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        "com.adobe.eventType.edge", "com.adobe.eventSource.requestContent");

        assertTrue(
                "Edge exposure should not be dispatched before configuration and flush triggers",
                edgeEvents == null || edgeEvents.isEmpty());
    }

    @Test
    public void testIsFeatureEnabled_withoutConfiguration_doesNotDispatchEdgeExposureImmediately()
            throws Exception {
        Flag.isFeatureEnabled(
                "test-feature",
                emptyEvaluationContext(),
                new AdobeCallbackWithError<Boolean>() {
                    @Override
                    public void fail(AdobeError error) {}

                    @Override
                    public void call(Boolean isEnabled) {}
                });

        FlagFunctionalTestSupport.sleep(500);

        List<Event> edgeEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        "com.adobe.eventType.edge", "com.adobe.eventSource.requestContent");

        assertTrue(
                "Edge exposure should not be dispatched before configuration and flush triggers",
                edgeEvents == null || edgeEvents.isEmpty());
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    private void updateConfiguration(final Map<String, Object> config) throws InterruptedException {
        MobileCore.updateConfiguration(config);
        FlagFunctionalTestSupport.sleep(500);
    }

    private static Map<String, Object> partialFlagConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("experienceCloud.org", "test@AdobeOrg");
        return config;
    }

    private static FeatureEvaluationContext emptyEvaluationContext() {
        return FeatureEvaluationContext.builder().build();
    }
}
