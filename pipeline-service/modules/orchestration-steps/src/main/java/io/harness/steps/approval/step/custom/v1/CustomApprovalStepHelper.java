/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.steps.approval.step.custom.CustomApprovalSpecParameters;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class CustomApprovalStepHelper {
  public CustomApprovalSpecParameters getCustomApprovalStepParameters(StepBaseParameters stepParameters) {
    String version = stepParameters.getSpec().getVersion();
    if (HarnessYamlVersion.isV1(version)) {
      return ((CustomApprovalStepParameters) stepParameters.getSpec()).toCustomApprovalStepParameterV0();
    }
    return (CustomApprovalSpecParameters) stepParameters.getSpec();
  }
}
