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

package com.adobe.marketing.mobile.flags.engine.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.marketing.mobile.flags.engine.internal.parser.EdgeResponseJsonFixtures;
import com.adobe.marketing.mobile.flags.engine.internal.parser.ResponseParser;
import com.adobe.marketing.mobile.flags.engine.models.EdgeResponse;
import com.adobe.marketing.mobile.flags.engine.models.MetadataResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SDKClientCacheMetadataTest {

    private SDKClientCache cache;

    @BeforeEach
    void setUp() {
        cache = new SDKClientCache();
    }

    @Test
    @DisplayName("putMetadata returns false for same contextVersion twice")
    void sameContextVersionTwiceReturnsFalse() {
        MetadataResponse first = metadata("ctx-v1", Map.of("country", "STRING"), "\"etag-1\"");
        MetadataResponse second = metadata("ctx-v1", Map.of("country", "INTEGER"), "\"etag-2\"");

        assertTrue(cache.putMetadata(first));
        assertFalse(cache.putMetadata(second));

        assertEquals("STRING", cache.getMetadata().getFieldDataTypeCache().get("country"));
        assertEquals("ctx-v1", cache.getMetadata().getContextVersion());
        assertEquals("\"etag-1\"", cache.getMetadata().getEtag());
    }

    @Test
    @DisplayName("putMetadata replaces cache when contextVersion changes")
    void differentContextVersionReplacesCache() {
        MetadataResponse first = metadata("ctx-v1", Map.of("country", "STRING"), "\"etag-1\"");
        MetadataResponse second = metadata("ctx-v2", Map.of("age", "INTEGER"), "\"etag-2\"");

        assertTrue(cache.putMetadata(first));
        assertTrue(cache.putMetadata(second));

        assertEquals("ctx-v2", cache.getMetadata().getContextVersion());
        assertNull(cache.getMetadata().getFieldDataTypeCache().get("country"));
        assertEquals("INTEGER", cache.getMetadata().getFieldDataTypeCache().get("age"));
    }

    @Test
    @DisplayName("putMetadata returns false when contextVersion is empty")
    void emptyContextVersionReturnsFalse() {
        MetadataResponse response =
                new MetadataResponse(Map.of(), Map.of("country", "STRING"), "", "\"etag-1\"", true);

        assertFalse(cache.putMetadata(response));
        assertFalse(cache.hasMetadata());
    }

    @Test
    @DisplayName("putMetadata returns false when isChanged is false")
    void notChangedReturnsFalse() {
        MetadataResponse response = metadata("ctx-v1", Map.of("country", "STRING"), "\"etag-1\"");
        response =
                new MetadataResponse(
                        response.getContextVariableMap(),
                        response.getFieldDataTypeCache(),
                        "ctx-v1",
                        "\"etag-1\"",
                        false);

        assertFalse(cache.putMetadata(response));
        assertFalse(cache.hasMetadata());
    }

    @Test
    @DisplayName("EdgeResponse bridge copies contextVersion, maps, etag, and isChanged")
    void edgeResponseBridge() {
        String json = EdgeResponseJsonFixtures.minimalBody();
        EdgeResponse edge = ResponseParser.parseEdgeResponse(json);
        MetadataResponse metadata = edge.toMetadataResponse("\"combined-etag\"", true);

        assertEquals("test-context-v1", metadata.getContextVersion());
        assertEquals("\"combined-etag\"", metadata.getEtag());
        assertTrue(metadata.isChanged());
        assertEquals("country", metadata.getContextVariableMap().get("COUNTRY"));
        assertEquals("STRING", metadata.getFieldDataTypeCache().get("country"));

        assertTrue(cache.putMetadata(metadata));
        assertEquals("test-context-v1", cache.getMetadata().getContextVersion());
    }

    private static MetadataResponse metadata(
            String contextVersion, Map<String, String> fieldTypes, String etag) {
        return new MetadataResponse(Map.of(), fieldTypes, contextVersion, etag, true);
    }
}
