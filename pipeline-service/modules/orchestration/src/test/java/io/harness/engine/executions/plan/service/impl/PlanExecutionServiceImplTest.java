/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.engine.pms.execution.manual.beans.ManualExecutionAction.MARK_AS_FAIL;
import static io.harness.engine.pms.execution.manual.beans.ManualExecutionAction.MARK_AS_RESUME;
import static io.harness.execution.PlanExecution.PlanExecutionKeys;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationTestBase;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.helpers.PipelineStageStatusHelper;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.interrupts.statusupdate.PausedStepStatusUpdate;
import io.harness.engine.interrupts.statusupdate.QueuedLicenseLimitReachedStatusUpdate;
import io.harness.engine.interrupts.statusupdate.UploadWaitingStepStatusUpdate;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PriorityType;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.FacilitatorExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.planexecution.PlanExecutionRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionServiceImplTest extends OrchestrationTestBase {
  @Mock NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PausedStepStatusUpdate pausedStepStatusUpdate;
  @Mock QueuedLicenseLimitReachedStatusUpdate queuedLicenseLimitReachedStatusUpdate;
  @Mock UploadWaitingStepStatusUpdate uploadWaitingStepStatusUpdate;
  @Mock Subject<PlanExecutionDeleteObserver> planExecutionDeleteObserverSubject;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Spy @Inject @InjectMocks PlanExecutionService planExecutionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock AccessControlClient accessControlClient;
  @Mock PipelineStageStatusHelper pipelineStageStatusHelper;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String ORG_ID = "ORG_ID";
  private final String PROJECT_ID = "PROJECT_ID";
  private final String PLAN_EXECUTION_ID = "planExecutionId";
  private final String PIPELINE_IDENTIFIER = "p1";
  private final ExecutionMetadata EXECUTION_METADATA =
      ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_IDENTIFIER).build();
  private final ExecutionContext EXECUTION_CONTEXT =
      ExecutionContext.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();
  private final Ambiance AMBIANCE = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(SettingValueResponseDTO.builder().value("false").build());
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String planExecutionId = generateUuid();
    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);
  }

  @Test

  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFindAllByPlanExecutionIdIn() {
    String planExecutionId = generateUuid();
    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);

    List<PlanExecution> planExecutions =
        planExecutionService.findAllByPlanExecutionIdIn(ImmutableList.of(planExecutionId));

    assertThat(planExecutions).isNotEmpty();
    assertThat(planExecutions.size()).isEqualTo(1);
    assertThat(planExecutions).extracting(PlanExecution::getUuid).containsExactly(planExecutionId);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void shouldTestFindAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps() {
    String planExecutionId = generateUuid();
    String accountId = "TestAccountId";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";
    long startTS = System.currentTimeMillis();

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder()
                                                                 .uuid(planExecutionId)
                                                                 .setupAbstractions(setupAbstractions)
                                                                 .lastUpdatedAt(System.currentTimeMillis())
                                                                 .build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId)).isEqualTo(accountId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier)).isEqualTo(orgId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier)).isEqualTo(projectId);

    long endTS = System.currentTimeMillis() + 5 * 60 * 1000;

    List<PlanExecution> planExecutions =
        planExecutionService.findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
            accountId, orgId, projectId, startTS, endTS);

    assertThat(planExecutions).isNotEmpty();
    assertThat(planExecutions.size()).isEqualTo(1);
    assertThat(planExecutions).extracting(PlanExecution::getUuid).containsExactly(planExecutionId);
    assertThat(planExecutions).extracting(PlanExecution::getSetupAbstractions).containsExactly(setupAbstractions);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusWithoutOps() {
    String uuid = generateUuid();
    planExecutionService.updateStatus(uuid, Status.RUNNING);
    verify(planExecutionService, times(1)).updateStatus(uuid, Status.RUNNING, null);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusWithOps() {
    String uuid = generateUuid();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis());
    planExecutionService.updateStatus(uuid, Status.RUNNING, op);
    verify(planExecutionService, times(1)).updateStatusForceful(uuid, Status.RUNNING, op, false);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusForcefulReturningNull() {
    String planExecutionId = generateUuid();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis());
    PlanExecution planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, true);
    assertNull(planExecution);
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(SUCCEEDED).build());
    planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, false);
    assertNull(planExecution);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusForceful() {
    String planExecutionId = generateUuid();
    long endTs = System.currentTimeMillis();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, endTs);
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(PAUSED).build());
    PlanExecution planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, false);
    assertEquals(planExecution.getUuid(), planExecutionId);
    assertEquals(planExecution.getStatus(), Status.ABORTED);
    assertEquals(planExecution.getEndTs().longValue(), endTs);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusForcefulWithoutEndTs() {
    String planExecutionId = generateUuid();
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(PAUSED).build());
    PlanExecution planExecution =
        planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, null, true);
    assertEquals(planExecution.getUuid(), planExecutionId);
    assertEquals(planExecution.getStatus(), Status.ABORTED);
    assertThat(planExecution.getEndTs()).isNotNull();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestGet() {
    String planExecutionId = generateUuid();
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    assertEquals(planExecution.getUuid(), planExecutionId);
  }

  @Test

  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldFetchPlanExecutionsByStatus() {
    String planExecutionId = generateUuid();
    String accountId = "TestAccountId";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    planExecutionService.save(PlanExecution.builder()
                                  .uuid(planExecutionId)
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.WAIT_STEP_RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.APPROVAL_WAITING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());

    List<PlanExecution> finalList = new LinkedList<>();
    try (Stream<PlanExecution> stream =
             planExecutionService.fetchPlanExecutionsByStatusFromAnalytics(StatusUtils.activeStatuses(),
                 ImmutableSet.of(PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.metadata))) {
      stream.forEach(planExecution -> { finalList.add(planExecution); });
    }
    assertEquals(finalList.size(), 4);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdate() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.PAUSED).build()).build();
    doReturn(pausedStepStatusUpdate).when(nodeStatusUpdateHandlerFactory).obtainStepStatusUpdate(nodeUpdateInfo);
    planExecutionService.onNodeStatusUpdate(nodeUpdateInfo);
    verify(pausedStepStatusUpdate, times(1)).handleNodeStatusUpdate(nodeUpdateInfo);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWithQueueLimit() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder().status(Status.QUEUED_LICENSE_LIMIT_REACHED).build())
            .build();
    doReturn(queuedLicenseLimitReachedStatusUpdate)
        .when(nodeStatusUpdateHandlerFactory)
        .obtainStepStatusUpdate(nodeUpdateInfo);
    planExecutionService.onNodeStatusUpdate(nodeUpdateInfo);
    verify(queuedLicenseLimitReachedStatusUpdate, times(1)).handleNodeStatusUpdate(nodeUpdateInfo);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWithUploadWaiting() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.UPLOAD_WAITING).build()).build();
    doReturn(uploadWaitingStepStatusUpdate).when(nodeStatusUpdateHandlerFactory).obtainStepStatusUpdate(nodeUpdateInfo);
    planExecutionService.onNodeStatusUpdate(nodeUpdateInfo);
    verify(uploadWaitingStepStatusUpdate, times(1)).handleNodeStatusUpdate(nodeUpdateInfo);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindPrevUnTerminatedPlanExecutionsByExecutionTag() {
    String planExecutionId = generateUuid();
    String executionTag = "exec";
    long createdAt = System.currentTimeMillis();
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(Status.RUNNING)
            .createdAt(createdAt)
            .metadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(TriggeredBy.newBuilder()
                                                .putExtraInfo("execution_trigger_tag_needed_for_abort", executionTag)
                                                .build())
                            .build())
                    .build())
            .build();
    planExecutionService.save(planExecution);
    List<PlanExecution> planExecution1 = planExecutionService.findPrevUnTerminatedPlanExecutionsByExecutionTag(
        PlanExecution.builder().createdAt(System.currentTimeMillis()).build(), executionTag);
    assertEquals(planExecution1.size(), 1);
    assertEquals(planExecutionId, planExecution1.get(0).getUuid());
    assertEquals(createdAt, planExecution1.get(0).getCreatedAt().longValue());
    assertEquals(Status.RUNNING, planExecution1.get(0).getStatus());
    assertEquals(executionTag,
        planExecution1.get(0).getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfo().get(
            "execution_trigger_tag_needed_for_abort"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindUnterminatedPlanExecutionsByExecutionTag() {
    String planExecutionId = generateUuid();
    String executionTag = "merge-queue-exec-tag";
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(Status.RUNNING)
            .createdAt(System.currentTimeMillis())
            .metadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(TriggeredBy.newBuilder()
                                                .putExtraInfo("execution_trigger_tag_needed_for_abort", executionTag)
                                                .build())
                            .build())
                    .build())
            .build();
    planExecutionService.save(planExecution);

    // no createdAt filter: unlike findPrevUnTerminatedPlanExecutionsByExecutionTag, this must find the
    // execution even though there is no newer execution to compare against.
    List<PlanExecution> found = planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(executionTag);
    assertEquals(1, found.size());
    assertEquals(planExecutionId, found.get(0).getUuid());

    // The query projects to the id only, so nothing else should come back over the wire. Callers that need
    // more than the id must widen the projection rather than silently reading nulls.
    assertNull(found.get(0).getMetadata());
    assertNull(found.get(0).getStatus());

    assertEquals(0, planExecutionService.findUnterminatedPlanExecutionsByExecutionTag("unrelated-tag").size());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindUnterminatedPlanExecutionsByExecutionTagFindsQueuedPlanCreation() {
    // StatusUtils.resumableStatuses() contains neither QUEUED_PLAN_CREATION nor STARTING_PLAN_CREATION, yet
    // PlanCreationQueueRequestHelper persists exactly QUEUED_PLAN_CREATION while
    // PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS is on. Such an execution is unterminated and must be
    // found, otherwise a merge queue checks_canceled has nothing to abort and the stale build runs to completion.
    for (Status pendingStatus : new Status[] {Status.QUEUED_PLAN_CREATION, Status.STARTING_PLAN_CREATION}) {
      String planExecutionId = generateUuid();
      String executionTag = "merge-queue-exec-tag-" + pendingStatus.name();
      planExecutionService.save(
          PlanExecution.builder()
              .uuid(planExecutionId)
              .status(pendingStatus)
              .createdAt(System.currentTimeMillis())
              .metadata(
                  ExecutionMetadata.newBuilder()
                      .setTriggerInfo(
                          ExecutionTriggerInfo.newBuilder()
                              .setTriggeredBy(TriggeredBy.newBuilder()
                                                  .putExtraInfo("execution_trigger_tag_needed_for_abort", executionTag)
                                                  .build())
                              .build())
                      .build())
              .build());

      List<PlanExecution> found = planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(executionTag);
      assertEquals(1, found.size());
      assertEquals(planExecutionId, found.get(0).getUuid());
    }
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestCalculateStatus() {
    String planExecutionId = generateUuid();
    doReturn(ImmutableList.of(Status.RUNNING, Status.FAILED, Status.ABORTED))
        .when(nodeExecutionService)
        .fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId, false);
    Status status = planExecutionService.calculateStatus(planExecutionId);
    assertEquals(Status.ABORTED, status);
  }

  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void shouldTestCalculateStatusExcludingIdentityNode() {
    String planExecutionId = generateUuid();
    doReturn(ImmutableList.of(Status.RUNNING, Status.FAILED, Status.ABORTED))
        .when(nodeExecutionService)
        .fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId, true);
    Status status = planExecutionService.calculateStatus(planExecutionId);
    assertEquals(Status.ABORTED, status);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateCalculatedStatus() {
    String planExecutionId = generateUuid();
    doReturn(ImmutableList.of(Status.RUNNING, Status.PAUSED))
        .when(nodeExecutionService)
        .fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId, false);
    Status status = planExecutionService.calculateStatus(planExecutionId);
    planExecutionService.save(PlanExecution.builder().status(Status.QUEUED).uuid(planExecutionId).build());
    PlanExecution planExecution = planExecutionService.updateCalculatedStatus(planExecutionId);
    verify(planExecutionService, times(1)).updateStatus(planExecutionId, status);
    assertEquals(Status.RUNNING, planExecution.getStatus());
    assertEquals(planExecutionId, planExecution.getUuid());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestCalculateAndUpdateRunningStatus() {
    String planExecutionId = generateUuid();
    // Check if planExecution is RUNNING
    planExecutionService.save(PlanExecution.builder().status(Status.RUNNING).uuid(planExecutionId).build());
    planExecutionService.calculateAndUpdateRunningStatusUnderLock(planExecutionId, null);
    verify(nodeExecutionService, times(0)).fetchNonFlowingAndNonFinalStatuses(planExecutionId);

    // Check if planExecution is not RUNNING
    planExecutionService.updateStatus(planExecutionId, Status.WAIT_STEP_RUNNING);
    List<Status> statuses = Arrays.asList(Status.RUNNING, Status.INTERVENTION_WAITING, Status.INTERVENTION_WAITING);
    // Doing to keep the code testing consistent
    List<Status> collectStatuses = statuses.stream().collect(Collectors.toList());
    doReturn(collectStatuses).when(nodeExecutionService).fetchNonFlowingAndNonFinalStatuses(planExecutionId);
    planExecutionService.calculateAndUpdateRunningStatusUnderLock(planExecutionId, Status.INTERVENTION_WAITING);
    assertThat(planExecutionService.getStatus(planExecutionId)).isEqualTo(Status.INTERVENTION_WAITING);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindByStatusWithProjections() {
    planExecutionService.save(PlanExecution.builder().status(Status.NO_OP).build());
    planExecutionService.save(PlanExecution.builder().status(Status.ABORTED).build());
    planExecutionService.save(PlanExecution.builder().status(Status.SUCCEEDED).build());
    List<PlanExecution> planExecutions = planExecutionService.findByStatusWithProjections(
        ImmutableSet.of(Status.ABORTED, Status.SUCCEEDED, Status.FAILED),
        ImmutableSet.of(PlanExecutionKeys.uuid, PlanExecutionKeys.status, PlanExecutionKeys.endTs));
    assertEquals(2, planExecutions.size());
    assertEquals(Status.ABORTED, planExecutions.get(0).getStatus());
    assertEquals(Status.SUCCEEDED, planExecutions.get(1).getStatus());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestDeleteAllPlanExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(planExecutionService).set("mongoTemplate", mongoTemplateMock);
    PlanExecutionRepository planExecutionRepositoryMock = Mockito.mock(PlanExecutionRepository.class);
    Reflect.on(planExecutionService).set("planExecutionRepository", planExecutionRepositoryMock);
    Reflect.on(planExecutionService).set("planExecutionDeleteObserverSubject", planExecutionDeleteObserverSubject);

    List<PlanExecution> planExecutionList = new LinkedList<>();
    Set<String> planExecutionIds = new HashSet<>();
    for (int i = 0; i < 1200; i++) {
      String uuid = generateUuid();
      planExecutionIds.add(uuid);
      planExecutionList.add(PlanExecution.builder().uuid(uuid).build());
    }

    Stream<PlanExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(planExecutionList.iterator()).stream();
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    for (String fieldName : PlanExecutionProjectionConstants.fieldsForPlanExecutionDelete) {
      query.fields().include(fieldName);
    }
    doReturn(iterator).when(planExecutionRepositoryMock).fetchPlanExecutionsFromAnalytics(query);

    planExecutionService.deleteAllPlanExecutionAndMetadata(planExecutionIds, false, null);

    verify(planExecutionDeleteObserverSubject, times(2)).fireInform(any(), any(), anyBoolean(), any());
    verify(planExecutionRepositoryMock, times(1)).deleteAllByUuidIn(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestUpdateTTLForAllPlanExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(planExecutionService).set("mongoTemplate", mongoTemplateMock);
    PlanExecutionRepository planExecutionRepositoryMock = Mockito.mock(PlanExecutionRepository.class);
    Reflect.on(planExecutionService).set("planExecutionRepository", planExecutionRepositoryMock);

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    planExecutionService.updateTTL(generateUuid(), ttlExpiry);

    verify(planExecutionRepositoryMock, times(1)).multiUpdatePlanExecution(any(), any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestAggregateRunningExecutionCountPerAccount() {
    Map<String, String> m1 = new HashMap<>();
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(Status.RUNNING).build());
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(Status.APPROVAL_WAITING).build());
    m1.put(SetupAbstractionKeys.accountId, generateUuid());
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(SUCCEEDED).build());

    List<PlanExecutionCountWithAccountResult> accountResults =
        planExecutionService.aggregateActiveExecutionsCountPerAccount();
    assertThat(accountResults.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCountRunningExecutionsForGivenPipelineInAccountIgnoreWaitingStatuses() {
    Map<String, String> m1 = new HashMap<>();
    m1.put(SetupAbstractionKeys.accountId, "accountId");
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(Status.RUNNING).build());
    m1.put(SetupAbstractionKeys.accountId, "accountId");
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(Status.APPROVAL_WAITING).build());
    m1.put(SetupAbstractionKeys.accountId, "accountId");
    planExecutionService.save(
        PlanExecution.builder().setupAbstractions(m1).uuid(generateUuid()).status(Status.RESOURCE_WAITING).build());

    long actualRunningExecutionsCount =
        planExecutionService.countRunningExecutionsForGivenPipelineInAccount("accountId");

    assertEquals(actualRunningExecutionsCount, 3);

    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("accountId", FeatureName.CDS_IGNORE_APPROVAL_WAITING_FROM_CONCURRENT);

    actualRunningExecutionsCount = planExecutionService.countRunningExecutionsForGivenPipelineInAccount("accountId");

    assertEquals(actualRunningExecutionsCount, 1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCountRunningExecutionsForGivenPriorityInAccount() {
    Map<String, String> m1 = new HashMap<>();
    m1.put(SetupAbstractionKeys.accountId, "accountId");

    // Create executions with different priority types and statuses
    planExecutionService.save(PlanExecution.builder()
                                  .setupAbstractions(m1)
                                  .uuid(generateUuid())
                                  .status(Status.RUNNING)
                                  .priorityType(PriorityType.HIGH)
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .setupAbstractions(m1)
                                  .uuid(generateUuid())
                                  .status(Status.APPROVAL_WAITING)
                                  .priorityType(PriorityType.HIGH)
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .setupAbstractions(m1)
                                  .uuid(generateUuid())
                                  .status(Status.RUNNING)
                                  .priorityType(PriorityType.LOW)
                                  .build());

    // Test without feature flag
    long highPriorityCount =
        planExecutionService.countRunningExecutionsForGivenPriorityInAccount("accountId", PriorityType.HIGH);
    assertEquals(highPriorityCount, 2);

    long lowPriorityCount =
        planExecutionService.countRunningExecutionsForGivenPriorityInAccount("accountId", PriorityType.LOW);
    assertEquals(lowPriorityCount, 1);

    // Test with feature flag enabled
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("accountId", FeatureName.CDS_IGNORE_APPROVAL_WAITING_FROM_CONCURRENT);

    highPriorityCount =
        planExecutionService.countRunningExecutionsForGivenPriorityInAccount("accountId", PriorityType.HIGH);
    assertEquals(highPriorityCount, 1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleManualExecutionSuccess() {
    String nodeExecutionId = generateUuid();
    String callbackId = generateUuid();
    FacilitatorExecutableResponse facilitatorResponse =
        FacilitatorExecutableResponse.newBuilder().addCallbackIds(callbackId).build();
    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .executionContext(EXECUTION_CONTEXT)
                                      .uuid(nodeExecutionId)
                                      .executableResponses(Collections.singletonList(executableResponse))
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    doReturn(EXECUTION_METADATA).when(planExecutionService).getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionService.handleManualExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_RESUME, null);
    verify(planExecutionService).getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    verify(waitNotifyEngine)
        .doneWith(eq(callbackId), eq(ManualExecutionResponseData.builder().action(MARK_AS_RESUME).build()));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleManualExecution_noExecutableResponses() {
    String nodeExecutionId = generateUuid();
    doReturn(EXECUTION_METADATA).when(planExecutionService).getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    NodeExecution nodeExecution =
        NodeExecution.builder().executionContext(EXECUTION_CONTEXT).uuid(nodeExecutionId).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Node Execution is not a Manual execution for nodeExecutionId: " + nodeExecutionId);

    nodeExecution = NodeExecution.builder()
                        .executionContext(EXECUTION_CONTEXT)
                        .uuid(nodeExecutionId)
                        .executableResponses(Collections.emptyList())
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Node Execution is not a Manual execution for nodeExecutionId: " + nodeExecutionId);

    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().build();
    nodeExecution = NodeExecution.builder()
                        .executionContext(EXECUTION_CONTEXT)
                        .uuid(nodeExecutionId)
                        .executableResponses(Collections.singletonList(executableResponse))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Node Execution is not a Manual execution for nodeExecutionId: " + nodeExecutionId);

    FacilitatorExecutableResponse facilitatorResponse = FacilitatorExecutableResponse.newBuilder().build();
    executableResponse = ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    nodeExecution = NodeExecution.builder()
                        .ambiance(AMBIANCE)
                        .uuid(nodeExecutionId)
                        .executableResponses(Collections.singletonList(executableResponse))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid callback exists for resuming Manual execution for nodeExecutionId: " + nodeExecutionId);

    executableResponse = ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build();
    nodeExecution = NodeExecution.builder()
                        .ambiance(AMBIANCE)
                        .uuid(nodeExecutionId)
                        .executableResponses(Collections.singletonList(executableResponse))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Node Execution is not a Manual execution for nodeExecutionId: " + nodeExecutionId);

    facilitatorResponse = FacilitatorExecutableResponse.newBuilder().addAllCallbackIds(Collections.emptyList()).build();
    executableResponse = ExecutableResponse.newBuilder().setFacilitator(facilitatorResponse).build();
    nodeExecution = NodeExecution.builder()
                        .ambiance(AMBIANCE)
                        .uuid(nodeExecutionId)
                        .executableResponses(Collections.singletonList(executableResponse))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), any());
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_FAIL, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid callback exists for resuming Manual execution for nodeExecutionId: " + nodeExecutionId);

    doThrow(new NGAccessDeniedException("Access denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_RESUME, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Access denied");
    when(planExecutionService.getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID)).thenReturn(null);
    assertThatThrownBy(()
                           -> planExecutionService.handleManualExecution(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, nodeExecutionId, MARK_AS_RESUME, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Plan Execution metadata not found for id: planExecutionId");

    verify(planExecutionService, times(8)).getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID);
    verify(nodeExecutionService, times(8))
        .getWithFieldsIncluded(eq(nodeExecutionId),
            eq(Sets.newHashSet(NodeExecutionKeys.executableResponses, NodeExecutionKeys.executionContext,
                NodeExecutionKeys.ambiance)));
    verify(accessControlClient, times(7))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCalculateAndUpdateRunningStatusForStageAndPlanUnderLock_PlanAlreadyRunning() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    // Mock plan execution status as RUNNING
    doReturn(Status.RUNNING).when(planExecutionService).getStatus(planExecutionId);
    planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    // Verify that method returns early when plan is already RUNNING
    verify(nodeExecutionService, never()).fetchWaitingStatusNodeExecutions(any(), any());
    verify(nodeExecutionService, never()).fetchDistinctWaitingStatusesForPlan(any(), any(), any());
    verify(planExecutionService, never()).updateStatusForceful(any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCalculateAndUpdateRunningStatusForStageAndPlanUnderLock_LegacyPath() {
    String planExecutionId = generateUuid();
    String currentNodeExecutionId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setRuntimeId(generateUuid())
                           .build())
            .build();
    doReturn(Status.APPROVAL_WAITING).when(planExecutionService).getStatus(planExecutionId);

    // Mock waiting node executions
    List<NodeExecution> waitingNodeExecutions =
        Arrays.asList(NodeExecution.builder().uuid(currentNodeExecutionId).status(Status.APPROVAL_WAITING).build());

    when(nodeExecutionService.fetchWaitingStatusNodeExecutions(
             planExecutionId, NodeProjectionUtils.withAmbianceAndStatusProjected))
        .thenReturn(waitingNodeExecutions);

    // Enable legacy FF to trigger old path
    try (MockedStatic<AmbianceUtils> ambianceUtilsMock =
             Mockito.mockStatic(AmbianceUtils.class, invocation -> invocation.callRealMethod())) {
      ambianceUtilsMock
          .when(()
                    -> AmbianceUtils.checkIfFeatureFlagEnabled(
                        any(), eq(FeatureName.PIPE_ROLLBACK_LEGACY_RESUME_STATUS_RECALC.name())))
          .thenReturn(true);

      planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    }

    verify(nodeExecutionService, times(1)).updateCalculatedStatusForParentStageNode(any(), any());
    verify(planExecutionService)
        .updateStatusForceful(
            eq(planExecutionId), eq(Status.APPROVAL_WAITING), eq(null), eq(false), eq(StatusUtils.waitingStatuses()));
    verify(pipelineStageStatusHelper).updatePipelineAndStageRunningStatus(ambiance, Status.APPROVAL_WAITING);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCalculateAndUpdateRunningStatusForStageAndPlanUnderLock_OptimizedPath() {
    String planExecutionId = generateUuid();
    String currentNodeExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setRuntimeId(stageRuntimeId)
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(currentNodeExecutionId).build())
            .build();
    doReturn(Status.APPROVAL_WAITING).when(planExecutionService).getStatus(planExecutionId);

    // Mock distinct waiting statuses at plan level — one waiter remains
    when(nodeExecutionService.fetchDistinctWaitingStatusesForPlan(
             eq(planExecutionId), eq(currentNodeExecutionId), eq(stageRuntimeId)))
        .thenReturn(Arrays.asList(Status.APPROVAL_WAITING));

    // FF disabled (default) triggers optimized path
    planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);

    // Plan status should stay at APPROVAL_WAITING since a waiter remains and it already matches
    verify(planExecutionService, never()).updateStatusForceful(any(), any(), any(), anyBoolean(), any());
    verify(pipelineStageStatusHelper, never()).updatePipelineAndStageRunningStatus(any(), any());
    // Legacy methods should not be called
    verify(nodeExecutionService, never()).fetchWaitingStatusNodeExecutions(any(), any());
    verify(nodeExecutionService, never()).updateCalculatedStatusForParentStageNode(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCalculateAndUpdateRunningStatusForStageAndPlanUnderLock_OptimizedPath_NoWaiters() {
    String planExecutionId = generateUuid();
    String currentNodeExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setRuntimeId(stageRuntimeId)
                           .build())
            .addLevels(Level.newBuilder().setRuntimeId(currentNodeExecutionId).build())
            .build();
    doReturn(Status.APPROVAL_WAITING).when(planExecutionService).getStatus(planExecutionId);

    // Mock: no waiters remain at either level
    when(nodeExecutionService.fetchDistinctWaitingStatusesForStage(
             eq(planExecutionId), eq(stageRuntimeId), eq(currentNodeExecutionId)))
        .thenReturn(Collections.emptyList());
    when(nodeExecutionService.fetchDistinctWaitingStatusesForPlan(
             eq(planExecutionId), eq(currentNodeExecutionId), eq(stageRuntimeId)))
        .thenReturn(Collections.emptyList());

    planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);

    // Verify stage transitions to RUNNING
    verify(nodeExecutionService)
        .updateStatusWithOps(eq(stageRuntimeId), eq(Status.RUNNING), eq(null), eq(StatusUtils.waitingStatuses()));
    // Verify plan transitions to RUNNING
    verify(planExecutionService)
        .updateStatusForceful(
            eq(planExecutionId), eq(Status.RUNNING), eq(null), eq(false), eq(StatusUtils.waitingStatuses()));
    verify(pipelineStageStatusHelper).updatePipelineAndStageRunningStatus(ambiance, Status.RUNNING);
  }
}
