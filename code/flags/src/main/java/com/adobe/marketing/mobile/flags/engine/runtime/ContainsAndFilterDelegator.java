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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegator for handling CONTAINS operator with nested FILTER type.
 *
 * <p>This handles validation scenarios where a user attribute contains a list of nested objects
 * (UserAttributes), and the filter checks if any of those nested objects match a sub-filter.
 */
class ContainsAndFilterDelegator implements FilterValidatorDelegator {

    private static final Logger logger = LoggerFactory.getLogger(ContainsAndFilterDelegator.class);
    private static final String SELECTED_ATTR_PREFIX = "selected_";
    private static final int DEFAULT_FILTER_ID = 0;

    private final FilterService filterService;

    /**
     * Create a delegator with a filter service.
     *
     * @param filterService Filter service for nested filter evaluation
     */
    public ContainsAndFilterDelegator(FilterService filterService) {
        this.filterService = filterService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean delegate(Object stateValue, Object filterValue, Object relationalEquality) {
        IFilter filter;
        try {
            filter = getFilterFromValue(filterValue);
        } catch (Exception e) {
            logger.error("Error getting filter from value: {}", e.getMessage());
            return false;
        }

        if (!(stateValue instanceof List)) {
            return false;
        }

        List<?> states = (List<?>) stateValue;
        for (Object state : states) {
            if (state instanceof UserAttributes) {
                if (filterService.isValid((UserAttributes) state, filter)) {
                    return true;
                }
            } else if (state instanceof Map) {
                // Convert map to UserAttributes
                UserAttributes attrs = mapToUserAttributes((Map<String, Object>) state);
                if (filterService.isValid(attrs, filter)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public IFilter.FilterResult delegateWithReturnValues(
            Object stateValue,
            Object filterValue,
            Object relationalEquality,
            int id,
            String attrId) {

        IFilter filter;
        try {
            filter = getFilterFromValue(filterValue);
        } catch (Exception e) {
            logger.error("Error getting filter from value: {}", e.getMessage());
            return new IFilter.FilterResult(false, new UserAttributes());
        }

        if (!(stateValue instanceof List)) {
            return new IFilter.FilterResult(false, new UserAttributes());
        }

        List<?> states = (List<?>) stateValue;

        // Find first matching state
        for (Object state : states) {
            UserAttributes attrs = toUserAttributes(state);
            if (attrs != null && filterService.isValid(attrs, filter)) {
                UserAttributes returnValues = generateReturnValues(id, attrs);
                return new IFilter.FilterResult(true, returnValues);
            }
        }

        // No match found, return first state with false
        if (!states.isEmpty()) {
            UserAttributes firstState = toUserAttributes(states.get(0));
            if (firstState != null) {
                UserAttributes returnValues = generateReturnValues(id, firstState);
                return new IFilter.FilterResult(false, returnValues);
            }
        }

        return new IFilter.FilterResult(false, new UserAttributes());
    }

    @Override
    public List<UserAttributes> delegateWithAllReturnValues(
            Object stateValue,
            Object filterValue,
            Object relationalEquality,
            int id,
            String attrId) {

        List<UserAttributes> validStates = new ArrayList<>();

        IFilter filter;
        try {
            filter = getFilterFromValue(filterValue);
        } catch (Exception e) {
            logger.error("Error getting filter from value: {}", e.getMessage());
            return validStates;
        }

        if (!(stateValue instanceof List)) {
            return validStates;
        }

        List<?> states = (List<?>) stateValue;

        for (Object state : states) {
            UserAttributes attrs = toUserAttributes(state);
            if (attrs != null && filterService.isValid(attrs, filter)) {
                validStates.add(attrs);
            }
        }

        return validStates;
    }

    @Override
    public void preprocessUserAttributes(Object stateValue, UserAttributes objectToValidate) {
        if (!(stateValue instanceof List)) {
            return;
        }

        List<?> states = (List<?>) stateValue;
        UserAttributes selectedAttrs = getSelectedAttributes(objectToValidate);

        for (Object state : states) {
            if (state instanceof UserAttributes) {
                ((UserAttributes) state).addOrUpdateUserAttributes(selectedAttrs);
            }
        }
    }

    /** Get filter from various value types. */
    private IFilter getFilterFromValue(Object filterValue) {
        if (filterValue == null) {
            return new EmptyFilter();
        }

        if (filterValue instanceof IFilter) {
            return (IFilter) filterValue;
        }

        if (filterValue instanceof Map || filterValue instanceof String) {
            return filterService.getIFilterFromMap(filterValue);
        }

        return new EmptyFilter();
    }

    /** Convert object to UserAttributes. */
    @SuppressWarnings("unchecked")
    private UserAttributes toUserAttributes(Object obj) {
        if (obj instanceof UserAttributes) {
            return (UserAttributes) obj;
        }
        if (obj instanceof Map) {
            return mapToUserAttributes((Map<String, Object>) obj);
        }
        return null;
    }

    /** Convert map to UserAttributes. */
    private UserAttributes mapToUserAttributes(Map<String, Object> map) {
        UserAttributes attrs = new UserAttributes();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                attrs.addAttribute(entry.getKey(), entry.getValue());
            }
        }
        return attrs;
    }

    /** Generate return values with matched attribute keys. */
    private UserAttributes generateReturnValues(int id, UserAttributes selectedValue) {
        UserAttributes returnList = new UserAttributes();

        if (id == DEFAULT_FILTER_ID) {
            return returnList;
        }

        for (Map.Entry<String, List<String>> entry : selectedValue.getAttributes().entrySet()) {
            if (entry.getKey().startsWith(SELECTED_ATTR_PREFIX)) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                returnList.addAttribute(generateMatchedAttributeKey(id, entry.getKey()), values);
            }
        }

        return returnList;
    }

    /** Extract selected attributes from user attributes. */
    private UserAttributes getSelectedAttributes(UserAttributes userAttributes) {
        UserAttributes selectedAttrs = new UserAttributes();

        Map<String, List<String>> attrs = userAttributes.getAttributes();
        for (String attrKey : attrs.keySet()) {
            if (attrKey.startsWith(SELECTED_ATTR_PREFIX)) {
                selectedAttrs.addAttribute(attrKey, attrs.get(attrKey));
            }
        }

        return selectedAttrs;
    }

    /** Generate matched attribute key. */
    private String generateMatchedAttributeKey(int id, String attribute) {
        return "matched_" + id + "_" + attribute;
    }
}
