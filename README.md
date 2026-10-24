# Adobe Experience Platform - Flags extension for Android

[![Build and Test](https://github.com/adobe/aepsdk-flags-android/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/adobe/aepsdk-flags-android/actions/workflows/build-and-test.yml)
[![Code Coverage](https://codecov.io/gh/adobe/aepsdk-flags-android/branch/main/graph/badge.svg)](https://codecov.io/gh/adobe/aepsdk-flags-android)

## About this project
The AEPFlags extension for Adobe Experience Platform Mobile SDKs evaluates Experience Flags feature flags in your mobile app. Flag definitions are downloaded once and then evaluated on the device, and feature exposures are measured through Adobe Experience Platform.

## Installation

Integrate the AEPFlags extension into your app by including the following in your app level gradle file's `dependencies`:

```groovy
    implementation platform('com.adobe.marketing.mobile:sdk-bom:3.+')
    implementation 'com.adobe.marketing.mobile:core'
    implementation 'com.adobe.marketing.mobile:lifecycle'
    implementation 'com.adobe.marketing.mobile:edge'
    implementation 'com.adobe.marketing.mobile:edgeidentity'
    implementation 'com.adobe.marketing.mobile:flags:<latest-version>'
```

> [!NOTE]
> The Flags extension isn't part of the Mobile SDK BOM yet, so you must specify its version. Use the latest version from [Maven Central](https://central.sonatype.com/artifact/com.adobe.marketing.mobile/flags).

### Development

**Open the project**

To open and run the project, open the `code/build.gradle.kts` file in Android Studio.

**Run demo application**
- Set `LAUNCH_ENVIRONMENT_FILE_ID` in `code/gradle.properties` to the Environment File ID of your mobile property.
- Once you've opened the project in Android Studio (see above), select the `testapp` runnable and your favorite emulator, and run the program.

## Documentation
Additional documentation for configuration and SDK usage can be found under the [Documentation](Documentation/README.md) directory.

## Related Projects

| Project                                                      | Description                                                  |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [Core extensions](https://github.com/adobe/aepsdk-core-android) | The Mobile Core represents the foundation of the Adobe Experience Platform Mobile SDK. |
| [Edge Network extension](https://github.com/adobe/aepsdk-edge-android) | The Edge Network extension allows you to send data to the Adobe Experience Platform (AEP) from a mobile application. |
| [Identity for Edge Network extension](https://github.com/adobe/aepsdk-edgeidentity-android) | The Identity for Edge Network extension enables identity management from a mobile app when using the Edge Network extension. |

## Contributing
Contributions are welcomed! Read the [CONTRIBUTING](.github/CONTRIBUTING.md) for more information.

## Licensing
This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.

