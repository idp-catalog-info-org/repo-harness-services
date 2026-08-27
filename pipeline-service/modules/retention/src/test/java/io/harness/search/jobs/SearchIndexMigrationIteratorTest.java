/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.jobs;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_INCLUDING_ENDS;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;
import static io.harness.search.entity.beans.PipelineSearchMigrationStatus.COMPLETE;
import static io.harness.search.entity.beans.PipelineSearchMigrationStatus.FAILED;
import static io.harness.search.entity.beans.PipelineSearchMigrationStatus.IN_PROGRESS;
import static io.harness.search.entity.beans.PipelineSearchMigrationStatus.NOT_STARTED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.rule.Owner;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity.PipelineSearchIndexMigrationEntityKeys;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.service.PipelineSearchIndexMigrationService;
import io.harness.search.service.PipelineSearchService;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.tasks.GetTasksRequest;
import co.elastic.clients.elasticsearch.tasks.GetTasksResponse;
import co.elastic.clients.elasticsearch.tasks.TaskInfo;
import co.elastic.clients.util.ObjectBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class SearchIndexMigrationIteratorTest extends CategoryTest {
  @Mock private ElasticSearchClient elasticsearchClient;
  @Mock private PipelineSearchService pipelineSearchService;
  @Mock private PipelineSearchIndexMigrationService indexMigrationService;
  @Mock private PipelineRetentionService pipelineRetentionService;
  @Mock PersistentLocker persistentLocker;
  @InjectMocks SearchIndexMigrationIterator iterator;

  private final String ACCOUNT_ID = "accountID";
  private final String OLD_INDEX_NAME = "pms-execution-alias-6-month";
  private final String NEW_INDEX_NAME = "pms-accountid-execution-alias-12-month";
  private final String UUID = "UUID";
  private final Long startTs = 1727150478000L;
  private final Long runningTimeinNanos = TimeUnit.MINUTES.toNanos(5);
  private final Long endTs = startTs + TimeUnit.NANOSECONDS.toMillis(runningTimeinNanos);
  private final Long queryStartTs = 1727149878000L;
  private final Long queryEndTs = 1727151078000L;
  private final Query accountQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
      EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, ACCOUNT_ID);
  private final Query timeQuery = ElasticSearchQueryBuilder.buildRangeQuery(
      RANGE_INCLUDING_ENDS, PipelineSearchExecutionSummaryDTOKeys.endTs, queryStartTs, queryEndTs);
  private final PipelineSearchIndexMigrationEntity notStartedEntity =
      PipelineSearchIndexMigrationEntity.builder()
          .status(NOT_STARTED)
          .accountIdentifier(ACCOUNT_ID)
          .uuid(UUID)
          .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
          .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
          .build();
  private final PipelineSearchIndexMigrationEntity inProgressEntity =
      PipelineSearchIndexMigrationEntity.builder()
          .status(IN_PROGRESS)
          .elasticTaskID("task1")
          .accountIdentifier(ACCOUNT_ID)
          .uuid(UUID)
          .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
          .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
          .build();
  private final PipelineSearchIndexMigrationEntity inProgressEntityWithBufferTask =
      PipelineSearchIndexMigrationEntity.builder()
          .status(IN_PROGRESS)
          .elasticTaskID("task1")
          .elasticBufferSyncTaskID("task2")
          .accountIdentifier(ACCOUNT_ID)
          .uuid(UUID)
          .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
          .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
          .build();
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(iterator).set("pipelineSearchService", pipelineSearchService);
    Reflect.on(iterator).set("indexMigrationService", indexMigrationService);
    Reflect.on(iterator).set("pipelineRetentionService", pipelineRetentionService);
    Reflect.on(iterator).set("elasticsearchClient", elasticsearchClient);
    AcquiredLock<?> acquiredLock = mock(AcquiredLock.class);
    doReturn(acquiredLock).when(persistentLocker).waitToAcquireLockOptional(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandlePendingFailIndexCreation() {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);

    doThrow(new InvalidRequestException("[ELASTIC_SEARCH]: Unable to create the index"))
        .when(pipelineSearchService)
        .createIndexAlias(eq(ACCOUNT_ID), eq(ACCOUNT_RETENTION_12_MONTHS));
    assertThatThrownBy(() -> iterator.handle(notStartedEntity))
        .hasMessage(
            "[ELASTIC_SEARCH]: Failed while migrating the account: accountID from index: pms-execution-alias-6-month to new index: pms-accountid-execution-alias-12-month");
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(eq(ACCOUNT_ID), eq(FAILED), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandlePendingReindexThrowException() {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    doNothing().when(pipelineSearchService).createIndexAlias(eq(ACCOUNT_ID), eq(ACCOUNT_RETENTION_12_MONTHS));
    when(pipelineSearchService.reIndexDocuments(
             eq(ACCOUNT_ID), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME), queryArgumentCaptor.capture()))
        .thenThrow(new InternalServerErrorException("[ELASTIC_SEARCH]: Error while reindexing documents for account"));
    assertThatThrownBy(() -> iterator.handle(notStartedEntity))
        .hasMessage(
            "[ELASTIC_SEARCH]: Failed while migrating the account: accountID from index: pms-execution-alias-6-month to new index: pms-accountid-execution-alias-12-month");
    assertThat(queryArgumentCaptor.getValue().toString())
        .isEqualTo(ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery)).toString());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(ACCOUNT_ID, FAILED, OLD_INDEX_NAME, NEW_INDEX_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandlePendingSuccess() {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, IN_PROGRESS);
    update.set(PipelineSearchIndexMigrationEntityKeys.elasticTaskID, "taskId1");

    ReindexResponse mockResponse = mock(ReindexResponse.class);
    when(mockResponse.task()).thenReturn("taskId1");

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    doNothing().when(pipelineSearchService).createIndexAlias(eq(ACCOUNT_ID), eq(ACCOUNT_RETENTION_12_MONTHS));
    when(pipelineSearchService.reIndexDocuments(
             eq(ACCOUNT_ID), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME), queryArgumentCaptor.capture()))
        .thenReturn(mockResponse);

    iterator.handle(notStartedEntity);
    assertThat(queryArgumentCaptor.getValue())
        .hasToString(ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery)).toString());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(eq(ACCOUNT_ID), eq(IN_PROGRESS), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusTaskBufferReindexListFailed() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(null);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(OLD_INDEX_NAME), eq(10000)))
        .thenThrow(new InternalServerErrorException("[ELASTIC_SEARCH]: Error while fetching documents for account"));
    assertThatThrownBy(() -> iterator.handle(inProgressEntity))
        .hasMessage("[ELASTIC_SEARCH]: Failed while reindexing buffer records for elastic for accountId: accountID");
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task1");
    assertThat(queryArgumentCaptor.getValue())
        .hasToString(
            ElasticSearchQueryBuilder
                .buildNestedQuery(CONSTANT_SCORE, null,
                    ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery, timeQuery)))
                .toString());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(ACCOUNT_ID, FAILED, OLD_INDEX_NAME, NEW_INDEX_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusTaskBufferReindexFailed() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(null);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(OLD_INDEX_NAME), eq(10000)))
        .thenReturn(List.of("executionId1", "executionId2", "executionId3", "executionId4"));
    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(NEW_INDEX_NAME), eq(10000)))
        .thenReturn(List.of("executionId1", "executionId3", "executionId5", "executionId6"));
    when(pipelineSearchService.reIndexDocuments(
             eq(ACCOUNT_ID), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME), queryArgumentCaptor.capture()))
        .thenThrow(new InternalServerErrorException("[ELASTIC_SEARCH]: Error while reindexing documents for account"));
    assertThatThrownBy(() -> iterator.handle(inProgressEntity))
        .hasMessage("[ELASTIC_SEARCH]: Failed while reindexing buffer records for elastic for accountId: accountID");

    Query query = ElasticSearchQueryBuilder.buildNestedQuery(CONSTANT_SCORE, null,
        ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery, timeQuery)));
    List<Query> queries = queryArgumentCaptor.getAllValues();
    assertThat(queries.get(0)).hasToString(query.toString());
    assertThat(queries.get(1)).hasToString(query.toString());
    assertThat(queries.get(2))
        .hasToString(
            "Query: {\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"accountID\"}}},{\"terms\":{\"planExecutionId\":[\"executionId2\",\"executionId4\"]}}]}}");
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task1");
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(ACCOUNT_ID, FAILED, OLD_INDEX_NAME, NEW_INDEX_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusTaskFailed() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);

    ErrorCause errorCause = new ErrorCause.Builder()
                                .type("illegal_argument_exception")
                                .reason("invalid sequence of tokens near ['value'].")
                                .build();

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(errorCause);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    ReindexResponse reindexResponse = mock(ReindexResponse.class);
    when(reindexResponse.task()).thenReturn("taskId2");

    iterator.handle(inProgressEntity);
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task1");
    verify(pipelineSearchService, times(0)).reIndexDocuments(any(), any(), any(), any());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(ACCOUNT_ID, FAILED, OLD_INDEX_NAME, NEW_INDEX_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusSuccess() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.elasticBufferSyncTaskID, "taskId2");
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationStartTime, startTs);
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationEndTime, endTs);

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(null);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    ReindexResponse reindexResponse = mock(ReindexResponse.class);
    when(reindexResponse.task()).thenReturn("taskId2");

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(OLD_INDEX_NAME), eq(10000)))
        .thenReturn(List.of("executionId1", "executionId2", "executionId3", "executionId4"));
    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(NEW_INDEX_NAME), eq(10000)))
        .thenReturn(List.of("executionId1", "executionId3", "executionId5", "executionId6"));
    when(pipelineSearchService.reIndexDocuments(
             eq(ACCOUNT_ID), eq(OLD_INDEX_NAME), eq(NEW_INDEX_NAME), queryArgumentCaptor.capture()))
        .thenReturn(reindexResponse);
    iterator.handle(inProgressEntity);

    Query query = ElasticSearchQueryBuilder.buildNestedQuery(CONSTANT_SCORE, null,
        ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery, timeQuery)));
    List<Query> queries = queryArgumentCaptor.getAllValues();
    assertThat(queries.get(0)).hasToString(query.toString());
    assertThat(queries.get(1)).hasToString(query.toString());
    assertThat(queries.get(2))
        .hasToString(
            "Query: {\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"accountID\"}}},{\"terms\":{\"planExecutionId\":[\"executionId2\",\"executionId4\"]}}]}}");
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task1");
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(0)).updateSearchIndexMigrationDetails(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusNoMissingExecutions() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, COMPLETE);
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationStartTime, startTs);
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationEndTime, endTs);

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(null);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    ReindexResponse reindexResponse = mock(ReindexResponse.class);
    when(reindexResponse.task()).thenReturn("taskId2");

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(OLD_INDEX_NAME), eq(10000)))
        .thenReturn(List.of("executionId1", "executionId2", "executionId3", "executionId4"));
    when(pipelineSearchService.listExecutionsFromIndex(queryArgumentCaptor.capture(), eq(NEW_INDEX_NAME), eq(10000)))
        .thenReturn(
            List.of("executionId1", "executionId2", "executionId3", "executionId4", "executionId5", "executionId6"));
    iterator.handle(inProgressEntity);

    Query query = ElasticSearchQueryBuilder.buildNestedQuery(CONSTANT_SCORE, null,
        ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, List.of(accountQuery, timeQuery)));
    List<Query> queries = queryArgumentCaptor.getAllValues();
    assertThat(queries.get(0)).hasToString(query.toString());
    assertThat(queries.get(1)).hasToString(query.toString());

    verify(pipelineSearchService, times(0)).reIndexDocuments(any(), any(), any(), any());
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task1");
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1)).updateSearchIndexMigrationDetails(ACCOUNT_ID, COMPLETE, null, null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusBufferTaskFailed() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, FAILED);

    ErrorCause errorCause = new ErrorCause.Builder()
                                .type("illegal_argument_exception")
                                .reason("invalid sequence of tokens near ['value'].")
                                .build();

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(errorCause);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);

    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    iterator.handle(inProgressEntityWithBufferTask);
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task2");

    verify(pipelineSearchService, times(0)).reIndexDocuments(any(), any(), any(), any());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1))
        .updateSearchIndexMigrationDetails(ACCOUNT_ID, FAILED, OLD_INDEX_NAME, NEW_INDEX_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleInProgressStatusBufferTaskSuccess() throws IOException {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, COMPLETE);
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationStartTime, startTs);
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationEndTime, endTs);

    GetTasksResponse getTasksResponse = mock(GetTasksResponse.class);
    TaskInfo taskInfoResponse = mock(TaskInfo.class);
    when(taskInfoResponse.startTimeInMillis()).thenReturn(startTs);
    when(taskInfoResponse.runningTimeInNanos()).thenReturn(runningTimeinNanos);

    when(getTasksResponse.completed()).thenReturn(true);
    when(getTasksResponse.error()).thenReturn(null);
    when(getTasksResponse.task()).thenReturn(taskInfoResponse);
    ArgumentCaptor<Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>>> taskArgumentCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(elasticsearchClient.getTask(taskArgumentCaptor.capture())).thenReturn(getTasksResponse);

    iterator.handle(inProgressEntityWithBufferTask);
    GetTasksRequest.Builder builder = new GetTasksRequest.Builder();
    GetTasksRequest updateRequest = taskArgumentCaptor.getValue().apply(builder).build();
    assertThat(updateRequest.taskId()).isEqualTo("task2");

    verify(pipelineSearchService, times(0)).reIndexDocuments(any(), any(), any(), any());
    verify(indexMigrationService, times(1)).update(eq(UUID), eq(update));
    verify(pipelineRetentionService, times(1)).updateSearchIndexMigrationDetails(ACCOUNT_ID, COMPLETE, null, null);
  }
}
