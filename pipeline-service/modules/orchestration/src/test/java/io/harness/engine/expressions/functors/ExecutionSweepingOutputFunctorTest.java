/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExecutionSweepingOutputFunctorTest extends CategoryTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @Mock PmsSweepingOutputService pmsSweepingOutputService;
  transient Ambiance ambiance;
  transient ExecutionSweepingOutputMetadata outputMetadata;
  transient NodeExecutionsCache nodeExecutionsCache;
  ExecutionSweepingOutputFunctor executionSweepingOutputFunctor;
  List<String> sweepingOutputInstanceList;

  String planExecutionId = "planExecutionId";
  String originalPlanExecutionIdForRollbackMode = "originalPlanExecutionIdForRollbackMode";
  String resolvedJson = "{\"__recast\": \"io.harness.beans.sweepingoutputs.CodebaseSweepingOutput\",\n"
      + "  \"branch\": \"main\",\n"
      + "  \"commitSha\": \"6074f8e52fa9c9b5b022685490a0a9bc0de096c9\"}";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ambiance = getAmbiance();
    outputMetadata = new ExecutionSweepingOutputMetadata(pmsSweepingOutputService, ambiance.getPlanExecutionId(),
        ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode());
    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
    executionSweepingOutputFunctor = ExecutionSweepingOutputFunctor.builder()
                                         .outputMetadata(outputMetadata)
                                         .pmsSweepingOutputService(pmsSweepingOutputService)
                                         .ambiance(ambiance)
                                         .nodeExecutionsCache(nodeExecutionsCache)
                                         .build();
    sweepingOutputInstanceList = List.of("codebase");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetMethodForSweepingOutputFunctor() {
    String key = "codebase";
    doReturn(sweepingOutputInstanceList)
        .when(pmsSweepingOutputService)
        .fetchNameOfOutcomesInPlanExecutionId(planExecutionId);
    doReturn(resolvedJson)
        .when(pmsSweepingOutputService)
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    executionSweepingOutputFunctor.get(key);
    assertThat(executionSweepingOutputFunctor.get(key))
        .isEqualTo(NodeExecutionUtils.extractAndProcessObject(resolvedJson));
    assertThatThrownBy(() -> executionSweepingOutputFunctor.get("invalidKey"))
        .isInstanceOf(SweepingOutputException.class)
        .hasMessage("Could not resolve sweeping output with name 'invalidKey'");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetMethodForSweepingOutputFunctorWithRollbackMode() {
    String key = "codebase";
    doReturn(Collections.emptyList())
        .when(pmsSweepingOutputService)
        .fetchNameOfOutcomesInPlanExecutionId(planExecutionId);
    doReturn(sweepingOutputInstanceList)
        .when(pmsSweepingOutputService)
        .fetchNameOfOutcomesInPlanExecutionId(originalPlanExecutionIdForRollbackMode);
    doThrow(new SweepingOutputException("Could not resolve for rollback planExecutionId, throwing exception"))
        .when(pmsSweepingOutputService)
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    NodeExecution currentNodeExecution =
        NodeExecution.builder().originalNodeExecutionId(originalPlanExecutionIdForRollbackMode).build();
    doReturn(currentNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("pipelineRunTimeId", NodeProjectionUtils.fieldsForExpressionEngine);
    NodeExecution originalNodeExecution =
        NodeExecution.builder().uuid(originalPlanExecutionIdForRollbackMode).ambiance(ambiance).build();
    doReturn(originalNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(originalPlanExecutionIdForRollbackMode, NodeProjectionUtils.withAmbiance);
    doReturn(originalNodeExecution.getAmbiance()).when(nodeExecutionService).getAmbiance(originalNodeExecution);
    RawOptionalSweepingOutput rawOptionalSweepingOutput =
        RawOptionalSweepingOutput.builder().found(true).output(resolvedJson).build();
    doReturn(rawOptionalSweepingOutput)
        .when(pmsSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    assertThat(executionSweepingOutputFunctor.get(key))
        .isEqualTo(NodeExecutionUtils.extractAndProcessObject(resolvedJson));
  }

  private Ambiance getAmbiance() {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setRuntimeId("pipelineRunTimeId")
            .setSetupId("pipelineSetupId")
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .setNodeType("IDENTITY_PLAN_NODE")
            .build());
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addAllLevels(levelList)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                             .setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionIdForRollbackMode)
                             .build())
            .build();
    return ambiance;
  }
}
