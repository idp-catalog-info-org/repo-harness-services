/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans.unified;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Value
@Builder
@JsonTypeName(YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedJiraApprovalStepSpec extends UnifiedAbstractApprovalStepSpec {
  @NotNull @JsonProperty(YAMLFieldNameConstants.RETRY) String retry;
  @NotNull JsonNode approve;
  JsonNode reject;
  @JsonProperty(YAMLFieldNameConstants.RUN_V1) RunStepInfoV1 runStep;

  @Override
  public SpecParameters getSpecParameters() {
    return JiraApprovalSpecParameters.builder()
        .retryInterval(ParameterField.createValueField(Timeout.fromString(getRetryTimeout(retry))))
        .approvalCriteria(UnifiedCriteriaMapper.toCriteriaSpecWrapper(approve))
        .rejectionCriteria(UnifiedCriteriaMapper.toCriteriaSpecWrapper(reject))
        .runStepInfo(runStep)
        .build();
  }
}
