/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.distribution.barrier.Barrier.State;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.distribution.barrier.Barrier.builder;
import static io.harness.distribution.barrier.Forcer.State.ABANDONED;
import static io.harness.distribution.barrier.Forcer.State.APPROACHING;
import static io.harness.distribution.barrier.Forcer.State.ARRIVED;
import static io.harness.distribution.barrier.Forcer.State.TIMED_OUT;
import static io.harness.govern.Switch.unhandled;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.ASYNC_WAITING;
import static io.harness.pms.contracts.execution.Status.EXPIRED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.barrier.Barrier;
import io.harness.distribution.barrier.BarrierId;
import io.harness.distribution.barrier.ForceProctor;
import io.harness.distribution.barrier.Forcer;
import io.harness.distribution.barrier.ForcerId;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.BarrierNodeRepository;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.PersistenceUtils;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierExecutionInstance.BarrierExecutionInstanceKeys;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionKeys;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.beans.BarrierResponseData.BarrierError;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.steps.barriers.beans.StageDetail.StageDetailKeys;
import io.harness.steps.barriers.service.visitor.BarrierVisitor;
import io.harness.tracing.TracingUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.BasicDBObject;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class BarrierServiceImpl implements BarrierService, ForceProctor {
  private static final String LEVEL = "level";
  private static final String PLAN = "plan";
  private static final String STAGE = "stage";
  private static final String STEP_GROUP = "stepGroup";
  private static final String STEP = "step";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String BARRIER_UPSERT_LOCK = "BARRIER_UPSERT_LOCK_";

  @Inject private PersistentLocker persistentLocker;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private BarrierNodeRepository barrierNodeRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private MongoTemplate hMongoTemplate;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private Injector injector;
  public static final Tracer tracer =
      GlobalOpenTelemetry.getTracer("io.harness.steps.barriers.service.BarrierServiceImpl");

  public void registerIterators(IteratorConfig config) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("PmsBarrierExecutionInstanceMonitor")
            .poolSize(config.getThreadPoolCount())
            .interval(ofSeconds(config.getTargetIntervalInSeconds()))
            .build(),
        BarrierService.class,
        MongoPersistenceIterator.<BarrierExecutionInstance, SpringFilterExpander>builder()
            .clazz(BarrierExecutionInstance.class)
            .fieldName(BarrierExecutionInstanceKeys.nextIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .handler(this::update)
            .filterExpander(
                query -> query.addCriteria(Criteria.where(BarrierExecutionInstanceKeys.barrierState).in(STANDING)))
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public BarrierExecutionInstance save(BarrierExecutionInstance barrierExecutionInstance) {
    return barrierNodeRepository.save(barrierExecutionInstance);
  }

  @Override
  public List<BarrierExecutionInstance> saveAll(List<BarrierExecutionInstance> barrierExecutionInstances) {
    return (List<BarrierExecutionInstance>) barrierNodeRepository.saveAll(barrierExecutionInstances);
  }

  @Override
  public BarrierExecutionInstance get(String barrierUuid) {
    return barrierNodeRepository.findById(barrierUuid)
        .orElseThrow(() -> new InvalidRequestException("Barrier not found for id: " + barrierUuid));
  }

  @Override
  public BarrierExecutionInstance findByIdentifierAndPlanExecutionId(String identifier, String planExecutionId) {
    return barrierNodeRepository.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);
  }

  @Override
  public List<BarrierExecutionInstance> findManyByPlanExecutionIdAndStrategySetupId(
      String planExecutionId, String strategySetupId) {
    /* This method is used by `BarrierWithinStrategyExpander` for fetching all BarrierExecutionInstances which
       have positions that are children of a given strategy node. */
    return barrierNodeRepository.findManyByPlanExecutionIdAndSetupInfo_StrategySetupIds(
        planExecutionId, strategySetupId);
  }

  @Override
  public boolean existsByPlanExecutionIdAndStrategySetupId(String planExecutionId, String strategySetupId) {
    /* This method is used by `BarrierWithinStrategyExpander` for fetching all BarrierExecutionInstances which
       have positions that are children of a given strategy node. */
    return barrierNodeRepository.existsByPlanExecutionIdAndSetupInfo_StrategySetupIds(planExecutionId, strategySetupId);
  }

  @Override
  public BarrierExecutionInstance findByPlanNodeIdAndPlanExecutionId(String planNodeId, String planExecutionId) {
    Criteria positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                                    .elemMatch(Criteria.where(BarrierPositionKeys.stepSetupId).is(planNodeId));
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);

    Query query = query(planExecutionIdCriteria.andOperator(positionCriteria));
    return mongoTemplate.findOne(query, BarrierExecutionInstance.class);
  }

  @Override
  public BarrierExecutionInstance update(BarrierExecutionInstance barrierExecutionInstance) {
    try (TracingUtils.TracingContext ignore = TracingUtils.createAndStartSpan(tracer, "BarrierService.update",
             barrierExecutionInstance.getTraceId(), barrierExecutionInstance.getSpanId())) {
      if (barrierExecutionInstance.getBarrierState() != STANDING) {
        return barrierExecutionInstance;
      }

      Forcer forcer = buildForcer(barrierExecutionInstance);

      Barrier barrier = builder().id(new BarrierId(barrierExecutionInstance.getUuid())).forcer(forcer).build();
      State state = barrier.pushDown(this);

      switch (state) {
        case STANDING:
          log.info("The barrier [{}] keeps standing", barrierExecutionInstance.getUuid());
          return barrierExecutionInstance;
        case DOWN:
          log.info("The barrier [{}] is down", barrierExecutionInstance.getUuid());
          waitNotifyEngine.doneWith(
              barrierExecutionInstance.getUuid(), BarrierResponseData.builder().failed(false).build());
          break;
        case ENDURE:
          log.warn("The barrier [{}] is endured", barrierExecutionInstance.getUuid());
          waitNotifyEngine.doneWith(barrierExecutionInstance.getUuid(),
              BarrierResponseData.builder()
                  .failed(true)
                  .barrierError(BarrierError.builder()
                                    .timedOut(false)
                                    .errorMessage("The barrier is rejected. "
                                        + "One of the stage failed before arriving to the barrier step.")
                                    .build())
                  .build());
          break;
        case TIMED_OUT:
          log.warn("The barrier [{}] timed out", barrierExecutionInstance.getUuid());
          waitNotifyEngine.doneWith(barrierExecutionInstance.getUuid(),
              BarrierResponseData.builder()
                  .failed(true)
                  .barrierError(BarrierError.builder().timedOut(true).errorMessage("The barrier timed out").build())
                  .build());
          break;
        default:
          unhandled(state);
      }

      return HMongoTemplate.retry(() -> updateState(barrierExecutionInstance.getUuid(), state));
    }
  }

  @Override
  public BarrierExecutionInstance updateState(String uuid, State state) {
    Query query = new Query(Criteria.where(BarrierExecutionInstanceKeys.uuid).is(uuid));
    Update update = new Update().set(BarrierExecutionInstanceKeys.barrierState, state);

    return mongoTemplate.findAndModify(query, update, BarrierExecutionInstance.class);
  }

  @Override
  public void updatePosition(BarrierPositionType positionType, String positionSetupId, String positionExecutionId,
      String stageExecutionId, String stepGroupExecutionId, List<BarrierExecutionInstance> barrierExecutionInstances,
      boolean optimizationFFEnabled, boolean disableDummyPositionFix) {
    Update update = obtainRuntimeIdUpdate(positionType, positionSetupId, positionExecutionId, stageExecutionId,
        stepGroupExecutionId, optimizationFFEnabled, disableDummyPositionFix);

    // mongo does not support multiple documents atomic update, let's update one by one
    // changing from FindAndModify to update is going to remove WriteConflicts exceptions
    // effectively this means that we are delegating concurrency synchronization to Mongo itself.
    barrierExecutionInstances.forEach(instance
        -> HMongoTemplate.retry(()
                                    -> mongoTemplate.updateFirst(query(Criteria.where(BarrierExecutionInstanceKeys.uuid)
                                                                           .is(instance.getUuid())
                                                                           .andOperator(obtainBarrierPositionCriteria(
                                                                               positionType, positionSetupId))),
                                        update, BarrierExecutionInstance.class)));
  }

  @Override
  public void upsert(BarrierExecutionInstance barrierExecutionInstance) {
    Update update = obtainInstanceUpdate(barrierExecutionInstance);
    hMongoTemplate.upsert(query(Criteria.where(BarrierExecutionInstanceKeys.identifier)
                                    .is(barrierExecutionInstance.getIdentifier())
                                    .and(BarrierExecutionInstanceKeys.planExecutionId)
                                    .is(barrierExecutionInstance.getPlanExecutionId())),
        update, BarrierExecutionInstance.class);
  }

  @Override
  public void updateBarrierPositionInfoListAndStrategyConcurrency(String barrierIdentifier, String planExecutionId,
      List<BarrierPositionInfo.BarrierPosition> barrierPositions, String strategyId, int concurrency) {
    Update update = obtainBarrierPositionInfoAndStrategyConcurrencyUpdate(barrierPositions, strategyId, concurrency);
    hMongoTemplate.findAndModify(query(Criteria.where(BarrierExecutionInstanceKeys.identifier)
                                           .is(barrierIdentifier)
                                           .and(BarrierExecutionInstanceKeys.planExecutionId)
                                           .is(planExecutionId)),
        update, BarrierExecutionInstance.class);
  }

  @Override
  public void updateBarrierPositionInfoList(
      String barrierIdentifier, String planExecutionId, List<BarrierPositionInfo.BarrierPosition> barrierPositions) {
    Update update = obtainBarrierPositionInfoUpdate(barrierPositions);
    hMongoTemplate.findAndModify(query(Criteria.where(BarrierExecutionInstanceKeys.identifier)
                                           .is(barrierIdentifier)
                                           .and(BarrierExecutionInstanceKeys.planExecutionId)
                                           .is(planExecutionId)),
        update, BarrierExecutionInstance.class);
  }

  private Update obtainBarrierPositionInfoUpdate(List<BarrierPositionInfo.BarrierPosition> barrierPositions) {
    return new Update().set(BarrierExecutionInstanceKeys.positions, barrierPositions);
  }

  private Update obtainRuntimeIdUpdate(BarrierPositionType positionType, String positionSetupId,
      String positionExecutionId, String stageExecutionId, String stepGroupExecutionId, boolean optimizationFFEnabled,
      boolean disableDummyPositionFix) {
    // The optimized path only applies to Barrier STEP nodes. For STAGE/STEP_GROUP, use legacy to match on their
    // respective setup identifiers; otherwise, the filter would incorrectly target stepSetupId and not update.
    return optimizationFFEnabled && positionType == BarrierPositionType.STEP
        ? obtainRuntimeIdUpdateOptimized(
              positionSetupId, positionExecutionId, stageExecutionId, stepGroupExecutionId, disableDummyPositionFix)
        : obtainRuntimeIdUpdateLegacy(
              positionType, positionSetupId, positionExecutionId, stageExecutionId, stepGroupExecutionId);
  }

  /**
   * This method is the optimize version of {@link #obtainRuntimeIdUpdate(BarrierPositionType, String, String, String,
   * String, boolean, boolean)}
   * It doesn't need to query on the strategyNodeType as it will have both stage and step group runtime ids.
   * This optimized version should be used only with the Barrier Step nodes.
   * @param positionSetupId
   * @param positionExecutionId
   * @param stageExecutionId
   * @param stepGroupExecutionId
   * @param disableDummyPositionFix
   * @return the MongoDB Update operation object
   */
  private Update obtainRuntimeIdUpdateOptimized(String positionSetupId, String positionExecutionId,
      String stageExecutionId, String stepGroupExecutionId, boolean disableDummyPositionFix) {
    String position = "position";
    final String positions = BarrierExecutionInstanceKeys.positions + ".$[" + position + "].";
    Criteria stepCriteria;

    // Condition 1: For when #BarrierWithinStrategyExpander expands a strategy STAGE AND STEP_GROUP
    // Matches: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=Z
    Criteria condition1 = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                              .is(positionSetupId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                              .is(stageExecutionId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                              .is(stepGroupExecutionId);

    // Condition 2: For when #BarrierWithinStrategyExpander expands a strategy STAGE
    // Matches: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=null
    Criteria condition2 = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                              .is(positionSetupId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                              .is(stageExecutionId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                              .is(null);

    // Condition 3: For when #BarrierWithinStrategyExpander expands a strategy STEP_GROUP
    // Matches: stepSetupId=X, stepGroupRuntimeId=Z, stageRuntimeId=null
    Criteria condition3 = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                              .is(positionSetupId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                              .is(stepGroupExecutionId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                              .is(null);

    // Condition 4: For regular Stages and StepGroups without strategy
    // Matches: stepSetupId=X, stageRuntimeId=null, stepGroupRuntimeId=null
    Criteria condition4 = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                              .is(positionSetupId)
                              .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                              .is(null)
                              .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                              .is(null);

    if (!disableDummyPositionFix) {
      // Use Condition 5: dummy position with race condition fix
      Criteria condition5 =
          buildCriteriaForDummyPosition(positionSetupId, stageExecutionId, stepGroupExecutionId, position);
      stepCriteria = new Criteria().orOperator(condition1, condition2, condition3, condition4, condition5);
    } else {
      stepCriteria = new Criteria().orOperator(condition1, condition2, condition3, condition4);
    }

    return new Update()
        .set(positions.concat(BarrierPositionKeys.stepRuntimeId), positionExecutionId)
        .set(positions.concat(BarrierPositionKeys.stepGroupRuntimeId), stepGroupExecutionId)
        .set(positions.concat(BarrierPositionKeys.stageRuntimeId), stageExecutionId)
        .filterArray(stepCriteria);
  }

  // Condition 5: For dummy positions that have been expanded by strategy but stepRuntimeId hasn't been set yet
  // This handles the race condition where expanded positions have stage/stepGroup runtime IDs but no step runtime ID
  // yet Match: stepSetupId=X AND stepRuntimeId=null AND correct stage/stepGroup context
  private Criteria buildCriteriaForDummyPosition(
      String positionSetupId, String stageExecutionId, String stepGroupExecutionId, String position) {
    Criteria dummyPosition = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                                 .is(positionSetupId)
                                 .and(position.concat(".").concat(BarrierPositionKeys.stepRuntimeId))
                                 .is(null);

    // Add stage context: if present, match the value; if absent, assert it must be null in the position
    // This ensures we don't over-match positions with different stageRuntimeId values
    if (stageExecutionId != null) {
      dummyPosition.and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId)).is(stageExecutionId);
    } else {
      dummyPosition.and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId)).is(null);
    }

    // Add stepGroup context: if present, match the value; if absent, assert it must be null in the position
    if (stepGroupExecutionId != null) {
      dummyPosition.and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId)).is(stepGroupExecutionId);
    } else {
      dummyPosition.and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId)).is(null);
    }
    return dummyPosition;
  }

  private Update obtainRuntimeIdUpdateLegacy(BarrierPositionType positionType, String positionSetupId,
      String positionExecutionId, String stageExecutionId, String stepGroupExecutionId) {
    String position = "position";
    final String positions = BarrierExecutionInstanceKeys.positions + ".$[" + position + "].";
    Update update;
    switch (positionType) {
      case STAGE:
        Criteria stageCriteria = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stageSetupId))
                                     .is(positionSetupId)
                                     .and(position.concat(".").concat(BarrierPositionKeys.strategyNodeType))
                                     .isNull();
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stageRuntimeId), positionExecutionId)
                     .filterArray(stageCriteria);
        break;
      case STEP_GROUP:
        Criteria stepGroupCriteria = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepGroupSetupId))
                                         .is(positionSetupId)
                                         .and(position.concat(".").concat(BarrierPositionKeys.strategyNodeType))
                                         .in(BarrierPositionType.STAGE, null)
                                         .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                                         .is(stageExecutionId);
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stepGroupRuntimeId), positionExecutionId)
                     .filterArray(stepGroupCriteria);
        break;
      case STEP:
        Criteria stepCriteria = Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId))
                                    .is(positionSetupId)
                                    .and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                                    .is(stageExecutionId)
                                    .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                                    .is(stepGroupExecutionId);
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stepRuntimeId), positionExecutionId)
                     .filterArray(stepCriteria);
        break;
      default:
        throw new InvalidRequestException(String.format("%s position type is not implemented", positionType));
    }

    return update;
  }

  private Update obtainInstanceUpdate(BarrierExecutionInstance barrierExecutionInstance) {
    Update update =
        new Update()
            .set(BarrierExecutionInstanceKeys.name, barrierExecutionInstance.getName())
            .set(BarrierExecutionInstanceKeys.identifier, barrierExecutionInstance.getIdentifier())
            .set(BarrierExecutionInstanceKeys.planExecutionId, barrierExecutionInstance.getPlanExecutionId())
            .set(BarrierExecutionInstanceKeys.barrierState, STANDING)
            .set(BarrierExecutionInstanceKeys.setupInfoName, barrierExecutionInstance.getSetupInfo().getName())
            .set(BarrierExecutionInstanceKeys.setupInfoIdentifier,
                barrierExecutionInstance.getSetupInfo().getIdentifier())
            .set(BarrierExecutionInstanceKeys.traceId, barrierExecutionInstance.getTraceId())
            .set(BarrierExecutionInstanceKeys.spanId, barrierExecutionInstance.getSpanId())
            .addToSet(BarrierExecutionInstanceKeys.stages)
            .each(barrierExecutionInstance.getSetupInfo().getStages())
            .set(BarrierExecutionInstanceKeys.positionInfoPlanExecutionId,
                barrierExecutionInstance.getPositionInfo().getPlanExecutionId())
            .addToSet(BarrierExecutionInstanceKeys.positions)
            .each(barrierExecutionInstance.getPositionInfo().getBarrierPositionList());
    if (barrierExecutionInstance.getSetupInfo().getStrategySetupIds() != null) {
      update.addToSet(BarrierExecutionInstanceKeys.strategySetupIds)
          .each(barrierExecutionInstance.getSetupInfo().getStrategySetupIds());
    }
    return update;
  }

  private Update obtainBarrierPositionInfoAndStrategyConcurrencyUpdate(
      List<BarrierPositionInfo.BarrierPosition> barrierPositions, String strategyId, int concurrency) {
    return new Update()
        .set(BarrierExecutionInstanceKeys.positions, barrierPositions)
        .set(BarrierExecutionInstanceKeys.strategyConcurrencyMap.concat(".").concat(strategyId), concurrency);
  }

  /**
   * Barrier works with 4 forcers : Plan -> Stage -> Step Group -> Barrier Node
   */
  private Forcer buildForcer(BarrierExecutionInstance barrierExecutionInstance) {
    final String planExecutionId = barrierExecutionInstance.getPlanExecutionId();

    return Forcer.builder()
        .id(new ForcerId(barrierExecutionInstance.getPlanExecutionId()))
        .metadata(ImmutableMap.of(LEVEL, PLAN))
        .children(
            barrierExecutionInstance.getPositionInfo()
                .getBarrierPositionList()
                .stream()
                // Filter out dummy positions for child pipelines that may have been skipped or never executed
                // These dummy positions are placeholders created during plan creation for chained pipelines
                // If a child pipeline stage is skipped (e.g., due to conditional execution), the dummy position
                // is never removed and would cause the barrier to wait indefinitely for a step that will never execute
                .filter(position -> {
                  boolean isDummy = Boolean.TRUE.equals(position.getIsDummyPositionForChildPipeline());
                  boolean passes = !isDummy;
                  log.info("Skipping Position - stageRuntimeId: {}, stepRuntimeId: {}", position.getStageRuntimeId(),
                      position.getStepRuntimeId());
                  return passes;
                })
                .map(position -> {
                  final Forcer step = Forcer.builder()
                                          .id(new ForcerId(position.getStepRuntimeId()))
                                          .metadata(ImmutableMap.of(LEVEL, STEP, PLAN_EXECUTION_ID, planExecutionId))
                                          .build();
                  final Forcer stepGroup =
                      Forcer.builder()
                          .id(new ForcerId(position.getStepGroupRuntimeId()))
                          .metadata(ImmutableMap.of(LEVEL, STEP_GROUP, PLAN_EXECUTION_ID, planExecutionId))
                          .children(Collections.singletonList(step))
                          .build();
                  boolean isStepGroupPresent =
                      EmptyPredicate.isNotEmpty(stepGroup.getId().getValue()) && !position.isStepGroupRollback();
                  return Forcer.builder()
                      .id(new ForcerId(position.getStageRuntimeId()))
                      .metadata(ImmutableMap.of(LEVEL, STAGE, PLAN_EXECUTION_ID, planExecutionId))
                      .children(
                          isStepGroupPresent ? Collections.singletonList(stepGroup) : Collections.singletonList(step))
                      .build();
                })
                .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Forcer.State getForcerState(ForcerId forcerId, Map<String, Object> metadata) {
    Status status;
    if (PLAN.equals(metadata.get(LEVEL))) {
      PlanExecution planExecution;
      try {
        status = planExecutionService.getStatus(forcerId.getValue());
      } catch (InvalidRequestException e) {
        log.warn("Plan Execution was not found. State set to APPROACHING", e);
        return APPROACHING;
      }

      if (StatusUtils.positiveStatuses().contains(status)) {
        return ARRIVED;
      } else if (StatusUtils.brokeStatuses().contains(status) || status == ABORTED) {
        return ABANDONED;
      }
    } else {
      NodeExecution forcerNode =
          nodeExecutionService.getWithFieldsIncluded(forcerId.getValue(), NodeProjectionUtils.withStatus);
      status = forcerNode.getStatus();
    }

    if (StatusUtils.positiveStatuses().contains(status)) {
      return ARRIVED;
    } else if (status == EXPIRED) {
      return TIMED_OUT;
    } else if (StatusUtils.finalStatuses().contains(status)) {
      return ABANDONED;
    }

    if (STEP.equals(metadata.get(LEVEL))) {
      if (status == ASYNC_WAITING) {
        return ARRIVED;
      } else {
        log.warn("NodeExecution with id: {} had status: {}. State set to APPROACHING", forcerId.getValue(), status);
      }
    }

    return APPROACHING;
  }

  @Override
  public List<BarrierExecutionInstance> findByStageIdentifierAndPlanExecutionIdAnsStateIn(
      String stageIdentifier, String planExecutionId, Set<State> stateSet) {
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);
    Criteria stageIdentifierCriteria = Criteria.where(BarrierExecutionInstanceKeys.stages)
                                           .elemMatch(Criteria.where(StageDetailKeys.identifier).is(stageIdentifier));

    Query query = query(new Criteria().andOperator(planExecutionIdCriteria, stageIdentifierCriteria));

    if (!stateSet.isEmpty()) {
      query.addCriteria(where(BarrierExecutionInstanceKeys.barrierState).in(stateSet));
    }

    return mongoTemplate.find(query, BarrierExecutionInstance.class);
  }

  @Override
  public List<BarrierExecutionInstance> findByPosition(
      String planExecutionId, BarrierPositionType positionType, String positionSetupId) {
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);

    Query query = query(new Criteria().andOperator(
        planExecutionIdCriteria, obtainBarrierPositionCriteria(positionType, positionSetupId)));

    return mongoTemplate.find(query, BarrierExecutionInstance.class);
  }

  private Criteria obtainBarrierPositionCriteria(BarrierPositionType positionType, String positionSetupId) {
    Criteria positionCriteria;
    switch (positionType) {
      case STAGE:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stageSetupId).is(positionSetupId));
        break;
      case STEP_GROUP:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stepGroupSetupId).is(positionSetupId));
        break;
      case STEP:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stepSetupId).is(positionSetupId));
        break;
      default:
        throw new InvalidRequestException(String.format("%s position type is not implemented", positionType));
    }

    return positionCriteria;
  }

  @Override
  public List<BarrierSetupInfo> getBarrierSetupInfoList(String yaml) {
    try {
      YamlNode yamlNode = YamlUtils.extractPipelineField(yaml).getNode();
      BarrierVisitor barrierVisitor = new BarrierVisitor(injector);
      barrierVisitor.walkElementTree(yamlNode);
      return new ArrayList<>(barrierVisitor.getBarrierIdentifierMap().values());
    } catch (IOException e) {
      log.error("Error while extracting yaml");
      throw new InvalidRequestException("Error while extracting yaml");
    } catch (InvalidRequestException e) {
      log.error("Error while processing yaml");
      throw e;
    }
  }

  @Override
  public void deleteAllForGivenPlanExecutionId(Set<String> planExecutionIds) {
    // Uses - planExecutionId_barrierState_stagesIdentifier_idx
    Criteria planExecutionIdCriteria =
        Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(planExecutionIdCriteria);

    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed deleting BarrierExecutionInstance; attempt: {}",
            "[Failed]: Failed deleting BarrierExecutionInstance; attempt: {}");

    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, BarrierExecutionInstance.class));
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  public void upsertBarrierExecutionInstance(String setupId, String barrierId, String barrierName,
      String planExecutionId, String parentInfoStrategyNodeType, String stageId, String stepGroupId, String strategyId,
      List<String> allStrategyIds) {
    upsertBarrierExecutionInstance(setupId, barrierId, barrierName, planExecutionId, parentInfoStrategyNodeType,
        stageId, stepGroupId, strategyId, allStrategyIds, false, null, null);
  }

  /**
   * Atomically removes a barrier position that meets specified criteria.
   * Uses MongoDB's $pull operator for thread-safety without requiring to read the document first.
   * Includes retry logic for handling transient database connectivity issues.
   *
   * @param barrierIdentifier       Barrier identifier
   * @param planExecutionId         Plan execution ID
   * @param parentPipelineStageNodeId The parent pipeline stage node ID to match
   * @return True if a position was removed, false otherwise
   */
  public boolean atomicallyRemoveDummyBarrierPosition(
      String barrierIdentifier, String planExecutionId, String parentPipelineStageNodeId) {
    Criteria matchCriteria = new Criteria();
    matchCriteria.and(BarrierExecutionInstanceKeys.identifier).is(barrierIdentifier);
    matchCriteria.and(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);

    Query query = query(matchCriteria);

    Update update = new Update().pull(BarrierExecutionInstanceKeys.positions,
        new BasicDBObject(BarrierExecutionInstanceKeys.isDummyPositionForChildPipeline, true)
            .append(BarrierExecutionInstanceKeys.parentPipelineStageNodeId, parentPipelineStageNodeId));

    // Add retry logic for transient failures
    String operationDesc = String.format(
        "Removing dummy barrier position for barrier %s in execution %s", barrierIdentifier, planExecutionId);
    String failedAttemptMessage = "Retrying " + operationDesc;
    String failureMessage = "Failed to complete " + operationDesc + " after max retries";
    RetryPolicy<Object> retryPolicy = getRetryPolicy(failedAttemptMessage, failureMessage);

    try {
      return Failsafe.with(retryPolicy).get(() -> {
        // Execute the atomic update and check if any document was modified
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(false); // get the original
        BarrierExecutionInstance originalInstance =
            mongoTemplate.findAndModify(query, update, options, BarrierExecutionInstance.class);

        if (originalInstance == null || originalInstance.getPositionInfo() == null
            || EmptyPredicate.isEmpty(originalInstance.getPositionInfo().getBarrierPositionList())) {
          return false;
        }

        return originalInstance.getPositionInfo().getBarrierPositionList().stream().anyMatch(pos
            -> null != pos.getIsDummyPositionForChildPipeline()
                && Boolean.TRUE.equals(pos.getIsDummyPositionForChildPipeline())
                && parentPipelineStageNodeId.equals(pos.getParentPipelineStageNodeId()));
      });
    } catch (Exception e) {
      log.error("Failed to atomically remove barrier position", e);
      return false;
    }
  }

  public void upsertBarrierExecutionInstance(String setupId, String barrierId, String barrierName,
      String planExecutionId, String parentInfoStrategyNodeType, String stageId, String stepGroupId, String strategyId,
      List<String> allStrategyIds, boolean dummyEntryForChildPipeline, String parentPipelineExecutionId,
      String parentPipelineStageNodeId) {
    BarrierExecutionInstance barrierExecutionInstance = null;
    if (dummyEntryForChildPipeline) {
      barrierExecutionInstance = getDummyBarrierExecutionInstance(
          barrierName, barrierId, dummyEntryForChildPipeline, parentPipelineExecutionId, parentPipelineStageNodeId);
    } else {
      BarrierPositionType strategyNodeType =
          getStrategyNodeType(parentInfoStrategyNodeType, setupId, barrierId, planExecutionId);
      barrierExecutionInstance = getBarrierExecutionInstance(setupId, barrierName, barrierId, planExecutionId, stageId,
          stepGroupId, strategyId, strategyNodeType, allStrategyIds);
    }
    String lockKey = String.format("%s%s_%s", BARRIER_UPSERT_LOCK,
        EmptyPredicate.isNotEmpty(parentPipelineExecutionId) ? parentPipelineExecutionId : planExecutionId, barrierId);
    try (AcquiredLock<?> ignore =
             persistentLocker.waitToAcquireLock(lockKey, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      upsert(barrierExecutionInstance);
    }
  }

  private BarrierExecutionInstance getDummyBarrierExecutionInstance(String barrierName, String barrierId,
      boolean isDummyPositionForChildPipeline, String parentPipelineExecutionId, String parentPipelineStageNodeId) {
    List<BarrierPositionInfo.BarrierPosition> barrierPositionList =
        List.of(BarrierPositionInfo.BarrierPosition.builder()
                    .isDummyPositionForChildPipeline(isDummyPositionForChildPipeline)
                    .parentPipelineStageNodeId(parentPipelineStageNodeId)
                    .build());
    return BarrierExecutionInstance.builder()
        .setupInfo(BarrierSetupInfo.builder().name(barrierName).identifier(barrierId).build())
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(parentPipelineExecutionId)
                          .barrierPositionList(barrierPositionList)
                          .build())
        .name(barrierName)
        .barrierState(Barrier.State.STANDING)
        .identifier(barrierId)
        .planExecutionId(parentPipelineExecutionId)
        .build();
  }

  private BarrierPositionType getStrategyNodeType(
      String parentInfoStrategyNodeType, String stepSetupId, String barrierId, String planExecutionId) {
    BarrierPositionType strategyNodeType = null;
    if (isNotEmpty(parentInfoStrategyNodeType)) {
      if (YAMLFieldNameConstants.STAGE.equals(parentInfoStrategyNodeType)) {
        strategyNodeType = BarrierPositionType.STAGE;
      } else if (YAMLFieldNameConstants.STEP_GROUP.equals(parentInfoStrategyNodeType)) {
        strategyNodeType = BarrierPositionType.STEP_GROUP;
      } else {
        log.warn("parentInfoStrategyNodeType [{}] for Barrier Step with setupId: [{}], barrierId: [{}], "
                + "planExecutionId: [{}], is neither stage or stepGroup."
                + " Setting strategyNodeType to null.",
            parentInfoStrategyNodeType, stepSetupId, barrierId, planExecutionId);
      }
    }
    return strategyNodeType;
  }

  private BarrierExecutionInstance getBarrierExecutionInstance(String setupId, String barrierName, String barrierId,
      String planExecutionId, String stageId, String stepGroupId, String strategyId,
      BarrierPositionType strategyNodeType, List<String> allStrategyIds) {
    String traceId = Span.current().getSpanContext().getTraceId();
    String spanId = Span.current().getSpanContext().getSpanId();
    List<BarrierPositionInfo.BarrierPosition> barrierPositionList =
        List.of(BarrierPositionInfo.BarrierPosition.builder()
                    .stageSetupId(stageId)
                    .stepGroupSetupId(isNotEmpty(stepGroupId) ? stepGroupId : null)
                    .strategySetupId(isNotEmpty(strategyId) ? strategyId : null)
                    .allStrategySetupIds(allStrategyIds)
                    .strategyNodeType(strategyNodeType)
                    .stepSetupId(setupId)
                    .stepGroupRollback(false)
                    .isDummyPosition(isNotEmpty(strategyId))
                    .build());
    return BarrierExecutionInstance.builder()
        .setupInfo(BarrierSetupInfo.builder()
                       .name(barrierName)
                       .identifier(barrierId)
                       .stages(Set.of(StageDetail.builder().identifier(stageId).build()))
                       .strategySetupIds(new HashSet<>(allStrategyIds))
                       .build())
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(planExecutionId)
                          .barrierPositionList(barrierPositionList)
                          .build())
        .name(barrierName)
        .barrierState(Barrier.State.STANDING)
        .identifier(barrierId)
        .planExecutionId(planExecutionId)
        .traceId(traceId)
        .spanId(spanId)
        .build();
  }
}
