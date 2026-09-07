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

import com.adobe.marketing.mobile.flags.engine.exception.FlagInitException;
import okhttp3.OkHttpClient;

/**
 * Immutable configuration for the Flags Android SDK.
 *
 * <pre>{@code
 * FlagConfiguration config = FlagConfiguration.builder()
 *     .edgeDomain("your-edge-domain.example.com")
 *     .imsOrg("my-org-id")
 *     .sandboxName("prod")
 *     .clientId("my-app")
 *     .build();
 *
 * FlagClient client = FlagClient.create(config);
 * }</pre>
 *
 * <p>To share an HTTP client across multiple {@code FlagClient} instances, supply one via {@link
 * Builder#httpClient(OkHttpClient)}. The SDK applies its own timeout settings on top of the
 * provided instance and does not shut it down on {@code close()} — the caller retains ownership and
 * is responsible for its lifecycle.
 */
public class FlagConfiguration {

    private final String edgeDomain;
    private final String imsOrg;
    private final String sandboxName;
    private final String clientId;
    private final OkHttpClient httpClient;

    private FlagConfiguration(Builder builder) {
        this.edgeDomain = builder.edgeDomain;
        this.imsOrg = builder.imsOrg;
        this.sandboxName = builder.sandboxName;
        this.clientId = builder.clientId;
        this.httpClient = builder.httpClient;
    }

    /**
     * @return host-only edge domain supplied at build time
     */
    public String getEdgeDomain() {
        return edgeDomain;
    }

    /**
     * @return the IMS organization identifier
     */
    public String getImsOrg() {
        return imsOrg;
    }

    /**
     * @return the sandbox name
     */
    public String getSandboxName() {
        return sandboxName;
    }

    /**
     * @return the Flags client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the caller-supplied HTTP client, or {@code null} if none was provided.
     *
     * @return caller-supplied {@link OkHttpClient}, or {@code null}
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for creating {@link FlagConfiguration} instances. */
    public static class Builder {
        private String edgeDomain;
        private String imsOrg;
        private String sandboxName;
        private String clientId;
        private OkHttpClient httpClient;

        Builder() {}

        /**
         * Set the edge domain for feature requests. Required. Host only — no scheme or path.
         *
         * @param edgeDomain edge domain (e.g. {@code your-edge-domain.example.com})
         * @return this builder
         */
        public Builder edgeDomain(String edgeDomain) {
            this.edgeDomain = edgeDomain;
            return this;
        }

        /**
         * Set the IMS organization identifier. Required.
         *
         * @param imsOrg organization identifier
         * @return this builder
         */
        public Builder imsOrg(String imsOrg) {
            this.imsOrg = imsOrg;
            return this;
        }

        /**
         * Set the sandbox name. Required.
         *
         * @param sandboxName sandbox name
         * @return this builder
         */
        public Builder sandboxName(String sandboxName) {
            this.sandboxName = sandboxName;
            return this;
        }

        /**
         * Set the Flags client ID to fetch features for.
         *
         * @param clientId Client ID
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Supply an existing {@link OkHttpClient} for the SDK to use for HTTP requests.
         *
         * <p>The SDK derives its own client from the provided instance using {@code
         * existingClient.newBuilder()} and enforces its own timeouts, so the original instance is
         * not mutated. The caller retains ownership of the supplied client and must shut it down
         * independently.
         *
         * @param httpClient shared HTTP client
         * @return this builder
         */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Build the configuration.
         *
         * @return FlagConfiguration instance
         * @throws FlagInitException if configuration is invalid
         */
        public FlagConfiguration build() throws FlagInitException {
            validate();
            return new FlagConfiguration(this);
        }

        private void validate() throws FlagInitException {
            if (imsOrg == null || imsOrg.trim().isEmpty()) {
                throw new FlagInitException("IMS organization is required");
            }
            if (sandboxName == null || sandboxName.trim().isEmpty()) {
                throw new FlagInitException("Sandbox name is required");
            }
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new FlagInitException("Client ID is required");
            }
            if (edgeDomain == null || edgeDomain.trim().isEmpty()) {
                throw new FlagInitException("Edge domain is required");
            }
            imsOrg = imsOrg.trim();
            sandboxName = sandboxName.trim();
            clientId = clientId.trim();
            edgeDomain = normalizeEdgeDomain(edgeDomain);
        }

        private static String normalizeEdgeDomain(String edgeDomain) throws FlagInitException {
            String trimmed = edgeDomain.trim();
            if (trimmed.contains("://")) {
                throw new FlagInitException("Edge domain must not include a URL scheme");
            }
            if (trimmed.contains("/")) {
                throw new FlagInitException("Edge domain must not include a path");
            }
            return trimmed;
        }
    }
}
