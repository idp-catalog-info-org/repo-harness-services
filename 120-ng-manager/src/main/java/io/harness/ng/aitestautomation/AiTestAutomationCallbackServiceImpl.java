/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.aitestautomation;

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
public class AiTestAutomationCallbackServiceImpl implements AiTestAutomationCallbackService {
  private final WaitNotifyEngine waitNotifyEngine;

  @Inject
  public AiTestAutomationCallbackServiceImpl(WaitNotifyEngine waitNotifyEngine) {
    this.waitNotifyEngine = waitNotifyEngine;
    log.info("AiTestAutomationCallbackServiceImpl initialized with WaitNotifyEngine");
  }

  @Override
  public boolean notifyCompletion(AiTestAutomationCallbackRequest callbackRequest) {
    try {
      String jobId = callbackRequest.getJobId();
      boolean success = callbackRequest.isSuccess();

      if (jobId == null) {
        log.error("Received callback with missing runId/jobId in testSuite field");
        return false;
      }

      log.info("Processing callback notification for AI test job: {}, status: {}", jobId, callbackRequest.getStatus());

      // Create enhanced response data with all available information
      AiTestExecutionData data =
          AiTestExecutionData.builder()
              .phase(success ? PHASE_DONE : PHASE_FAILED)
              .executionId(jobId)
              .success(success)
              // Add test results data if available
              .totalTests(callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getTotal() : null)
              .passedTests(
                  callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getPassed() : null)
              .failedTests(
                  callbackRequest.getTestResults() != null ? callbackRequest.getTestResults().getFailed() : null)
              // Add report link if available
              .reportUrl(callbackRequest.getReportLink())
              // Add details URL if available
              .detailsUrl(callbackRequest.getDetailsUrl())
              // Add test suite info if available
              .testSuiteName(callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getName() : null)
              .testSuiteId(callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getId() : null)
              // Add environment info if available
              .environmentName(
                  callbackRequest.getEnvironment() != null ? callbackRequest.getEnvironment().getName() : null)
              .environmentId(callbackRequest.getEnvironment() != null ? callbackRequest.getEnvironment().getId() : null)
              .build();

      // Notify the waiters using the WaitNotifyEngine
      waitNotifyEngine.doneWith(jobId, data);
      log.info("Successfully notified completion for job: {}", jobId);
      return true;
    } catch (Exception e) {
      log.error("Failed to process AI test callback: " + e.getMessage(), e);
      return false;
    }
  }
}
