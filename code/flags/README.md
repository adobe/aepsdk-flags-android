# Flags module (`code/flags`)

Contributor guide for the Android **Experience Flags** extension (EventHub name `com.adobe.flags`; Gradle namespace `com.adobe.marketing.mobile.flags`).

**Integrators:** setup, Data Collection configuration, public API, and exposure batching behavior are documented in the [repository README](../../README.md). This file covers module internals, build commands, and tests only.

---

## Source layout

| Component | File | Responsibility |
| --------- | ---- | ---------------- |
| Public API | `Flag.java`, `FeatureEvaluationContext.java` | App-facing evaluation APIs |
| Extension | `FlagExtension.java` | EventHub registration, readiness gating, lifecycle |
| Client | `FlagClientManager.java` | Flags SDK lifecycle, request handling, exposure queue wiring |
| Exposure queue | `FeatureExposureQueue.java` | Batch by `aggregationKey`, dedup, one-shot timer, lifecycle pause/resume, shutdown |
| Key generation | `ExposureEventIdGenerator.java` | `generateAggregationKey()`, `generateCorrelationId()`, eligibility |
| Edge dispatch | `FlagEdgeHandler.java` | `decisioning.propositionDisplay` XDM build + Edge events (`correlationID` from feature snapshot). Profile identity is merged by Edge + Edge Identity at dispatch. |
| Flush contract | `AggregatedExposureEvent.java`, `ExposureFlushCallback.java` | Immutable flush snapshots (`aggregationKey`, `feature`, `displayCount`, timestamp) and callback |
| Constants | `internal/FlagConstants.kt` | Extension name, Edge keys, `ExposureQueue` tuning |

### Queue key vs Edge `correlationID`

| Feature type | `aggregationKey` (queue maps + batch size of 20) | `correlationID` (Edge `scopeDetails`) |
| ------------ | ------------------------------------------------ | --------------------------------------- |
| Standalone | `F-{featureId}-{variantId}` | `F-{featureId}-{variantId}` |
| Feature group | `FG-{releaseId}-{featureId}-{variantId}` | `FG-{releaseId}-{variantId}` |

`FlagClientManager.queueExposureEvent` enqueues by `aggregationKey`. `FlagEdgeHandler.buildScopeDetails` builds Edge `correlationID` from the stored `FeatureResult` at flush time — it does **not** copy `aggregationKey` for feature groups.

Exposure batching rules (flush triggers, enqueue eligibility) are defined in product terms in the [root README — Exposure batching (Edge)](../../README.md#exposure-batching-edge). Implementation constants:

```kotlin
// FlagConstants.ExposureQueue
BATCH_SIZE = 20          // unique aggregationKey values
FLUSH_INTERVAL_MS = 150_000L  // 150 seconds
```

---

## Build and test

From the **repository root**:

```bash
make unit-test          # flags unit tests
make functional-test    # device/emulator required
make assemble-app       # build testapp APK
```

| Test class | Focus |
| ---------- | ----- |
| `FeatureExposureQueueTest` | Batch by `aggregationKey`, timer, dedup, feature-group per-feature separation, coalescing, shutdown |
| `ExposureEventIdGeneratorTest` | `aggregationKey` vs `correlationID` formats, control cohort, null guards |
| `FlagEdgeHandlerTest` | XDM schema, `correlationID`, display count, timestamp |
| `FlagClientManagerTest` | Enqueue gating, lifecycle flush, background behavior |
| `FlagExposureIntegrationTest` | Evaluation → queue → flush → Edge; feature-group distinct features |
| `FlagExtensionTest` | Readiness gating, lifecycle, API paths |
| `FlagFunctionalTests` (androidTest) | Public API; no premature Edge without config |
| `FlagEdgeIdentityFunctionalTests` (androidTest) | Edge Identity XDM shared state + Flags request dispatch |

Test helpers (not shipped in the AAR):

| Source set | Helpers |
| ---------- | ------- |
| `src/test` | `FlagTestFixtures`, `ExposureQueueTestSupport`, `ExposureTestAssertions`, `TestCallerThreadExecutorService` |
| `src/androidTest` | `FlagFunctionalTestSupport`, `MonitorExtension`, `FlagFunctionalTestConstants`, `ADBCountDownLatch` |

Key regression tests for the aggregation-key split:

- `FeatureExposureQueueTest.featureGroupDistinctFeatures_sameVariant_aggregateSeparately`
- `FlagExposureIntegrationTest.featureGroupDistinctFeatures_sameVariant_flushSeparatelyWithGroupCorrelationId`

---

## Manual validation (test app)

Install `code/testapp` with valid Data Collection config (Core, Lifecycle, Edge, Identity for Edge Network, Flags). In Logcat, filter `Flags` and Edge extension logs.

1. Call `getFeature` / `isFeatureEnabled` repeatedly for the **same** flag — expect **one** Edge exposure per flush window with `display` > 1.
2. Evaluate **different** features in the same feature group — expect **separate** Edge events (may share group-level `correlationID`, different `items`).
3. Background the app (Home) — pending exposures should flush immediately.

---

## Related documentation

- [Repository README](../../README.md) — integration guide for app developers
- [Adobe Mobile SDK dependency management](https://developer.adobe.com/client-sdks/resources/manage-gradle-dependencies/)
