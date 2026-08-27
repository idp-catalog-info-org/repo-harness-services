/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.config;

import static io.harness.waiter.PmsNotifyEventListener.PMS_ORCHESTRATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.provider.ExpressionEvaluatorProvider;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.planExecutionJson.ExpandedJsonLockConfig;
import io.harness.threading.ThreadPoolConfig;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationModuleConfig {
  @NonNull String serviceName;
  @NonNull ExpressionEvaluatorProvider expressionEvaluatorProvider;

  @Default
  ThreadPoolConfig observerThreadPoolConfig =
      ThreadPoolConfig.builder().corePoolSize(1).idleTime(10).maxPoolSize(5).queueSize(100).build();

  @Default
  ThreadPoolConfig sdkResponseThreadPoolConfig =
      ThreadPoolConfig.builder().corePoolSize(1).idleTime(10).maxPoolSize(5).queueSize(100).build();

  @Default
  ThreadPoolConfig orchestrationPoolConfig =
      ThreadPoolConfig.builder().corePoolSize(1).idleTime(10).maxPoolSize(5).queueSize(100).build();

  @Default
  ThreadPoolConfig ciSecretResolutionPoolConfig =
      ThreadPoolConfig.builder().corePoolSize(20).idleTime(5).maxPoolSize(200).queueSize(100).build();

  @Default String publisherName = PMS_ORCHESTRATION;
  @Default
  EventsFrameworkConfiguration eventsFrameworkConfiguration =
      EventsFrameworkConfiguration.builder()
          .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
          .build();
  boolean withPMS;
  boolean isPipelineService;
  boolean useFeatureFlagService;
  @Nullable io.harness.remote.client.ServiceHttpClientConfig accountServiceHttpClientConfig;
  @Nullable String accountServiceSecret;
  @Nullable String accountClientId;
  @Default
  OrchestrationRedisEventsConfig orchestrationRedisEventsConfig = OrchestrationRedisEventsConfig.builder().build();
  @Default
  OrchestrationLogConfiguration orchestrationLogConfiguration = OrchestrationLogConfiguration.builder().build();
  @Default
  OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration =
      OrchestrationRestrictionConfiguration.builder().build();
  ServiceHttpClientConfig licenseClientConfig;
  String licenseClientServiceSecret;
  String licenseClientId;

  ExpandedJsonLockConfig expandedJsonLockConfig;
  @Default boolean stuckExecutionDetectorEnabled = true;

  // Step-level concurrency limits — cluster-wide cap and per-account default. Both are read by
  // the counter-based gate wired in a follow-up PR; this PR only adds the plumbing.
  @Default Long pipelineExecutionClusterStepConcurrencyLimit = Long.MAX_VALUE;
  @Default Integer pipelineExecutionDefaultMaxLeafStepConcurrency = 5000;
  @Default boolean streamPerServiceConfiguration;

  // Kill switch for the counter-mutation hook. When false, status-transition hooks return
  // without touching Redis — the daily rebuild is the only path that reconciles counters.
  @Default boolean stepConcurrencyCounterMutationEnabled = true;

  // Kill switch for the Postgres-backed tier-2 dequeue queue store. When false, insert/delete/
  // fetch on step_concurrency_queue are no-ops — the gate (PR 5) falls back to tier-1 same-plan
  // dequeue only.
  @Default boolean stepConcurrencyQueueStoreEnabled = true;

  // Counter-based gate mode: enforce | shadow | disabled. Default shadow — the gate evaluates
  // counters and emits metric/log lines but callers always get "allow" so nothing is queued via
  // the counter gate until we deliberately flip to enforce. Consulted only when the FF
  // PIPE_USE_COUNTER_BASED_STEP_CONCURRENCY_GATE is enabled for the account.
  @Default String stepConcurrencyGateMode = "shadow";

  // Kill switch for the daily leader-elected Redis-counter drift-recompute job. When false, the
  // job is never scheduled — counters only self-correct via the mutation hook.
  @Default boolean pipelineExecutionCounterRebuildJobEnabled = true;

  // Producer switch for async plan creation: when false (default), requests continue to be
  // enqueued on hsqs exactly as today. When true, requests are enqueued on the Postgres
  // plan_creation_queue table instead. Also acts as the store-level kill switch: when false,
  // insert/delete/fetch on plan_creation_queue are no-ops.
  @Default boolean useDbQueueForPlanCreation;

  // Batch size used when draining plan_creation_queue. Unused until the drainer/poller (a later
  // PR) is wired up.
  @Default int planCreationDbQueueBatchSize = 100;

  // Kill switch for the per-project/account Redis-counter mutation hook. Unused until the
  // per-project concurrency counter service (a later PR) is wired up.
  @Default boolean planConcurrencyCounterMutationEnabled = true;

  // Per-project concurrency gate mode: enforce | shadow | disabled. Unused until the gate (a
  // later PR) is wired up.
  @Default String planConcurrencyGateMode = "shadow";

  // Kill switch for the per-project concurrency counter drift-recompute job. Unused until the
  // rebuild job (a later PR) is wired up.
  @Default boolean planConcurrencyRebuildJobEnabled = true;
}
