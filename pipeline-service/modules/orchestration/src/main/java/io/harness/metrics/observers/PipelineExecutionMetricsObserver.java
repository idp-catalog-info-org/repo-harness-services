/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.observers;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.eraro.ErrorCode;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.metrics.service.api.MetricService;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
public class PipelineExecutionMetricsObserver implements OrchestrationEndObserver, AsyncInformObserver {
  private static final String PIPELINE_EXECUTION_END_COUNT = "pipeline_execution_end_count";
  private static final String TRIGGER_EXECUTOR_PIPELINE_EXECUTION_END_COUNT =
      "trigger_executor_pipeline_execution_end_count";
  private static final String ACCESS_DENIED = "ACCESS_DENIED";
  private static final String OTHER = "OTHER";
  private static final String NONE = "NONE";

  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject PipelineMetricUtils pipelineMetricUtils;
  @Inject PipelineSettingsService pipelineSettingsService;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject MetricService metricService;

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    // Update pipeline execution metrics for end
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String accountEdition = pipelineSettingsService.getAccountEdition(accountId);
    List<FailureData> failureDataList = getFailureData(ambiance);
    ErrorCode errorCode = getErrorCode(failureDataList);

    pipelineMetricUtils.publishPipelineExecutionMetrics(
        PIPELINE_EXECUTION_END_COUNT, endStatus, accountId, accountEdition, errorCode);
    recordTriggerExecutorPipelineExecutionEnd(ambiance, endStatus, accountId, failureDataList);
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }

  private List<FailureData> getFailureData(Ambiance ambiance) {
    String planExecutionId = AmbianceUtils.getPipelineExecutionIdentifier(ambiance);
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return Collections.emptyList();
    }
    Optional<NodeExecution> nodeExecution = nodeExecutionService.getPipelineNodeExecutionWithProjections(
        planExecutionId, Collections.singleton(NodeExecutionKeys.failureInfo));
    if (nodeExecution.isEmpty() || nodeExecution.get().getFailureInfo() == null
        || EmptyPredicate.isEmpty(nodeExecution.get().getFailureInfo().getFailureDataList())) {
      return Collections.emptyList();
    }
    return nodeExecution.get().getFailureInfo().getFailureDataList();
  }

  private ErrorCode getErrorCode(List<FailureData> failureDataList) {
    ErrorCode errorCode = ErrorCode.DEFAULT_ERROR_CODE;
    for (FailureData failureData : failureDataList) {
      int statusCode = ErrorCode.valueOf(failureData.getCode()).getStatus().getCode();
      if (statusCode >= 500) {
        errorCode = ErrorCode.valueOf(failureData.getCode());
        break;
      }
    }
    return errorCode;
  }

  private void recordTriggerExecutorPipelineExecutionEnd(
      Ambiance ambiance, Status endStatus, String accountId, List<FailureData> failureDataList) {
    if (ambiance.getMetadata() == null || !ambiance.getMetadata().hasTriggerInfo()
        || !ambiance.getMetadata().getTriggerInfo().hasTriggeredBy()) {
      return;
    }
    String executorType = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoOrDefault(
        PmsEventMonitoringConstants.EXECUTOR_TYPE, null);
    if (EmptyPredicate.isEmpty(executorType)) {
      return;
    }

    String failureReason = getFailureReason(endStatus, failureDataList);
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(Map.of(PmsEventMonitoringConstants.ACCOUNT_ID,
             accountId, PmsEventMonitoringConstants.EXECUTOR_TYPE, executorType, PmsEventMonitoringConstants.STATUS,
             endStatus.name(), PmsEventMonitoringConstants.FAILURE_REASON, failureReason))) {
      metricService.incCounter(TRIGGER_EXECUTOR_PIPELINE_EXECUTION_END_COUNT);
    } catch (Exception e) {
      log.warn("Failed to record trigger executor pipeline execution end metric", e);
    }
  }

  String getFailureReason(Status endStatus, List<FailureData> failureDataList) {
    if (failureDataList.stream()
            .map(FailureData::getCode)
            .anyMatch(code
                -> ErrorCode.ACCESS_DENIED.name().equals(code) || ErrorCode.NG_ACCESS_DENIED.name().equals(code))) {
      return ACCESS_DENIED;
    }
    return StatusUtils.brokeStatuses().contains(endStatus) ? OTHER : NONE;
  }
}
