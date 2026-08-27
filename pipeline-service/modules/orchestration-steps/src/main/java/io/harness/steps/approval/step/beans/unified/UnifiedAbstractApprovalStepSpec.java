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
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@JsonSubTypes({
  @JsonSubTypes.Type(value = UnifiedManualApprovalStepSpec.class, name = YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL)
  ,
      @JsonSubTypes.Type(
          value = UnifiedCustomApprovalStepSpec.class, name = YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL),
      @JsonSubTypes.Type(
          value = UnifiedServiceNowApprovalStepSpec.class, name = YAMLFieldNameConstants.UNIFIED_SERVICENOW_APPROVAL),
      @JsonSubTypes.Type(value = UnifiedJiraApprovalStepSpec.class, name = YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL)
})
public abstract class UnifiedAbstractApprovalStepSpec {
  private static final String DEFAULT_RETRY_TIMEOUT = "1m";
  public abstract SpecParameters getSpecParameters();

  String getRetryTimeout(String timeout) {
    if (EmptyPredicate.isEmpty(timeout)) {
      return DEFAULT_RETRY_TIMEOUT;
    }
    return timeout;
  }
}
