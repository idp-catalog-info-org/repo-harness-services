/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api.aitestautomation;

import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_ABORTED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_DONE;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.PHASE_FAILED;

import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.AI)
@Singleton
@Slf4j
public class AiTestAutomationCIBuildCallbackServiceImpl implements AiTestAutomationCIBuildCallbackService {
  private final WaitNotifyEngine waitNotifyEngine;
  private final TIServiceUtils tiServiceUtils;

  @Inject
  public AiTestAutomationCIBuildCallbackServiceImpl(WaitNotifyEngine waitNotifyEngine, TIServiceUtils tiServiceUtils) {
    this.waitNotifyEngine = waitNotifyEngine;
    this.tiServiceUtils = tiServiceUtils;
  }

  @Override
  public boolean notifyCompletion(AiTestAutomationPlaywrightCallbackRequest callbackRequest) {
    try {
      String buildRunId = callbackRequest.getBuildRunId();
      boolean success = callbackRequest.isSuccess();
      boolean aborted = callbackRequest.isAborted();

      if (buildRunId == null) {
        log.error("Received CI build callback with missing buildRunId");
        return false;
      }

      log.info(
          "Processing CI callback notification for build run: {}, status: {}", buildRunId, callbackRequest.getStatus());

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

      waitNotifyEngine.doneWith(buildRunId, data);
      log.info("Successfully notified CI completion for build run: {}", buildRunId);
      return true;
    } catch (Exception e) {
      log.error("Failed to process CI build run callback: {}", e.getMessage(), e);
      return false;
    }
  }

  @Override
  public String generateTiToken(String accountId) {
    if (StringUtils.isBlank(accountId)) {
      throw new IllegalArgumentException("accountId is required for TI token generation");
    }
    log.info("Generating TI service token for accountId: {}", accountId);
    String token = tiServiceUtils.getTIServiceToken(accountId, true);
    return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }
}
