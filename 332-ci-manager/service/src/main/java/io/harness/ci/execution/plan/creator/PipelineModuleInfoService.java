/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.ci.beans.entities.StageModuleInfoEntity;
import io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.repositories.PipelineModuleInfoRepository;
import io.harness.utils.CILicenseUsageUtils;
import io.harness.utils.DateTimeUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Service responsible for persisting and retrieving pipeline module information.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineModuleInfoService {
  @Inject private PipelineModuleInfoRepository repository;
  @Inject CILicenseUsageUtils ciLicenseUsageUtils;

  public void saveStageModuleInfo(
      OrchestrationEvent event, CIPipelineStageModuleInfo stageModuleInfo, StepType currentStepType) {
    if (Objects.isNull(stageModuleInfo)) {
      log.warn("Skipping persisting to mongo as stageModuleInfo is null");
      return;
    }
    try {
      Ambiance ambiance = event.getAmbiance();
      String planExecutionId = ambiance.getPlanExecutionId();
      String stageExecutionId = ambiance.getStageExecutionId();
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
      String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);

      if (StringUtils.isEmpty(stageExecutionId) || StringUtils.isEmpty(planExecutionId)) {
        log.warn("Skipping persisting to mongo as stageExecutionId / planExecutionId is empty, stageExecutionId: {}, "
                + "planExecutionId: {}",
            stageExecutionId, planExecutionId);
        return;
      }

      log.debug("Persisting pipeline module info for planExecutionId: {}, accountId: {}, stageExecutionId: {}",
          planExecutionId, accountId, stageExecutionId);

      StageModuleInfoEntity stageEntityInfo = convertToEntityModel(stageModuleInfo, currentStepType, event);
      PipelineModuleInfoEntity updatedEntity = repository.addStageModuleInfo(
          accountId, orgId, projectId, pipelineId, planExecutionId, parentUniqueId, stageEntityInfo);

      log.debug("Successfully saved pipeline module info with {} stages using atomic update",
          updatedEntity.getStageModuleInfoList().size());
    } catch (Exception ex) {
      log.error("Failed to persist pipeline module info to MongoDB", ex);
    }
  }

  /**
   * Converts from domain model to entity model.
   */
  private StageModuleInfoEntity convertToEntityModel(
      CIPipelineStageModuleInfo domainModel, StepType currentStepType, OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();

    return StageModuleInfoEntity.builder()
        .stageId(domainModel.getStageId())
        .stageExecutionId(domainModel.getStageExecutionId())
        .stageName(domainModel.getStageName())
        .status(domainModel.getStatus())
        .cpuTime(domainModel.getCpuTime())
        .stageBuildTime(domainModel.getStageBuildTime())
        .buildMultiplier(ciLicenseUsageUtils.getBuilderMultiplier(AmbianceUtils.getAccountId(ambiance),
            domainModel.getResourceClass(), domainModel.getOsType(), domainModel.getOsArch()))
        .optimizationState(domainModel.getOptimizationState())
        .timeSaved(domainModel.getTimeSaved())
        .moduleName(ciLicenseUsageUtils.getModuleName(currentStepType))
        .architectureType(ciLicenseUsageUtils.getArchitectureType(domainModel.getOsArch()))
        .buildInfraType(ciLicenseUsageUtils.getBuildInfraType(domainModel.getInfraType()))
        .infraOSType(ciLicenseUsageUtils.getOSType(domainModel.getOsType()))
        .infraResourceClass(ciLicenseUsageUtils.getResourceClass(domainModel.getResourceClass()))
        .buildMinutes(DateTimeUtils.roundToNearestMinute(domainModel.getCpuTime()))
        .lastBuildTimestamp(AmbianceUtils.getCurrentLevelStartTs(ambiance))
        .startTimestamp(domainModel.getStartTs())
        .committers(ciLicenseUsageUtils.getDevelopers(ambiance))
        .commitId(domainModel.getCommitId())
        .repoName(domainModel.getRepoName())
        .queueTimeMs(domainModel.getQueueTimeMs())
        .branch(domainModel.getBranch())
        .sourceBranch(domainModel.getSourceBranch())
        .tag(domainModel.getTag())
        .buildType(domainModel.getBuildType())
        .prNumber(domainModel.getPrNumber())
        .repoUrl(domainModel.getRepoUrl())
        .commitMessage(domainModel.getCommitMessage())
        .prTitle(domainModel.getPrTitle())
        .build();
  }
}
