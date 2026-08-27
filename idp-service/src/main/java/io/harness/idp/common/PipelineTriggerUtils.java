/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.idp.common.Constants.POST_METHOD;
import static io.harness.idp.common.Constants.X_API_KEY;
import static io.harness.idp.common.HttpUtils.buildRequest;
import static io.harness.idp.common.HttpUtils.executeRequest;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.retry.RetryHelper;

import io.github.resilience4j.retry.Retry;
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import okhttp3.Request;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class PipelineTriggerUtils {
  public static final String ACCOUNT_ID = "account_id";
  public static final String NAMESPACE = "namespace";
  public static final String ENVIRONMENT = "environment";
  public static final String VANITY_URL = "vanity_url";

  public static Retry buildRetryAndRegisterListeners(String className) {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(className,
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }

  public String trigger(String accountIdentifier, String namespace, String env, String url, String vanityUrl,
      Retry retry, String xApiKey) {
    String body = buildRequestBody(accountIdentifier, namespace, env, vanityUrl);
    Request request = buildRequest(url, POST_METHOD, Map.of(X_API_KEY, xApiKey), body);
    String responseBodyString = executeRequest(request, retry);
    Map<String, Object> responseObject =
        (Map<String, Object>) GsonUtils.convertJsonStringToObject(responseBodyString, Map.class);
    return (String) CommonUtils.findObjectByName(responseObject, "apiUrl");
  }

  private String buildRequestBody(String accountIdentifier, String namespace, String env, String vanityUrl) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(ACCOUNT_ID, accountIdentifier);
    jsonObject.put(NAMESPACE, namespace);

    if (StringUtils.isNotBlank(env)) {
      jsonObject.put(ENVIRONMENT, env);
    }

    if (StringUtils.isNotBlank(vanityUrl)) {
      jsonObject.put(VANITY_URL, vanityUrl);
    }

    return jsonObject.toString();
  }
}
