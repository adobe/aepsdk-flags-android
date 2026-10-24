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

import com.adobe.marketing.mobile.flags.engine.policy.PolicyCache;
import com.adobe.marketing.mobile.flags.engine.policy.PolicyEvaluator;
import java.util.Map;

/** Represents a feature flag with its configuration and targeting criteria. */
public class Feature {

    private int id;
    private String feature;
    private String criteria;
    private Integer policyId;
    private PolicyCache.PolicyDetail policy;
    private String hash;
    private boolean enabled;
    private Object value;
    private Map<String, Object> params;
    private String meta;
    private boolean analyticsEnabled = true;

    public Feature() {}

    public Feature(Feature other) {
        this.id = other.id;
        this.feature = other.feature;
        this.criteria = other.criteria;
        this.policyId = other.policyId;
        this.policy = other.policy;
        this.hash = other.hash;
        this.enabled = other.enabled;
        this.value = other.value;
        this.params = other.params;
        this.meta = other.meta;
        this.analyticsEnabled = other.analyticsEnabled;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getName() {
        return feature;
    }

    public String getCriteria() {
        return criteria;
    }

    public void setCriteria(String criteria) {
        this.criteria = criteria;
    }

    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public PolicyCache.PolicyDetail getPolicy() {
        return policy;
    }

    public void setPolicy(PolicyCache.PolicyDetail policy) {
        this.policy = policy;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * Identity-map namespace for feature-level A/B bucketing, resolved from {@link #getParams()}.
     *
     * @return namespace key for {@code identityMap} lookup, or {@code null} when identity-map
     *     lookup should not be attempted
     */
    public String getCohortingNamespace() {
        return PolicyEvaluator.resolveCohortingNamespace(this.getParams());
    }

    /**
     * Opaque metadata string from the service response (Base64-decoded UTF-8), or {@code null}.
     *
     * @return metadata string, or {@code null}
     */
    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    /**
     * Whether analytics is enabled for this feature on the wire.
     *
     * @return {@code true} when enabled (default when absent on wire)
     */
    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }

    @Override
    public String toString() {
        return "Feature{"
                + "id="
                + id
                + ", feature='"
                + feature
                + '\''
                + ", enabled="
                + enabled
                + ", policyId="
                + policyId
                + '}';
    }
}
