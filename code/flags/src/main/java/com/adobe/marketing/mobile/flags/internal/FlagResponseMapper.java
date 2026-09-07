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

package com.adobe.marketing.mobile.flags.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.flags.AnalyticsParam;
import com.adobe.marketing.mobile.flags.FeatureEvaluationResult;
import java.util.Map;

/**
 * Maps {@code getFeature} response payloads into {@link FeatureEvaluationResult}.
 *
 * <p>Required top-level fields: {@code id}, {@code key}. Optional: {@code featureGroupKey}, {@code
 * meta}, {@code analyticsParam}. {@code meta} must be a string when present. When {@code
 * analyticsParam} is a non-empty map, {@code featureGroupId} and {@code featureId} are required and
 * must coerce to {@code int}.
 */
public final class FlagResponseMapper {

    private FlagResponseMapper() {}

    @Nullable public static FeatureEvaluationResult toFeatureEvaluationResult(
            @Nullable final Map<String, Object> featureMap) {
        if (featureMap == null || featureMap.isEmpty()) {
            return null;
        }

        final int id = readRequiredInt(featureMap, FlagConstants.EventDataKeys.ID);
        final String key = readRequiredString(featureMap, FlagConstants.EventDataKeys.KEY);
        final String featureGroupKey =
                readOptionalString(featureMap.get(FlagConstants.EventDataKeys.FEATURE_GROUP_KEY));
        final String meta = readOptionalString(featureMap.get(FlagConstants.EventDataKeys.META));
        final AnalyticsParam analyticsParam =
                toAnalyticsParam(featureMap.get(FlagConstants.EventDataKeys.ANALYTICS_PARAM));

        return new FeatureEvaluationResult(id, key, featureGroupKey, meta, analyticsParam);
    }

    @Nullable public static AnalyticsParam toAnalyticsParam(@Nullable final Object analyticsObj) {
        if (!(analyticsObj instanceof Map)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> analyticsMap = (Map<String, Object>) analyticsObj;
        if (analyticsMap.isEmpty()) {
            return null;
        }

        final int featureGroupId =
                readRequiredInt(analyticsMap, FlagConstants.EventDataKeys.FEATURE_GROUP_ID);
        final int featureId = readRequiredInt(analyticsMap, FlagConstants.EventDataKeys.FEATURE_ID);
        final String variantId =
                readOptionalString(analyticsMap.get(FlagConstants.EventDataKeys.VARIANT_ID));

        return new AnalyticsParam(featureGroupId, featureId, variantId);
    }

    private static int readRequiredInt(
            @NonNull final Map<String, Object> source, @NonNull final String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required numeric field: " + key);
        }
        return coerceToInt(source.get(key), key);
    }

    private static int coerceToInt(@Nullable final Object value, @NonNull final String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required numeric field: " + fieldName);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            final String s = ((String) value).trim();
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Missing required numeric field: " + fieldName);
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric field: " + fieldName, e);
            }
        }
        throw new IllegalArgumentException("Invalid numeric field: " + fieldName);
    }

    private static String readRequiredString(
            @NonNull final Map<String, Object> source, @NonNull final String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required string field: " + key);
        }
        final String value = coerceToString(source.get(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required string field: " + key);
        }
        return value;
    }

    @Nullable private static String readOptionalString(@Nullable final Object value) {
        final String s = coerceToString(value);
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s;
    }

    @Nullable private static String coerceToString(@Nullable final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            final String s = ((String) value).trim();
            return s.isEmpty() ? null : s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof CharSequence) {
            final String s = value.toString().trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }
}
