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

/** Response wrapper for feature API calls. */
public class FlagApiResponse {

    private final EdgeResponse edgeResponse;
    private final String etag;
    private final boolean isChanged;

    public FlagApiResponse(EdgeResponse edgeResponse, String etag, boolean isChanged) {
        this.edgeResponse = edgeResponse;
        this.etag = etag;
        this.isChanged = isChanged;
    }

    /**
     * Parsed combined response body.
     *
     * @return parsed body, or {@code null} when absent
     */
    public EdgeResponse getEdgeResponse() {
        return edgeResponse;
    }

    /**
     * Parsed feature groups from the combined response.
     *
     * @return feature groups, or {@code null} when {@link #getEdgeResponse()} is {@code null}
     */
    public FeaturesResponse[] getFeaturesResponses() {
        return edgeResponse != null ? edgeResponse.getFeatureGroups() : null;
    }

    /**
     * Get the ETag for cache validation.
     *
     * @return ETag string
     */
    public String getEtag() {
        return etag;
    }

    /**
     * Check if the data has changed since the last request.
     *
     * @return {@code true} when the response carried a new body
     */
    public boolean isChanged() {
        return isChanged;
    }

    /**
     * Get the server-requested poll interval.
     *
     * @return poll interval in seconds, or {@code null} if not specified by the server
     */
    public Integer getPollInterval() {
        return edgeResponse != null ? edgeResponse.getPollInterval() : null;
    }
}
