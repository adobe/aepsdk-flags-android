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

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule processor for evaluating targeting criteria against user attributes. Supports AND/OR
 * conditions with various operators.
 *
 * <p>Supported operators:
 *
 * <ul>
 *   <li>EQUALS (EQ), NOT_EQUALS (NE)
 *   <li>IN, NOT_IN
 *   <li>CONTAINS (CT), NOT_CONTAINS
 *   <li>STARTS_WITH (SW), ENDS_WITH (EW)
 *   <li>GREATER_THAN (GT), LESS_THAN (LT), GTE (GE), LTE (LE)
 *   <li>BETWEEN (BW)
 *   <li>MATCHES_REGEX (RE)
 *   <li>VERSION_GT, VERSION_LT, VERSION_EQ, VERSION_GE, VERSION_LE
 *   <li>EXISTS (EX) - Check if field exists
 *   <li>LIKE (LK) - SQL-like pattern matching
 *   <li>COLLECTION_EQUAL (CEQ) - Collection equality
 * </ul>
 */
class RuleProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RuleProcessor.class);

    private static final String CONDITION_AND = "AND";
    private static final String CONDITION_OR = "OR";
    private static final String OPERATOR = "operator";
    private static final String FIELD = "field";
    private static final String ATTRIBUTE = "attr";
    private static final String VALUE_COMPACT = "val";
    private static final String VALUE = "value";

    private final Map<String, String> fieldDataTypeCache;

    RuleProcessor(Map<String, String> fieldDataTypeCache) {
        this.fieldDataTypeCache = fieldDataTypeCache;
    }

    /**
     * Evaluate criteria against user attributes.
     *
     * @param criteriaJson Criteria JSON string
     * @param userAttributes User attributes
     * @return true if criteria matches
     */
    boolean evaluate(String criteriaJson, UserAttributes userAttributes) {
        if (criteriaJson == null || criteriaJson.isEmpty() || criteriaJson.equals("null")) {
            return true; // No criteria means always match
        }

        try {
            JsonElement element = JsonParser.parseString(criteriaJson);
            if (element.isJsonNull()) {
                return true;
            }

            JsonObject jsonObject = element.getAsJsonObject();

            // Handle "criteria" root wrapper if present
            // Format: {"criteria": {"and": [...]}}
            JsonObject criteria;
            if (jsonObject.has("criteria")) {
                JsonElement criteriaElement = jsonObject.get("criteria");
                if (criteriaElement.isJsonNull()) {
                    return true;
                }
                criteria = criteriaElement.getAsJsonObject();
            } else {
                criteria = jsonObject;
            }

            return evaluateNode(criteria, userAttributes);
        } catch (Exception e) {
            logger.error("Failed to evaluate criteria: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateNode(JsonObject node, UserAttributes userAttributes) {
        if (node.has("and")) {
            return evaluateAnd(node.getAsJsonArray("and"), userAttributes);
        }

        if (node.has("or")) {
            return evaluateOr(node.getAsJsonArray("or"), userAttributes);
        }

        if (node.has("not")) {
            return !evaluateAnd(node.getAsJsonArray("not"), userAttributes);
        }

        if (node.has("negate")) {
            return !evaluateAnd(node.getAsJsonArray("negate"), userAttributes);
        }

        if (node.has("condition") && node.has("rules")) {
            String condition = node.get("condition").getAsString();
            JsonArray rules = node.getAsJsonArray("rules");

            if (CONDITION_AND.equalsIgnoreCase(condition)) {
                return evaluateAnd(rules, userAttributes);
            } else if (CONDITION_OR.equalsIgnoreCase(condition)) {
                return evaluateOr(rules, userAttributes);
            }
        }

        // Compact rule format: {"operator": "IN", "attr": "field", "val": [...]}
        if (node.has(ATTRIBUTE) && node.has(OPERATOR)) {
            return evaluateRule(node, userAttributes);
        }

        // Legacy format ("field"/"value")
        if (node.has(FIELD) && node.has(OPERATOR)) {
            return evaluateRule(node, userAttributes);
        }

        return true;
    }

    private boolean evaluateAnd(JsonArray rules, UserAttributes userAttributes) {
        for (JsonElement ruleElement : rules) {
            if (!evaluateNode(ruleElement.getAsJsonObject(), userAttributes)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateOr(JsonArray rules, UserAttributes userAttributes) {
        for (JsonElement ruleElement : rules) {
            if (evaluateNode(ruleElement.getAsJsonObject(), userAttributes)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateRule(JsonObject rule, UserAttributes userAttributes) {
        // Support both compact format ("attr"/"val") and legacy format ("field"/"value")
        String field;
        if (rule.has(ATTRIBUTE)) {
            field = rule.get(ATTRIBUTE).getAsString();
        } else if (rule.has(FIELD)) {
            field = rule.get(FIELD).getAsString();
        } else {
            logger.warn("Rule missing field/attr: {}", rule);
            return true;
        }

        String operator = rule.get(OPERATOR).getAsString();

        // Support both "val" (compact) and "value" (legacy)
        JsonElement valueElement;
        if (rule.has(VALUE_COMPACT)) {
            valueElement = rule.get(VALUE_COMPACT);
        } else if (rule.has(VALUE)) {
            valueElement = rule.get(VALUE);
        } else {
            valueElement = null;
        }

        // Handle EXISTS operator first (checks field presence)
        if ("EXISTS".equalsIgnoreCase(operator) || "EX".equalsIgnoreCase(operator)) {
            return evaluateExists(userAttributes, field, valueElement);
        }

        List<String> userValues = userAttributes.getAttributeValues(field);

        if (userValues == null || userValues.isEmpty()) {
            // Field not present in user attributes
            return operator.contains("NOT") || operator.equals("NOT_IN");
        }

        String userValue = userValues.get(0);

        try {
            switch (operator.toUpperCase()) {
                case "EQUALS":
                case "EQ":
                    return evaluateEquals(userValue, valueElement);

                case "NOT_EQUALS":
                case "NEQ":
                case "NE":
                    return !evaluateEquals(userValue, valueElement);

                case "IN":
                    return evaluateIn(userValues, valueElement);

                case "NOT_IN":
                    return !evaluateIn(userValues, valueElement);

                case "CONTAINS":
                case "CT":
                    return evaluateContains(userValue, valueElement);

                case "NOT_CONTAINS":
                    return !evaluateContains(userValue, valueElement);

                case "STARTS_WITH":
                case "SW":
                    return evaluateStartsWith(userValue, valueElement);

                case "ENDS_WITH":
                case "EW":
                    return evaluateEndsWith(userValue, valueElement);

                case "GREATER_THAN":
                case "GT":
                    return evaluateGreaterThan(userValue, valueElement, field);

                case "LESS_THAN":
                case "LT":
                    return evaluateLessThan(userValue, valueElement, field);

                case "GREATER_THAN_OR_EQUALS":
                case "GTE":
                case "GE":
                    return evaluateGreaterThanOrEquals(userValue, valueElement, field);

                case "LESS_THAN_OR_EQUALS":
                case "LTE":
                case "LE":
                    return evaluateLessThanOrEquals(userValue, valueElement, field);

                case "BETWEEN":
                case "BW":
                    return evaluateBetween(userValue, valueElement, field);

                case "MATCHES_REGEX":
                case "REGEX":
                case "RE":
                    return evaluateRegex(userValue, valueElement);

                case "LIKE":
                case "LK":
                    return evaluateLike(userValue, valueElement);

                case "COLLECTION_EQUAL":
                case "CEQ":
                    return evaluateCollectionEqual(userValues, valueElement);

                case "VERSION_GREATER_THAN":
                case "VERSION_GT":
                    return evaluateVersionGreaterThan(userValue, valueElement);

                case "VERSION_LESS_THAN":
                case "VERSION_LT":
                    return evaluateVersionLessThan(userValue, valueElement);

                case "VERSION_EQUALS":
                case "VERSION_EQ":
                    return evaluateVersionEquals(userValue, valueElement);

                case "VERSION_GREATER_THAN_OR_EQUALS":
                case "VERSION_GTE":
                case "VERSION_GE":
                    return evaluateVersionGreaterThanOrEquals(userValue, valueElement);

                case "VERSION_LESS_THAN_OR_EQUALS":
                case "VERSION_LTE":
                case "VERSION_LE":
                    return evaluateVersionLessThanOrEquals(userValue, valueElement);

                default:
                    logger.warn("Unknown operator: {}", operator);
                    return false;
            }
        } catch (Exception e) {
            logger.error(
                    "Error evaluating rule: field={}, operator={}, error={}",
                    field,
                    operator,
                    e.getMessage());
            return false;
        }
    }

    private boolean evaluateEquals(String userValue, JsonElement targetValue) {
        if (targetValue.isJsonPrimitive()) {
            if (targetValue.getAsJsonPrimitive().isBoolean()) {
                return Boolean.parseBoolean(userValue) == targetValue.getAsBoolean();
            }
            if (targetValue.getAsJsonPrimitive().isNumber()) {
                try {
                    return Double.parseDouble(userValue) == targetValue.getAsDouble();
                } catch (NumberFormatException e) {
                    return userValue.equals(targetValue.getAsString());
                }
            }
            return userValue.equalsIgnoreCase(targetValue.getAsString());
        }
        return userValue.equals(targetValue.toString());
    }

    private boolean evaluateIn(List<String> userValues, JsonElement targetValue) {
        if (targetValue.isJsonArray()) {
            JsonArray targetArray = targetValue.getAsJsonArray();
            for (String userValue : userValues) {
                for (JsonElement element : targetArray) {
                    String targetStr =
                            element.isJsonPrimitive() ? element.getAsString() : element.toString();
                    if (userValue.equalsIgnoreCase(targetStr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean evaluateContains(String userValue, JsonElement targetValue) {
        String target =
                targetValue.isJsonPrimitive() ? targetValue.getAsString() : targetValue.toString();
        return userValue.toLowerCase().contains(target.toLowerCase());
    }

    private boolean evaluateStartsWith(String userValue, JsonElement targetValue) {
        String target =
                targetValue.isJsonPrimitive() ? targetValue.getAsString() : targetValue.toString();
        return userValue.toLowerCase().startsWith(target.toLowerCase());
    }

    private boolean evaluateEndsWith(String userValue, JsonElement targetValue) {
        String target =
                targetValue.isJsonPrimitive() ? targetValue.getAsString() : targetValue.toString();
        return userValue.toLowerCase().endsWith(target.toLowerCase());
    }

    private boolean evaluateGreaterThan(String userValue, JsonElement targetValue, String field) {
        String dataType = getFieldDataType(field);

        if ("DATE".equalsIgnoreCase(dataType) || "DATETIME".equalsIgnoreCase(dataType)) {
            return compareDates(userValue, targetValue.getAsString()) > 0;
        }

        try {
            double userNum = Double.parseDouble(userValue);
            double targetNum = targetValue.getAsDouble();
            return userNum > targetNum;
        } catch (NumberFormatException e) {
            return userValue.compareTo(targetValue.getAsString()) > 0;
        }
    }

    private boolean evaluateLessThan(String userValue, JsonElement targetValue, String field) {
        String dataType = getFieldDataType(field);

        if ("DATE".equalsIgnoreCase(dataType) || "DATETIME".equalsIgnoreCase(dataType)) {
            return compareDates(userValue, targetValue.getAsString()) < 0;
        }

        try {
            double userNum = Double.parseDouble(userValue);
            double targetNum = targetValue.getAsDouble();
            return userNum < targetNum;
        } catch (NumberFormatException e) {
            return userValue.compareTo(targetValue.getAsString()) < 0;
        }
    }

    private boolean evaluateGreaterThanOrEquals(
            String userValue, JsonElement targetValue, String field) {
        return evaluateEquals(userValue, targetValue)
                || evaluateGreaterThan(userValue, targetValue, field);
    }

    private boolean evaluateLessThanOrEquals(
            String userValue, JsonElement targetValue, String field) {
        return evaluateEquals(userValue, targetValue)
                || evaluateLessThan(userValue, targetValue, field);
    }

    private boolean evaluateBetween(String userValue, JsonElement targetValue, String field) {
        if (!targetValue.isJsonArray()) {
            return false;
        }

        JsonArray range = targetValue.getAsJsonArray();
        if (range.size() != 2) {
            return false;
        }

        return evaluateGreaterThanOrEquals(userValue, range.get(0), field)
                && evaluateLessThanOrEquals(userValue, range.get(1), field);
    }

    private boolean evaluateRegex(String userValue, JsonElement targetValue) {
        try {
            String pattern =
                    targetValue.isJsonPrimitive()
                            ? targetValue.getAsString()
                            : targetValue.toString();
            return Pattern.matches(pattern, userValue);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid regex pattern: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateVersionGreaterThan(String userValue, JsonElement targetValue) {
        return compareVersions(userValue, targetValue.getAsString()) > 0;
    }

    private boolean evaluateVersionLessThan(String userValue, JsonElement targetValue) {
        return compareVersions(userValue, targetValue.getAsString()) < 0;
    }

    private boolean evaluateVersionEquals(String userValue, JsonElement targetValue) {
        return compareVersions(userValue, targetValue.getAsString()) == 0;
    }

    private boolean evaluateVersionGreaterThanOrEquals(String userValue, JsonElement targetValue) {
        return compareVersions(userValue, targetValue.getAsString()) >= 0;
    }

    private boolean evaluateVersionLessThanOrEquals(String userValue, JsonElement targetValue) {
        return compareVersions(userValue, targetValue.getAsString()) <= 0;
    }

    private boolean evaluateExists(
            UserAttributes userAttributes, String field, JsonElement targetValue) {
        List<String> userValues = userAttributes.getAttributeValues(field);
        boolean exists = userValues != null && !userValues.isEmpty();

        // Target value can be boolean to check for existence or absence
        boolean expected = true;
        if (targetValue != null && targetValue.isJsonPrimitive()) {
            expected = targetValue.getAsBoolean();
        }

        return exists == expected;
    }

    private boolean evaluateLike(String userValue, JsonElement targetValue) {
        // SQL LIKE pattern: % = any characters, _ = single character
        String pattern =
                targetValue.isJsonPrimitive() ? targetValue.getAsString() : targetValue.toString();

        // Convert SQL LIKE pattern to regex
        String regex =
                pattern.replace(".", "\\.") // Escape dots
                        .replace("*", "\\*") // Escape asterisks
                        .replace("%", ".*") // % -> .*
                        .replace("_", "."); // _ -> .

        try {
            return Pattern.matches("(?i)" + regex, userValue);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid LIKE pattern: {}", pattern);
            return false;
        }
    }

    private boolean evaluateCollectionEqual(List<String> userValues, JsonElement targetValue) {
        if (!targetValue.isJsonArray()) {
            return false;
        }

        JsonArray targetArray = targetValue.getAsJsonArray();

        // Check if sizes match
        if (userValues.size() != targetArray.size()) {
            return false;
        }

        // Check if all values match (order-independent)
        for (JsonElement element : targetArray) {
            String targetStr =
                    element.isJsonPrimitive() ? element.getAsString() : element.toString();
            boolean found = false;
            for (String userValue : userValues) {
                if (userValue.equalsIgnoreCase(targetStr)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    private String getFieldDataType(String field) {
        if (fieldDataTypeCache != null) {
            return fieldDataTypeCache.get(field);
        }
        return null;
    }

    private int compareDates(String date1, String date2) {
        Date dt1 = parseDateTime(date1);
        Date dt2 = parseDateTime(date2);
        if (dt1 == null || dt2 == null) {
            logger.error("Failed to parse date: {} or {}", date1, date2);
            return 0;
        }
        return dt1.compareTo(dt2);
    }

    /**
     * Parse an ISO-8601 date/date-time string for comparison. Any timezone offset is ignored (the
     * wall-clock value is compared), matching the previous {@code java.time.LocalDateTime}
     * behavior. Uses {@link SimpleDateFormat} for API 21 compatibility ({@code java.time} requires
     * API 26).
     *
     * @return the parsed date, or {@code null} if none of the supported formats fully match
     */
    private Date parseDateTime(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        // Strip a trailing ISO-8601 offset (Z, +HH:MM, -HHMM, ...) so only the local part is
        // parsed.
        String local = dateStr.trim().replaceAll("(?:Z|[+-]\\d{2}:?\\d{2})$", "");
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String format : formats) {
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            sdf.setLenient(false);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition pos = new ParsePosition(0);
            Date parsed = sdf.parse(local, pos);
            if (parsed != null && pos.getIndex() == local.length()) {
                return parsed;
            }
        }
        return null;
    }

    private int compareVersions(String version1, String version2) {
        try {
            String[] parts1 = version1.split("\\.");
            String[] parts2 = version2.split("\\.");

            int maxLength = Math.max(parts1.length, parts2.length);

            for (int i = 0; i < maxLength; i++) {
                int v1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
                int v2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

                if (v1 != v2) {
                    return Integer.compare(v1, v2);
                }
            }

            return 0;
        } catch (Exception e) {
            logger.error("Failed to compare versions: {}", e.getMessage());
            return 0;
        }
    }

    private int parseVersionPart(String part) {
        // Remove any non-numeric suffix (e.g., "1-SNAPSHOT" -> "1")
        StringBuilder numStr = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                numStr.append(c);
            } else {
                break;
            }
        }
        return numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : 0;
    }
}
