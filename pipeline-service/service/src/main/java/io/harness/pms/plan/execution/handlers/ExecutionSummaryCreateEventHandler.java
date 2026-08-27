/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.beans.FeatureName.DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT;
import static io.harness.beans.FeatureName.PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.DynamicOrchestrationStartInfo;
import io.harness.engine.observers.beans.OrchestrationQueueInfo;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.plan.Plan;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.plancreator.strategy.v1.StrategyTypeV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.template.TemplateReferenceSummary;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.LabelsHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.StoreTypeMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class ExecutionSummaryCreateEventHandler implements OrchestrationStartObserver {
  private static List<String> INTERNAL_NODE_TYPES = Lists.newArrayList(YAMLFieldNameConstants.PARALLEL);
  private final PMSPipelineService pmsPipelineService;
  private final PlanService planService;
  private final PlanExecutionService planExecutionService;
  private final NodeTypeLookupService nodeTypeLookupService;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final NotificationHelper notificationHelper;
  private final RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final PMSExecutionService pmsExecutionService;
  private final PipelineExecutionGitMetadataService executionGitMetadataService;
  private final PmsFeatureFlagService pmsFeatureFlagService;

  @Inject
  public ExecutionSummaryCreateEventHandler(PMSPipelineService pmsPipelineService, PlanService planService,
      PlanExecutionService planExecutionService, NodeTypeLookupService nodeTypeLookupService,
      PmsGitSyncHelper pmsGitSyncHelper, NotificationHelper notificationHelper,
      RecentExecutionsInfoHelper recentExecutionsInfoHelper, PmsExecutionSummaryService pmsExecutionSummaryService,
      PMSExecutionService pmsExecutionService, PipelineExecutionGitMetadataService executionGitMetadataService,
      PmsFeatureFlagService pmsFeatureFlagService) {
    this.pmsPipelineService = pmsPipelineService;
    this.planService = planService;
    this.planExecutionService = planExecutionService;
    this.nodeTypeLookupService = nodeTypeLookupService;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.notificationHelper = notificationHelper;
    this.recentExecutionsInfoHelper = recentExecutionsInfoHelper;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
    this.pmsExecutionService = pmsExecutionService;
    this.executionGitMetadataService = executionGitMetadataService;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
  }

  private record QueuedExecutionSummaryResult(String pipelineId, Optional<PipelineEntity> pipelineEntity,
      String rootExecutionId, RetryExecutionMetadata retryExecutionMetadata) {}

  private record UpdateExecutionSummaryResult(String startingNodeId, List<String> startingNodeIds, boolean isDagEnabled,
      Map<String, List<String>> dependencyGraph, Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap,
      Set<String> modules) {}

  @Override
  public void onStart(OrchestrationStartInfo orchestrationStartInfo) {
    Ambiance ambiance = orchestrationStartInfo.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
                              .build();
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        orchestrationStartInfo.getPlanExecutionMetadataWithContext();
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();

    ExecutionMetadata metadata = planExecution.getMetadata();
    boolean queuedPlanCreationFFEnabled = OrchestrationUtils.checkAsyncPlanCreation(
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name()),
        AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS.name()),
        ambiance.getMetadata().getTriggerInfo(), planExecutionMetadataWithContext.getIsAsyncPlanCreation());

    QueuedExecutionSummaryResult queuedExecutionSummaryResult = queuedPlanCreationFFEnabled
        ? null
        : getQueuedExecutionSummaryResult(metadata, planExecutionMetadataWithContext, accountId, orgId, projectId,
              scopeInfo, true, planExecutionId, planExecutionMetadata, planExecution);
    UpdateExecutionSummaryResult result = getResult(ambiance, planExecutionMetadataWithContext);

    if (!queuedPlanCreationFFEnabled) {
      if (queuedExecutionSummaryResult == null) {
        return;
      }
      savePlanExecutionSummary(result, metadata, queuedExecutionSummaryResult, planExecutionId,
          planExecutionMetadataWithContext, planExecution, scopeInfo, ambiance, planExecutionMetadata);
    } else {
      // will be called when plan execution starts in queue based plan creation as planExecutionSummary will already be
      // created when we create request for queue. Code for that will be in next pr
      updatePlanExecutionSummary(result, planExecution, planExecutionMetadata, ambiance, planExecutionId);
    }
  }

  @Override
  public void onQueue(OrchestrationQueueInfo orchestrationQueueInfo) {
    String planExecutionId = orchestrationQueueInfo.getPlanExecution().getUuid();
    PlanExecutionMetadata planExecutionMetadata =
        orchestrationQueueInfo.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    ExecutionMetadata metadata = orchestrationQueueInfo.getPlanExecution().getMetadata();
    String accountId =
        orchestrationQueueInfo.getPlanExecution().getSetupAbstractions().get(SetupAbstractionKeys.accountId);
    String projectId =
        orchestrationQueueInfo.getPlanExecution().getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier);
    String orgId =
        orchestrationQueueInfo.getPlanExecution().getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier);
    ScopeInfo scopeInfo = orchestrationQueueInfo.getScopeInfo();
    if (scopeInfo == null) {
      scopeInfo = ScopeInfo.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(orgId)
                      .projectIdentifier(projectId)
                      .uniqueId(orchestrationQueueInfo.getPlanExecution().getSetupAbstractions().get(
                          SetupAbstractionKeys.parentUniqueId))
                      .build();
    }

    QueuedExecutionSummaryResult queuedExecutionSummaryResult = getQueuedExecutionSummaryResult(metadata,
        orchestrationQueueInfo.getPlanExecutionMetadataWithContext(), accountId, orgId, projectId, scopeInfo, true,
        planExecutionId, planExecutionMetadata, orchestrationQueueInfo.getPlanExecution());
    if (queuedExecutionSummaryResult == null) {
      return;
    }
    Ambiance ambiance = orchestrationQueueInfo.getPlanExecution().getAmbiance() != null
        ? orchestrationQueueInfo.getPlanExecution().getAmbiance()
        : Ambiance.newBuilder().build();
    savePlanExecutionSummary(null, metadata, queuedExecutionSummaryResult, planExecutionId,
        orchestrationQueueInfo.getPlanExecutionMetadataWithContext(), orchestrationQueueInfo.getPlanExecution(),
        scopeInfo, ambiance, planExecutionMetadata);
  }

  @Override
  public void onDynamicStart(DynamicOrchestrationStartInfo dynamicOrchestrationStartInfo) {
    Ambiance ambiance = dynamicOrchestrationStartInfo.getAmbiance();
    Plan plan = dynamicOrchestrationStartInfo.getPlan();
    Update update = new Update();
    if (plan.getGraphLayoutInfo() != null) {
      for (Map.Entry<String, GraphLayoutNode> entry : plan.getGraphLayoutInfo().getLayoutNodesMap().entrySet()) {
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + entry.getKey(),
            GraphLayoutDtoMapper.toDto(entry.getValue()));
      }
    }
    pmsExecutionSummaryService.update(ambiance.getPlanExecutionId(), update);
  }

  private void savePlanExecutionSummary(UpdateExecutionSummaryResult result, ExecutionMetadata metadata,
      QueuedExecutionSummaryResult queuedExecutionSummaryResult, String planExecutionId,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, PlanExecution planExecution,
      ScopeInfo scopeInfo, Ambiance ambiance, PlanExecutionMetadata planExecutionMetadata) {
    PipelineExecutionSummaryEntityBuilder executionSummaryEntityBuilder =
        PipelineExecutionSummaryEntity.builder()
            .runSequence(metadata.getRunSequence())
            .pipelineIdentifier(queuedExecutionSummaryResult.pipelineId())
            .planExecutionId(planExecutionId)
            .name(queuedExecutionSummaryResult.pipelineEntity().get().getName())
            .pipelineTemplate(
                HarnessYamlVersion.isV1(queuedExecutionSummaryResult.pipelineEntity().get().getHarnessVersion())
                    ? null
                    : getPipelineTemplate(planExecutionMetadataWithContext))
            .internalStatus(planExecution.getStatus())
            .status(ExecutionStatus.getExecutionStatus(planExecution.getStatus()))
            .startTs(planExecution.getStartTs())
            .accountId(scopeInfo.getAccountIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .parentUniqueId(getParentUniqueId(scopeInfo, ambiance))
            .executionTriggerInfo(metadata.getTriggerInfo())
            .parentStageInfo(metadata.getPipelineStageInfo())
            .entityGitDetails(pmsGitSyncHelper.getEntityGitDetailsFromBytes(metadata.getGitSyncBranchContext()))
            .tags(getNgTags(queuedExecutionSummaryResult, planExecutionMetadataWithContext))
            .labels(LabelsHelper.getLabels(planExecutionMetadata.getYaml(),
                queuedExecutionSummaryResult.pipelineEntity().get().getHarnessVersion()))
            .retryExecutionMetadata(planExecutionId.equals(queuedExecutionSummaryResult.rootExecutionId())
                    ? null
                    : queuedExecutionSummaryResult.retryExecutionMetadata())
            .isLatestExecution(true)
            .notesExistForPlanExecutionId(isNotEmpty(planExecutionMetadata.getNotes()))
            .notes(planExecutionMetadata.getNotes())
            .allowStagesExecution(planExecutionMetadata.isStagesExecutionAllowed())
            .stagesExecutionMetadata(planExecutionMetadataWithContext.getStagesExecutionMetadata())
            .storeType(StoreTypeMapper.fromPipelineStoreType(metadata.getPipelineStoreType()))
            .connectorRef(isEmpty(metadata.getPipelineConnectorRef()) ? null : metadata.getPipelineConnectorRef())
            .executionMode(metadata.getExecutionMode())
            .pipelineVersion(planExecutionMetadata.getHarnessVersion())
            .isDynamicExecution(planExecutionMetadataWithContext.getIsDynamicExecution())
            .isOriginalYamlUsedOnRerun(planExecutionMetadataWithContext.getIsOriginalYamlUsedOnRerun())
            .priorityType(planExecution.getPriorityType())
            .modules(new ArrayList<>())
            .inputSetIdentifiers(planExecutionMetadataWithContext.getInputSetIdentifiers() != null
                    ? planExecutionMetadataWithContext.getInputSetIdentifiers()
                    : new ArrayList<>())
            .inputSetBranchName(planExecutionMetadataWithContext.getInputSetBranchName());
    if (result != null) {
      // adding this null check as in next pr we will use this method where updateResult wont be present and will be
      // null so we will use this same method
      executionSummaryEntityBuilder.layoutNodeMap(result.layoutNodeDTOMap());
      executionSummaryEntityBuilder.startingNodeId(result.startingNodeId());
      executionSummaryEntityBuilder.startingNodeIds(result.startingNodeIds());
      executionSummaryEntityBuilder.isDagEnabled(result.isDagEnabled());
      executionSummaryEntityBuilder.dependencyGraph(result.dependencyGraph());
      executionSummaryEntityBuilder.modules(new ArrayList<>(result.modules()));
      executionSummaryEntityBuilder.governanceMetadata(planExecution.getGovernanceMetadata());
      executionSummaryEntityBuilder.executionInputConfigured(planExecutionMetadata.getExecutionInputConfigured());
      executionSummaryEntityBuilder.shouldUseSimplifiedLogBaseKey(AmbianceUtils.shouldSimplifyLogBaseKey(ambiance));
    }
    executionSummaryEntityBuilder.cdcGraphEnabled(
        pmsFeatureFlagService.isEnabled(scopeInfo.getAccountIdentifier(), FeatureName.PIPE_USE_CDC_BASED_GRAPH));
    storeTemplateReferenceSummary(ambiance, planExecutionMetadata, executionSummaryEntityBuilder, planExecutionId);
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionSummaryService.save(executionSummaryEntityBuilder.build());

    saveExecutionGitMetadata(executionSummaryEntity, scopeInfo);
  }

  private static void storeTemplateReferenceSummary(Ambiance ambiance, PlanExecutionMetadata planExecutionMetadata,
      PipelineExecutionSummaryEntityBuilder executionSummaryEntityBuilder, String planExecutionId) {
    boolean storeTemplateReferenceFFEnabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name());
    boolean isV1 = HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance));
    if (storeTemplateReferenceFFEnabled || isV1) {
      try {
        TemplateReferenceSummary templateReferenceSummary = PlanCreatorUtils.extractTemplateInfoFromYaml(
            YamlUtils.extractPipelineField(
                isV1 ? planExecutionMetadata.getUnifiedYaml() : planExecutionMetadata.getYaml()),
            AmbianceUtils.getPipelineVersion(ambiance));
        if (templateReferenceSummary != null) {
          executionSummaryEntityBuilder.templateReferenceSummary(templateReferenceSummary);
        }
      } catch (IOException e) {
        log.error("[STORE_TEMPLATE_REFERENCE]: Failed to extract template reference for planExecutionId: {}",
            planExecutionId, e);
      }
    }
  }

  private void updatePlanExecutionSummary(UpdateExecutionSummaryResult result, PlanExecution planExecution,
      PlanExecutionMetadata planExecutionMetadata, Ambiance ambiance, String planExecutionId) {
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.layoutNodeMap, result.layoutNodeDTOMap());
    update.set(PlanExecutionSummaryKeys.startingNodeId, result.startingNodeId());
    update.set(PlanExecutionSummaryKeys.startingNodeIds, result.startingNodeIds());
    update.set(PlanExecutionSummaryKeys.isDagEnabled, result.isDagEnabled());
    update.set(PlanExecutionSummaryKeys.dependencyGraph, result.dependencyGraph());
    update.set(PlanExecutionSummaryKeys.internalStatus, planExecution.getStatus());
    update.set(PlanExecutionSummaryKeys.status, ExecutionStatus.getExecutionStatus(planExecution.getStatus()));
    update.set(PlanExecutionSummaryKeys.modules, result.modules());
    update.set(PlanExecutionSummaryKeys.notesExistForPlanExecutionId, isNotEmpty(planExecutionMetadata.getNotes()));
    update.set(PlanExecutionSummaryKeys.notes, planExecutionMetadata.getNotes());
    update.set(PlanExecutionSummaryKeys.governanceMetadata, planExecution.getGovernanceMetadata());
    update.set(PlanExecutionSummaryKeys.executionInputConfigured, planExecutionMetadata.getExecutionInputConfigured());
    update.set(
        PlanExecutionSummaryKeys.shouldUseSimplifiedLogBaseKey, AmbianceUtils.shouldSimplifyLogBaseKey(ambiance));
    pmsExecutionSummaryService.update(planExecutionId, update);
  }

  private QueuedExecutionSummaryResult getQueuedExecutionSummaryResult(ExecutionMetadata metadata,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, String accountId, String orgId,
      String projectId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, String planExecutionId,
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    String pipelineId = metadata.getPipelineIdentifier();
    boolean getMetadataOnly = true;

    // pipelineYaml in planExecutionMetadata is a transient field and not stored in db. It is passed by caller and can
    // be null in some cases like in case of Pipeline Rollback. In such cases, we will get the pipeline yaml from
    // pipeline entity
    if (isEmpty(planExecutionMetadataWithContext.getPipelineYaml())) {
      getMetadataOnly = false;
    }
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId,
        false, getMetadataOnly, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntity.isEmpty()) {
      return null;
    }
    if (isEmpty(planExecutionMetadataWithContext.getPipelineYaml())) {
      planExecutionMetadataWithContext.setPipelineYaml(pipelineEntity.get().getYaml());
    }
    // RetryInfo
    String rootExecutionId = planExecutionId;
    String parentExecutionId = planExecutionId;
    RetryExecutionMetadata retryExecutionMetadata = null;
    if (planExecutionMetadata.getRetryExecutionInfo() != null
        && planExecutionMetadata.getRetryExecutionInfo().getIsRetry()) {
      rootExecutionId = planExecutionMetadata.getRetryExecutionInfo().getRootExecutionId();
      parentExecutionId = planExecutionMetadata.getRetryExecutionInfo().getParentRetryId();
      PipelineExecutionSummaryEntity parentPipelineExecutionSummaryEntity =
          pmsExecutionService.getPipelineExecutionSummaryEntity(scopeInfo.getAccountIdentifier(), parentExecutionId);
      retryExecutionMetadata = RetryExecutionMetadata.builder()
                                   .startTs(parentPipelineExecutionSummaryEntity.getStartTs())
                                   .endTs(parentPipelineExecutionSummaryEntity.getEndTs())
                                   .executedBy(parentPipelineExecutionSummaryEntity.getExecutionTriggerInfo())
                                   .runSequence(parentPipelineExecutionSummaryEntity.getRunSequence())
                                   .parentExecutionId(parentExecutionId)
                                   .rootExecutionId(rootExecutionId)
                                   .build();

      // updating isLatest and canRetry
      Update update = new Update();
      update.set(PlanExecutionSummaryKeys.isLatestExecution, false);
      pmsExecutionSummaryService.update(parentExecutionId, update);
    } else {
      // remove after next release
      if (metadata.hasRetryInfo() && metadata.getRetryInfo().getIsRetry()) {
        rootExecutionId = metadata.getRetryInfo().getRootExecutionId();
        parentExecutionId = metadata.getRetryInfo().getParentRetryId();

        // updating isLatest and canRetry
        Update update = new Update();
        update.set(PlanExecutionSummaryKeys.isLatestExecution, false);
        pmsExecutionSummaryService.update(parentExecutionId, update);
      }
    }

    recentExecutionsInfoHelper.onExecutionStart(scopeInfo, pipelineId, planExecution, isParentIdQueryingEnabled);

    updateExecutionInfoInPipelineEntity(scopeInfo, pipelineId, pipelineEntity.get().getExecutionSummaryInfo(),
        planExecutionId, isParentIdQueryingEnabled);
    return new QueuedExecutionSummaryResult(pipelineId, pipelineEntity, rootExecutionId, retryExecutionMetadata);
  }

  private UpdateExecutionSummaryResult getResult(
      Ambiance ambiance, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    Plan plan = planService.fetchPlan(ambiance.getPlanId());
    GraphLayoutInfo graphLayoutInfo = plan.getGraphLayoutInfo();
    Map<String, GraphLayoutNode> layoutNodeMap = new HashMap<>(graphLayoutInfo.getLayoutNodesMap());
    String startingNodeId = graphLayoutInfo.getStartingNodeId();
    List<String> startingNodeIds = new ArrayList<>(graphLayoutInfo.getStartingNodeIdsList());
    boolean isDagEnabled = graphLayoutInfo.getIsDagEnabled();
    Map<String, List<String>> dependencyGraph = null;
    if (graphLayoutInfo.hasDependencyGraph()) {
      dependencyGraph = DependencyUtils.convertDependencyGraphToMap(graphLayoutInfo.getDependencyGraph());
    }

    if (ExecutionModeUtils.isPostExecutionRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      String rollbackTargetStageId =
          planExecutionMetadataWithContext.getPostExecutionRollbackInfos().get(0).getPostExecutionRollbackStageId();

      String accountId = AmbianceUtils.getAccountId(ambiance);
      boolean enableDAG = ambiance.getMetadata().getEnableDAG();
      boolean isDagPostExecutionRollbackActive = RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
          accountId, enableDAG, isDagEnabled, dependencyGraph, pmsFeatureFlagService);

      if (!isDagPostExecutionRollbackActive) {
        // Sequential post-prod rollback: trim layout to the rollback target stage subtree only.
        startingNodeId = rollbackTargetStageId;
        startingNodeIds = Collections.singletonList(startingNodeId);
        dependencyGraph = null;
        GraphLayoutNode layoutNode = layoutNodeMap.get(startingNodeId);

        Map<String, GraphLayoutNode> modifiedLayoutNodeMap = new HashMap<>();
        modifiedLayoutNodeMap.put(startingNodeId,
            layoutNode.toBuilder()
                .setEdgeLayoutList(
                    EdgeLayoutList.newBuilder()
                        .addAllCurrentNodeChildren(layoutNode.getEdgeLayoutList().getCurrentNodeChildrenList())
                        .build())
                .build());
        for (String childrenNode : layoutNode.getEdgeLayoutList().getCurrentNodeChildrenList()) {
          GraphLayoutNode childrenLayoutNode = layoutNodeMap.get(childrenNode);
          modifiedLayoutNodeMap.put(childrenNode, childrenLayoutNode);
        }
        layoutNodeMap = modifiedLayoutNodeMap;
      } else {
        // DAG post-prod rollback: focused rollback run — show only the rollback target and its internal
        // step/rollback/strategy subgraph (same UX as sequential), not upstream DAG stages or sibling stages.
        isDagEnabled = false;
        String layoutFocusNodeId = layoutNodeMap.containsKey(rollbackTargetStageId)
            ? rollbackTargetStageId
            : graphLayoutInfo.getStartingNodeId();
        startingNodeId = layoutFocusNodeId;
        startingNodeIds = Collections.singletonList(startingNodeId);
        dependencyGraph = null;
        layoutNodeMap = DependencyUtils.pruneLayoutNodeMapForSubgraph(
            layoutNodeMap, Collections.singletonList(layoutFocusNodeId), false);
      }
    }
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    Set<String> modules = new LinkedHashSet<>();
    for (Map.Entry<String, GraphLayoutNode> entry : layoutNodeMap.entrySet()) {
      GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(entry.getValue());
      if (INTERNAL_NODE_TYPES.contains(entry.getValue().getNodeType())
          || Arrays.stream(StrategyType.values()).anyMatch(type -> type.name().equals(entry.getValue().getNodeType()))
          || Arrays.stream(StrategyTypeV1.values())
                 .anyMatch(type -> type.name().equals(entry.getValue().getNodeType()))) {
        layoutNodeDTOMap.put(entry.getKey(), graphLayoutNodeDTO);
        continue;
      }
      String moduleName = nodeTypeLookupService.findNodeTypeServiceName(entry.getValue().getNodeType());
      graphLayoutNodeDTO.setModule(moduleName);
      Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
      moduleInfo.put(moduleName, new LinkedHashMap<>());
      graphLayoutNodeDTO.setModuleInfo(moduleInfo);
      layoutNodeDTOMap.put(entry.getKey(), graphLayoutNodeDTO);
      modules.add(moduleName);
    }

    if (!isEmpty(plan.getStepModules())) {
      // order of the modules is preserved based on the priority of the services in config.yml
      modules.addAll(plan.getStepModules());
    }
    if (!isEmpty(modules) && modules.size() == 1 && modules.contains("pms")) {
      modules.add("common");
    }
    return new UpdateExecutionSummaryResult(
        startingNodeId, startingNodeIds, isDagEnabled, dependencyGraph, layoutNodeDTOMap, modules);
  }

  private String getPipelineTemplate(PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    StagesExecutionMetadata stagesExecutionMetadata = planExecutionMetadataWithContext.getStagesExecutionMetadata();
    Set<FeatureName> enabledFlags = new HashSet<>();
    if (pmsFeatureFlagService.isEnabled(
            planExecutionMetadataWithContext.getPlanExecutionMetadata().getAccountIdentifier(),
            PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY)) {
      enabledFlags.add(FeatureName.PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY);
    }
    if (stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution()) {
      return InputSetTemplateHelper.createTemplateFromWithDefaultValuesPipelineForGivenStages(
          planExecutionMetadataWithContext.getPipelineYaml(), stagesExecutionMetadata.getStageIdentifiers(),
          enabledFlags);
    }
    return InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipeline(
        planExecutionMetadataWithContext.getPipelineYaml(), enabledFlags);
  }

  private void updateExecutionInfoInPipelineEntity(ScopeInfo scopeInfo, String pipelineId,
      ExecutionSummaryInfo executionSummaryInfo, String planExecutionId, boolean isParentIdQueryingEnabled) {
    if (executionSummaryInfo == null) {
      executionSummaryInfo = ExecutionSummaryInfo.builder().build();
    }
    executionSummaryInfo.setLastExecutionStatus(ExecutionStatus.RUNNING);
    Map<String, Integer> deploymentsMap = executionSummaryInfo.getDeployments();
    Date todaysDate = new Date();
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
    String strDate = formatter.format(todaysDate);
    if (deploymentsMap.containsKey(strDate)) {
      deploymentsMap.put(strDate, deploymentsMap.get(strDate) + 1);
    } else {
      deploymentsMap.put(strDate, 1);
    }
    executionSummaryInfo.setDeployments(deploymentsMap);
    executionSummaryInfo.setLastExecutionTs(todaysDate.getTime());
    executionSummaryInfo.setLastExecutionId(planExecutionId);
    pmsPipelineService.saveExecutionInfo(scopeInfo, pipelineId, executionSummaryInfo, isParentIdQueryingEnabled);
  }

  protected void saveExecutionGitMetadata(PipelineExecutionSummaryEntity executionSummaryEntity, ScopeInfo scopeInfo) {
    if (executionSummaryEntity == null || executionSummaryEntity.getEntityGitDetails() == null
        || pmsFeatureFlagService.isEnabled(
            executionSummaryEntity.getAccountId(), DISABLE_PIPELINE_EXECUTION_GIT_METADATA_UPSERT)) {
      return;
    }

    String repoName = executionSummaryEntity.getEntityGitDetails().getRepoName();
    String branch = executionSummaryEntity.getEntityGitDetails().getBranch();
    if (repoName != null && branch != null) {
      executionGitMetadataService.upsert(scopeInfo, executionSummaryEntity.getPipelineIdentifier(), repoName, branch);
    }
  }

  private String getParentUniqueId(ScopeInfo scopeInfo, Ambiance ambiance) {
    if (scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      return scopeInfo.getUniqueId();
    } else if (ambiance != null && isNotEmpty(AmbianceUtils.getParentUniqueIdentifier(ambiance))) {
      return AmbianceUtils.getParentUniqueIdentifier(ambiance);
    } else {
      return null;
    }
  }

  @NotNull
  private static List<NGTag> getNgTags(QueuedExecutionSummaryResult queuedExecutionSummaryResult,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    List<NGTag> tagsToUse;
    tagsToUse = planExecutionMetadataWithContext.getTags() != null ? planExecutionMetadataWithContext.getTags()
                                                                   : new ArrayList<>();

    if (isEmpty(tagsToUse)) {
      tagsToUse = queuedExecutionSummaryResult.pipelineEntity().get().getTags() != null
          ? queuedExecutionSummaryResult.pipelineEntity().get().getTags()
          : new ArrayList<>();
    }
    return tagsToUse;
  }
}
