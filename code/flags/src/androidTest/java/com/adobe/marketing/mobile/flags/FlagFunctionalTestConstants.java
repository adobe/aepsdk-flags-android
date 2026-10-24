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

/** Constants used only by functional / instrumentation tests (not shipped in the AAR). */
final class FlagFunctionalTestConstants {

    private FlagFunctionalTestConstants() {}

    static final String LOG_TAG = "FeatureTest";
    static final String CONFIG_DATA_STORE = "AdobeMobile_ConfigState";

    /** ECID namespace in Edge Identity {@code identityMap} (functional-test assertions only). */
    static final String ECID_NAMESPACE = "ECID";

    static final class MonitorEventType {
        static final String MONITOR = "com.adobe.functional.eventType.monitor";
    }

    static final class MonitorEventSource {
        static final String UNREGISTER = "com.adobe.eventSource.unregister";
        static final String SHARED_STATE_REQUEST = "com.adobe.eventSource.sharedStateRequest";
        static final String SHARED_STATE_RESPONSE = "com.adobe.eventSource.sharedStateResponse";
        static final String XDM_SHARED_STATE_REQUEST =
                "com.adobe.eventSource.xdmSharedStateRequest";
    }

    static final class MonitorEventDataKeys {
        static final String STATE_OWNER = "stateowner";
        static final String STATUS = "status";
        static final String VALUE = "value";
    }
}
