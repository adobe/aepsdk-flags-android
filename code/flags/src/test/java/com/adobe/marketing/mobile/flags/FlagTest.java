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
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.Extension;
import org.junit.Test;

public class FlagTest {

    @Test
    public void testExtensionVersion() {
        // Assert semver format rather than a hardcoded literal: the version has a single source
        // (gradle.properties:moduleVersion, surfaced via BuildConfig) and is validated against the
        // featureGroup tag by `make version-check`, so this test must not need editing on every
        // bump.
        final String version = Flag.extensionVersion();
        assertNotNull(version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-.+)?"));
    }

    @Test
    public void testExtensionClass() {
        Class<? extends Extension> extensionClass = Flag.EXTENSION;
        assertNotNull(extensionClass);
        assertEquals(FlagExtension.class, extensionClass);
    }
}
