/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationComponentTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionBackfillService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionSummaryBackfillServiceTest extends OrchestrationVisualizationComponentTest {
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private NodeExecutionBackfillService nodeExecutionBackfillService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private ExecutorService executorService;
  @InjectMocks private ExecutionSummaryBackfillService executionSummaryBackfillService;

  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";
  private static final String PARENT_UNIQUE_ID = "parentUniqueId1";
  private static final String MODULE = "CD";
  private static final long START_TS = 1625097600000L; // 2021-07-01
  private static final long END_TS = 1627776000000L; // 2021-08-01

  @Captor private ArgumentCaptor<Runnable> runnableCaptor;

  @Before
  public void setUp() {
    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .uniqueId(PARENT_UNIQUE_ID)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    ScopeInfo accountScopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).build();
    when(scopeResolutionHelper.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(projectScopeInfo);
    when(scopeResolutionHelper.getScopeInfo(eq(ACCOUNT_ID), eq(null), eq(null))).thenReturn(accountScopeInfo);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutions_WithFullScope() {
    String planExecutionId1 = generateUuid();
    String planExecutionId2 = generateUuid();

    PipelineExecutionSummaryEntity summary1 = createExecutionSummary(planExecutionId1);
    PipelineExecutionSummaryEntity summary2 = createExecutionSummary(planExecutionId2);

    Stream<PipelineExecutionSummaryEntity> summaryStream = Stream.of(summary1, summary2);

    when(pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(any(Query.class)))
        .thenReturn(summaryStream);

    executionSummaryBackfillService.replayNodeExecutions(ACCOUNT_ID, ORG_ID, PROJECT_ID, MODULE, START_TS, END_TS);

    verify(pmsExecutionSummaryRepository).fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    verify(executorService, times(2)).submit(runnableCaptor.capture());

    runnableCaptor.getAllValues().get(0).run();
    runnableCaptor.getAllValues().get(1).run();

    verify(nodeExecutionBackfillService).replayNodeExecutionEvents(eq(planExecutionId1), eq(MODULE));
    verify(nodeExecutionBackfillService).replayNodeExecutionEvents(eq(planExecutionId2), eq(MODULE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutions_WithAccountOnly() {
    String planExecutionId = generateUuid();
    PipelineExecutionSummaryEntity summary = createExecutionSummary(planExecutionId);
    Stream<PipelineExecutionSummaryEntity> summaryStream = Stream.of(summary);

    when(pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(any(Query.class)))
        .thenReturn(summaryStream);

    executionSummaryBackfillService.replayNodeExecutions(ACCOUNT_ID, null, null, MODULE, START_TS, END_TS);

    verify(pmsExecutionSummaryRepository).fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    verify(executorService).submit(runnableCaptor.capture());

    runnableCaptor.getValue().run();

    verify(nodeExecutionBackfillService).replayNodeExecutionEvents(eq(planExecutionId), eq(MODULE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutions_WithNoModule() {
    String planExecutionId = generateUuid();
    PipelineExecutionSummaryEntity summary = createExecutionSummary(planExecutionId);
    Stream<PipelineExecutionSummaryEntity> summaryStream = Stream.of(summary);

    when(pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(any(Query.class)))
        .thenReturn(summaryStream);

    executionSummaryBackfillService.replayNodeExecutions(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, START_TS, END_TS);

    verify(pmsExecutionSummaryRepository).fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    verify(executorService).submit(runnableCaptor.capture());

    runnableCaptor.getValue().run();

    verify(nodeExecutionBackfillService).replayNodeExecutionEvents(eq(planExecutionId), eq(null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutions_WithNoResults() {
    Stream<PipelineExecutionSummaryEntity> emptyStream = Stream.empty();

    when(pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(any(Query.class)))
        .thenReturn(emptyStream);

    executionSummaryBackfillService.replayNodeExecutions(ACCOUNT_ID, ORG_ID, PROJECT_ID, MODULE, START_TS, END_TS);

    verify(pmsExecutionSummaryRepository).fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    verify(executorService, never()).submit(any(Runnable.class));
    verify(nodeExecutionBackfillService, never()).replayNodeExecutionEvents(anyString(), anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplayNodeExecutions_ExceptionHandling() {
    String planExecutionId = generateUuid();
    PipelineExecutionSummaryEntity summary = createExecutionSummary(planExecutionId);
    Stream<PipelineExecutionSummaryEntity> summaryStream = Stream.of(summary);

    when(pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(any(Query.class)))
        .thenReturn(summaryStream);

    Mockito
        .doAnswer(invocation -> {
          Runnable runnable = invocation.getArgument(0);
          runnable.run();
          return null;
        })
        .when(executorService)
        .submit(any(Runnable.class));

    Mockito.doThrow(new RuntimeException("Test exception"))
        .when(nodeExecutionBackfillService)
        .replayNodeExecutionEvents(anyString(), anyString());

    executionSummaryBackfillService.replayNodeExecutions(ACCOUNT_ID, ORG_ID, PROJECT_ID, MODULE, START_TS, END_TS);

    verify(pmsExecutionSummaryRepository).fetchExecutionSummaryEntityFromAnalytics(any(Query.class));
    verify(executorService).submit(any(Runnable.class));
    verify(nodeExecutionBackfillService).replayNodeExecutionEvents(eq(planExecutionId), eq(MODULE));
  }

  private PipelineExecutionSummaryEntity createExecutionSummary(String planExecutionId) {
    return PipelineExecutionSummaryEntity.builder()
        .planExecutionId(planExecutionId)
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .parentUniqueId(PARENT_UNIQUE_ID)
        .startTs(START_TS)
        .endTs(END_TS)
        .build();
  }
}
