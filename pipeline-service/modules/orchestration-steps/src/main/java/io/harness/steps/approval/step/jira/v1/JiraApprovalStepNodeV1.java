/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.v1;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.steps.StepSpecTypeConstantsV1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName(StepSpecTypeConstantsV1.JIRA_APPROVAL)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraApprovalStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.JIRA_APPROVAL;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) JiraApprovalStepInfoV1 spec;

  @Override
  public SpecParameters getSpecParameters() {
    return JiraApprovalStepParameters.infoBuilder()
        .connector(spec.getConnector())
        .key(spec.getKey())
        .type(spec.getType())
        .project(spec.getProject())
        .retry(spec.getRetry())
        .approval_criteria(spec.getApproval_criteria())
        .rejection_criteria(spec.getRejection_criteria())
        .delegates(spec.getDelegates())
        .build();
  }
}
