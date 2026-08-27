/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.WorkflowGraph;
import io.harness.beans.WorkflowGraphNode;
import io.harness.beans.WorkflowGraphRelation;
import io.harness.beans.converter.EphemeralOrchestrationGraphConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.skip.service.VertexSkipperService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class ExecutionGraphServiceImplTest extends CategoryTest {
  @InjectMocks private ExecutionGraphServiceImpl executionGraphService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock GraphGenerationService graphGenerationService;
  @Mock OrchestrationGraphDTOConverter orchestrationGraphDTOConverter;
  @Mock EphemeralOrchestrationGraph ephemeralOrchestrationGraph;
  @Mock VertexSkipperService vertexSkipperService;
  private AutoCloseable mocks;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() throws IOException {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetNodeExecutionSubGraph() {
    when(graphGenerationService.getCachedOrchestrationGraphFromSecondary(any(), any(), any()))
        .thenReturn(getOrchestrationGraph());

    MockedStatic<EphemeralOrchestrationGraphConverter> aStatic =
        Mockito.mockStatic(EphemeralOrchestrationGraphConverter.class);
    aStatic.when(() -> EphemeralOrchestrationGraphConverter.convertFrom(any(OrchestrationGraph.class)))
        .thenReturn(EphemeralOrchestrationGraph.builder().build());
    MockedStatic<ExecutionGraphMapper> bStatic = Mockito.mockStatic(ExecutionGraphMapper.class);

    bStatic.when(() -> ExecutionGraphMapper.toExecutionGraph(any(OrchestrationGraphDTO.class)))
        .thenReturn(ExecutionGraph.builder().build());
    MockedStatic<OrchestrationGraphDTOConverter> cStatic = Mockito.mockStatic(OrchestrationGraphDTOConverter.class);
    cStatic.when(() -> OrchestrationGraphDTOConverter.convertFrom(any(EphemeralOrchestrationGraph.class)))
        .thenReturn(OrchestrationGraphDTO.builder().build());

    assertThat(executionGraphService.getNodeExecutionSubGraph(
                   "nodeExecutionId", "planExecutionId", ACCOUNT_ID, getTimeInMillisForTheDayBefore()))
        .isNotNull();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetNodeExecutionSubGraphFromNodeExecutions() {
    when(nodeExecutionService.get(any(String.class)))
        .thenReturn(NodeExecution.builder()
                        .group(StepCategory.STEP_GROUP.name())
                        .oldRetry(true)
                        .stepType(StepType.newBuilder().build())
                        .build());
    when(graphGenerationService.constructOldRetryGraph(any(), any(), any())).thenReturn(getOrchestrationGraph());
    assertThat(executionGraphService.getNodeExecutionSubGraph(
                   "nodeExecutionId", "planExecutionId", ACCOUNT_ID, getTimeInMillisForTheDayBefore()))
        .isNotNull();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetNodeExecutionSubGraphOfExpiredNodeExecutions() {
    assertThatThrownBy(()
                           -> executionGraphService.getNodeExecutionSubGraph(
                               "nodeExecutionId", "planExecutionId", ACCOUNT_ID, getTimeInMillisForTheDayBefore()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetWorkflowGraph() {
    // Setup
    String planExecutionId = "planExecutionId";
    String nodeExecutionId = "nodeExecutionId";
    int depth = 5;

    // Create mock workflow graph
    Map<String, WorkflowGraphNode> data = new HashMap<>();
    data.put(nodeExecutionId, WorkflowGraphNode.builder().uuid(nodeExecutionId).name("Test Node").build());

    Map<String, WorkflowGraphRelation> relation = new HashMap<>();
    relation.put(nodeExecutionId, WorkflowGraphRelation.builder().build());

    WorkflowGraph expectedGraph = WorkflowGraph.builder().data(data).relation(relation).build();

    // Mock service call
    when(graphGenerationService.generateWorkflowGraph(ACCOUNT_ID, planExecutionId, nodeExecutionId, depth))
        .thenReturn(expectedGraph);

    // Execute
    WorkflowGraph result = executionGraphService.getWorkflowGraph(planExecutionId, nodeExecutionId, depth, ACCOUNT_ID);

    // Verify
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedGraph);
    assertThat(result.getData()).containsKey(nodeExecutionId);
    assertThat(result.getRelation()).containsKey(nodeExecutionId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetWorkflowGraphWithNullPlanExecutionId() {
    // Execute and verify
    assertThatThrownBy(() -> executionGraphService.getWorkflowGraph(null, "nodeExecutionId", 5, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Plan execution ID cannot be null or empty");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetWorkflowGraphWithDefaultDepth() {
    // Setup
    String planExecutionId = "planExecutionId";
    String nodeExecutionId = "nodeExecutionId";
    int depth = 0; // Should use default depth

    // Create mock workflow graph
    WorkflowGraph expectedGraph = WorkflowGraph.builder().data(new HashMap<>()).relation(new HashMap<>()).build();

    // Mock service call - verify that default depth (10) is used
    when(graphGenerationService.generateWorkflowGraph(ACCOUNT_ID, planExecutionId, nodeExecutionId, 10))
        .thenReturn(expectedGraph);

    // Execute
    WorkflowGraph result = executionGraphService.getWorkflowGraph(planExecutionId, nodeExecutionId, depth, ACCOUNT_ID);

    // Verify
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedGraph);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckRequestedNodeTypeInNodeExecution_WithStepGroupAndOldRetry_NoException() {
    NodeExecution nodeExecution = NodeExecution.builder().group(StepCategory.STEP_GROUP.name()).oldRetry(true).build();
    // Should not throw any exception
    executionGraphService.checkRequestedNodeTypeInNodeExecution(
        nodeExecution, "planExecutionId", "nodeExecutionId", ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckRequestedNodeTypeInNodeExecution_WithGroupConstantAndOldRetry_NoException() {
    NodeExecution nodeExecution =
        NodeExecution.builder().group(NGCommonUtilPlanCreationConstants.GROUP).oldRetry(true).build();
    // Should not throw any exception
    executionGraphService.checkRequestedNodeTypeInNodeExecution(
        nodeExecution, "planExecutionId", "nodeExecutionId", ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckRequestedNodeTypeInNodeExecution_WithNonStepGroup_ThrowsException() {
    NodeExecution nodeExecution = NodeExecution.builder().group("STAGE").oldRetry(true).build();

    assertThatThrownBy(()
                           -> executionGraphService.checkRequestedNodeTypeInNodeExecution(
                               nodeExecution, "planExecutionId", "nodeExecutionId", ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input nodeExecutionId does not belong to step group");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckRequestedNodeTypeInNodeExecution_WithStepGroupNotOldRetry_ThrowsException() {
    NodeExecution nodeExecution = NodeExecution.builder().group("GROUP").oldRetry(false).build();

    assertThatThrownBy(()
                           -> executionGraphService.checkRequestedNodeTypeInNodeExecution(
                               nodeExecution, "planExecutionId", "nodeExecutionId", ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input nodeExecutionId does not belong to old failed step group");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckRequestedNodeTypeInNodeExecution_WithStepGroupNullOldRetry_ThrowsException() {
    NodeExecution nodeExecution = NodeExecution.builder().group(StepCategory.STEP_GROUP.name()).build();

    assertThatThrownBy(()
                           -> executionGraphService.checkRequestedNodeTypeInNodeExecution(
                               nodeExecution, "planExecutionId", "nodeExecutionId", ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input nodeExecutionId does not belong to old failed step group");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedSubGraph_CdcPathReturnsGraph() {
    when(graphGenerationService.getCdcSubGraph(ACCOUNT_ID, "planExecutionId", "nodeExecutionId"))
        .thenReturn(getOrchestrationGraph());

    assertThat(executionGraphService.getNodeExecutionSubGraph(
                   "nodeExecutionId", "planExecutionId", ACCOUNT_ID, getTimeInMillisForTheDayBefore()))
        .isNotNull();
    // Should not fall through to object store or NodeExecution paths
    verify(graphGenerationService, never()).getCachedOrchestrationGraphFromSecondary(any(), any(), any());
    verify(nodeExecutionService, never()).get(any(String.class));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedSubGraph_CdcPathReturnsNull_FallsThrough() {
    when(graphGenerationService.getCdcSubGraph(ACCOUNT_ID, "planExecutionId", "nodeExecutionId")).thenReturn(null);
    when(graphGenerationService.getCachedOrchestrationGraphFromSecondary(
             ACCOUNT_ID, "planExecutionId", "nodeExecutionId"))
        .thenReturn(getOrchestrationGraph());

    assertThat(executionGraphService.getNodeExecutionSubGraph(
                   "nodeExecutionId", "planExecutionId", ACCOUNT_ID, getTimeInMillisForTheDayBefore()))
        .isNotNull();
    // CDC returned null, so it should fall through to object store
    verify(graphGenerationService)
        .getCachedOrchestrationGraphFromSecondary(ACCOUNT_ID, "planExecutionId", "nodeExecutionId");
  }

  private OrchestrationGraph getOrchestrationGraph() {
    Map<String, GraphVertex> vertexMap = new HashMap<>();
    vertexMap.put("parent", GraphVertex.builder().build());
    vertexMap.put("child1", GraphVertex.builder().uuid("child1").build());
    vertexMap.put("child2", GraphVertex.builder().uuid("child2").build());
    Map<String, EdgeListInternal> adjacencyMap = new HashMap<>();
    adjacencyMap.put("parent", EdgeListInternal.builder().edges(List.of("child1", "child2")).build());
    adjacencyMap.put("child1", EdgeListInternal.builder().parentId("parent").build());
    adjacencyMap.put("child2", EdgeListInternal.builder().parentId("parent").build());
    return OrchestrationGraph.builder()
        .rootNodeIds(List.of("parent"))
        .adjacencyList(
            OrchestrationAdjacencyListInternal.builder().graphVertexMap(vertexMap).adjacencyMap(adjacencyMap).build())
        .build();
  }

  private Long getTimeInMillisForTheDayBefore() {
    return System.currentTimeMillis() - (24 * 60 * 60 * 1000);
  }
}
