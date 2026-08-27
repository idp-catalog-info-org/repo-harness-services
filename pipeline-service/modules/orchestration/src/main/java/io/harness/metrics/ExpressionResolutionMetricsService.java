/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExpressionResolutionMetricsService {
  public static final String METRIC_EXPRESSION_RESOLUTION_COUNT = "expression_resolution_count";
  public static final String METRIC_EXPRESSION_RESOLUTION_DURATION_MS = "expression_resolution_duration_ms";

  public static final String STATUS_SUCCESS = "success";
  public static final String STATUS_FAILURE = "failure";

  private final MetricService metricService;

  public void recordExpressionResolution(String accountId, String status, Duration duration) {
    try (ExpressionResolutionMetricContext ignore = ExpressionResolutionMetricContext.build(accountId, status)) {
      metricService.incCounter(METRIC_EXPRESSION_RESOLUTION_COUNT);
      if (duration != null) {
        metricService.recordDuration(METRIC_EXPRESSION_RESOLUTION_DURATION_MS, duration);
      }
    } catch (Exception ex) {
      log.warn("Failed to record expression resolution metrics", ex);
    }
  }
}
