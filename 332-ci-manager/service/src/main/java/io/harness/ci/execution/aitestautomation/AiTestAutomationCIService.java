/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.aitestautomation;

import io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters;
import io.harness.aitestautomation.models.AiTestRunParameters;
import io.harness.aitestautomation.models.ExecutePlaywrightResponse;
import io.harness.aitestautomation.models.TestSuiteRunResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.AI)
public interface AiTestAutomationCIService {
  String getAuthToken(String accountId);

  TestSuiteRunResponse triggerTestSuiteRun(String accountId, String authToken, AiTestRunParameters params);

  ExecutePlaywrightResponse triggerBuildRun(String accountId, String authToken, String buildId, String applicationName,
      AiTestAutomationPlaywrightParameters params);

  void abortBuildRun(String accountId, String authToken, String buildRunId);

  String buildCallbackUrl(String callbackPath);

  void cacheAuthToken(String key, String token);

  String getCachedOrFreshToken(String key, String accountId);

  void evictAuthTokenCache(String key);
}
