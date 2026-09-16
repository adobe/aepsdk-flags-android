/*
 * Copyright 2026 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
import com.adobe.marketing.mobile.gradle.BuildConstants

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mavenSdkBomVersion: String by project

android {
    namespace = "com.adobe.marketing.flagsdemoapp"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.adobe.marketing.flagsdemoapp"
        compileSdk = BuildConstants.Versions.COMPILE_SDK_VERSION
        minSdk = BuildConstants.Versions.MIN_SDK_VERSION
        targetSdk = BuildConstants.Versions.TARGET_SDK_VERSION
        versionCode = BuildConstants.Versions.VERSION_CODE
        versionName = BuildConstants.Versions.VERSION_NAME
        buildConfigField(
            "String",
            "LAUNCH_ENVIRONMENT_FILE_ID",
            "\"${project.findProperty("LAUNCH_ENVIRONMENT_FILE_ID") ?: ""}\""
        )
    }

    kotlinOptions {
        jvmTarget = BuildConstants.Versions.KOTLIN_JVM_TARGET
        languageVersion = BuildConstants.Versions.KOTLIN_LANGUAGE_VERSION
        apiVersion = BuildConstants.Versions.KOTLIN_API_VERSION
    }

    buildTypes {
        getByName(BuildConstants.BuildTypes.RELEASE) {
            isMinifyEnabled = false
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = BuildConstants.Versions.COMPOSE_COMPILER
    }
}

dependencies {
    implementation(project(":flags"))
    implementation(platform("com.adobe.marketing.mobile:sdk-bom:$mavenSdkBomVersion"))
    implementation("com.adobe.marketing.mobile:core")
    implementation("com.adobe.marketing.mobile:edge")
    implementation("com.adobe.marketing.mobile:edgeidentity")
    implementation("com.adobe.marketing.mobile:assurance")
    implementation("com.adobe.marketing.mobile:lifecycle")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.material:material:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.3")
}
