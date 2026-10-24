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

package com.adobe.marketing.mobile.flags.engine.internal.parser;

/** Builds minimal combined-response JSON for parser tests. */
public final class EdgeResponseJsonFixtures {

    private EdgeResponseJsonFixtures() {}

    /** Minimal v2 combined-response body (top-level {@code cohortingType} on feature). */
    public static String minimalBody() {
        return bodyWithFeatureGroups(
                120,
                "test-context-v1",
                "[{\"id\":\"country\",\"type\":\"STRING\"}]",
                featureGroup(
                        1001,
                        "default_feature_group",
                        feature(
                                1001,
                                "my-feature",
                                "\"cohortingType\":\"ECID\",\"analyticsEnabled\":true")));
    }

    public static String bodyWithFeatureGroups(
            int ttl,
            String contextVersion,
            String contextsArrayContent,
            String featureGroupsArrayContent) {
        return "{"
                + "\"v\":2,"
                + "\"ttl\":"
                + ttl
                + ","
                + "\"contextVersion\":\""
                + contextVersion
                + "\","
                + "\"contexts\":"
                + contextsArrayContent
                + ","
                + "\"featureGroups\":["
                + featureGroupsArrayContent
                + "]"
                + "}";
    }

    public static String bodyWithTtlOnly(int ttl) {
        return "{\"v\":2,\"ttl\":" + ttl + "}";
    }

    /** Combined body with contexts, feature groups, and the given poll {@code ttl}. */
    public static String body(
            int ttl,
            String contextVersion,
            String contextsArrayContent,
            String featureGroupsArrayContent) {
        return bodyWithFeatureGroups(
                ttl, contextVersion, contextsArrayContent, featureGroupsArrayContent);
    }

    /**
     * Features-only delta body: {@code contextVersion} and {@code featureGroups} without {@code
     * contexts}.
     */
    public static String bodyFeaturesOnly(
            int ttl, String contextVersion, String featureGroupsArrayContent) {
        return "{"
                + "\"v\":2,"
                + "\"ttl\":"
                + ttl
                + ","
                + "\"contextVersion\":\""
                + contextVersion
                + "\","
                + "\"featureGroups\":["
                + featureGroupsArrayContent
                + "]"
                + "}";
    }

    public static String featureGroup(int id, String key, String featureObjects) {
        return "{"
                + "\"id\":"
                + id
                + ","
                + "\"key\":\""
                + key
                + "\","
                + "\"features\":["
                + featureObjects
                + "]"
                + "}";
    }

    public static String featureGroupWithFields(
            int id, String key, String extraFields, String featureObjects) {
        String trimmed = extraFields.startsWith(",") ? extraFields.substring(1) : extraFields;
        String fieldsPrefix = trimmed.isEmpty() ? "" : trimmed + ",";
        return "{"
                + "\"id\":"
                + id
                + ","
                + "\"key\":\""
                + key
                + "\","
                + fieldsPrefix
                + "\"features\":["
                + featureObjects
                + "]"
                + "}";
    }

    public static String feature(int id, String key, String extraFields) {
        String trimmed = extraFields.startsWith(",") ? extraFields.substring(1) : extraFields;
        return "{"
                + "\"id\":"
                + id
                + ","
                + "\"key\":\""
                + key
                + "\""
                + (trimmed.isEmpty() ? "" : "," + trimmed)
                + "}";
    }
}
