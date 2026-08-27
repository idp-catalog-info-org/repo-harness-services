/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.execution.NodeExecution.builder;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.identity.IdentityStep;
import io.harness.engine.pms.execution.strategy.identity.IdentityStepHelper;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.FacilitatorExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityStepTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private IdentityStepHelper identityStepHelper;
  @Inject @InjectMocks private IdentityStep identityStep;

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainTask() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    ChildExecutableResponse expectedChildExecutable =
        ChildExecutableResponse.newBuilder().setChildNodeId("childId").build();
    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().setChild(expectedChildExecutable).build();
    NodeExecution nodeExecution = builder().uuid("nodeUuid").executableResponse(executableResponse).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse childExecutableResponse = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("childId");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testObtainTaskWithEmptyInsertSteps() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().build();
    NodeExecution nodeExecution = builder().uuid("nodeUuid").build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse childExecutableResponse = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(childExecutableResponse.getSkip()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleChildResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution = builder().uuid("nodeUuid").status(Status.ABORTED).build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", Collections.singleton(NodeExecutionKeys.status));

    doReturn(StepResponse.builder().status(Status.ABORTED).build())
        .when(identityStepHelper)
        .handleChildResponse(any(), any(), any());
    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainChildren() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    ChildrenExecutableResponse expectedChildrenExecutable = ChildrenExecutableResponse.newBuilder().build();
    ExecutableResponse executableResponse =
        ExecutableResponse.newBuilder().setChildren(expectedChildrenExecutable).build();
    NodeExecution nodeExecution = builder().uuid("nodeUuid").executableResponse(executableResponse).build();
    doReturn(nodeExecution).when(nodeExecutionService).get(anyString());

    ChildrenExecutableResponse childrenExecutableResponse = identityStep.obtainChildren(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(expectedChildrenExecutable).isEqualTo(childrenExecutableResponse);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution = builder().uuid("nodeUuid").status(Status.ABORTED).build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded("nodeUuid", Collections.singleton(NodeExecutionKeys.status));
    doReturn(StepResponse.builder().status(Status.ABORTED).build())
        .when(identityStepHelper)
        .handleChildrenResponse(any(), any(), any());
    StepResponse stepResponse = identityStep.handleChildrenResponse(ambiance, identityParams, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testStreamingLogicSuccessCases() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // Test Case 1: obtainChild with facilitator first, then child
    ChildExecutableResponse expectedChildExecutable =
        ChildExecutableResponse.newBuilder().setChildNodeId("childId").build();
    NodeExecution nodeExecutionWithChild = createNodeExecutionWithFacilitatorAndChild(expectedChildExecutable);
    doReturn(nodeExecutionWithChild).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse actualChildResponse = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(expectedChildExecutable).isEqualTo(actualChildResponse);

    // Test Case 2: obtainChildren with facilitator first, then children
    ChildrenExecutableResponse expectedChildrenExecutable =
        ChildrenExecutableResponse.newBuilder().setMaxConcurrency(5).build();
    NodeExecution nodeExecutionWithChildren = createNodeExecutionWithFacilitatorAndChildren(expectedChildrenExecutable);
    doReturn(nodeExecutionWithChildren).when(nodeExecutionService).get(anyString());

    ChildrenExecutableResponse actualChildrenResponse = identityStep.obtainChildren(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(2)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(expectedChildrenExecutable).isEqualTo(actualChildrenResponse);

    // Test Case 3: obtainChild with multiple facilitators, then child
    ChildExecutableResponse expectedChildFromMultiple =
        ChildExecutableResponse.newBuilder().setChildNodeId("childIdMultiple").build();
    NodeExecution nodeExecutionWithMultipleFacilitators =
        createNodeExecutionWithMultipleFacilitatorsAndChild(expectedChildFromMultiple);
    doReturn(nodeExecutionWithMultipleFacilitators).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse actualChildFromMultiple = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(3)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(expectedChildFromMultiple).isEqualTo(actualChildFromMultiple);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testStreamingLogicFailureCases() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // Test Case 1: obtainChild with only facilitator, no child response
    NodeExecution nodeExecutionOnlyFacilitator = createNodeExecutionWithOnlyFacilitator();
    doReturn(nodeExecutionOnlyFacilitator).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse childResponseWithSkip = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(childResponseWithSkip.getSkip()).isTrue();

    // Test Case 2: obtainChildren with only facilitator, no children response
    doReturn(nodeExecutionOnlyFacilitator).when(nodeExecutionService).get(anyString());

    ChildrenExecutableResponse emptyChildrenResponse = identityStep.obtainChildren(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(2)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(emptyChildrenResponse).isEqualTo(ChildrenExecutableResponse.newBuilder().build());
  }

  private NodeExecution createNodeExecutionWithFacilitatorAndChild(ChildExecutableResponse childResponse) {
    FacilitatorExecutableResponse facilitatorResponse = FacilitatorExecutableResponse.newBuilder().build();
    ExecutableResponse facilitatorExecutableResponse =
        ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    ExecutableResponse childExecutableResponse = ExecutableResponse.newBuilder().setChild(childResponse).build();
    return builder()
        .uuid("nodeUuid")
        .executableResponse(facilitatorExecutableResponse)
        .executableResponse(childExecutableResponse)
        .build();
  }

  private NodeExecution createNodeExecutionWithFacilitatorAndChildren(ChildrenExecutableResponse childrenResponse) {
    FacilitatorExecutableResponse facilitatorResponse = FacilitatorExecutableResponse.newBuilder().build();
    ExecutableResponse facilitatorExecutableResponse =
        ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    ExecutableResponse childrenExecutableResponse =
        ExecutableResponse.newBuilder().setChildren(childrenResponse).build();
    return builder()
        .uuid("nodeUuid")
        .executableResponse(facilitatorExecutableResponse)
        .executableResponse(childrenExecutableResponse)
        .build();
  }

  private NodeExecution createNodeExecutionWithMultipleFacilitatorsAndChild(ChildExecutableResponse childResponse) {
    FacilitatorExecutableResponse facilitatorResponse1 = FacilitatorExecutableResponse.newBuilder().build();
    ExecutableResponse facilitatorExecutableResponse1 =
        ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse1).build();
    FacilitatorExecutableResponse facilitatorResponse2 = FacilitatorExecutableResponse.newBuilder().build();
    ExecutableResponse facilitatorExecutableResponse2 =
        ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse2).build();
    ExecutableResponse childExecutableResponse = ExecutableResponse.newBuilder().setChild(childResponse).build();
    return builder()
        .uuid("nodeUuid")
        .executableResponse(facilitatorExecutableResponse1)
        .executableResponse(facilitatorExecutableResponse2)
        .executableResponse(childExecutableResponse)
        .build();
  }

  private NodeExecution createNodeExecutionWithOnlyFacilitator() {
    FacilitatorExecutableResponse facilitatorResponse = FacilitatorExecutableResponse.newBuilder().build();
    ExecutableResponse facilitatorExecutableResponse =
        ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    return builder().uuid("nodeUuid").executableResponse(facilitatorExecutableResponse).build();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParameters() {
    assertThat(identityStep.getStepParametersClass()).isEqualTo(IdentityStepParameters.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testModifyAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder()
                                           .setRuntimeId("RID")
                                           .setStepType(StepType.newBuilder().getDefaultInstanceForType())
                                           .build())
                            .build();
    Ambiance ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(0).getStepType().getType(), "IDENTITY_STEP");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STEP);
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY))
                                  .setNodeType("IDENTITY_PLAN_NODE")
                                  .build())
                   .build();
    ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(0).getStepType().getType(), "IDENTITY_STRATEGY");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STRATEGY);
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY))
                                  .setNodeType("IDENTITY_PLAN_NODE")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP))
                                  .build())
                   .build();
    ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(1).getStepType().getType(), "IDENTITY_STRATEGY_INTERNAL");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STRATEGY);
  }
}
