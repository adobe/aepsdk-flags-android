# Getting started with the Experience Flags extension

## Prerequisites

- A mobile property in [Adobe Experience Platform Data Collection](https://experience.adobe.com/#/data-collection/) with the following extensions installed and configured:
  - Mobile Core
  - Lifecycle
  - Edge Network (with a datastream)
  - Identity for Edge Network
  - Experience Flags
- Android API 21 (Android 5.0) or later
- Mobile Core 3.6.0 or later (`sdk-bom` 3.17.0 or later)

## Configure the Experience Flags extension in Data Collection

1. In Data Collection, select **Tags** and open your mobile property.
2. Go to **Extensions** > **Catalog**, search for **Experience Flags**, and select **Install**.
3. Enter the extension settings and select **Save**:

   | Setting | Description |
   | ------- | ----------- |
   | **Sandbox** | The Adobe Experience Platform sandbox that contains your Experience Flags configuration. |
   | **Application ID** | The identifier of your application in Experience Flags. |

4. [Publish](https://experienceleague.adobe.com/docs/experience-platform/tags/publish/overview.html) the changes.
5. Under **Environments**, select the install icon for your environment and copy the **Environment File ID**.

## Add the extension to your app

Add the following to your app's `build.gradle` file:

```groovy
implementation platform('com.adobe.marketing.mobile:sdk-bom:3.+')
implementation 'com.adobe.marketing.mobile:core'
implementation 'com.adobe.marketing.mobile:lifecycle'
implementation 'com.adobe.marketing.mobile:edge'
implementation 'com.adobe.marketing.mobile:edgeidentity'
implementation 'com.adobe.marketing.mobile:flags:<latest-version>'
```

> [!IMPORTANT]
> The Experience Flags extension isn't included in the Mobile SDK BOM, so you must specify its version. Use the latest version from [Maven Central](https://central.sonatype.com/artifact/com.adobe.marketing.mobile/flags). Identity for Edge Network (`edgeidentity`) is required. Without it, the extension can't initialize.

For production apps, use explicit versions instead of `3.+`. For more information, see [Manage Gradle dependencies](https://developer.adobe.com/client-sdks/resources/manage-gradle-dependencies/).

No additional permissions or ProGuard/R8 rules are needed.

## Initialize the SDK

Initialize the Mobile SDK in `Application.onCreate()`. `MobileCore.initialize` registers all included extensions and tracks app lifecycle automatically.

#### Kotlin

```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileCore.setLogLevel(LoggingMode.ERROR)
        MobileCore.initialize(this, "YOUR_ENVIRONMENT_FILE_ID")
    }
}
```

#### Java

```java
public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MobileCore.setLogLevel(LoggingMode.ERROR);
        MobileCore.initialize(this, "YOUR_ENVIRONMENT_FILE_ID");
    }
}
```

### Manual registration

If your app registers extensions manually with `MobileCore.registerExtensions`, also register `Flag.EXTENSION`. Use the Identity for Edge Network class (`com.adobe.marketing.mobile.edge.identity.Identity`):

```kotlin
MobileCore.registerExtensions(
    listOf(Lifecycle.EXTENSION, Identity.EXTENSION, Edge.EXTENSION, Flag.EXTENSION)
) { }
```

In this setup, call `MobileCore.lifecycleStart(null)` in each Activity's `onResume` and `MobileCore.lifecyclePause()` in its `onPause`. The extension uses these signals to pause background refreshes and to flush exposure events when the app is backgrounded.

## Next steps

- [API usage](./api-usage.md)
- [Exposure analytics](./exposure-analytics.md)


