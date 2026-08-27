/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.beans.FeatureName.PIPE_AUTO_ABORT_STUCK_EXECUTIONS;
import static io.harness.engine.executions.node.StuckNodeExecutionsMonitor.STUCK_NODE_EXECUTIONS_COUNTER;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class StuckNodeExecutionsMonitorTest extends OrchestrationTestBase {
  @Mock private InterruptManager interruptManager;
  @Mock private MetricService metricService;
  @Mock private PmsFeatureFlagHelper featureFlagService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Inject @InjectMocks StuckNodeExecutionsMonitor stuckNodeExecutionsMonitor;

  @Before
  public void beforeTest() {
    doReturn(mock(Interrupt.class)).when(interruptManager).register(any(InterruptPackage.class));
    doNothing().when(metricService).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    doNothing().when(nodeExecutionService).markNodesProcessing(any(), anyBoolean());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldBeNoopIfConfigIsNotEnabled() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecutionId")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();
    stuckNodeExecutionsMonitor.handle(nodeExecution);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
    verify(metricService).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldRegisterInterruptIfFFIsEnabled() {
    var accountId = UUID.randomUUID();
    doReturn(true).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecutionId")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();
    stuckNodeExecutionsMonitor.handle(nodeExecution);
    verify(interruptManager).register(any(InterruptPackage.class));
    verify(metricService).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void registerInterruptShouldHaveCreatedDate() {
    var accountId = UUID.randomUUID();
    doReturn(true).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecutionId")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();
    stuckNodeExecutionsMonitor.handle(nodeExecution);
    ArgumentCaptor<InterruptPackage> captor = ArgumentCaptor.forClass(InterruptPackage.class);
    verify(interruptManager).register(captor.capture());
    verify(metricService).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);

    InterruptPackage captured = captor.getValue();
    assertThat(captured.getInterruptConfig().getIssuedBy().hasIssueTime()).isTrue();
    assertThat(captured.getInterruptConfig().getIssuedBy().getIssueTime().getSeconds()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldTrackNewStuckExecutionWhenAutoAbortIsDisabled() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    stuckNodeExecutionsMonitor.handle(nodeExecution);

    // First execution should increment the counter
    verify(metricService, times(1)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldNotIncrementMetricForDuplicateStuckExecution() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    // Handle the same execution twice
    stuckNodeExecutionsMonitor.handle(nodeExecution);
    stuckNodeExecutionsMonitor.handle(nodeExecution);

    // Metric should only be incremented once for the first occurrence
    verify(metricService, times(1)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldTrackMultipleUniqueStuckExecutions() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution1 =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    var nodeExecution2 =
        NodeExecution.builder()
            .uuid("uuid2")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution2")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432124L)
            .build();

    var nodeExecution3 =
        NodeExecution.builder()
            .uuid("uuid3")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution3")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432125L)
            .build();

    stuckNodeExecutionsMonitor.handle(nodeExecution1);
    stuckNodeExecutionsMonitor.handle(nodeExecution2);
    stuckNodeExecutionsMonitor.handle(nodeExecution3);

    // Metric should be incremented for each unique execution
    verify(metricService, times(3)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldIncrementMetricForNewExecutionsButNotDuplicates() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution1 =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    var nodeExecution2 =
        NodeExecution.builder()
            .uuid("uuid2")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution2")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432124L)
            .build();

    // Handle execution 1 twice and execution 2 once
    stuckNodeExecutionsMonitor.handle(nodeExecution1);
    stuckNodeExecutionsMonitor.handle(nodeExecution1); // duplicate
    stuckNodeExecutionsMonitor.handle(nodeExecution2);

    // Metric should be incremented only twice (once for each unique execution)
    verify(metricService, times(2)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldHandleMixOfAutoAbortEnabledAndDisabled() {
    var accountId1 = UUID.randomUUID();
    var accountId2 = UUID.randomUUID();

    // Auto-abort enabled for account1, disabled for account2
    doReturn(true).when(featureFlagService).isEnabled(accountId1.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);
    doReturn(false).when(featureFlagService).isEnabled(accountId2.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecutionWithAbort =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId1.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    var nodeExecutionWithoutAbort =
        NodeExecution.builder()
            .uuid("uuid2")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution2")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId2.toString())
                                  .build())
            .processingEventStartedAt(23432124L)
            .build();

    stuckNodeExecutionsMonitor.handle(nodeExecutionWithAbort);
    stuckNodeExecutionsMonitor.handle(nodeExecutionWithoutAbort);

    // Both should increment metrics
    verify(metricService, times(2)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    // Only the first one should register an interrupt
    verify(interruptManager, times(1)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldTrackAtPlanExecutionLevel() {
    var accountId = UUID.randomUUID();
    doReturn(false).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    // Two different node executions with the same planExecutionId
    var nodeExecution1 =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("samePlanExecution")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    var nodeExecution2 =
        NodeExecution.builder()
            .uuid("uuid2") // Different UUID
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("samePlanExecution") // Same plan execution
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432124L)
            .build();

    stuckNodeExecutionsMonitor.handle(nodeExecution1);
    stuckNodeExecutionsMonitor.handle(nodeExecution2);

    // Metric should only be incremented once since they share the same planExecutionId
    verify(metricService, times(1)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(0)).register(any(InterruptPackage.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldMarkNodeAsNotProcessingOnException() {
    var accountId = UUID.randomUUID();
    doReturn(true).when(featureFlagService).isEnabled(accountId.toString(), PIPE_AUTO_ABORT_STUCK_EXECUTIONS);

    var nodeExecution =
        NodeExecution.builder()
            .uuid("uuid1")
            .executionContext(ExecutionContext.newBuilder()
                                  .setPlanExecutionId("planExecution1")
                                  .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId.toString())
                                  .build())
            .processingEventStartedAt(23432123L)
            .build();

    // Make interruptManager throw an exception
    doThrow(new RuntimeException("Test exception")).when(interruptManager).register(any(InterruptPackage.class));

    stuckNodeExecutionsMonitor.handle(nodeExecution);

    // Verify that nodeExecutionService.markNodesProcessing was called to mark as not processing
    verify(nodeExecutionService, times(1)).markNodesProcessing(Collections.singletonList("uuid1"), false);
    // Metric should still be incremented even when exception occurs
    verify(metricService, times(1)).incCounter(STUCK_NODE_EXECUTIONS_COUNTER);
    verify(interruptManager, times(1)).register(any(InterruptPackage.class));
  }
}
