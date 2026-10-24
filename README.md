# Experience Flags Extension for Android

This guide describes how to integrate **the Experience Flags extension** with the Adobe Experience Platform Mobile SDK on Android.

## Prerequisites

Before implementing the Experience Flags extension, ensure you have:

- A mobile property configured in [Adobe Experience Platform Data Collection](https://experience.adobe.com/#/data-collection/)
- The Experience Flags extension installed and configured in your mobile property
- An Adobe Experience Cloud Organization ID
- Minimum SDK: API 21 (Android 5.0 Lollipop)

### Extension dependencies

The Experience Flags extension requires the following Adobe Experience Platform extensions:


| Extension                          | Description                                                                                               | Required |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------- | -------- |
| **Mobile Core**                    | Provides core functionality including configuration and event processing                                  | Yes      |
| **Lifecycle**                      | Collects application lifecycle and session data for the Mobile SDK                                        | Yes      |
| **Edge Network**                   | Sends exposure analytics events to Adobe Experience Platform                                              | Yes      |
| **Identity for Edge Network**      | Provides the ECID and other identities used for cohort bucketing and analytics association                | Yes      |


Ensure these extensions are installed in your Data Collection mobile property and included in your app dependencies.

> **Identity is resolved automatically.** The Flags extension reads the identity map from the **Identity for Edge Network** extension on each evaluation. You do not supply an identity per call. Exposure analytics events do not embed profile identity in their XDM payload; the Edge extension merges identity from Edge Identity when each event is processed.

---

## Configure Experience Flags extension in Data Collection

### Install the extension

1. Log in to [Adobe Experience Platform Data Collection](https://experience.adobe.com/#/data-collection/).
2. Select the **Tags** tab and choose your mobile property.
3. Navigate to **Extensions** > **Catalog**.
4. Search for **Experience Flags extension** and select **Install**.
5. Configure the extension settings:


| Setting            | Description                                                                            |
| ------------------ | -------------------------------------------------------------------------------------- |
| **Sandbox**        | The Adobe Experience Platform sandbox containing your Experience Flags configuration |
| **Application ID** | A unique identifier for your application in Experience Flags                         |


1. Select **Save**.
2. Follow the [publishing process](https://experienceleague.adobe.com/docs/experience-platform/tags/publish/overview.html) to update your configuration.

### Get the Environment File ID

1. In your mobile property, navigate to **Environments**.
2. Select the box icon under the **Install** column for your environment.
3. In the **Mobile Install Instructions** dialog, copy the **Environment File ID**.

---

## Add Experience Flags extension to your app

### Add dependencies

Add the Mobile SDK dependencies to your project. The Experience Flags extension requires Mobile Core, Lifecycle, Edge Network, and Identity for Edge Network.

#### Using Gradle with BOM (Recommended)

Add the following dependencies to your app's `build.gradle.kts` file:

```kotlin
dependencies {
    // Adobe Experience Platform Mobile SDK BOM
    implementation(platform("com.adobe.marketing.mobile:sdk-bom:3.+"))

    // Required extensions
    implementation("com.adobe.marketing.mobile:core")
    implementation("com.adobe.marketing.mobile:lifecycle")
    implementation("com.adobe.marketing.mobile:edge")
    implementation("com.adobe.marketing.mobile:edgeidentity")

    // Experience Flags extension
    implementation("com.adobe.marketing.mobile:flags")
}
```

#### Using Gradle (Groovy)

```groovy
dependencies {
    // Adobe Experience Platform Mobile SDK BOM
    implementation platform('com.adobe.marketing.mobile:sdk-bom:3.+')

    // Required extensions
    implementation 'com.adobe.marketing.mobile:core'
    implementation 'com.adobe.marketing.mobile:lifecycle'
    implementation 'com.adobe.marketing.mobile:edge'
    implementation 'com.adobe.marketing.mobile:edgeidentity'

    // Experience Flags extension
    implementation 'com.adobe.marketing.mobile:flags'
}
```

> **Important**: For production applications, Adobe recommends using explicit version numbers instead of dynamic versions. See [Managing Gradle dependencies](https://developer.adobe.com/client-sdks/resources/manage-gradle-dependencies/) for more information.

### Add permissions

Add the following permissions to your `AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Initialize the SDK

Initialize the Mobile SDK in your Application class before calling any Experience Flags extension APIs. Register **Lifecycle**, **Identity for Edge Network**, **Edge Network**, and the **Experience Flags** extension, then call `MobileCore.lifecycleStart`. Use the **Environment File ID** from your mobile property with `MobileCore.initialize` so the app picks up the flags settings you published in Data Collection.

### Using MobileCore.initialize

Available starting from Android BOM version 3.8.0, this API initializes the SDK with your Data Collection environment file. For **production** apps, use `LoggingMode.ERROR` only; do not use DEBUG or VERBOSE in release builds.

#### Kotlin

```kotlin
import android.app.Application
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.flags.Flag

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Production: use LoggingMode.ERROR only. Do not use DEBUG or VERBOSE in release builds.
        MobileCore.setLogLevel(LoggingMode.ERROR)

        // Initialize with your Environment File ID from Data Collection
        MobileCore.initialize(this, "YOUR_ENVIRONMENT_FILE_ID") {
            MobileCore.registerExtensions(
                listOf(
                    Lifecycle.EXTENSION,
                    Identity.EXTENSION,
                    Edge.EXTENSION,
                    Flag.EXTENSION
                )
            ) {
                MobileCore.lifecycleStart(null)
            }
        }
    }
}
```

#### Java

```java
import android.app.Application;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.Lifecycle;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.flags.Flag;
import java.util.Arrays;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Production: use LoggingMode.ERROR only. Do not use DEBUG or VERBOSE in release builds.
        MobileCore.setLogLevel(LoggingMode.ERROR);

        // Initialize with your Environment File ID from Data Collection
        MobileCore.initialize(this, "YOUR_ENVIRONMENT_FILE_ID", initStatus -> {
            MobileCore.registerExtensions(
                    Arrays.asList(
                            Lifecycle.EXTENSION,
                            Identity.EXTENSION,
                            Edge.EXTENSION,
                            Flag.EXTENSION),
                    registrationStatus -> MobileCore.lifecycleStart(null));
        });
    }
}
```

### Register the Application class

Register your Application class in `AndroidManifest.xml`:

```xml
<application
    android:name=".MainApplication"
    ... >
</application>
```

---

## Evaluation context

`FeatureEvaluationContext` carries **targeting attributes** for flags rule matching. Identity for bucketing and analytics is read automatically from the **Identity for Edge Network** extension on each evaluation — you do not supply an identity per call.


| Method                 | Required | Description                                                                                                                                                                                                                                                                               |
| ---------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `withAttributes(map)`  | No       | `Map<String, List<String>>`. **Key** is the context attribute name used by your flags rules (for example `locale`, `platform`, `appVersion`, `deviceType`). **Value** is the list of candidate attribute values for that key for the current user/session (for example `["en_US"]` or `["phone"]`). |


### Usage

##### Kotlin

```kotlin
import com.adobe.marketing.mobile.flags.FeatureEvaluationContext

val attrs = mapOf(
    "locale" to listOf("en_US"),
    "platform" to listOf("ANDROID"),
    "appVersion" to listOf("3.0.0")
)

val ctx = FeatureEvaluationContext.builder()
    .withAttributes(attrs)
    .build()
```

##### Java

```java
import com.adobe.marketing.mobile.flags.FeatureEvaluationContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

Map<String, List<String>> attrs = new HashMap<>();
attrs.put("locale", Arrays.asList("en_US"));
attrs.put("platform", Arrays.asList("ANDROID"));
attrs.put("appVersion", Arrays.asList("3.0.0"));

FeatureEvaluationContext ctx = FeatureEvaluationContext.builder()
        .withAttributes(attrs)
        .build();
```

### Sample targeting attributes


| Attribute    | Description            | Example values            |
| ------------ | ---------------------- | ------------------------- |
| `locale`     | User's locale/language | `["en_US"]`, `["fr_FR"]`  |
| `platform`   | Platform identifier    | `["ANDROID"]`             |
| `appVersion` | Application version    | `["3.0.0"]`               |
| `deviceType` | Device type            | `["phone"]`, `["tablet"]` |


---

## API reference

### Feature evaluation

`isFeatureEnabled` returns whether an Experience Flags feature is on or off for the given context. Pass `featureKey`, an optional `FeatureEvaluationContext` with targeting attributes, and a callback.

#### Signature

##### Kotlin

```kotlin
Flag.isFeatureEnabled(
    featureKey: String,
    evaluationContext: FeatureEvaluationContext,
    callback: AdobeCallback<Boolean>
)
```

##### Java

```java
Flag.isFeatureEnabled(
    String featureKey,
    FeatureEvaluationContext evaluationContext,
    AdobeCallback<Boolean> callback);
```

#### Parameters


| Parameter           | Type                       | Description                                                                                                                                |
| ------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `featureKey`        | `String`                   | Feature key to evaluate in Experience Flags                                                                                              |
| `evaluationContext` | `FeatureEvaluationContext` | Optional targeting attributes via `withAttributes(...)`. Identity is resolved automatically. See [Evaluation context](#evaluation-context). |
| `callback`          | `AdobeCallback<Boolean>`   | Invoked with `true` if the feature is enabled, `false` otherwise. You can also pass `AdobeCallbackWithError<Boolean>` to handle `fail(...)`. |


#### Examples

##### Kotlin

```kotlin
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.flags.Flag

Flag.isFeatureEnabled(
    "new-checkout-experience",
    ctx,
    object : AdobeCallback<Boolean> {
        override fun call(isEnabled: Boolean?) {
            if (isEnabled == true) {
                showNewCheckout()
            } else {
                showDefaultCheckout()
            }
        }
    }
)
```

##### Java

```java
import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.flags.Flag;

Flag.isFeatureEnabled(
    "new-checkout-experience",
    ctx,
    new AdobeCallback<Boolean>() {
        @Override
        public void call(Boolean isEnabled) {
            if (Boolean.TRUE.equals(isEnabled)) {
                showNewCheckout();
            } else {
                showDefaultCheckout();
            }
        }
    }
);
```

---

### getFeature

`getFeature` returns the evaluated feature payload for the provided context. Use this API when you need more than enabled/disabled and want feature metadata or values.

#### Signature

##### Kotlin

```kotlin
Flag.getFeature(
    featureKey: String,
    evaluationContext: FeatureEvaluationContext,
    callback: AdobeCallback<FeatureEvaluationResult>
)
```

##### Java

```java
Flag.getFeature(
    String featureKey,
    FeatureEvaluationContext evaluationContext,
    AdobeCallback<FeatureEvaluationResult> callback);
```

#### Parameters


| Parameter           | Type                                     | Description                                                                                                                                |
| ------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `featureKey`        | `String`                                 | Feature key to evaluate in Experience Flags                                                                                              |
| `evaluationContext` | `FeatureEvaluationContext`               | Optional targeting attributes via `withAttributes(...)`. Identity is resolved automatically. See [Evaluation context](#evaluation-context). |
| `callback`          | `AdobeCallback<FeatureEvaluationResult>` | Invoked with the evaluated feature payload; may be `null` when the feature is not found. You can also pass `AdobeCallbackWithError<FeatureEvaluationResult>` to handle `fail(...)`. |


#### Response

#### `FeatureEvaluationResult`


| Field             | Type              | Description                                      |
| ----------------- | ----------------- | ------------------------------------------------ |
| `id`              | `Int`             | Numeric feature identifier                       |
| `key`             | `String`          | Feature key                                      |
| `featureGroupKey` | `String?`         | Feature group key when available                 |
| `meta`            | `String?`         | Feature metadata as a JSON string when available |
| `analyticsParam`  | `AnalyticsParam?` | Analytics details for the evaluated feature      |


#### `AnalyticsParam`


| Field            | Type      | Description                      |
| ---------------- | --------- | -------------------------------- |
| `featureGroupId` | `Int`     | Numeric feature group identifier |
| `featureId`      | `Int`     | Numeric feature identifier       |
| `variantId`      | `String?` | Variant identifier               |


#### Examples

##### Kotlin

```kotlin
import com.adobe.marketing.mobile.AdobeCallback
import com.adobe.marketing.mobile.flags.FeatureEvaluationResult
import com.adobe.marketing.mobile.flags.Flag

Flag.getFeature(
    "new-checkout-experience",
    ctx,
    object : AdobeCallback<FeatureEvaluationResult> {
        override fun call(feature: FeatureEvaluationResult?) {
            val meta = feature?.meta
            if (!meta.isNullOrEmpty()) {
                applyMetaDrivenExperience(meta)
            } else {
                showFallbackExperience()
            }
        }
    }
)
```

##### Java

```java
import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.flags.FeatureEvaluationResult;
import com.adobe.marketing.mobile.flags.Flag;

Flag.getFeature(
    "new-checkout-experience",
    ctx,
    new AdobeCallback<FeatureEvaluationResult>() {
        @Override
        public void call(FeatureEvaluationResult feature) {
            String meta = feature != null ? feature.getMeta() : null;
            if (meta != null && !meta.isEmpty()) {
                applyMetaDrivenExperience(meta);
            } else {
                showFallbackExperience();
            }
        }
    }
);
```

---

### extensionVersion

Returns the version string of the Experience Flags extension.

#### Syntax

```kotlin
Flag.extensionVersion(): String
```

#### Example

##### Kotlin

```kotlin
val version = Flag.extensionVersion()
```

##### Java

```java
String version = Flag.extensionVersion();
```

---

### API summary


| API                                                                                                                                                  | Returns                                |
| ---------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------- |
| `isFeatureEnabled(featureKey, evaluationContext, callback)`. Optional targeting attributes. Identity resolved automatically. See [Feature evaluation](#feature-evaluation). | `Boolean` via callback                 |
| `getFeature(featureKey, evaluationContext, callback)`. Returns the evaluated feature payload for the given context. See [getFeature](#getfeature).   | `FeatureEvaluationResult` via callback |
| `extensionVersion()`                                                                                                                                 | `String`                               |

---

## Exposure batching (Edge)

Feature evaluations can emit **exposure events** to Adobe Experience Platform Edge as `decisioning.propositionDisplay` events. The extension **batches** these events for efficiency and accurate impression counting.

| Condition | Queued for exposure? |
| --------- | -------------------- |
| Feature evaluated with valid `analyticsParam.variantId` | **Yes** (includes control cohort) |
| Feature not found | No |
| No `analyticsParam` or `variantId == null` | No |

API responses from `getFeature` / `isFeatureEnabled` are returned **immediately**. Edge dispatch is **asynchronous** and uses these flush triggers:

1. **20 unique `aggregationKey` values** queued (immediate flush), **or** a **150-second one-shot timer** while pending work exists (**whichever comes first**). The timer stops when the queue drains to idle and restarts on the next enqueue. It is paused while the app is backgrounded.
2. App moves to **background** (Lifecycle pause) — timer cancelled and pending events flushed.
3. App returns to **foreground** (Lifecycle start) — timer restarts if pending work remains.
4. Extension **shutdown** (best-effort async flush).

**Aggregation vs Edge `correlationID`**

The extension uses an internal **`aggregationKey`** to decide which evaluations are merged in the queue before flush. Edge XDM `scopeDetails.correlationID` is built separately at dispatch time and is **not** always the same string as `aggregationKey`.

| Feature type | `aggregationKey` (queue dedup / batch count) | `correlationID` (Edge XDM) |
| ------------ | ---------------------------------------------- | -------------------------- |
| Standalone | `F-{featureId}-{variantId}` | `F-{featureId}-{variantId}` (same) |
| Feature group | `FG-{releaseId}-{featureId}-{variantId}` | `FG-{releaseId}-{variantId}` (group-level) |

**Behavior**

- Duplicate evaluations of the **same feature** in one flush window produce **one** Edge event with `propositionEventType.display` set to the evaluation count and `timestamp` set to the **last** evaluation time.
- Distinct features in the **same feature group** (same variant) produce **separate** Edge events. They may share the same group-level `correlationID` but include different per-feature `items` in the XDM payload.

Contributors: see [`code/flags/README.md`](code/flags/README.md) for module layout, source files, and test commands.

---

## Contributing

Contributions to the Flags Extension are welcome. See [CONTRIBUTING](.github/CONTRIBUTING.md) for development setup, local commands, and pull request guidelines.

## Licensing

This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.
