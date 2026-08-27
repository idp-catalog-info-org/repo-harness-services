/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.aitestautomation;

import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_ABORTED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_DONE;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_FAILED;

import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.AI)
@Singleton
@Slf4j
public class AiTestAutomationPlaywrightCallbackServiceImpl implements AiTestAutomationPlaywrightCallbackService {
  private final WaitNotifyEngine waitNotifyEngine;

  @Inject
  public AiTestAutomationPlaywrightCallbackServiceImpl(WaitNotifyEngine waitNotifyEngine) {
    this.waitNotifyEngine = waitNotifyEngine;
  }

  @Override
  public boolean notifyCompletion(AiTestAutomationPlaywrightCallbackRequest callbackRequest) {
    try {
      String buildRunId = callbackRequest.getBuildRunId();
      boolean success = callbackRequest.isSuccess();
      boolean aborted = callbackRequest.isAborted();

      if (buildRunId == null) {
        log.error("Received build callback with missing buildRunId");
        return false;
      }

      log.info(
          "Processing callback notification for build run: {}, status: {}", buildRunId, callbackRequest.getStatus());

      String phase;
      if (success) {
        phase = PHASE_DONE;
      } else if (aborted) {
        phase = PHASE_ABORTED;
      } else {
        phase = PHASE_FAILED;
      }

      AiTestAutomationPlaywrightExecutionData data = AiTestAutomationPlaywrightExecutionData.builder()
                                                         .phase(phase)
                                                         .executionId(buildRunId)
                                                         .success(success)
                                                         .aborted(aborted)
                                                         .callbackStatus(callbackRequest.getStatus())
                                                         .buildRunId(buildRunId)
                                                         .buildName(callbackRequest.getBuildName())
                                                         .message(callbackRequest.getMessage())
                                                         .buildRunUrl(callbackRequest.getBuildRunUrl())
                                                         .build();

      String result = waitNotifyEngine.doneWith(buildRunId, data);
      if (result == null) {
        log.error(
            "WaitNotifyEngine.doneWith returned null for build run: {} — notify may not have persisted", buildRunId);
        return false;
      }
      log.info("Successfully notified completion for build run: {}", buildRunId);
      return true;
    } catch (Exception e) {
      log.error("Failed to process build run callback: {}", e.getMessage(), e);
      return false;
    }
  }
}
