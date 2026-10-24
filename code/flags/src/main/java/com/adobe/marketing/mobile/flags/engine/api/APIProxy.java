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

package com.adobe.marketing.mobile.flags.engine.api;

import com.adobe.marketing.mobile.flags.engine.constants.Constants;
import com.adobe.marketing.mobile.flags.engine.constants.SdkVersion;
import com.adobe.marketing.mobile.flags.engine.exception.FlagClientException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.ResponseParseException;
import com.adobe.marketing.mobile.flags.engine.internal.parser.ResponseParser;
import com.adobe.marketing.mobile.flags.engine.models.EdgeResponse;
import com.adobe.marketing.mobile.flags.engine.models.FlagApiResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Handles all HTTP communication with Flags services. */
public class APIProxy {

    private final String edgeBaseUrl;
    private final String imsOrg;
    private final String sandboxName;
    private final OkHttpClient httpClient;
    private final boolean isInternalHttpClient;

    /**
     * Create a new API Proxy, optionally sharing a caller-supplied HTTP client.
     *
     * <p>When {@code sharedClient} is non-null, the SDK applies its own timeout settings on top of
     * it and shares its connection pool. The caller retains ownership and must shut it down
     * independently. When {@code null}, the SDK creates and owns its own client.
     *
     * @param edgeBaseUrl HTTPS base URL for feature requests
     * @param imsOrg IMS organization identifier for {@code x-gw-ims-org-id}
     * @param sandboxName sandbox name for {@code x-sandbox-name}
     * @param sharedClient optional caller-owned HTTP client to share, or {@code null}
     * @throws IllegalArgumentException if any required string is null or blank
     */
    public APIProxy(
            String edgeBaseUrl, String imsOrg, String sandboxName, OkHttpClient sharedClient) {
        this.edgeBaseUrl = requireNonBlank(edgeBaseUrl, "edgeBaseUrl").replaceAll("/$", "");
        this.imsOrg = requireNonBlank(imsOrg, "imsOrg");
        this.sandboxName = requireNonBlank(sandboxName, "sandboxName");
        this.isInternalHttpClient = (sharedClient == null);

        this.httpClient =
                (sharedClient != null ? sharedClient.newBuilder() : new OkHttpClient.Builder())
                        .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(Constants.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build();
    }

    /**
     * Fetch the combined feature payload.
     *
     * @param clientId Flags client ID
     * @param contextVersion cached context version for conditional metadata, or {@code null} on
     *     first fetch
     * @param etag cached ETag for conditional fetch, or {@code null} on first fetch
     * @return API response with parsed body, or a not-modified wrapper on HTTP 304
     * @throws FlagClientException if the request fails
     */
    public FlagApiResponse getFeatures(String clientId, String contextVersion, String etag)
            throws FlagClientException {
        try {
            return fetchFeatures(clientId, contextVersion, etag);
        } catch (FlagClientException e) {
            throw e;
        } catch (Exception e) {
            throw new FlagClientException("Failed to fetch features", e);
        }
    }

    private FlagApiResponse fetchFeatures(String clientId, String contextVersion, String etag)
            throws FlagClientException {
        HttpUrl url = buildFeatureRequestUrl(clientId, contextVersion);

        Request.Builder requestBuilder =
                new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader(Constants.HEADER_ACCEPT, Constants.ACCEPT_JSON)
                        .addHeader(Constants.HEADER_IMS_ORG, imsOrg)
                        .addHeader(Constants.HEADER_SANDBOX_NAME, sandboxName);

        if (etag != null && !etag.isEmpty()) {
            requestBuilder.addHeader(Constants.HEADER_IF_NONE_MATCH, etag);
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseEtag = response.header(Constants.HEADER_ETAG);

            if (response.code() == 304) {
                return new FlagApiResponse(null, responseEtag, false);
            }

            if (!response.isSuccessful()) {
                throw new FlagClientException("Request failed with status: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            EdgeResponse parsed;
            try {
                parsed = ResponseParser.parseEdgeResponse(body);
            } catch (ResponseParseException e) {
                throw new FlagClientException("Failed to parse features response", e);
            }
            return new FlagApiResponse(parsed, responseEtag, true);
        } catch (IOException e) {
            throw new FlagClientException("Features request failed", e);
        }
    }

    private HttpUrl buildFeatureRequestUrl(String clientId, String contextVersion) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }

        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(edgeBaseUrl + Constants.FLAGS_FEATURE_PATH)
                        .newBuilder()
                        .addQueryParameter(Constants.QUERY_CLIENT_ID, clientId);

        String sdkVersion = SdkVersion.get();
        urlBuilder.addQueryParameter(
                Constants.QUERY_SDK_VERSION,
                sdkVersion == null || sdkVersion.isEmpty() ? null : sdkVersion);

        if (contextVersion != null && !contextVersion.isEmpty()) {
            urlBuilder.addQueryParameter(Constants.QUERY_CONTEXT_VERSION, contextVersion);
        }

        return urlBuilder.build();
    }

    /**
     * Evict idle connections from the SDK-owned connection pool.
     *
     * <p>Only has effect when the SDK owns the HTTP client. When a caller-supplied client is in use
     * the pool is shared with the caller's other requests; evicting it would disrupt unrelated HTTP
     * traffic, so this call is a no-op in that case.
     */
    public void evictIdleConnections() {
        if (isInternalHttpClient) {
            httpClient.connectionPool().evictAll();
        }
    }

    public void shutdown() {
        if (isInternalHttpClient) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        return value.trim();
    }
}
