/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionBackfillServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks private NodeExecutionBackfillServiceImpl nodeExecutionBackfillService;

  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String MODULE_CD = "CD";
  private static final String MODULE_CI = "CI";

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutionEvents_WithModuleFilter() {
    NodeExecution cdNodeExecution = createNodeExecution(MODULE_CD);
    NodeExecution ciNodeExecution = createNodeExecution(MODULE_CI);

    Stream<NodeExecution> nodeExecutionStream = Stream.of(cdNodeExecution, ciNodeExecution);

    when(nodeExecutionService.fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID), any(Set.class)))
        .thenReturn(nodeExecutionStream);

    nodeExecutionBackfillService.replayNodeExecutionEvents(PLAN_EXECUTION_ID, MODULE_CD);

    verify(nodeExecutionService)
        .fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID),
            eq(Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                NodeExecutionKeys.endTs)));

    verify(nodeExecutionService, times(1))
        .emitEvent(eq(cdNodeExecution), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));

    verify(nodeExecutionService, never())
        .emitEvent(eq(ciNodeExecution), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutionEvents_WithoutModuleFilter() {
    NodeExecution cdNodeExecution = createNodeExecution(MODULE_CD);
    NodeExecution ciNodeExecution = createNodeExecution(MODULE_CI);

    Stream<NodeExecution> nodeExecutionStream = Stream.of(cdNodeExecution, ciNodeExecution);

    when(nodeExecutionService.fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID), any(Set.class)))
        .thenReturn(nodeExecutionStream);

    nodeExecutionBackfillService.replayNodeExecutionEvents(PLAN_EXECUTION_ID, null);

    verify(nodeExecutionService)
        .fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID),
            eq(Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                NodeExecutionKeys.endTs)));

    verify(nodeExecutionService, never())
        .emitEvent(any(), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutionEvents_WithEmptyModuleFilter() {
    // Given
    NodeExecution cdNodeExecution = createNodeExecution(MODULE_CD);
    NodeExecution ciNodeExecution = createNodeExecution(MODULE_CI);

    Stream<NodeExecution> nodeExecutionStream = Stream.of(cdNodeExecution, ciNodeExecution);

    when(nodeExecutionService.fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID), any(Set.class)))
        .thenReturn(nodeExecutionStream);

    nodeExecutionBackfillService.replayNodeExecutionEvents(PLAN_EXECUTION_ID, "");

    verify(nodeExecutionService)
        .fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID),
            eq(Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                NodeExecutionKeys.endTs)));

    verify(nodeExecutionService, never())
        .emitEvent(any(), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutionEvents_WithCaseInsensitiveModuleFilter() {
    NodeExecution cdNodeExecution = createNodeExecution(MODULE_CD);
    NodeExecution ciNodeExecution = createNodeExecution(MODULE_CI);

    Stream<NodeExecution> nodeExecutionStream = Stream.of(cdNodeExecution, ciNodeExecution);

    when(nodeExecutionService.fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID), any(Set.class)))
        .thenReturn(nodeExecutionStream);

    nodeExecutionBackfillService.replayNodeExecutionEvents(PLAN_EXECUTION_ID, "cd");

    verify(nodeExecutionService)
        .fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID),
            eq(Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                NodeExecutionKeys.endTs)));

    verify(nodeExecutionService, times(1))
        .emitEvent(eq(cdNodeExecution), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));
    verify(nodeExecutionService, never())
        .emitEvent(eq(ciNodeExecution), eq(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE), eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutionEvents_WithNoMatchingNodeExecutions() {
    Stream<NodeExecution> emptyStream = Stream.empty();

    when(nodeExecutionService.fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID), any(Set.class))).thenReturn(emptyStream);

    nodeExecutionBackfillService.replayNodeExecutionEvents(PLAN_EXECUTION_ID, MODULE_CD);

    verify(nodeExecutionService)
        .fetchAllNodeExecutions(eq(PLAN_EXECUTION_ID),
            eq(Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                NodeExecutionKeys.endTs)));

    verify(nodeExecutionService, never())
        .emitEvent(any(NodeExecution.class), any(OrchestrationEventType.class), anyBoolean());
  }

  private NodeExecution createNodeExecution(String module) {
    return NodeExecution.builder()
        .uuid(generateUuid())
        .nodeType(NodeType.IDENTITY_PLAN_NODE.toString())
        .ambiance(AmbianceTestUtils.buildAmbiance())
        .nodeId(generateUuid())
        .name("test-node")
        .identifier("test-identifier")
        .module(module)
        .startTs(System.currentTimeMillis())
        .status(Status.SUCCEEDED)
        .build();
  }
}
