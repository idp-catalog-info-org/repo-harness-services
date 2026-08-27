/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.jobs;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.gitmetadata.service.ExecutionGitMetadataReconciliationEntityService;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.exception.InternalServerErrorException;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PIPELINE)
public class ExecutionGitMetadataReconciliationIteratorTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private ExecutionGitMetadataReconciliationEntityService reconciliationEntityService;
  @Mock private PipelineExecutionGitMetadataService executionGitMetadataService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @InjectMocks private ExecutionGitMetadataReconciliationIterator iterator;

  @Captor private ArgumentCaptor<ScopeInfo> scopeInfoCaptor;
  @Captor private ArgumentCaptor<String> uuidCaptor;
  @Captor private ArgumentCaptor<Long> timestampCaptor;

  private ExecutionGitMetadataReconciliationEntity reconciliationEntity;
  private String entityUuid;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Reflect.on(iterator).set("syncJobMaxRunTime", Duration.ofMinutes(30));
    entityUuid = UUID.randomUUID().toString();
    reconciliationEntity = ExecutionGitMetadataReconciliationEntity.builder()
                               .uuid(entityUuid)
                               .syncCompletedUntil(1000L)
                               .syncUntil(2000L)
                               .status(ExecutionGitMetadataReconciliationStatus.IN_PROGRESS)
                               .build();
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString())).thenAnswer(invocation -> {
      String accountId = invocation.getArgument(0);
      String parentUniqueId = invocation.getArgument(1);
      String suffix = parentUniqueId.replace("parent", "");
      return ScopeInfo.builder()
          .accountIdentifier(accountId)
          .orgIdentifier("org" + suffix)
          .projectIdentifier("project" + suffix)
          .uniqueId(parentUniqueId)
          .scopeType(ScopeLevel.PROJECT)
          .build();
    });
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleWithNoExecutions() {
    Stream<PipelineExecutionSummaryEntity> emptyStream = Stream.empty();
    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
             eq(null), eq(1000L), eq(2000L), anySet()))
        .thenReturn(emptyStream);

    iterator.handle(reconciliationEntity);

    verify(reconciliationEntityService).updateStatus(entityUuid, ExecutionGitMetadataReconciliationStatus.COMPLETE);
    verify(reconciliationEntityService, never()).updateSyncCompletedUntil(anyString(), anyLong());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleWithExecutions() {
    List<PipelineExecutionSummaryEntity> executionList = createExecutionSummaries(5, true);
    Stream<PipelineExecutionSummaryEntity> executionStream = executionList.stream();

    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
             eq(null), eq(1000L), eq(2000L), anySet()))
        .thenReturn(executionStream);

    iterator.handle(reconciliationEntity);

    verify(reconciliationEntityService).updateStatus(entityUuid, ExecutionGitMetadataReconciliationStatus.COMPLETE);
    verify(reconciliationEntityService).updateSyncCompletedUntil(entityUuid, 1005L); // Last execution endTs
    verify(executionGitMetadataService, times(5))
        .upsert(scopeInfoCaptor.capture(), anyString(), eq("repo"), eq("main"));

    ScopeInfo lastScopeInfo = scopeInfoCaptor.getAllValues().get(4);
    assertThat(lastScopeInfo.getAccountIdentifier()).isEqualTo("account5");
    assertThat(lastScopeInfo.getOrgIdentifier()).isEqualTo("org5");
    assertThat(lastScopeInfo.getProjectIdentifier()).isEqualTo("project5");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleWithLargeBatch() {
    List<PipelineExecutionSummaryEntity> executionList = createExecutionSummaries(1001, true);
    Stream<PipelineExecutionSummaryEntity> executionStream = executionList.stream();

    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
             eq(null), eq(1000L), eq(2000L), anySet()))
        .thenReturn(executionStream);

    iterator.handle(reconciliationEntity);

    verify(reconciliationEntityService, times(2)).updateSyncCompletedUntil(eq(entityUuid), timestampCaptor.capture());
    List<Long> capturedTimestamps = timestampCaptor.getAllValues();

    assertThat(capturedTimestamps.get(0)).isEqualTo(2000L); // endTs of 1000th record
    assertThat(capturedTimestamps.get(1)).isEqualTo(2001L); // endTs of last record

    verify(reconciliationEntityService).updateStatus(entityUuid, ExecutionGitMetadataReconciliationStatus.COMPLETE);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleWithException() {
    Stream<PipelineExecutionSummaryEntity> mockStream = mock(Stream.class);
    Iterator<PipelineExecutionSummaryEntity> mockIterator = mock(Iterator.class);

    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(any(), any(), any(), any()))
        .thenReturn(mockStream);
    when(mockStream.iterator()).thenReturn(mockIterator);
    when(mockIterator.hasNext()).thenReturn(true);
    when(mockIterator.next()).thenThrow(new RuntimeException("Test exception"));

    assertThatThrownBy(() -> iterator.handle(reconciliationEntity))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed while reconciling git metadata");

    verify(reconciliationEntityService).updateNextIteration(eq(entityUuid), anyLong());

    verify(reconciliationEntityService, never())
        .updateStatus(entityUuid, ExecutionGitMetadataReconciliationStatus.COMPLETE);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessExecutionSummaryWithNullEntityGitDetails() {
    List<PipelineExecutionSummaryEntity> executionList = createExecutionSummaries(1001, false);
    Stream<PipelineExecutionSummaryEntity> executionStream = executionList.stream();

    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
             eq(null), eq(1000L), eq(2000L), anySet()))
        .thenReturn(executionStream);

    iterator.handle(reconciliationEntity);

    verify(reconciliationEntityService).updateStatus(entityUuid, ExecutionGitMetadataReconciliationStatus.COMPLETE);

    verify(reconciliationEntityService, times(2)).updateSyncCompletedUntil(eq(entityUuid), anyLong());
    verify(executionGitMetadataService, never()).upsert(any(), any(), any(), any());
  }

  // Helper method to create test execution summaries
  private List<PipelineExecutionSummaryEntity> createExecutionSummaries(int count, boolean addGitDetails) {
    List<PipelineExecutionSummaryEntity> list = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      EntityGitDetails gitDetails = EntityGitDetails.builder().repoName("repo").branch("main").build();

      PipelineExecutionSummaryEntityBuilder summary = PipelineExecutionSummaryEntity.builder()
                                                          .accountId("account" + i)
                                                          .orgIdentifier("org" + i)
                                                          .projectIdentifier("project" + i)
                                                          .pipelineIdentifier("pipeline" + i)
                                                          .parentUniqueId("parent" + i)
                                                          .endTs(1000L + i);
      if (addGitDetails) {
        summary.entityGitDetails(gitDetails);
      }
      list.add(summary.build());
    }
    return list;
  }
}
