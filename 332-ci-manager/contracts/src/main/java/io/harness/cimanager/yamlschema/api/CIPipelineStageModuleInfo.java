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

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CI)
@RecasterAlias("io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo")
public class CIPipelineStageModuleInfo {
  String stageExecutionId;
  String stageId;
  String stageName;
  String status;
  Long cpuTime;
  Long stageBuildTime;
  String infraType;
  String osType;
  String osArch;
  Long startTs;
  Double buildMultiplier;
  String resourceClass;
  String optimizationState;
  Long timeSaved;
  String commitId;
  String repoName;
  Long queueTimeMs;
  String branch;
  String sourceBranch;
  String tag;
  String buildType;
  String prNumber;
  String repoUrl;
  String commitMessage;
  String prTitle;
}
