/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.concurrency;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class MaxConcurrentChildCallbackTest extends OrchestrationTestBase {
  private static final String PARENT_NODE_EXECUTION_ID = "parentExecutionId";
  private static final String PUBLISHER_NAME = "publisher";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock OrchestrationEngine engine;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock NodeExecutionInfoService nodeExecutionInfoService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;

  MaxConcurrentChildCallback maxConcurrentChildCallback;
  @Before
  public void setUp() throws IllegalAccessException {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();
    executionSweepingOutputService = mock(ExecutionSweepingOutputService.class);
    maxConcurrentChildCallback = MaxConcurrentChildCallback.builder()
                                     .ambiance(ambiance)
                                     .planExecutionId(ambiance.getPlanExecutionId())
                                     .parentNodeExecutionId(PARENT_NODE_EXECUTION_ID)
                                     .engine(engine)
                                     .executionSweepingOutputService(executionSweepingOutputService)
                                     .nodeExecutionService(nodeExecutionService)
                                     .nodeExecutionInfoService(nodeExecutionInfoService)
                                     .maxConcurrency(2)
                                     .pmsFeatureFlagHelper(pmsFeatureFlagHelper)
                                     .publisherName(PUBLISHER_NAME)
                                     .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testNotifyIfNullChildInstance() {
    when(nodeExecutionInfoService.incrementCursor(PARENT_NODE_EXECUTION_ID, Status.SUCCEEDED)).thenReturn(null);
    maxConcurrentChildCallback.notify(new HashMap<>());
    verify(nodeExecutionService).errorOutActiveNodes(anyString());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testNotify() {
    when(nodeExecutionInfoService.incrementCursor(PARENT_NODE_EXECUTION_ID, Status.SUCCEEDED))
        .thenReturn(ConcurrentChildInstance.builder()
                        .cursor(1)
                        .childrenNodeExecutionIds(Lists.newArrayList("a", "b", "c"))
                        .build());
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).status(Status.QUEUED).uuid("b").build();
    when(nodeExecutionService.getWithFieldsIncluded("b", NodeProjectionUtils.withAmbianceAndStatus))
        .thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).build();
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(optionalSweepingOutput);
    maxConcurrentChildCallback.notify(new HashMap<>());
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testshouldSkipNodeExecution() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setPlanExecutionId(PLAN_EXECUTION_ID).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).status(Status.QUEUED).uuid("b").build();
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).build();
    when(pmsFeatureFlagHelper.isEnabled("accountId", FeatureName.PIE_STEP_GROUP_SKIP_ON_LOOPING_STRATEGY))
        .thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(optionalSweepingOutput);
    Status status = Status.FAILED;
    ConcurrentChildInstance concurrentChildInstance = ConcurrentChildInstance.builder().build();
    boolean result = maxConcurrentChildCallback.shouldSkipNodeExecution(status, concurrentChildInstance, nodeExecution);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testShouldNotSkipNodeExecutionWhenUnderRollbackSteps() {
    List<Level> levels = new ArrayList<>();
    Level l1 = Level.newBuilder().setIdentifier("i1").build();
    Level l2 = Level.newBuilder().setIdentifier(YAMLFieldNameConstants.ROLLBACK_STEPS).build();
    Level l3 = Level.newBuilder().setIdentifier("i3").build();
    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    Ambiance rollbackAmbiance = Ambiance.newBuilder()
                                    .putAllSetupAbstractions(setupAbstractions)
                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                    .addAllLevels(levels)
                                    .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().ambiance(rollbackAmbiance).status(Status.QUEUED).uuid("b").build();
    when(pmsFeatureFlagHelper.isEnabled("accountId", FeatureName.PIE_STEP_GROUP_SKIP_ON_LOOPING_STRATEGY))
        .thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(rollbackAmbiance);

    boolean result = maxConcurrentChildCallback.shouldSkipNodeExecution(
        Status.FAILED, ConcurrentChildInstance.builder().build(), nodeExecution);

    assertThat(result).isFalse();
    verify(nodeExecutionService).getAmbiance(nodeExecution);
    verify(executionSweepingOutputService, never()).resolveOptional(any(), any());

    levels.remove(l2);
    rollbackAmbiance = Ambiance.newBuilder()
                           .putAllSetupAbstractions(setupAbstractions)
                           .setPlanExecutionId(PLAN_EXECUTION_ID)
                           .addAllLevels(levels)
                           .build();
    nodeExecution = NodeExecution.builder().ambiance(rollbackAmbiance).status(Status.QUEUED).uuid("b").build();
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(rollbackAmbiance);
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).build();
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(optionalSweepingOutput);
    result = maxConcurrentChildCallback.shouldSkipNodeExecution(
        Status.FAILED, ConcurrentChildInstance.builder().build(), nodeExecution);

    assertThat(result).isTrue();
  }
}
