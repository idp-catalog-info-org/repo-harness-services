/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;
import io.harness.steps.approval.step.jira.beans.v1.CriteriaSpecWrapper;
import io.harness.yaml.core.timeout.Timeout;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@RecasterAlias("io.harness.steps.approval.step.jira.v1.JiraApprovalStepParameters")
@Value
@OwnedBy(CDC)
public class JiraApprovalStepParameters implements SpecParameters {
  ParameterField<String> connector;
  ParameterField<String> key;
  String type;
  String project;
  CriteriaSpecWrapper approval_criteria;
  CriteriaSpecWrapper rejection_criteria;
  ParameterField<List<TaskSelectorYaml>> delegates;
  ParameterField<Timeout> retry;

  @Builder(builderMethodName = "infoBuilder")
  public JiraApprovalStepParameters(ParameterField<String> connector, ParameterField<String> key, String type,
      String project, CriteriaSpecWrapper approval_criteria, CriteriaSpecWrapper rejection_criteria,
      ParameterField<List<TaskSelectorYaml>> delegates, ParameterField<Timeout> retry) {
    this.connector = connector;
    this.key = key;
    this.type = type;
    this.project = project;
    this.approval_criteria = approval_criteria;
    this.rejection_criteria = rejection_criteria;
    this.delegates = delegates;
    this.retry = retry;
  }

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }

  public JiraApprovalSpecParameters toJiraApprovalStepParameterV0() {
    return JiraApprovalSpecParameters.builder()
        .connectorRef(getConnector())
        .projectKey(getProject())
        .issueKey(getKey())
        .issueType(getType())
        .approvalCriteria(toCriteria(getApproval_criteria()))
        .rejectionCriteria(toCriteria(getRejection_criteria()))
        .delegateSelectors(getDelegates())
        .retryInterval(getRetry())
        .build();
  }

  private io.harness.steps.approval.step.beans.CriteriaSpecWrapper toCriteria(CriteriaSpecWrapper criteria) {
    if (criteria == null) {
      return null;
    }
    return io.harness.steps.approval.step.beans.CriteriaSpecWrapper.builder()
        .criteriaSpec(JexlCriteriaSpec.builder().expression(criteria.getExpression()).build())
        .build();
  }
}
