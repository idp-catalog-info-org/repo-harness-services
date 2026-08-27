/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.OM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PriorityType;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.execution.TimeRangeFilterType;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResponseDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResultDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineExecutionDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineFilterDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineListResponse;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.service.PipelineSearchService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class QueuedPipelineServiceImplTest extends CategoryTest {
  @Mock private AccessControlClient accessControlClient;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private FilterService filterService;
  @Mock private MetricService metricService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PipelineSearchService pipelineSearchService;
  @Mock private PMSExecutionService pmsExecutionService;
  @InjectMocks private QueuedPipelineServiceImpl queuedPipelineService;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projId";
  // 1 day ago
  private static final long FIXTURE_BASE_TS = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private void enableAccess() {
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString());
  }

  private void stubOrgScopeResolution(String orgId, String orgUniqueId, List<String> projectUniqueIds) {
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_ID)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .orgIdentifier(orgId)
                                 .uniqueId(orgUniqueId)
                                 .build();
    doReturn(Collections.singletonList(orgScopeInfo))
        .when(scopeResolutionHelper)
        .getScopeInfoListForOrgs(eq(ACCOUNT_ID), eq(new HashSet<>(Collections.singletonList(orgId))));
    doReturn(projectUniqueIds).when(scopeResolutionHelper).getProjectUniqueIds(eq(ACCOUNT_ID), eq(orgUniqueId));
  }

  // Legacy fixtures pass tiny monotonic offsets (1000L, 2000L, ...); anything below this threshold
  // is treated as such an offset and shifted onto FIXTURE_BASE_TS.
  private static final long SYNTHETIC_OFFSET_CUTOFF = TimeUnit.DAYS.toMillis(365L * 30);

  private PipelineExecutionSummaryEntity buildEntity(String planExecutionId, String pipelineId, String pipelineName,
      String org, String project, Status internalStatus, PriorityType priorityType, Long startTs, int runSequence) {
    return buildEntity(planExecutionId, pipelineId, pipelineName, org, project, internalStatus, priorityType, startTs,
        runSequence, null);
  }

  private PipelineExecutionSummaryEntity buildEntity(String planExecutionId, String pipelineId, String pipelineName,
      String org, String project, Status internalStatus, PriorityType priorityType, Long startTs, int runSequence,
      String parentUniqueId) {
    Long anchoredTs =
        startTs == null ? null : (startTs < SYNTHETIC_OFFSET_CUTOFF ? FIXTURE_BASE_TS + startTs : startTs);
    // bring it under the 30 day lookback window
    return PipelineExecutionSummaryEntity.builder()
        .planExecutionId(planExecutionId)
        .pipelineIdentifier(pipelineId)
        .name(pipelineName)
        .orgIdentifier(org)
        .projectIdentifier(project)
        .internalStatus(internalStatus)
        .priorityType(priorityType)
        .startTs(anchoredTs)
        .createdAt(anchoredTs)
        .runSequence(runSequence)
        .parentUniqueId(parentUniqueId)
        .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                  .setTriggerType(TriggerType.MANUAL)
                                  .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                  .build())
        .tags(Collections.emptyList())
        .build();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_accessDenied() {
    doThrow(new AccessDeniedException("Not authorized", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString());

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_invalidPageParams() {
    enableAccess();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, -1, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid page request");

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid page request");

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 101))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid page request");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_emptyQueue() {
    enableAccess();
    doReturn(Stream.empty())
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(50L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(0);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    assertThat(response.getTotalRunningInAccount()).isEqualTo(0);
    assertThat(response.getMaxConcurrency()).isEqualTo(100L);
    assertThat(response.getCurrentRunning()).isEqualTo(50L);
    assertThat(response.getQueuedExecutions().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_withResults() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);
    PipelineExecutionSummaryEntity entity3 = buildEntity("exec-3", "pipeline-c", "Pipeline C", "otherOrg", PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.LOW, 3000L, 3);

    doReturn(Stream.of(entity1, entity2, entity3))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(100L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(3);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(3);
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(content.get(1).getQueuePosition()).isEqualTo(2);
    assertThat(content.get(2).getQueuePosition()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_runningExecutionsHaveNoPosition() {
    enableAccess();

    PipelineExecutionSummaryEntity running1 = buildEntity(
        "run-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.NORMAL, 1000L, 1);
    PipelineExecutionSummaryEntity running2 = buildEntity(
        "run-2", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(running1, running2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(2L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalRunningInAccount()).isEqualTo(2);
    assertThat(response.getTotalQueuedInAccount()).isEqualTo(0);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(2);
    // Running rows hold no queue position.
    assertThat(content.get(0).getQueuePosition()).isNull();
    assertThat(content.get(1).getQueuePosition()).isNull();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_mixedQueuedAndRunning_numbersOnlyQueued() {
    enableAccess();

    // createdAt-ASC ordered: queued, running, queued
    PipelineExecutionSummaryEntity queued1 = buildEntity("q-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.NORMAL, 1000L, 1);
    PipelineExecutionSummaryEntity running1 = buildEntity(
        "run-1", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.NORMAL, 2000L, 2);
    PipelineExecutionSummaryEntity queued2 = buildEntity("q-2", "pipeline-c", "Pipeline C", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 3000L, 3);

    doReturn(Stream.of(queued1, running1, queued2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(1L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    assertThat(response.getTotalRunningInAccount()).isEqualTo(1);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(3);
    // queued1 -> position 1
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("q-1");
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    // running1 -> no position, does not consume a queue number
    assertThat(content.get(1).getPlanExecutionId()).isEqualTo("run-1");
    assertThat(content.get(1).getQueuePosition()).isNull();
    // queued2 -> position 2 (running row did not shift the queued numbering)
    assertThat(content.get(2).getPlanExecutionId()).isEqualTo("q-2");
    assertThat(content.get(2).getQueuePosition()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_waitingExecutions_sorted_byCreationTime() {
    enableAccess();

    // Mix of queued, waiting, and running executions
    PipelineExecutionSummaryEntity queued1 = buildEntity("q-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.NORMAL, 1000L, 1);
    PipelineExecutionSummaryEntity waiting1 = buildEntity(
        "w-1", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID, Status.APPROVAL_WAITING, PriorityType.NORMAL, 2000L, 2);
    PipelineExecutionSummaryEntity running1 = buildEntity(
        "run-1", "pipeline-c", "Pipeline C", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.NORMAL, 3000L, 3);
    PipelineExecutionSummaryEntity waiting2 = buildEntity(
        "w-2", "pipeline-d", "Pipeline D", ORG_ID, PROJECT_ID, Status.INPUT_WAITING, PriorityType.NORMAL, 4000L, 4);
    PipelineExecutionSummaryEntity queued2 = buildEntity("q-2", "pipeline-e", "Pipeline E", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 5000L, 5);

    doReturn(Stream.of(queued1, waiting1, running1, waiting2, queued2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(1L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(2);
    assertThat(response.getTotalRunningInAccount()).isEqualTo(1);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(5);

    // Verify results are sorted by createdAt (ascending)
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("q-1");
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    assertThat(content.get(1).getPlanExecutionId()).isEqualTo("w-1");
    assertThat(content.get(1).getQueuePosition()).isNull(); // Waiting has no queue position
    assertThat(content.get(2).getPlanExecutionId()).isEqualTo("run-1");
    assertThat(content.get(2).getQueuePosition()).isNull(); // Running has no queue position
    assertThat(content.get(3).getPlanExecutionId()).isEqualTo("w-2");
    assertThat(content.get(3).getQueuePosition()).isNull(); // Waiting has no queue position
    assertThat(content.get(4).getPlanExecutionId()).isEqualTo("q-2");
    assertThat(content.get(4).getQueuePosition()).isEqualTo(2); // Second queued execution
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_emptyOrgIdentifiersList_treatedAsNoFilter() {
    // Sending {"orgIdentifiers":[]} from the UI must behave identically to no filter —
    // EmptyPredicate.isEmpty([]) is true so no org-scoping is applied (account-wide).
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", "other-org", PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    // empty list — not null, but treated as "no filter"
    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder().orgIdentifiers(Collections.emptyList()).build();
    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);
    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    // Both entities returned — org filter did not exclude anything
    assertThat(response.getQueuedExecutions().getContent()).hasSize(2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_emptyProjectIdentifiersList_treatedAsNoFilter() {
    // {"projectIdentifiers":[]} must behave identically to no project filter.
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, "other-project", Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().projectIdentifiers(Collections.emptyList()).build();
    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    assertThat(response.getQueuedExecutions().getContent()).hasSize(2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_globalPositionsPreservedAfterFiltering() {
    enableAccess();
    stubOrgScopeResolution(ORG_ID, "org-unique-1", Arrays.asList("proj-unique-1", "proj-unique-other"));

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1, "proj-unique-1");
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "pipeline-b", "Pipeline B", "otherOrg", PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2, "proj-unique-other-org");
    PipelineExecutionSummaryEntity entity3 = buildEntity("exec-3", "pipeline-c", "Pipeline C", ORG_ID, "otherProject",
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.LOW, 3000L, 3, "proj-unique-other");

    doReturn(Stream.of(entity1, entity2, entity3))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(100L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(Collections.singletonList(ORG_ID)).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(3);
    assertThat(response.getTotalWaitingInAccount()).isEqualTo(0);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(2);
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(content.get(1).getQueuePosition()).isEqualTo(3);
    assertThat(content.get(1).getPlanExecutionId()).isEqualTo("exec-3");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_searchTermFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "deploy-prod", "Deploy to Production", ORG_ID,
        PROJECT_ID, Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "build-ci", "Build CI", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, "deploy", 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPipelineIdentifier()).isEqualTo("deploy-prod");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_pagination() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);
    PipelineExecutionSummaryEntity entity3 = buildEntity("exec-3", "p3", "P3", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.LOW, 3000L, 3);

    doReturn(Stream.of(entity1, entity2, entity3))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(50L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse page0 = queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 2);
    assertThat(page0.getQueuedExecutions().getContent()).hasSize(2);
    assertThat(page0.getQueuedExecutions().getTotalElements()).isEqualTo(3);
    assertThat(page0.getQueuedExecutions().getTotalPages()).isEqualTo(2);
    assertThat(page0.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(page0.getQueuedExecutions().getContent().get(1).getPlanExecutionId()).isEqualTo("exec-2");

    doReturn(Stream.of(entity1, entity2, entity3))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    QueuedPipelineListResponse page1 = queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 1, 2);
    assertThat(page1.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(page1.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-3");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_pageOutOfRange() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);

    doReturn(Stream.of(entity1))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 5, 20);
    assertThat(response.getQueuedExecutions().getContent()).isEmpty();
    assertThat(response.getTotalQueuedInAccount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_statusFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder()
            .statuses(Collections.singletonList(ExecutionStatus.QUEUED_EXECUTION_CONCURRENCY_REACHED))
            .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_priorityFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().priorityTypes(Collections.singletonList(PriorityType.HIGH)).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_timeRangeFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 5000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder()
            .queuedTimeRange(
                TimeRange.builder().startTime(FIXTURE_BASE_TS + 3000L).endTime(FIXTURE_BASE_TS + 6000L).build())
            .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-2");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_timeRangeFilterTypeThisMonth() {
    enableAccess();

    ZoneId zone = ZoneId.systemDefault();
    LocalDate today = LocalDate.now(zone);
    LocalDate firstDayOfCurrentMonth = today.with(TemporalAdjusters.firstDayOfMonth());
    long thisMonthStart = firstDayOfCurrentMonth.atStartOfDay(zone).toInstant().toEpochMilli();
    // Every lookup is also clamped to a rolling 30-day window, so on the 31st of a month the effective
    // start lands after the calendar month start. Anchoring the in-range fixture to the start of today
    // keeps it inside both bounds on any day of the year.
    long todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, thisMonthStart - 86_400_000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, todayStart, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder()
            .queuedTimeRange(TimeRange.builder().timeRangeFilterType(TimeRangeFilterType.THIS_MONTH).build())
            .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-2");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_dropsEntriesOlderThan30DaysWhenNoFilter() {
    enableAccess();

    long now = System.currentTimeMillis();
    long thirtyDaysMs = TimeUnit.DAYS.toMillis(30);

    // entity1 is 40 days old → must be filtered out by the unconditional 30-day cap.
    PipelineExecutionSummaryEntity tooOld = buildEntity("exec-old", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, now - TimeUnit.DAYS.toMillis(40), 1);
    PipelineExecutionSummaryEntity inWindow = buildEntity("exec-recent", "p2", "P2", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, now - TimeUnit.DAYS.toMillis(5), 2);

    doReturn(Stream.of(tooOld, inWindow))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-recent");
    // Account-wide counters reflect the unfiltered fetch; only the page content is capped.
    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    // Sanity check that the synthetic timestamp really is outside the cap.
    assertThat(now - TimeUnit.DAYS.toMillis(40)).isLessThan(now - thirtyDaysMs);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_clampsExplicitStartTimeOlderThan30Days() {
    enableAccess();

    long now = System.currentTimeMillis();

    PipelineExecutionSummaryEntity tooOld = buildEntity("exec-old", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, now - TimeUnit.DAYS.toMillis(60), 1);
    PipelineExecutionSummaryEntity inWindow = buildEntity("exec-recent", "p2", "P2", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, now - TimeUnit.DAYS.toMillis(10), 2);

    doReturn(Stream.of(tooOld, inWindow))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    // Caller asks for a 90-day window; the start should be silently clamped to now - 30d.
    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder()
            .queuedTimeRange(TimeRange.builder().startTime(now - TimeUnit.DAYS.toMillis(90)).endTime(now).build())
            .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-recent");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_clampsRelativeTimeOlderThan30Days() {
    enableAccess();

    long now = System.currentTimeMillis();

    PipelineExecutionSummaryEntity tooOld = buildEntity("exec-old", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, now - TimeUnit.DAYS.toMillis(50), 1);
    PipelineExecutionSummaryEntity inWindow = buildEntity("exec-recent", "p2", "P2", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, now - TimeUnit.DAYS.toMillis(15), 2);

    doReturn(Stream.of(tooOld, inWindow))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().queuedTimeRange(TimeRange.builder().relativeTime("-90d").build()).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-recent");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_tagFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-1")
            .pipelineIdentifier("p1")
            .name("P1")
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .internalStatus(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED)
            .priorityType(PriorityType.HIGH)
            .startTs(FIXTURE_BASE_TS + 1000L)
            .createdAt(FIXTURE_BASE_TS + 1000L)
            .runSequence(1)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tag(NGTag.builder().key("env").value("prod").build())
            .build();

    PipelineExecutionSummaryEntity entity2 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-2")
            .pipelineIdentifier("p2")
            .name("P2")
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .internalStatus(Status.QUEUED_PLAN_CREATION)
            .priorityType(PriorityType.NORMAL)
            .startTs(FIXTURE_BASE_TS + 2000L)
            .createdAt(FIXTURE_BASE_TS + 2000L)
            .runSequence(2)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tag(NGTag.builder().key("env").value("staging").build())
            .build();

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .pipelineTags(Arrays.asList(NGTag.builder().key("env").value("prod").build()))
                                         .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_triggerTypeFilter() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-2")
            .pipelineIdentifier("p2")
            .name("P2")
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .internalStatus(Status.QUEUED_PLAN_CREATION)
            .priorityType(PriorityType.NORMAL)
            .startTs(FIXTURE_BASE_TS + 2000L)
            .createdAt(FIXTURE_BASE_TS + 2000L)
            .runSequence(2)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("github-trigger").build())
                                      .build())
            .tags(Collections.emptyList())
            .build();

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().triggerTypes(Collections.singletonList(TriggerType.WEBHOOK)).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPlanExecutionId()).isEqualTo("exec-2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_combinedFilters() {
    enableAccess();
    stubOrgScopeResolution(ORG_ID, "org-unique-1", Collections.singletonList("proj-unique-1"));

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "deploy-prod", "Deploy Prod", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1, "proj-unique-1");
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "deploy-staging", "Deploy Staging", ORG_ID,
        PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2, "proj-unique-1");
    PipelineExecutionSummaryEntity entity3 = buildEntity("exec-3", "build-ci", "Build CI", "otherOrg", PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.LOW, 3000L, 3, "proj-unique-other");

    doReturn(Stream.of(entity1, entity2, entity3))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(Collections.singletonList(ORG_ID)).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, "deploy", 0, 20);

    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(2);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    assertThat(content.get(1).getPlanExecutionId()).isEqualTo("exec-2");
    assertThat(content.get(1).getQueuePosition()).isEqualTo(2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_verifyAccessCheckParams() {
    enableAccess();
    doReturn(Stream.empty())
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, null, null)), eq(Resource.of("ACCOUNT", ACCOUNT_ID)),
            eq("core_account_edit"));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testListQueuedPipelines_elasticSearchPath() {
    enableAccess();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_ENABLE_ELASTIC_SEARCH));

    // ElasticSearchStream is mocked to yield two DTOs; the impl streams it and collects planExecutionIds.
    PipelineSearchReadExecutionSummaryDTO dto1 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-1").runSequence(1).build();
    PipelineSearchReadExecutionSummaryDTO dto2 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-2").runSequence(2).build();

    ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> mockStream =
        (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO>) mock(ElasticSearchStream.class);
    doReturn(Arrays.asList(dto1, dto2).spliterator()).when(mockStream).spliterator();
    doReturn(mockStream)
        .when(pipelineSearchService)
        .fetchPipelineSearchReadExecutionSummaryDTO(eq(ACCOUNT_ID), any(), any(), any());

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Arrays.asList(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(Criteria.class), any(List.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(50L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(2);
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(content.get(1).getQueuePosition()).isEqualTo(2);
    assertThat(content.get(1).getPlanExecutionId()).isEqualTo("exec-2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testListQueuedPipelines_elasticSearchPath_emptyResults() {
    enableAccess();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_ENABLE_ELASTIC_SEARCH));

    ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> mockStream =
        (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO>) mock(ElasticSearchStream.class);
    doReturn(Collections.<PipelineSearchReadExecutionSummaryDTO>emptyList().spliterator())
        .when(mockStream)
        .spliterator();
    doReturn(mockStream)
        .when(pipelineSearchService)
        .fetchPipelineSearchReadExecutionSummaryDTO(eq(ACCOUNT_ID), any(), any(), any());
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(0);
    assertThat(response.getQueuedExecutions().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testListQueuedPipelines_elasticSearchPath_dropsDriftedMongoStatus() {
    // ES says exec-1 is QUEUED_PLAN_CREATION and exec-2 is QUEUED_EXECUTION_CONCURRENCY_REACHED,
    // but Mongo has already moved exec-2 to SUCCESS. The drifted row must be filtered out before
    // it reaches the response.
    enableAccess();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_ENABLE_ELASTIC_SEARCH));

    PipelineSearchReadExecutionSummaryDTO dto1 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-1").runSequence(1).build();
    PipelineSearchReadExecutionSummaryDTO dto2 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-2").runSequence(2).build();

    ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> mockStream =
        (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO>) mock(ElasticSearchStream.class);
    doReturn(Arrays.asList(dto1, dto2).spliterator()).when(mockStream).spliterator();
    doReturn(mockStream)
        .when(pipelineSearchService)
        .fetchPipelineSearchReadExecutionSummaryDTO(eq(ACCOUNT_ID), any(), any(), any());

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 1000L, 1);
    // Drift: ES still considers exec-2 queued, but Mongo has moved on to SUCCESS.
    PipelineExecutionSummaryEntity entity2Drifted = buildEntity(
        "exec-2", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID, Status.SUCCEEDED, PriorityType.NORMAL, 2000L, 2);

    doReturn(Arrays.asList(entity1, entity2Drifted))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(Criteria.class), any(List.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, null, null, 0, 20);

    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(response.getTotalQueuedInAccount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testListQueuedPipelines_elasticSearchPath_dropsDriftWhenStatusFilterPresent() {
    // Caller filters for QUEUED_PLAN_CREATION only. ES returns two ids; Mongo says exec-1 is
    // QUEUED_PLAN_CREATION (matches) and exec-2 is RUNNING (drifted out of the caller's filter).
    enableAccess();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(eq(ACCOUNT_ID), eq(FeatureName.PIPE_ENABLE_ELASTIC_SEARCH));

    PipelineSearchReadExecutionSummaryDTO dto1 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-1").runSequence(1).build();
    PipelineSearchReadExecutionSummaryDTO dto2 =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId("exec-2").runSequence(2).build();

    ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> mockStream =
        (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO>) mock(ElasticSearchStream.class);
    doReturn(Arrays.asList(dto1, dto2).spliterator()).when(mockStream).spliterator();
    doReturn(mockStream)
        .when(pipelineSearchService)
        .fetchPipelineSearchReadExecutionSummaryDTO(eq(ACCOUNT_ID), any(), any(), any());

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "pipeline-a", "Pipeline A", ORG_ID, PROJECT_ID,
        Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 1000L, 1);
    PipelineExecutionSummaryEntity entity2Running = buildEntity(
        "exec-2", "pipeline-b", "Pipeline B", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.NORMAL, 2000L, 2);

    doReturn(Arrays.asList(entity1, entity2Running))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(Criteria.class), any(List.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .statuses(Collections.singletonList(ExecutionStatus.QUEUED_PLAN_CREATION))
                                         .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
  }

  // ─── Case 2: account + org IDs ────────────────────────────────────────────────

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_scopeInfoOrgFilter() {
    enableAccess();

    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_ID)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .orgIdentifier(ORG_ID)
                                 .uniqueId("org-unique-1")
                                 .build();

    Set<String> expectedOrgIdSet = new HashSet<>(Collections.singletonList(ORG_ID));
    doReturn(Collections.singletonList(orgScopeInfo))
        .when(scopeResolutionHelper)
        .getScopeInfoListForOrgs(eq(ACCOUNT_ID), eq(expectedOrgIdSet));
    doReturn(Arrays.asList("proj-unique-1", "proj-unique-2"))
        .when(scopeResolutionHelper)
        .getProjectUniqueIds(eq(ACCOUNT_ID), eq("org-unique-1"));

    PipelineExecutionSummaryEntity entity1 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-1")
            .pipelineIdentifier("p1")
            .name("P1")
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .internalStatus(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED)
            .priorityType(PriorityType.HIGH)
            .startTs(FIXTURE_BASE_TS + 1000L)
            .createdAt(FIXTURE_BASE_TS + 1000L)
            .runSequence(1)
            .parentUniqueId("proj-unique-1")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tags(Collections.emptyList())
            .build();

    PipelineExecutionSummaryEntity entity2 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-2")
            .pipelineIdentifier("p2")
            .name("P2")
            .orgIdentifier("otherOrg")
            .projectIdentifier("otherProject")
            .internalStatus(Status.QUEUED_PLAN_CREATION)
            .priorityType(PriorityType.NORMAL)
            .startTs(FIXTURE_BASE_TS + 2000L)
            .createdAt(FIXTURE_BASE_TS + 2000L)
            .runSequence(2)
            .parentUniqueId("proj-unique-other")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tags(Collections.emptyList())
            .build();

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(Collections.singletonList(ORG_ID)).build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(content.get(0).getQueuePosition()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_scopeInfoServiceError_propagatesException() {
    enableAccess();

    Set<String> expectedOrgIdSet = new HashSet<>(Collections.singletonList(ORG_ID));
    doThrow(new RuntimeException("ScopeInfo service unavailable"))
        .when(scopeResolutionHelper)
        .getScopeInfoListForOrgs(eq(ACCOUNT_ID), eq(expectedOrgIdSet));

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(Collections.singletonList(ORG_ID)).build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to resolve scope info while filtering queued pipelines")
        .hasMessageContaining("ScopeInfo service unavailable");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_unresolvedOrgIds_throwsInvalidScope() {
    enableAccess();

    // Request two orgs but the service only knows about one (the second is a typo / deleted)
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_ID)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .orgIdentifier(ORG_ID)
                                 .uniqueId("org-unique-1")
                                 .build();

    Set<String> requestedOrgIds = new HashSet<>(Arrays.asList(ORG_ID, "ghost-org"));
    doReturn(Collections.singletonList(orgScopeInfo))
        .when(scopeResolutionHelper)
        .getScopeInfoListForOrgs(eq(ACCOUNT_ID), eq(requestedOrgIds));

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(Arrays.asList(ORG_ID, "ghost-org")).build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not resolve scope info for org identifier(s)")
        .hasMessageContaining("ghost-org");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_unresolvedProjectIds_throwsInvalidScope() {
    enableAccess();

    // Request two projects but the service only knows about one (the second is a typo / deleted)
    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .uniqueId("proj-unique-1")
                                     .build();

    Set<String> requestedProjectIds = new HashSet<>(Arrays.asList(PROJECT_ID, "ghost-project"));
    doReturn(Collections.singletonList(projectScopeInfo))
        .when(scopeResolutionHelper)
        .getScopeInfoListForProjects(eq(ACCOUNT_ID), eq(ORG_ID), eq(requestedProjectIds));

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .orgIdentifiers(Collections.singletonList(ORG_ID))
                                         .projectIdentifiers(Arrays.asList(PROJECT_ID, "ghost-project"))
                                         .build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not resolve scope info for project identifier(s)")
        .hasMessageContaining("ghost-project")
        .hasMessageContaining(ORG_ID);
  }

  // ─── Case 3: account + no org + project IDs → invalid ────────────────────────

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_projectsWithoutOrg_throwsInvalidScope() {
    // Case 3 validation fires before the ScopeInfo FF check — always invalid regardless of FF
    enableAccess();

    QueuedPipelineFilterDTO filter =
        QueuedPipelineFilterDTO.builder().projectIdentifiers(Collections.singletonList(PROJECT_ID)).build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("projectIdentifiers cannot be provided without at least one orgIdentifier");
  }

  // ─── Case 4: account + org + project IDs → only 1 org allowed ────────────────

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_multipleOrgsWithProjects_throwsInvalidScope() {
    // Case 4 validation fires before the ScopeInfo FF check — always invalid regardless of FF
    enableAccess();

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .orgIdentifiers(Arrays.asList(ORG_ID, "anotherOrg"))
                                         .projectIdentifiers(Collections.singletonList(PROJECT_ID))
                                         .build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("exactly one orgIdentifier must be provided when projectIdentifiers are specified");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_orgAndProjectFilter_scopeInfo() {
    enableAccess();

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .uniqueId("proj-unique-1")
                                     .build();

    Set<String> expectedProjectIdSet = new HashSet<>(Collections.singletonList(PROJECT_ID));
    doReturn(Collections.singletonList(projectScopeInfo))
        .when(scopeResolutionHelper)
        .getScopeInfoListForProjects(eq(ACCOUNT_ID), eq(ORG_ID), eq(expectedProjectIdSet));

    PipelineExecutionSummaryEntity entity1 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-1")
            .pipelineIdentifier("p1")
            .name("P1")
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .internalStatus(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED)
            .priorityType(PriorityType.HIGH)
            .startTs(FIXTURE_BASE_TS + 1000L)
            .createdAt(FIXTURE_BASE_TS + 1000L)
            .runSequence(1)
            .parentUniqueId("proj-unique-1")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tags(Collections.emptyList())
            .build();

    PipelineExecutionSummaryEntity entity2 =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("exec-2")
            .pipelineIdentifier("p2")
            .name("P2")
            .orgIdentifier(ORG_ID)
            .projectIdentifier("otherProject")
            .internalStatus(Status.QUEUED_PLAN_CREATION)
            .priorityType(PriorityType.NORMAL)
            .startTs(FIXTURE_BASE_TS + 2000L)
            .createdAt(FIXTURE_BASE_TS + 2000L)
            .runSequence(2)
            .parentUniqueId("proj-unique-other")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("user@test.com").build())
                                      .build())
            .tags(Collections.emptyList())
            .build();

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .orgIdentifiers(Collections.singletonList(ORG_ID))
                                         .projectIdentifiers(Collections.singletonList(PROJECT_ID))
                                         .build();

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20);

    assertThat(response.getTotalQueuedInAccount()).isEqualTo(2);
    List<QueuedPipelineExecutionDTO> content = response.getQueuedExecutions().getContent();
    assertThat(content).hasSize(1);
    assertThat(content.get(0).getPlanExecutionId()).isEqualTo("exec-1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_multipleOrgsAndProjects_throwsInvalidScope() {
    // The "exactly 1 org" constraint is an API invariant — always invalid.
    enableAccess();

    QueuedPipelineFilterDTO filter = QueuedPipelineFilterDTO.builder()
                                         .orgIdentifiers(Arrays.asList(ORG_ID, "org-2"))
                                         .projectIdentifiers(Collections.singletonList(PROJECT_ID))
                                         .build();

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, null, filter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("exactly one orgIdentifier must be provided when projectIdentifiers are specified");
  }

  // ─── Bulk abort tests ────────────────────────────────────────────────────────

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_emptyInput() {
    enableAccess();

    assertThatThrownBy(() -> queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, Collections.emptyList()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("planExecutionIds cannot be empty");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_exceedsLimit() {
    enableAccess();

    List<String> tooManyIds = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      tooManyIds.add("exec-" + i);
    }

    assertThatThrownBy(() -> queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, tooManyIds))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot abort more than 500 executions at once");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_allSuccess() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity entity2 = buildEntity(
        "exec-2", "p2", "P2", ORG_ID, PROJECT_ID, Status.QUEUED_PLAN_CREATION, PriorityType.NORMAL, 2000L, 2);

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));

    QueuedPipelineBulkAbortResponseDTO response =
        queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, Arrays.asList("exec-1", "exec-2"));

    assertThat(response.getSuccessCount()).isEqualTo(2);
    assertThat(response.getFailureCount()).isEqualTo(0);
    assertThat(response.getResults()).hasSize(2);
    assertThat(response.getResults().get(0).isSuccess()).isTrue();
    assertThat(response.getResults().get(1).isSuccess()).isTrue();

    verify(pmsExecutionService).registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-1"), eq(null));
    verify(pmsExecutionService).registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-2"), eq(null));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_partialSuccess() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);

    doReturn(Stream.of(entity1))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));

    QueuedPipelineBulkAbortResponseDTO response =
        queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, Arrays.asList("exec-1", "exec-not-found"));

    assertThat(response.getSuccessCount()).isEqualTo(1);
    assertThat(response.getFailureCount()).isEqualTo(1);

    QueuedPipelineBulkAbortResultDTO successResult = response.getResults().get(0);
    assertThat(successResult.getPlanExecutionId()).isEqualTo("exec-1");
    assertThat(successResult.isSuccess()).isTrue();

    QueuedPipelineBulkAbortResultDTO failureResult = response.getResults().get(1);
    assertThat(failureResult.getPlanExecutionId()).isEqualTo("exec-not-found");
    assertThat(failureResult.isSuccess()).isFalse();
    assertThat(failureResult.getErrorMessage()).isEqualTo("Execution not found or not in an abortable state");

    verify(pmsExecutionService).registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-1"), eq(null));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testBulkAbort_successForRunningAndWaitingStatuses() {
    enableAccess();

    PipelineExecutionSummaryEntity runningEntity =
        buildEntity("exec-running", "p1", "P1", ORG_ID, PROJECT_ID, Status.RUNNING, PriorityType.HIGH, 1000L, 1);
    PipelineExecutionSummaryEntity waitingEntity = buildEntity(
        "exec-waiting", "p2", "P2", ORG_ID, PROJECT_ID, Status.APPROVAL_WAITING, PriorityType.NORMAL, 2000L, 2);
    PipelineExecutionSummaryEntity inputWaitingEntity =
        buildEntity("exec-input", "p3", "P3", ORG_ID, PROJECT_ID, Status.INPUT_WAITING, PriorityType.LOW, 3000L, 3);

    doReturn(Stream.of(runningEntity, waitingEntity, inputWaitingEntity))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));

    QueuedPipelineBulkAbortResponseDTO response = queuedPipelineService.bulkAbortQueuedPipelines(
        ACCOUNT_ID, Arrays.asList("exec-running", "exec-waiting", "exec-input"));

    assertThat(response.getSuccessCount()).isEqualTo(3);
    assertThat(response.getFailureCount()).isEqualTo(0);
    verify(pmsExecutionService)
        .registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-running"), eq(null));
    verify(pmsExecutionService)
        .registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-waiting"), eq(null));
    verify(pmsExecutionService).registerInterrupt(eq(PlanExecutionInterruptType.ABORTALL), eq("exec-input"), eq(null));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testBulkAbort_rejectsCompletedExecution() {
    enableAccess();

    doReturn(Stream.empty())
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));

    QueuedPipelineBulkAbortResponseDTO response =
        queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, List.of("exec-completed"));

    assertThat(response.getSuccessCount()).isEqualTo(0);
    assertThat(response.getFailureCount()).isEqualTo(1);
    assertThat(response.getResults().get(0).isSuccess()).isFalse();
    assertThat(response.getResults().get(0).getErrorMessage())
        .isEqualTo("Execution not found or not in an abortable state");
    verify(pmsExecutionService, never()).registerInterrupt(any(), eq("exec-completed"), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_interruptRegistrationFailure() {
    enableAccess();

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1);

    doReturn(Stream.of(entity1))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doThrow(new RuntimeException("Interrupt registration failed"))
        .when(pmsExecutionService)
        .registerInterrupt(any(), eq("exec-1"), any());

    QueuedPipelineBulkAbortResponseDTO response =
        queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, List.of("exec-1"));

    assertThat(response.getSuccessCount()).isEqualTo(0);
    assertThat(response.getFailureCount()).isEqualTo(1);
    assertThat(response.getResults().get(0).isSuccess()).isFalse();
    assertThat(response.getResults().get(0).getErrorMessage()).isEqualTo("Interrupt registration failed");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBulkAbort_accessDenied() {
    doThrow(new AccessDeniedException("Not authorized", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), anyString());

    assertThatThrownBy(() -> queuedPipelineService.bulkAbortQueuedPipelines(ACCOUNT_ID, List.of("exec-1")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_filterIdentifierResolvesAndApplies() {
    enableAccess();
    stubOrgScopeResolution(ORG_ID, "org-unique-1", Collections.singletonList("proj-unique-1"));

    PipelineExecutionSummaryEntity entity1 = buildEntity("exec-1", "p1", "P1", ORG_ID, PROJECT_ID,
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.HIGH, 1000L, 1, "proj-unique-1");
    PipelineExecutionSummaryEntity entity2 = buildEntity("exec-2", "p2", "P2", "otherOrg", "otherProject",
        Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, PriorityType.NORMAL, 2000L, 2, "proj-unique-other");

    doReturn(Stream.of(entity1, entity2))
        .when(pmsExecutionSummaryRepository)
        .fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    doReturn(100L).when(pipelineSettingsService).getMaxConcurrency(ACCOUNT_ID);
    doReturn(0L).when(pipelineSettingsService).getCurrentExecutionCount(ACCOUNT_ID);

    QueuedPipelineFilterDTO savedProps =
        QueuedPipelineFilterDTO.builder().orgIdentifiers(List.of(ORG_ID)).pipelineIdentifiers(List.of("p1")).build();
    FilterDTO savedFilter = FilterDTO.builder().filterProperties(savedProps).build();
    doReturn(savedFilter)
        .when(filterService)
        .get(eq(ACCOUNT_ID), eq(null), eq(null), eq("my-saved-filter"), eq(FilterType.QUEUED_PIPELINE));

    QueuedPipelineListResponse response =
        queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, "my-saved-filter", null, null, 0, 20);

    assertThat(response.getQueuedExecutions().getContent()).hasSize(1);
    assertThat(response.getQueuedExecutions().getContent().get(0).getPipelineIdentifier()).isEqualTo("p1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_filterIdentifierNotFound_throwsException() {
    enableAccess();

    doReturn(null)
        .when(filterService)
        .get(eq(ACCOUNT_ID), eq(null), eq(null), eq("non-existent"), eq(FilterType.QUEUED_PIPELINE));

    assertThatThrownBy(() -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, "non-existent", null, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("non-existent");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListQueuedPipelines_filterIdentifierAndInlineFilterBothProvided_throwsException() {
    enableAccess();

    QueuedPipelineFilterDTO inlineFilter = QueuedPipelineFilterDTO.builder().pipelineIdentifiers(List.of("p1")).build();

    assertThatThrownBy(
        () -> queuedPipelineService.listQueuedPipelines(ACCOUNT_ID, "some-filter-id", inlineFilter, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot apply both");
  }
}
