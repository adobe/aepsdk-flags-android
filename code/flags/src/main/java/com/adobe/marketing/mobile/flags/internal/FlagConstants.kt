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

package com.adobe.marketing.mobile.flags.internal

internal object FlagConstants {
    const val LOG_TAG = "Flags"
    const val EXTENSION_NAME = "com.adobe.flags"
    const val FRIENDLY_NAME = "Flags"
    const val API_TIMEOUT_MS = 10000L

    object EventNames {
        const val GET_FEATURE_REQUEST = "Flags Get Feature Request"
        const val IS_FEATURE_ENABLED_REQUEST = "Flags Is Feature Enabled Request"
        const val FLAGS_RESPONSE = "Flags Response"
        const val EDGE_FEATURE_EXPOSURE_REQUEST = "Flags Edge Feature Exposure"
    }

    object EventType {
        const val FLAGS = "com.adobe.eventType.flags"
    }

    object EventSource {
        const val REQUEST_CONTENT = "com.adobe.eventSource.requestContent"
        const val RESPONSE_CONTENT = "com.adobe.eventSource.responseContent"
        const val REQUEST_RESET = "com.adobe.eventSource.requestReset"
    }

    object EventDataKeys {
        const val REQUEST_TYPE = "requesttype"
        const val FEATURE_NAME = "featurename"
        const val CONTEXT = "context"
        const val FEATURE = "feature"
        const val ID = "id"
        const val KEY = "key"
        const val FEATURE_GROUP_KEY = "featureGroupKey"
        const val META = "meta"
        const val ANALYTICS_PARAM = "analyticsParam"
        const val FEATURE_GROUP_ID = "featureGroupId"
        const val FEATURE_ID = "featureId"
        const val VARIANT_ID = "variantId"
        const val IS_ENABLED = "isenabled"
        const val RESPONSE_ERROR = "responseerror"
    }

    object EventDataValues {
        const val REQUEST_TYPE_GET_FEATURE = "getfeature"
        const val REQUEST_TYPE_IS_ENABLED = "isfeatureenabled"
    }

    object SharedState {
        const val INITIALIZATION_STATUS = "initializationStatus"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
    }

    object Edge {
        const val REQUEST = "request"
        const val REQUEST_PATH = "path"
        const val COLLECT_PATH = "/v1/collect"
        const val XDM = "xdm"
        const val EVENT_TYPE = "eventType"
        const val EVENT_TYPE_PROPOSITION_DISPLAY = "decisioning.propositionDisplay"
        const val TIMESTAMP = "timestamp"
        const val IDENTITY_MAP = "identityMap"
        const val IDENTITY_ID = "id"
        const val IDENTITY_PRIMARY = "primary"
        const val EXPERIENCE = "_experience"
        const val DECISIONING = "decisioning"
        const val PROPOSITION_EVENT_TYPE = "propositionEventType"
        const val DISPLAY = "display"
        const val PROPOSITIONS = "propositions"
        const val SCOPE_DETAILS = "scopeDetails"
        const val DECISION_PROVIDER = "decisionProvider"
        const val DECISION_PROVIDER_FLAGS = "FLAGS"
        const val CORRELATION_ID = "correlationID"
        const val CORRELATION_ID_SEPARATOR = "-"
        const val ACTIVITY = "activity"
        const val SCOPE_EXPERIENCE = "experience"
        const val STRATEGIES = "strategies"
        const val ALGORITHM_ID = "algorithmID"
        const val ALGORITHM_MURMUR = "murmur"
        const val CHARACTERISTICS = "characteristics"
        const val ENTITY_TYPE = "entityType"
        const val ENTITY_TYPE_FEATURE = "feature"
        const val ENTITY_TYPE_FEATURE_GROUP = "featureGroup"
        const val ITEMS = "items"
        const val NAME = "name"
        const val VARIANT_PREFIX = "Variant-"
        const val FEATURE_ACTIVITY_PREFIX = "F-"
        const val FEATURE_GROUP_ACTIVITY_PREFIX = "FG-"
        const val STANDALONE_FEATURES_FEATURE_GROUP_KEY = "||features||"
        const val STANDALONE_FEATURES_FEATURE_GROUP_ID = -1
    }

    /** Configuration keys published from Data Collection for the Flags extension. */
    object Configuration {
        const val EXTENSION_NAME = "com.adobe.module.configuration"
        const val EDGE_DOMAIN = "edge.domain"
        const val DEFAULT_EDGE_DOMAIN = "edge.adobedc.net"
        const val FLAGS_CLIENT_ID = "flags.clientId"
        const val FLAGS_SANDBOX = "flags.sandbox"
        const val EXPERIENCE_CLOUD_ORG = "experienceCloud.org"
    }

    object Lifecycle {
        const val ACTION_KEY = "action"
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
    }

    object EdgeIdentity {
        const val EXTENSION_NAME = "com.adobe.edge.identity"
    }

    /** Batched feature exposure queue tuning (cross-platform parity). */
    object ExposureQueue {
        const val BATCH_SIZE = 20
        const val FLUSH_INTERVAL_MS = 150_000L
    }
}
