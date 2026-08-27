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
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public enum UnifiedApprovalType {
  @JsonProperty(YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL)
  UNIFIED_MANUAL_APPROVAL(YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL),

  @JsonProperty(YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL)
  UNIFIED_CUSTOM_APPROVAL(YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL),

  @JsonProperty(YAMLFieldNameConstants.UNIFIED_SERVICENOW_APPROVAL)
  UNIFIED_SERVICENOW_APPROVAL(YAMLFieldNameConstants.UNIFIED_SERVICENOW_APPROVAL),

  @JsonProperty(YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL)
  UNIFIED_JIRA_APPROVAL(YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL);
  private final String value;

  UnifiedApprovalType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
