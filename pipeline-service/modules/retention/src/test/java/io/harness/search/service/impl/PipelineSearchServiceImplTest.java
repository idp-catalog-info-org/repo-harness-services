/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_RUNNING_EXECUTIONS_INDEX;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.elasticsearch.ElasticSearchDBConfig;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.entity.accountoverrides.SearchSettings;
import io.harness.exception.InternalServerErrorException;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineRetryExecutionMetadata;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOBuilder;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.util.ObjectBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PipelineSearchServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountID";
  private static final String UUID = "UUID";
  private static final String PLAN_EXECUTION_ID = "PLAN_EXECUTION_ID";
  private static final String OLD_INDEX_NAME = "OLD";
  private static final String NEW_INDEX_NAME = "NEW";

  @Mock private ElasticSearchClient elasticSearchClient;

  @Mock private PipelineRetentionService pipelineRetentionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  private ElasticSearchDBConfig elasticSearchDBConfig = ElasticSearchDBConfig.builder().enabled(true).build();
  @InjectMocks @Spy PipelineSearchServiceImpl pipelineSearchService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(pipelineSearchService).set("elasticSearchDBConfig", elasticSearchDBConfig);
    Reflect.on(pipelineSearchService).set("pipelineRetentionService", pipelineRetentionService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetAllIndexNames() {
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, PMS_EXECUTION_ALIAS_6_MONTH));
    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, OLD_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.IN_PROGRESS)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, OLD_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.FAILED)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, OLD_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.COMPLETE)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, NEW_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(
            SearchSettings.builder().indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED).build()));
    assertThat(pipelineSearchService.getAllIndexNames(ACCOUNT_ID))
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, PMS_EXECUTION_ALIAS_6_MONTH));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetAllWriteIndexNames() {
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.RUNNING))
        .isEqualTo(List.of(PMS_RUNNING_EXECUTIONS_INDEX));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_WAITING))
        .isEqualTo(List.of(PMS_RUNNING_EXECUTIONS_INDEX));

    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.ABORTED))
        .isEqualTo(List.of(PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(PMS_EXECUTION_ALIAS_6_MONTH));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(OLD_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.IN_PROGRESS)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(OLD_INDEX_NAME, NEW_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.FAILED)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(OLD_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.COMPLETE)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(NEW_INDEX_NAME));

    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(
            SearchSettings.builder().indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED).build()));
    assertThat(pipelineSearchService.getAllWriteIndexNames(ACCOUNT_ID, Status.APPROVAL_REJECTED))
        .isEqualTo(List.of(PMS_EXECUTION_ALIAS_6_MONTH));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetRunningExecutionIndexName() {
    assertThat(pipelineSearchService.getRunningExecutionIndexName()).isEqualTo(PMS_RUNNING_EXECUTIONS_INDEX);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testListExecutions() throws Exception {
    ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    Query elasticQuery = Query.of(query -> query.term(m -> m.field("test").value("test")));
    Pageable pageable = PageRequest.of(0, 1);

    PipelineSearchReadExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId(PLAN_EXECUTION_ID).build();
    when(elasticSearchClient.search(
             searchRequestArgumentCaptor.capture(), eq(PipelineSearchReadExecutionSummaryDTO.class)))
        .thenReturn(getExecutionSearchResponse(executionSummaryDTO));

    Page<String> result = pipelineSearchService.listExecutions(ACCOUNT_ID, pageable, elasticQuery);
    assertThat(result.getSize()).isEqualTo(1);
    assertThat(PLAN_EXECUTION_ID).isEqualTo(result.getContent().get(0));

    SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
    assertThat(searchRequest.index())
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(searchRequest.query().toString()).isEqualTo(elasticQuery.toString());
    assertThat(searchRequest.trackTotalHits().toString()).isEqualTo(TrackHits.of(th -> th.enabled(true)).toString());
    assertThat(searchRequest.from()).isZero();
    assertThat(searchRequest.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFetchFirstExecutionEndTs() throws Exception {
    ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    PipelineSearchReadExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchReadExecutionSummaryDTO.builder().planExecutionId(PLAN_EXECUTION_ID).build();
    when(elasticSearchClient.search(
             searchRequestArgumentCaptor.capture(), eq(PipelineSearchReadExecutionSummaryDTO.class)))
        .thenReturn(getExecutionSearchResponse(executionSummaryDTO));

    Optional<Long> result = pipelineSearchService.fetchFirstExecutionEndTs();
    assertThat(result).isNotPresent();

    SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
    assertThat(searchRequest.index())
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(searchRequest.query()).isNull();
    assertThat(searchRequest.source().toString()).isEqualTo("SourceConfig: {\"includes\":[\"endTs\"]}");
    assertThat(searchRequest.sort().toString()).isEqualTo("[SortOptions: {\"endTs\":{\"order\":\"asc\"}}]");
    assertThat(searchRequest.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFetchFirstExecutionEndTsSuccess() throws Exception {
    ArgumentCaptor<SearchRequest> searchRequestArgumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    PipelineSearchReadExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchReadExecutionSummaryDTO.builder().endTs(100L).build();
    when(elasticSearchClient.search(
             searchRequestArgumentCaptor.capture(), eq(PipelineSearchReadExecutionSummaryDTO.class)))
        .thenReturn(getExecutionSearchResponse(executionSummaryDTO));

    Optional<Long> result = pipelineSearchService.fetchFirstExecutionEndTs();
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(100L);

    SearchRequest searchRequest = searchRequestArgumentCaptor.getValue();
    assertThat(searchRequest.index())
        .isEqualTo(Arrays.asList(PMS_RUNNING_EXECUTIONS_INDEX, PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(searchRequest.query()).isNull();
    assertThat(searchRequest.source().toString()).isEqualTo("SourceConfig: {\"includes\":[\"endTs\"]}");
    assertThat(searchRequest.sort().toString()).isEqualTo("[SortOptions: {\"endTs\":{\"order\":\"asc\"}}]");
    assertThat(searchRequest.size()).isEqualTo(1);
  }

  private static SearchResponse getExecutionSearchResponse(PipelineSearchReadExecutionSummaryDTO executionSummaryDTO) {
    Hit<PipelineSearchReadExecutionSummaryDTO> hit = new Hit.Builder<PipelineSearchReadExecutionSummaryDTO>()
                                                         .index("my_index")
                                                         .id("1")
                                                         .source(executionSummaryDTO)
                                                         .build();
    ShardStatistics shardStats = new ShardStatistics.Builder().total(1).successful(1).skipped(0).failed(0).build();
    SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
        new SearchResponse.Builder<PipelineSearchReadExecutionSummaryDTO>()
            .took(10)
            .timedOut(false)
            .hits(h -> h.total(t -> t.value(1).relation(TotalHitsRelation.Eq)).hits(List.of(hit)))
            .shards(shardStats)
            .build();

    return searchResponse;
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testIndexSuccess() throws Exception {
    ArgumentCaptor<IndexRequest> indexRequestArgumentCaptor = ArgumentCaptor.forClass(IndexRequest.class);
    IndexResponse mockResponse = mock(IndexResponse.class);
    when(mockResponse.result()).thenReturn(Result.Created);
    when(elasticSearchClient.index(indexRequestArgumentCaptor.capture())).thenReturn(mockResponse);

    PipelineSearchExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder().uuid(UUID).accountId(ACCOUNT_ID).build();
    Result result = pipelineSearchService.index(executionSummaryDTO, PMS_RUNNING_EXECUTIONS_INDEX);

    assertThat(result).isEqualTo(Result.Created);
    verify(elasticSearchClient, times(1)).index(any(IndexRequest.class));

    IndexRequest indexRequest = indexRequestArgumentCaptor.getValue();
    assertThat(indexRequest.id()).isEqualTo(UUID);
    assertThat(indexRequest.routing()).isEqualTo(ACCOUNT_ID);
    assertThat(indexRequest.document()).isEqualTo(executionSummaryDTO);
    assertThat(indexRequest.index()).isEqualTo(PMS_RUNNING_EXECUTIONS_INDEX);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testIndexFail() throws IOException {
    when(elasticSearchClient.index(any(IndexRequest.class))).thenThrow(new IOException("IOException"));

    PipelineSearchExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder().uuid(UUID).accountId(ACCOUNT_ID).build();

    assertThatThrownBy(() -> pipelineSearchService.index(executionSummaryDTO, PMS_RUNNING_EXECUTIONS_INDEX))
        .hasMessage("[ELASTIC_SEARCH]: Could not save execution for uuid: UUID");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateIsDeleted() throws Exception {
    UpdateResponse mockResponse = mock(UpdateResponse.class);
    when(mockResponse.result()).thenReturn(Result.Updated);
    when(elasticSearchClient.updateRecord(any(), eq(PipelineSearchExecutionSummaryDTO.class), any()))
        .thenReturn(mockResponse);

    PipelineSearchExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder().uuid(UUID).accountId(ACCOUNT_ID).build();
    Result result = pipelineSearchService.updateIsDeleted(executionSummaryDTO, PMS_RUNNING_EXECUTIONS_INDEX, true);

    assertThat(result).isEqualTo(Result.Updated);

    ArgumentCaptor<Function<UpdateRequest.Builder<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO>,
        ObjectBuilder<UpdateRequest<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO>>>> captor =
        ArgumentCaptor.forClass(Function.class);
    verify(elasticSearchClient, times(1))
        .updateRecord(captor.capture(), eq(PipelineSearchExecutionSummaryDTO.class), any());

    Function<UpdateRequest.Builder<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO>,
        ObjectBuilder<UpdateRequest<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO>>>
        capturedFunction = captor.getValue();
    UpdateRequest.Builder<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO> builder =
        new UpdateRequest.Builder<>();
    UpdateRequest<PipelineSearchExecutionSummaryDTO, PipelineSearchExecutionSummaryDTO> updateRequest =
        capturedFunction.apply(builder).build();

    assertThat(updateRequest.id()).isEqualTo(UUID);
    assertThat(updateRequest.routing()).isEqualTo(ACCOUNT_ID);
    assertThat(updateRequest.doc()).isEqualTo(PipelineSearchExecutionSummaryDTO.builder().isDeleted(true).build());
    assertThat(updateRequest.index()).isEqualTo(PMS_RUNNING_EXECUTIONS_INDEX);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateIsDeletedFail() throws Exception {
    when(elasticSearchClient.updateRecord(any(), any(), any())).thenThrow(new IOException("IOException"));

    PipelineSearchExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder().uuid(UUID).accountId(ACCOUNT_ID).build();

    assertThatThrownBy(
        () -> pipelineSearchService.updateIsDeleted(executionSummaryDTO, PMS_RUNNING_EXECUTIONS_INDEX, true))
        .hasMessage("[ELASTIC_SEARCH]: Could not update isDeleted field for execution uuid: UUID");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateIsDeletedMissingDocument() throws Exception {
    ErrorCause errorCause =
        new ErrorCause.Builder().type("document_missing_exception").reason("[UUID]: document missing").build();
    ErrorResponse errorResponse = new ErrorResponse.Builder().error(errorCause).status(404).build();
    when(elasticSearchClient.updateRecord(any(), any(), any()))
        .thenThrow(new ElasticsearchException("es/update", errorResponse));

    PipelineSearchExecutionSummaryDTO executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder().uuid(UUID).accountId(ACCOUNT_ID).build();

    assertThat(pipelineSearchService.updateIsDeleted(executionSummaryDTO, PMS_RUNNING_EXECUTIONS_INDEX, true))
        .isEqualTo(Result.NotFound);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSave() {
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .index(eq(getRunningExecutionSummaryDTO(true)), eq(PMS_RUNNING_EXECUTIONS_INDEX));
    pipelineSearchService.save(getRunningExecutionSummaryEntity());
    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1)).index(dtoArgumentCaptor.capture(), eq(PMS_RUNNING_EXECUTIONS_INDEX));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.RUNNING);
    assertThat(gotDTO).isEqualTo(getRunningExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSaveFail() {
    doThrow(new InternalServerErrorException("[ELASTIC_SEARCH]: Could not save execution for uuid: UUID"))
        .when(pipelineSearchService)
        .index(eq(getRunningExecutionSummaryDTO(true)), eq(PMS_RUNNING_EXECUTIONS_INDEX));
    pipelineSearchService.save(getRunningExecutionSummaryEntity());

    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1)).index(dtoArgumentCaptor.capture(), eq(PMS_RUNNING_EXECUTIONS_INDEX));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.RUNNING);
    assertThat(gotDTO).isEqualTo(getRunningExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSyncCompletedExecutionsToElasticFail() {
    doThrow(new InternalServerErrorException("[ELASTIC_SEARCH]: Could not save execution for uuid: UUID"))
        .when(pipelineSearchService)
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    assertThatThrownBy(() -> pipelineSearchService.syncCompletedExecutionsToElastic(getSuccessExecutionSummaryEntity()))
        .hasMessage("[ELASTIC_SEARCH]: Could not save execution for uuid: UUID");

    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1)).index(dtoArgumentCaptor.capture(), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    assertThat(gotDTO).isEqualTo(getSuccessExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSyncCompletedExecutionsToElasticSuccess() {
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    pipelineSearchService.syncCompletedExecutionsToElastic(getSuccessExecutionSummaryEntity());
    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    verify(pipelineSearchService, times(1))
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateCompletedExecution_RecordNotExists_ShouldInsert() {
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .updateIsDeleted(eq(getSuccessExecutionSummaryDTO(false)), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    doReturn(null)
        .when(pipelineSearchService)
        .fetchByPlanExecutionId(eq(ACCOUNT_ID), eq(PMS_EXECUTION_ALIAS_6_MONTH), eq(PLAN_EXECUTION_ID));

    pipelineSearchService.update(getSuccessExecutionSummaryEntity());

    verify(pipelineSearchService, times(1))
        .fetchByPlanExecutionId(eq(ACCOUNT_ID), eq(PMS_EXECUTION_ALIAS_6_MONTH), eq(PLAN_EXECUTION_ID));
    verify(pipelineSearchService, times(1))
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    verify(pipelineSearchService, times(0)).updateRecord(any(PipelineSearchExecutionSummaryDTO.class), anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateCompletedExecution_RecordExists_ShouldUpdate() {
    Hit<PipelineSearchReadExecutionSummaryDTO> mockHit = mock(Hit.class);
    when(mockHit.index()).thenReturn(DEFAULT_RETENTION_6_MONTHS.getFirstIndexName());
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .updateIsDeleted(eq(getSuccessExecutionSummaryDTO(false)), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    doReturn(mockHit)
        .when(pipelineSearchService)
        .fetchByPlanExecutionId(eq(ACCOUNT_ID), eq(PMS_EXECUTION_ALIAS_6_MONTH), eq(PLAN_EXECUTION_ID));
    doReturn(Result.Updated)
        .when(pipelineSearchService)
        .updateRecord(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));

    pipelineSearchService.update(getSuccessExecutionSummaryEntity());

    verify(pipelineSearchService, times(1))
        .fetchByPlanExecutionId(eq(ACCOUNT_ID), eq(PMS_EXECUTION_ALIAS_6_MONTH), eq(PLAN_EXECUTION_ID));
    verify(pipelineSearchService, times(1))
        .updateRecord(eq(getSuccessExecutionSummaryDTO(true)), eq(DEFAULT_RETENTION_6_MONTHS.getFirstIndexName()));
    verify(pipelineSearchService, times(0)).index(any(PipelineSearchExecutionSummaryDTO.class), anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateSuccess() {
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .updateIsDeleted(eq(getSuccessExecutionSummaryDTO(false)), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    pipelineSearchService.update(getSuccessExecutionSummaryEntity());
    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1))
        .updateIsDeleted(dtoArgumentCaptor.capture(), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    verify(pipelineSearchService, times(1))
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(gotDTO).isEqualTo(getSuccessExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateMultipleIndexes() throws IOException {
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .updateIsDeleted(eq(getSuccessExecutionSummaryDTO(false)), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.IN_PROGRESS)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));

    IndexResponse mockResponse = mock(IndexResponse.class);
    when(mockResponse.result()).thenReturn(Result.Created);
    when(elasticSearchClient.index(any(IndexRequest.class))).thenReturn(mockResponse);

    pipelineSearchService.update(getSuccessExecutionSummaryEntity());
    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1))
        .updateIsDeleted(dtoArgumentCaptor.capture(), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    verify(pipelineSearchService, times(1)).index(eq(getSuccessExecutionSummaryDTO(true)), eq(OLD_INDEX_NAME));
    verify(pipelineSearchService, times(1)).index(eq(getSuccessExecutionSummaryDTO(true)), eq(NEW_INDEX_NAME));
    assertThat(gotDTO).isEqualTo(getSuccessExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldSyncToElastic() {
    Update update = new Update();
    update.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.isLatestExecution, true);
    assertThat(pipelineSearchService.shouldSyncToElastic(update)).isFalse();

    update.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap, new HashMap<>());
    assertThat(pipelineSearchService.shouldSyncToElastic(update)).isFalse();

    update.set("moduleInfo.cd.serviceIdentifiers", new HashMap<>());
    assertThat(pipelineSearchService.shouldSyncToElastic(update)).isTrue();

    Update update2 = new Update();
    update2.set("entityGitDetails.branch", new HashMap<>());
    assertThat(pipelineSearchService.shouldSyncToElastic(update2)).isTrue();

    Update update3 = new Update();
    update3.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo, new HashMap<>());
    assertThat(pipelineSearchService.shouldSyncToElastic(update3)).isTrue();

    Update update4 = new Update();
    update4.set("accountId", "test");
    assertThat(pipelineSearchService.shouldSyncToElastic(update4)).isTrue();

    Update update5 = new Update();
    update5.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.tags, Collections.emptyList());
    assertThat(pipelineSearchService.shouldSyncToElastic(update5)).isTrue();

    Update update6 = new Update();
    update6.set("parentStageInfo.hasParentPipeline", true);
    assertThat(pipelineSearchService.shouldSyncToElastic(update6)).isTrue();

    Update update7 = new Update();
    update7.set("executionTriggerInfo.isRerun", true);
    assertThat(pipelineSearchService.shouldSyncToElastic(update7)).isTrue();

    Update update8 = new Update();
    update8.set("retryExecutionMetadata.parentExecutionId", "test");
    assertThat(pipelineSearchService.shouldSyncToElastic(update8)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateNoOp() {
    doReturn(Result.NoOp)
        .when(pipelineSearchService)
        .updateIsDeleted(eq(getSuccessExecutionSummaryDTO(false)), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));

    doReturn(Result.Created)
        .when(pipelineSearchService)
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));

    pipelineSearchService.update(getSuccessExecutionSummaryEntity());

    ArgumentCaptor<PipelineSearchExecutionSummaryDTO> dtoArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchExecutionSummaryDTO.class);
    verify(pipelineSearchService, times(1))
        .updateIsDeleted(dtoArgumentCaptor.capture(), eq(PMS_RUNNING_EXECUTIONS_INDEX), eq(true));
    PipelineSearchExecutionSummaryDTO gotDTO = dtoArgumentCaptor.getValue();

    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    verify(pipelineSearchService, times(1))
        .index(eq(getSuccessExecutionSummaryDTO(true)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    assertThat(gotDTO).isEqualTo(getSuccessExecutionSummaryDTO(true));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForPlanExecution_NoHits_NoUpdates() throws Exception {
    when(pipelineRetentionService.getSearchSettings(ACCOUNT_ID))
        .thenReturn(Optional.of(SearchSettings.builder()
                                    .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                                    .oldIndexName(OLD_INDEX_NAME)
                                    .newIndexName(NEW_INDEX_NAME)
                                    .build()));

    ShardStatistics shardStats = new ShardStatistics.Builder().total(1).successful(1).skipped(0).failed(0).build();
    SearchResponse<PipelineSearchReadExecutionSummaryDTO> emptySearchResponse =
        new SearchResponse.Builder<PipelineSearchReadExecutionSummaryDTO>()
            .took(5)
            .timedOut(false)
            .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(Collections.emptyList()))
            .shards(shardStats)
            .build();

    when(elasticSearchClient.search(any(SearchRequest.class), eq(PipelineSearchReadExecutionSummaryDTO.class)))
        .thenReturn(emptySearchResponse);

    verify(elasticSearchClient, times(0)).updateRecord(any(), eq(PipelineSearchExecutionSummaryDTO.class), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSyncCompletedExecutionsToElasticSuccessWithNotes() {
    String note = "sample note";
    doReturn(Result.Created)
        .when(pipelineSearchService)
        .index(eq(getSuccessExecutionSummaryDTOWithNote(true, note)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
    pipelineSearchService.syncCompletedExecutionsToElastic(getSuccessExecutionSummaryEntityWithNotes());
    verify(pipelineSearchService, times(1)).getAllWriteIndexNames(ACCOUNT_ID, Status.SUCCEEDED);
    verify(pipelineSearchService, times(1))
        .index(eq(getSuccessExecutionSummaryDTOWithNote(true, note)), eq(PMS_EXECUTION_ALIAS_6_MONTH));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDeleteExecutionsIncludesRunningIndex() throws IOException {
    ArgumentCaptor<Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>>> captor =
        ArgumentCaptor.forClass(Function.class);
    DeleteByQueryResponse mockResponse = mock(DeleteByQueryResponse.class);
    when(elasticSearchClient.deleteRecords(any())).thenReturn(mockResponse);

    DeleteByQueryResponse result = pipelineSearchService.deleteExecutions(Set.of(PLAN_EXECUTION_ID), ACCOUNT_ID);

    assertThat(result).isEqualTo(mockResponse);
    verify(elasticSearchClient, times(1)).deleteRecords(captor.capture());

    DeleteByQueryRequest deleteRequest = captor.getValue().apply(new DeleteByQueryRequest.Builder()).build();
    assertThat(deleteRequest.index())
        .containsExactlyInAnyOrder(PMS_EXECUTION_ALIAS_6_MONTH, PMS_RUNNING_EXECUTIONS_INDEX);
  }

  private PipelineExecutionSummaryEntity getRunningExecutionSummaryEntity() {
    return PipelineExecutionSummaryEntity.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .planExecutionId(PLAN_EXECUTION_ID)
        .status(ExecutionStatus.RUNNING)
        .createdAt(1000L)
        .startTs(1000L)
        .executionMode(ExecutionMode.NORMAL)
        .build();
  }

  private PipelineSearchExecutionSummaryDTO getRunningExecutionSummaryDTO(boolean isSaveEvent) {
    PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid(UUID)
            .accountId(ACCOUNT_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .tags(Collections.EMPTY_LIST)
            .labels(Collections.EMPTY_LIST)
            .executionMode("NORMAL")
            .createdAt(1000L)
            .startTs(1000L)
            .runSequence(0)
            .isChildPipeline(false)
            .status("RUNNING")
            .retryExecutionMetadata(
                PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build());
    if (isSaveEvent) {
      executionSummaryDTO.isDeleted(false);
    }
    return executionSummaryDTO.build();
  }

  private PipelineExecutionSummaryEntity getSuccessExecutionSummaryEntity() {
    return PipelineExecutionSummaryEntity.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .status(ExecutionStatus.SUCCESS)
        .planExecutionId(PLAN_EXECUTION_ID)
        .createdAt(1000L)
        .startTs(1000L)
        .endTs(1100L)
        .executionMode(ExecutionMode.NORMAL)
        .notesExistForPlanExecutionId(false)
        .build();
  }

  private PipelineExecutionSummaryEntity getSuccessExecutionSummaryEntityWithNotes() {
    return PipelineExecutionSummaryEntity.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .status(ExecutionStatus.SUCCESS)
        .planExecutionId(PLAN_EXECUTION_ID)
        .createdAt(1000L)
        .startTs(1000L)
        .endTs(1100L)
        .executionMode(ExecutionMode.NORMAL)
        .notesExistForPlanExecutionId(true)
        .notes("sample note")
        .build();
  }

  private PipelineSearchExecutionSummaryDTO getSuccessExecutionSummaryDTO(boolean isSaveEvent) {
    PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid(UUID)
            .accountId(ACCOUNT_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .tags(Collections.EMPTY_LIST)
            .labels(Collections.EMPTY_LIST)
            .executionMode("NORMAL")
            .status("SUCCESS")
            .createdAt(1000L)
            .runSequence(0)
            .isChildPipeline(false)
            .startTs(1000L)
            .endTs(1100L)
            .retryExecutionMetadata(
                PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build());
    if (isSaveEvent) {
      executionSummaryDTO.isDeleted(false);
    }
    return executionSummaryDTO.build();
  }

  private PipelineSearchExecutionSummaryDTO getSuccessExecutionSummaryDTOWithNote(boolean isSaveEvent, String note) {
    PipelineSearchExecutionSummaryDTOBuilder executionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid(UUID)
            .accountId(ACCOUNT_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .tags(Collections.EMPTY_LIST)
            .labels(Collections.EMPTY_LIST)
            .executionMode("NORMAL")
            .status("SUCCESS")
            .createdAt(1000L)
            .runSequence(0)
            .isChildPipeline(false)
            .notes(note)
            .startTs(1000L)
            .endTs(1100L)
            .retryExecutionMetadata(
                PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build());
    if (isSaveEvent) {
      executionSummaryDTO.isDeleted(false);
    }
    return executionSummaryDTO.build();
  }
}
