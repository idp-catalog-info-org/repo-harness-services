/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.aitestautomation;

import static io.harness.aitestautomation.constants.AiTestAutomationConstants.CALLBACK_PATH_CI;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.CALLBACK_PATH_CI_PLAYWRIGHT;
import static io.harness.annotations.dev.HarnessTeam.AI;

import io.harness.aitestautomation.models.AiTestAutomationExecutionException;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters;
import io.harness.aitestautomation.models.AiTestRunParameters;
import io.harness.aitestautomation.models.ExecutePlaywrightResponse;
import io.harness.aitestautomation.models.TestSuiteRunResponse;
import io.harness.aitestautomation.service.AiTestAutomationService;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;

/**
 * CI-specific adapter that delegates to the shared AiTestAutomationService.
 * The only CI-specific behavior is resolving the callback base URL from @Named("ngBaseUrl").
 */
@OwnedBy(AI)
@Singleton
public class AiTestAutomationCIServiceImpl implements AiTestAutomationCIService {
  @Inject private AiTestAutomationService sharedService;
  @Inject @Named("ngBaseUrl") private String ngBaseUrl;

  @Override
  public String getAuthToken(String accountId) {
    return sharedService.getAuthToken(accountId);
  }

  @Override
  public TestSuiteRunResponse triggerTestSuiteRun(String accountId, String authToken, AiTestRunParameters params) {
    String callbackUrl = buildCallbackUrl(CALLBACK_PATH_CI);
    if (StringUtils.isBlank(callbackUrl)) {
      throw new AiTestAutomationExecutionException(
          "CI manager callback URL could not be built — check ngBaseUrl configuration");
    }
    params.setCallbackUrl(callbackUrl);
    return sharedService.triggerTestSuiteRun(accountId, authToken, params);
  }

  @Override
  public ExecutePlaywrightResponse triggerBuildRun(String accountId, String authToken, String buildId,
      String applicationName, AiTestAutomationPlaywrightParameters params) {
    String callbackUrl = buildCallbackUrl(CALLBACK_PATH_CI_PLAYWRIGHT);
    if (StringUtils.isBlank(callbackUrl)) {
      throw new AiTestAutomationExecutionException(
          "CI manager callback URL could not be built — check ngBaseUrl configuration");
    }
    params.setCallbackUrl(callbackUrl);
    return sharedService.triggerBuildRun(accountId, authToken, buildId, applicationName, params);
  }

  @Override
  public void abortBuildRun(String accountId, String authToken, String buildRunId) {
    sharedService.abortBuildRun(accountId, authToken, buildRunId);
  }

  @Override
  public String buildCallbackUrl(String callbackPath) {
    String gatewayBaseUrl = ngBaseUrl;
    if (gatewayBaseUrl != null) {
      if (gatewayBaseUrl.endsWith("/")) {
        gatewayBaseUrl = gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1);
      }
      if (gatewayBaseUrl.endsWith("/ng")) {
        gatewayBaseUrl = gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - "/ng".length());
      }
    }
    return sharedService.buildCallbackUrl(gatewayBaseUrl, callbackPath);
  }

  @Override
  public void cacheAuthToken(String key, String token) {
    sharedService.cacheAuthToken(key, token);
  }

  @Override
  public String getCachedOrFreshToken(String key, String accountId) {
    return sharedService.getCachedOrFreshToken(key, accountId);
  }

  @Override
  public void evictAuthTokenCache(String key) {
    sharedService.evictAuthTokenCache(key);
  }
}
