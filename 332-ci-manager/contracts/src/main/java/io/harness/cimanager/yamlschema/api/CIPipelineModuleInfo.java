/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.execution;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.CIStageOptimizationState;
import io.harness.ci.pipeline.executions.beans.CIWebhookInfoDTO;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CI)
@RecasterAlias("io.harness.ci.plan.creator.execution.CIPipelineModuleInfo")
public class CIPipelineModuleInfo implements PipelineModuleInfo {
  private CIWebhookInfoDTO ciExecutionInfoDTO;
  private String branch;
  private String repoName;
  private String triggerRepoName;
  private String tag;
  private String prNumber;
  private String buildType;
  private Boolean isPrivateRepo;
  private List<CIScmDetails> scmDetailsList;
  private List<CIInfraDetails> infraDetailsList;
  private List<CIImageDetails> imageDetailsList;
  private List<TIBuildDetails> tiBuildDetailsList;
  private CIPipelineStageModuleInfo ciPipelineStageModuleInfo;
  private String ciLicenseType;
  private String ciEditionType;
  private List<CIStageOptimizationState> ciStageOptimizationStateList;
  private Long baselineMs;

  /**
   * CD-related module info for Unified Stages (V1).
   * Nested structure works with recursive update logic in PmsExecutionGrpcService.
   * Inner collections use $addToSet for accumulation, stageInfoMap preserves entries.
   */
  private UnifiedPipelineExecutionModuleInfo unifiedPipelineExecutionModuleInfo;
}
