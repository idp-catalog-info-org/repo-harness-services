/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.rule.OwnerRule.SAKSHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutTracker;
import io.harness.timeout.engine.TimeoutEngine;

import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineTimeoutUpdateHandlerTest extends CategoryTest {
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private TimeoutEngine timeoutEngine;

  @InjectMocks private PipelineTimeoutUpdateHandler handler;

  private static final String PLAN_EXECUTION_ID = "planExecId";
  private static final String TIMEOUT_INSTANCE_ID = "timeoutId1";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private NodeExecution pipelineNodeExecution(List<String> timeoutInstanceIds) {
    Level level =
        Level.newBuilder()
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("PIPELINE").build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).addLevels(level).build();
    return NodeExecution.builder().ambiance(ambiance).timeoutInstanceIds(timeoutInstanceIds).build();
  }

  private NodeExecution stageNodeExecution() {
    Level level = Level.newBuilder()
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
                      .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).addLevels(level).build();
    return NodeExecution.builder().ambiance(ambiance).build();
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_stampsTimeoutWhenPipelineNodeHasTimeout() {
    long expiryTs = 9_000_000_000L;
    TimeoutTracker tracker = mock(TimeoutTracker.class);
    when(tracker.getExpiryTime()).thenReturn(expiryTs);
    TimeoutInstance instance = TimeoutInstance.builder().tracker(tracker).build();
    when(timeoutEngine.getTimeoutInstances(List.of(TIMEOUT_INSTANCE_ID))).thenReturn(List.of(instance));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    handler.onNodeStart(
        NodeStartInfo.builder().nodeExecution(pipelineNodeExecution(List.of(TIMEOUT_INSTANCE_ID))).build());

    verify(pmsExecutionSummaryService).update(eq(PLAN_EXECUTION_ID), updateCaptor.capture());
    Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(set.get(PlanExecutionSummaryKeys.pipelineTimeoutTs)).isEqualTo(expiryTs);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_picksEarliestExpiryAcrossMultipleTimeouts() {
    TimeoutTracker tracker1 = mock(TimeoutTracker.class);
    TimeoutTracker tracker2 = mock(TimeoutTracker.class);
    when(tracker1.getExpiryTime()).thenReturn(5_000L);
    when(tracker2.getExpiryTime()).thenReturn(3_000L);
    when(timeoutEngine.getTimeoutInstances(List.of("t1", "t2")))
        .thenReturn(List.of(
            TimeoutInstance.builder().tracker(tracker1).build(), TimeoutInstance.builder().tracker(tracker2).build()));

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    handler.onNodeStart(NodeStartInfo.builder().nodeExecution(pipelineNodeExecution(List.of("t1", "t2"))).build());

    verify(pmsExecutionSummaryService).update(eq(PLAN_EXECUTION_ID), updateCaptor.capture());
    Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(set.get(PlanExecutionSummaryKeys.pipelineTimeoutTs)).isEqualTo(3_000L);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_skipsUpdateForNonPipelineNode() {
    handler.onNodeStart(NodeStartInfo.builder().nodeExecution(stageNodeExecution()).build());
    verify(pmsExecutionSummaryService, never()).update(any(), any());
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_skipsUpdateWhenNoTimeoutInstanceIds() {
    handler.onNodeStart(NodeStartInfo.builder().nodeExecution(pipelineNodeExecution(Collections.emptyList())).build());
    verify(pmsExecutionSummaryService, never()).update(any(), any());
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_skipsUpdateWhenAllTrackersReturnNullExpiry() {
    TimeoutTracker tracker = mock(TimeoutTracker.class);
    when(tracker.getExpiryTime()).thenReturn(null);
    when(timeoutEngine.getTimeoutInstances(List.of(TIMEOUT_INSTANCE_ID)))
        .thenReturn(List.of(TimeoutInstance.builder().tracker(tracker).build()));

    handler.onNodeStart(
        NodeStartInfo.builder().nodeExecution(pipelineNodeExecution(List.of(TIMEOUT_INSTANCE_ID))).build());
    verify(pmsExecutionSummaryService, never()).update(any(), any());
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testOnNodeStart_skipsUpdateWhenNullNodeExecution() {
    handler.onNodeStart(NodeStartInfo.builder().nodeExecution(null).build());
    verify(pmsExecutionSummaryService, never()).update(any(), any());
  }
}
