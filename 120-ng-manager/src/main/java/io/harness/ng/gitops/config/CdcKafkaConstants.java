/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.config;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * GitOps-owned constants for CDC Kafka consumers. Kept local to ng-manager
 * (not aliasing any cross-module registry) so GitOps fully owns the topic/consumer-group
 * naming for its CDC streams.
 *
 * <p>Topic and consumer-group names here MUST match the infrastructure-side definitions
 * in the {@code kafka-resources} (KafkaTopic CRs + ACLs) and {@code kafka-connect-strimzi}
 * (Debezium connector) repositories for the corresponding environment.
 */
@OwnedBy(GITOPS)
@UtilityClass
public class CdcKafkaConstants {
  /** Kafka topic where Debezium publishes utilization_snapshot change events. */
  public static final String UTILIZATION_SNAPSHOT_TOPIC = "gitops.harness-gitops.utilization_snapshot";

  /**
   * Consumer group used by the Kafka CDC consumer. Distinct from any other consumer group
   * on the same topic so its offsets and partition assignment are independent.
   */
  public static final String UTILIZATION_SNAPSHOT_CONSUMER_GROUP = "harness-gitops-utilization-consumer";

  /** Kafka topic where Debezium publishes applications change events. */
  public static final String APPLICATIONS_TOPIC = "gitops.harness-gitops.applications";

  /**
   * Consumer group used by the applications Kafka CDC consumer. Distinct from any other consumer group
   * on the same topic so its offsets and partition assignment are independent.
   */
  public static final String APPLICATIONS_CONSUMER_GROUP = "harness-gitops-applications-consumer";

  /**
   * Guice {@code @Named} keys for dedicated CDC Kafka executor services.
   * Each consumer has its own executor to prevent thread starvation when one topic has high volume.
   */
  public static final String UTILIZATION_SNAPSHOT_EXECUTOR = "CdcKafkaUtilizationSnapshotExecutor";
  public static final String APPLICATIONS_EXECUTOR = "CdcKafkaApplicationsExecutor";
}