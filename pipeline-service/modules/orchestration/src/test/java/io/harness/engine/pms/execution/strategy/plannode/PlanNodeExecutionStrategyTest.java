/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.beans.FeatureName.PIPE_FIX_STUCK_EXECUTION_AFTER_TRANSITION_FAILURE;
import static io.harness.beans.FeatureName.PIPE_SKIP_EXECUTE_WHEN_CONDITION_ON_RETRY_STEP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.WaitForExecutionInputHelper;
import io.harness.engine.executioncheck.ExecutionCheck;
import io.harness.engine.executioncheck.PreFacilitationExecutionCheck;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.observers.NodeCreateInfo;
import io.harness.engine.observers.NodeExecutionCreateObserver;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.advise.factory.AdviseHandlerFactory;
import io.harness.engine.pms.advise.handlers.NextStepHandler;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.execution.SdkResponseProcessorFactory;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceExecutionContextHelper;
import io.harness.engine.pms.execution.strategy.helper.intfc.EndNodeExecutionHelper;
import io.harness.engine.pms.resume.NodeResumeHelper;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.event.handlers.AdviserResponseRequestProcessor;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exceptions.RecasterException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.execution.RunNodeRequest;
import io.harness.expression.common.ExpressionMode;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.AdviserResponseRequest;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.transaction.CannotCreateTransactionException;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNodeExecutionStrategyTest extends OrchestrationTestBase {
  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock @Named("SdkResponseExecutorService") ExecutorService sdkResponseExecutorService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private FacilitateEventPublisher facilitateEventPublisher;
  @Mock private EndNodeExecutionHelper endNodeExecutionHelper;
  @Mock private NodeResumeHelper resumeHelper;
  @Mock private NodeStartHelper startHelper;
  @Mock private NodeAdviseHelper adviseHelper;
  @Mock private InterruptService interruptService;
  @Mock private PlanService planService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private SdkResponseProcessorFactory processorFactory;
  @Mock private AdviserResponseRequestProcessor adviserResponseProcessor;
  @Mock private WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Inject @InjectMocks @Spy PlanNodeExecutionStrategy executionStrategy;
  @Mock private NextStepHandler nextStepHandler;
  @Mock private AdviseHandlerFactory adviseHandlerFactory;
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Mock private RecastOrchestrationUtils recastOrchestrationUtils;
  @Mock private PmsOutcomeService outcomeService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private io.harness.engine.executions.plan.service.DagExecutionService dagExecutionService;
  @Inject @InjectMocks private AmbianceExecutionContextHelper ambianceExecutionContextHelper;
  @InjectMocks @Spy private FacilitationHelper facilitationHelper;
  @Inject Injector injector;

  private static final StepType TEST_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();

  private static final TriggeredBy triggeredBy =
      TriggeredBy.newBuilder().putExtraInfo("email", PRASHANT).setIdentifier(PRASHANT).setUuid(generateUuid()).build();
  private static final ExecutionTriggerInfo triggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build();
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() {
    initializeLogging();
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build())
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(generateUuid())
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .build();

    doReturn(NodeExecution.builder().build())
        .when(executionStrategy)
        .createNodeExecutionInternal(ambiance, planNode, null, null, null, null, null);
    executionStrategy.runNode(ambiance, planNode, null);
    verify(orchestrationEngine).queueOrStartExecution(any());
    doReturn(NodeExecution.builder().uuid("fda").build()).when(nodeExecutionService).save(any());
    // waitForExecutionInputHelper.waitForExecutionInputOrStart() will not be called.FF is off.
    verify(waitForExecutionInputHelper, never()).waitForExecutionInput(any(), any(), any());

    executionStrategy.runNode(ambiance, planNode, null);
    verify(orchestrationEngine, times(2)).queueOrStartExecution(any());
    // waitForExecutionInputHelper.waitForExecutionInputOrStart() will not be called.FF is on but executionInputTemplate
    // is empty.
    verify(waitForExecutionInputHelper, never()).waitForExecutionInput(any(), any(), any());

    planNode = PlanNode.builder()
                   .name("Test Node")
                   .uuid(generateUuid())
                   .identifier("test")
                   .stepType(TEST_STEP_TYPE)
                   .executionInputTemplate("executionInputTemplate")
                   .facilitatorObtainment(
                       FacilitatorObtainment.newBuilder()
                           .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                           .build())
                   .build();

    executionStrategy.runNode(ambiance, planNode, null);
    // executorService.submit will not be called this time because execution will pause for user input.
    verify(orchestrationEngine, times(3)).queueOrStartExecution(any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithCustomFacilitator() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    String whenCondition = "<+step.identifier>==null";

    PlanNode planNode = PlanNode.builder()
                            .name("Test Node")
                            .uuid(planNodeId)
                            .identifier("test")
                            .stepType(TEST_STEP_TYPE)
                            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                                       .setType(FacilitatorType.newBuilder().setType("CUSTOM").build())
                                                       .build())
                            .serviceName("CD")
                            .whenCondition(whenCondition)
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();

    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(facilitationHelper).checkAndRunSecondaryFacilitator(ambiance, planNode);

    executionStrategy.startExecution(ambiance);
    verify(nodeExecutionService, times(2)).updateV2(eq(nodeExecutionId), any());
    verify(facilitateEventPublisher).publishEvent(eq(ambiance), eq(planNode));
    verify(executionStrategy, times(0)).processFacilitationResponse(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    try {
      Field injectorField = FacilitationHelper.class.getDeclaredField("injector");
      injectorField.setAccessible(true); // Allows access to private fields
      injectorField.set(facilitationHelper, injector); // Set the field to the real Injector
    } catch (Exception e) {
      Assert.fail("failed to inject dependency via reflection");
    }
    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());

    executionStrategy.startExecution(ambiance);
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<FacilitatorResponseProto> facilitatorResponseCaptor =
        ArgumentCaptor.forClass(FacilitatorResponseProto.class);
    verify(executionStrategy)
        .processFacilitationResponseV2(
            ambianceCaptor.capture(), facilitatorResponseCaptor.capture(), updatesCaptor.capture());

    assertThat(ambianceCaptor.getValue().getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(facilitatorResponseCaptor.getValue().getExecutionMode()).isEqualTo(ExecutionMode.SYNC);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithMergeUpdatesFFEnabled() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();

    try {
      Field injectorField = FacilitationHelper.class.getDeclaredField("injector");
      injectorField.setAccessible(true); // Allows access to private fields
      injectorField.set(facilitationHelper, injector); // Set the field to the real Injector
    } catch (Exception e) {
      Assert.fail("failed to inject dependency via reflection");
    }

    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponseV2(any(), any(), any());

    executionStrategy.startExecution(ambiance);
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<FacilitatorResponseProto> facilitatorResponseCaptor =
        ArgumentCaptor.forClass(FacilitatorResponseProto.class);
    verify(executionStrategy)
        .processFacilitationResponseV2(
            ambianceCaptor.capture(), facilitatorResponseCaptor.capture(), eq(new HashMap<>()));

    assertThat(ambianceCaptor.getValue().getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(facilitatorResponseCaptor.getValue().getExecutionMode()).isEqualTo(ExecutionMode.SYNC);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithWrongExpressionStepParams() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PmsStepParameters stepParameters = PmsStepParameters.parse(Map.of("name", "<+abc>"));

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .stepParameters(stepParameters)
            .whenCondition("\"true\" == \"false\"")
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();

    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());

    executionStrategy.startExecution(ambiance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartExecutionWithWrongExpressionStepParamsAndNotSkip() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PmsStepParameters stepParameters = PmsStepParameters.parse(Map.of("name", "<+abc>"));

    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .serviceName("CD")
            .stepParameters(stepParameters)
            .expressionMode(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    doThrow(new InvalidRequestException("Exception eval failure"))
        .when(pmsEngineExpressionService)
        .resolve(ambiance, stepParameters, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);

    when(planService.fetchNode(planId, planNodeId)).thenReturn(planNode);
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);
    doNothing().when(executionStrategy).processFacilitationResponse(any(), any());
    executionStrategy.startExecution(ambiance);

    verify(executionStrategy).handleError(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecutionWithStatusRunning() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.RUNNING).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), eq(NodeProjectionUtils.fieldsForResume)))
        .thenReturn(nodeExecution);
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(resumeHelper).resume(eq(nodeExecution), eq(responseMap), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecutionWithStatusApprovalWaiting() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.APPROVAL_WAITING).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), eq(NodeProjectionUtils.fieldsForResume)))
        .thenReturn(nodeExecution);
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), eq(null), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(nodeExecution);
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(planExecutionService, times(1)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    verify(resumeHelper).resume(eq(nodeExecution), eq(responseMap), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResumeNodeExecutionWithStatusAborted() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.ABORTED).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(resumeHelper, times(0)).resume(eq(nodeExecution), eq(responseMap), eq(false));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponse() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", true)))
            .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any()))
        .thenReturn(NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .status(Status.QUEUED)
                        .mode(ExecutionMode.ASYNC)
                        .build());
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse));

    // when optimization is enabled
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                       Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", false)))
                   .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponseV2() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", true)))
            .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponseV2(ambiance, facilitatorResponse, new HashMap<>());
    verify(nodeExecutionService, times(1)).updateV2(eq(nodeExecutionId), any());
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse));

    // when optimization is enabled
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                       Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", false)))
                   .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    executionStrategy.processFacilitationResponseV2(ambiance, facilitatorResponse, new HashMap<>());
    verify(nodeExecutionService, times(1))
        .updateV2(eq(nodeExecutionId), any()); // when optimize this write is not called
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponseForPipeline() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", true)))
            .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any()))
        .thenReturn(NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .status(Status.QUEUED)
                        .mode(ExecutionMode.ASYNC)
                        .build());
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse));

    // when optimization is enabled
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                       Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", false)))
                   .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper).startNode(eq(ambiance), eq(facilitatorResponse), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponseWithInterrupt() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
            .build();

    when(nodeExecutionService.update(eq(nodeExecutionId), any()))
        .thenReturn(NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .status(Status.QUEUED)
                        .mode(ExecutionMode.ASYNC)
                        .build());
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(false).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper, times(0)).startNode(eq(ambiance), eq(facilitatorResponse));

    // when optimization is enabled
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                       Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", false)))
                   .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(false).build());

    executionStrategy.processFacilitationResponse(ambiance, facilitatorResponse);
    verify(startHelper, times(0)).startNode(eq(ambiance), eq(facilitatorResponse), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestProcessFacilitatorResponseV2WithInterrupt() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                Map.of("PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION", true)))
            .build();
    when(interruptService.checkInterruptsPreInvocation(eq(Collections.singletonList(planExecutionId)),
             eq(nodeExecutionId), eq(List.of(nodeExecutionId, stageNodeExecutionId)), eq(ambiance)))
        .thenReturn(ExecutionCheck.builder().proceed(false).build());

    FacilitatorResponseProto facilitatorResponse =
        FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.ASYNC).build();
    executionStrategy.processFacilitationResponseV2(ambiance, facilitatorResponse, new HashMap<>());
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
    verify(startHelper, times(0)).startNode(eq(ambiance), eq(facilitatorResponse));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConcludeNodeExecutionNoAdvisers() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .name("Test Node")
                            .uuid(planNodeId)
                            .identifier("test")
                            .stepType(TEST_STEP_TYPE)
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder()
                                                    .uuid(nodeExecutionId)
                                                    .ambiance(ambiance)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .mode(ExecutionMode.ASYNC);

    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), any(), any(), any()))
        .thenReturn(nodeExecutionBuilder.status(Status.FAILED).build());
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    doNothing().when(executionStrategy).endNodeExecution(eq(ambiance), any(), any());

    executionStrategy.concludeExecution(
        ambiance, Status.FAILED, Status.INTERVENTION_WAITING, EnumSet.noneOf(Status.class));
    verify(executionStrategy).endNodeExecution(eq(ambiance), any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConcludeNodeExecutionWithAdvisers() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .name("Test Node")
                            .uuid(planNodeId)
                            .identifier("test")
                            .stepType(TEST_STEP_TYPE)
                            .adviserObtainment(AdviserObtainment.newBuilder()
                                                   .setType(AdviserType.newBuilder().setType("ROLLBACK_CUSTOM").build())
                                                   .build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder()
                                                    .uuid(nodeExecutionId)
                                                    .ambiance(ambiance)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .mode(ExecutionMode.ASYNC);
    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    NodeExecution updated = nodeExecutionBuilder.status(Status.FAILED).endTs(1234L).build();
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(Status.FAILED), any(), any()))
        .thenReturn(updated);

    executionStrategy.concludeExecution(
        ambiance, Status.FAILED, Status.INTERVENTION_WAITING, EnumSet.noneOf(Status.class));
    verify(adviseHelper).queueAdvisingEvent(eq(updated), eq(planNode), eq(Status.INTERVENTION_WAITING));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestConcludeNodeExecutionWithoutCustomAdvisers() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planNodeId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid(planNodeId)
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .serviceName("CD")
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder()
                                                    .uuid(nodeExecutionId)
                                                    .ambiance(ambiance)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .mode(ExecutionMode.ASYNC);
    when(planService.fetchNode(eq(planId), eq(planNodeId))).thenReturn(planNode);
    NodeExecution updated = nodeExecutionBuilder.status(Status.FAILED).endTs(1234L).build();
    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(Status.FAILED), any(), any()))
        .thenReturn(updated);
    doReturn(SdkResponseEventProto.newBuilder()
                 .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                 .build())
        .when(adviseHelper)
        .getResponseInCaseOfNoCustomAdviser(eq(updated), eq(planNode), eq(Status.INTERVENTION_WAITING));

    executionStrategy.concludeExecution(
        ambiance, Status.FAILED, Status.INTERVENTION_WAITING, EnumSet.noneOf(Status.class));
    verify(adviseHelper).getResponseInCaseOfNoCustomAdviser(eq(updated), eq(planNode), eq(Status.INTERVENTION_WAITING));
    verify(sdkResponseExecutorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleErrorWithExceptionManager() {
    ArgumentCaptor<Ambiance> nExCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<StepResponseProto> sCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).build();
    when(nodeExecutionService.get(nodeExecution.getUuid())).thenReturn(nodeExecution);
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecution.getUuid()).build())
                            .build();
    CannotCreateTransactionException ex = new CannotCreateTransactionException("Cannot Create Transaction");
    executionStrategy.handleError(ambiance, ex);
    verify(executionStrategy).handleStepResponseInternal(nExCaptor.capture(), sCaptor.capture());
    assertThat(AmbianceUtils.obtainCurrentRuntimeId(nExCaptor.getValue())).isEqualTo(nodeExecution.getUuid());
    assertThat(sCaptor.getValue().getFailureInfo()).isNotNull();
    assertThat(sCaptor.getValue().getFailureInfo().getErrorMessage()).isEqualTo("Cannot Create Transaction");
    assertThat(sCaptor.getValue().getFailureInfo().getFailureTypesList().get(0)).isEqualTo(APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void handleStepResponseInternal_unexpectedError() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(runtimeId)
                                      .notifyId("someNotifyId")
                                      .failureInfo(FailureInfo.newBuilder().build())
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(null).when(endNodeExecutionHelper).handleStepResponsePreAdviser(ambiance, stepResponseProto, null);
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(AmbianceUtils.getAccountId(ambiance), PIPE_FIX_STUCK_EXECUTION_AFTER_TRANSITION_FAILURE);
    doReturn(Collections.emptyList()).when(outcomeService).fetchOutcomeRefs(nodeExecution.getUuid());

    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(nodeExecutionService, times(2)).updateV2(eq(nodeExecution.getUuid()), any());
    verify(nodeExecutionService)
        .getWithFieldsIncluded(eq(nodeExecution.getUuid()), eq(NodeProjectionUtils.fieldsForExecutionStrategy));
    verify(outcomeService).fetchOutcomeRefs(nodeExecution.getUuid());
    verify(waitNotifyEngine).doneWith(eq(nodeExecution.getNotifyId()), any(StepResponseNotifyData.class));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void handleStepResponseInternal_RecastException() {
    // The handle functionaly should not break in case, we encounter an exception while logging
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(NodeExecution.builder().build())
        .when(endNodeExecutionHelper)
        .handleStepResponsePreAdviser(ambiance, stepResponseProto, planNode);
    try (MockedStatic<RecastOrchestrationUtils> mockedStatic = Mockito.mockStatic(RecastOrchestrationUtils.class)) {
      mockedStatic.when(() -> RecastOrchestrationUtils.toJson(any()))
          .thenThrow(new RecasterException("Cannot serialize to json"));

      executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
      verify(adviseHelper, times(1)).queueAdvisingEvent(any(), any(), any());
      verify(planExecutionService, times(1)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(any());
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void handleStepResponseWithError() {
    String nodeExecutionId = generateUuid();
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("planExecutionId")
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();
    when(nodeExecutionService.get(nodeExecution.getUuid())).thenReturn(nodeExecution);
    doThrow(new InvalidRequestException("test"))
        .when(endNodeExecutionHelper)
        .endNodeExecutionWithNoAdvisers(ambiance, stepResponseProto, null);
    doNothing().when(executionStrategy).handleError(any(), any());
    executionStrategy.processStepResponse(ambiance, stepResponseProto);
    verify(executionStrategy).handleError(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleSdkResponseWithoutError() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(generateUuid()).build();
    AdviserResponseRequest request = AdviserResponseRequest.newBuilder()
                                         .setAdviserResponse(AdviserResponse.newBuilder()
                                                                 .setType(AdviseType.END_PLAN)
                                                                 .setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
                                                                 .build())
                                         .build();
    SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                      .setAmbiance(ambiance)
                                      .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                                      .setAdviserResponseRequest(request)
                                      .build();
    doReturn(adviserResponseProcessor).when(processorFactory).getHandler(SdkResponseEventType.HANDLE_ADVISER_RESPONSE);
    doNothing().when(adviserResponseProcessor).handleEvent(eq(event));
    executionStrategy.handleSdkResponseEvent(event);
    verify(adviserResponseProcessor, times(1)).handleEvent(eq(event));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleSdkResponseWithError() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(generateUuid()).build();
    AdviserResponseRequest request = AdviserResponseRequest.newBuilder()
                                         .setAdviserResponse(AdviserResponse.newBuilder()
                                                                 .setType(AdviseType.END_PLAN)
                                                                 .setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
                                                                 .build())
                                         .build();
    SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                      .setAmbiance(ambiance)
                                      .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
                                      .setAdviserResponseRequest(request)
                                      .build();

    InvalidRequestException ex = new InvalidRequestException("Invalid Request");
    doReturn(adviserResponseProcessor).when(processorFactory).getHandler(SdkResponseEventType.HANDLE_ADVISER_RESPONSE);
    doThrow(ex).when(adviserResponseProcessor).handleEvent(eq(event));
    executionStrategy.handleSdkResponseEvent(event);
    verify(adviserResponseProcessor, times(1)).handleEvent(eq(event));
    verify(executionStrategy, times(1)).handleError(eq(ambiance), eq(ex));
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateNodeExecution() {
    long startTs = 1234L;
    String uuid = generateUuid();
    String nodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setStartTs(startTs).setRuntimeId(uuid).build())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                                Map.of("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", true)))
                            .build();
    StepType stepType = StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
    PlanNode node = PlanNode.builder()
                        .uuid(nodeId)
                        .name("PLAN_NODE")
                        .identifier("plan_node")
                        .serviceName("CD")
                        .stepType(stepType)
                        .group("grp")
                        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                                   .setType(FacilitatorType.newBuilder().setType("ASYNC").build())
                                                   .build())
                        .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(uuid)
                                      .ambiance(ambiance)
                                      .levelCount(1)
                                      .status(Status.QUEUED)
                                      .unitProgresses(new ArrayList<>())
                                      .name("PLAN_NODE")
                                      .identifier("plan_node")
                                      .notifyId("NID")
                                      .parentId("PaID")
                                      .previousId("PrID")
                                      .skipGraphType(SkipType.NOOP)
                                      .module("CD")
                                      .stepType(stepType)
                                      .nodeId(nodeId)
                                      .group("grp")
                                      .nodeType(node.getNodeType().name())
                                      .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
                                      .skipExpressionChain(false)
                                      .mode(ExecutionMode.ASYNC)
                                      .executionContext(AmbianceUtils.getExecutionContextFromAmbiance(ambiance))
                                      .build();
    when(nodeExecutionService.save(any(NodeExecution.class))).thenReturn(nodeExecution);
    NodeExecution nodeExecution1 =
        executionStrategy.createNodeExecutionInternal(ambiance, node, null, "NID", "PaID", "PrID", null);
    assertEquals(nodeExecution1, nodeExecution);
    ArgumentCaptor<NodeExecution> mCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(nodeExecutionService).save(mCaptor.capture());
    verify(pmsGraphStepDetailsService)
        .saveNodeExecutionInfo(nodeExecution1.getUuid(), ambiance.getPlanExecutionId(), null, ACCOUNT_ID);
    assertThat(mCaptor.getValue()).usingRecursiveComparison().ignoringFields("validUntil").isEqualTo(nodeExecution);
    PlanNode node1 =
        PlanNode.builder()
            .uuid(nodeId)
            .name("PLAN_NODE")
            .identifier("plan_node")
            .serviceName("CD")
            .stepType(stepType)
            .group("grp")
            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                       .setType(FacilitatorType.newBuilder().setType("RESOURCE_RESTRAINT").build())
                                       .build())
            .build();
    NodeExecution nodeExecution3 = NodeExecution.builder()
                                       .uuid(uuid)
                                       .ambiance(ambiance)
                                       .levelCount(1)
                                       .status(Status.QUEUED)
                                       .unitProgresses(new ArrayList<>())
                                       .name("PLAN_NODE")
                                       .identifier("plan_node")
                                       .notifyId("NID")
                                       .parentId("PaID")
                                       .previousId("PrID")
                                       .skipGraphType(SkipType.NOOP)
                                       .module("CD")
                                       .stepType(stepType)
                                       .nodeId(nodeId)
                                       .group("grp")
                                       .nodeType(node.getNodeType().name())
                                       .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
                                       .skipExpressionChain(false)
                                       .executionContext(AmbianceUtils.getExecutionContextFromAmbiance(ambiance))
                                       .build();
    when(nodeExecutionService.save(any(NodeExecution.class))).thenReturn(nodeExecution3);
    NodeExecution nodeExecution2 =
        executionStrategy.createNodeExecutionInternal(ambiance, node1, null, "NID", "PaID", "PrID", null);
    assertEquals(nodeExecution3, nodeExecution2);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateNodeExecutionWithMetadata() {
    long startTs = 1234L;
    String uuid = generateUuid();
    String nodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setStartTs(startTs).setRuntimeId(uuid).build())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                                Map.of("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", true)))
                            .build();
    StepType stepType = StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
    PlanNode node = PlanNode.builder()
                        .uuid(nodeId)
                        .name("PLAN_NODE")
                        .identifier("plan_node")
                        .serviceName("CD")
                        .stepType(stepType)
                        .group("grp")
                        .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(uuid)
                                      .ambiance(ambiance)
                                      .levelCount(1)
                                      .status(Status.QUEUED)
                                      .unitProgresses(new ArrayList<>())
                                      .name("PLAN_NODE")
                                      .identifier("plan_node")
                                      .notifyId("NID")
                                      .parentId("PaID")
                                      .previousId("PrID")
                                      .skipGraphType(SkipType.NOOP)
                                      .module("CD")
                                      .stepType(stepType)
                                      .nodeId(nodeId)
                                      .group("grp")
                                      .skipExpressionChain(false)
                                      .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
                                      .nodeType(NodeType.PLAN_NODE.name())
                                      .executionContext(AmbianceUtils.getExecutionContextFromAmbiance(ambiance))
                                      .build();
    when(nodeExecutionService.save(any(NodeExecution.class))).thenReturn(nodeExecution);
    NodeExecution nodeExecution1 = executionStrategy.createNodeExecutionInternal(ambiance, node,
        NodeExecutionMetadata.builder().strategyMetadata(StrategyMetadata.newBuilder().build()).build(), "NID", "PaID",
        "PrID", null);
    assertEquals(nodeExecution1, nodeExecution);
    ArgumentCaptor<NodeExecution> mCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(nodeExecutionService).save(mCaptor.capture());
    verify(pmsGraphStepDetailsService)
        .saveNodeExecutionInfo(
            nodeExecution1.getUuid(), ambiance.getPlanExecutionId(), StrategyMetadata.newBuilder().build(), ACCOUNT_ID);
    assertThat(mCaptor.getValue()).usingRecursiveComparison().ignoringFields("validUntil").isEqualTo(nodeExecution);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateNodeExecutionInternalBatch() {
    String planExecutionId = generateUuid();
    String runtimeId1 = generateUuid();
    String runtimeId2 = generateUuid();
    String nodeId1 = generateUuid();
    String nodeId2 = generateUuid();
    Ambiance ambiance1 = Ambiance.newBuilder()
                             .setPlanExecutionId(planExecutionId)
                             .addLevels(Level.newBuilder().setRuntimeId(runtimeId1).build())
                             .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                             .build();
    Ambiance ambiance2 = Ambiance.newBuilder()
                             .setPlanExecutionId(planExecutionId)
                             .addLevels(Level.newBuilder().setRuntimeId(runtimeId2).build())
                             .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                             .build();
    PlanNode node1 = PlanNode.builder()
                         .uuid(nodeId1)
                         .name("PLAN_NODE_1")
                         .identifier("plan_node_1")
                         .serviceName("CD")
                         .stepType(TEST_STEP_TYPE)
                         .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                                    .setType(FacilitatorType.newBuilder().setType("ASYNC").build())
                                                    .build())
                         .build();
    PlanNode node2 = PlanNode.builder()
                         .uuid(nodeId2)
                         .name("PLAN_NODE_2")
                         .identifier("plan_node_2")
                         .serviceName("CD")
                         .stepType(TEST_STEP_TYPE)
                         .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                                    .setType(FacilitatorType.newBuilder().setType("ASYNC").build())
                                                    .build())
                         .build();
    StrategyMetadata strategyMetadata1 = StrategyMetadata.newBuilder().setCurrentIteration(1).build();
    StrategyMetadata strategyMetadata2 = StrategyMetadata.newBuilder().setCurrentIteration(2).build();
    RunNodeRequest request1 = RunNodeRequest.builder()
                                  .runtimeId(runtimeId1)
                                  .setupId(nodeId1)
                                  .node(node1)
                                  .strategyMetadata(strategyMetadata1)
                                  .ambiance(ambiance1)
                                  .notifyId("NID1")
                                  .parentId("PID1")
                                  .previousId("PRID1")
                                  .build();
    RunNodeRequest request2 = RunNodeRequest.builder()
                                  .runtimeId(runtimeId2)
                                  .setupId(nodeId2)
                                  .node(node2)
                                  .strategyMetadata(strategyMetadata2)
                                  .ambiance(ambiance2)
                                  .notifyId("NID2")
                                  .parentId("PID2")
                                  .previousId("PRID2")
                                  .build();
    RunNodeBatchRequest batchRequest = RunNodeBatchRequest.builder().nodes(List.of(request1, request2)).build();
    when(nodeExecutionService.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));
    NodeExecutionCreateObserver observer = Mockito.mock(NodeExecutionCreateObserver.class);
    executionStrategy.getNodeExecutionCreateObserverSubject().register(observer);

    try {
      try {
        Field helperField = PlanNodeExecutionStrategy.class.getDeclaredField("ambianceExecutionContextHelper");
        helperField.setAccessible(true);
        helperField.set(executionStrategy, ambianceExecutionContextHelper);
        Field ffServiceField = AmbianceExecutionContextHelper.class.getDeclaredField("pmsFeatureFlagService");
        ffServiceField.setAccessible(true);
        ffServiceField.set(ambianceExecutionContextHelper, pmsFeatureFlagService);
      } catch (Exception e) {
        Assert.fail("failed to inject AmbianceExecutionContextHelper dependencies via reflection");
      }

      List<NodeExecution> result = executionStrategy.createNodeExecutionInternal(batchRequest);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getUuid()).isEqualTo(runtimeId1);
      assertThat(result.get(1).getUuid()).isEqualTo(runtimeId2);
      assertThat(result.get(0).getExecutionContext())
          .isEqualTo(AmbianceUtils.getExecutionContextFromAmbiance(ambiance1));
      assertThat(result.get(1).getExecutionContext())
          .isEqualTo(AmbianceUtils.getExecutionContextFromAmbiance(ambiance2));

      ArgumentCaptor<List<NodeExecution>> nodeExecutionsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<List<StrategyMetadata>> strategyMetadataCaptor = ArgumentCaptor.forClass(List.class);
      verify(pmsGraphStepDetailsService)
          .saveNodeExecutionInfo(nodeExecutionsCaptor.capture(), strategyMetadataCaptor.capture());
      assertThat(strategyMetadataCaptor.getValue()).containsExactly(strategyMetadata1, strategyMetadata2);

      ArgumentCaptor<NodeCreateInfo> nodeCreateInfoCaptor = ArgumentCaptor.forClass(NodeCreateInfo.class);
      verify(observer, times(2)).onNodeCreate(nodeCreateInfoCaptor.capture());
      List<NodeCreateInfo> createInfos = nodeCreateInfoCaptor.getAllValues();
      assertThat(createInfos.get(0).getNodeExecutionId()).isEqualTo(runtimeId1);
      assertThat(createInfos.get(0).getPlanExecutionId()).isEqualTo(planExecutionId);
      assertThat(createInfos.get(0).getNode()).isEqualTo(node1);
      assertThat(createInfos.get(0).getAmbiance()).isEqualTo(ambiance1);
      assertThat(createInfos.get(1).getNodeExecutionId()).isEqualTo(runtimeId2);
      assertThat(createInfos.get(1).getPlanExecutionId()).isEqualTo(planExecutionId);
      assertThat(createInfos.get(1).getNode()).isEqualTo(node2);
      assertThat(createInfos.get(1).getAmbiance()).isEqualTo(ambiance2);
    } finally {
      executionStrategy.getNodeExecutionCreateObserverSubject().unregister(observer);
    }
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponse() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();
    doNothing().when(executionStrategy).endNodeExecution(eq(ambiance), any(), any());
    executionStrategy.processAdviserResponse(ambiance, adviserResponse);
    verify(executionStrategy, times(1)).endNodeExecution(eq(ambiance), any(), any());
    adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.NEXT_STEP).build();
    doReturn(NodeExecution.builder().build()).when(nodeExecutionService).get(uuid);
    doReturn(nextStepHandler).when(adviseHandlerFactory).obtainHandler(AdviseType.NEXT_STEP);
    doNothing().when(nextStepHandler).handleAdvise(any(), any());
    executionStrategy.processAdviserResponse(ambiance, adviserResponse);
    verify(nextStepHandler, times(1)).handleAdvise(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecutionWithEmptyNotifyId() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    executionStrategy.endNodeExecution(ambiance, nodeExecution, null);
    verify(orchestrationEngine, times(1)).endNodeExecution(any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecution() {
    String uuid = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(uuid).build()).build();
    String notifyId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder().notifyId(notifyId).failureInfo(FailureInfo.newBuilder().build()).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    executionStrategy.endNodeExecution(ambiance, nodeExecution, null);
    verify(waitNotifyEngine, times(1)).doneWith(any(), any(StepResponseNotifyData.class));
    verify(nodeExecutionService, never())
        .getWithFieldsIncluded(any(), any()); // getWithFieldsIncluded not called because of optimization enabled

    // if PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION is enabled, the optimization is disabled and
    // getWithFieldsIncluded is called
    ambiance = ambiance.toBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                       Map.of("PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION", true)))
                   .build();
    executionStrategy.endNodeExecution(ambiance, nodeExecution, null);
    verify(waitNotifyEngine, times(2)).doneWith(any(), any(StepResponseNotifyData.class));
    verify(nodeExecutionService, times(1)).getWithFieldsIncluded(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternalWithEmptyAdviserObtainments() {
    String uuid = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().build();
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanId(planId).addLevels(Level.newBuilder().setSetupId(uuid).build()).build();
    doReturn(planNode).when(planService).fetchNode(planId, uuid);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    doReturn(NodeExecution.builder().status(Status.RUNNING).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), any());
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(endNodeExecutionHelper, times(1)).endNodeExecutionWithNoAdvisers(ambiance, stepResponseProto, planNode);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternalWithUpdatedNodeExecutionNull() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(null).when(endNodeExecutionHelper).handleStepResponsePreAdviser(ambiance, stepResponseProto, null);
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(adviseHelper, times(0)).queueAdvisingEvent(any(), any(), any());
    verify(planExecutionService, times(0)).calculateAndUpdateRunningStatusUnderLock(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternal() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, setupId);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().build();
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    doReturn(NodeExecution.builder().build())
        .when(endNodeExecutionHelper)
        .handleStepResponsePreAdviser(ambiance, stepResponseProto, planNode);
    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);
    verify(adviseHelper, times(1)).queueAdvisingEvent(any(), any(), any());
    verify(planExecutionService, times(1)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testPerformPreFacilitationChecksWithIsRetryNotEqualToZero() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).setRetryIndex(1).build())
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .build();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("accountId"), eq(PIPE_SKIP_EXECUTE_WHEN_CONDITION_ON_RETRY_STEP));
    PreFacilitationExecutionCheck executionCheck = executionStrategy.performPreFacilitationChecks(ambiance, planNode);
    assertTrue(executionCheck.isProceed());
    assertEquals(executionCheck.getReason(), "Node is retried.");
    assertThat(executionCheck.getUpdates()).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testPerformPreFacilitationChecksWithIsRetryNotEqualToZeroFFDisabled() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).setRetryIndex(1).build())
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .build();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("accountId"), eq(PIPE_SKIP_EXECUTE_WHEN_CONDITION_ON_RETRY_STEP));
    PreFacilitationExecutionCheck executionCheck = executionStrategy.performPreFacilitationChecks(ambiance, planNode);
    assertTrue(executionCheck.isProceed());
    assertThat(executionCheck.getReason()).isNull();
    assertThat(executionCheck.getUpdates()).isNull();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testResolveParameters() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId(setupId).setRetryIndex(1).build())
            .setMetadata(ExecutionMetadata.newBuilder().build())
            .build();
    PlanNode planNode = PlanNode.builder()
                            .expressionMode(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)
                            .stepParameters(new PmsStepParameters())
                            .build();
    doReturn(new Object()).when(pmsEngineExpressionService).resolve(ambiance, new PmsStepParameters(), true);
    try (MockedStatic<PmsStepParameters> utilities = Mockito.mockStatic(PmsStepParameters.class);
         MockedStatic<OrchestrationMapBackwardCompatibilityUtils> utilities1 =
             Mockito.mockStatic(OrchestrationMapBackwardCompatibilityUtils.class)) {
      utilities.when(() -> PmsStepParameters.parse(any(OrchestrationMap.class))).thenReturn(new PmsStepParameters());
      utilities1
          .when(
              () -> OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(any(PmsStepParameters.class)))
          .thenReturn(new OrchestrationMap());
      executionStrategy.resolveParameters(ambiance, planNode);
      verify(pmsEngineExpressionService, times(1))
          .resolve(ambiance, planNode.getStepParameters(), planNode.getExpressionMode());
      verify(nodeExecutionService, times(1)).getResolvedStepInputs(any(), any());
      verify(pmsGraphStepDetailsService, times(1)).addStepInputsInternal(any(), any(), any(), any(), any());
      verify(nodeExecutionService, times(1)).updateV2(any(), any());
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessOrQueueAdvisingEvent() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid("uuid").build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("planNodeId")
            .identifier("test")
            .stepType(StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build())
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .serviceName("CD")
            .build();
    doReturn(
        SdkResponseEventProto.newBuilder().setSdkResponseEventType(SdkResponseEventType.HANDLE_EVENT_ERROR).build())
        .when(adviseHelper)
        .getResponseInCaseOfNoCustomAdviser(eq(nodeExecution), eq(planNode), eq(Status.RUNNING));
    executionStrategy.processOrQueueAdvisingEvent(nodeExecution, planNode, Status.RUNNING);
    verify(adviseHelper, times(1))
        .getResponseInCaseOfNoCustomAdviser(eq(nodeExecution), eq(planNode), eq(Status.RUNNING));
    verify(executorService, times(0)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testEndNodeExecutionWithFailureInfo() {
    Level level =
        Level.newBuilder().setIdentifier("levelIdentifier").setRuntimeId("runTimeId").setSetupId("setUpId").build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();
    String notifyId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder().status(Status.ABORTED).notifyId(notifyId).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                              .nodeUuid(level.getSetupId())
                                              .stepOutcomeRefs(Collections.emptyList())
                                              .failureInfo(null)
                                              .identifier(level.getIdentifier())
                                              .nodeExecutionId(level.getRuntimeId())
                                              .status(nodeExecution.getStatus())
                                              .adviserResponse(nodeExecution.getAdviserResponse())
                                              .nodeExecutionEndTs(nodeExecution.getEndTs())
                                              .build();
    executionStrategy.endNodeExecution(ambiance, nodeExecution, null);
    ArgumentCaptor<String> correlationIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ResponseData> responseDataArgumentCaptor = ArgumentCaptor.forClass(ResponseData.class);
    verify(waitNotifyEngine, times(1))
        .doneWith(correlationIdArgumentCaptor.capture(), responseDataArgumentCaptor.capture());
    assertThat(correlationIdArgumentCaptor.getValue()).isEqualTo(notifyId);
    assertThat(responseDataArgumentCaptor.getValue()).isEqualTo(responseData);

    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().build()).build();
    nodeExecution = NodeExecution.builder().notifyId(notifyId).status(Status.ABORTED).failureInfo(failureInfo).build();
    responseData = StepResponseNotifyData.builder()
                       .nodeUuid(level.getSetupId())
                       .stepOutcomeRefs(Collections.emptyList())
                       .failureInfo(nodeExecution.getFailureInfo())
                       .identifier(level.getIdentifier())
                       .nodeExecutionId(level.getRuntimeId())
                       .status(nodeExecution.getStatus())
                       .adviserResponse(nodeExecution.getAdviserResponse())
                       .nodeExecutionEndTs(nodeExecution.getEndTs())
                       .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());
    executionStrategy.endNodeExecution(ambiance, nodeExecution, null);
    verify(waitNotifyEngine, times(2))
        .doneWith(correlationIdArgumentCaptor.capture(), responseDataArgumentCaptor.capture());
    assertThat(correlationIdArgumentCaptor.getValue()).isEqualTo(notifyId);
    assertThat(responseDataArgumentCaptor.getValue()).isEqualTo(responseData);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreateNodeExecutionInternal_OptimizationEnabled_NoInitialSave() {
    Ambiance ambiance = buildAmbianceWithFF("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", false);
    PlanNode planNode = buildTestPlanNode();
    NodeExecutionMetadata metadata = NodeExecutionMetadata.builder().strategyMetadata(null).build();

    doReturn(NodeExecution.builder().uuid("nodeExecutionId").build()).when(nodeExecutionService).save(any());

    executionStrategy.createNodeExecutionInternal(ambiance, planNode, metadata, null, null, null, null);

    verify(pmsGraphStepDetailsService, never()).saveNodeExecutionInfo(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreateNodeExecutionInternal_OptimizationEnabled_InitialSaveInCreateMode() {
    Ambiance ambiance = buildAmbianceWithFF("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", false);
    PlanNode planNode = buildTestPlanNode();
    NodeExecutionMetadata metadata =
        NodeExecutionMetadata.builder().strategyMetadata(StrategyMetadata.newBuilder().build()).build();

    doReturn(NodeExecution.builder().uuid("nodeExecutionId").build()).when(nodeExecutionService).save(any());

    executionStrategy.createNodeExecutionInternal(ambiance, planNode, metadata, null, null, null, InitiateMode.CREATE);

    verify(pmsGraphStepDetailsService, times(1)).saveNodeExecutionInfo(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testAddResolvedStepInputs_StrategyMetadataPassedCorrectly() {
    Ambiance ambiance = buildAmbianceWithFF("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", false);
    PlanNode planNode = buildTestPlanNode();
    StrategyMetadata strategyMetadata =
        StrategyMetadata.newBuilder().setCurrentIteration(2).setTotalIterations(5).build();

    executionStrategy.addResolvedStepInputs("planExecutionId", "nodeExecutionId", new PmsStepParameters(),
        strategyMetadata, ACCOUNT_ID, ambiance, planNode);

    ArgumentCaptor<StrategyMetadata> metadataCaptor = ArgumentCaptor.forClass(StrategyMetadata.class);
    verify(pmsGraphStepDetailsService, times(1))
        .addStepInputs(eq("nodeExecutionId"), any(PmsStepParameters.class), eq("planExecutionId"),
            metadataCaptor.capture(), eq(ACCOUNT_ID));

    StrategyMetadata capturedMetadata = metadataCaptor.getValue();
    assertThat(capturedMetadata.getCurrentIteration()).isEqualTo(2);
    assertThat(capturedMetadata.getTotalIterations()).isEqualTo(5);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testAddResolvedStepInputs_NullStrategyMetadata() {
    Ambiance ambiance = buildAmbianceWithFF("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", false);
    PlanNode planNode = buildTestPlanNode();

    executionStrategy.addResolvedStepInputs(
        "planExecutionId", "nodeExecutionId", new PmsStepParameters(), null, ACCOUNT_ID, ambiance, planNode);

    verify(pmsGraphStepDetailsService, times(1))
        .addStepInputs(eq("nodeExecutionId"), any(PmsStepParameters.class), eq("planExecutionId"), any(), any());

    verify(pmsGraphStepDetailsService, never()).addStepInputs(any(), any(), any(), any(StrategyMetadata.class), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForUpdateFail() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = "j3DpJDA4SpSNyq0RE4qADQ";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).status(Status.WAIT_STEP_RUNNING).build();
    Map<String, ResponseDataProto> responseMap = ImmutableMap.of(
        generateUuid(), ResponseDataProto.newBuilder().setResponse(ByteString.copyFromUtf8(generateUuid())).build());
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), eq(NodeProjectionUtils.fieldsForResume)))
        .thenReturn(nodeExecution);
    when(nodeExecutionService.updateStatusWithOps(anyString(), any(), any(), any())).thenReturn(null);
    doNothing().when(executionStrategy).handleError(any(), any());
    executionStrategy.resumeNodeExecution(ambiance, responseMap, false);
    verify(resumeHelper, times(0)).resume(eq(nodeExecution), eq(responseMap), eq(false));
    ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
    verify(executionStrategy).handleError(eq(ambiance), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).isInstanceOf(InternalServerErrorException.class);
    assertThat(exceptionCaptor.getValue().getMessage())
        .contains("Failed to resume NodeExecution [nodeExecutionId=j3DpJDA4SpSNyq0RE4qADQ, "
            + "previousStatus=WAIT_STEP_RUNNING]");
  }

  private Ambiance buildAmbianceWithFF(String featureFlagName, boolean featureFlagValue) {
    return Ambiance.newBuilder()
        .setPlanExecutionId("planExecutionId")
        .addLevels(Level.newBuilder().setRuntimeId("nodeExecutionId").build())
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .putAllFeatureFlagToValueMap(Map.of(featureFlagName, featureFlagValue))
                         .build())
        .build();
  }

  private PlanNode buildTestPlanNode() {
    return PlanNode.builder()
        .uuid(generateUuid())
        .name("Test Node")
        .identifier("test")
        .stepType(StepType.newBuilder().setType("TEST_STEP").setStepCategory(StepCategory.STEP).build())
        .build();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleStepResponseInternal_FiresDagCallbacksForNoAdviserStage() {
    String uuid = generateUuid();
    String planId = generateUuid();
    String runtimeId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(uuid)
            .name("Test Stage")
            .identifier("test-stage")
            .stepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .addLevels(Level.newBuilder().setSetupId(uuid).setRuntimeId(runtimeId).build())
                            .build();
    doReturn(planNode).when(planService).fetchNode(planId, uuid);
    StepResponseProto stepResponseProto = StepResponseProto.newBuilder().setStatus(Status.SUCCEEDED).build();
    doReturn(NodeExecution.builder().uuid(runtimeId).status(Status.RUNNING).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), any());

    executionStrategy.handleStepResponseInternal(ambiance, stepResponseProto);

    verify(dagExecutionService, times(1)).fireDagCallbacksForNoAdviserStage(ambiance, planNode, Status.SUCCEEDED);
    verify(endNodeExecutionHelper, times(1)).endNodeExecutionWithNoAdvisers(ambiance, stepResponseProto, planNode);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponse_UnknownAdvise_FiresDagCallbacksForDagPipeline() {
    String stageSetupId = generateUuid();
    String stageRuntimeId = generateUuid();
    String parentSetupId = generateUuid();
    String planId = generateUuid();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId(stageSetupId)
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode parentPlanNode =
        PlanNode.builder()
            .uuid(parentSetupId)
            .name("Stages")
            .identifier("stages")
            .stepType(StepType.newBuilder().setType("STAGES").setStepCategory(StepCategory.STAGES).build())
            .dependencyGraph(dependencyGraph)
            .build();

    PlanNode stagePlanNode =
        PlanNode.builder()
            .uuid(stageSetupId)
            .name("Stage 1")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder().setSetupId(parentSetupId).setRuntimeId(generateUuid()).build())
            .addLevels(Level.newBuilder().setSetupId(stageSetupId).setRuntimeId(stageRuntimeId).build())
            .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();

    doReturn(parentPlanNode).when(planService).fetchNode(planId, parentSetupId);
    doReturn(stagePlanNode).when(planService).fetchNode(planId, stageSetupId);
    doReturn(NodeExecution.builder().uuid(stageRuntimeId).status(Status.ABORTED).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq(stageRuntimeId), eq(NodeProjectionUtils.withStatus));
    doNothing().when(executionStrategy).endNodeExecution(eq(ambiance), any(), any());

    executionStrategy.processAdviserResponse(ambiance, adviserResponse);

    verify(dagExecutionService, times(1)).fireDagCallbacksForNoAdviserStage(ambiance, stagePlanNode, Status.ABORTED);
    verify(executionStrategy, times(1)).endNodeExecution(eq(ambiance), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testProcessAdviserResponse_UnknownAdvise_SkipsDagCallbacksForNonDagPipeline() {
    String stageSetupId = generateUuid();
    String stageRuntimeId = generateUuid();
    String parentSetupId = generateUuid();
    String planId = generateUuid();

    PlanNode parentPlanNode =
        PlanNode.builder()
            .uuid(parentSetupId)
            .name("Stages")
            .identifier("stages")
            .stepType(StepType.newBuilder().setType("STAGES").setStepCategory(StepCategory.STAGES).build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .putAllSetupAbstractions(prepareInputArgs())
            .addLevels(Level.newBuilder().setSetupId(parentSetupId).setRuntimeId(generateUuid()).build())
            .addLevels(Level.newBuilder().setSetupId(stageSetupId).setRuntimeId(stageRuntimeId).build())
            .build();

    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();

    doReturn(parentPlanNode).when(planService).fetchNode(planId, parentSetupId);
    doNothing().when(executionStrategy).endNodeExecution(eq(ambiance), any(), any());

    executionStrategy.processAdviserResponse(ambiance, adviserResponse);

    verify(dagExecutionService, never()).fireDagCallbacksForNoAdviserStage(any(), any(Node.class), any());
    verify(executionStrategy, times(1)).endNodeExecution(eq(ambiance), any(), any());
  }
}
