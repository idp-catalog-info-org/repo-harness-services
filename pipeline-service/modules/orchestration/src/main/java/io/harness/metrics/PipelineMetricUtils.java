/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ErrorCode;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PipelineMetricUtils {
  @Inject MetricService metricService;
  public void publishPipelineExecutionMetrics(
      String metricName, Status status, String accountId, String edition, ErrorCode errorCode) {
    ImmutableMap<String, String> metricContextMap =
        ImmutableMap.<String, String>builder()
            .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountId)
            .put(PmsEventMonitoringConstants.STATUS, status.toString())
            .put(PmsEventMonitoringConstants.EDITION, edition)
            .put(PmsEventMonitoringConstants.ERROR_CODE,
                StatusUtils.brokeStatuses().contains(status) && errorCode.getStatus().getCode() >= 500
                    ? errorCode.toString()
                    : "")
            .build();

    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.incCounter(metricName);
    }
  }

  public void publishStepExecutionMetrics(String metricName, StepType stepType, Status status, String module) {
    ImmutableMap<String, String> metricContextMap = ImmutableMap.<String, String>builder()
                                                        .put(PmsEventMonitoringConstants.STATUS, status.toString())
                                                        .put(PmsEventMonitoringConstants.STEP_TYPE, stepType.getType())
                                                        .put(PmsEventMonitoringConstants.MODULE, module)
                                                        .build();

    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.incCounter(metricName);
    }
  }

  public void publishStepTimeTakenMetrics(
      String metricName, String accountId, StepType stepType, String module, Long value) {
    ImmutableMap<String, String> metricContextMap =
        ImmutableMap.<String, String>builder()
            .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountId)
            .put(PmsEventMonitoringConstants.STEP_TYPE, stepType.getType())
            .put(PmsEventMonitoringConstants.STEP_CATEGORY, stepType.getStepCategory().name())
            .put(PmsEventMonitoringConstants.MODULE, module)
            .build();

    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricName, value);
    }
  }
}
