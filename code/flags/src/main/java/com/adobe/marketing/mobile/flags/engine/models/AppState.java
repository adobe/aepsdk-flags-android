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

package com.adobe.marketing.mobile.flags.engine.models;

import com.adobe.marketing.mobile.flags.engine.FlagClient;

/**
 * Represents the host application's lifecycle state.
 *
 * <p>Pass to {@link FlagClient#setAppState(AppState)} so the SDK can adjust background resource
 * usage accordingly.
 */
public enum AppState {

    /** App is visible and actively used. Polling and connections run normally. */
    FOREGROUND,

    /**
     * App is not visible. Polling is paused and idle connections are evicted to conserve battery,
     * data, and sockets.
     */
    BACKGROUND
}
