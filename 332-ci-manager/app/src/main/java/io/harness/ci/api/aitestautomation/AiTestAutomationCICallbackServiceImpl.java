/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api.aitestautomation;

import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_DONE;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_FAILED;

import io.harness.aitestautomation.models.AiTestAutomationCallbackRequest;
import io.harness.aitestautomation.models.AiTestExecutionData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.AI)
@Singleton
@Slf4j
public class AiTestAutomationCICallbackServiceImpl implements AiTestAutomationCICallbackService {
  private final WaitNotifyEngine waitNotifyEngine;

  @Inject
  public AiTestAutomationCICallbackServiceImpl(WaitNotifyEngine waitNotifyEngine) {
    this.waitNotifyEngine = waitNotifyEngine;
  }

  @Override
  public boolean notifyCompletion(AiTestAutomationCallbackRequest callbackRequest) {
    try {
      String jobId = callbackRequest.getJobId();
      boolean success = callbackRequest.isSuccess();

      if (jobId == null) {
        log.error("Received CI callback with missing runId/jobId in testSuite field");
        return false;
      }

      log.info(
          "Processing CI callback notification for AI test job: {}, status: {}", jobId, callbackRequest.getStatus());

      AiTestExecutionData data =
          AiTestExecutionData.builder()
              .phase(success ? PHASE_DONE : PHASE_FAILED)
              .executionId(jobId)
              .success(success)
              .totalTests(callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getTotal() : null)
              .passedTests(
                  callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getPassed() : null)
              .failedTests(
                  callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getFailed() : null)
              .reportUrl(callbackRequest.getReportLink())
              .detailsUrl(callbackRequest.getDetailsUrl())
              .testSuiteName(callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getName() : null)
              .testSuiteId(callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getId() : null)
              .environmentName(
                  callbackRequest.getEnvironment() != null ? callbackRequest.getEnvironment().getName() : null)
              .environmentId(callbackRequest.getEnvironment() != null ? callbackRequest.getEnvironment().getId() : null)
              .build();

      waitNotifyEngine.doneWith(jobId, data);
      log.info("Successfully notified CI completion for job: {}", jobId);
      return true;
    } catch (Exception e) {
      log.error("Failed to process AI test CI callback: {}", e.getMessage(), e);
      return false;
    }
  }
}
