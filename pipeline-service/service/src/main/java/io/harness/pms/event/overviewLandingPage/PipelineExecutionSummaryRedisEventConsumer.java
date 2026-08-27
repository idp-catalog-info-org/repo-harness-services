/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(SPG)
@Singleton
public class PipelineExecutionSummaryRedisEventConsumer extends DebeziumAbstractRedisConsumer {
  private final FeatureFlagService featureFlagService;
  private final boolean redisShortCircuit;

  @Inject
  public PipelineExecutionSummaryRedisEventConsumer(
      @Named(PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER) Consumer redisConsumer, QueueController queueController,
      PipelineExecutionSummaryChangeEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache, FeatureFlagService featureFlagService,
      @Named("planExecutionsSummaryRedisShortCircuit") boolean redisShortCircuit) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
    this.featureFlagService = featureFlagService;
    this.redisShortCircuit = redisShortCircuit;
  }

  /**
   * Short-circuits Redis processing only when BOTH conditions are true:
   * <ol>
   *   <li>FF {@code PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY} is ON — Kafka consumer is actively processing.</li>
   *   <li>Config {@code cdcKafkaConfig.consumers.planExecutionsSummary.redisShortCircuit=true} — explicitly
   *       confirms the operator is ready to stop Redis. Separate from the FF so the Kafka consumer can
   *       run and be verified before Redis is stopped.</li>
   * </ol>
   */
  @Override
  protected boolean processMessage(Message message) {
    try {
      if (redisShortCircuit && featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY)) {
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed to evaluate short-circuit conditions for planExecutionsSummary Redis consumer, "
              + "falling back to Redis processing: {}",
          e.getMessage());
    }
    return super.processMessage(message);
  }
}
