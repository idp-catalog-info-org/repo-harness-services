/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;

import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@Slf4j
public class FetchMarketPlacePluginsUtil {
  private static final String USER_AGENT = "Mozilla/5.0";

  public String fetch(String url, Retry retry, String token) throws IOException {
    Request request = createHttpRequest(url, token);
    OkHttpClient client = new OkHttpClient();
    Supplier<Response> responseSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        return client.newCall(request).execute();
      } catch (IOException e) {
        String errMessage = "Error occurred while connecting URL: " + url;
        log.error(errMessage, e);
        throw new UnexpectedException(errMessage);
      }
    });

    Response response = responseSupplier.get();
    if (response.isSuccessful()) {
      try (ResponseBody responseBody = response.body()) {
        return responseBody.string();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      throw new IOException("Error listing/fetching directory contents: " + response.code());
    }
  }

  private Request createHttpRequest(String url, String token) {
    return new Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/vnd.github.v3+json")
        .header("Authorization", "Bearer " + token)
        .get()
        .build();
  }
}
