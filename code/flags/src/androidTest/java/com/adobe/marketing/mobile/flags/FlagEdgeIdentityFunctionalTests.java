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
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.edge.identity.IdentityItem;
import com.adobe.marketing.mobile.edge.identity.IdentityMap;
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
 * Instrumented tests for the Flags + Edge Identity integration path.
 *
 * <p>Unlike {@link FlagFunctionalTests}, these register {@link Identity#EXTENSION} alongside {@link
 * Flag#EXTENSION} and assert that Edge Identity publishes XDM shared state in the wrapped {@code
 * identityMap} shape that {@code EdgeIdentityFetcher} reads during evaluation.
 */
@RunWith(AndroidJUnit4.class)
public class FlagEdgeIdentityFunctionalTests {

    private static final int IDENTITY_BOOT_TIMEOUT_MS = 10_000;

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(new FlagFunctionalTestSupport.SetupCoreRule())
                    .around(new FlagFunctionalTestSupport.RegisterMonitorExtensionRule());

    @Before
    public void setup() throws Exception {
        final CountDownLatch registrationLatch = new CountDownLatch(1);
        MobileCore.registerExtensions(
                Arrays.asList(Identity.EXTENSION, Flag.EXTENSION),
                o -> registrationLatch.countDown());
        Assert.assertTrue(registrationLatch.await(2_000, TimeUnit.MILLISECONDS));
        FlagFunctionalTestSupport.resetTestExpectations();
    }

    @After
    public void tearDown() {
        final NamedCollection configDataStore =
                ServiceProvider.getInstance()
                        .getDataStoreService()
                        .getNamedCollection(FlagFunctionalTestConstants.CONFIG_DATA_STORE);
        if (configDataStore != null) {
            configDataStore.removeAll();
        }
    }

    @Test
    public void edgeIdentity_publishesXdmSharedStateWithWrappedIdentityMap() throws Exception {
        final SharedStateResult identityState =
                FlagFunctionalTestSupport.pollXdmSharedStateSet(
                        FlagConstants.EdgeIdentity.EXTENSION_NAME, IDENTITY_BOOT_TIMEOUT_MS);

        assertNotNull(
                "Edge Identity XDM shared state should reach SET within "
                        + IDENTITY_BOOT_TIMEOUT_MS
                        + " ms",
                identityState);
        assertNotNull(
                "Edge Identity XDM shared state value should not be null",
                identityState.getValue());

        final Object identityMapWrapper =
                identityState.getValue().get(FlagConstants.Edge.IDENTITY_MAP);
        assertTrue(
                "Edge Identity must publish namespaces under the identityMap wrapper",
                identityMapWrapper instanceof Map);

        @SuppressWarnings("unchecked")
        final Map<String, Object> namespaces = (Map<String, Object>) identityMapWrapper;
        assertFalse("identityMap should contain at least one namespace", namespaces.isEmpty());

        final Object ecidItems = namespaces.get(FlagFunctionalTestConstants.ECID_NAMESPACE);
        assertTrue("ECID namespace should be present after boot", ecidItems instanceof List);

        @SuppressWarnings("unchecked")
        final List<Object> ecidList = (List<Object>) ecidItems;
        assertFalse("ECID namespace should contain at least one identity item", ecidList.isEmpty());

        @SuppressWarnings("unchecked")
        final Map<String, Object> ecidEntry = (Map<String, Object>) ecidList.get(0);
        final Object ecid = ecidEntry.get(FlagConstants.Edge.IDENTITY_ID);
        assertNotNull("ECID item must include an id field", ecid);
        assertFalse("ECID id must be non-empty", String.valueOf(ecid).isEmpty());
    }

    @Test
    public void edgeIdentity_getIdentities_returnsEcidConsistentWithXdmSharedState()
            throws Exception {
        final SharedStateResult identityState =
                FlagFunctionalTestSupport.pollXdmSharedStateSet(
                        FlagConstants.EdgeIdentity.EXTENSION_NAME, IDENTITY_BOOT_TIMEOUT_MS);
        assertNotNull(identityState);

        final CountDownLatch identitiesLatch = new CountDownLatch(1);
        final IdentityMap[] identityMapHolder = new IdentityMap[1];

        Identity.getIdentities(
                identityMap -> {
                    identityMapHolder[0] = identityMap;
                    identitiesLatch.countDown();
                });

        assertTrue(
                "Identity.getIdentities should callback within timeout",
                identitiesLatch.await(5_000, TimeUnit.MILLISECONDS));
        assertNotNull(identityMapHolder[0]);

        final List<IdentityItem> ecidItems =
                identityMapHolder[0].getIdentityItemsForNamespace(
                        FlagFunctionalTestConstants.ECID_NAMESPACE);
        assertNotNull(ecidItems);
        assertFalse(ecidItems.isEmpty());

        @SuppressWarnings("unchecked")
        final Map<String, Object> namespaces =
                (Map<String, Object>) identityState.getValue().get(FlagConstants.Edge.IDENTITY_MAP);
        @SuppressWarnings("unchecked")
        final Map<String, Object> xdmEcidEntry =
                (Map<String, Object>)
                        ((List<?>) namespaces.get(FlagFunctionalTestConstants.ECID_NAMESPACE))
                                .get(0);

        assertEquals(
                ecidItems.get(0).getId(),
                String.valueOf(xdmEcidEntry.get(FlagConstants.Edge.IDENTITY_ID)));
    }

    @Test
    public void getFeature_withCompleteConfig_afterIdentitySet_dispatchesFlagsRequestContent()
            throws Exception {
        assertNotNull(
                FlagFunctionalTestSupport.pollXdmSharedStateSet(
                        FlagConstants.EdgeIdentity.EXTENSION_NAME, IDENTITY_BOOT_TIMEOUT_MS));

        MobileCore.updateConfiguration(completeFlagConfig());
        assertNotNull(
                "Flags extension should finish initialization after complete config",
                FlagFunctionalTestSupport.pollFlagsInitializationComplete(
                        IDENTITY_BOOT_TIMEOUT_MS));

        MonitorExtension.setExpectedEvent(
                FlagConstants.EventType.FLAGS, FlagConstants.EventSource.REQUEST_CONTENT, 1);

        Flag.getFeature(
                "test-feature",
                FeatureEvaluationContext.builder().build(),
                new AdobeCallbackWithError<FeatureEvaluationResult>() {
                    @Override
                    public void fail(final AdobeError error) {
                        // SDK init may fail offline; request dispatch is still validated.
                    }

                    @Override
                    public void call(final FeatureEvaluationResult feature) {}
                });

        final List<Event> requestEvents =
                FlagFunctionalTestSupport.getDispatchedEventsWith(
                        FlagConstants.EventType.FLAGS,
                        FlagConstants.EventSource.REQUEST_CONTENT,
                        5_000);

        assertFalse(
                "Flags request should dispatch when Edge Identity is SET and flags init completed",
                requestEvents.isEmpty());
    }

    private static Map<String, Object> completeFlagConfig() {
        final Map<String, Object> config = new HashMap<>();
        config.put("experienceCloud.org", "functional-test@AdobeOrg");
        config.put("flags.sandbox", "prod");
        config.put("flags.clientId", "functional-test-client");
        config.put("edge.domain", "edge.adobedc.net");
        return config;
    }
}
