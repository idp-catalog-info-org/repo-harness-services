/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.idp.common.Constants.GET_METHOD;
import static io.harness.idp.common.Constants.POST_METHOD;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;

import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@Slf4j
public class HttpUtils {
  public static Request buildRequest(String url, String method, Map<String, String> headers, String body) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
    headers.forEach(requestBuilder::addHeader);

    switch (method) {
            case POST_METHOD -> {
                RequestBody requestBody = RequestBody.create(body, MediaType.parse(APPLICATION_JSON));
                requestBuilder.post(requestBody);
                return requestBuilder.build();
            }
            case GET_METHOD -> {
                requestBuilder.get();
                return requestBuilder.build();
            }
            default -> throw new UnsupportedOperationException("Method " + method + " is not supported");
        }
    }

    public static String executeRequest(Request request, Retry retry) {
        OkHttpClient client = new OkHttpClient();
        Supplier<Response> responseSupplier = Retry.decorateSupplier(retry, () -> {
            try {
                return client.newCall(request).execute();
            } catch (IOException e) {
                String errMessage = "Error occurred while reaching URL: " + request.url();
                log.error(errMessage, e);
                throw new UnexpectedException(errMessage);
            }
        });
        Response response = responseSupplier.get();
        if (!response.isSuccessful()) {
            throw new UnexpectedException(format("HTTP call failed for URL: %s, code: %s", request.url(), response.code()));
        }

        try {
            return Objects.requireNonNull(response.body()).string();
        } catch (IOException e) {
            String errMessage = "Error occurred while fetching response from URL: " + request.url();
            log.error(errMessage, e);
            throw new UnexpectedException(errMessage);
        }
    }
}
