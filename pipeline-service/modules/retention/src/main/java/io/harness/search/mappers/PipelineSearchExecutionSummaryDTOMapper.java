/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO.RetryExecutionInfoDTOBuilder;
import io.harness.search.entity.beans.PipelineGitDetails;
import io.harness.search.entity.beans.PipelineRetryExecutionMetadata;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOBuilder;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineTriggeredBy;
import io.harness.search.entity.beans.cd.CDPipelineSearchModuleInfo;
import io.harness.search.entity.beans.cd.CDPipelineSearchModuleInfo.CDPipelineSearchModuleInfoBuilder;
import io.harness.search.entity.beans.ci.CIPipelineSearchModuleInfo;
import io.harness.search.entity.beans.ci.CIPipelineSearchModuleInfo.CIPipelineSearchModuleInfoBuilder;
import io.harness.search.entity.beans.ci.ExecutionInfoDTO;
import io.harness.search.entity.beans.ci.ExecutionInfoDTO.ExecutionInfoDTOBuilder;
import io.harness.search.entity.beans.ci.PullRequestDTO;
import io.harness.search.entity.beans.ci.PullRequestDTO.PullRequestDTOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
public class PipelineSearchExecutionSummaryDTOMapper {
  private static final String SERVICE_IDENTIFIERS = "serviceIdentifiers";
  private static final String ENV_IDENTIFIERS = "envIdentifiers";
  private static final String SERVICE_DEFINITION_TYPES = "serviceDefinitionTypes";
  private static final String ARTIFACT_DISPLAY_NAMES = "artifactDisplayNames";
  private static final String HELM_CHART_VERSIONS = "helmChartVersions";
  private static final String GIT_OPS_APP_IDENTIFIERS = "gitOpsAppIdentifiers";
  private static final String EXECUTION_INFO_DTO = "ciExecutionInfoDTO";
  private static final String EVENT = "event";
  private static final String PULL_REQUEST = "pullRequest";
  private static final String SOURCE_BRANCH = "sourceBranch";
  private static final String TARGET_BRANCH = "targetBranch";
  private static final String BRANCH = "branch";
  private static final String TAG = "tag";
  private static final String BUILD_TYPE = "buildType";
  private static final String REPO_NAME = "repoName";
  private static final String EMAIL = "email";
  private static final String GIT_USER = "gitUser";

  public PipelineSearchExecutionSummaryDTO toSearchEntity(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, boolean isSaveEvent) {
    PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid(pipelineExecutionSummaryEntity.getUuid())
            .runSequence(pipelineExecutionSummaryEntity.getRunSequence())
            .accountId(pipelineExecutionSummaryEntity.getAccountId())
            .orgIdentifier(pipelineExecutionSummaryEntity.getOrgIdentifier())
            .projectIdentifier(pipelineExecutionSummaryEntity.getProjectIdentifier())
            .pipelineIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
            .parentUniqueId(pipelineExecutionSummaryEntity.getParentUniqueId())
            .planExecutionId(pipelineExecutionSummaryEntity.getPlanExecutionId())
            .name(pipelineExecutionSummaryEntity.getName())
            .status(pipelineExecutionSummaryEntity.getStatus().toString())
            .tags(pipelineExecutionSummaryEntity.getTags())
            .labels(pipelineExecutionSummaryEntity.getLabels())
            .startTs(pipelineExecutionSummaryEntity.getStartTs())
            .endTs(pipelineExecutionSummaryEntity.getEndTs())
            .createdAt(pipelineExecutionSummaryEntity.getCreatedAt())
            .executionMode(pipelineExecutionSummaryEntity.getExecutionMode().toString())
            .isChildPipeline(pipelineExecutionSummaryEntity.getParentStageInfo() != null
                && pipelineExecutionSummaryEntity.getParentStageInfo().getHasParentPipeline())
            .inputSetIdentifiers(pipelineExecutionSummaryEntity.getInputSetIdentifiers())
            .notes(pipelineExecutionSummaryEntity.getNotes())
            .pipelineTimeoutTs(pipelineExecutionSummaryEntity.getPipelineTimeoutTs());

    addModules(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getModules());
    addRetryExecutionMetadata(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getRetryExecutionMetadata());
    addPmsElasticEntityGitDetails(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getEntityGitDetails());
    addExecutionTriggerInfo(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getExecutionTriggerInfo());
    addCDModuleInfo(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getModuleInfo());
    addCIModuleInfo(executionSummaryDTOBuilder, pipelineExecutionSummaryEntity.getModuleInfo());

    // This is required as isDeleted is a Class now instead of primitives, so only on save event we need to make it as
    // false
    if (isSaveEvent) {
      executionSummaryDTOBuilder.isDeleted(false);
    }
    return executionSummaryDTOBuilder.build();
  }

  private void addModules(PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder, List<String> modules) {
    if (isNull(modules)) {
      return;
    }
    List<String> modulesList = new ArrayList<>(modules);
    if (!isEmpty(modulesList) && modulesList.size() == 1 && modulesList.contains("pms")) {
      modulesList.add("common");
    }
    executionSummaryDTOBuilder.modules(modulesList);
  }

  private void addRetryExecutionMetadata(PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder,
      RetryExecutionMetadata retryExecutionMetadata) {
    if (retryExecutionMetadata == null) {
      return;
    }
    executionSummaryDTOBuilder.retryExecutionMetadata(
        PipelineRetryExecutionMetadata.builder().rootExecutionId(retryExecutionMetadata.getRootExecutionId()).build());
  }

  private void addPmsElasticEntityGitDetails(
      PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder, EntityGitDetails entityGitDetails) {
    if (entityGitDetails == null) {
      return;
    }
    executionSummaryDTOBuilder.entityGitDetails(PipelineGitDetails.builder()
                                                    .branch(entityGitDetails.getBranch())
                                                    .repoIdentifier(entityGitDetails.getRepoIdentifier())
                                                    .repoName(entityGitDetails.getRepoName())
                                                    .build());
  }

  private void addExecutionTriggerInfo(
      PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder, ExecutionTriggerInfo executionTriggerInfo) {
    if (executionTriggerInfo == null) {
      return;
    }
    executionSummaryDTOBuilder.triggerType(executionTriggerInfo.getTriggerType().toString());
    if (executionTriggerInfo.getTriggeredBy() == null) {
      return;
    }
    executionSummaryDTOBuilder.triggeredBy(
        PipelineTriggeredBy.builder()
            .triggerIdentifier(executionTriggerInfo.getTriggeredBy().getTriggerIdentifier())
            .email(executionTriggerInfo.getTriggeredBy().getExtraInfoOrDefault(EMAIL, ""))
            .gitUser(executionTriggerInfo.getTriggeredBy().getExtraInfoOrDefault(GIT_USER, ""))
            .build());
  }

  private void addCDModuleInfo(
      PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder, Map<String, org.bson.Document> moduleInfo) {
    if (moduleInfo == null) {
      return;
    }
    Map<String, Object> moduleInfoMap = moduleInfo.get("cd");
    if (EmptyPredicate.isEmpty(moduleInfoMap)) {
      return;
    }
    CDPipelineSearchModuleInfoBuilder cdModuleInfo = CDPipelineSearchModuleInfo.builder();
    if (moduleInfoMap.containsKey(SERVICE_IDENTIFIERS)) {
      cdModuleInfo.serviceIdentifiers((List<String>) moduleInfoMap.get(SERVICE_IDENTIFIERS));
    }
    if (moduleInfoMap.containsKey(ENV_IDENTIFIERS)) {
      cdModuleInfo.envIdentifiers((List<String>) moduleInfoMap.get(ENV_IDENTIFIERS));
    }
    if (moduleInfoMap.containsKey(SERVICE_DEFINITION_TYPES)) {
      cdModuleInfo.serviceDefinitionTypes((List<String>) moduleInfoMap.get(SERVICE_DEFINITION_TYPES));
    }
    if (moduleInfoMap.containsKey(ARTIFACT_DISPLAY_NAMES)) {
      cdModuleInfo.artifactDisplayNames((List<String>) moduleInfoMap.get(ARTIFACT_DISPLAY_NAMES));
    }
    if (moduleInfoMap.containsKey(HELM_CHART_VERSIONS)) {
      cdModuleInfo.helmChartVersions((List<String>) moduleInfoMap.get(HELM_CHART_VERSIONS));
    }
    if (moduleInfoMap.containsKey(GIT_OPS_APP_IDENTIFIERS)) {
      cdModuleInfo.gitOpsAppIdentifiers((List<String>) moduleInfoMap.get(GIT_OPS_APP_IDENTIFIERS));
    }
    executionSummaryDTOBuilder.cdModuleInfo(cdModuleInfo.build());
  }

  private void addCIModuleInfo(
      PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTOBuilder, Map<String, org.bson.Document> moduleInfo) {
    if (moduleInfo == null) {
      return;
    }
    Map<String, Object> moduleInfoMap = moduleInfo.get("ci");
    if (EmptyPredicate.isEmpty(moduleInfoMap)) {
      return;
    }
    CIPipelineSearchModuleInfoBuilder ciModuleInfo = CIPipelineSearchModuleInfo.builder();
    if (moduleInfoMap.containsKey(EXECUTION_INFO_DTO)) {
      Map<String, Object> ciWebhookInfoDTO = (Map<String, Object>) moduleInfoMap.get(EXECUTION_INFO_DTO);
      ExecutionInfoDTOBuilder ciElasticExecutionInfoDTO = ExecutionInfoDTO.builder();
      if (ciWebhookInfoDTO.containsKey(EVENT)) {
        ciElasticExecutionInfoDTO.event((String) ciWebhookInfoDTO.get(EVENT));
      }
      if (ciWebhookInfoDTO.containsKey(PULL_REQUEST)) {
        PullRequestDTOBuilder ciElasticPullRequestDTO = PullRequestDTO.builder();
        Map<String, Object> pullRequest = (Map<String, Object>) ciWebhookInfoDTO.get(PULL_REQUEST);
        if (pullRequest.containsKey(SOURCE_BRANCH)) {
          ciElasticPullRequestDTO.sourceBranch((String) pullRequest.get(SOURCE_BRANCH));
        }
        if (pullRequest.containsKey(TARGET_BRANCH)) {
          ciElasticPullRequestDTO.targetBranch((String) pullRequest.get(TARGET_BRANCH));
        }
        ciElasticExecutionInfoDTO.pullRequest(ciElasticPullRequestDTO.build());
      }
      ciModuleInfo.ciExecutionInfoDTO(ciElasticExecutionInfoDTO.build());
    }
    if (moduleInfoMap.containsKey(BRANCH)) {
      ciModuleInfo.branch((String) moduleInfoMap.get(BRANCH));
    }
    if (moduleInfoMap.containsKey(TAG)) {
      ciModuleInfo.tag((String) moduleInfoMap.get(TAG));
    }
    if (moduleInfoMap.containsKey(BUILD_TYPE)) {
      ciModuleInfo.buildType((String) moduleInfoMap.get(BUILD_TYPE));
    }
    if (moduleInfoMap.containsKey(REPO_NAME)) {
      ciModuleInfo.repoName((String) moduleInfoMap.get(REPO_NAME));
    }
    executionSummaryDTOBuilder.ciModuleInfo(ciModuleInfo.build());
  }

  public static RetryExecutionInfoDTO toRetryExecutionInfoDTO(
      PipelineSearchReadExecutionSummaryDTO pipelineSearchReadExecutionSummaryDTO) {
    if (pipelineSearchReadExecutionSummaryDTO == null) {
      return null;
    }
    RetryExecutionInfoDTOBuilder retryExecutionInfoDTOBuilder =
        RetryExecutionInfoDTO.builder()
            .runSequence(pipelineSearchReadExecutionSummaryDTO.getRunSequence())
            .startTs(pipelineSearchReadExecutionSummaryDTO.getStartTs())
            .endTs(pipelineSearchReadExecutionSummaryDTO.getEndTs())
            .planExecutionId(pipelineSearchReadExecutionSummaryDTO.getPlanExecutionId());
    if (EmptyPredicate.isNotEmpty(pipelineSearchReadExecutionSummaryDTO.getStatus())) {
      retryExecutionInfoDTOBuilder.status(ExecutionStatus.valueOf(pipelineSearchReadExecutionSummaryDTO.getStatus()));
    }
    return retryExecutionInfoDTOBuilder.build();
  }
}
