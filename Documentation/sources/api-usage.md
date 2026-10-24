# Experience Flags API usage

- [extensionVersion](#extensionversion)
- [isFeatureEnabled](#isfeatureenabled)
- [getFeature](#getfeature)
- [FeatureEvaluationContext](#featureevaluationcontext)
- [Handling errors](#handling-errors)

---

## extensionVersion

Returns the version of the Experience Flags extension.

#### Java

```java
String version = Flag.extensionVersion();
```

#### Kotlin

```kotlin
val version = Flag.extensionVersion()
```

---

## isFeatureEnabled

Returns whether a feature is enabled for the current user. The result is `false` when the feature isn't found, its targeting criteria aren't met, or the user is in a control cohort.

#### Signature

```java
public static void isFeatureEnabled(
        @NonNull String featureKey,
        @NonNull FeatureEvaluationContext evaluationContext,
        @NonNull AdobeCallback<Boolean> callback);
```

#### Java

```java
Flag.isFeatureEnabled("new-checkout", ctx, new AdobeCallbackWithError<Boolean>() {
    @Override
    public void call(Boolean isEnabled) {
        runOnUiThread(() -> {
            if (Boolean.TRUE.equals(isEnabled)) {
                showNewCheckout();
            } else {
                showDefaultCheckout();
            }
        });
    }

    @Override
    public void fail(AdobeError error) {
        runOnUiThread(() -> showDefaultCheckout());
    }
});
```

#### Kotlin

```kotlin
Flag.isFeatureEnabled("new-checkout", ctx, object : AdobeCallbackWithError<Boolean> {
    override fun call(isEnabled: Boolean?) {
        runOnUiThread { if (isEnabled == true) showNewCheckout() else showDefaultCheckout() }
    }

    override fun fail(error: AdobeError?) {
        runOnUiThread { showDefaultCheckout() }
    }
})
```

---

## getFeature

Returns the evaluated feature for the current user, or `null` if the feature isn't found or doesn't apply to the user.

#### Signature

```java
public static void getFeature(
        @NonNull String featureKey,
        @NonNull FeatureEvaluationContext evaluationContext,
        @NonNull AdobeCallback<FeatureEvaluationResult> callback);
```

#### Java

```java
Flag.getFeature("new-checkout", ctx, new AdobeCallbackWithError<FeatureEvaluationResult>() {
    @Override
    public void call(FeatureEvaluationResult feature) {
        final String meta = feature != null ? feature.getMeta() : null;
        runOnUiThread(() -> applyExperience(meta));
    }

    @Override
    public void fail(AdobeError error) {
        runOnUiThread(() -> applyExperience(null));
    }
});
```

#### Kotlin

```kotlin
Flag.getFeature("new-checkout", ctx, object : AdobeCallbackWithError<FeatureEvaluationResult> {
    override fun call(feature: FeatureEvaluationResult?) {
        runOnUiThread { applyExperience(feature?.meta) }
    }

    override fun fail(error: AdobeError?) {
        runOnUiThread { applyExperience(null) }
    }
})
```

#### FeatureEvaluationResult

| Property | Type | Description |
| -------- | ---- | ----------- |
| `id` | `int` | Feature ID |
| `key` | `String` | Feature key |
| `featureGroupKey` | `String` (nullable) | Feature group key |
| `meta` | `String` (nullable) | Metadata configured for the feature, as decoded UTF-8 text. If you stored JSON, parse it yourself. |
| `analyticsParam` | `AnalyticsParam` (nullable) | Analytics details: `featureGroupId`, `featureId`, `variantId` |

---

## FeatureEvaluationContext

Holds the targeting attributes that your flag rules use. Identity is read automatically from Identity for Edge Network, so you don't pass it in.

#### Java

```java
Map<String, List<String>> attrs = new HashMap<>();
attrs.put("locale", Arrays.asList("en_US"));
attrs.put("appVersion", Arrays.asList("3.0.0"));

FeatureEvaluationContext ctx = FeatureEvaluationContext.builder()
        .withAttributes(attrs)
        .build();
```

#### Kotlin

```kotlin
val ctx = FeatureEvaluationContext.builder()
    .withAttributes(mapOf("locale" to listOf("en_US"), "appVersion" to listOf("3.0.0")))
    .build()
```

- The context is required. If you have no attributes, pass `FeatureEvaluationContext.builder().build()`.
- You can call `withAttributes` only once per builder.
- The context is immutable, so you can reuse it across calls.

---

## Custom identity

By default, users are split into variants by their ECID. A feature flag can instead split users by a custom identity, such as a CRM ID or loyalty ID, so that a user gets the same variant on every device.

To use a custom identity:

1. When you create the feature flag in Experience Flags, select the identity namespace to split variants by (for example, `userCRMId`).
2. In your app, add the user's identity under the **same namespace** with the Identity for Edge Network [`updateIdentities`](https://developer.adobe.com/client-sdks/documentation/identity-for-edge-network/api-reference/#updateidentities) API, before you evaluate the flag.

#### Java

```java
IdentityMap identityMap = new IdentityMap();
identityMap.addItem(
        new IdentityItem("1111", AuthenticatedState.AUTHENTICATED, true),
        "userCRMId"); // must match the namespace selected on the feature flag
Identity.updateIdentities(identityMap);
```

#### Kotlin

```kotlin
val identityMap = IdentityMap()
identityMap.addItem(
    IdentityItem("1111", AuthenticatedState.AUTHENTICATED, true),
    "userCRMId" // must match the namespace selected on the feature flag
)
Identity.updateIdentities(identityMap)
```

`Identity`, `IdentityMap`, `IdentityItem`, and `AuthenticatedState` are in the `com.adobe.marketing.mobile.edge.identity` package.

- The namespace must match exactly, including case.
- If the namespace has several identities, the one marked as primary is used. Otherwise, the first one is used.
- If the identity is missing when a flag is evaluated, the user is split by a temporary ID that changes each time the app starts, so the user's variant can change between app launches. Set the identity before you evaluate the flag.

---

## Handling errors

- Use `AdobeCallbackWithError` and show your default experience in `fail`. A plain `AdobeCallback` isn't called when an error occurs.
- Callbacks run on a background thread. Switch to the main thread before you update UI.
- If no result arrives within 10 seconds, `fail` is called with `AdobeError.CALLBACK_TIMEOUT`. This can happen on the first call after launch, while the extension is still downloading flag definitions.
- `fail` is called with `AdobeError.UNEXPECTED_ERROR` when:
  - the feature key is empty,
  - the Experience Flags configuration is missing, or
  - the extension couldn't download flag definitions at launch (for example, when the device is offline). The extension retries the download on the next configuration update.
