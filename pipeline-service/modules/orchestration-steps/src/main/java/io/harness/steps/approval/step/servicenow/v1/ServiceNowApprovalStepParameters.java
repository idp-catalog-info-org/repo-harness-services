/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.jira.beans.v1.CriteriaSpecWrapper;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalSpecParameters;
import io.harness.yaml.core.timeout.Timeout;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder(builderMethodName = "infoBuilder")
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@RecasterAlias("io.harness.steps.approval.step.servicenow.v1.ServiceNowApprovalStepParameters")
public class ServiceNowApprovalStepParameters implements SpecParameters {
  CriteriaSpecWrapper approval_criteria;
  CriteriaSpecWrapper rejection_criteria;
  ParameterField<List<TaskSelectorYaml>> delegates;
  ParameterField<String> connector;
  Ticket ticket;
  ServiceNowChangeWindowSpecV1 window;
  ParameterField<Timeout> retry;

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }

  public ServiceNowApprovalSpecParameters toServiceNowApprovalSPecParametersV0() {
    return ServiceNowApprovalSpecParameters.builder()
        .approvalCriteria(toCriteria(getApproval_criteria()))
        .delegateSelectors(getDelegates())
        .rejectionCriteria(toCriteria(getRejection_criteria()))
        .retryInterval(getRetry())
        .connectorRef(getConnector())
        .ticketNumber(getTicket().getNumber())
        .ticketType(getTicket().getType())
        .changeWindowSpec(toWindow(getWindow()))
        .build();
  }

  private io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec toWindow(
      ServiceNowChangeWindowSpecV1 window) {
    if (window == null) {
      return null;
    }
    return io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec.builder()
        .endField(window.getEnd())
        .startField(window.getStart())
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
