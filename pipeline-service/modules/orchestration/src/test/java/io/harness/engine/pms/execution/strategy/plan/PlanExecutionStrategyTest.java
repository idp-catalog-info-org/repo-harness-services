/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NAVNEET_KHANDELWAL;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.OrchestrationTestBase;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.GovernanceService;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.PriorityType;
import io.harness.governance.GovernanceMetadata;
import io.harness.observer.Subject;
import io.harness.opaclient.model.ActionContext;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Ambiance.Builder;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionStrategyTest extends OrchestrationTestBase {
  private static final String DUMMY_NODE_1_ID = generateUuid();
  private static final String DUMMY_NODE_2_ID = generateUuid();
  private static final String DUMMY_NODE_3_ID = generateUuid();

  private static final StepType DUMMY_STEP_TYPE = StepType.newBuilder().setType("DUMMY").build();

  private static final TriggeredBy triggeredBy =
      TriggeredBy.newBuilder().putExtraInfo("email", PRASHANT).setIdentifier(PRASHANT).setUuid(generateUuid()).build();

  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock OrchestrationEngine orchestrationEngine;
  @Mock PlanService planService;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock Subject<OrchestrationStartObserver> orchestrationStartSubject;
  @Mock GovernanceService governanceService;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock Subject<OrchestrationEndObserver> orchestrationEndSubject;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Spy @Inject PlanExecutionService planExecutionService;
  @Inject @InjectMocks PlanExecutionStrategy executionStrategy;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    on(executionStrategy).set("orchestrationEndSubject", orchestrationEndSubject);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder()
                    .planNode(startingNode)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_2_ID)
                                  .name("Dummy Node 2")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy2")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_3_ID)
                                  .name("Dummy Node 3")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy3")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    // shouldQueue is true. So there will be zero interactions with executorService to start the executions.
    verify(executorService, times(0)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED);
    // Will be invoked because the current execution is being queued.
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    planExecutionId = generateUuid();
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());
    // shouldQueue is false. So executorService will be called to start the execution..
    verify(executorService, times(1)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    // Will not be invoked because the current execution is being started and useNewFlow is false So invocations would
    // remain 1 as above..
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    planExecutionId = generateUuid();
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());
    verify(executorService, times(2)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    // Will be invoked because the current execution is being started but useNewFlow is true So invocations would become
    // 2 now.
    verify(waitNotifyEngine, times(2)).waitForAllOn(any(), any(), any());

    verify(orchestrationStartSubject, times(3)).fireInform(any(), any());

    // Governance will deny. So planExecution should have status errored.
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
    planExecutionId = generateUuid();
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());
    assertThat(planExecution.getStatus()).isEqualTo(Status.ERRORED);

    // Governance is denying the execution. executorService and waitNotifyEngine invocations should remain same.
    verify(executorService, times(2)).submit(any(Callable.class));
    verify(waitNotifyEngine, times(2)).waitForAllOn(any(), any(), any());

    // OrchestrationStartObserver throwing exception. PlanExecution should be marked as ERRORED.
    doThrow(new InvalidRequestException("Error Message")).when(orchestrationStartSubject).fireInform(any(), any());
    String planExecutionId1 = generateUuid();
    assertThatThrownBy(
        ()
            -> executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId1).build(), plan,
                PlanExecutionMetadataWithContext.builder()
                    .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId1).build())
                    .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error Message");
    assertThat(planExecutionService.get(planExecutionId1).getStatus()).isEqualTo(Status.ERRORED);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestRunNodeNewPlanCreationFlow() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name(), true)
                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build())
                    .build())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder()
                    .planNode(startingNode)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_2_ID)
                                  .name("Dummy Node 2")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy2")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_3_ID)
                                  .name("Dummy Node 3")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy3")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(PlanExecution.builder()
                 .uuid(ambiance.getPlanExecutionId())
                 .ambiance(ambiance.build())
                 .status(Status.RUNNING)
                 .build())
        .when(planExecutionService)
        .updateStatus(eq(planExecutionId), eq(Status.RUNNING), any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .isAsyncPlanCreation(true)
            .build());
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    verify(orchestrationStartSubject, times(1)).fireInform(any(), any());
    verify(planExecutionMetadataService, times(1))
        .updatePlanExecutionMetadata(eq(ambiance.getPlanExecutionId()), any());
    verify(executorService, times(1)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);

    // Governance will deny. So planExecution should have status errored.
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
    planExecutionId = generateUuid();
    doReturn(PlanExecution.builder()
                 .uuid(ambiance.getPlanExecutionId())
                 .ambiance(ambiance.build())
                 .status(Status.ERRORED)
                 .build())
        .when(planExecutionService)
        .markPlanExecutionErrored(any());
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .isAsyncPlanCreation(true)
            .build());
    assertThat(planExecution.getStatus()).isEqualTo(Status.ERRORED);
    // Governance is denying the execution. executorService and waitNotifyEngine invocations should remain same.
    verify(executorService, times(1)).submit(any(Callable.class));
    verify(orchestrationStartSubject, times(2)).fireInform(any(), any());

    // OrchestrationStartObserver throwing exception. PlanExecution should be marked as ERRORED.
    doThrow(new InvalidRequestException("Error Message")).when(orchestrationStartSubject).fireInform(any(), any());
    String planExecutionId1 = generateUuid();
    assertThatThrownBy(
        ()
            -> executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId1).build(), plan,
                PlanExecutionMetadataWithContext.builder()
                    .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId1).build())
                    .isAsyncPlanCreation(true)
                    .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error Message");
    verify(planExecutionService, times(2)).markPlanExecutionErrored(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldSetServiceName() {
    doReturn(Status.ERRORED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(PlanExecution.builder().status(Status.ERRORED).build())
        .when(planExecutionService)
        .updateStatus(any(), any(), any());
    ArgumentCaptor<OrchestrationEvent> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);

    executionStrategy.endNodeExecution(Ambiance.newBuilder().build(), null, null);

    verify(eventEmitter).emitEvent(argumentCaptor.capture());
    OrchestrationEvent event = argumentCaptor.getValue();
    assertThat(event.getServiceName()).isEqualTo("pms");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNode_QueuedExecution_NonMatchingLowPriorityExecution() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Mock governance service response
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());

    // Mock PipelineSettingsService to test non-matching low priority execution
    PlanExecutionSettingResponse mockResponse = PlanExecutionSettingResponse.builder()
                                                    .shouldQueue(true)
                                                    .useNewFlow(true)
                                                    .priorityExecutionLimitReached(true)
                                                    .build();

    // Mock both overloaded versions of shouldQueuePlanExecution
    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any());

    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any(), eq(PriorityType.LOW));

    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());
    doReturn(PriorityType.LOW)
        .when(pipelineSettingsService)
        .getPriorityTypeOfCurrentExecution(any(), any(), any(), anyBoolean());

    // Execute the method under test
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());

    // Verify the execution is queued with the correct status
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED);
    assertThat(planExecution.getPriorityType()).isEqualTo(PriorityType.LOW);

    // Verify the waitNotifyEngine is called to set up the callback
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    // Verify the execution is not started (executor service not called)
    verify(executorService, times(0)).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNode_QueuedExecution_MatchingHighPriorityExecution() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Mock governance service response
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());

    // Mock PipelineSettingsService to test matching high priority execution
    PlanExecutionSettingResponse mockResponse = PlanExecutionSettingResponse.builder()
                                                    .shouldQueue(true)
                                                    .useNewFlow(true)
                                                    .priorityExecutionLimitReached(true)
                                                    .build();

    // Mock both overloaded versions of shouldQueuePlanExecution
    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any());

    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any(), eq(PriorityType.HIGH));

    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());
    doReturn(PriorityType.HIGH)
        .when(pipelineSettingsService)
        .getPriorityTypeOfCurrentExecution(any(), any(), any(), anyBoolean());

    // Execute the method under test
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());

    // Verify the execution is queued with the correct status
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED);
    assertThat(planExecution.getPriorityType()).isEqualTo(PriorityType.HIGH);

    // Verify the waitNotifyEngine is called to set up the callback
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    // Verify the execution is not started (executor service not called)
    verify(executorService, times(0)).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNode_QueuedExecution_ExceedsQueueLimit() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Mock governance service response
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());

    // Mock PipelineSettingsService to test queue limit exceeded
    PlanExecutionSettingResponse mockResponse =
        PlanExecutionSettingResponse.builder().shouldQueue(true).useNewFlow(true).build();

    // Mock both overloaded versions of shouldQueuePlanExecution
    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any());

    // Mock that queue limit is exceeded
    doReturn(false).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());

    // Execute the method under test and verify it throws LimitExceededException
    assertThatThrownBy(
        ()
            -> executionStrategy.runNode(ambiance.build(), plan,
                PlanExecutionMetadataWithContext.builder()
                    .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
                    .build()))
        .isInstanceOf(io.harness.enforcement.exceptions.LimitExceededException.class)
        .hasMessageContaining("You have exceeded the number of queued executions allowed on the account");

    // Verify the execution is not started (executor service not called)
    verify(executorService, times(0)).submit(any(Callable.class));

    // Verify no callback was registered
    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNode_NonQueuedExecution_WithPriorityType() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Mock governance service response
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());

    // Mock PipelineSettingsService to test non-queued execution with priority
    PlanExecutionSettingResponse mockResponse = PlanExecutionSettingResponse.builder()
                                                    .shouldQueue(false)
                                                    .useNewFlow(true)
                                                    .priorityExecutionLimitReached(false)
                                                    .build();

    // Mock both overloaded versions of shouldQueuePlanExecution
    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any());

    doReturn(mockResponse).when(pipelineSettingsService).shouldQueuePlanExecution(any(), eq(PriorityType.HIGH));

    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());
    doReturn(PriorityType.HIGH)
        .when(pipelineSettingsService)
        .getPriorityTypeOfCurrentExecution(any(), any(), any(), anyBoolean());

    // Execute the method under test
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());

    // Verify the execution is started with the correct status
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(planExecution.getPriorityType()).isEqualTo(PriorityType.HIGH);

    // Verify the waitNotifyEngine is called to set up the callback (because useNewFlow is true)
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    // Verify the execution is started (executor service called)
    verify(executorService, times(1)).submit(any(Callable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunNode_QueueBasedPlanCreation_WithPriorityType() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name(), true)
                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build())
                    .build())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Mock governance service response
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());

    // Mock PlanExecutionService for updateStatus
    doReturn(
        PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).priorityType(PriorityType.HIGH).build())
        .when(planExecutionService)
        .updateStatus(eq(planExecutionId), eq(Status.RUNNING), any());

    // Mock both overloaded versions of shouldQueuePlanExecution
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());

    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());

    doReturn(PriorityType.HIGH)
        .when(pipelineSettingsService)
        .getPriorityTypeOfCurrentExecution(any(), any(), any(), anyBoolean());

    // Execute the method under test
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .isAsyncPlanCreation(true)
            .build());

    // Verify the execution is started with the correct status
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(planExecution.getPriorityType()).isEqualTo(PriorityType.HIGH);

    // Verify the execution is started (executor service called)
    verify(executorService, times(1)).submit(any(Callable.class));

    // Verify updatePlanExecution was called
    verify(planExecutionMetadataService, times(1)).updatePlanExecutionMetadata(eq(planExecutionId), any());
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithFailedStatus_AndFailureInfo() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with failed status
    PlanExecution failedPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    // Mock node execution with failure info
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .failureInfo(io.harness.pms.contracts.execution.failure.FailureInfo.newBuilder()
                                                       .setErrorMessage("Test failure message")
                                                       .build())
                                      .build();

    // Mock service responses
    doReturn(Status.FAILED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(failedPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());
    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), nodeExecution, null);

    // Verify that event emitter was called with failure info
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.FAILED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    assertThat(capturedEvent.getFailureInfo()).isNotNull();
    assertThat(capturedEvent.getFailureInfo().getErrorMessage()).isEqualTo("Test failure message");

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.FAILED));
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithFailedStatus_NoFailureInfo() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with failed status
    PlanExecution failedPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    // Mock node execution without failure info - use empty failure info instead of null
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .failureInfo(io.harness.pms.contracts.execution.failure.FailureInfo.newBuilder().build())
            .build();

    // Mock service responses
    doReturn(Status.FAILED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(failedPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());
    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), nodeExecution, null);

    // Verify that event emitter was called without failure info
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.FAILED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    // FailureInfo is not set since nodeExecution.failureInfo is null

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.FAILED));
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithSuccessStatus() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with success status
    PlanExecution successPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.SUCCEEDED).build();

    // Mock service responses
    doReturn(Status.SUCCEEDED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(successPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), null, null);

    // Verify that event emitter was called without failure info (since status is not failed)
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    // FailureInfo is not added for success status

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.SUCCEEDED));

    // Verify that nodeExecutionService was not called since status is not failed
    verify(nodeExecutionService, times(0)).getPipelineNodeExecutionWithProjections(any(), any());
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBuildEndEvent() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Test with different statuses
    Status[] testStatuses = {Status.SUCCEEDED, Status.FAILED, Status.ERRORED, Status.ABORTED};

    for (Status status : testStatuses) {
      // Use reflection to call the private buildEndEvent method
      OrchestrationEvent.Builder eventBuilder =
          (OrchestrationEvent.Builder) on(executionStrategy).call("buildEndEvent", ambiance.build(), status).get();

      OrchestrationEvent event = eventBuilder.build();

      // Verify the event properties
      assertThat(event.getAmbiance()).isEqualTo(ambiance.build());
      assertThat(event.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
      assertThat(event.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
      assertThat(event.getStatus()).isEqualTo(status);
    }
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithFailedStatus_FeatureFlagDisabled_ShouldAddFailureInfo() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with failed status
    PlanExecution failedPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    // Mock node execution with failure info
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .failureInfo(io.harness.pms.contracts.execution.failure.FailureInfo.newBuilder()
                                                       .setErrorMessage("Test failure message")
                                                       .build())
                                      .build();

    // Mock service responses
    doReturn(Status.FAILED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(failedPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());
    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());

    // Mock feature flag as DISABLED (should add failure info)
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("kmpySmUISimoRrJL6NL73w"), eq(FeatureName.PIPE_DISABLE_ADD_FAILURE_INFO_END_EVENT.name()));

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), null, null);

    // Verify that event emitter was called with failure info
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.FAILED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    assertThat(capturedEvent.getFailureInfo()).isNotNull();
    assertThat(capturedEvent.getFailureInfo().getErrorMessage()).isEqualTo("Test failure message");

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.FAILED));

    // Verify nodeExecutionService was called to get failure info
    verify(nodeExecutionService).getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithFailedStatus_FeatureFlagEnabled_ShouldNotAddFailureInfo() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with failed status
    PlanExecution failedPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    // Mock service responses
    doReturn(Status.FAILED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(failedPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());

    // Mock feature flag as ENABLED (should NOT add failure info)
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("kmpySmUISimoRrJL6NL73w"), eq(FeatureName.PIPE_DISABLE_ADD_FAILURE_INFO_END_EVENT.name()));

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), null, null);

    // Verify that event emitter was called without failure info
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.FAILED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    // FailureInfo should not be set when feature flag is enabled

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.FAILED));

    // Verify nodeExecutionService was NOT called when feature flag is enabled
    verify(nodeExecutionService, times(0)).getPipelineNodeExecutionWithProjections(any(), any());
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithSuccessStatus_FeatureFlagEnabled_ShouldNotCallNodeExecutionService() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with success status
    PlanExecution successPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.SUCCEEDED).build();

    // Mock service responses
    doReturn(Status.SUCCEEDED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(successPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());

    // Mock feature flag as ENABLED (should not matter for success status)
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("kmpySmUISimoRrJL6NL73w"), eq(FeatureName.PIPE_DISABLE_ADD_FAILURE_INFO_END_EVENT.name()));

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), null, null);

    // Verify that event emitter was called
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.SUCCEEDED));

    // Verify nodeExecutionService was NOT called since status is not failed
    verify(nodeExecutionService, times(0)).getPipelineNodeExecutionWithProjections(any(), any());
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testEndNodeExecution_WithFailedStatus_NodeExecutionPresentButFailureInfoNull() {
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());

    // Mock plan execution with failed status
    PlanExecution failedPlanExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    // Mock node execution with null failure info (this is the key test case for line 322-323)
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .failureInfo(null) // Explicitly set to null
                                      .build();

    // Mock service responses
    doReturn(Status.FAILED).when(planExecutionService).calculateStatus(any(), anyBoolean());
    doReturn(failedPlanExecution).when(planExecutionService).updateStatus(any(), any(), any());
    doReturn(Optional.of(nodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());

    // Mock feature flag as DISABLED (should try to add failure info)
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(eq("kmpySmUISimoRrJL6NL73w"), eq(FeatureName.PIPE_DISABLE_ADD_FAILURE_INFO_END_EVENT.name()));

    // Execute the method under test
    executionStrategy.endNodeExecution(ambiance.build(), null, null);

    // Verify that event emitter was called without failure info (since failureInfo is null)
    ArgumentCaptor<OrchestrationEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);
    verify(eventEmitter).emitEvent(eventCaptor.capture());

    OrchestrationEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getAmbiance()).isEqualTo(ambiance.build());
    assertThat(capturedEvent.getStatus()).isEqualTo(Status.FAILED);
    assertThat(capturedEvent.getServiceName()).isEqualTo(ModuleType.PMS.name().toLowerCase());
    assertThat(capturedEvent.getEventType()).isEqualTo(OrchestrationEventType.ORCHESTRATION_END);
    // FailureInfo should not be set since nodeExecution.failureInfo is null
    assertThat(capturedEvent.hasFailureInfo()).isFalse();

    // Verify orchestration end subject was fired
    verify(orchestrationEndSubject).fireInform(any(), eq(ambiance.build()), eq(Status.FAILED));

    // Verify nodeExecutionService was called to get failure info
    verify(nodeExecutionService).getPipelineNodeExecutionWithProjections(eq(planExecutionId), any());
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void shouldTestRunNodeWithRerun() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).setIsRerun(true).build())
                    .build())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());
    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    verify(executorService, times(1)).submit(any(Callable.class));
    verify(governanceService, times(1))
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void shouldTestRunNodeWithMetadataCapture() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putAllSetupAbstractions(prepareInputArgs())
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setRunSequence(5)
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).setIsRerun(false).build())
                    .build())
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder().planNode(startingNode).startingNodeId(DUMMY_NODE_1_ID).build();

    // Capture the metadata passed to governance service
    ArgumentCaptor<ActionContext> metadataCaptor = ArgumentCaptor.forClass(ActionContext.class);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), metadataCaptor.capture());

    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(any());

    PlanExecution planExecution = executionStrategy.runNode(ambiance.build(), plan,
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build())
            .build());

    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);

    // Verify metadata passed to governance service
    ActionContext capturedMetadata = metadataCaptor.getValue();
    assertThat(capturedMetadata).isNotNull();
    assertThat(capturedMetadata.getRerun()).isFalse();
    assertThat(capturedMetadata.getExecutionId()).isEqualTo(5);

    verify(executorService, times(1)).submit(any(Callable.class));
    verify(governanceService, times(1))
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any(), any());
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }
}
