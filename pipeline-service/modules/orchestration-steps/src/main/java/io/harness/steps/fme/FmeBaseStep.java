/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FMEPipelineContextData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.StringOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineSyncExecutable;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.FME)
public abstract class FmeBaseStep extends PipelineSyncExecutable {
  private static final String COMMAND_UNIT = "Execute";

  @Inject protected LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject protected FMEPipelineClient fmePipelineClient;
  @Inject protected FmeStepResponseBuilder fmeStepResponseBuilder;
  @Inject protected FmeOwnerResolver fmeOwnerResolver;

  @Override
  public final StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    try {
      FMEPipelineContextData.setFromAmbiance(ambiance);
      return executeFmeStep(ambiance, stepParameters, logCallback, startTime);
    } catch (Exception e) {
      log.error("Step execution failed: {}", e.getMessage(), e);
      logCallback.saveExecutionLog("Step failed: " + e.getMessage(), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return fmeStepResponseBuilder.getFailedStepResponse(startTime, System.currentTimeMillis(), e);
    } finally {
      FMEPipelineContextData.clear();
    }
  }

  protected abstract StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime);

  protected Scope getScope(Ambiance ambiance) {
    return Scope.builder()
        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .build();
  }

  protected StepResponse buildSuccessResponse(long startTime, String outcomeName, String message) {
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(outcomeName)
                         .outcome(StringOutcome.builder().message(message).build())
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  protected StepResponse buildSuccessResponseNoOutcome(long startTime) {
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  protected void assertNotEmpty(String value, String errorMsg) {
    if (StringUtils.isBlank(value)) {
      throw new FmeInvalidParameterException(errorMsg);
    }
  }
}
