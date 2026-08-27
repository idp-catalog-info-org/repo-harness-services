/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpressionFunctorMetricsHelper {
  public static final String METRIC_DURATION = "expression_functor_duration";
  public static final String METRIC_CALL_COUNT = "expression_functor_call_count";
  public static final String LABEL_FUNCTOR_TYPE = "functor_type";
  public static final String LABEL_RESULT = "result";

  public static final String RESULT_HIT = "hit";
  public static final String RESULT_MISS = "miss";
  public static final String RESULT_ERROR = "error";

  // Functor type constants
  public static final String FUNCTOR_OUTCOME = "outcome";
  public static final String FUNCTOR_SWEEPING_OUTPUT = "sweeping_output";
  public static final String FUNCTOR_ANCESTOR = "ancestor";
  public static final String FUNCTOR_CHILD = "child";
  public static final String FUNCTOR_QUALIFIED = "qualified";
  public static final String FUNCTOR_STRATEGY = "strategy";
  public static final String FUNCTOR_EXPANDED_JSON = "expanded_json";
  public static final String FUNCTOR_INPUT_SET = "input_set";

  public static void recordMetrics(MetricService metricService, String functorType, String result, Instant start) {
    if (metricService == null) {
      return;
    }
    try {
      Duration duration = Duration.between(start, Instant.now());
      Map<String, String> ctx = ImmutableMap.of(LABEL_FUNCTOR_TYPE, functorType, LABEL_RESULT, result);
      try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(ctx)) {
        metricService.recordDuration(METRIC_DURATION, duration);
        metricService.incCounter(METRIC_CALL_COUNT);
      }
    } catch (Exception ex) {
      // swallow - metrics should never break expression resolution
    }
  }
}
