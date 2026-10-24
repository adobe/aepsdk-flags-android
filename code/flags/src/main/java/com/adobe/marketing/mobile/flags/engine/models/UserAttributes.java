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

package com.adobe.marketing.mobile.flags.engine.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for user attributes used in feature evaluation. Supports single values and multi-valued
 * attributes.
 */
public class UserAttributes {

    private final Map<String, List<String>> attributes;

    public UserAttributes() {
        this.attributes = new HashMap<>();
    }

    /**
     * Create a UserAttributes from a context map (key → list of string values). Single-element
     * lists are stored as single-valued attributes.
     *
     * @param context Context map, may be null
     * @return New UserAttributes populated from the context
     */
    public static UserAttributes fromContext(Map<String, List<String>> context) {
        UserAttributes attrs = new UserAttributes();
        if (context != null) {
            for (Map.Entry<String, List<String>> entry : context.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    if (values.size() == 1) {
                        attrs.addAttribute(key, values.get(0));
                    } else {
                        attrs.addAttribute(key, values);
                    }
                }
            }
        }
        return attrs;
    }

    public UserAttributes(Map<String, ?> initialAttributes) {
        this.attributes = new HashMap<>();
        if (initialAttributes != null) {
            for (Map.Entry<String, ?> entry : initialAttributes.entrySet()) {
                addAttribute(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Add or update a single-valued attribute.
     *
     * @param key Attribute name
     * @param value Attribute value (will be converted to String)
     * @return this instance for chaining
     */
    public UserAttributes addAttribute(String key, Object value) {
        if (key != null && value != null) {
            List<String> values = new ArrayList<>();
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    values.add(String.valueOf(item));
                }
            } else {
                values.add(String.valueOf(value));
            }
            attributes.put(key, values);
        }
        return this;
    }

    /**
     * Add or update a multi-valued attribute.
     *
     * @param key Attribute name
     * @param values List of attribute values
     * @return this instance for chaining
     */
    public UserAttributes addAttribute(String key, List<String> values) {
        if (key != null && values != null) {
            attributes.put(key, new ArrayList<>(values));
        }
        return this;
    }

    /**
     * Get all attributes as a map.
     *
     * @return Map of attribute names to lists of values
     */
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    /**
     * Get a single attribute value.
     *
     * @param key Attribute name
     * @return First value if exists, null otherwise
     */
    public String getAttribute(String key) {
        List<String> values = attributes.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Get all values for an attribute.
     *
     * @param key Attribute name
     * @return List of values, or null if not found
     */
    public List<String> getAttributeValues(String key) {
        return attributes.get(key);
    }

    /**
     * Check if an attribute exists.
     *
     * @param key Attribute name
     * @return true if attribute exists
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /**
     * Remove an attribute.
     *
     * @param key Attribute name
     * @return this instance for chaining
     */
    public UserAttributes removeAttribute(String key) {
        attributes.remove(key);
        return this;
    }

    /**
     * Clear all attributes.
     *
     * @return this instance for chaining
     */
    public UserAttributes clear() {
        attributes.clear();
        return this;
    }

    /**
     * Add or update multiple attributes from another UserAttributes object.
     *
     * @param other UserAttributes to merge from
     * @return this instance for chaining
     */
    public UserAttributes addOrUpdateUserAttributes(UserAttributes other) {
        if (other != null && other.attributes != null) {
            for (Map.Entry<String, List<String>> entry : other.attributes.entrySet()) {
                if (entry.getValue() != null) {
                    this.attributes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
        }
        return this;
    }

    /**
     * Create a copy of this UserAttributes.
     *
     * @return New UserAttributes with same values
     */
    public UserAttributes copy() {
        UserAttributes copy = new UserAttributes();
        for (Map.Entry<String, List<String>> entry : this.attributes.entrySet()) {
            copy.addAttribute(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
