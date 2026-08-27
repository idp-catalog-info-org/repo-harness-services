/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage.kafka;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Pipeline-owned constants for CDC Kafka consumers. Kept local to the pipeline-service
 * (not aliasing any cross-module registry) so pipeline fully owns the topic/consumer-group
 * naming for its CDC streams.
 *
 * <p>Topic and consumer-group names here MUST match the infrastructure-side definitions
 * in the {@code kafka-resources} (KafkaTopic CRs + ACLs) and {@code kafka-connect-strimzi}
 * (Debezium connector) repositories for the corresponding environment.
 */
@OwnedBy(PIPELINE)
@UtilityClass
public class CdcKafkaConstants {
  /** Kafka topic where Debezium publishes planExecutionsSummary change events. */
  public static final String PLAN_EXECUTIONS_SUMMARY_TOPIC = "pmsMongo.pms-harness.planExecutionsSummary";

  /**
   * Consumer group used by the Kafka CDC consumer. Distinct from any other consumer group
   * on the same topic so its offsets and partition assignment are independent.
   */
  public static final String PLAN_EXECUTIONS_SUMMARY_CONSUMER_GROUP = "pipeline-plan-executions-summary-cdc";

  /** Guice {@code @Named} key for the dedicated CDC Kafka executor service. */
  public static final String CDC_KAFKA_EXECUTOR_SERVICE = "CdcKafkaExecutorService";
}
