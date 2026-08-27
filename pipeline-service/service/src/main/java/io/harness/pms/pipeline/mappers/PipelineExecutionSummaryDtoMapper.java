/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.GovernanceServiceHelper;
import io.harness.execution.PriorityType;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.QueuedType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO.PipelineExecutionOutlineDTOBuilder;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO.PipelineExecutionSummaryDTOBuilder;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.utils.DateTimeUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class PipelineExecutionSummaryDtoMapper {
  private static final long EXECUTION_DETAILS_TTL_IN_DAYS = 30;
  public static final long EXECUTION_DETAILS_TTL_MILLIS =
      DateTimeUtils.getTimeDiffInMillis(System.currentTimeMillis(), EXECUTION_DETAILS_TTL_IN_DAYS);
  private static final int MAX_NOTES_LENGTH_FOR_LISTING = 500;
  public PipelineExecutionSummaryDTO toDto(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity,
      EntityGitDetails entityGitDetails, boolean showRetryHistory, Boolean isLatestExecution, QueuedType queuedType,
      ScopeInfo scopeInfo, boolean shouldPopulateNotes) {
    boolean useScopeInfo = scopeInfo != null;
    entityGitDetails = updateEntityGitDetails(entityGitDetails);

    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
    String startingNodeId = pipelineExecutionSummaryEntity.getStartingNodeId();
    StagesExecutionMetadata stagesExecutionMetadata = pipelineExecutionSummaryEntity.getStagesExecutionMetadata();

    boolean isStagesExecution = stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution();
    List<String> stageIdentifiers = getStageIdentifiers(stagesExecutionMetadata, pipelineExecutionSummaryEntity);
    Map<String, String> stagesExecutedNames =
        getStagesExecutedNames(stageIdentifiers, stagesExecutionMetadata, pipelineExecutionSummaryEntity);

    RetryCapability retryCapability = determineRetryCapability(pipelineExecutionSummaryEntity, isLatestExecution);
    Map<ExecutionStatus, Integer> countsByStatus =
        getStagesCountByStatus(layoutNodeDTOMap, startingNodeId, new HashMap<>());
    PipelineExecutionSummaryDTOBuilder pipelineExecutionSummaryDTOBuilder =
        PipelineExecutionSummaryDTO.builder()
            .name(pipelineExecutionSummaryEntity.getName())
            .orgIdentifier(
                useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier())
            .projectIdentifier(
                useScopeInfo ? scopeInfo.getProjectIdentifier() : pipelineExecutionSummaryEntity.getProjectIdentifier())
            .createdAt(pipelineExecutionSummaryEntity.getCreatedAt())
            .layoutNodeMap(layoutNodeDTOMap)
            .moduleInfo(ModuleInfoMapper.getModuleInfo(pipelineExecutionSummaryEntity.getModuleInfo()))
            .startingNodeId(startingNodeId)
            .startingNodeIds(pipelineExecutionSummaryEntity.getStartingNodeIds())
            .isDagEnabled(pipelineExecutionSummaryEntity.getIsDagEnabled())
            .dependencyGraph(pipelineExecutionSummaryEntity.getDependencyGraph())
            .planExecutionId(pipelineExecutionSummaryEntity.getPlanExecutionId())
            .pipelineIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
            .startTs(pipelineExecutionSummaryEntity.getStartTs())
            .endTs(pipelineExecutionSummaryEntity.getEndTs())
            .status(pipelineExecutionSummaryEntity.getStatus())
            .governanceMetadata(pipelineExecutionSummaryEntity.getGovernanceMetadata())
            .opaOnSaveStatus(pipelineExecutionSummaryEntity.getOpaOnSaveStatus())
            .executionInputConfigured(pipelineExecutionSummaryEntity.getExecutionInputConfigured())
            .executionTriggerInfo(pipelineExecutionSummaryEntity.getExecutionTriggerInfo())
            .executionErrorInfo(pipelineExecutionSummaryEntity.getExecutionErrorInfo())
            .successfulStagesCount(countsByStatus.getOrDefault(ExecutionStatus.SUCCESS, 0))
            .failedStagesCount(countsByStatus.getOrDefault(ExecutionStatus.FAILED, 0))
            .runningStagesCount(countsByStatus.getOrDefault(ExecutionStatus.RUNNING, 0))
            .totalStagesCount(getStagesCount(layoutNodeDTOMap, startingNodeId))
            .runSequence(pipelineExecutionSummaryEntity.getRunSequence())
            .tags(pipelineExecutionSummaryEntity.getTags())
            .labels(pipelineExecutionSummaryEntity.getLabels())
            .failureInfo(pipelineExecutionSummaryEntity.getFailureInfo())
            .modules(isEmpty(pipelineExecutionSummaryEntity.getModules()) ? new ArrayList<>()
                                                                          : pipelineExecutionSummaryEntity.getModules())
            .gitDetails(entityGitDetails)
            .canRetry(retryCapability.canRetry)
            .showRetryHistory(showRetryHistory)
            .isRetriedExecution(!pipelineExecutionSummaryEntity.getPlanExecutionId().equals(
                pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId()))
            .governanceMetadata(GovernanceServiceHelper.updateOrgIdForProjectLevelPolicies(
                pipelineExecutionSummaryEntity.getGovernanceMetadata(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier()))
            .isStagesExecution(isStagesExecution)
            .stagesExecuted(stageIdentifiers)
            .stagesExecutedNames(stagesExecutedNames)
            .parentStageInfo(pipelineExecutionSummaryEntity.getParentStageInfo())
            .allowStageExecutions(pipelineExecutionSummaryEntity.isStagesExecutionAllowed())
            .storeType(pipelineExecutionSummaryEntity.getStoreType())
            .connectorRef(isEmpty(pipelineExecutionSummaryEntity.getConnectorRef())
                    ? null
                    : pipelineExecutionSummaryEntity.getConnectorRef())
            .abortedBy(pipelineExecutionSummaryEntity.getAbortedBy())
            .executionMode(pipelineExecutionSummaryEntity.getExecutionMode())
            .notesExistForPlanExecutionId(checkNotesExistForPlanExecutionId(pipelineExecutionSummaryEntity))
            .notes(shouldPopulateNotes ? truncateNotesForListing(pipelineExecutionSummaryEntity.getNotes(),
                                             pipelineExecutionSummaryEntity.getAccountId(),
                                             pipelineExecutionSummaryEntity.getPlanExecutionId())
                                       : null)
            .yamlVersion(pipelineExecutionSummaryEntity.getPipelineVersion())
            .shouldUseSimplifiedKey(checkShouldUseSimplifiedLogBaseKey(pipelineExecutionSummaryEntity))
            .canReExecute(retryCapability.canReExecute)
            .pipelineTimeoutTs(pipelineExecutionSummaryEntity.getPipelineTimeoutTs());

    populateOptionalFields(pipelineExecutionSummaryDTOBuilder, pipelineExecutionSummaryEntity, queuedType);
    return pipelineExecutionSummaryDTOBuilder.build();
  }

  public PipelineExecutionSummaryDTO toDto(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity,
      EntityGitDetails entityGitDetails, boolean showRetryHistory, Boolean isLatestExecution, QueuedType queuedType) {
    return toDto(
        pipelineExecutionSummaryEntity, entityGitDetails, showRetryHistory, isLatestExecution, queuedType, null, true);
  }

  public PipelineExecutionSummaryDTO toDto(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity,
      EntityGitDetails entityGitDetails, boolean showRetryHistory, Boolean isLatestExecution, QueuedType queuedType,
      ScopeInfo scopeInfo) {
    return toDto(pipelineExecutionSummaryEntity, entityGitDetails, showRetryHistory, isLatestExecution, queuedType,
        scopeInfo, true);
  }

  @VisibleForTesting
  protected String getQueuedReason(QueuedType queuedType, PriorityType priorityType) {
    return String.format(queuedType.getQueuedReason(), priorityType.name());
  }

  public boolean checkNotesExistForPlanExecutionId(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (null != pipelineExecutionSummaryEntity.getNotesExistForPlanExecutionId()) {
      return pipelineExecutionSummaryEntity.getNotesExistForPlanExecutionId();
    }
    return false;
  }

  public boolean checkShouldUseSimplifiedLogBaseKey(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (null != pipelineExecutionSummaryEntity.getShouldUseSimplifiedLogBaseKey()) {
      return pipelineExecutionSummaryEntity.getShouldUseSimplifiedLogBaseKey();
    }
    return false;
  }

  private String truncateNotesForListing(String notes, String accountId, String planExecutionId) {
    if (notes == null) {
      return null;
    }
    if (notes.length() > MAX_NOTES_LENGTH_FOR_LISTING) {
      log.info("[EXECUTION_NOTES_TRUNCATION] Notes length exceeds {} chars for accountId: {}, planExecutionId: {}, "
              + "actual length: {}",
          MAX_NOTES_LENGTH_FOR_LISTING, accountId, planExecutionId, notes.length());
      return notes.substring(0, MAX_NOTES_LENGTH_FOR_LISTING);
    }
    return notes;
  }

  public PipelineExecutionIdentifierSummaryDTO toExecutionIdentifierDto(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    return PipelineExecutionIdentifierSummaryDTO.builder()
        .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : pipelineExecutionSummaryEntity.getOrgIdentifier())
        .projectIdentifier(
            useScopeInfo ? scopeInfo.getProjectIdentifier() : pipelineExecutionSummaryEntity.getProjectIdentifier())
        .planExecutionId(pipelineExecutionSummaryEntity.getPlanExecutionId())
        .pipelineIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
        .runSequence(pipelineExecutionSummaryEntity.getRunSequence())
        .status(pipelineExecutionSummaryEntity.getStatus())
        .build();
  }

  private Map<String, String> getStageNames(
      List<String> stageIdentifiers, String pipelineYaml, String pipelineVersion) {
    Map<String, String> identifierToNames = new LinkedHashMap<>();
    List<BasicStageInfo> stageInfoList;
    if (HarnessYamlVersion.V0.equals(pipelineVersion)) {
      stageInfoList = StageExecutionSelectorHelper.getStageInfoList(pipelineYaml);
    } else {
      stageInfoList = StageExecutionSelectorHelper.getStageInfoListV1(pipelineYaml);
    }
    stageInfoList.forEach(stageInfo -> {
      String identifier = stageInfo.getIdentifier();
      if (stageIdentifiers.contains(identifier)) {
        identifierToNames.put(identifier, stageInfo.getName());
      }
    });
    return identifierToNames;
  }

  public Map<ExecutionStatus, Integer> getStagesCountByStatus(Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap,
      String startingNodeId, Map<ExecutionStatus, Integer> countsByStatus) {
    if (startingNodeId == null) {
      return countsByStatus;
    }
    GraphLayoutNodeDTO nodeDTO = layoutNodeDTOMap.get(startingNodeId);
    if (nodeDTO == null) {
      return countsByStatus;
    }
    EdgeLayoutListDTO edgeLayoutList = nodeDTO.getEdgeLayoutList();
    if (!YAMLFieldNameConstants.PARALLEL.equals(nodeDTO.getNodeType())) {
      countsByStatus.merge(nodeDTO.getStatus(), 1, Integer::sum);
    } else {
      if (edgeLayoutList != null && edgeLayoutList.getCurrentNodeChildren() != null) {
        for (String child : edgeLayoutList.getCurrentNodeChildren()) {
          countsByStatus.merge(layoutNodeDTOMap.get(child).getStatus(), 1, Integer::sum);
        }
      }
    }
    if (edgeLayoutList == null || EmptyPredicate.isEmpty(edgeLayoutList.getNextIds())) {
      return countsByStatus;
    }
    return getStagesCountByStatus(layoutNodeDTOMap, edgeLayoutList.getNextIds().get(0), countsByStatus);
  }
  public int getStagesCount(Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap, String startingNodeId) {
    if (startingNodeId == null) {
      return 0;
    }
    int count = 0;
    GraphLayoutNodeDTO nodeDTO = layoutNodeDTOMap.get(startingNodeId);
    if (nodeDTO == null) {
      return count;
    }
    EdgeLayoutListDTO edgeLayoutListDTO = nodeDTO.getEdgeLayoutList();
    if (!YAMLFieldNameConstants.PARALLEL.equals(nodeDTO.getNodeType())) {
      count++;
    } else {
      count += isNotEmpty(edgeLayoutListDTO.getCurrentNodeChildren())
          ? edgeLayoutListDTO.getCurrentNodeChildren().size()
          : 0;
    }
    if (edgeLayoutListDTO == null || isEmpty(edgeLayoutListDTO.getNextIds())) {
      return count;
    }
    return count + getStagesCount(layoutNodeDTOMap, nodeDTO.getEdgeLayoutList().getNextIds().get(0));
  }

  private EntityGitDetails updateEntityGitDetails(EntityGitDetails entityGitDetails) {
    if (entityGitDetails == null) {
      return null;
    }
    String rootFolder = entityGitDetails.getRootFolder();
    String filePath = entityGitDetails.getFilePath();
    String repoIdentifier = entityGitDetails.getRepoIdentifier();
    String repoName = entityGitDetails.getRepoName();
    String branch = entityGitDetails.getBranch();
    String objectId = entityGitDetails.getObjectId();
    String commitId = entityGitDetails.getCommitId();
    return EntityGitDetails.builder()
        .rootFolder(EntityGitDetailsMapper.nullIfDefault(rootFolder))
        .filePath(EntityGitDetailsMapper.nullIfDefault(filePath))
        .repoIdentifier(EntityGitDetailsMapper.nullIfDefault(repoIdentifier))
        .repoName(EntityGitDetailsMapper.nullIfDefault(repoName))
        .branch(EntityGitDetailsMapper.nullIfDefault(branch))
        .objectId(EntityGitDetailsMapper.nullIfDefault(objectId))
        .commitId(EntityGitDetailsMapper.nullIfDefault(commitId))
        .build();
  }

  /**
   * Mapper for executionSummaryEntity. LayoutNodeMap should not contain hidden nodes
   * @param executionSummaryEntity
   * @return mapped PipelineExecutionOutlineDTO
   */
  public PipelineExecutionOutlineDTO toOutlineDto(
      PipelineExecutionSummaryEntity executionSummaryEntity, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    PipelineExecutionOutlineDTOBuilder pipelineExecutionOutlineDTOBuilder =
        PipelineExecutionOutlineDTO.builder()
            .runSequence(executionSummaryEntity.getRunSequence())
            .accountIdentifier(executionSummaryEntity.getAccountId())
            .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummaryEntity.getOrgIdentifier())
            .projectIdentifier(
                useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummaryEntity.getProjectIdentifier())
            .pipelineIdentifier(executionSummaryEntity.getPipelineIdentifier())
            .planExecutionId(executionSummaryEntity.getPlanExecutionId())
            .runtimeInputYaml(executionSummaryEntity.getResolvedUserInputSetYaml())
            .startTs(executionSummaryEntity.getStartTs())
            .endTs(executionSummaryEntity.getEndTs())
            .status(executionSummaryEntity.getStatus())
            .failureInfo(executionSummaryEntity.getFailureInfo() == null
                    ? null
                    : executionSummaryEntity.getFailureInfo().getMessage())
            .modules(executionSummaryEntity.getModules())
            .createdAt(executionSummaryEntity.getCreatedAt())
            .lastUpdatedAt(executionSummaryEntity.getLastUpdatedAt())
            .runtimeInputYaml(executionSummaryEntity.getResolvedUserInputSetYaml())
            .startingNodeId(executionSummaryEntity.getStartingNodeId())
            .startingNodeIds(executionSummaryEntity.getStartingNodeIds())
            .isDagEnabled(executionSummaryEntity.getIsDagEnabled())
            .dependencyGraph(executionSummaryEntity.getDependencyGraph())
            .name(executionSummaryEntity.getName());
    Map<String, GraphLayoutNodeDTO> layoutNodeMap = executionSummaryEntity.getLayoutNodeMap();

    if (layoutNodeMap != null) {
      Map<String, NodeExecutionOutlineDTO> stagesMap =
          layoutNodeMap.entrySet()
              .stream()
              .filter(entry -> {
                Boolean hidden = entry.getValue().getHidden();
                return hidden == null || !hidden;
              })
              .collect(Collectors.toMap(Map.Entry::getKey,
                  entry -> PipelineExecutionSummaryDtoMapper.toNodeExecutionOutlineDto(entry.getValue())));

      pipelineExecutionOutlineDTOBuilder.stagesMap(stagesMap);
    }

    return pipelineExecutionOutlineDTOBuilder.build();
  }

  public NodeExecutionOutlineDTO toNodeExecutionOutlineDto(GraphLayoutNodeDTO graphLayoutNodeDTO) {
    return NodeExecutionOutlineDTO.builder()
        .nodeType(graphLayoutNodeDTO.getNodeType())
        .nodeGroup(graphLayoutNodeDTO.getNodeGroup())
        .nodeIdentifier(graphLayoutNodeDTO.getNodeIdentifier())
        .name(graphLayoutNodeDTO.getName())
        .nodeUuid(graphLayoutNodeDTO.getNodeUuid())
        .status(graphLayoutNodeDTO.getStatus())
        .startTs(graphLayoutNodeDTO.getStartTs())
        .endTs(graphLayoutNodeDTO.getEndTs())
        .failureInfo(
            graphLayoutNodeDTO.getFailureInfoDTO() != null ? graphLayoutNodeDTO.getFailureInfoDTO().getMessage() : null)
        .nodeExecutionId(graphLayoutNodeDTO.getNodeExecutionId())
        .edgeLayoutList(graphLayoutNodeDTO.getEdgeLayoutList())
        .build();
  }

  private static List<String> getStageIdentifiers(
      StagesExecutionMetadata stagesExecutionMetadata, PipelineExecutionSummaryEntity entity) {
    if (stagesExecutionMetadata == null || ExecutionModeUtils.isRollbackMode(entity.getExecutionMode())) {
      return null;
    }
    return stagesExecutionMetadata.getStageIdentifiers();
  }

  private static Map<String, String> getStagesExecutedNames(List<String> stageIdentifiers,
      StagesExecutionMetadata stagesExecutionMetadata, PipelineExecutionSummaryEntity entity) {
    if (isEmpty(stageIdentifiers)) {
      return null;
    }

    if (isNotEmpty(stagesExecutionMetadata.getStageIdentifierToNameMap())) {
      return stagesExecutionMetadata.getStageIdentifierToNameMap();
    }

    log.warn("[REMOVAL_OF_FULL_PIPELINE_YAML]: Falling back to using full pipeline yaml for planExecutionId {}. "
            + "Please check",
        entity.getPlanExecutionId());
    return getStageNames(stageIdentifiers, stagesExecutionMetadata.getFullPipelineYaml(), entity.getPipelineVersion());
  }

  private static RetryCapability determineRetryCapability(
      PipelineExecutionSummaryEntity entity, Boolean isLatestExecution) {
    boolean canReExecute = entity.getEndTs() != null && (entity.getEndTs() >= EXECUTION_DETAILS_TTL_MILLIS);
    Boolean canRetry = null;

    if (entity.getIsDynamicExecution() != null && entity.getIsDynamicExecution()) {
      return new RetryCapability(false, false);
    }

    if (isLatestExecution != null) {
      canRetry = !ExecutionModeUtils.isRollbackMode(entity.getExecutionMode()) && isLatestExecution;
    }

    return new RetryCapability(canRetry, canReExecute);
  }

  private static void populateOptionalFields(
      PipelineExecutionSummaryDTOBuilder builder, PipelineExecutionSummaryEntity entity, QueuedType queuedType) {
    if (entity.getIsDynamicExecution() != null) {
      builder.isDynamicExecution(entity.getIsDynamicExecution());
    }
    if (entity.getTemplateReferenceSummary() != null) {
      builder.templateReferenceSummary(entity.getTemplateReferenceSummary());
    }
    if (entity.getIsOriginalYamlUsedOnRerun() != null) {
      builder.isOriginalYamlUsedOnRerun(entity.getIsOriginalYamlUsedOnRerun());
    }

    List<String> inputSetIds = entity.getInputSetIdentifiers();
    builder.inputSetIdentifiers(inputSetIds != null ? inputSetIds : new ArrayList<>());

    if (!entity.getPlanExecutionId().equals(entity.getRetryExecutionMetadata().getRootExecutionId())) {
      builder.retryExecutionMetadata(entity.getRetryExecutionMetadata());
    }

    if (queuedType != null) {
      builder.queuedType(queuedType);
      builder.queuedReason(getQueuedReason(queuedType, entity.getPriorityType()));
    }
  }

  private static class RetryCapability {
    final Boolean canRetry;
    final boolean canReExecute;

    RetryCapability(Boolean canRetry, boolean canReExecute) {
      this.canRetry = canRetry;
      this.canReExecute = canReExecute;
    }
  }
}
