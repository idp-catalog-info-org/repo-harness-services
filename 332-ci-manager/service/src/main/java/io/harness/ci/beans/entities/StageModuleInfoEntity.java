/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.platform.ArchitectureType;
import io.harness.eventsframework.schemas.platform.BuildInfraType;
import io.harness.eventsframework.schemas.platform.Developer;
import io.harness.eventsframework.schemas.platform.ModuleName;
import io.harness.eventsframework.schemas.platform.OSType;
import io.harness.eventsframework.schemas.platform.ResourceClass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(HarnessTeam.CI)
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("stageModuleInfoEntity")
public class StageModuleInfoEntity {
  private String stageExecutionId;
  private String stageId;
  private String stageName;
  private String status;
  private Long cpuTime;
  private Long stageBuildTime;
  private Double buildMultiplier;
  private String optimizationState;
  private Long timeSaved;
  private ArchitectureType architectureType;
  private BuildInfraType buildInfraType;
  private OSType infraOSType;
  private ResourceClass infraResourceClass;
  private int buildMinutes;
  private long lastBuildTimestamp;
  private Long startTimestamp;
  private List<Developer> committers;
  private ModuleName moduleName;
  private String commitId;
  private String repoName;
  private Long queueTimeMs;
  private String branch;
  private String sourceBranch;
  private String tag;
  private String buildType;
  private String prNumber;
  private String repoUrl;
  private String commitMessage;
  private String prTitle;
}
