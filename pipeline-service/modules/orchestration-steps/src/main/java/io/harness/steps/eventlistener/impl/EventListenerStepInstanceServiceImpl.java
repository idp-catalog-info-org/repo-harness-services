/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.repositories.EventListenerStepInstanceRepository;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.eventlistener.EventListenerStepInstanceService;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.steps.eventlistener.beans.EventListenerStepResponseData;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance.EventListenerStepInstanceKeys;
import io.harness.tasks.ResponseData;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.mapping.Mapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Slf4j
public class EventListenerStepInstanceServiceImpl implements EventListenerStepInstanceService {
  private final EventListenerStepInstanceRepository eventListenerStepInstanceRepository;
  private final WaitNotifyEngine waitNotifyEngine;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private static final int MAX_BATCH_SIZE = 500;
  private static final Set<String> eventListenerStepSpecTypeConstants =
      new HashSet<>(List.of(StepSpecTypeConstants.EVENT_LISTENER));

  @Inject
  public EventListenerStepInstanceServiceImpl(EventListenerStepInstanceRepository eventListenerStepInstanceRepository,
      WaitNotifyEngine waitNotifyEngine, ScopeResolutionHelper scopeResolutionHelper) {
    this.eventListenerStepInstanceRepository = eventListenerStepInstanceRepository;
    this.waitNotifyEngine = waitNotifyEngine;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public EventListenerStepInstance save(@NotNull EventListenerStepInstance instance) {
    return eventListenerStepInstanceRepository.save(instance);
  }

  @Override
  public EventListenerStepInstance get(@NotNull String eventListenerInstanceId) {
    Optional<EventListenerStepInstance> optional =
        eventListenerStepInstanceRepository.findById(eventListenerInstanceId);
    if (optional.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Invalid EventListener step instance id: %s", eventListenerInstanceId));
    }
    return optional.get();
  }

  @Override
  public Iterator<EventListenerStepInstance> findByWebhookIdAndStatusWaiting(
      String accountIdentifier, String webhookIdentifier) {
    Criteria criteria = Criteria.where(EventListenerStepInstanceKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(EventListenerStepInstanceKeys.webhookIdentifier).is(webhookIdentifier);
    criteria.and(EventListenerStepInstanceKeys.status).is(EventListenerStepInstanceStatus.WAITING);
    return eventListenerStepInstanceRepository.findAll(criteria);
  }

  @Override
  public void deleteByNodeExecutionIds(@NotNull Set<String> nodeExecutionIds) {
    if (isEmpty(nodeExecutionIds)) {
      return;
    }
    List<String> nodeExecutionIdsList = new ArrayList<>(nodeExecutionIds);
    List<List<String>> batchNodeExecutionIdsList = Lists.partition(nodeExecutionIdsList, MAX_BATCH_SIZE);
    batchNodeExecutionIdsList.forEach(
        batchNodeExecutionIds -> deleteByNodeExecutionIdsInternal(new HashSet<>(batchNodeExecutionIds)));
  }

  @Override
  public boolean isNodeExecutionOfEventListenerStepType(NodeExecution nodeExecution) {
    if (isNull(nodeExecution) || isNull(nodeExecution.getStepType())) {
      return false;
    }
    if (!StepCategory.STEP.equals(nodeExecution.getStepType().getStepCategory())) {
      return false;
    }
    String stepType = nodeExecution.getStepType().getType();
    return eventListenerStepSpecTypeConstants.contains(stepType);
  }

  @Override
  public void abortByNodeExecutionId(String nodeExecutionId) {
    eventListenerStepInstanceRepository.updateFirst(
        new Query(Criteria.where(EventListenerStepInstanceKeys.nodeExecutionId).is(nodeExecutionId))
            .addCriteria(
                Criteria.where(EventListenerStepInstanceKeys.status).is(EventListenerStepInstanceStatus.WAITING)),
        new Update().set(EventListenerStepInstanceKeys.status, EventListenerStepInstanceStatus.ABORTED));
  }

  @Override
  public void expireByNodeExecutionId(String nodeExecutionId) {
    eventListenerStepInstanceRepository.updateFirst(
        new Query(Criteria.where(EventListenerStepInstanceKeys.nodeExecutionId).is(nodeExecutionId))
            .addCriteria(
                Criteria.where(EventListenerStepInstanceKeys.status).is(EventListenerStepInstanceStatus.WAITING)),
        new Update().set(EventListenerStepInstanceKeys.status, EventListenerStepInstanceStatus.EXPIRED));
  }

  @Override
  public EventListenerStepInstance finalizeStatus(String eventListenerInstanceId, String eventCorrelationId,
      EventListenerStepInstanceStatus status, List<HeaderConfig> headerConfigs) {
    Update update = new Update().set(EventListenerStepInstanceKeys.status, status);
    EventListenerStepInstance eventListenerStepInstance = eventListenerStepInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(eventListenerInstanceId))
            .addCriteria(
                Criteria.where(EventListenerStepInstanceKeys.status).is(EventListenerStepInstanceStatus.WAITING)),
        update);
    if (status.isFinalStatus() && eventListenerStepInstance != null) {
      ResponseData responseData = EventListenerStepResponseData.builder()
                                      .instanceId(eventListenerInstanceId)
                                      .eventCorrelationId(eventCorrelationId)
                                      .headersConfigs(headerConfigs)
                                      .build();
      waitNotifyEngine.doneWith(eventListenerInstanceId, responseData);
    }
    return eventListenerStepInstance;
  }

  private void deleteByNodeExecutionIdsInternal(Set<String> nodeExecutionIds) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // uses nodeExecutionId_1 idx
      long deletedCount = eventListenerStepInstanceRepository.deleteAllByNodeExecutionIdIn(nodeExecutionIds);
      log.info("Successfully deleted {} eventListenerStepInstances based on nodeExecutionIds", deletedCount);
      return null;
    });
  }
}
