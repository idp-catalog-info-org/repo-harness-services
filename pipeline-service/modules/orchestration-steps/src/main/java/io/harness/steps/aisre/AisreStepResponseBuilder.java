/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CHAOS)
@Singleton
public class AisreStepResponseBuilder {
  private static final String COMMAND_UNIT = "Execute";

  @Inject private ExceptionManager exceptionManager;

  @VisibleForTesting
  void setExceptionManager(ExceptionManager exceptionManager) {
    this.exceptionManager = exceptionManager;
  }

  /**
   * Builds a failed StepResponse with enriched FailureInfo from exception metadata.
   */
  public StepResponse getFailedStepResponse(long startTime, long endTime, Exception exception) {
    List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(exception);
    FailureInfo failureInfo = EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages);

    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(failureInfo)
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(COMMAND_UNIT)
                                                        .setStatus(UnitStatus.FAILURE)
                                                        .setStartTime(startTime)
                                                        .setEndTime(endTime)
                                                        .build()))
        .build();
  }
}
