/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.metadata.OutcomeMetadata;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
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

public class OutcomeFunctorTest extends CategoryTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @Mock PmsOutcomeService pmsOutcomeService;
  transient Ambiance ambiance;
  transient OutcomeMetadata outcomeMetadata;
  transient NodeExecutionsCache nodeExecutionsCache;
  OutcomeFunctor outcomeFunctor;
  List<String> outcomeInstances;

  String planExecutionId = "planExecutionId";
  String resolvedJson = "{\"__recast\":\"io.harness.cdng.service.steps.ServiceStepOutcome\",\"identifier\":"
      + "\"ServiceWithManifestAndArtifact\",\"name\":\"ServiceWithManifestAndArtifact\",\"type\":"
      + "\"Kubernetes\",\"tags\":{},\"gitOpsEnabled\":false}";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ambiance = getAmbiance();
    outcomeMetadata = new OutcomeMetadata(pmsOutcomeService, ambiance.getPlanExecutionId());
    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
    outcomeFunctor = OutcomeFunctor.builder()
                         .outcomeMetadata(outcomeMetadata)
                         .pmsOutcomeService(pmsOutcomeService)
                         .ambiance(ambiance)
                         .build();
    outcomeInstances = Collections.singletonList("Key1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMethod() {
    String key = "Key1";
    doReturn(true).when(pmsOutcomeService).existsOutcomeName(planExecutionId, key);

    doReturn(resolvedJson).when(pmsOutcomeService).resolve(ambiance, RefObjectUtils.getOutcomeRefObject((String) key));
    outcomeFunctor.get(key);
    assertThat(outcomeFunctor.get(key)).isEqualTo(NodeExecutionUtils.extractAndProcessObject(resolvedJson));
    assertThatThrownBy(() -> outcomeFunctor.get("invalidKey"))
        .isInstanceOf(OutcomeException.class)
        .hasMessage("Could not resolve outcome with name invalidKey");
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
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build())
            .build();
    return ambiance;
  }
}
