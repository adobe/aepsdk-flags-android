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
import com.adobe.marketing.mobile.flags.engine.runtime.expression.ExpressionValidator;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Leaf filter for evaluating a single condition against user attributes. */
class Filter implements IFilter {

    private static final Logger logger = LoggerFactory.getLogger(Filter.class);

    private final String key;
    private Object value;
    private final FilterComparator comparator;
    private final FieldDataType dataType;
    private final int id;
    private final boolean isExpression;
    private final boolean isSelectedVal;
    private final boolean isPatch;
    private final FilterPatchesCache filterPatchesCache;

    /**
     * Create a filter.
     *
     * @param key Attribute key
     * @param value Filter value(s)
     * @param comparator Comparison operator
     * @param dataType Data type of the field
     * @param id Filter ID for tracking
     * @param isExpression Whether value is an expression
     * @param isSelectedVal Whether value references another attribute
     */
    public Filter(
            String key,
            Object value,
            FilterComparator comparator,
            FieldDataType dataType,
            int id,
            boolean isExpression,
            boolean isSelectedVal) {
        this.key = key;
        this.value = value;
        this.comparator = comparator;
        this.dataType = dataType != null ? dataType : FieldDataType.STRING;
        this.id = id;
        this.isExpression = isExpression;
        this.isSelectedVal = isSelectedVal;
        this.isPatch = (comparator == FilterComparator.PATCH);
        this.filterPatchesCache = FilterPatchesCache.getInstance();
    }

    /**
     * Simplified constructor.
     *
     * @param key the attribute key to filter on
     * @param value the value to compare against
     * @param comparator the comparison operator
     * @param dataType the data type of the field
     */
    public Filter(String key, Object value, FilterComparator comparator, FieldDataType dataType) {
        this(key, value, comparator, dataType, 0, false, false);
    }

    /**
     * Check if this filter is a patch reference.
     *
     * @return true if this is a patch filter
     */
    public boolean isPatch() {
        return isPatch;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean isValid(UserAttributes userAttributes) {
        return validateWithReturnValues(userAttributes).isValid();
    }

    @Override
    public FilterResult validateWithReturnValues(UserAttributes userAttributes) {
        try {
            // Handle PATCH operator - delegate to cached filter
            if (isPatch) {
                IFilter patchFilter = filterPatchesCache.getPatch(String.valueOf(value));
                return patchFilter.validateWithReturnValues(userAttributes);
            }

            // Handle EXISTS operator
            if (comparator == FilterComparator.EX) {
                return evaluateExists(userAttributes);
            }

            // Get user attribute value
            List<String> userValues = userAttributes.getAttributeValues(key);
            boolean hasValue = userValues != null && !userValues.isEmpty();

            // Handle null/missing values
            if (!hasValue) {
                boolean result = comparator.isNegation();
                return new FilterResult(result, new UserAttributes());
            }

            // Compute the filter value (handle expressions and selected values)
            Object filterValue = computeFilterValue(userAttributes);

            String userValue = userValues.get(0);
            boolean result = evaluate(userValue, userValues, filterValue);

            // Track matched attributes
            UserAttributes matched = new UserAttributes();
            if (result && id > 0) {
                matched.addAttribute("matched_" + id, userValue);
            }

            return new FilterResult(result, matched);

        } catch (Exception e) {
            logger.error("Filter validation error: key={}, comparator={}", key, comparator, e);
            return new FilterResult(false, new UserAttributes());
        }
    }

    /** Compute the actual filter value, handling expressions and selected values. */
    private Object computeFilterValue(UserAttributes userAttributes) {
        Object filterValue = this.value;

        // Handle expression (e.g., "today+5d")
        if (isExpression && ExpressionValidator.supportsExpression(dataType)) {
            filterValue =
                    ExpressionValidator.getExpressionValidator(dataType)
                            .parseExpression(filterValue);
        }

        // Handle selected value (reference to another attribute)
        if (isSelectedVal && filterValue instanceof String) {
            List<String> selectedValues = userAttributes.getAttributeValues((String) filterValue);
            if (selectedValues != null && !selectedValues.isEmpty()) {
                filterValue = selectedValues.get(0);
            } else {
                filterValue = null;
            }
        }

        return filterValue;
    }

    private boolean evaluate(String userValue, List<String> userValues, Object filterValue) {
        switch (comparator) {
            case EQ:
                return evaluateEquals(userValue, filterValue);
            case NE:
                return !evaluateEquals(userValue, filterValue);
            case LT:
                return evaluateCompare(userValue, filterValue) < 0;
            case LE:
                return evaluateCompare(userValue, filterValue) <= 0;
            case GT:
                return evaluateCompare(userValue, filterValue) > 0;
            case GE:
                return evaluateCompare(userValue, filterValue) >= 0;
            case IN:
                return evaluateIn(userValues, filterValue);
            case NOT_IN:
                return !evaluateIn(userValues, filterValue);
            case BW:
                return evaluateBetween(userValue, filterValue);
            case CT:
                return evaluateContains(userValue, filterValue);
            case NOT_CONTAINS:
                return !evaluateContains(userValue, filterValue);
            case SW:
                return evaluateStartsWith(userValue, filterValue);
            case EW:
                return evaluateEndsWith(userValue, filterValue);
            case RE:
                return evaluateRegex(userValue, filterValue);
            case LK:
                return evaluateLike(userValue, filterValue);
            case CEQ:
                return evaluateCollectionEqual(userValues, filterValue);
            case VERSION_EQ:
                return compareVersions(userValue, filterValue) == 0;
            case VERSION_GT:
                return compareVersions(userValue, filterValue) > 0;
            case VERSION_LT:
                return compareVersions(userValue, filterValue) < 0;
            case VERSION_GE:
                return compareVersions(userValue, filterValue) >= 0;
            case VERSION_LE:
                return compareVersions(userValue, filterValue) <= 0;
            default:
                logger.warn("Unsupported comparator: {}", comparator);
                return false;
        }
    }

    private FilterResult evaluateExists(UserAttributes userAttributes) {
        List<String> userValues = userAttributes.getAttributeValues(key);
        boolean exists = userValues != null && !userValues.isEmpty();

        boolean expected = true;
        if (value instanceof Boolean) {
            expected = (Boolean) value;
        } else if (value instanceof String) {
            expected = Boolean.parseBoolean((String) value);
        }

        return new FilterResult(exists == expected, new UserAttributes());
    }

    private boolean evaluateEquals(String userValue, Object filterValue) {
        if (filterValue == null) {
            return userValue == null;
        }

        if (dataType.isNumeric()) {
            try {
                double userNum = Double.parseDouble(userValue);
                double filterNum = toDouble(filterValue);
                return Double.compare(userNum, filterNum) == 0;
            } catch (NumberFormatException e) {
                return userValue.equalsIgnoreCase(String.valueOf(filterValue));
            }
        }

        if (filterValue instanceof Boolean || dataType == FieldDataType.BOOLEAN) {
            return Boolean.parseBoolean(userValue) == toBoolean(filterValue);
        }

        return userValue.equalsIgnoreCase(String.valueOf(filterValue));
    }

    private int evaluateCompare(String userValue, Object filterValue) {
        if (filterValue == null) {
            return 1;
        }

        if (dataType.isDate()) {
            // Handle epoch milliseconds from expression evaluation
            if (filterValue instanceof Long) {
                try {
                    long userEpoch = Long.parseLong(userValue);
                    return Long.compare(userEpoch, (Long) filterValue);
                } catch (NumberFormatException e) {
                    // User value is a date string, convert filter value
                    return compareDates(userValue, String.valueOf(filterValue));
                }
            }
            return compareDates(userValue, String.valueOf(filterValue));
        }

        if (dataType.isNumeric()) {
            try {
                double userNum = Double.parseDouble(userValue);
                double filterNum = toDouble(filterValue);
                return Double.compare(userNum, filterNum);
            } catch (NumberFormatException e) {
                return userValue.compareTo(String.valueOf(filterValue));
            }
        }

        return userValue.compareTo(String.valueOf(filterValue));
    }

    private boolean evaluateIn(List<String> userValues, Object filterValue) {
        List<?> filterList;

        if (filterValue instanceof List) {
            filterList = (List<?>) filterValue;
        } else if (filterValue instanceof Object[]) {
            filterList = Arrays.asList((Object[]) filterValue);
        } else if (filterValue instanceof String) {
            filterList = Arrays.asList(((String) filterValue).split(","));
        } else {
            filterList = Arrays.asList(filterValue);
        }

        for (String userValue : userValues) {
            for (Object fv : filterList) {
                if (userValue.equalsIgnoreCase(String.valueOf(fv).trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean evaluateBetween(String userValue, Object filterValue) {
        List<?> range;

        if (filterValue instanceof List) {
            range = (List<?>) filterValue;
        } else if (filterValue instanceof Object[]) {
            range = Arrays.asList((Object[]) filterValue);
        } else {
            return false;
        }

        if (range.size() < 2) {
            return false;
        }

        Object start = range.get(0);
        Object end = range.get(1);

        if (dataType.isNumeric()) {
            try {
                double userNum = Double.parseDouble(userValue);
                double startNum = toDouble(start);
                double endNum = toDouble(end);
                return userNum >= startNum && userNum <= endNum;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (dataType.isDate()) {
            int startCmp = compareDates(userValue, String.valueOf(start));
            int endCmp = compareDates(userValue, String.valueOf(end));
            return startCmp >= 0 && endCmp <= 0;
        }

        return userValue.compareTo(String.valueOf(start)) >= 0
                && userValue.compareTo(String.valueOf(end)) <= 0;
    }

    private boolean evaluateContains(String userValue, Object filterValue) {
        if (filterValue == null) return false;
        return userValue.toLowerCase().contains(String.valueOf(filterValue).toLowerCase());
    }

    private boolean evaluateStartsWith(String userValue, Object filterValue) {
        if (filterValue == null) return false;
        return userValue.toLowerCase().startsWith(String.valueOf(filterValue).toLowerCase());
    }

    private boolean evaluateEndsWith(String userValue, Object filterValue) {
        if (filterValue == null) return false;
        return userValue.toLowerCase().endsWith(String.valueOf(filterValue).toLowerCase());
    }

    private boolean evaluateRegex(String userValue, Object filterValue) {
        if (filterValue == null) return false;
        try {
            return Pattern.matches(String.valueOf(filterValue), userValue);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid regex pattern: {}", filterValue, e);
            return false;
        }
    }

    private boolean evaluateLike(String userValue, Object filterValue) {
        if (filterValue == null) return false;

        // Convert SQL LIKE to regex: % -> .*, _ -> .
        String pattern =
                String.valueOf(filterValue)
                        .replace(".", "\\.")
                        .replace("*", "\\*")
                        .replace("%", ".*")
                        .replace("_", ".");

        try {
            return Pattern.matches("(?i)" + pattern, userValue);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid LIKE pattern: {}", filterValue, e);
            return false;
        }
    }

    private boolean evaluateCollectionEqual(List<String> userValues, Object filterValue) {
        List<?> filterList;

        if (filterValue instanceof List) {
            filterList = (List<?>) filterValue;
        } else if (filterValue instanceof Object[]) {
            filterList = Arrays.asList((Object[]) filterValue);
        } else {
            return false;
        }

        if (userValues.size() != filterList.size()) {
            return false;
        }

        for (Object fv : filterList) {
            boolean found = false;
            for (String userValue : userValues) {
                if (userValue.equalsIgnoreCase(String.valueOf(fv))) {
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

    private int compareVersions(String userValue, Object filterValue) {
        String filterVersion = String.valueOf(filterValue);
        try {
            String[] parts1 = userValue.split("\\.");
            String[] parts2 = filterVersion.split("\\.");

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
            logger.error("Failed to compare versions: {} vs {}", userValue, filterVersion);
            return 0;
        }
    }

    private int parseVersionPart(String part) {
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

    private double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return Double.parseDouble(String.valueOf(obj));
    }

    private boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    /**
     * Set the filter value (for expression evaluation caching).
     *
     * @param value New filter value
     */
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Filter{key='" + key + "', comparator=" + comparator + ", value=" + value + "}";
    }
}
