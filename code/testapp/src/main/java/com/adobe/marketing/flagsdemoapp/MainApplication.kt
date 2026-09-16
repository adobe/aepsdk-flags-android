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

package com.adobe.marketing.flagsdemoapp

import android.app.Application
import android.util.Log
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.flags.Flag

class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()
        MobileCore.setLogLevel(LoggingMode.VERBOSE)
        val envFileId = BuildConfig.LAUNCH_ENVIRONMENT_FILE_ID
        if (envFileId.isBlank()) {
            Log.e(TAG, "LAUNCH_ENVIRONMENT_FILE_ID is not set. Set it in gradle.properties (or pass -PLAUNCH_ENVIRONMENT_FILE_ID=...). SDK will not initialize.")
            return
        }
        MobileCore.initialize(this, envFileId) {
            MobileCore.registerExtensions(
                listOf(
                    Lifecycle.EXTENSION,
                    Identity.EXTENSION,
                    Edge.EXTENSION,
                    Flag.EXTENSION
                )
            ) {
                MobileCore.lifecycleStart(null)
                print("Adobe mobile SDKs are successfully registered.")
            }
        }
    }
}
