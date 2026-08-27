/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.observers;

import static io.harness.eraro.ErrorCode.INTERNAL_SERVER_ERROR;
import static io.harness.eraro.ErrorCode.REQUEST_TIMEOUT;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.licensing.Edition;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionMetricsObserverTest extends CategoryTest {
  @Mock PipelineMetricUtils pipelineMetricUtils;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock MetricService metricService;
  @InjectMocks PipelineExecutionMetricsObserver pipelineExecutionMetricsObserver;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testOnEnd() {
    String accountId = UUIDGenerator.generateUuid();
    doReturn(Edition.ENTERPRISE.name()).when(pipelineSettingsService).getAccountEdition(accountId);
    pipelineExecutionMetricsObserver.onEnd(
        Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, accountId).build(),
        Status.SUCCEEDED);
    verify(pipelineMetricUtils)
        .publishPipelineExecutionMetrics("pipeline_execution_end_count", Status.SUCCEEDED, accountId,
            Edition.ENTERPRISE.name(), ErrorCode.DEFAULT_ERROR_CODE);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testOnEndWithStatusFailed() {
    String accountId = UUIDGenerator.generateUuid();
    String planExecutionId = UUIDGenerator.generateUuid();
    doReturn(Edition.ENTERPRISE.name()).when(pipelineSettingsService).getAccountEdition(accountId);
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("Internal server error test")
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(INTERNAL_SERVER_ERROR.name())
                                                 .setMessage("request time out")
                                                 .build())
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(REQUEST_TIMEOUT.name())
                                                 .setMessage("internal server error")
                                                 .build())
                             .build())
            .build();
    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(planExecutionId, Collections.singleton(NodeExecutionKeys.failureInfo));
    pipelineExecutionMetricsObserver.onEnd(Ambiance.newBuilder()
                                               .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                               .setPlanExecutionId(planExecutionId)
                                               .build(),
        Status.FAILED);
    verify(pipelineMetricUtils)
        .publishPipelineExecutionMetrics(
            "pipeline_execution_end_count", Status.FAILED, accountId, Edition.ENTERPRISE.name(), INTERNAL_SERVER_ERROR);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testOnEndRecordsTriggerExecutorPipelineExecution() {
    String accountId = UUIDGenerator.generateUuid();
    doReturn(Edition.ENTERPRISE.name()).when(pipelineSettingsService).getAccountEdition(accountId);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
            .setMetadata(ExecutionMetadata.newBuilder().setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggeredBy(
                TriggeredBy.newBuilder().putExtraInfo(PmsEventMonitoringConstants.EXECUTOR_TYPE, "SERVICE_ACCOUNT"))))
            .build();

    pipelineExecutionMetricsObserver.onEnd(ambiance, Status.SUCCEEDED);

    verify(metricService).incCounter("trigger_executor_pipeline_execution_end_count");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testAccessDeniedFailureReason() {
    FailureData accessDeniedFailure = FailureData.newBuilder().setCode(ErrorCode.NG_ACCESS_DENIED.name()).build();

    assertThat(pipelineExecutionMetricsObserver.getFailureReason(
                   Status.FAILED, Collections.singletonList(accessDeniedFailure)))
        .isEqualTo("ACCESS_DENIED");
  }
}