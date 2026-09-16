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

package com.adobe.marketing.mobile.flags.engine.constants;

/** SDK constants including service endpoints, headers, and configuration values. */
public final class Constants {

    private Constants() {}

    // --- Service Endpoints ---
    public static final String HTTPS_SCHEME = "https://";
    public static final String FLAGS_FEATURE_PATH = "/flags/feature";

    // --- HTTP Headers ---
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    public static final String HEADER_IMS_ORG = "x-gw-ims-org-id";
    public static final String HEADER_SANDBOX_NAME = "x-sandbox-name";
    public static final String HEADER_ETAG = "ETag";
    public static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final String ACCEPT_JSON = "application/json";
    public static final String ENCODING_GZIP = "gzip";

    // --- Query Parameters ---
    public static final String QUERY_CLIENT_ID = "clientId";
    public static final String QUERY_SDK_VERSION = "sdkVersion";
    public static final String QUERY_CONTEXT_VERSION = "contextVersion";

    // --- Cache Configuration ---
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 300;
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_DELAY_MS = 1000; // 1 second
    public static final long MAX_RETRY_DELAY_MS = 30000; // 30 seconds

    // --- HTTP Timeouts ---
    public static final int HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int HTTP_READ_TIMEOUT_SECONDS = 30;
    public static final int HTTP_WRITE_TIMEOUT_SECONDS = 30;

    // --- Policy / Cohort ---
    public static final String CONTROL_GROUP_VARIANT_ID = "0";
    public static final int CONTROL_GROUP_FEATURE_ID = -1;
    public static final String DEFAULT_COHORTING_NAMESPACE = "ECID";

    // --- JSON Keys ---
    public static final String JSON_KEY_VERSION = "v";
    public static final String JSON_KEY_FEATURE_GROUPS = "featureGroups";
    public static final String JSON_KEY_CONTEXTS = "contexts";
    public static final String JSON_KEY_KEY = "key";
    public static final String JSON_KEY_HASH_ALGORITHM = "hashAlgorithm";
    public static final String JSON_KEY_BUCKETS = "buckets";
    public static final String JSON_KEY_ANALYTICS_ENABLED = "analyticsEnabled";
    public static final String JSON_KEY_COHORTING_TYPE = "cohortingType";
    /**
     * Identity-map namespace key in feature/featureGroup {@code params}; used for A/B bucketing.
     */
    public static final String JSON_KEY_COHORTING_NAMESPACE_CODE = "cohortingNamespaceCode";

    public static final String JSON_KEY_PARAMS = "params";
    public static final String JSON_KEY_CONTEXT_VERSION = "contextVersion";
    public static final String JSON_KEY_FEATURES = "features";
    public static final String JSON_TTL = "ttl";
    public static final String JSON_KEY_CRITERIA = "criteria";
    public static final String JSON_KEY_POLICY_ID = "policyId";
    public static final String JSON_KEY_HASH = "hash";

    // --- Identity map entry keys ---
    public static final String IDENTITY_ENTRY_KEY_ID = "id";
    public static final String IDENTITY_ENTRY_KEY_PRIMARY = "primary";
    public static final String IDENTITY_ENTRY_KEY_AUTHENTICATED_STATE = "authenticatedState";

    // --- Context field types ---
    public static final String CONTEXT_DATA_TYPE_COMPLEX = "COMPLEX";
    public static final String CONTEXT_DATA_TYPE_STRING = "STRING";
}
