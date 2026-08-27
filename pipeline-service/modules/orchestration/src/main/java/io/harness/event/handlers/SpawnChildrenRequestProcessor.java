/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.BarrierExpandObserver;
import io.harness.engine.observers.BarrierExpandRequest;
import io.harness.engine.pms.resume.callback.resume.EngineResumeCallback;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.helpers.ChildrenStartRequestBatch;
import io.harness.execution.helpers.InitiateNodeHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.logging.AutoLogContext;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SpawnChildrenRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InitiateNodeHelper initiateNodeHelper;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject @Getter private final Subject<BarrierExpandObserver> barrierWithinStrategyExpander = new Subject<>();
  @Inject FeatureFlagService featureFlagService;
  @Inject @Named("InitiateNodeRequestBatchSize") Integer initiateNodeRequestBatchSize;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    SpawnChildrenRequest request = event.getSpawnChildrenRequest();
    Ambiance ambiance = event.getAmbiance();
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    String nodeSetupId = Objects.requireNonNull(AmbianceUtils.obtainCurrentSetupId(ambiance));
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      List<String> childrenIds = new ArrayList<>();
      List<String> callbackIds = new ArrayList<>();
      int currentChild = 0;
      for (int i = 0; i < request.getChildren().getChildrenList().size(); i++) {
        childrenIds.add(generateUuid());
      }
      int maxConcurrency = getMaxConcurrencyLimit(ambiance, childrenIds, request.getChildren().getMaxConcurrency());
      expandBarriersWithinStrategyNode(nodeExecutionId, nodeSetupId,
          request.getChildren().getChildrenList().stream().map(Child::getChildNodeId).collect(Collectors.toList()),
          childrenIds, ambiance, maxConcurrency);

      List<Child> filteredChildren = getFilteredChildren(ambiance, request.getChildren().getChildrenList());
      if (childrenIds.isEmpty() || filteredChildren.isEmpty()) {
        // If callbackIds are empty then it means that there are no children, we should just do a no-op and return to
        // parent.
        orchestrationEngine.resumeNodeExecution(ambiance, new HashMap<>(), false);
        return;
      }

      if (featureFlagService.isEnabled(
              FeatureName.PIPE_BATCHING_IN_SPAWN_CHILDREN_REQUEST_PROCESSING, AmbianceUtils.getAccountId(ambiance))) {
        List<ChildrenStartRequestBatch> startRequestBatches = getBatches(filteredChildren, childrenIds);
        for (ChildrenStartRequestBatch batch : startRequestBatches) {
          callbackIds.addAll(batch.getChildren().stream().map(InitiateNodeBatchEvent.Child::getRuntimeId).toList());
        }
      } else {
        for (int i = 0; i < filteredChildren.size(); i++) {
          callbackIds.add(childrenIds.get(i));
        }
      }

      // Save the ConcurrentChildInstance in db first so that whenever callback is called, this information is readily
      // available. If not done here, it could lead to race conditions
      nodeExecutionInfoService.addConcurrentChildInformation(
          ConcurrentChildInstance.builder().childrenNodeExecutionIds(callbackIds).cursor(maxConcurrency).build(),
          nodeExecutionId);

      if (featureFlagService.isEnabled(
              FeatureName.PIPE_BATCHING_IN_SPAWN_CHILDREN_REQUEST_PROCESSING, AmbianceUtils.getAccountId(ambiance))) {
        boolean isCallbackRequired = filteredChildren.size() > maxConcurrency;

        List<ChildrenStartRequestBatch> startRequestBatches = getBatches(filteredChildren, childrenIds);
        for (int index = 0; index < startRequestBatches.size(); index++) {
          ChildrenStartRequestBatch batch = startRequestBatches.get(index);

          // If below condition is true means the current batch can be started immediately.
          if (maxConcurrency >= index * initiateNodeRequestBatchSize + batch.getChildren().size()) {
            initiateNodeHelper.publishEventBatch(ambiance, batch, InitiateMode.CREATE_AND_START, isCallbackRequired,
                request.getChildren().getShouldProceedIfFailed(), maxConcurrency);
          } else if (maxConcurrency > index * initiateNodeRequestBatchSize) {
            // It means few nodes of this batch can be started and rest would only be created.
            int toBeStartedCount = maxConcurrency - index * initiateNodeRequestBatchSize;
            ChildrenStartRequestBatch partialBatchToStart =
                ChildrenStartRequestBatch.builder()
                    .uuid(batch.getUuid())
                    .children(batch.getChildren().subList(0, toBeStartedCount))
                    .build();
            initiateNodeHelper.publishEventBatch(ambiance, partialBatchToStart, InitiateMode.CREATE_AND_START,
                isCallbackRequired, request.getChildren().getShouldProceedIfFailed(), maxConcurrency);

            // Create the nodes for the rest of the batch.
            ChildrenStartRequestBatch partialBatchToCreate =
                ChildrenStartRequestBatch.builder()
                    .uuid(batch.getUuid())
                    .children(batch.getChildren().subList(toBeStartedCount, batch.getChildren().size()))
                    .build();
            initiateNodeHelper.publishEventBatch(ambiance, partialBatchToCreate, InitiateMode.CREATE,
                isCallbackRequired, request.getChildren().getShouldProceedIfFailed(), maxConcurrency);
          } else {
            // TODO: Do not create here. Do via callback. Only running nodes should be created. Rest would be created
            // only when previous ones finish
            initiateNodeHelper.publishEventBatch(ambiance, batch, InitiateMode.CREATE, isCallbackRequired,
                request.getChildren().getShouldProceedIfFailed(), maxConcurrency);
          }
        }
      } else {
        List<Ambiance> ambianceList = new ArrayList<>();
        for (Child child : filteredChildren) {
          String uuid = childrenIds.get(currentChild);
          StrategyMetadata strategyMetadata = child.hasStrategyMetadata() ? child.getStrategyMetadata() : null;
          NodeExecution nodeExecution = orchestrationEngine.initiateNode(
              ambiance, child.getChildNodeId(), uuid, null, strategyMetadata, InitiateMode.CREATE);
          if (shouldCreateAndStart(maxConcurrency, currentChild)) {
            if (nodeExecution != null) {
              ambianceList.add(nodeExecutionService.getAmbiance(nodeExecution));
            }
          }
          // We should register MaxConcurrentChildCallback only when we will use max concurrency.
          // If there is no need to have concurrency, we should avoid adding callbacks.
          if (filteredChildren.size() > maxConcurrency) {
            MaxConcurrentChildCallback maxConcurrentChildCallback =
                MaxConcurrentChildCallback.builder()
                    .parentNodeExecutionId(nodeExecutionId)
                    .planExecutionId(ambiance.getPlanExecutionId())
                    .maxConcurrency(maxConcurrency)
                    .proceedIfFailed(request.getChildren().getShouldProceedIfFailed())
                    .build();

            String waitInstanceId = waitNotifyEngine.waitForAllOn(publisherName, maxConcurrentChildCallback, uuid);
            log.info(
                "SpawnChildrenRequestProcessor registered a waitInstance for maxConcurrency with waitInstanceId: {}",
                waitInstanceId);
          }
          currentChild++;
        }

        for (Ambiance ambianceentry : ambianceList) {
          initiateNodeHelper.publishEvent(ambianceentry, InitiateMode.START);
        }
      }

      if (callbackIds.isEmpty()) {
        orchestrationEngine.resumeNodeExecution(ambiance, new HashMap<>(), false);
        return;
      }
      // TODO: To check if this needs to be skipped for DAG.
      // Attach a Callback to the parent for the child
      EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(ambiance).build();
      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(publisherName, callback, callbackIds.toArray(new String[0]));
      log.info("SpawnChildrenRequestProcessor registered a waitInstance with id: {}", waitInstanceId);

      // Update the parent with executable response. With updated max concurrency by applying the pipeline-side limits.
      // Store filteredChildren (the subset actually spawned) rather than the full children list from the request, so
      // that post-prod rollback matrix metadata reflects only the combinations that were executed.
      nodeExecutionService.updateV2(nodeExecutionId,
          ops
          -> ops.addToSet(NodeExecutionKeys.executableResponses,
              ExecutableResponse.newBuilder()
                  .setChildren(request.getChildren()
                                   .toBuilder()
                                   .clearChildren()
                                   .addAllChildren(filteredChildren)
                                   .setMaxConcurrency(maxConcurrency)
                                   .build())
                  .build()));
    }
  }

  /**
   * Get max concurrency limit based on plan and pipeline setting.
   * If maxConcurrency provided is less than the limit, we use the max concurrency provided by the user.
   */
  private int getMaxConcurrencyLimit(Ambiance ambiance, List<String> childrenIds, long requestMaxConcurrency) {
    int maxConcurrencyLimit = pipelineSettingsService.getMaxConcurrencyBasedOnEdition(
        AmbianceUtils.getAccountId(ambiance), childrenIds.size());
    int maxConcurrency = maxConcurrencyLimit;
    if (requestMaxConcurrency > 0 && requestMaxConcurrency < maxConcurrencyLimit) {
      maxConcurrency = (int) requestMaxConcurrency;
    }
    return maxConcurrency;
  }

  /**
   * This filters the children provided by strategy node.
   *
   * Filtering is required mainly for post prod rollback because
   *  - We need to run only one combination in matrix which deployed that service.
   *  - If the service being rolled back is not inside matrix, then we want to do a
   *  no-op
   */
  @VisibleForTesting
  List<Child> getFilteredChildren(Ambiance ambiance, List<Child> children) {
    // If the FF is enabled then we are filtering the children for PostExecutionRollback in the IdentityStrategyStep
    // itself. This filter is only for PostExecutionRollback. So If FF enabled then this method will be no-op. And will
    // be removed after GA of the FF.
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())) {
      return children;
    }
    // Calculating children only when strategy is at stage level - AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance)
    if (ambiance.getMetadata().getExecutionMode() == ExecutionMode.POST_EXECUTION_ROLLBACK
        && AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance)) {
      List<PostExecutionRollbackInfo> postExecutionRollbackInfos = getPostExecutionRollbackInfo(ambiance);
      Multimap<String, StrategyMetadata> strategyMetadataMap = HashMultimap.create();
      postExecutionRollbackInfos.forEach(
          o -> strategyMetadataMap.put(o.getPostExecutionRollbackStageId(), o.getRollbackStageStrategyMetadata()));
      String parentNodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);
      List<Child> filteredChild = new LinkedList<>();
      // If the parentNodeId is present in the list of stages being rolledBack. Then initiate  we will select the first
      // child and replace the strategyMetaData of the child with the strategyMetadata in postExecutionRollbackInfo
      if (AmbianceUtils.getCurrentStepType(ambiance).getStepCategory() == StepCategory.STRATEGY) {
        if (strategyMetadataMap.containsKey(parentNodeId)) {
          Collection<StrategyMetadata> strategyMetadataList = strategyMetadataMap.get(parentNodeId);
          int count = 0;
          for (StrategyMetadata strategyMetadata : strategyMetadataList) {
            filteredChild.add(Child.newBuilder()
                                  .setChildNodeId(children.get(count).getChildNodeId())
                                  .setStrategyMetadata(strategyMetadata)
                                  .build());
            count++;
            if (count == children.size()) {
              break;
            }
          }
        }
        return filteredChild;
      } else {
        return children;
      }
    }
    return children;
  }

  private List<PostExecutionRollbackInfo> getPostExecutionRollbackInfo(Ambiance ambiance) {
    PlanExecutionMetadata planExecutionMetadata =
        planExecutionMetadataService.getWithFieldsIncludedFromSecondary(AmbianceUtils.getAccountId(ambiance),
            ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    PlanExecution planExecution = null;
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    return PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
        planExecutionMetadata, planExecution);
  }

  private boolean shouldCreateAndStart(int maxConcurrency, int currentChild) {
    return currentChild < maxConcurrency;
  }

  private void expandBarriersWithinStrategyNode(String strategyExecutionId, String strategySetupId,
      List<String> childrenSetupIds, List<String> childrenRuntimeIds, Ambiance ambiance, int maxConcurrency) {
    boolean isChildPipeline = ambiance.getMetadata().hasPipelineStageInfo()
        && ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline();
    String parentPlanExecutionId = null;
    if (isChildPipeline) {
      parentPlanExecutionId = ambiance.getMetadata().getPipelineStageInfo().getExecutionId();
    }

    BarrierExpandRequest barrierExpandRequest = BarrierExpandRequest.builder()
                                                    .strategyExecutionId(strategyExecutionId)
                                                    .strategySetupId(strategySetupId)
                                                    .childrenSetupIds(childrenSetupIds)
                                                    .childrenRuntimeIds(childrenRuntimeIds)
                                                    .stageExecutionId(ambiance.getStageExecutionId())
                                                    .planExecutionId(ambiance.getPlanExecutionId())
                                                    .maxConcurrency(maxConcurrency)
                                                    .parentPlanExecutionId(parentPlanExecutionId)
                                                    .build();

    barrierWithinStrategyExpander.fireInform(BarrierExpandObserver::onInitializeRequest, barrierExpandRequest);
  }

  @VisibleForTesting
  protected List<ChildrenStartRequestBatch> getBatches(List<Child> children, List<String> runtimeIds) {
    List<ChildrenStartRequestBatch> batches = new ArrayList<>();
    for (int i = 0; i < children.size(); i += initiateNodeRequestBatchSize) {
      int end = Math.min(i + initiateNodeRequestBatchSize, children.size());
      List<Child> newBatch = children.subList(i, end);
      List<InitiateNodeBatchEvent.Child> initiateNodeBatchEvents = new ArrayList<>();
      for (int index = 0; index < end - i; index++) {
        InitiateNodeBatchEvent.Child.Builder childBuilder = InitiateNodeBatchEvent.Child.newBuilder()
                                                                .setSetupId(newBatch.get(index).getChildNodeId())
                                                                .setRuntimeId(runtimeIds.get(index + i));
        if (newBatch.get(index).hasStrategyMetadata()) {
          childBuilder.setStrategyMetadata(newBatch.get(index).getStrategyMetadata());
        }
        initiateNodeBatchEvents.add(childBuilder.build());
      }
      batches.add(ChildrenStartRequestBatch.builder()
                      .uuid(UUIDGenerator.generateUuid())
                      .children(initiateNodeBatchEvents)
                      .build());
    }
    return batches;
  }
}
