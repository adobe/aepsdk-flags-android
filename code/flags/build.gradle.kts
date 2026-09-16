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
import org.gradle.api.plugins.quality.Checkstyle

plugins {
    id("aep-library")
    id("org.jetbrains.kotlin.android")
}

val mavenCoreVersion: String by project
val mavenEdgeIdentityVersion: String by project

// com.adobe.marketing.mobile:core's POM strictly pins androidx.lifecycle:{common,runtime,
// viewmodel,livedata,livedata-core} to 2.0.0 (matching the old appcompat:1.0.0 it was built
// against). The Compose runtime that aep-library adds to the androidTest classpath transitively
// pulls in androidx.emoji2, whose EmojiCompatInitializer has a compiled reference to
// androidx.lifecycle.ProcessLifecycleInitializer -- a class that only exists in lifecycle-process
// 2.4.1+. Pinning the family down to 2.0.0 (matching Core) removes that class entirely and trades
// one NoClassDefFoundError for another; the family has to go up, not down, to stay compatible with
// emoji2. Force everything to the version lifecycle-process itself already resolves to elsewhere
// in this graph, overriding Core's stale strict pin.
val androidxLifecycleVersion = "2.6.1"

// Flags Engine (in-repo source under com.adobe.marketing.mobile.flags.engine) third-party deps.
val okHttpVersion: String by project
val gsonVersion: String by project
val slf4jVersion: String by project
val junitJupiterVersion: String by project
val awaitilityVersion: String by project
val junitVintageVersion: String by project

aepLibrary {
    namespace = "com.adobe.marketing.mobile.flags"
    enableDokkaDoc = true
    enableSpotless = true
    enableCheckStyle = true

    publishing {
        gitRepoName = "aepsdk-flags-android"
        addCoreDependency(mavenCoreVersion)
        // Flags Engine dependencies are bundled as source in this module, so its
        // third-party dependencies must be declared in the published POM.
        addMavenDependency("com.squareup.okhttp3", "okhttp", okHttpVersion)
        addMavenDependency("com.google.code.gson", "gson", gsonVersion)
        addMavenDependency("org.slf4j", "slf4j-api", slf4jVersion)
    }
}

android {
    // The Flags Engine source requires Java 11 (uses java.util collection factory
    // methods, `var`, String.isBlank, etc.); override the aep-library default of 8.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // Single source of truth for the SDK version: derived at build time from
        // `moduleVersion` in gradle.properties (the value the release workflow bumps).
        // The `-SNAPSHOT` suffix is stripped so the runtime-reported version stays clean.
        val flagsVersion = (project.property("moduleVersion") as String).removeSuffix("-SNAPSHOT")
        buildConfigField("String", "FLAGS_VERSION", "\"$flagsVersion\"")
    }
}

// aep-library sets the Kotlin jvmTarget to 1.8 via its own configureEach callback;
// register ours after it so Java (11) and Kotlin jvmTargets match.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

// The Flags Engine source (com.adobe.marketing.mobile.flags.engine) is bundled implementation code
// imported from a separate project with its own style conventions. It is auto-formatted by
// Spotless but excluded from Checkstyle's semantic rules (magic numbers, final parameters, etc.)
// to avoid large, risky hand-edits to production evaluation logic. Revisit if the engine is
// brought fully in line with the extension's Checkstyle configuration.
tasks.withType<Checkstyle>().configureEach {
    exclude("**/com/adobe/marketing/mobile/flags/engine/**")
}

// Run the Flags Engine's JUnit 5 tests alongside the existing JUnit 4 tests
// (executed via the JUnit Platform vintage engine).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    compileOnly("com.adobe.marketing.mobile:core:$mavenCoreVersion")

    // Flags Engine third-party dependencies (engine source lives in this module).
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Peer extension: compile-only so published POM does not force customer SDK versions.
    // Integrators add edgeidentity explicitly (see repository README).
    compileOnly("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")

    // testImplementation dependencies provided by aep-library:
    // MOCKITO_CORE, MOCKITO_INLINE, JSON
    testImplementation("com.adobe.marketing.mobile:core:$mavenCoreVersion")
    androidTestImplementation("com.adobe.marketing.mobile:core:$mavenCoreVersion")
    testImplementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")
    androidTestImplementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")
    testImplementation(BuildConstants.Dependencies.MOCKK)

    // Overrides Core's strict androidx.lifecycle:2.0.0 pin (see comment near
    // androidxLifecycleVersion above) so the whole family resolves consistently.
    configurations.all {
        resolutionStrategy.force(
            "androidx.lifecycle:lifecycle-common:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-runtime:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-viewmodel:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-livedata:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-livedata-core:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-process:$androidxLifecycleVersion"
        )
    }

    // Flags Engine test dependencies (JUnit 5 + supporting libraries).
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junitVintageVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:${BuildConstants.Versions.MOCKITO}")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.slf4j:slf4j-simple:$slf4jVersion")
}
