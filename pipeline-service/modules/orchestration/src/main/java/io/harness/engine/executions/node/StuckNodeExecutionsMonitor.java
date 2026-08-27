/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.PIPE_AUTO_ABORT_STUCK_EXECUTIONS;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.plan.NodeType.PLAN_NODE;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.SystemIssuer;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.serializer.ProtoUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(CDC)
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class StuckNodeExecutionsMonitor extends IteratorLoopModeHandler implements Handler<NodeExecution> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private InterruptManager interruptManager;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private MetricService metricService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  public static final String STUCK_NODE_EXECUTIONS_COUNTER = "stuck_node_executions_counter";
  private static final Duration TRACKING_EXPIRATION = Duration.ofMinutes(30);
  private static final int MAX_TRACKED_EXECUTIONS = 1000;

  // Track unique stuck executions when auto-abort is disabled
  private final Cache<String, Boolean> trackedStuckExecutions =
      Caffeine.newBuilder().maximumSize(MAX_TRACKED_EXECUTIONS).expireAfterWrite(TRACKING_EXPIRATION).build();

  @Override
  public void handle(NodeExecution entity) {
    boolean autoAbort = false;
    boolean foundInCache = false;

    // acquiring lock on planExecution for avoiding concurrent abortion on different pods.
    try (var ignore = entity.autoLogContext();
         var lock = persistentLocker.acquireLock(
             String.format("StuckMonitor-%s", entity.getPlanExecutionId()), Duration.ofMinutes(1))) {
      if (lock == null) {
        log.info("Lock not acquired in StuckMonitor for planExecutionId {}. Skipping.", entity.getPlanExecutionId());
        return;
      }
      autoAbort = pmsFeatureFlagHelper.isEnabled(entity.getAccountId(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);
      if (autoAbort) {
        log.info("Aborting stuck execution.");
        interruptManager.register(
            InterruptPackage.builder()
                .planExecutionId(entity.getPlanExecutionId())
                .nodeExecutionId(entity.getUuid())
                .interruptType(InterruptType.ABORT_ALL)
                .interruptConfig(
                    InterruptConfig.newBuilder()
                        .setIssuedBy(
                            IssuedBy.newBuilder()
                                .setIssueTime(ProtoUtils.unixMillisToTimestamp(Instant.now().toEpochMilli()))
                                .setSystemIssuer(SystemIssuer.newBuilder().setMessage("Stuck execution").build())
                                .build())
                        .build())
                .build());
      } else {
        // Track unique stuck executions when auto-abort is disabled
        String planExecutionId = entity.getPlanExecutionId();
        foundInCache = Boolean.TRUE.equals(trackedStuckExecutions.getIfPresent(planExecutionId));

        if (!foundInCache) {
          trackedStuckExecutions.put(planExecutionId, true);
          // Log all currently tracked stuck executions including the new one
          log.warn("New stuck execution detected. All tracked stuck executions on this pod (total: {}): {}",
              trackedStuckExecutions.estimatedSize(), trackedStuckExecutions.asMap().keySet());
        }
        nodeExecutionService.markNodesProcessing(Collections.singletonList(entity.getUuid()), false);
      }
    } catch (Exception e) {
      // if exception happens, it means that abort is not going forward via interruptHandler anymore. marking node
      // as not processing anymore
      log.error("Exception in StuckNodeExecutionsMonitor, defaulting to marking node as not processing anymore", e);
      nodeExecutionService.markNodesProcessing(Collections.singletonList(entity.getUuid()), false);
    } finally {
      try (var ignore = new PmsMetricContextGuard(accountIdContextMap(entity))) {
        // Only increment metric for unique stuck executions
        if (autoAbort || !foundInCache) {
          metricService.incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
        }
      }
    }
  }

  private Map<String, String> accountIdContextMap(NodeExecution nodeExecution) {
    return new HashMap<>() {
      { put("accountId", nodeExecution.getAccountId()); }
    };
  }

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorExecutionHandler.registerIteratorHandler("StuckExecutionsMonitor", this);
  }

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    // empty as we are using redis batch mode
    throw new UnsupportedOperationException("Pump mode not supported in StuckNodeExecutionsMonitor");
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<NodeExecution, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       StuckNodeExecutionsMonitor.class,
                       MongoPersistenceIterator.<NodeExecution, SpringFilterExpander>builder()
                           .clazz(NodeExecution.class)
                           .fieldName(NodeExecutionKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(10))
                           .acceptableExecutionTime(ofSeconds(30))
                           .handler(this)
                           .filterExpander(q
                               -> q.addCriteria(where(NodeExecutionKeys.processingEvent)
                                                    .is(true)
                                                    .and(NodeExecutionKeys.nodeType)
                                                    .is(PLAN_NODE.name())))
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
                           .redistribute(true));
  }
}
