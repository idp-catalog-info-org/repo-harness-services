/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class StepDetailsUpdateEventHandlerTest extends OrchestrationVisualizationTestBase {
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks StepDetailsUpdateEventHandler stepDetailsUpdateEventHandler;
  private static String nodeExecutionId = "nodeId";
  private static String setupId = "setupId123";
  private static String runtimeId = "runtimeId456";
  private static String accountId = "accountId";

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleEvent() {
    OrchestrationGraph orchestrationGraph =
        OrchestrationGraph.builder()
            .adjacencyList(OrchestrationAdjacencyListInternal.builder().graphVertexMap(Collections.emptyMap()).build())
            .build();

    stepDetailsUpdateEventHandler.handleEvent(
        "planExecutionId", "nodeExecutionId", orchestrationGraph, new Update(), null);
    assertThatCode(()
                       -> stepDetailsUpdateEventHandler.handleEvent(
                           "planExecutionId", "nodeExecutionId", orchestrationGraph, new Update(), null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateGraphAndSummaryEntity_FeatureFlagDisabled_UsesSetupId() {
    OrchestrationGraph orchestrationGraph = getOrchestrationGraph(true);
    Update summaryEntityUpdate = new Update();
    when(pmsFeatureFlagService.isEnabled(
             accountId, FeatureName.PIPE_POPULATE_STEP_DETAILS_IN_RUNTIME_ID_FOR_STRATEGY_CHILD_NODES))
        .thenReturn(false);
    stepDetailsUpdateEventHandler.handleEventV2(
        nodeExecutionId, orchestrationGraph, summaryEntityUpdate, getStepDetails(), accountId);
    String setupIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + setupId + ".stepDetails";
    String runtimeIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + runtimeId + ".stepDetails";
    assertThat(summaryEntityUpdate.getUpdateObject().get("$set")).isNotNull();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(setupIdPath))
        .isTrue();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(runtimeIdPath))
        .isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateGraphAndSummaryEntity_FeatureFlagEnabled_UsesRuntimeId() {
    OrchestrationGraph orchestrationGraph = getOrchestrationGraph(true);
    Update summaryEntityUpdate = new Update();
    when(pmsFeatureFlagService.isEnabled(
             accountId, FeatureName.PIPE_POPULATE_STEP_DETAILS_IN_RUNTIME_ID_FOR_STRATEGY_CHILD_NODES))
        .thenReturn(true);
    stepDetailsUpdateEventHandler.handleEventV2(
        nodeExecutionId, orchestrationGraph, summaryEntityUpdate, getStepDetails(), accountId);
    String setupIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + setupId + ".stepDetails";
    String runtimeIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + runtimeId + ".stepDetails";
    assertThat(summaryEntityUpdate.getUpdateObject().get("$set")).isNotNull();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(setupIdPath))
        .isFalse();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(runtimeIdPath))
        .isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateGraphAndSummaryEntity_NoStrategy() {
    OrchestrationGraph orchestrationGraph = getOrchestrationGraph(false);
    Update summaryEntityUpdate = new Update();
    when(pmsFeatureFlagService.isEnabled(
             accountId, FeatureName.PIPE_POPULATE_STEP_DETAILS_IN_RUNTIME_ID_FOR_STRATEGY_CHILD_NODES))
        .thenReturn(true);
    stepDetailsUpdateEventHandler.handleEventV2(
        nodeExecutionId, orchestrationGraph, summaryEntityUpdate, getStepDetails(), accountId);
    String setupIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + setupId + ".stepDetails";
    String runtimeIdPath =
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + runtimeId + ".stepDetails";
    assertThat(summaryEntityUpdate.getUpdateObject().get("$set")).isNotNull();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(setupIdPath))
        .isTrue();
    assertThat(((Map<String, Object>) summaryEntityUpdate.getUpdateObject().get("$set")).containsKey(runtimeIdPath))
        .isFalse();
  }

  private OrchestrationGraph getOrchestrationGraph(boolean setStrategy) {
    Level currentLevel = Level.newBuilder()
                             .setSetupId(setupId)
                             .setRuntimeId(runtimeId)
                             .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .build();
    if (setStrategy) {
      currentLevel = currentLevel.toBuilder().setStrategyMetadata(StrategyMetadata.newBuilder().build()).build();
    }

    GraphVertex graphVertex = GraphVertex.builder().currentLevel(currentLevel).build();

    Map<String, GraphVertex> graphVertexMap = new HashMap<>();
    graphVertexMap.put(nodeExecutionId, graphVertex);

    OrchestrationGraph orchestrationGraph =
        OrchestrationGraph.builder()
            .adjacencyList(OrchestrationAdjacencyListInternal.builder().graphVertexMap(graphVertexMap).build())
            .build();
    return orchestrationGraph;
  }
  private Map<String, PmsStepDetails> getStepDetails() {
    Map<String, Object> stepDetailsMap = ImmutableMap.of("templateRef", "template1", "versionLabel", "version1");

    Map<String, PmsStepDetails> pmsStepDetails = new HashMap<>();
    pmsStepDetails.put("templateReferenceSummary", PmsStepDetails.parse(stepDetailsMap));
    return pmsStepDetails;
  }
}
