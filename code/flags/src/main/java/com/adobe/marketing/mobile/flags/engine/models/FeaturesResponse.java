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
import java.util.Arrays;
import java.util.Map;

/** Represents a feature group containing one or more feature flags. */
public class FeaturesResponse {

    private int featureGroupId;
    private String featureGroupName;
    private String[] features;
    private Feature[] featuresObj;
    private String criteria;
    private Integer policyId;
    private PolicyCache.PolicyDetail policy;
    private String hash;
    private Map<String, Object> params;

    public FeaturesResponse() {}

    public FeaturesResponse(FeaturesResponse other) {
        this.featureGroupId = other.featureGroupId;
        this.featureGroupName = other.featureGroupName;
        this.features =
                other.features != null
                        ? Arrays.copyOf(other.features, other.features.length)
                        : null;
        this.featuresObj =
                other.featuresObj != null
                        ? Arrays.copyOf(other.featuresObj, other.featuresObj.length)
                        : null;
        this.criteria = other.criteria;
        this.policyId = other.policyId;
        this.policy = other.policy;
        this.hash = other.hash;
        this.params = other.params;
    }

    // Getters and Setters

    public int getFeatureGroupId() {
        return featureGroupId;
    }

    public void setFeatureGroupId(int featureGroupId) {
        this.featureGroupId = featureGroupId;
    }

    public String getFeatureGroupName() {
        return featureGroupName;
    }

    public void setFeatureGroupName(String featureGroupName) {
        this.featureGroupName = featureGroupName;
    }

    public String[] getFeatures() {
        return features;
    }

    public void setFeatures(String[] features) {
        this.features = features;
    }

    public Feature[] getFeaturesObj() {
        return featuresObj;
    }

    public void setFeaturesObj(Feature[] featuresObj) {
        this.featuresObj = featuresObj;
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

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * Identity-map namespace for feature group-level A/B bucketing, resolved from {@link
     * #getParams()}.
     *
     * @return namespace key for {@code identityMap} lookup, or {@code null} when identity-map
     *     lookup should not be attempted
     */
    public String getCohortingNamespace() {
        return PolicyEvaluator.resolveCohortingNamespace(this.getParams());
    }

    @Override
    public String toString() {
        return "FeaturesResponse{"
                + "featureGroupId="
                + featureGroupId
                + ", featureGroupName='"
                + featureGroupName
                + '\''
                + ", features="
                + Arrays.toString(features)
                + ", policyId="
                + policyId
                + '}';
    }
}
