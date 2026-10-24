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

package com.adobe.marketing.mobile.flags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.adobe.marketing.mobile.flags.engine.models.AnalyticsParam;
import com.adobe.marketing.mobile.flags.engine.models.FeatureResult;
import com.adobe.marketing.mobile.flags.internal.FlagConstants;
import org.junit.Test;

public class ExposureEventIdGeneratorTest {

    @Test
    public void generateAggregationKey_nullFeature_returnsNull() {
        assertNull(ExposureEventIdGenerator.generateAggregationKey(null));
    }

    @Test
    public void generateCorrelationId_nullFeature_returnsNull() {
        assertNull(ExposureEventIdGenerator.generateCorrelationId(null));
    }

    @Test
    public void generateAggregationKey_nullAnalytics_returnsNull() {
        FeatureResult feature = new FeatureResult(1, "feature-a", "fg-group", null, null, null);

        assertNull(ExposureEventIdGenerator.generateAggregationKey(feature));
    }

    @Test
    public void generateAggregationKey_nullVariantId_returnsNull() {
        AnalyticsParam analytics = new AnalyticsParam(23261, 1, "feature-a", null);
        FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, null, analytics);

        assertNull(ExposureEventIdGenerator.generateAggregationKey(feature));
    }

    @Test
    public void generateAggregationKey_standaloneFeature_matchesCorrelationId() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "10283012");
        FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);

        assertEquals("F-173226-10283012", ExposureEventIdGenerator.generateAggregationKey(feature));
        assertEquals(
                ExposureEventIdGenerator.generateAggregationKey(feature),
                ExposureEventIdGenerator.generateCorrelationId(feature));
    }

    @Test
    public void generateCorrelationId_standaloneFeature_matchesEdgeActivityPrefix() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "10283012");
        FeatureResult feature =
                new FeatureResult(173226, "checkout-flag", "||features||", null, null, analytics);

        assertEquals("F-173226-10283012", ExposureEventIdGenerator.generateCorrelationId(feature));
    }

    @Test
    public void generateAggregationKey_featureGroup_includesFeatureId() {
        AnalyticsParam analytics = new AnalyticsParam(23261, 1, "feature-a", "10283012");
        FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, null, analytics);

        assertEquals(
                "FG-23261-1-10283012", ExposureEventIdGenerator.generateAggregationKey(feature));
    }

    @Test
    public void generateCorrelationId_featureGroup_omitsFeatureId() {
        AnalyticsParam analytics = new AnalyticsParam(23261, 1, "feature-a", "10283012");
        FeatureResult feature =
                new FeatureResult(1, "feature-a", "fg-group", null, null, analytics);

        assertEquals("FG-23261-10283012", ExposureEventIdGenerator.generateCorrelationId(feature));
    }

    @Test
    public void generateAggregationKey_featureGroup_distinctFeaturesProduceDistinctKeys() {
        AnalyticsParam analyticsA = new AnalyticsParam(23261, 1, "feature-a", "10283012");
        FeatureResult featureA =
                new FeatureResult(1, "feature-a", "fg-group", null, null, analyticsA);
        AnalyticsParam analyticsB = new AnalyticsParam(23261, 2, "feature-b", "10283012");
        FeatureResult featureB =
                new FeatureResult(2, "feature-b", "fg-group", null, null, analyticsB);

        assertEquals(
                "FG-23261-1-10283012", ExposureEventIdGenerator.generateAggregationKey(featureA));
        assertEquals(
                "FG-23261-2-10283012", ExposureEventIdGenerator.generateAggregationKey(featureB));
        assertEquals("FG-23261-10283012", ExposureEventIdGenerator.generateCorrelationId(featureA));
        assertEquals(
                ExposureEventIdGenerator.generateCorrelationId(featureA),
                ExposureEventIdGenerator.generateCorrelationId(featureB));
    }

    @Test
    public void generateCorrelationId_controlCohortVariantZero_includesVariantInCorrelationId() {
        AnalyticsParam analytics = new AnalyticsParam(-1, 173226, "checkout-flag", "0");
        FeatureResult feature =
                new FeatureResult(
                        -1,
                        null,
                        FlagConstants.Edge.STANDALONE_FEATURES_FEATURE_GROUP_KEY,
                        null,
                        null,
                        analytics);

        assertEquals("F-173226-0", ExposureEventIdGenerator.generateCorrelationId(feature));
        assertEquals(
                ExposureEventIdGenerator.generateCorrelationId(feature),
                ExposureEventIdGenerator.generateAggregationKey(feature));
    }
}
