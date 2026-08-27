/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.ExecutionInfo;
import io.harness.engine.executions.retry.ExecutionInfo.ExecutionInfoBuilder;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.executions.retry.RetryStagesMetadataDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.RetryStagesMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PipelineExecutionSummaryEntityProjectionConstants;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.plan.utils.PlanResourceUtility;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.service.PipelineSearchService;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PipelineYamlUtils;
import io.harness.utils.PmsFeatureFlagService;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RetryExecutionHelper {
  private static final String LAST_STAGE_IDENTIFIER = "last_stage_identifier";
  private final NodeExecutionService nodeExecutionService;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PlanExecutionService planExecutionService;
  private final PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  private final PMSPipelineService pmsPipelineService;
  private final PMSExecutionService pmsExecutionService;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final PipelineSearchService pipelineSearchService;
  private final ExecutionRetentionService executionRetentionService;
  private DynamicExecutionService dynamicExecutionService;
  private static final int RETRY_HISTORY_LIMIT = 100;

  public List<String> fetchOnlyFailedStages(List<RetryStageInfo> info, List<String> retryStagesIdentifier) {
    List<String> onlyFailedStage = new ArrayList<>();
    for (int i = 0; i < info.size(); i++) {
      RetryStageInfo stageInfo = info.get(i);
      String stageIdentifier = stageInfo.getIdentifier();
      if (!retryStagesIdentifier.contains(stageIdentifier)) {
        throw new InvalidRequestException("Run only failed stages is applicable only for failed parallel group stages");
      }
      if (isFailedStatus(stageInfo.getStatus())) {
        onlyFailedStage.add(stageInfo.getIdentifier());
      }
    }
    if (onlyFailedStage.size() == 0) {
      throw new InvalidRequestException("No failed stage found in parallel group");
    }
    return onlyFailedStage;
  }
  public RetryGroup validateRetryStagesIdentifiersAndGetRetryGroup(
      String previousExecutionId, List<String> retryStagesIdentifier, String pipelineVersion) {
    return validateRetryStagesIdentifiersAndGetRetryGroup(
        previousExecutionId, retryStagesIdentifier, pipelineVersion, false);
  }

  public RetryGroup validateRetryStagesIdentifiersAndGetRetryGroup(
      String previousExecutionId, List<String> retryStagesIdentifier, String pipelineVersion, boolean isDagEnabled) {
    List<RetryStageInfo> stageDetails = getStageDetails(previousExecutionId, pipelineVersion);

    if (isDagEnabled) {
      Set<String> executionStageIdentifiers =
          stageDetails.stream().map(RetryStageInfo::getIdentifier).collect(Collectors.toSet());
      List<String> missingIdentifiers = retryStagesIdentifier.stream()
                                            .filter(id -> !executionStageIdentifiers.contains(id))
                                            .collect(Collectors.toList());
      if (!missingIdentifiers.isEmpty()) {
        throw new InvalidRequestException(
            "The execution can not be retried because the retryStagesIdentifier could not be found in the "
            + "execution. Missing identifiers: " + missingIdentifiers);
      }
      List<RetryStageInfo> selectedStages = stageDetails.stream()
                                                .filter(stage -> retryStagesIdentifier.contains(stage.getIdentifier()))
                                                .collect(Collectors.toList());
      return RetryGroup.builder().info(selectedStages).build();
    }

    RetryInfo retryInfo;
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      retryInfo = getRetryInfoV1(stageDetails);
    } else {
      retryInfo = getRetryInfo(stageDetails);
    }
    if (retryInfo != null) {
      Optional<RetryGroup> retryGroupOptional =
          retryInfo.getGroups()
              .stream()
              .filter(o -> checkIfCurrentStageGroupHasRetryStageIdentifiers(o.getInfo(), retryStagesIdentifier))
              .findAny();
      if (retryGroupOptional.isEmpty()) {
        throw new InvalidRequestException(
            "The execution can not be retried because the retryStagesIdentifier could not be found in any stage "
            + "Groups. Please provide the correct list of retryStagesIdentifier");
      }
      return retryGroupOptional.get();
    }
    throw new InvalidRequestException("Pipeline is updated, cannot resume");
  }

  private boolean checkIfCurrentStageGroupHasRetryStageIdentifiers(
      List<RetryStageInfo> retryStageInfos, List<String> retryStagesIdentifier) {
    Set<String> stageIdentifiersInCurrentGroup =
        retryStageInfos.stream().map(RetryStageInfo::getIdentifier).collect(Collectors.toSet());
    return stageIdentifiersInCurrentGroup.containsAll(retryStagesIdentifier);
  }

  public boolean isFailedStatus(ExecutionStatus status) {
    return status.equals(ExecutionStatus.ABORTED) || status.equals(ExecutionStatus.FAILED)
        || status.equals(ExecutionStatus.EXPIRED) || status.equals(ExecutionStatus.APPROVAL_REJECTED)
        || status.equals(ExecutionStatus.APPROVALREJECTED) || status.equals(ExecutionStatus.ABORTEDBYFREEZE);
  }

  public RetryInfo validateRetry(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String loadFromCache, ScopeInfo scopeInfo) {
    // Checking if this is the latest execution
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);

    PipelineGitXHelper.setupEntityDetails(pipelineExecutionSummaryEntity.getEntityGitDetails());

    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false,
            false, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo, true);

    boolean isV1 =
        optionalPipelineEntity.filter(pipelineEntity -> HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion()))
            .isPresent();

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_VALIDATE_RETRY_FROM_PREVIOUS_EXECUTION_ID)
        || isV1) {
      validateParentExecution(accountId, orgIdentifier, projectIdentifier, planExecutionId, true, scopeInfo);
    } else if (pipelineExecutionSummaryEntity.getRetryExecutionMetadata() != null) {
      validateParentExecution(accountId, orgIdentifier, projectIdentifier,
          pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId(), false, scopeInfo);
    }
    if (!isLatestExecution(pipelineExecutionSummaryEntity)) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage(
              "This execution is not the latest of all retried execution. You can only retry the latest execution.")
          .build();
    }

    if (EmptyPredicate.isNotEmpty(pipelineExecutionSummaryEntity.getRollbackModeExecutionId())) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("This execution has undergone Pipeline Rollback, and hence cannot be retried.")
          .build();
    }

    if (optionalPipelineEntity.isEmpty()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage(
              String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier))
          .build();
    }

    boolean inTimeLimit =
        PlanResourceUtility.validateInTimeLimitForRetry(pipelineExecutionSummaryEntity.getCreatedAt());
    if (!inTimeLimit) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("Execution is more than 30 days old. Cannot retry")
          .build();
    }

    String updatedPipeline = optionalPipelineEntity.get().getYaml();

    Optional<PlanExecutionMetadata> byPlanExecutionId =
        planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId);
    if (byPlanExecutionId.isEmpty()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("No Plan Execution exists for id " + planExecutionId)
          .build();
    }
    PlanExecutionMetadata planExecutionMetadata = byPlanExecutionId.get();
    String executedPipeline = planExecutionMetadata.getYaml();
    orgIdentifier =
        scopeInfo != null && isNotEmpty(scopeInfo.getOrgIdentifier()) ? scopeInfo.getOrgIdentifier() : orgIdentifier;
    projectIdentifier = scopeInfo != null && isNotEmpty(scopeInfo.getProjectIdentifier())
        ? scopeInfo.getProjectIdentifier()
        : projectIdentifier;

    String yamlVersion = optionalPipelineEntity.get().getHarnessVersion();
    // In case of pipeline template we will resolve only the pipeline template, we are only using stage identifiers
    // therefore we do not need to resolve the all templates in pipeline completely
    updatedPipeline = pmsPipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(accountId, orgIdentifier,
        projectIdentifier, updatedPipeline, optionalPipelineEntity.get().getStoreType(), loadFromCache, yamlVersion);
    PlanExecution planExecution = null;
    boolean readSwitchEnabled =
        pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE);
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          planExecutionId, Set.of(PlanExecutionKeys.stagesExecutionMetadata));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    StagesExecutionMetadata stagesExecutionMetadata =
        PlanExecutionMigrationHelper.readStagesExecutionMetadataWithFallBackOnMetadata(
            planExecutionMetadata, planExecution);
    if (stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution()) {
      updatedPipeline = InputSetMergeHelper.removeNonRequiredStages(
          updatedPipeline, stagesExecutionMetadata.getStageIdentifiers(), yamlVersion);
    }
    boolean storeTemplateRefEnabled =
        pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION)
        || HarnessYamlVersion.isV1(yamlVersion);

    // Check if the pipeline is DAG-enabled for proper retry grouping
    boolean isDagEnabled = Boolean.TRUE.equals(optionalPipelineEntity.get().getEnableDAG());

    return getRetryStages(updatedPipeline, executedPipeline, planExecutionId,
        pipelineExecutionSummaryEntity.getPipelineVersion(), storeTemplateRefEnabled, isDagEnabled);
  }

  private void validateParentExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String parentExecutionId, boolean checkPreviousExecution, ScopeInfo scopeInfo) {
    if (isEmpty(parentExecutionId)) {
      return;
    }
    PipelineExecutionSummaryEntity parentExecutionSummaryEntity =
        pmsExecutionSummaryService.getFromSecondaryWithProjections(accountId, orgIdentifier, projectIdentifier,
            parentExecutionId, false, List.of(PipelineExecutionSummaryKeys.endTs), scopeInfo);
    long currentTimeMillis = System.currentTimeMillis();
    long endTsMillis = parentExecutionSummaryEntity.getEndTs();
    long _30DaysMillis = 30L * 24 * 60 * 60 * 1000;
    if (currentTimeMillis - endTsMillis > _30DaysMillis) {
      if (checkPreviousExecution) {
        throw new InvalidRequestException(
            "The pipeline execution cannot be retried because the previous execution is more than 30 days old");
      } else {
        throw new InvalidRequestException(
            "The pipeline execution cannot be retried because the first original execution is more than 30 days old");
      }
    }
  }

  public boolean validateRetry(
      String updatedYaml, String executedYaml, boolean storeTemplateRefEnabled, String yamlVersion) {
    // compare fqn
    if (isEmpty(updatedYaml) || isEmpty(executedYaml)) {
      return false;
    }

    YamlConfig updatedConfig = new YamlConfig(updatedYaml, yamlVersion);
    YamlConfig executedConfig = new YamlConfig(executedYaml, yamlVersion);

    Map<FQN, Object> fqnToValueMapUpdatedYaml = updatedConfig.getFqnToValueMap();
    Map<FQN, Object> fqnToValueMapExecutedYaml = executedConfig.getFqnToValueMap();

    List<String> updateStageIdentifierList =
        getStageIdentifiers(fqnToValueMapUpdatedYaml, storeTemplateRefEnabled, yamlVersion);
    List<String> executedStageIdentifierList =
        getStageIdentifiers(fqnToValueMapExecutedYaml, storeTemplateRefEnabled, yamlVersion);

    if (!updateStageIdentifierList.equals(executedStageIdentifierList)) {
      return false;
    }
    return true;
  }

  private List<String> getStageIdentifiers(
      Map<FQN, Object> fqnToValueMapYaml, boolean storeTemplateRefEnabled, String yamlVersion) {
    List<String> stageIdentifierList = new ArrayList<>();
    for (FQN fqn : fqnToValueMapYaml.keySet()) {
      boolean ignoreTemplateFQN = storeTemplateRefEnabled && fqn.isTemplateFQN();
      if ((HarnessYamlVersion.isV1(yamlVersion) ? fqn.isStageIdentifierV1() : fqn.isStageIdentifier())
          && !ignoreTemplateFQN) {
        stageIdentifierList.add(fqn.display());
      }
    }
    return stageIdentifierList;
  }

  public RetryInfo getRetryStages(String updatedYaml, String executedYaml, String planExecutionId,
      String pipelineVersion, boolean storeTemplateRefEnabled, boolean isDagEnabled) {
    if (isEmpty(planExecutionId)) {
      return null;
    }
    boolean isResumable = validateRetry(updatedYaml, executedYaml, storeTemplateRefEnabled, pipelineVersion);
    if (!isResumable) {
      return RetryInfo.builder()
          .isResumable(isResumable)
          .errorMessage("Adding, deleting or changing the name of the stage identifier is not allowed for retrying")
          .build();
    }
    List<RetryStageInfo> stageDetails = getStageDetails(planExecutionId, pipelineVersion);

    // For DAG pipelines, each stage should be in its own separate group
    // This allows individual stage selection for retry operations
    if (isDagEnabled) {
      return getRetryInfoForDAG(stageDetails);
    }

    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return getRetryInfoV1(stageDetails);
    }
    return getRetryInfo(stageDetails);
  }

  /**
   * Groups stages for DAG pipelines where each stage gets its own separate group.
   * In DAG pipelines, there's no concept of sequential nextId or parallel grouping,
   * so each stage should be independently selectable for retry.
   *
   * @param stageDetails List of stage information from the execution
   * @return RetryInfo with each stage in its own group
   */
  public RetryInfo getRetryInfoForDAG(List<RetryStageInfo> stageDetails) {
    List<RetryGroup> retryGroupList = new ArrayList<>();
    for (RetryStageInfo stageDetail : stageDetails) {
      // Each stage gets its own separate group for DAG pipelines
      retryGroupList.add(RetryGroup.builder().info(Collections.singletonList(stageDetail)).build());
    }
    return RetryInfo.builder().isResumable(true).groups(retryGroupList).build();
  }

  public RetryInfo getRetryInfo(List<RetryStageInfo> stageDetails) {
    HashMap<String, List<RetryStageInfo>> mapNextIdWithStageInfo = new LinkedHashMap<>();
    for (RetryStageInfo stageDetail : stageDetails) {
      String nextId = stageDetail.getNextId();
      if (isEmpty(nextId)) {
        nextId = LAST_STAGE_IDENTIFIER;
      }
      List<RetryStageInfo> stageList = mapNextIdWithStageInfo.getOrDefault(nextId, new ArrayList<>());
      stageList.add(stageDetail);
      mapNextIdWithStageInfo.put(nextId, stageList);
    }
    List<RetryGroup> retryGroupList = new ArrayList<>();
    for (Map.Entry<String, List<RetryStageInfo>> entry : mapNextIdWithStageInfo.entrySet()) {
      retryGroupList.add(RetryGroup.builder().info(entry.getValue()).build());
    }
    return RetryInfo.builder().isResumable(true).groups(retryGroupList).build();
  }

  public RetryInfo getRetryInfoV1(List<RetryStageInfo> stageDetails) {
    HashMap<Pair<String, String>, List<RetryStageInfo>> mapNextIdWithStageInfo = new LinkedHashMap<>();
    for (RetryStageInfo stageDetail : stageDetails) {
      String nextId = stageDetail.getNextId();
      if (isEmpty(nextId)) {
        nextId = LAST_STAGE_IDENTIFIER;
      }
      List<RetryStageInfo> stageList =
          mapNextIdWithStageInfo.getOrDefault(Pair.of(nextId, stageDetail.getParentId()), new ArrayList<>());
      stageList.add(stageDetail);
      mapNextIdWithStageInfo.put(Pair.of(nextId, stageDetail.getParentId()), stageList);
    }
    List<RetryGroup> retryGroupList = new ArrayList<>();
    for (Map.Entry<Pair<String, String>, List<RetryStageInfo>> entry : mapNextIdWithStageInfo.entrySet()) {
      retryGroupList.add(RetryGroup.builder().info(entry.getValue()).build());
    }
    return RetryInfo.builder().isResumable(true).groups(retryGroupList).build();
  }

  public List<RetryStageInfo> getStageDetails(String planExecutionId, String pipelineVersion) {
    return nodeExecutionService.getStageDetailFromPlanExecutionId(planExecutionId, pipelineVersion);
  }

  /**
   * Backward-compatible overload – delegates to the DAG-aware version with {@code isDagEnabled=false}.
   */
  public String retryProcessedYaml(String originalExecutionId, String previousProcessedYaml,
      String currentProcessedYaml, List<String> retryStages, List<String> identifierOfSkipStages,
      String pipelineVersion, String accountId) throws IOException {
    return retryProcessedYaml(originalExecutionId, previousProcessedYaml, currentProcessedYaml, retryStages,
        identifierOfSkipStages, pipelineVersion, accountId, false);
  }

  /**
   * Produces the processed YAML for a retry execution.
   *
   * <p>For sequential pipelines ({@code isDagEnabled=false}) the existing linear-scan logic is used unchanged.
   *
   * <p>For DAG pipelines ({@code isDagEnabled=true}) see {@link #retryProcessedYamlForDAG}.
   *
   * @param originalExecutionId plan execution id of the run being retried; used for dynamic-stage expansion in the
   *     sequential path and reserved for the same in the DAG path
   * @param accountId account id used for feature-flag checks (e.g. OPA auto-injected stage handling)
   */
  public String retryProcessedYaml(String originalExecutionId, String previousProcessedYaml,
      String currentProcessedYaml, List<String> retryStages, List<String> identifierOfSkipStages,
      String pipelineVersion, String accountId, boolean isDagEnabled) throws IOException {
    if (isDagEnabled) {
      return retryProcessedYamlForDAG(originalExecutionId, previousProcessedYaml, currentProcessedYaml, retryStages,
          identifierOfSkipStages, pipelineVersion, accountId);
    }

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    JsonNode previousRootJsonNode = mapper.readTree(previousProcessedYaml);
    JsonNode currentRootJsonNode = mapper.readTree(currentProcessedYaml);

    if (previousRootJsonNode == null || currentRootJsonNode == null) {
      return currentProcessedYaml;
    }
    int stageCounter = 0;
    JsonNode stagesNode = PipelineYamlUtils.getStagesNodeFromRootNode(previousRootJsonNode);
    JsonNode currentStagesNode = PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode);
    // When strategy is defined in the stage, in that case we might not run some stages(under the strategy for that
    // stage). So we need to update the uuid for strategy node and next node.
    boolean isStrategyNodeProcessed = false;

    // Handle OPA evaluation stage retry logic when OPA is auto-injected.
    // OPA evaluation stage is auto-injected during plan creation but not saved to processedYaml.
    // When OPA is the retry stage and not in previousProcessedYaml, subsequent stages should run normally
    // (not be added to skip list). This is gated behind OPA_PIPELINE_GOVERNANCE feature flag.
    boolean hasOpaRetryStageNotInPreviousYaml = false;
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_RUN_ON_CUSTOMER_INFRA)
        && retryStages.contains("Harness_OPA_Evaluation")) {
      // OPA is the retry stage - since OPA is auto-injected and not saved to processedYaml,
      // it will never be found in previousProcessedYaml. Set flag to prevent skipping subsequent stages.
      log.info("RetryExecutionHelper: OPA evaluation stage is in retryStages. "
          + "OPA is auto-injected and not in previousProcessedYaml. Setting hasOpaRetryStageNotInPreviousYaml=true. "
          + "Subsequent stages should run normally.");
      hasOpaRetryStageNotInPreviousYaml = true;
    }
    log.info("RetryExecutionHelper: Final hasOpaRetryStageNotInPreviousYaml={}, identifierOfSkipStages={}",
        hasOpaRetryStageNotInPreviousYaml, identifierOfSkipStages);

    for (JsonNode stage : stagesNode) {
      if (PipelineYamlUtils.isInjectNode(stage, pipelineVersion) || isDynamicStage(stage, pipelineVersion)) {
        // if the stage does not belongs to the retry stages and is to be skipped, copy the stage node from the
        // previous processed yaml
        int stageCounterInsideInject = 0;
        JsonNode stagesInsideWrapper;
        String wrapperNodeKey;
        if (isDynamicStage(stage, pipelineVersion)) {
          if (HarnessYamlVersion.isV1(pipelineVersion)) {
            wrapperNodeKey = null;
            stagesInsideWrapper = fetchStagesNodeFromDynamicStageV1(originalExecutionId, stage);
            if (stagesInsideWrapper != null) {
              ((ObjectNode) currentStagesNode.get(stageCounter))
                  .set(YAMLFieldNameConstants.STAGES, stagesInsideWrapper);
            }
          } else {
            wrapperNodeKey = YAMLFieldNameConstants.STAGE;
            stagesInsideWrapper = fetchStagesNodeFromDynamicStage(originalExecutionId, stage);
            ((ObjectNode) currentStagesNode.get(stageCounter).get(YAMLFieldNameConstants.STAGE))
                .set(YAMLFieldNameConstants.STAGES, stagesInsideWrapper);
          }
        } else {
          wrapperNodeKey = YAMLFieldNameConstants.INSERT;
          stagesInsideWrapper = PipelineYamlUtils.getStagesNodeFromInjectNode(stage, pipelineVersion);
        }

        boolean shouldIterateWrapper = null != stagesInsideWrapper && stagesInsideWrapper.isArray();
        boolean exitOuterStageTraversal = false;
        if (shouldIterateWrapper) {
          ArrayNode currentStagesNodeInsideWrapper;
          if (wrapperNodeKey != null) {
            currentStagesNodeInsideWrapper =
                (ArrayNode) currentStagesNode.get(stageCounter).get(wrapperNodeKey).get(YAMLFieldNameConstants.STAGES);
          } else {
            currentStagesNodeInsideWrapper =
                (ArrayNode) currentStagesNode.get(stageCounter).get(YAMLFieldNameConstants.STAGES);
          }
          for (JsonNode wrappedStage : stagesInsideWrapper) {
            if (!PipelineYamlUtils.isParallelNode(wrappedStage, pipelineVersion)) {
              Pair<Integer, Boolean> stagesCounterAndStrategyProcessed = processNonParallelNode(wrappedStage,
                  currentStagesNodeInsideWrapper, pipelineVersion, retryStages, identifierOfSkipStages,
                  isStrategyNodeProcessed, stageCounterInsideInject, hasOpaRetryStageNotInPreviousYaml);
              if (isStrategyNodeProcessed) {
                break;
              }
              stageCounterInsideInject = stagesCounterAndStrategyProcessed.getLeft();
              isStrategyNodeProcessed = stagesCounterAndStrategyProcessed.getRight();
            } else {
              // parallel group
              stageCounterInsideInject = processParallelGroup(currentStagesNodeInsideWrapper, wrappedStage, retryStages,
                  identifierOfSkipStages, isStrategyNodeProcessed, stageCounterInsideInject, pipelineVersion);
              if (stageCounterInsideInject == -1) {
                // If some nodes in parallel are already successful, we only want to run the failed ones as PlanNode.
                // The remaining successful nodes will run as IdentityNode.
                // We copy the UUID of parallel nodes from the previously processed node. For successful nodes, we copy
                // the stage, meaning the UUID is copied for that stage node as well. If the UUID of the current node
                // matches the previously processed node's UUID, that stage node will run as an IdentityNode. Otherwise,
                // it will run as a PlanNode. Since the UUIDs of stages after the parallel section are not modified in
                // the current processed YAML, those stages will run as PlanNode by default.
                exitOuterStageTraversal = true;
                break;
              }
            }
          }
        }
        // If the previous stage ran as an Identity Node, the advisor will provide the next UUID as UUID_1.
        // To ensure proper linking, we are setting the UUID of the inject stage to UUID_1.
        if (wrapperNodeKey != null && currentStagesNode.get(stageCounter) != null
            && currentStagesNode.get(stageCounter).get(wrapperNodeKey) instanceof ObjectNode) {
          ((ObjectNode) currentStagesNode.get(stageCounter).get(wrapperNodeKey))
              .set(YAMLFieldNameConstants.UUID, stage.get(wrapperNodeKey).get(YAMLFieldNameConstants.UUID));
        }
        if (exitOuterStageTraversal) {
          break;
        }
        stageCounter = stageCounter + 1;
      } else if (PipelineYamlUtils.isStageGroupNode(stage, pipelineVersion)) {
        stageCounter =
            processStageGroup((ArrayNode) currentStagesNode, stage, retryStages, identifierOfSkipStages, stageCounter);
      } else if (!PipelineYamlUtils.isParallelNode(stage, pipelineVersion)) {
        Pair<Integer, Boolean> stagesCounterAndStrategyProcessed =
            processNonParallelNode(stage, (ArrayNode) currentStagesNode, pipelineVersion, retryStages,
                identifierOfSkipStages, isStrategyNodeProcessed, stageCounter, hasOpaRetryStageNotInPreviousYaml);
        if (isStrategyNodeProcessed) {
          break;
        }
        stageCounter = stagesCounterAndStrategyProcessed.getLeft();
        isStrategyNodeProcessed = stagesCounterAndStrategyProcessed.getRight();
      } else {
        // parallel group
        stageCounter = processParallelGroup((ArrayNode) currentStagesNode, stage, retryStages, identifierOfSkipStages,
            isStrategyNodeProcessed, stageCounter, pipelineVersion);
        if (stageCounter == -1) {
          break;
        }
      }
    }
    return currentRootJsonNode.toString();
  }

  /**
   * Produces the retry processed YAML for a DAG pipeline.
   *
   * <p>Run-set = user-selected retry stages plus all transitive downstream dependents. Skip-set = every other stage
   * (ancestors and unrelated branches), replayed as IdentityNodes by copying previous-execution UUIDs.
   *
   * @param originalExecutionId plan execution id of the run being retried; used when expanding dynamic stages
   * @param accountId account id for feature-flag checks (e.g. OPA auto-injected stage handling)
   */
  @VisibleForTesting
  String retryProcessedYamlForDAG(String originalExecutionId, String previousProcessedYaml, String currentProcessedYaml,
      List<String> retryStages, List<String> identifierOfSkipStages, String pipelineVersion, String accountId)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode previousRoot = mapper.readTree(previousProcessedYaml);
    JsonNode currentRoot = mapper.readTree(currentProcessedYaml);

    if (previousRoot == null || currentRoot == null) {
      return currentProcessedYaml;
    }

    JsonNode previousStagesNode = PipelineYamlUtils.getStagesNodeFromRootNode(previousRoot);
    JsonNode currentStagesNode = PipelineYamlUtils.getStagesNodeFromRootNode(currentRoot);

    if (previousStagesNode == null || currentStagesNode == null) {
      log.warn("retryProcessedYamlForDAG: stages node missing in YAML (previous={}, current={}); "
              + "falling back to unmodified current YAML",
          previousStagesNode == null, currentStagesNode == null);
      return currentProcessedYaml;
    }

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_RUN_ON_CUSTOMER_INFRA)
        && retryStages.contains("Harness_OPA_Evaluation")) {
      log.info("retryProcessedYamlForDAG: OPA evaluation stage is in retryStages and OPA_RUN_ON_CUSTOMER_INFRA is "
          + "enabled. OPA is auto-injected and not in processedYaml; all stages will run as PlanNodes.");
      return currentProcessedYaml;
    }

    Map<String, JsonNode> identifierToPreviousElement = new HashMap<>();
    indexDagStageElementsByIdentifier(
        previousStagesNode, identifierToPreviousElement, pipelineVersion, originalExecutionId);

    Map<String, List<String>> dependsOnMap = new LinkedHashMap<>();
    Set<String> allIdentifiers = new LinkedHashSet<>();
    collectDagDependsOnGraph(currentStagesNode, dependsOnMap, allIdentifiers, pipelineVersion, originalExecutionId);

    Map<String, List<String>> forwardGraph = new HashMap<>();
    for (String id : allIdentifiers) {
      forwardGraph.put(id, new ArrayList<>());
    }
    for (Map.Entry<String, List<String>> entry : dependsOnMap.entrySet()) {
      String dependent = entry.getKey();
      for (String dependency : entry.getValue()) {
        forwardGraph.computeIfAbsent(dependency, k -> new ArrayList<>()).add(dependent);
      }
    }

    List<String> unknownRetryStages =
        retryStages.stream().filter(s -> !allIdentifiers.contains(s)).collect(Collectors.toList());
    if (!unknownRetryStages.isEmpty()) {
      log.warn("retryProcessedYamlForDAG: retry stage identifiers not found in current pipeline YAML: {}. "
              + "These will be ignored.",
          unknownRetryStages);
    }

    Set<String> runSet = new LinkedHashSet<>(retryStages);
    Queue<String> queue = new LinkedList<>(retryStages);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (String downstream : forwardGraph.getOrDefault(current, Collections.emptyList())) {
        if (runSet.add(downstream)) {
          queue.offer(downstream);
        }
      }
    }

    Set<String> skipSet = new HashSet<>(allIdentifiers);
    skipSet.removeAll(runSet);

    applyDagSkipSetToStagesArray((ArrayNode) currentStagesNode, skipSet, identifierToPreviousElement,
        identifierOfSkipStages, pipelineVersion, originalExecutionId);

    return currentRoot.toString();
  }

  private void indexDagStageElementsByIdentifier(JsonNode stagesNode, Map<String, JsonNode> identifierToPreviousElement,
      String pipelineVersion, String originalExecutionId) throws IOException {
    if (stagesNode == null || !stagesNode.isArray()) {
      return;
    }
    for (JsonNode stageElement : stagesNode) {
      if (PipelineYamlUtils.isInjectNode(stageElement, pipelineVersion)) {
        indexDagStageElementsByIdentifier(PipelineYamlUtils.getStagesNodeFromInjectNode(stageElement, pipelineVersion),
            identifierToPreviousElement, pipelineVersion, originalExecutionId);
      } else if (isDynamicStage(stageElement, pipelineVersion)) {
        addDagStageElementToIndex(stageElement, identifierToPreviousElement, pipelineVersion);
        indexDagStageElementsByIdentifier(fetchStagesNodeFromDynamicStage(originalExecutionId, stageElement),
            identifierToPreviousElement, pipelineVersion, originalExecutionId);
      } else if (PipelineYamlUtils.isParallelNode(stageElement, pipelineVersion)) {
        indexDagStageElementsByIdentifier(
            PipelineYamlUtils.getStagesNodeFromParallelNode(stageElement, pipelineVersion), identifierToPreviousElement,
            pipelineVersion, originalExecutionId);
      } else if (!PipelineYamlUtils.isStageGroupNode(stageElement, pipelineVersion)) {
        addDagStageElementToIndex(stageElement, identifierToPreviousElement, pipelineVersion);
      }
    }
  }

  private void collectDagDependsOnGraph(JsonNode stagesNode, Map<String, List<String>> dependsOnMap,
      Set<String> allIdentifiers, String pipelineVersion, String originalExecutionId) throws IOException {
    if (stagesNode == null || !stagesNode.isArray()) {
      return;
    }
    for (JsonNode stageElement : stagesNode) {
      if (PipelineYamlUtils.isInjectNode(stageElement, pipelineVersion)) {
        collectDagDependsOnGraph(PipelineYamlUtils.getStagesNodeFromInjectNode(stageElement, pipelineVersion),
            dependsOnMap, allIdentifiers, pipelineVersion, originalExecutionId);
      } else if (isDynamicStage(stageElement, pipelineVersion)) {
        collectDependsOnForStageElement(stageElement, dependsOnMap, allIdentifiers, pipelineVersion);
        collectDagDependsOnGraph(fetchStagesNodeFromDynamicStage(originalExecutionId, stageElement), dependsOnMap,
            allIdentifiers, pipelineVersion, originalExecutionId);
      } else if (PipelineYamlUtils.isParallelNode(stageElement, pipelineVersion)) {
        collectDagDependsOnGraph(PipelineYamlUtils.getStagesNodeFromParallelNode(stageElement, pipelineVersion),
            dependsOnMap, allIdentifiers, pipelineVersion, originalExecutionId);
      } else if (!PipelineYamlUtils.isStageGroupNode(stageElement, pipelineVersion)) {
        collectDependsOnForStageElement(stageElement, dependsOnMap, allIdentifiers, pipelineVersion);
      }
    }
  }

  private void collectDependsOnForStageElement(JsonNode stageElement, Map<String, List<String>> dependsOnMap,
      Set<String> allIdentifiers, String pipelineVersion) {
    String identifier = PipelineYamlUtils.getIdentifierFromStageNode(stageElement, pipelineVersion);
    if (identifier == null) {
      return;
    }
    allIdentifiers.add(identifier);
    JsonNode stageInner = PipelineYamlUtils.getStageNodeFromStagesElement(stageElement, pipelineVersion);
    List<String> deps = new ArrayList<>();
    if (stageInner != null) {
      JsonNode dependsOnNode = stageInner.get(YAMLFieldNameConstants.DEPENDS_ON);
      if (dependsOnNode != null && dependsOnNode.isArray()) {
        for (JsonNode dep : dependsOnNode) {
          String depText = dep.textValue();
          if (depText != null) {
            deps.add(depText);
          }
        }
      }
    }
    dependsOnMap.put(identifier, deps);
  }

  private void addDagStageElementToIndex(
      JsonNode stageElement, Map<String, JsonNode> identifierToPreviousElement, String pipelineVersion) {
    String identifier = PipelineYamlUtils.getIdentifierFromStageNode(stageElement, pipelineVersion);
    if (identifier != null) {
      identifierToPreviousElement.put(identifier, stageElement);
    }
  }

  private void applyDagSkipSetToStagesArray(ArrayNode currentStagesNode, Set<String> skipSet,
      Map<String, JsonNode> identifierToPreviousElement, List<String> identifierOfSkipStages, String pipelineVersion,
      String originalExecutionId) throws IOException {
    for (JsonNode currentElement : currentStagesNode) {
      if (PipelineYamlUtils.isInjectNode(currentElement, pipelineVersion)) {
        JsonNode innerStages = PipelineYamlUtils.getStagesNodeFromInjectNode(currentElement, pipelineVersion);
        if (innerStages instanceof ArrayNode) {
          applyDagSkipSetToStagesArray((ArrayNode) innerStages, skipSet, identifierToPreviousElement,
              identifierOfSkipStages, pipelineVersion, originalExecutionId);
        }
      } else if (isDynamicStage(currentElement, pipelineVersion)) {
        JsonNode innerStages = fetchStagesNodeFromDynamicStage(originalExecutionId, currentElement);
        if (innerStages != null) {
          ((ObjectNode) currentElement.get(YAMLFieldNameConstants.STAGE))
              .set(YAMLFieldNameConstants.STAGES, innerStages);
          if (innerStages instanceof ArrayNode) {
            applyDagSkipSetToStagesArray((ArrayNode) innerStages, skipSet, identifierToPreviousElement,
                identifierOfSkipStages, pipelineVersion, originalExecutionId);
          }
        }
        applyDagSkipToStageElement(
            currentElement, skipSet, identifierToPreviousElement, identifierOfSkipStages, pipelineVersion);
      } else if (PipelineYamlUtils.isParallelNode(currentElement, pipelineVersion)) {
        JsonNode innerStages = PipelineYamlUtils.getStagesNodeFromParallelNode(currentElement, pipelineVersion);
        if (innerStages instanceof ArrayNode) {
          applyDagSkipSetToStagesArray((ArrayNode) innerStages, skipSet, identifierToPreviousElement,
              identifierOfSkipStages, pipelineVersion, originalExecutionId);
        }
      } else if (!PipelineYamlUtils.isStageGroupNode(currentElement, pipelineVersion)) {
        applyDagSkipToStageElement(
            currentElement, skipSet, identifierToPreviousElement, identifierOfSkipStages, pipelineVersion);
      }
    }
  }

  private void applyDagSkipToStageElement(JsonNode currentElement, Set<String> skipSet,
      Map<String, JsonNode> identifierToPreviousElement, List<String> identifierOfSkipStages, String pipelineVersion) {
    String identifier = PipelineYamlUtils.getIdentifierFromStageNode(currentElement, pipelineVersion);
    if (identifier == null || !skipSet.contains(identifier)) {
      return;
    }
    JsonNode previousElement = identifierToPreviousElement.get(identifier);
    if (previousElement != null) {
      YamlUtils.replaceFieldInJsonNodeFromAnotherJsonNode(currentElement, previousElement, YAMLFieldNameConstants.UUID);
    }
    identifierOfSkipStages.add(identifier);
  }

  /*
  stageCounter: It will be stageCounterInsideInject when called from Inject Block, else will be hold stageCounter of
  Pipeline level stages.
   */
  private Pair<Integer, Boolean> processNonParallelNode(JsonNode stageNode, ArrayNode stagesArrayNode,
      String pipelineVersion, List<String> retryStages, List<String> identifierOfSkipStages,
      boolean isStrategyNodeProcessed, int stageCounter, boolean hasOpaRetryStageNotInPreviousYaml) {
    String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageNode, pipelineVersion);

    // Don't add to skip list if OPA is the retry stage but not in previousProcessedYaml
    if (!retryStages.contains(stageIdentifier) && !isStrategyNodeProcessed && !hasOpaRetryStageNotInPreviousYaml) {
      identifierOfSkipStages.add(stageIdentifier);

      stagesArrayNode.set(stageCounter, stageNode);
      stageCounter++;
    } else {
      JsonNode currentResumableStagejsonNode = stagesArrayNode.get(stageCounter);

      if (isStrategyNodeProcessed) {
        ((ObjectNode) PipelineYamlUtils.getStageNodeFromStagesElement(currentResumableStagejsonNode, pipelineVersion))
            .set(YAMLFieldNameConstants.UUID,
                PipelineYamlUtils.getStageNodeFromStagesElement(stageNode, pipelineVersion)
                    .get(YAMLFieldNameConstants.UUID));
        return Pair.of(-1, isStrategyNodeProcessed); // break the loop
      }

      YamlUtils.replaceFieldInJsonNodeFromAnotherJsonNode(
          currentResumableStagejsonNode, stageNode, YAMLFieldNameConstants.UUID);
      stageCounter++;

      isStrategyNodeProcessed = true;
    }
    return Pair.of(stageCounter, isStrategyNodeProcessed);
  }

  private int processStageGroup(ArrayNode stagesArrayNode, JsonNode stageGroupNode, List<String> retryStages,
      List<String> identifierOfSkipStages, int stageCounter) {
    String stageGroupIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageGroupNode, HarnessYamlVersion.V1);
    if (!retryStages.contains(stageGroupIdentifier)) {
      stagesArrayNode.set(stageCounter, stageGroupNode);
      identifierOfSkipStages.addAll(PipelineYamlUtils.getStageIdentifiersFromStageGroup(stageGroupNode));
    }
    return stageCounter + 1;
  }

  /*
  stageCounter: It will be stageCounterInsideInject when called from Inject Block, else will be hold stageCounter of
  Pipeline level stages.
   */
  private int processParallelGroup(ArrayNode stagesArrayNode, JsonNode parallelNode, List<String> retryStages,
      List<String> identifierOfSkipStages, boolean isStrategyNodeProcessed, int stageCounter, String pipelineVersion) {
    if (!isRetryStagesInParallelStages(PipelineYamlUtils.getStagesNodeFromParallelNode(parallelNode, pipelineVersion),
            retryStages, identifierOfSkipStages, isStrategyNodeProcessed, pipelineVersion)
        && !isStrategyNodeProcessed) {
      // If the parallel group does not contain the retry stages, copy the whole parallel node
      stagesArrayNode.set(stageCounter, parallelNode);
      stageCounter = stageCounter + 1;
      return stageCounter;
    } else {
      // Replace only those stages that need to be skipped
      stagesArrayNode.set(stageCounter,
          replaceStagesInParallelGroup(PipelineYamlUtils.getStagesNodeFromParallelNode(parallelNode, pipelineVersion),
              retryStages, stagesArrayNode.get(stageCounter), identifierOfSkipStages, isStrategyNodeProcessed,
              pipelineVersion));

      // Replace UUID for the parallel node
      ((ObjectNode) stagesArrayNode.get(stageCounter))
          .set(YAMLFieldNameConstants.UUID, parallelNode.get(YAMLFieldNameConstants.UUID));

      // Break the loop after processing the stage
      return -1;
    }
  }

  private JsonNode replaceStagesInParallelGroup(JsonNode parallelStage, List<String> retryStages,
      JsonNode currentParallelStageNode, List<String> identifierOfSkipStages, boolean isStrategyNodeProcessed,
      String pipelineVersion) {
    int stageCounter = 0;
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageNode, pipelineVersion);
      if (!retryStages.contains(stageIdentifier) && !isStrategyNodeProcessed) {
        identifierOfSkipStages.add(stageIdentifier);
        ((ArrayNode) PipelineYamlUtils.getStagesNodeFromParallelNode(currentParallelStageNode, pipelineVersion))
            .set(stageCounter, stageNode);
      } else {
        // replace only the uuid of the retry parallel stage
        JsonNode currentResumableStagejsonNode = PipelineYamlUtils.getStageNodeFromStagesNode(
            (ArrayNode) PipelineYamlUtils.getStagesNodeFromParallelNode(currentParallelStageNode, pipelineVersion),
            stageCounter, pipelineVersion);
        // Replacing all the UUIDs under the stage node.
        YamlUtils.replaceFieldInJsonNodeFromAnotherJsonNode(currentResumableStagejsonNode,
            PipelineYamlUtils.getStageNodeFromStagesElement(stageNode, pipelineVersion), YAMLFieldNameConstants.UUID);
      }
      stageCounter++;
    }

    return currentParallelStageNode;
  }

  private boolean isRetryStagesInParallelStages(JsonNode parallelStage, List<String> retryStages,
      List<String> identifierOfSkipStages, boolean isStrategyNodeProcessed, String pipelineVersion) {
    List<String> stagesIdentifierInParallelNode = new ArrayList<>();
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageNode, pipelineVersion);
      stagesIdentifierInParallelNode.add(stageIdentifier);
      if (retryStages.contains(stageIdentifier)) {
        return true;
      }
    }
    /*
    This whole parallel node will get copied. We need to copy the stage identifier in identifierForSkipStages
     */
    if (!isStrategyNodeProcessed) {
      identifierOfSkipStages.addAll(stagesIdentifierInParallelNode);
    }
    return false;
  }

  /**
   * @param plan                        Initial plan created without considering retry
   * @param identifierOfSkipStages      identifier of stages that are to be skipped during the retry.
   * @param previousExecutionId         planExecutionId of the execution that is being retried.
   * @param stageIdentifiersToRetryWith stage identifiers of the stages from which the execution is being retried.
   * @param runAllStages                this is added to decide if all matrix nodes needs to be retried or only the
   *     failed ones
   * @return Returns the transformed Plan for the retry
   * This method operates on 3 kind of nodes:
   * 1. Nodes that belong to stages that are to be skipped: Convert all planNodes into IdentityNodes.
   * 2. Nodes that belong to the stages that are being retried: Only the strategy node that is parent of stage will be
   * converted into IdentityNode. Rest all will remain as planNodes.
   * 3. Nodes belong to subsequent stages: Will remain as planNodes and will be executed as normal execution.
   */
  public Plan transformPlan(Plan plan, List<String> identifierOfSkipStages, String previousExecutionId,
      List<String> stageIdentifiersToRetryWith, boolean runAllStages) {
    List<Node> finalUpdatedPlanNodes = new ArrayList<>();
    // identifierOfSkipStages: previousStageIdentifiers we want to skip
    List<String> stageFqnForStagesToBeSkipped =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(previousExecutionId, identifierOfSkipStages);
    // Adding nodes to be skipped in the finalUpdatedPlanNodes list.
    finalUpdatedPlanNodes.addAll(handleNodesForStagesBeingSkipped(previousExecutionId, stageFqnForStagesToBeSkipped));

    // Get all nodes that will be re-executed.(Does not belong to the stages to be skipped)
    List<Node> planNodesToBeExecuted = plan.getPlanNodes()
                                           .stream()
                                           .filter(node -> !stageFqnForStagesToBeSkipped.contains(node.getStageFqn()))
                                           .collect(Collectors.toList());

    List<String> stageFqnForStagesBeingRetried =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(previousExecutionId, stageIdentifiersToRetryWith);
    List<Node> strategyNodes = new ArrayList<>();
    // Filtering the strategy nodes of stages that are being retried and populating the strategyNodes list with such
    // nodes. The nodes after filtering will remain as is and will not be converted into IdentityNodes.
    planNodesToBeExecuted = filterStrategyNodesForStagesBeingRetried(
        planNodesToBeExecuted, stageFqnForStagesBeingRetried, strategyNodes, runAllStages);

    // Adding nodes to be re-executed in the finalUpdatedPlanNodes list.
    finalUpdatedPlanNodes.addAll(planNodesToBeExecuted);

    // Adding nodes for the stages that are being retried.
    finalUpdatedPlanNodes.addAll(
        handleStrategyNodeForStagesBeingRetried(strategyNodes, stageFqnForStagesBeingRetried, previousExecutionId));

    Map<String, Node> nodeIdToPlanNodes = new HashMap<>();
    for (Node node : finalUpdatedPlanNodes) {
      nodeIdToPlanNodes.put(node.getUuid(), node);
    }
    for (Node node : plan.getPlanNodes()) {
      if (!nodeIdToPlanNodes.containsKey(node.getUuid())) {
        nodeIdToPlanNodes.put(node.getUuid(), node);
      }
    }

    return Plan.builder()
        .uuid(plan.getUuid())
        .planNodes(nodeIdToPlanNodes.values())
        .startingNodeId(plan.getStartingNodeId())
        .setupAbstractions(plan.getSetupAbstractions())
        .graphLayoutInfo(plan.getGraphLayoutInfo())
        .validUntil(plan.getValidUntil())
        .valid(plan.isValid())
        .errorResponse(plan.getErrorResponse())
        .build();
  }

  private List<Node> handleNodesForStagesBeingSkipped(
      String previousExecutionId, List<String> stagesFqnForStageToBeSkipped) {
    List<Node> identityNodesList = new ArrayList<>();
    // NodeExecutionUuid -> Node for the nodes those belong to stages that will be skipped.
    Map<String, Node> nodeUuidToNodeExecutionUuid = nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
        previousExecutionId, stagesFqnForStageToBeSkipped);
    nodeUuidToNodeExecutionUuid.forEach((nodeExecutionUuid, planNode)
                                            -> identityNodesList.add(IdentityPlanNode.mapPlanNodeToIdentityNode(
                                                planNode, planNode.getStepType(), nodeExecutionUuid)));
    return identityNodesList;
  }
  private List<Node> filterStrategyNodesForStagesBeingRetried(
      List<Node> planNodes, List<String> stagesFqnToRetryWith, List<Node> strategyNodes, boolean runAllStages) {
    List<Node> filteredPlanNodesList = new ArrayList<>();
    for (Node node : planNodes) {
      if (!runAllStages && stagesFqnToRetryWith.contains(node.getStageFqn())
          && node.getStepCategory() == StepCategory.STRATEGY) {
        strategyNodes.add(node);
      } else {
        filteredPlanNodesList.add(node);
      }
    }
    return filteredPlanNodesList;
  }

  public RetryHistoryResponseDto getRetryHistory(String accountId, String rootParentId, String currentPlanExecutionId) {
    boolean rootExecutionFound = false;
    List<ExecutionInfo> executionInfos = new ArrayList<>();
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      Query elasticQuery = pipelineSearchService.formQueryForRootExecutionId(accountId, rootParentId);
      List<PipelineSearchReadExecutionSummaryDTO> pipelineSearchExecutionSummaryDTOList =
          pipelineSearchService.listExecutions(accountId, elasticQuery, null, RETRY_HISTORY_LIMIT);
      for (PipelineSearchReadExecutionSummaryDTO pipelineSearchReadExecutionSummaryDTO :
          pipelineSearchExecutionSummaryDTOList) {
        executionInfos.add(convertToExecutionInfo(pipelineSearchReadExecutionSummaryDTO));
        if (pipelineSearchReadExecutionSummaryDTO.getPlanExecutionId().equals(rootParentId)) {
          rootExecutionFound = true;
        }
      }
    } else {
      try (Stream<PipelineExecutionSummaryEntity> stream =
               pmsExecutionSummaryRespository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
                   rootParentId)) {
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineExecutionSummaryEntity entity = iterator.next();
          if (entity.getPlanExecutionId().equals(rootParentId)) {
            rootExecutionFound = true;
          }
          executionInfos.add(convertToExecutionInfo(entity));
        }
      }
    }

    if (!rootExecutionFound) {
      PipelineExecutionSummaryEntity rootExecution = pmsExecutionSummaryService.fetchFromSecondaryWithProjections(
          accountId, rootParentId, PipelineExecutionSummaryEntityProjectionConstants.fieldsForRetryHistory);
      if (rootExecution != null) {
        executionInfos.add(convertToExecutionInfo(rootExecution));
      }
    }
    if (executionInfos.size() <= 1) {
      return RetryHistoryResponseDto.builder().errorMessage("Nothing to show in retry history").build();
    }

    String latestRetryExecutionId = executionInfos.get(0).getUuid();
    RetryStagesMetadata retryStagesMetadata =
        planExecutionMetadataService.getRetryStagesMetadata(accountId, currentPlanExecutionId);
    if (retryStagesMetadata != null) {
      return RetryHistoryResponseDto.builder()
          .executionInfos(executionInfos)
          .latestExecutionId(latestRetryExecutionId)
          .retryStagesMetadata(toRetryStagesMetadataDTO(retryStagesMetadata))
          .build();
    } else {
      return RetryHistoryResponseDto.builder()
          .executionInfos(executionInfos)
          .latestExecutionId(latestRetryExecutionId)
          .build();
    }
  }

  private RetryStagesMetadataDTO toRetryStagesMetadataDTO(RetryStagesMetadata retryStagesMetadata) {
    return RetryStagesMetadataDTO.builder()
        .retryStagesIdentifier(retryStagesMetadata.getRetryStagesIdentifier())
        .skipStagesIdentifier(retryStagesMetadata.getSkipStagesIdentifier())
        .build();
  }

  private List<Node> handleStrategyNodeForStagesBeingRetried(
      List<Node> planNodes, List<String> stagesFqnToRetryWith, String previousExecutionId) {
    List<NodeExecution> strategyNodeExecutions =
        nodeExecutionService.fetchStrategyNodeExecutions(previousExecutionId, stagesFqnToRetryWith);
    List<Node> processedNodes = new ArrayList<>();
    for (Node node : planNodes) {
      // Find the strategyNodeExecution that belong to the node. And its on the stage.(Basically to check if node is of
      // type stage or not). We need to convert only the stage's strategy node into IdentityNode.
      Optional<NodeExecution> strategyNodeExecution =
          strategyNodeExecutions.stream()
              .filter(o -> node.getUuid().equals(o.getNodeId()))
              .filter(o -> AmbianceUtils.isCurrentStrategyLevelAtStage(o.getLevels()))
              .findFirst();
      // If current node is not stage strategy, or it does not have any children then do not convert to identityNode but
      // keep as planNode only.
      if (strategyNodeExecution.isEmpty()
          || EmptyPredicate.isEmpty(strategyNodeExecution.get().getExecutableResponses())) {
        processedNodes.add(node);
      } else {
        // This strategyNodeExecution is at the stage level. And the execution is being retried from this strategy
        // stage. And setting useAdviserObtainments true because we want that IdentityNodeExecutionStrategy to use the
        // original advisorsObtainments from the node.
        processedNodes.add(
            IdentityPlanNode.mapPlanNodeToIdentityNode(node, node.getStepType(), strategyNodeExecution.get().getUuid())
                .withUseAdviserObtainments(true));
      }
    }
    return processedNodes;
  }

  public RetryLatestExecutionResponseDto getRetryLatestExecutionId(String accountIdentifier, String rootParentId) {
    RetryExecutionInfoDTO retryExecutionInfoDTO =
        pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountIdentifier, rootParentId);
    if (retryExecutionInfoDTO == null || retryExecutionInfoDTO.getPlanExecutionId().equals(rootParentId)) {
      return RetryLatestExecutionResponseDto.builder().errorMessage("This is not a part of retry execution").build();
    }
    return RetryLatestExecutionResponseDto.builder()
        .latestExecutionId(retryExecutionInfoDTO.getPlanExecutionId())
        .build();
  }

  private List<ExecutionInfo> fetchExecutionInfoFromPipelineEntities(
      List<PipelineExecutionSummaryEntity> summaryEntityList) {
    return summaryEntityList.stream()
        .map(entity -> {
          return ExecutionInfo.builder()
              .uuid(entity.getPlanExecutionId())
              .startTs(entity.getStartTs())
              .status(entity.getStatus())
              .endTs(entity.getEndTs())
              .build();
        })
        .collect(Collectors.toList());
  }

  private ExecutionInfo convertToExecutionInfo(
      PipelineSearchReadExecutionSummaryDTO pipelineSearchReadExecutionSummaryDTO) {
    ExecutionInfoBuilder executionInfoBuilder =
        ExecutionInfo.builder()
            .uuid(pipelineSearchReadExecutionSummaryDTO.getPlanExecutionId())
            .startTs(pipelineSearchReadExecutionSummaryDTO.getStartTs())
            .endTs(pipelineSearchReadExecutionSummaryDTO.getEndTs())
            .runSequence(pipelineSearchReadExecutionSummaryDTO.getRunSequence());
    if (EmptyPredicate.isNotEmpty(pipelineSearchReadExecutionSummaryDTO.getStatus())) {
      executionInfoBuilder.status(ExecutionStatus.valueOf(pipelineSearchReadExecutionSummaryDTO.getStatus()));
    }
    return executionInfoBuilder.build();
  }

  private ExecutionInfo convertToExecutionInfo(PipelineExecutionSummaryEntity entity) {
    return ExecutionInfo.builder()
        .uuid(entity.getPlanExecutionId())
        .startTs(entity.getStartTs())
        .status(entity.getStatus())
        .endTs(entity.getEndTs())
        .runSequence(entity.getRunSequence())
        .build();
  }

  /**
   * @param pipelineExecutionSummaryEntity Checks if retry history is to be shown for this execution
   *                                       Based on this flag UI shows the button if they should show retry history
   *                                       on execution view page
   * @return Returns boolean whether to show retry history
   */
  public boolean shouldShowRetryHistory(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (!pipelineExecutionSummaryEntity.getPlanExecutionId().equals(
            pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId())) {
      // if this execution id is different from the root execution id this means that there was a retry
      return true;
    }
    RetryExecutionInfoDTO latestRetryExecutionInfoDTO =
        pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(pipelineExecutionSummaryEntity.getAccountId(),
            pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    return latestRetryExecutionInfoDTO != null
        && !latestRetryExecutionInfoDTO.getPlanExecutionId().equals(
            pipelineExecutionSummaryEntity.getPlanExecutionId());
  }

  /**
   * @param pipelineExecutionSummaryEntity Checks if this execution is the latest, based on which it will retry
   *                                       the execution. This is needed as we will deprecate isLatestExecution
   * @return Returns boolean whether this execution is the latest one
   */
  public Boolean isLatestExecution(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    RetryExecutionInfoDTO latestRetryExecutionInfoDTO =
        pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(pipelineExecutionSummaryEntity.getAccountId(),
            pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    if (latestRetryExecutionInfoDTO != null) {
      return latestRetryExecutionInfoDTO.getPlanExecutionId().equals(
          pipelineExecutionSummaryEntity.getPlanExecutionId());
    }
    return true;
  }

  public List<String> getInputSetIdForRerunPipeline(String accountId, String originalExecutionId) {
    try {
      Set<String> projections = Collections.singleton(PlanExecutionSummaryKeys.inputSetIdentifiers);

      PipelineExecutionSummaryEntity executionSummary =
          pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
              accountId, originalExecutionId, projections);

      if (executionSummary != null && isNotEmpty(executionSummary.getInputSetIdentifiers())) {
        return executionSummary.getInputSetIdentifiers();
      }

      return Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to fetch input set IDs for execution: " + originalExecutionId, e);
      return Collections.emptyList();
    }
  }

  private JsonNode fetchStagesNodeFromDynamicStage(String originalExecutionId, JsonNode stageNode) {
    Optional<DynamicExecutionInstanceResponseDTO> dynamicExecutionInstanceResponseDTO =
        dynamicExecutionService.getByPlanExecutionIdAndIdentifier(originalExecutionId,
            stageNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText());
    if (dynamicExecutionInstanceResponseDTO.isEmpty()) {
      // If instance was not found means the dynamic stage was not executed.
      return null;
    }

    String processedYaml = dynamicExecutionInstanceResponseDTO.get().getProcessedYaml();
    YamlField yamlField = YamlUtils.readYamlTree(processedYaml);
    if (yamlField.getNode().getField((YAMLFieldNameConstants.PIPELINE)) != null
        && yamlField.getNode()
                .getField(YAMLFieldNameConstants.PIPELINE)
                .getNode()
                .getField(YAMLFieldNameConstants.STAGES)
            != null) {
      return yamlField.getNode()
          .getField(YAMLFieldNameConstants.PIPELINE)
          .getNode()
          .getField(YAMLFieldNameConstants.STAGES)
          .getNode()
          .getCurrJsonNode();
    }
    return null;
  }

  private boolean isDynamicStage(JsonNode jsonNode, String pipelineVersion) {
    if (jsonNode == null) {
      return false;
    }
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return jsonNode.has(YAMLFieldNameConstants.TYPE)
          && YAMLFieldNameConstants.DYNAMIC_STAGE_V1.equals(jsonNode.get(YAMLFieldNameConstants.TYPE).asText());
    }
    if (jsonNode.has(YAMLFieldNameConstants.STAGE)
        && jsonNode.get(YAMLFieldNameConstants.STAGE).has(YAMLFieldNameConstants.TYPE)
        && StepSpecTypeConstants.DYNAMIC_STAGE.equals(
            jsonNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.TYPE).asText())) {
      return true;
    }
    return false;
  }

  private JsonNode fetchStagesNodeFromDynamicStageV1(String originalExecutionId, JsonNode stageNode) {
    Optional<DynamicExecutionInstanceResponseDTO> dynamicExecutionInstanceResponseDTO =
        dynamicExecutionService.getByPlanExecutionIdAndIdentifier(
            originalExecutionId, stageNode.get(YAMLFieldNameConstants.ID).asText());
    if (dynamicExecutionInstanceResponseDTO.isEmpty()) {
      log.warn("Dropping dynamic stage '{}' from retry YAML: no DynamicExecutionInstance found"
              + " for originalExecutionId={}",
          stageNode.get(YAMLFieldNameConstants.ID).asText(), originalExecutionId);
      return null;
    }
    String processedYaml = dynamicExecutionInstanceResponseDTO.get().getProcessedYaml();
    YamlField yamlField = YamlUtils.readYamlTree(processedYaml);
    if (yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE) != null
        && yamlField.getNode()
                .getField(YAMLFieldNameConstants.PIPELINE)
                .getNode()
                .getField(YAMLFieldNameConstants.STAGES)
            != null) {
      return yamlField.getNode()
          .getField(YAMLFieldNameConstants.PIPELINE)
          .getNode()
          .getField(YAMLFieldNameConstants.STAGES)
          .getNode()
          .getCurrJsonNode();
    }
    return null;
  }
}
