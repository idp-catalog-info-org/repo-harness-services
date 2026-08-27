/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.plan.execution.beans.dto.CIExecutionInfoDTO.CIExecutionInfoDTOKeys;
import io.harness.pms.plan.execution.beans.dto.CIPullRequestDTO.CIPullRequestDTOKeys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("CIModuleProperties")
@FieldNameConstants(innerTypeName = "CIModulePropertiesDTOKeys")
public class CIModulePropertiesDTO {
  CIExecutionInfoDTO ciExecutionInfoDTO;
  String branch;
  String buildType;
  String tag;
  String repoName;

  @UtilityClass
  public static class CIModulePropertiesDTOKeys {
    public static final String event =
        CIModulePropertiesDTOKeys.ciExecutionInfoDTO + "." + CIExecutionInfoDTOKeys.event;
    public static final String sourceBranch = CIModulePropertiesDTOKeys.ciExecutionInfoDTO + "."
        + CIExecutionInfoDTOKeys.pullRequest + "." + CIPullRequestDTOKeys.sourceBranch;
    public static final String targetBranch = CIModulePropertiesDTOKeys.ciExecutionInfoDTO + "."
        + CIExecutionInfoDTOKeys.pullRequest + "." + CIPullRequestDTOKeys.targetBranch;
  }
}
