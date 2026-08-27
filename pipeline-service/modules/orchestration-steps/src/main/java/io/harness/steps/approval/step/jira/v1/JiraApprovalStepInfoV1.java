/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.approval.step.jira.beans.v1.CriteriaSpecWrapper;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonTypeName(StepSpecTypeConstantsV1.JIRA_APPROVAL)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraApprovalStepInfoV1 implements PMSStepInfo, WithConnectorRef, WithDelegateSelector {
  ParameterField<String> connector;
  ParameterField<String> key;
  String type;
  String project;
  CriteriaSpecWrapper approval_criteria;
  CriteriaSpecWrapper rejection_criteria;
  ParameterField<Timeout> retry;
  ParameterField<List<TaskSelectorYaml>> delegates;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.JIRA_APPROVAL_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connector);
    return connectorRefMap;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegates();
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    setDelegates(delegates);
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }
}
