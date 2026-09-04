/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import static io.harness.beans.FeatureName.PIPE_CACHE_CURRENT_STATUS;
import static io.harness.plancreator.strategy.StrategyConstants.IDENTIFIER_POSTFIX;
import static io.harness.plancreator.strategy.StrategyConstants.ITEM;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATIONS;
import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;
import static io.harness.plancreator.strategy.StrategyConstants.PARTITION;
import static io.harness.plancreator.strategy.StrategyConstants.REPEAT;
import static io.harness.plancreator.strategy.StrategyConstants.TOTAL_ITERATIONS;
import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoBuilder;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.RetryNodeMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.observer.Subject;
import io.harness.plancreator.strategy.IterationVariables;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.LevelUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.bson.types.Binary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NodeExecutionInfoServiceImpl implements NodeExecutionInfoService {
  private static final String EXECUTION_START_PREFIX = "EXECUTION_START_CALLBACK_%s";
  private static final int DEFAULT_BATCH_SIZE = 100;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject NodeExecutionsInfoRepository nodeExecutionsInfoRepository;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject @Getter private final Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject = new Subject<>();
  @Inject KryoSerializer kryoSerializer;
  @Inject PersistentLocker persistentLocker;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;

  @Override
  public void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name) {
    Update update = new Update().addToSet(NodeExecutionsInfoKeys.nodeExecutionDetailsInfoList,
        NodeExecutionDetailsInfo.builder().name(name).stepDetails(stepDetails).build());
    update.set(NodeExecutionsInfoKeys.lastUpdatedAt, System.currentTimeMillis());
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    NodeExecutionsInfo modifiedInfo =
        mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
    publishStepDetailsUpdate(
        modifiedInfo != null ? modifiedInfo.getAccountIdentifier() : "", planExecutionId, nodeExecutionId);
  }

  // TODO: Make this better this should be called from no where else
  @Override
  public void saveNodeExecutionInfo(
      String nodeExecutionId, String planExecutionId, StrategyMetadata metadata, String accountIdentifier) {
    NodeExecutionsInfoBuilder nodeExecutionsInfoBuilder = NodeExecutionsInfo.builder()
                                                              .nodeExecutionId(nodeExecutionId)
                                                              .planExecutionId(planExecutionId)
                                                              .accountIdentifier(accountIdentifier)
                                                              .currentStatus(Status.SUCCEEDED);
    if (metadata == null) {
      nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
      return;
    }
    nodeExecutionsInfoBuilder.strategyMetadata(metadata);
    nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
  }

  @Override
  public void saveNodeExecutionInfo(List<NodeExecution> nodeExecutions, List<StrategyMetadata> metadataList) {
    if (nodeExecutions.size() != metadataList.size()) {
      throw new InvalidRequestException(
          "Batch NodeExecutionInfo request faild because nodeExecution and metadata count is different");
    }
    List<NodeExecutionsInfo> nodeExecutionsInfos = new ArrayList<>();
    for (int index = 0; index < nodeExecutions.size(); index++) {
      NodeExecution nodeExecution = nodeExecutions.get(index);

      NodeExecutionsInfoBuilder nodeExecutionsInfoBuilder = NodeExecutionsInfo.builder()
                                                                .nodeExecutionId(nodeExecution.getUuid())
                                                                .planExecutionId(nodeExecution.getPlanExecutionId())
                                                                .accountIdentifier(nodeExecution.getAccountId())
                                                                .currentStatus(Status.SUCCEEDED);

      StrategyMetadata metadata = metadataList.get(index);
      if (metadata != null) {
        nodeExecutionsInfoBuilder.strategyMetadata(metadata);
      }
      nodeExecutionsInfos.add(nodeExecutionsInfoBuilder.build());
    }
    nodeExecutionsInfoRepository.saveAll(nodeExecutionsInfos);
  }

  @Override
  public void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId) {
    // Delegate to the new method with null metadata and empty accountId for backward compatibility
    // This maintains existing behavior for callers that don't have metadata/accountId available
    addStepInputs(nodeExecutionId, resolvedInputs, planExecutionId, null, "");
  }

  @Override
  public void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId,
      StrategyMetadata strategyMetadata, String accountIdentifier) {
    addStepInputsInternal(nodeExecutionId, resolvedInputs, planExecutionId, strategyMetadata, accountIdentifier);
  }

  public boolean addStepInputsInternal(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId,
      StrategyMetadata strategyMetadata, String accountIdentifier) {
    long currentTime = System.currentTimeMillis();
    // TODO (Sahil) : This is a hack right now to serialize in binary as findAndModify is not honoring converter
    // for maps Find a better way to do this
    Update update = new Update().set(
        NodeExecutionsInfoKeys.resolvedInputs, new Binary(kryoSerializer.asDeflatedBytes(resolvedInputs)));
    update.set(NodeExecutionsInfoKeys.lastUpdatedAt, currentTime);

    // setOnInsert: Only set these fields if creating a new document (not on update)
    update.setOnInsert(NodeExecutionsInfoKeys.nodeExecutionId, nodeExecutionId);
    update.setOnInsert(NodeExecutionsInfoKeys.planExecutionId, planExecutionId);
    update.setOnInsert(NodeExecutionsInfoKeys.accountIdentifier, accountIdentifier);
    update.setOnInsert(NodeExecutionsInfoKeys.currentStatus, Status.SUCCEEDED);
    update.setOnInsert(NodeExecutionsInfoKeys.nodeExecutionDetailsInfoList, new ArrayList<>());
    update.setOnInsert(NodeExecutionsInfoKeys.validUntil,
        Date.from(OffsetDateTime.now().plusMonths(NodeExecutionsInfo.TTL_MONTHS).toInstant()));
    update.setOnInsert(NodeExecutionsInfoKeys.createdAt, currentTime);
    update.setOnInsert("_class", "nodeExecutionsInfo");
    if (strategyMetadata != null) {
      update.setOnInsert(NodeExecutionsInfoKeys.strategyMetadata, strategyMetadata);
    }

    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    UpdateResult result = mongoTemplate.upsert(new Query(criteria), update, NodeExecutionsInfo.class);

    // Check if this was an insert by upsertedId is null
    boolean wasInserted = result.getUpsertedId() != null;
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
        StepDetailsUpdateInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .planExecutionId(planExecutionId)
            .accountId(accountIdentifier)
            .build());

    return wasInserted;
  }

  @Override
  public PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId) {
    Query query = new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId));
    query.fields().include(NodeExecutionsInfoKeys.resolvedInputs);
    NodeExecutionsInfo nodeExecutionsInfo = mongoTemplate.findOne(query, NodeExecutionsInfo.class);

    if (nodeExecutionsInfo != null && nodeExecutionsInfo.getResolvedInputs() != null) {
      return nodeExecutionsInfo.getResolvedInputs();
    } else {
      log.warn("Could not find nodeExecutionsInfo with the given nodeExecutionId: " + nodeExecutionId);
      return new PmsStepParameters(new HashMap<>());
    }
  }

  @Override
  public PmsStepParameters getStepInputsRecasterPruned(String planExecutionId, String nodeExecutionId) {
    PmsStepParameters stepInputs = getStepInputs(planExecutionId, nodeExecutionId);
    return getStepInputsRecasterPruned(stepInputs);
  }

  @Override
  public PmsStepParameters getStepInputsRecasterPruned(PmsStepParameters stepInputs) {
    return PmsStepParameters.parse(RecastOrchestrationUtils.pruneRecasterAdditions(stepInputs));
  }

  @Override
  public NodeExecutionsInfo getNodeExecutionsInfo(String nodeExecutionId) {
    return nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId).orElse(null);
  }

  @Override
  public NodeExecutionsInfo getNodeExecutionsInfoWithProjections(String nodeExecutionId, Set<String> projections) {
    var query = new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId));
    query.fields().include(projections.toArray(new String[0]));
    return mongoTemplate.findOne(query, NodeExecutionsInfo.class);
  }

  @Override
  public Optional<Status> getCurrentStatus(String nodeExecutionId) {
    NodeExecutionsInfo info =
        getNodeExecutionsInfoWithProjections(nodeExecutionId, Set.of(NodeExecutionsInfoKeys.failedChildIdChain));
    return info == null                        ? Optional.empty()
        : info.getFailedChildIdChain() != null ? Optional.of(Status.FAILED)
                                               : Optional.of(Status.SUCCEEDED);
  }

  @Override
  public void updateCalculatedStatusForParentNodes(NodeExecution nodeExecution) {
    if (nodeExecution == null || !shouldUpdateOnStatus(nodeExecution.getStatus())) {
      return;
    }
    if (Boolean.TRUE.equals(nodeExecution.getOldRetry())) {
      return;
    }
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
    if (ambiance == null || !AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_CACHE_CURRENT_STATUS.name())) {
      return;
    }

    if (!nodeExecution.getAdvisorsProcessed()) {
      return;
    }

    if (shouldSkipUpdateOnAdviserResponse(nodeExecution.getAdviserResponse())) {
      return;
    }

    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    String runtimeChain = AmbianceUtils.buildRuntimeIdChain(ambiance.getLevelsList());
    // Update only for leaf-node or strategy-node(since all children inside the strategy are not updating it)
    if (stageLevel.isPresent() && !AmbianceUtils.isCurrentLevelStage(ambiance)) {
      if (nodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY
          && AmbianceUtils.doesStrategyExistUnderStage(ambiance)) {
        return;
      }
      // TODO: Handle the nested looping-strategy.
      if (ExecutionModeUtils.isLeafMode(nodeExecution.getMode())
          || nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        updateCalculatedStatusField(stageLevel.get().getRuntimeId(), nodeExecution.getStatus(), runtimeChain);
      }
    } else {
      if (nodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY
          && AmbianceUtils.isCurrentNodeUnderStageStrategy(ambiance)) {
        return;
      }
      Optional<Level> pipelineLevel = AmbianceUtils.getPipelineLevelFromAmbiance(ambiance);
      if (pipelineLevel.isPresent()
          && (AmbianceUtils.isCurrentLevelStage(ambiance)
              || nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY)) {
        updateCalculatedStatusField(pipelineLevel.get().getRuntimeId(), nodeExecution.getStatus(), runtimeChain);
      }
    }
  }

  private boolean shouldUpdateOnStatus(Status status) {
    return StatusUtils.isFinalStatus(status) && Status.SUCCEEDED != status && Status.SKIPPED != status
        && Status.IGNORE_FAILED != status && Status.PASSED_WITH_WARNING != status;
  }

  private boolean shouldSkipUpdateOnAdviserResponse(AdviserResponse adviserResponse) {
    return adviserResponse != null
        && (adviserResponse.getType() == AdviseType.IGNORE_FAILURE
            || adviserResponse.getType() == AdviseType.MARK_SUCCESS
            || adviserResponse.getType() == AdviseType.INTERVENTION_WAIT);
  }

  private void updateCalculatedStatusField(
      String stageExecutionId, Status childStatus, String candidateRuntimeIdChain) {
    if (childStatus == null) {
      return;
    }

    Optional<NodeExecutionsInfo> nodeExecutionsInfoOptional = fetchCalculatedStatus(
        stageExecutionId, Set.of(NodeExecutionsInfoKeys.currentStatus, NodeExecutionsInfoKeys.failedChildIdChain));
    if (nodeExecutionsInfoOptional.isEmpty()) {
      return;
    }
    Status existingStatus =
        Optional.ofNullable(nodeExecutionsInfoOptional.get().getCurrentStatus()).orElse(Status.SUCCEEDED);
    Status newStatus = StatusUtils.calculateStatusForNode(List.of(existingStatus, childStatus), stageExecutionId);
    String previousFailedNodeId =
        Optional.ofNullable(nodeExecutionsInfoOptional.get().getFailedChildIdChain()).orElse("");

    // it only save if previousFailedNodeId is empty
    if (!previousFailedNodeId.isEmpty()) {
      return;
    }

    // TODO: This might cause a race condition.
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(stageExecutionId);
    Update update = new Update()
                        .set(NodeExecutionsInfoKeys.currentStatus, newStatus)
                        .set(NodeExecutionsInfoKeys.failedChildIdChain, candidateRuntimeIdChain)
                        .set(NodeExecutionsInfoKeys.lastUpdatedAt, System.currentTimeMillis());

    mongoTemplate.updateFirst(new Query(criteria), update, NodeExecutionsInfo.class);
  }

  private Optional<NodeExecutionsInfo> fetchCalculatedStatus(String nodeExecutionId, Set<String> projections) {
    NodeExecutionsInfo info = getNodeExecutionsInfoWithProjections(nodeExecutionId, projections);
    if (info == null) {
      return Optional.empty();
    }
    return Optional.of(info);
  }

  @Override
  public Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId) {
    Query query = new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId));
    query.fields().include(NodeExecutionsInfoKeys.nodeExecutionDetailsInfoList);
    NodeExecutionsInfo nodeExecutionsInfo = mongoTemplate.findOne(query, NodeExecutionsInfo.class);

    if (nodeExecutionsInfo != null && nodeExecutionsInfo.getNodeExecutionDetailsInfoList() != null) {
      Map<String, PmsStepDetails> result = new HashMap<>();
      for (NodeExecutionDetailsInfo detailInfo : nodeExecutionsInfo.getNodeExecutionDetailsInfoList()) {
        if (result.containsKey(detailInfo.getName())) {
          log.warn("Duplicate step detail name [{}] found for nodeExecutionId [{}] in planExecutionId [{}]. "
                  + "Replacing existing entry.",
              detailInfo.getName(), nodeExecutionId, planExecutionId);
        }
        result.put(detailInfo.getName(), detailInfo.getStepDetails());
      }
      return result;
    }
    return new HashMap<>();
  }

  @Override
  public Map<String, PmsStepDetails> getStepDetailsFormNodeExecutionInfo(NodeExecutionsInfo nodeExecutionsInfo) {
    Map<String, PmsStepDetails> result = new HashMap<>();
    for (NodeExecutionDetailsInfo detailInfo : nodeExecutionsInfo.getNodeExecutionDetailsInfoList()) {
      if (result.containsKey(detailInfo.getName())) {
        log.warn("Duplicate step detail name [{}] found for nodeExecutionId [{}]. Replacing existing entry.",
            detailInfo.getName(), nodeExecutionsInfo.getNodeExecutionId());
      }
      result.put(detailInfo.getName(), detailInfo.getStepDetails());
    }
    return result;
  }

  @Override
  public Stream<NodeExecutionsInfo> getStepDetailsNotUpdatedInGraph(String planExecutionId, Long lastUpdatedAt) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionsInfoKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, NodeExecutionsInfoKeys.createdAt));
    query.cursorBatchSize(DEFAULT_BATCH_SIZE);
    return mongoTemplate.stream(query, NodeExecutionsInfo.class);
  }

  @Override
  public Stream<NodeExecutionsInfo> getStepDetailsNotUpdatedInGraphFromSecondary(
      String planExecutionId, Long lastUpdatedAt) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionsInfoKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, NodeExecutionsInfoKeys.createdAt));
    query.cursorBatchSize(DEFAULT_BATCH_SIZE);
    return secondaryMongoTemplateHolder.getSecondaryMongoTemplate().stream(query, NodeExecutionsInfo.class);
  }

  @Override
  public boolean checkIfUnprocessedNodeExecutionInfo(String planExecutionId, Long lastUpdatedAt) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionsInfoKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, NodeExecutionsInfo.class);
  }

  @Override
  public void saveNodeExecutionInfoForRetry(
      String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId) {
    Optional<NodeExecutionsInfo> originalStepDetailInstances =
        nodeExecutionsInfoRepository.findByNodeExecutionId(originalNodeExecutionId);
    if (originalStepDetailInstances.isPresent()) {
      NodeExecutionsInfo originalExecutionInfo = originalStepDetailInstances.get();
      NodeExecutionsInfoBuilder newNodeExecutionsInfoBuilder =
          NodeExecutionsInfo.builder()
              .nodeExecutionDetailsInfoList(originalExecutionInfo.getNodeExecutionDetailsInfoList())
              .nodeExecutionId(newNodeExecutionId)
              .planExecutionId(planExecutionId)
              .resolvedInputs(originalExecutionInfo.getResolvedInputs())
              .strategyMetadata(originalExecutionInfo.getStrategyMetadata());

      if (pmsFeatureFlagHelper.isEnabled(
              originalExecutionInfo.getAccountIdentifier(), FeatureName.PIE_POPULATE_RETRY_NODE_METADATA)) {
        newNodeExecutionsInfoBuilder.retryNodeMetadata(
            fetchRetryNodeMetaDataFromOriginalNodeExecutionsInfo(originalExecutionInfo, originalNodeExecutionId));
      }

      nodeExecutionsInfoRepository.save(newNodeExecutionsInfoBuilder.build());
      stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
          StepDetailsUpdateInfo.builder()
              .nodeExecutionId(newNodeExecutionId)
              .planExecutionId(planExecutionId)
              .accountId(originalExecutionInfo.getAccountIdentifier())
              .build());
    }
  }

  public RetryNodeMetadata fetchRetryNodeMetaDataFromOriginalNodeExecutionsInfo(
      NodeExecutionsInfo originalNodeExecutionsInfo, String nodeExecutionId) {
    if (originalNodeExecutionsInfo.getRetryNodeMetadata() == null) {
      NodeExecution originalNodeExecution = nodeExecutionService.get(nodeExecutionId);
      Ambiance originalAmbiance = nodeExecutionService.getAmbiance(originalNodeExecution);

      return RetryNodeMetadata.builder()
          .startTs(originalNodeExecution.getStartTs())
          .endTs(originalNodeExecution.getEndTs())
          .runSequence(originalAmbiance.getMetadata().getRunSequence())
          .originalPlanExecutionId(originalNodeExecution.getPlanExecutionId())
          .executedBy(getExecutedBy(originalAmbiance))
          .build();
    }
    // In case of multiple retries of planExecution, we return existing retryNodeMetadata of skipped nodes which were
    // skipped in earlier retries.
    return originalNodeExecutionsInfo.getRetryNodeMetadata();
  }

  private ExecutionTriggerInfo getExecutedBy(Ambiance originalAmbiance) {
    return Objects.requireNonNull(AmbianceUtils.getCurrentStepType(originalAmbiance)).getStepCategory() == STAGE
        ? originalAmbiance.getMetadata().getTriggerInfo()
        : null;
  }

  @Override
  public void addConcurrentChildInformation(ConcurrentChildInstance concurrentChildInstance, String nodeExecutionId) {
    Update update = new Update().set(NodeExecutionsInfoKeys.concurrentChildInstance, concurrentChildInstance);
    update.set(NodeExecutionsInfoKeys.lastUpdatedAt, System.currentTimeMillis());
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
  }

  @Override
  public ConcurrentChildInstance incrementCursor(String nodeExecutionId, Status status) {
    String lockName = String.format(EXECUTION_START_PREFIX, nodeExecutionId);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(20), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.error("[MAX_CONCURRENT_CALLBACK]: Could not acquire lock for nodeExecutionId: [{}]", nodeExecutionId);
        throw new UnexpectedException("Unable to occupy lock therefore throwing the exception");
      }
      Update update = new Update();
      update.inc(NodeExecutionsInfoKeys.concurrentChildInstance + ".cursor");
      update.addToSet(NodeExecutionsInfoKeys.concurrentChildInstance + ".childStatuses", status);
      update.set(NodeExecutionsInfoKeys.lastUpdatedAt, System.currentTimeMillis());
      Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
      NodeExecutionsInfo nodeExecutionsInfo =
          mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
      if (nodeExecutionsInfo == null) {
        return null;
      }
      return nodeExecutionsInfo.getConcurrentChildInstance();
    }
  }

  @Override
  public ConcurrentChildInstance fetchConcurrentChildInstance(String nodeExecutionId) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    NodeExecutionsInfo nodeExecutionsInfo = mongoTemplate.findOne(new Query(criteria), NodeExecutionsInfo.class);
    if (nodeExecutionsInfo == null) {
      return null;
    }
    return nodeExecutionsInfo.getConcurrentChildInstance();
  }

  @Override
  public void deleteNodeExecutionInfoForGivenIds(Set<String> nodeExecutionIds) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      nodeExecutionsInfoRepository.deleteAllByNodeExecutionIdIn(nodeExecutionIds);
      return true;
    });
  }

  @Override
  public void updateTTLForNodesForGivenPlanExecutionId(String planExecutionId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }

    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(NodeExecutionsInfoKeys.validUntil, ttlDate);

    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecutionsInfo.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No nodeExecutionInfo could be marked as updated TTL for given planExecutionId - " + planExecutionId);
      }
      return true;
    });
  }

  @Override
  public Map<String, Object> fetchStrategyObjectMap(String nodeExecutionId) {
    Map<String, StrategyMetadata> strategyMetadataMap =
        fetchStrategyMetadata(Collections.singletonList(nodeExecutionId));
    Map<String, Object> strategyObjectMap = new HashMap<>();
    if (strategyMetadataMap.isEmpty()) {
      strategyObjectMap.put(ITERATION, 0);
      strategyObjectMap.put(ITERATIONS, 1);
      strategyObjectMap.put(TOTAL_ITERATIONS, 1);
      return strategyObjectMap;
    }
    StrategyMetadata strategyMetadata = strategyMetadataMap.get(nodeExecutionId);
    Map<String, Object> matrixValuesMap = new HashMap<>();
    Map<String, Object> repeatValuesMap = new HashMap<>();
    strategyObjectMap = getStrategyMapInternal(strategyMetadata, matrixValuesMap, repeatValuesMap, strategyObjectMap);
    strategyObjectMap.put(MATRIX, matrixValuesMap);
    strategyObjectMap.put(REPEAT, repeatValuesMap);

    return strategyObjectMap;
  }

  @Override
  public Map<String, Object> fetchStrategyObjectMap(List<Level> levelsWithStrategyMetadata) {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    Map<String, Object> matrixValuesMap = new HashMap<>();
    Map<String, Object> repeatValuesMap = new HashMap<>();

    List<String> nodeExecutionIds =
        levelsWithStrategyMetadata.stream().map(Level::getRuntimeId).collect(Collectors.toList());
    Map<String, StrategyMetadata> strategyMetadataMap = fetchStrategyMetadata(nodeExecutionIds);

    List<IterationVariables> levels = new ArrayList<>();
    for (Level level : levelsWithStrategyMetadata) {
      StrategyMetadata strategyMetadata = getCorrespondingStrategyMetadata(strategyMetadataMap, level);
      levels.add(IterationVariables.builder()
                     .currentIteration(strategyMetadata.getCurrentIteration())
                     .totalIterations(strategyMetadata.getTotalIterations())
                     .build());
      strategyObjectMap = getStrategyMapInternal(strategyMetadata, matrixValuesMap, repeatValuesMap, strategyObjectMap);
      if (LevelUtils.isStepLevel(level)) {
        StrategyUtils.fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);
      }
    }
    strategyObjectMap.put(MATRIX, matrixValuesMap);
    strategyObjectMap.put(REPEAT, repeatValuesMap);

    return strategyObjectMap;
  }

  private Map<String, Object> getStrategyMapInternal(StrategyMetadata strategyMetadata,
      Map<String, Object> matrixValuesMap, Map<String, Object> repeatValuesMap, Map<String, Object> strategyObjectMap) {
    if (strategyMetadata.hasMatrixMetadata()) {
      // MatrixMapLocal can contain either a string as value or a json as value.
      Map<String, String> matrixMapLocal = strategyMetadata.getMatrixMetadata().getMatrixValuesMap();
      matrixValuesMap.putAll(StrategyUtils.getMatrixMapFromCombinations(matrixMapLocal));
    }
    if (strategyMetadata.hasForMetadata()) {
      repeatValuesMap.put(ITEM, strategyMetadata.getForMetadata().getValue());
      repeatValuesMap.put(PARTITION, strategyMetadata.getForMetadata().getPartitionList());
    }

    strategyObjectMap.put(ITERATION, strategyMetadata.getCurrentIteration());
    strategyObjectMap.put(ITERATIONS, strategyMetadata.getTotalIterations());
    strategyObjectMap.put(TOTAL_ITERATIONS, strategyMetadata.getTotalIterations());
    strategyObjectMap.put(IDENTIFIER_POSTFIX, strategyMetadata.getIdentifierPostFix());
    return strategyObjectMap;
  }

  private StrategyMetadata getCorrespondingStrategyMetadata(
      Map<String, StrategyMetadata> strategyMetadataMap, Level level) {
    StrategyMetadata strategyMetadata;
    if (strategyMetadataMap.containsKey(level.getRuntimeId())) {
      strategyMetadata = strategyMetadataMap.get(level.getRuntimeId());
    } else {
      log.warn("[REMOVAL_OF_STRATEGY_METADATA]: Falling back to level.getStrategyMetadata while fetching "
              + "strategyObjectMap for runtimeId {}. Please check.",
          level.getRuntimeId());
      // This should be removed in November release.
      strategyMetadata = level.getStrategyMetadata();
    }
    return strategyMetadata;
  }

  @Override
  public Map<String, StrategyMetadata> fetchStrategyMetadata(List<String> nodeExecutionIds) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).in(nodeExecutionIds);
    Query query = new Query(criteria);
    query.fields().include(NodeExecutionsInfoKeys.strategyMetadata);
    query.fields().include(NodeExecutionsInfoKeys.nodeExecutionId);

    List<NodeExecutionsInfo> nodeExecutionsInfo = mongoTemplate.find(query, NodeExecutionsInfo.class);
    if (EmptyPredicate.isEmpty(nodeExecutionsInfo)) {
      return new HashMap<>();
    }
    return nodeExecutionsInfo.stream()
        .filter(info -> info.getStrategyMetadata() != null)
        .collect(Collectors.toMap(NodeExecutionsInfo::getNodeExecutionId, NodeExecutionsInfo::getStrategyMetadata));
  }

  @Override
  public StrategyMetadata getStrategyMetadata(NodeExecution nodeExecution) {
    return getStrategyMetadata(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution));
  }

  @Override
  public StrategyMetadata getStrategyMetadata(Level level) {
    Map<String, StrategyMetadata> strategyMetadataMap = fetchStrategyMetadata(Lists.newArrayList(level.getRuntimeId()));
    if (strategyMetadataMap.isEmpty()) {
      if (level.hasStrategyMetadata()) {
        log.warn("[REMOVAL_OF_STRATEGY_METADATA]: Falling back to strategyMetadata from level for level with runtimeId "
                + "{}, please check",
            level.getRuntimeId());
      }
      return level.getStrategyMetadata();
    }
    return strategyMetadataMap.get(level.getRuntimeId());
  }

  @Override
  public void publishStepDetailsUpdate(String accountId, String planExecutionId, String nodeExecutionId) {
    publishStepDetailsUpdate(accountId, planExecutionId, nodeExecutionId, null);
  }

  @Override
  public void publishStepDetailsUpdate(
      String accountId, String planExecutionId, String nodeExecutionId, StepType stepType) {
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepDetailsUpdate,
        StepDetailsUpdateInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .planExecutionId(planExecutionId)
            .accountId(accountId)
            .stepType(stepType)
            .build());
  }

  @Override
  public void clearFirstUnsuccessfulRuntimeIdChain(String nodeExecutionId) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    Update update = new Update()
                        .set(NodeExecutionsInfoKeys.failedChildIdChain, null)
                        .set(NodeExecutionsInfoKeys.lastUpdatedAt, System.currentTimeMillis());
    mongoTemplate.updateFirst(new Query(criteria), update, NodeExecutionsInfo.class);
  }
}
