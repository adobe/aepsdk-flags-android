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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.flags.internal.FlagConfigurationProvider;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FlagConfigurationProviderTest {

    @Test
    public void testResolveEdgeDomain_configuredValue() {
        Map<String, Object> configData = new HashMap<>();
        configData.put(FlagConstants.Configuration.EDGE_DOMAIN, "my-company.data.adobedc.net");

        assertEquals(
                "my-company.data.adobedc.net",
                FlagConfigurationProvider.resolveEdgeDomain(configData));
    }

    @Test
    public void testResolveEdgeDomain_trimsWhitespace() {
        Map<String, Object> configData = new HashMap<>();
        configData.put(FlagConstants.Configuration.EDGE_DOMAIN, "  my.domain.com  ");

        assertEquals("my.domain.com", FlagConfigurationProvider.resolveEdgeDomain(configData));
    }

    @Test
    public void testResolveEdgeDomain_missingKey_usesDefault() {
        assertEquals(
                FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN,
                FlagConfigurationProvider.resolveEdgeDomain(createValidConfigData()));
    }

    @Test
    public void testResolveEdgeDomain_emptyString_usesDefault() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.EDGE_DOMAIN, "");

        assertEquals(
                FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN,
                FlagConfigurationProvider.resolveEdgeDomain(configData));
    }

    @Test
    public void testResolveEdgeDomain_whitespaceOnly_usesDefault() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.EDGE_DOMAIN, "   ");

        assertEquals(
                FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN,
                FlagConfigurationProvider.resolveEdgeDomain(configData));
    }

    // --- Maps from Data Collection configuration ---

    @Test
    public void testBuildConfiguration_validConfig() {
        Map<String, Object> configData = createValidConfigData();
        com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration config =
                FlagConfigurationProvider.buildConfiguration(configData);

        assertNotNull(config);
        assertEquals(FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN, config.getEdgeDomain());
        assertTrue(config.getClientId().contains("my-app"));
    }

    @Test
    public void testBuildConfiguration_missingImsOrg() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG);

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_missingSandbox() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.FLAGS_SANDBOX);

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_missingClientId() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.FLAGS_CLIENT_ID);

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_emptyClientId() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.FLAGS_CLIENT_ID, "");

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_emptyImsOrg() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "");

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_emptySandbox() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.FLAGS_SANDBOX, "");

        assertNull(FlagConfigurationProvider.buildConfiguration(configData));
    }

    @Test
    public void testBuildConfiguration_multipleClientIds() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.FLAGS_CLIENT_ID, "app1,app2,app3");

        com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration config =
                FlagConfigurationProvider.buildConfiguration(configData);
        assertNotNull(config);
        assertTrue(config.getClientId().contains("app1"));
        assertTrue(config.getClientId().contains("app2"));
        assertTrue(config.getClientId().contains("app3"));
    }

    @Test
    public void testBuildConfiguration_configuredEdgeDomain() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.EDGE_DOMAIN, "custom.domain.com");

        com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration config =
                FlagConfigurationProvider.buildConfiguration(configData);
        assertNotNull(config);
        assertEquals("custom.domain.com", config.getEdgeDomain());
    }

    @Test
    public void testBuildConfiguration_defaultEdgeDomainWhenMissing() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.EDGE_DOMAIN);

        com.adobe.marketing.mobile.flags.engine.models.FlagConfiguration config =
                FlagConfigurationProvider.buildConfiguration(configData);
        assertNotNull(config);
        assertEquals(FlagConstants.Configuration.DEFAULT_EDGE_DOMAIN, config.getEdgeDomain());
    }

    // --- Required configuration keys ---

    @Test
    public void testHasRequiredConfig_allPresent() {
        assertTrue(FlagConfigurationProvider.hasRequiredConfig(createValidConfigData()));
    }

    @Test
    public void testHasRequiredConfig_missingImsOrg() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG);

        assertFalse(FlagConfigurationProvider.hasRequiredConfig(configData));
    }

    @Test
    public void testHasRequiredConfig_missingSandbox() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.FLAGS_SANDBOX);

        assertFalse(FlagConfigurationProvider.hasRequiredConfig(configData));
    }

    @Test
    public void testHasRequiredConfig_missingClientId() {
        Map<String, Object> configData = createValidConfigData();
        configData.remove(FlagConstants.Configuration.FLAGS_CLIENT_ID);

        assertFalse(FlagConfigurationProvider.hasRequiredConfig(configData));
    }

    @Test
    public void testHasRequiredConfig_emptyValues() {
        Map<String, Object> configData = createValidConfigData();
        configData.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "");

        assertFalse(FlagConfigurationProvider.hasRequiredConfig(configData));
    }

    private Map<String, Object> createValidConfigData() {
        Map<String, Object> configData = new HashMap<>();
        configData.put(FlagConstants.Configuration.EXPERIENCE_CLOUD_ORG, "test@AdobeOrg");
        configData.put(FlagConstants.Configuration.FLAGS_SANDBOX, "prod");
        configData.put(FlagConstants.Configuration.FLAGS_CLIENT_ID, "my-app");
        return configData;
    }
}
