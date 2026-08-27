/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.jobs;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.metrics.service.api.MetricService;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationDB;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationEntityService;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.search.service.PipelineSearchService;

import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExecutionRetentionReconciliationIteratorOrgScopeTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";

  @Mock private ExecutionRetentionIteratorEntityService retentionIteratorEntityService;
  @Mock private ExecutionRetentionReconciliationEntityService reconciliationEntityService;
  @Mock private ObjectStoreClient objectStoreClient;
  @Mock private ElasticSearchClient elasticSearchClient;
  @Mock private PipelineSearchService pipelineSearchService;
  @Mock private MetricService metricService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private ExecutionRetentionMetadataService executionRetentionMetadataService;
  @Mock private ExecutionRetentionService executionRetentionService;
  @Mock private DataRetentionConfig dataRetentionConfig;

  @InjectMocks private ExecutionRetentionReconciliationIterator reconciliationIterator;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(objectStoreClient.getBucketName()).thenReturn("bucket");
    when(dataRetentionConfig.getReconciliationBatchProcessingSize()).thenReturn(0);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldPassOrgIdentifierWhenStreamingExecutionsFromMongo() {
    ExecutionRetentionReconciliationEntity entity = ExecutionRetentionReconciliationEntity.builder()
                                                        .accountIdentifier(ACCOUNT_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .syncCompletedUntil(100L)
                                                        .syncUntil(200L)
                                                        .reconciliationDB(ExecutionRetentionReconciliationDB.ELASTIC)
                                                        .build();
    when(pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
             eq(ACCOUNT_ID), eq(100L), eq(200L), isNull(), eq(ORG_ID)))
        .thenReturn(Stream.empty());

    reconciliationIterator.handle(entity);

    verify(pmsExecutionSummaryRepository)
        .fetchPlanExecutionIdsBetweenEndTsFromSecondary(ACCOUNT_ID, 100L, 200L, null, ORG_ID);
  }
}
