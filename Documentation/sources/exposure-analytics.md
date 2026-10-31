# Exposure analytics

When a feature evaluation returns a variant, the Experience Flags extension sends an exposure event to Adobe Experience Platform through the Edge Network extension. Each event is sent as an XDM `decisioning.propositionDisplay` event. Users in a feature-level control cohort are included.

## Batching

Exposure events are batched. Repeated evaluations of the same feature are combined into a single event, and `propositionEventType.display` holds the evaluation count.

A batch is sent when any of the following happens:

- 20 unique features have been evaluated.
- 150 seconds have passed since the first pending evaluation.
- The app goes to the background (`MobileCore.lifecyclePause`).

Pending exposures are held in memory. If the app process is killed before a batch is sent, those exposures are lost.

## Correlation IDs

`scopeDetails.correlationID` in the XDM payload uses one of these formats:

| Feature type | `correlationID` |
| ------------ | --------------- |
| Standalone feature | `F-{featureId}-{variantId}` |
| Feature in a feature group | `FG-{featureGroupId}-{variantId}` |

