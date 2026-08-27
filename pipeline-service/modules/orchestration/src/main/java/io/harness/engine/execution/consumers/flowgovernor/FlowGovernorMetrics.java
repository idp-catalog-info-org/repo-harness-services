/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Metric names and label keys emitted by the pipeline-execution-events flow governor.
 * Definitions live under
 * {@code
 * pipeline-service/service/src/main/resources/metrics/metricDefinitions/pipeline_execution_events_flow_governor_metrics.yaml}.
 */
@OwnedBy(PIPELINE)
@UtilityClass
public class FlowGovernorMetrics {
  public static final String INVOKED = "pipeline_execution_events_flow_governor_invoked";
  public static final String RPS_ACTUAL = "pipeline_execution_events_throttled_mode_rps_actual";
  public static final String RPS_EXPECTED = "pipeline_execution_events_throttled_mode_rps_expected";
  public static final String QUEUE_DEPTH = "pipeline_execution_events_flow_governor_queue_depth";
  public static final String PAUSE_RESUME = "pipeline_execution_events_flow_governor_pause_resume";

  public static final String LABEL_MODE = "mode";
  public static final String LABEL_TOPIC = "topic";
}
