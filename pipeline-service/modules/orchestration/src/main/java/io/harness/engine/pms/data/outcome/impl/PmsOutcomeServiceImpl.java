/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data.outcome.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.OutcomeInstance;
import io.harness.data.OutcomeInstance.OutcomeInstanceKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.pms.data.OptionalOutcome;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsOutcomeServiceImpl implements PmsOutcomeService {
  public static final int DEFAULT_BATCH_SIZE = 500;
  public static final Set<String> OUTCOME_NAMES_TO_SKIP =
      ImmutableSet.of("unified", "vmDetailsOutcome", "podDetailsOutcome");
  @Inject private Injector injector;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PlanExpansionService planExpansionService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private TransactionHelper transactionHelper;

  @Override
  public String resolve(Ambiance ambiance, RefObject refObject) {
    if (EmptyPredicate.isNotEmpty(refObject.getProducerId())) {
      return resolveUsingProducerSetupId(ambiance, refObject);
    }
    if (!refObject.getName().contains(".")) {
      // It is not an expression-like ref-object.
      return resolveUsingRuntimeId(ambiance, refObject);
    }

    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());
    String valueUsingFullyQualifiedName = resolveUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
    if (valueUsingFullyQualifiedName == null) {
      EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(
          ambiance, EnumSet.of(NodeExecutionEntityType.OUTCOME), true);
      injector.injectMembers(evaluator);
      Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
      if (value != null) {
        try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
          log.warn(String.format("Not able to find the outcome using fullyQualifiedName: %s", fullyQualifiedName));
        }
      }
      return value == null ? null : RecastOrchestrationUtils.toJson(value);
    }
    return valueUsingFullyQualifiedName;
  }

  @Override
  public String resolveUsingLevelRuntimeIdx(String planExecutionId, List<String> levelRuntimeIdx, RefObject refObject) {
    String name = refObject.getName();
    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(OutcomeInstanceKeys.name).is(name))
                      .addCriteria(where(OutcomeInstanceKeys.levelRuntimeIdIdx).in(levelRuntimeIdx));

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same name was saved at different levels/specificity.
    OutcomeInstance instance = EmptyPredicate.isEmpty(instances)
        ? null
        : instances.stream().max(Comparator.comparing(OutcomeInstance::getLevelRuntimeIdIdx)).orElse(null);
    if (instance == null) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instance.getOutcomeJsonValue();
  }

  @Override
  public String consumeInternal(Ambiance ambiance, Level producedBy, String name, String value, String groupName) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (StringUtils.isBlank(accountId)) {
      throw new OutcomeException(format("Account identifier empty for %s", value));
    }
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      return transactionHelper.performTransaction(() -> {
        OutcomeInstance instance = mongoTemplate.insert(
            OutcomeInstance.builder()
                .uuid(generateUuid())
                .planExecutionId(ambiance.getPlanExecutionId())
                .stageExecutionId(ambiance.getStageExecutionId())
                .producedBy(producedBy)
                .name(name)
                .outcomeValue(PmsOutcome.parse(value))
                .groupName(groupName)
                .levelRuntimeIdIdx(ResolverUtils.prepareLevelRuntimeIdIdx(ambiance.getLevelsList()))
                .fullyQualifiedName(ResolverUtils.generateFullyQualifiedName(ambiance, name))
                .accountIdentifier(accountId)
                .build());
        planExpansionService.addOutcomes(ambiance, name, instance.getOutcomeValue());
        return instance.getUuid();
      });
    } catch (DuplicateKeyException ex) {
      throw new OutcomeException(format("Outcome with name %s is already saved", name));
    }
  }

  @Override
  public List<String> findAllByRuntimeId(String planExecutionId, String runtimeId) {
    Map<String, String> outcomesMap = findAllOutcomesMapByRuntimeId(planExecutionId, runtimeId);
    if (isEmpty(outcomesMap)) {
      return Collections.emptyList();
    }
    return new ArrayList<>(outcomesMap.values());
  }

  @Override
  public Map<String, String> findAllOutcomesMapByRuntimeId(String planExecutionId, String runtimeId) {
    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(OutcomeInstanceKeys.producedByRuntimeId).is(runtimeId))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt));

    List<OutcomeInstance> outcomeInstances = mongoTemplate.find(query, OutcomeInstance.class);
    if (isEmpty(outcomeInstances)) {
      return Collections.emptyMap();
    }

    Map<String, String> outcomesMap = new LinkedHashMap<>();
    outcomeInstances.forEach(oi -> outcomesMap.put(oi.getName(), oi.getOutcomeJsonValue()));
    return outcomesMap;
  }

  @Override
  public List<String> findAllOutcomeNamesByPlanExecutionId(String planExecutionId) {
    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId));
    query.fields().include(OutcomeInstanceKeys.name);
    query.cursorBatchSize(DEFAULT_BATCH_SIZE);
    List<String> outcomeNames = new ArrayList<>();
    Stream<OutcomeInstance> stream = mongoTemplate.stream(query, OutcomeInstance.class);
    Iterator<OutcomeInstance> outcomeInstances = stream.iterator();
    while (outcomeInstances.hasNext()) {
      OutcomeInstance outcomeInstance = outcomeInstances.next();
      outcomeNames.add(outcomeInstance.getName());
    }
    return outcomeNames;
  }

  @Override
  public List<String> fetchOutcomes(List<String> outcomeInstanceIds) {
    if (isEmpty(outcomeInstanceIds)) {
      return Collections.emptyList();
    }
    List<String> outcomes = new ArrayList<>();
    Query query = query(where(OutcomeInstanceKeys.uuid).in(outcomeInstanceIds));
    Iterable<OutcomeInstance> outcomesInstances = mongoTemplate.find(query, OutcomeInstance.class);
    for (OutcomeInstance instance : outcomesInstances) {
      outcomes.add(instance.getOutcomeJsonValue());
    }
    return outcomes;
  }

  @Override
  public String fetchOutcome(@NonNull String outcomeInstanceId) {
    Query query = query(where(OutcomeInstanceKeys.uuid).is(outcomeInstanceId));
    Optional<OutcomeInstance> outcomeInstance =
        Optional.ofNullable(mongoTemplate.findOne(query, OutcomeInstance.class));
    return outcomeInstance.map(OutcomeInstance::getOutcomeJsonValue).orElse(null);
  }

  private String resolveUsingRuntimeId(@NotNull Ambiance ambiance, @NotNull RefObject refObject) {
    return resolveUsingLevelRuntimeIdx(
        ambiance.getPlanExecutionId(), ResolverUtils.prepareLevelRuntimeIdIndices(ambiance), refObject);
  }

  private String resolveUsingProducerSetupId(@NotNull Ambiance ambiance, @NotNull RefObject refObject) {
    String name = refObject.getName();

    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(OutcomeInstanceKeys.name).is(name))
                      .addCriteria(where(OutcomeInstanceKeys.producedBySetupId).is(refObject.getProducerId()))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt));

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same plan node executed multiple times.
    if (EmptyPredicate.isEmpty(instances)) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instances.get(0).getOutcomeJsonValue();
  }

  private String resolveUsingFullyQualifiedName(
      @NotNull Ambiance ambiance, @NotNull RefObject refObject, String fullyQualifiedName) {
    String name = refObject.getName();

    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(OutcomeInstanceKeys.fullyQualifiedName).is(fullyQualifiedName))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt))
                      .limit(1);

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same plan node executed multiple times.
    if (EmptyPredicate.isEmpty(instances)) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instances.get(0).getOutcomeJsonValue();
  }

  @Override
  public List<StepOutcomeRef> fetchOutcomeRefs(String nodeExecutionId) {
    List<OutcomeInstance> instances = fetchOutcomeInstanceByRuntimeId(nodeExecutionId);
    if (isEmpty(instances)) {
      return new ArrayList<>();
    }
    return instances.stream()
        .map(oi -> StepOutcomeRef.newBuilder().setName(oi.getName()).setInstanceId(oi.getUuid()).build())
        .collect(Collectors.toList());
  }

  @Override
  public OptionalOutcome resolveOptional(Ambiance ambiance, RefObject refObject) {
    if (EmptyPredicate.isNotEmpty(refObject.getProducerId())) {
      return resolveOptionalUsingProducerSetupId(ambiance, refObject);
    }
    if (!refObject.getName().contains(".")) {
      // It is not an expression-like ref-object.
      return resolveOptionalUsingRuntimeId(ambiance, refObject);
    }

    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());

    OptionalOutcome optionalOutcome = resolveOptionalUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
    if (optionalOutcome.isFound()) {
      return optionalOutcome;
    }
    EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(
        ambiance, EnumSet.of(NodeExecutionEntityType.OUTCOME), true);
    injector.injectMembers(evaluator);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
      if (value != null) {
        log.warn(String.format("Not able to find the outcome using fullyQualifiedName: %s", fullyQualifiedName));
      }
      return OptionalOutcome.builder()
          .found(value != null)
          .outcome(value == null ? null : RecastOrchestrationUtils.toJson(value))
          .build();
    } catch (UnresolvedExpressionsException | JexlException ignore) {
      return OptionalOutcome.builder().found(false).build();
    }
  }

  @Override
  public List<OutcomeInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId) {
    Query query = query(where(OutcomeInstanceKeys.producedByRuntimeId).is(runtimeId));
    return mongoTemplate.find(query, OutcomeInstance.class);
  }

  @Override
  public List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionUuid) {
    List<String> outcomeUuids = new ArrayList<>();
    List<OutcomeInstance> outcomeInstances = fetchOutcomeInstanceByRuntimeId(originalNodeExecutionUuid);
    for (OutcomeInstance outcomeInstance : outcomeInstances) {
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())
          && OUTCOME_NAMES_TO_SKIP.contains(outcomeInstance.getName())) {
        continue;
      }
      String uuid = consume(ambiance, outcomeInstance.getName(), outcomeInstance.getOutcomeValue().toJson(),
          outcomeInstance.getGroupName());
      outcomeUuids.add(uuid);
    }
    return outcomeUuids;
  }

  @Override
  public Map<String, List<StepOutcomeRef>> fetchOutcomeRefs(List<String> nodeExecutionIds) {
    Map<String, List<StepOutcomeRef>> refMap = new HashMap<>();
    Query query = query(where(OutcomeInstanceKeys.producedByRuntimeId).in(nodeExecutionIds));
    query.fields()
        .include(OutcomeInstanceKeys.uuid)
        .include(OutcomeInstanceKeys.name)
        .include(OutcomeInstanceKeys.producedBy);

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);
    for (OutcomeInstance oi : instances) {
      StepOutcomeRef stepOutcomeRef =
          StepOutcomeRef.newBuilder().setName(oi.getName()).setInstanceId(oi.getUuid()).build();
      refMap.compute(oi.getProducedBy().getRuntimeId(), (k, v) -> {
        if (v == null) {
          return new ArrayList<>(Collections.singletonList(stepOutcomeRef));
        } else {
          v.add(stepOutcomeRef);
          return v;
        }
      });
    }
    return refMap;
  }

  @Override
  public void deleteAllOutcomesInstances(Set<String> planExecutionIds) {
    Criteria criteria = where(OutcomeInstanceKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting OutcomeInstance; attempt: {}",
            "[Failed]: Failed deleting OutcomeInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, OutcomeInstance.class));
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    Criteria criteria = where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(OutcomeInstanceKeys.validUntil, ttlDate);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed updating TTL OutcomeInstance; attempt: {}",
            "[Failed]: Failed updating TTL OutcomeInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, OutcomeInstance.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No OutcomeInstance could be marked as updated TTL for given planExecutionIds - " + planExecutionId);
      }
      return true;
    });
  }

  private OptionalOutcome resolveOptionalUsingProducerSetupId(Ambiance ambiance, RefObject refObject) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingProducerSetupId(ambiance, refObject);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }

  private OptionalOutcome resolveOptionalUsingRuntimeId(Ambiance ambiance, RefObject refObject) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingRuntimeId(ambiance, refObject);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }

  private OptionalOutcome resolveOptionalUsingFullyQualifiedName(
      Ambiance ambiance, RefObject refObject, String fullyQualifiedName) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }

  @Override
  public boolean existsOutcomeName(String planExecutionId, String name) {
    Query query =
        query(where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId).and(OutcomeInstanceKeys.name).is(name))
            .limit(1);
    return mongoTemplate.exists(query, OutcomeInstance.class);
  }
}
