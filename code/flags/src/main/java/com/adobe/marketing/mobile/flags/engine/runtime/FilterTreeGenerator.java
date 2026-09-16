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

package com.adobe.marketing.mobile.flags.engine.runtime;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses JSON filter configurations into executable filter trees. */
class FilterTreeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(FilterTreeGenerator.class);

    // JSON keys
    private static final String KEY_CRITERIA = "criteria";
    private static final String KEY_ATTR = "attr";
    private static final String KEY_KEY = "key";
    private static final String KEY_FIELD = "field";
    private static final String KEY_OPERATOR = "operator";
    private static final String KEY_VALUE = "val";
    private static final String KEY_VALUE_ALT = "value";
    private static final String KEY_ID = "id";
    private static final String KEY_IS_EXPRESSION = "isExpression";
    private static final String KEY_IS_SELECTED_VAL = "isSelectedVal";
    private static final String KEY_IS_NAMED_LIST_VALUE = "isNamedListValue";

    private final Map<String, String> fieldDataTypeCache;
    private final NamedListCache namedListCache;

    /** Named list cache interface. */
    public interface NamedListCache {
        /**
         * Get values for a named list.
         *
         * @param name Named list name
         * @return List values or null if not found
         */
        Object getNamedListValues(String name);
    }

    /**
     * Create a FilterTreeGenerator with field data types.
     *
     * @param fieldDataTypeCache Map of field name to data type string
     */
    public FilterTreeGenerator(Map<String, String> fieldDataTypeCache) {
        this(fieldDataTypeCache, null);
    }

    /**
     * Create a FilterTreeGenerator with field data types and named list cache.
     *
     * @param fieldDataTypeCache Map of field name to data type string
     * @param namedListCache Named list cache for resolving named-list criteria values
     */
    public FilterTreeGenerator(
            Map<String, String> fieldDataTypeCache, NamedListCache namedListCache) {
        this.fieldDataTypeCache = fieldDataTypeCache;
        this.namedListCache = namedListCache;
    }

    /**
     * Parse a JSON criteria string into a filter tree.
     *
     * @param criteriaJson JSON string containing criteria
     * @return IFilter tree for evaluation
     */
    public IFilter getFilterTree(String criteriaJson) {
        return getFilterTree(criteriaJson, true, false);
    }

    /**
     * Parse a JSON criteria string into a filter tree.
     *
     * @param criteriaJson JSON string containing criteria
     * @param isRootTagPresent Whether the JSON has a "criteria" root tag
     * @return IFilter tree for evaluation
     */
    public IFilter getFilterTree(String criteriaJson, boolean isRootTagPresent) {
        return getFilterTree(criteriaJson, isRootTagPresent, false);
    }

    /**
     * Parse a JSON criteria string into a filter tree.
     *
     * @param criteriaJson JSON string containing criteria
     * @param isRootTagPresent Whether the JSON has a "criteria" root tag
     * @param rejectOnParseError When true, top-level parse failure yields a non-matching filter
     * @return IFilter tree for evaluation
     */
    public IFilter getFilterTree(
            String criteriaJson, boolean isRootTagPresent, boolean rejectOnParseError) {
        if (criteriaJson == null || criteriaJson.isEmpty() || "null".equals(criteriaJson)) {
            return new EmptyFilter();
        }

        try {
            JsonElement element = JsonParser.parseString(criteriaJson);
            if (element.isJsonNull()) {
                return new EmptyFilter();
            }

            JsonObject jsonObject = element.getAsJsonObject();
            JsonElement filterConfig;

            if (isRootTagPresent) {
                if (!jsonObject.has(KEY_CRITERIA)) {
                    logger.warn("Root tag 'criteria' not found, treating entire JSON as criteria");
                    filterConfig = jsonObject;
                } else {
                    filterConfig = jsonObject.get(KEY_CRITERIA);
                }
            } else {
                filterConfig = jsonObject;
            }

            return parseFilterTree(filterConfig, rejectOnParseError);

        } catch (Exception e) {
            logger.error("Error parsing filter tree: {}", e.getMessage(), e);
            return rejectOnParseError ? new RejectingFilter() : new EmptyFilter();
        }
    }

    /**
     * @param rejectOnParseError When true, parse failures yield a non-matching filter
     */
    public IFilter parseFilterTree(JsonElement configValue, boolean rejectOnParseError) {
        if (configValue == null || configValue.isJsonNull()) {
            return new EmptyFilter();
        }

        try {
            // Handle array (list of filters)
            if (configValue.isJsonArray()) {
                JsonArray array = configValue.getAsJsonArray();
                FilterExpression expression = parseFilterTreeHelper(FilterOperator.NONE, array);

                // If NONE operator with single filter, unwrap it
                if (expression.isFilterConvertible()) {
                    return expression.toFilter();
                }
                return expression;
            }

            // Handle object
            if (configValue.isJsonObject()) {
                JsonObject obj = configValue.getAsJsonObject();
                if (obj.isEmpty()) {
                    return new EmptyFilter();
                }

                // Check for logical operators first
                for (String key : obj.keySet()) {
                    FilterOperator logicalOp = FilterOperator.fromCode(key);
                    if (logicalOp != FilterOperator.NONE) {
                        return parseFilterTreeHelper(logicalOp, obj.get(key));
                    }
                }

                // Handle {"condition": "AND/OR", "rules": [...]} format
                if (obj.has("condition") && obj.has("rules")) {
                    String condition = obj.get("condition").getAsString();
                    FilterOperator op = FilterOperator.fromCode(condition);
                    if (op == FilterOperator.NONE) {
                        op = FilterOperator.AND;
                    }
                    return parseFilterTreeHelper(op, obj.get("rules"));
                }

                // It's a leaf filter
                return parseFilter(obj);
            }

            logger.warn("Unexpected config value type: {}", configValue.getClass());
            return rejectOnParseError ? new RejectingFilter() : new EmptyFilter();

        } catch (Exception e) {
            logger.error("Error in filter tree generation: {}", e.getMessage(), e);
            return rejectOnParseError ? new RejectingFilter() : new EmptyFilter();
        }
    }

    /** Helper to parse filter tree with a specific operator. */
    private FilterExpression parseFilterTreeHelper(
            FilterOperator operator, JsonElement configValue) {
        FilterExpression expression = new FilterExpression(operator);

        if (configValue == null || configValue.isJsonNull()) {
            return expression;
        }

        if (configValue.isJsonArray()) {
            JsonArray array = configValue.getAsJsonArray();
            for (JsonElement element : array) {
                addFilterOperand(expression, element);
            }
        } else {
            addFilterOperand(expression, configValue);
        }

        return expression;
    }

    /** Add a filter operand to an expression. */
    private void addFilterOperand(FilterExpression expression, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }

        JsonObject obj = element.getAsJsonObject();
        if (obj.isEmpty()) {
            return;
        }

        // Check if first key is a logical operator
        for (String key : obj.keySet()) {
            FilterOperator logicalOp = FilterOperator.fromCode(key);
            if (logicalOp != FilterOperator.NONE) {
                // It's a nested logical expression
                expression.addOperand(parseFilterTreeHelper(logicalOp, obj.get(key)));
                return;
            }
        }

        // It's a leaf filter
        expression.addOperand(parseFilter(obj));
    }

    /** Parse a single filter from a JSON object. */
    private IFilter parseFilter(JsonObject filterConfig) {
        // Extract key (supports multiple key names)
        String key = getStringValue(filterConfig, KEY_ATTR);
        if (key == null) {
            key = getStringValue(filterConfig, KEY_KEY);
        }
        if (key == null) {
            key = getStringValue(filterConfig, KEY_FIELD);
        }

        // Extract value
        JsonElement valueElement =
                filterConfig.has(KEY_VALUE)
                        ? filterConfig.get(KEY_VALUE)
                        : filterConfig.get(KEY_VALUE_ALT);

        if (valueElement == null) {
            return new EmptyFilter();
        }

        // Extract operator
        String operatorCode = getStringValue(filterConfig, KEY_OPERATOR);
        FilterComparator comparator = FilterComparator.fromString(operatorCode);
        if (comparator == null) {
            logger.warn("Invalid operator: {}", operatorCode);
            comparator = FilterComparator.EQ;
        }

        // Extract parameters
        int id = 0;
        boolean isExpression = false;
        boolean isSelectedVal = false;
        boolean isNamedListValue = false;

        if (filterConfig.has(KEY_ID)) {
            id = filterConfig.get(KEY_ID).getAsInt();
        }

        if (filterConfig.has(Constants.JSON_KEY_PARAMS)) {
            JsonObject params = filterConfig.getAsJsonObject(Constants.JSON_KEY_PARAMS);

            if (params.has(KEY_IS_EXPRESSION)) {
                isExpression = params.get(KEY_IS_EXPRESSION).getAsBoolean();
            }
            if (params.has(KEY_IS_SELECTED_VAL)) {
                isSelectedVal = params.get(KEY_IS_SELECTED_VAL).getAsBoolean();
            }
            if (params.has(KEY_IS_NAMED_LIST_VALUE)) {
                isNamedListValue = params.get(KEY_IS_NAMED_LIST_VALUE).getAsBoolean();
            }
        }

        // Get field data type
        FieldDataType dataType = getFieldDataType(key);

        // Convert value
        Object value = convertValue(valueElement, dataType, isNamedListValue);

        return new Filter(key, value, comparator, dataType, id, isExpression, isSelectedVal);
    }

    /** Get field data type from cache. */
    private FieldDataType getFieldDataType(String key) {
        if (key == null || fieldDataTypeCache == null) {
            return FieldDataType.STRING;
        }

        String typeStr = fieldDataTypeCache.get(key);
        return FieldDataType.fromString(typeStr);
    }

    /** Convert JSON value to appropriate Java type. */
    private Object convertValue(
            JsonElement valueElement, FieldDataType dataType, boolean isNamedListValue) {
        if (valueElement.isJsonNull()) {
            return null;
        }

        // Handle named list values
        if (isNamedListValue && namedListCache != null && valueElement.isJsonPrimitive()) {
            String listName = valueElement.getAsString();
            Object listValues = namedListCache.getNamedListValues(listName);
            if (listValues != null) {
                return listValues;
            }
        }

        // Handle array
        if (valueElement.isJsonArray()) {
            JsonArray array = valueElement.getAsJsonArray();
            Object[] values = new Object[array.size()];
            for (int i = 0; i < array.size(); i++) {
                values[i] = convertPrimitive(array.get(i), dataType);
            }
            return values;
        }

        // Handle primitive
        return convertPrimitive(valueElement, dataType);
    }

    /** Convert a JSON primitive to Java type. */
    private Object convertPrimitive(JsonElement element, FieldDataType dataType) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (!element.isJsonPrimitive()) {
            return element.toString();
        }

        var primitive = element.getAsJsonPrimitive();

        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }

        if (primitive.isNumber()) {
            if (dataType == FieldDataType.INTEGER) {
                return primitive.getAsInt();
            }
            if (dataType == FieldDataType.LONG) {
                return primitive.getAsLong();
            }
            if (dataType == FieldDataType.DECIMAL) {
                return primitive.getAsDouble();
            }
            return primitive.getAsNumber();
        }

        return primitive.getAsString();
    }

    /** Get string value from JSON object. */
    private String getStringValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }
}
