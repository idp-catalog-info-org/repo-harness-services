/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Optional;

/**
 * Enum to represent different workload types for the Pipeline Service.
 * This is used to configure which components of the service should be active.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public enum WorkloadType {
  /**
   * All workload components are active (default).
   */
  ALL,

  /**
   * Only engine components are active that are related to orchestration (e.g., iterators, Redis/Kafka consumers).
   */
  ORCHESTRATION_ENGINE,

  /**
   * graph-related consumer + all api components are active.
   */
  GRAPH;

  private static final WorkloadType CURRENT_WORKLOAD_TYPE = fromEnvironment();

  /**
   * Gets the current workload type from the environment variable.
   * Defaults to ALL if not set or if an invalid value is provided.
   *
   * @return the current WorkloadType
   */
  public static WorkloadType current() {
    return CURRENT_WORKLOAD_TYPE;
  }

  /**
   * Checks if the current workload type matches any of the provided types.
   *
   * @param types the types to check against
   * @return true if the current type matches any of the provided types
   */
  public static boolean isAnyOf(WorkloadType... types) {
    for (WorkloadType type : types) {
      if (CURRENT_WORKLOAD_TYPE == type) {
        return true;
      }
    }
    return false;
  }

  private static WorkloadType fromEnvironment() {
    String envValue = Optional.ofNullable(System.getenv("WORKLOAD_TYPE")).orElse("ALL");
    try {
      return WorkloadType.valueOf(envValue.toUpperCase());
    } catch (IllegalArgumentException e) {
      // Default to ALL if invalid value
      return ALL;
    }
  }
}
