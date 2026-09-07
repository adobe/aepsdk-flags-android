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

import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import org.junit.Test;

public class FlagConstantsTest {

    @Test
    public void testExtensionName() {
        assertEquals("com.adobe.flags", FlagConstants.EXTENSION_NAME);
    }

    @Test
    public void testFriendlyName() {
        assertEquals("Flags", FlagConstants.FRIENDLY_NAME);
    }

    @Test
    public void testLogTag() {
        assertEquals("Flags", FlagConstants.LOG_TAG);
    }

    @Test
    public void testEventType() {
        assertEquals("com.adobe.eventType.flags", FlagConstants.EventType.FLAGS);
    }

    @Test
    public void testEventSources() {
        assertEquals(
                "com.adobe.eventSource.requestContent", FlagConstants.EventSource.REQUEST_CONTENT);
        assertEquals(
                "com.adobe.eventSource.responseContent",
                FlagConstants.EventSource.RESPONSE_CONTENT);
        assertEquals("com.adobe.eventSource.requestReset", FlagConstants.EventSource.REQUEST_RESET);
    }

    @Test
    public void testRequestTypes() {
        assertEquals("getfeature", FlagConstants.EventDataValues.REQUEST_TYPE_GET_FEATURE);
        assertEquals("isfeatureenabled", FlagConstants.EventDataValues.REQUEST_TYPE_IS_ENABLED);
    }

    @Test
    public void testConfigurationKeys() {
        assertNotNull(FlagConstants.Configuration.EDGE_DOMAIN);
        assertNotNull(FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN);
        assertNotNull(FlagConstants.Configuration.FLAGS_CLIENT_ID);
        assertNotNull(FlagConstants.Configuration.FLAGS_SANDBOX);
        assertNotNull(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG);
        assertNotNull(FlagConstants.EdgeIdentity.EXTENSION_NAME);
    }

    @Test
    public void testEdgeIdentityExtensionName() {
        assertEquals("com.adobe.edge.identity", FlagConstants.EdgeIdentity.EXTENSION_NAME);
    }

    @Test
    public void testEventDataKeys_featureGroupStrings() {
        assertEquals("featureGroupKey", FlagConstants.EventDataKeys.FEATURE_GROUP_KEY);
        assertEquals("featureGroupId", FlagConstants.EventDataKeys.FEATURE_GROUP_ID);
    }

    @Test
    public void testEdge_decisioningKeys() {
        assertEquals(
                "decisioning.propositionDisplay",
                FlagConstants.Edge.EVENT_TYPE_PROPOSITION_DISPLAY);
        assertEquals("decisioning", FlagConstants.Edge.DECISIONING);
        assertEquals("FLAGS", FlagConstants.Edge.DECISION_PROVIDER_FLAGS);
        assertEquals("||features||", FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY);
        assertEquals(-1, FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_ID);
    }

    @Test
    public void testEdge_collectPath() {
        assertEquals("/v1/collect", FlagConstants.Edge.COLLECT_PATH);
    }

    @Test
    public void testSharedState_initializationStatusKeys() {
        assertEquals("initializationStatus", FlagConstants.SharedState.INITIALIZATION_STATUS);
        assertEquals("ready", FlagConstants.SharedState.STATUS_READY);
        assertEquals("failed", FlagConstants.SharedState.STATUS_FAILED);
    }
}
