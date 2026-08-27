/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.v1;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.yaml.utils.v1.NGVariablesUtilsV1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.CUSTOM_APPROVAL)
public class CustomApprovalStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.CUSTOM_APPROVAL;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CustomApprovalStepInfoV1 spec;

  @Override
  public SpecParameters getSpecParameters() {
    return CustomApprovalStepParameters.infoBuilder()
        .shell(spec.getShell())
        .approval_criteria(spec.getApproval_criteria())
        .retry(spec.getRetry())
        .env_vars(NGVariablesUtilsV1.getMapOfVariables(
            spec.getEnv_vars() != null ? spec.getEnv_vars().getMap() : null, 0L, true))
        .output_vars(NGVariablesUtilsV1.getMapOfVariablesWithoutSecretExpression(
            spec.getOutput_vars() != null ? spec.getOutput_vars().getMap() : null))
        .secret_output_vars(NGVariablesUtilsV1.getSetOfSecretVars(
            spec.getOutput_vars() != null ? spec.getOutput_vars().getMap() : null))
        .rejection_criteria(spec.getRejection_criteria())
        .source(spec.getSource())
        .timeout(spec.getTimeout())
        .delegates(spec.getDelegates())
        .build();
  }
}
