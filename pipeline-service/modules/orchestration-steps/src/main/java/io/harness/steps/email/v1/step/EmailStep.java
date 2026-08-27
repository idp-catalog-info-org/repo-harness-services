/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.email.v1.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.executables.PipelineSyncExecutable;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Slf4j
public class EmailStep extends PipelineSyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstantsV1.EMAIL_STEP_TYPE;
  @Inject io.harness.steps.email.EmailStep emailStep;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return emailStep.getLogKeys(ambiance);
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    return emailStep.executeSyncAfterRbac(ambiance, stepParameters, inputPackage, passThroughData);
  }
}
