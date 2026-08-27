/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.recorder.OperationTimeMetricRecorder;
import io.harness.metrics.service.api.MetricService;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Metrics helper for ng-manager entity_crud stream listeners.
 * Records per-event processing time labeled by entityType and action.
 */
@OwnedBy(PL)
@UtilityClass
@Slf4j
public class EntityCRUDStreamListenerMetrics {
  public static final String PROCESS_TIME_METRIC = "ng_manager_entity_crud_listener_process_time";

  public static final String LABEL_ENTITY_TYPE = "entityType";
  public static final String LABEL_ACTION = "action";
  public static final String LABEL_STATUS = "status";

  public static final String STATUS_SUCCESS = "success";
  public static final String STATUS_FAILURE = "failure";
  public static final String UNKNOWN = "unknown";

  /**
   * Executes {@code operation} and records wall-clock duration to
   * {@link #PROCESS_TIME_METRIC} with entityType/action/status labels.
   */
  public static <T> T executeWithMetrics(
      String entityType, String action, MetricService metricService, Supplier<T> operation) {
    if (metricService == null) {
      log.warn("MetricService is null, skipping process-time metric for entityType={} action={}", entityType, action);
      return operation.get();
    }

    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_ENTITY_TYPE, isEmpty(entityType) ? UNKNOWN : entityType);
    labels.put(LABEL_ACTION, isEmpty(action) ? UNKNOWN : action);
    labels.put(LABEL_STATUS, STATUS_SUCCESS);

    try (OperationTimeMetricRecorder recorder =
             new OperationTimeMetricRecorder(PROCESS_TIME_METRIC, labels, metricService)) {
      try {
        return operation.get();
      } catch (RuntimeException e) {
        labels.put(LABEL_STATUS, STATUS_FAILURE);
        throw e;
      }
    }
  }
}
