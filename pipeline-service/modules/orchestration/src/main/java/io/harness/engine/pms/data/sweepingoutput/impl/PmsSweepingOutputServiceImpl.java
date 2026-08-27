/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data.sweepingoutput.impl;

import static io.harness.data.ExecutionSweepingOutputInstance.TTL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.engine.pms.data.ResolverUtils.getLog;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.data.ExecutionSweepingOutputInstance.ExecutionSweepingOutputKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.RawSweepingOutputConsumeUpsert;
import io.harness.engine.pms.data.Resolver;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.data.output.PmsSweepingOutput;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.serializer.spring.converters.outputs.PmsSweepingOutputWriteConverter;
import io.harness.springdata.PersistenceUtils;
import io.harness.utils.SizeValidatorUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.client.result.UpdateResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.jexl3.JexlException;
import org.bson.types.Binary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsSweepingOutputServiceImpl implements PmsSweepingOutputService {
  public static final int DEFAULT_BATCH_SIZE = 500;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private Injector injector;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputWriteConverter pmsSweepingOutputWriteConverter;
  public static final Set<String> fieldsForConsumeUpsert = Sets.newHashSet(ExecutionSweepingOutputKeys.producedBy,
      ExecutionSweepingOutputKeys.fullyQualifiedName, ExecutionSweepingOutputKeys.planExecutionId);

  public static final Set<String> OUTPUT_NAMES_TO_SKIP = ImmutableSet.of("uniqueStepIdentifiers", "portDetails",
      "containerDetails", "podCleanupDetails", "stageInfraDetails", "initEnvVars", "initializeExecution",
      "usePipelineRollbackStrategy", "pipelineRollbackFailureInfo");
  @Override
  public String resolve(Ambiance ambiance, RefObject refObject) {
    if (!refObject.getName().contains(".")) {
      // It is not an expression-like ref-object.
      return resolveUsingRuntimeId(ambiance, refObject);
    }
    // TODO(sahil): Add implementation for groupName in refObject for expression-like ref name
    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());
    ExecutionSweepingOutputInstance sweepingOutputInstance =
        getInstanceUsingFullyQualifiedName(ambiance, fullyQualifiedName);
    if (sweepingOutputInstance != null) {
      return sweepingOutputInstance.getOutputValueJson();
    }
    EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(
        ambiance, EnumSet.of(NodeExecutionEntityType.SWEEPING_OUTPUT), true);
    injector.injectMembers(evaluator);
    Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
    if (value != null) {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        log.warn(
            String.format("Not able to find the sweeping output using fullyQualifiedName: %s", fullyQualifiedName));
      }
    }
    return value == null ? null : RecastOrchestrationUtils.toJson(value);
  }

  @Override
  public String resolveUsingLevelRuntimeIdx(String planExecutionId, List<String> levelRuntimeIdx, RefObject refObject) {
    String name = refObject.getName();
    // We can't filter by groupName provided in rejObject via this utility
    ExecutionSweepingOutputInstance instance = getInstance(planExecutionId, levelRuntimeIdx, refObject);
    if (instance == null) {
      throw new SweepingOutputException(format("Could not resolve sweeping output with name '%s'", name));
    }

    return instance.getOutputValueJson();
  }

  private String resolveUsingRuntimeId(Ambiance ambiance, RefObject refObject) {
    String name = refObject.getName();
    String groupName = refObject.getGroupName();
    ExecutionSweepingOutputInstance instance = getInstance(ambiance.getPlanExecutionId(),
        ResolverUtils.prepareLevelRuntimeIdIndicesUsingGroupName(ambiance, groupName), refObject);
    if (instance == null) {
      throw new SweepingOutputException(format("Could not resolve sweeping output with name '%s'", name));
    }

    return instance.getOutputValueJson();
  }

  @Override
  public List<RawOptionalSweepingOutput> findOutputsUsingNodeId(Ambiance ambiance, String name, List<String> nodeIds) {
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name))
                      .addCriteria(where(ExecutionSweepingOutputKeys.producedBy + ".setupId").in(nodeIds));
    List<ExecutionSweepingOutputInstance> instances = mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);
    return instances.stream()
        .map(instance -> RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build())
        .collect(Collectors.toList());
  }

  @Override
  public List<RawOptionalSweepingOutput> findOutputsWithGivenNameAndStageExecution(Ambiance ambiance, String name) {
    /*
    This method uses the plan and stage execution IDs from the original execution.
    For example, during a pipeline rollback, it does not use the execution IDs of the rollback itself,
    but rather takes those from the initial/original execution.
    */
    String planExecutionId = AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance);
    String stageExecutionId = AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance);
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name))
                      .addCriteria(where(ExecutionSweepingOutputKeys.stageExecutionId).is(stageExecutionId));
    List<ExecutionSweepingOutputInstance> instances = mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);
    return instances.stream()
        .map(instance -> RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build())
        .collect(Collectors.toList());
  }

  @Override
  public List<RawOptionalSweepingOutput> findOutputsUsingExecutionIds(
      Ambiance ambiance, String name, List<String> nodeIds) {
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name))
                      .addCriteria(where(ExecutionSweepingOutputKeys.producedBy + ".runtimeId").in(nodeIds));
    List<ExecutionSweepingOutputInstance> instances = mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);
    return instances.stream()
        .map(instance -> RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build())
        .collect(Collectors.toList());
  }

  @Override
  public List<ExecutionSweepingOutputInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId) {
    Query query = query(where(ExecutionSweepingOutputKeys.producedBy + "."
        + "runtimeId")
                            .is(runtimeId));
    return mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);
  }

  @Override
  public List<String> fetchNameOfOutcomesInPlanExecutionId(String planExecutionId) {
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(planExecutionId));
    query.fields().include(ExecutionSweepingOutputKeys.name);
    return mongoTemplate.find(query, ExecutionSweepingOutputInstance.class)
        .stream()
        .map(ExecutionSweepingOutputInstance::getName)
        .toList();
  }

  @Override
  public List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionUuid) {
    List<String> outputUuids = new ArrayList<>();
    List<ExecutionSweepingOutputInstance> outputInstances = fetchOutcomeInstanceByRuntimeId(originalNodeExecutionUuid);
    for (ExecutionSweepingOutputInstance outputInstance : outputInstances) {
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())
          && OUTPUT_NAMES_TO_SKIP.contains(outputInstance.getName())) {
        continue;
      }
      String uuid = consume(
          ambiance, outputInstance.getName(), outputInstance.getValueOutput().toJson(), outputInstance.getGroupName());
      outputUuids.add(uuid);
    }
    return outputUuids;
  }

  @Override
  public RawOptionalSweepingOutput resolveOptional(Ambiance ambiance, RefObject refObject) {
    if (!refObject.getName().contains(".")) {
      if (isNotEmpty(refObject.getLevelRuntimeIdx())) {
        // match individual runtime Id
        return resolveOptionalUsingRuntimeId(ambiance, refObject);
      }
      // It is not an expression-like ref-object.
      // match with any runtime Id
      return resolveOptionalUsingRuntimeIds(ambiance, refObject);
    }

    // TODO(sahil): Add implementation for groupName in refObject for expression-like ref name
    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());
    RawOptionalSweepingOutput rawOptionalSweepingOutput =
        resolveOptionalUsingFullyQualifiedName(ambiance, fullyQualifiedName);
    if (rawOptionalSweepingOutput.isFound()) {
      return rawOptionalSweepingOutput;
    }
    EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(
        ambiance, EnumSet.of(NodeExecutionEntityType.SWEEPING_OUTPUT), true);
    injector.injectMembers(evaluator);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
      if (value != null) {
        log.warn(
            String.format("Not able to find the sweeping output using fullyQualifiedName: %s", fullyQualifiedName));
      }
      return value == null
          ? RawOptionalSweepingOutput.builder().found(false).build()
          : RawOptionalSweepingOutput.builder().found(true).output(RecastOrchestrationUtils.toJson(value)).build();
    } catch (UnresolvedExpressionsException | JexlException e) {
      return RawOptionalSweepingOutput.builder().found(false).build();
    }
  }

  @Override
  public void deleteAllSweepingOutputInstances(Set<String> planExecutionIds) {
    Criteria criteria = where(ExecutionSweepingOutputKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting ExecutionSweepingOutputInstance; attempt: {}",
            "[Failed]: Failed deleting ExecutionSweepingOutputInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, ExecutionSweepingOutputInstance.class));
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    Criteria criteria = where(ExecutionSweepingOutputKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(ExecutionSweepingOutputKeys.validUntil, ttlDate);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed updating TTL ExecutionSweepingOutputInstance; attempt: {}",
            "[Failed]: Failed updating TTL ExecutionSweepingOutputInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, ExecutionSweepingOutputInstance.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No ExecutionSweepingOutputInstance could be marked as updated TTL for given planExecutionIds - "
            + planExecutionId);
      }
      return true;
    });
  }

  /**
   * Note: Use this method with caution
   * Overriding Behaviour: If an existing output is present at same scope and name, then override with `value` specified
   *
   * If such an output is not present, behaviour is same as consume
   * @return the uuid of the instance created/modified and isUpsert boolean
   */
  @Override
  public RawSweepingOutputConsumeUpsert consumeUpsert(
      @NotNull Ambiance ambiance, @NotNull String name, String value, String groupName) {
    Level producedBy = AmbianceUtils.obtainCurrentLevel(ambiance);
    Ambiance groupAmbiance = Resolver.processGroupAmbiance(ambiance, groupName);
    return consumeUpsertInternal(groupAmbiance, producedBy, name, value, groupName);
  }

  private RawOptionalSweepingOutput resolveOptionalUsingRuntimeIds(Ambiance ambiance, RefObject refObject) {
    String groupName = refObject.getGroupName();
    ExecutionSweepingOutputInstance instance = getInstance(ambiance.getPlanExecutionId(),
        ResolverUtils.prepareLevelRuntimeIdIndicesUsingGroupName(ambiance, groupName), refObject);
    if (instance == null) {
      return RawOptionalSweepingOutput.builder().found(false).build();
    }
    return RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build();
  }

  private RawOptionalSweepingOutput resolveOptionalUsingRuntimeId(Ambiance ambiance, RefObject refObject) {
    ExecutionSweepingOutputInstance instance =
        getInstance(ambiance.getPlanExecutionId(), refObject.getLevelRuntimeIdx(), refObject);
    if (instance == null) {
      return RawOptionalSweepingOutput.builder().found(false).build();
    }
    return RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build();
  }

  private RawOptionalSweepingOutput resolveOptionalUsingFullyQualifiedName(
      Ambiance ambiance, String fullyQualifiedName) {
    ExecutionSweepingOutputInstance instance = getInstanceUsingFullyQualifiedName(ambiance, fullyQualifiedName);
    if (instance == null) {
      return RawOptionalSweepingOutput.builder().found(false).build();
    }
    return RawOptionalSweepingOutput.builder().found(true).output(instance.getOutputValueJson()).build();
  }

  private ExecutionSweepingOutputInstance getInstance(
      String planExecutionId, List<String> levelRuntimeIdIdx, RefObject refObject) {
    String name = refObject.getName();
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name))
                      .addCriteria(where(ExecutionSweepingOutputKeys.levelRuntimeIdIdx).in(levelRuntimeIdIdx));
    List<ExecutionSweepingOutputInstance> instances = mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);
    // Multiple instances might be returned if the same name was saved at different levels/specificity.
    return EmptyPredicate.isEmpty(instances)
        ? null
        : instances.stream()
              .max(Comparator.comparing(ExecutionSweepingOutputInstance::getLevelRuntimeIdIdx))
              .orElse(null);
  }

  private ExecutionSweepingOutputInstance getInstance(
      String planExecutionId, String levelRuntimeIdIdx, RefObject refObject) {
    String name = refObject.getName();
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name))
                      .addCriteria(where(ExecutionSweepingOutputKeys.levelRuntimeIdIdx).is(levelRuntimeIdIdx));
    return mongoTemplate.findOne(query, ExecutionSweepingOutputInstance.class);
  }

  private ExecutionSweepingOutputInstance getInstanceUsingFullyQualifiedName(
      Ambiance ambiance, String fullyQualifiedName) {
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(ExecutionSweepingOutputKeys.fullyQualifiedName).is(fullyQualifiedName))
                      .with(Sort.by(Sort.Direction.DESC, ExecutionSweepingOutputKeys.createdAt))
                      .limit(1);
    List<ExecutionSweepingOutputInstance> instances = mongoTemplate.find(query, ExecutionSweepingOutputInstance.class);

    if (EmptyPredicate.isEmpty(instances)) {
      return null;
    }
    // Multiple instances might be returned if the same name was saved at different levels/specificity.
    return instances.get(0);
  }

  @Override
  public String consumeInternal(Ambiance ambiance, Level producedBy, String name, String value, String groupName) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      SizeValidatorUtils.validate(value, "OUTPUT");
      ExecutionSweepingOutputInstance instance =
          mongoTemplate.insert(ExecutionSweepingOutputInstance.builder()
                                   .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                   .uuid(generateUuid())
                                   .planExecutionId(ambiance.getPlanExecutionId())
                                   .stageExecutionId(ambiance.getStageExecutionId())
                                   .producedBy(producedBy)
                                   .name(name)
                                   .valueOutput(PmsSweepingOutput.parse(value))
                                   .levelRuntimeIdIdx(ResolverUtils.prepareLevelRuntimeIdIdx(ambiance.getLevelsList()))
                                   .groupName(groupName)
                                   .fullyQualifiedName(ResolverUtils.generateFullyQualifiedName(ambiance, name))
                                   .build());
      return instance.getUuid();
    } catch (DuplicateKeyException ex) {
      throw new SweepingOutputException(format("Sweeping output with name %s is already saved", name));
    }
  }

  private RawSweepingOutputConsumeUpsert consumeUpsertInternal(
      Ambiance ambiance, Level producedBy, String name, String value, String groupName) {
    try {
      return RawSweepingOutputConsumeUpsert.builder()
          .isUpsert(false)
          .id(consumeInternal(ambiance, producedBy, name, value, groupName))
          .build();
    } catch (SweepingOutputException ex) {
      SizeValidatorUtils.validate(value, "OUTPUT");
      // update document
      Query uniqueIndexQuery = buildUniqueIndexQueryWithFieldsToInclude(ambiance, name, fieldsForConsumeUpsert);
      Update nonUniqueUpdateSet = buildNonUniqueUpdateSet(ambiance, producedBy,
          // IMP: need to call the convertor in findAndModify flow, else read of modified document will fail
          pmsSweepingOutputWriteConverter.convert(PmsSweepingOutput.parse(value)), groupName, name);
      ExecutionSweepingOutputInstance originalInstance =
          mongoTemplate.findAndModify(uniqueIndexQuery, nonUniqueUpdateSet, ExecutionSweepingOutputInstance.class);
      if (originalInstance != null) {
        log.info("Sweeping output with fqn {} produced by {} is getting overridden by {} for execution {}",
            originalInstance.getFullyQualifiedName(), getLog(originalInstance.getProducedBy()), getLog(producedBy),
            originalInstance.getPlanExecutionId());
        return RawSweepingOutputConsumeUpsert.builder().isUpsert(true).id(originalInstance.getUuid()).build();
      }
      throw new SweepingOutputException("Couldn't find sweeping output to update, also insert failed", ex);
    }
  }

  private static Query buildUniqueIndexQueryWithFieldsToInclude(
      Ambiance ambiance, String name, Set<String> fieldsToInclude) {
    String levelRuntimeIdIdx = ResolverUtils.prepareLevelRuntimeIdIdx(ambiance.getLevelsList());
    Query query = query(where(ExecutionSweepingOutputKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(ExecutionSweepingOutputKeys.levelRuntimeIdIdx).is(levelRuntimeIdIdx))
                      .addCriteria(where(ExecutionSweepingOutputKeys.name).is(name));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return query;
  }

  private static Update buildNonUniqueUpdateSet(
      Ambiance ambiance, Level producedBy, Binary value, String groupName, String name) {
    Update update = new Update();
    update.set(ExecutionSweepingOutputKeys.stageExecutionId, ambiance.getStageExecutionId());
    update.set(ExecutionSweepingOutputKeys.producedBy, producedBy);
    update.set(ExecutionSweepingOutputKeys.valueOutput, value);
    update.set(ExecutionSweepingOutputKeys.groupName, groupName);
    update.set(
        ExecutionSweepingOutputKeys.fullyQualifiedName, ResolverUtils.generateFullyQualifiedName(ambiance, name));
    update.set(ExecutionSweepingOutputKeys.validUntil, Date.from(OffsetDateTime.now().plus(TTL).toInstant()));
    return update;
  }
}
