/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.PmsExecutionSummaryReadHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ReconciliationOrgScopeCriteriaHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryRepositoryCustomImplTest extends OrchestrationVisualizationTestBase {
  @Inject MongoTemplate mongoTemplate;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsExecutionSummaryReadHelper pmsExecutionSummaryReadHelper;
  @Inject @InjectMocks PmsExecutionSummaryRepositoryCustom pmsExecutionSummaryRepositoryCustom;
  @InjectMocks ReconciliationOrgScopeCriteriaHelper reconciliationOrgScopeCriteriaHelper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdate() {
    String planExecutionId = generateUuid();
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    Update update = new Update().set("status", ExecutionStatus.FAILED);
    assertNull(pmsExecutionSummaryRepositoryCustom.update(query, update));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionSummaryRepositoryCustom.update(query, update);
    assertEquals(pipelineExecutionSummaryEntity.getStatus(), ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testDeleteAllExecutionsWhenPipelineDeleted() {
    String planExecutionId = generateUuid();
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    Update update = new Update().set("pipelineDeleted", Boolean.TRUE);
    UpdateResult updateResult =
        pmsExecutionSummaryRepositoryCustom.deleteAllExecutionsWhenPipelineDeleted(query, update);
    assertEquals(updateResult.getMatchedCount(), 0);
    assertEquals(updateResult.getModifiedCount(), 0);
    assertNull(updateResult.getUpsertedId());
    assertTrue(updateResult.wasAcknowledged());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    UpdateResult updateResult1 =
        pmsExecutionSummaryRepositoryCustom.deleteAllExecutionsWhenPipelineDeleted(query, update);
    assertEquals(updateResult1.getMatchedCount(), 1);
    assertEquals(updateResult1.getModifiedCount(), 1);
    assertTrue(updateResult1.wasAcknowledged());
    assertNull(updateResult1.getUpsertedId());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchRootRetryExecutionId() {
    String planExecutionId = generateUuid();
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
                           .build());
    assertEquals(pmsExecutionSummaryRepositoryCustom.fetchRootRetryExecutionId(planExecutionId), "root");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchPipelineSummaryEntityFromRootParentId() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("test")
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
            .status(ExecutionStatus.SKIPPED)
            .build();
    mongoTemplate.save(pipelineExecutionSummaryEntity);
    try (
        Stream<PipelineExecutionSummaryEntity> stream =
            pmsExecutionSummaryRepositoryCustom.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo("root")) {
      Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
      int count = 0;
      while (iterator.hasNext()) {
        count++;
        iterator.next();
      }
      assertEquals(count, 1);
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryWithProjections() {
    String planExecutionId = generateUuid();
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    PipelineExecutionSummaryEntity entity =
        pmsExecutionSummaryRepositoryCustom.getPipelineExecutionSummaryWithProjections(
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId),
            Sets.newHashSet(PlanExecutionSummaryKeys.planExecutionId));
    assertThat(entity).isNotNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getPlanExecutionId()).isEqualTo(planExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAll() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId1").build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId2").build());

    Page<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom.findAll(criteria, PageRequest.of(0, 10));

    assertThat(response.stream().count()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.getContent().get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.getContent().get(1).getPlanExecutionId())).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAllWithProjections() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom
            .findAllWithProjection(criteria, PageRequest.of(0, 10),
                List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.accountId,
                    PlanExecutionSummaryKeys.projectIdentifier))
            .stream()
            .collect(Collectors.toList());

    assertThat((long) response.size()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.get(1).getPlanExecutionId())).isTrue();

    assertThat(response.get(0).getPlanExecutionId()).isNotNull();
    assertThat(response.get(0).getAccountId()).isNotNull();
    assertThat(response.get(0).getProjectIdentifier()).isNotNull();
    // orgId is null because it was not in the projections.
    assertThat(response.get(0).getOrgIdentifier()).isNull();
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAllWithRequiredProjectionUsingAnalyticsNode() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .uuid(generateUuid())
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .pipelineIdentifier("pipelineId")
                           .createdAt(System.currentTimeMillis())
                           .lastUpdatedAt(System.currentTimeMillis())
                           .name("name")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .uuid(generateUuid())
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .pipelineIdentifier("pipelineId")
                           .createdAt(System.currentTimeMillis())
                           .lastUpdatedAt(System.currentTimeMillis())
                           .name("name")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom
            .findAllWithRequiredProjectionUsingAnalyticsNode(
                criteria, Collections.singletonList(PlanExecutionSummaryKeys.planExecutionId))
            .collect(Collectors.toList());

    assertThat(response.size()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.get(1).getPlanExecutionId())).isTrue();

    assertThat(response.get(0).getPlanExecutionId()).isNotNull();
    assertThat(response.get(0).getAccountId()).isNotNull();
    assertThat(response.get(0).getProjectIdentifier()).isNotNull();
    assertThat(response.get(0).getOrgIdentifier()).isNotNull();
    assertThat(response.get(0).getRunSequence()).isNotNull();
    assertThat(response.get(0).getPipelineIdentifier()).isNotNull();
    assertThat(response.get(0).getName()).isNotNull();
    assertThat(response.get(0).getUuid()).isNotNull();
    assertThat(response.get(0).getCreatedAt()).isNotNull();
    assertThat(response.get(0).getLastUpdatedAt()).isNotNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCountOfExecutionSummary() {
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is("planExecutionId1"),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is("planExecutionId2"));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());

    assertThat(pmsExecutionSummaryRepositoryCustom.getCountOfExecutionSummary(criteria)).isEqualTo(2L);
  }

  public static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFindAllWithHint() {
    PmsExecutionSummaryRepositoryCustom pmsExecutionSummaryRepositoryCustomWithMocks =
        new PmsExecutionSummaryRepositoryCustomImpl(
            null, pmsExecutionSummaryReadHelper, pmsFeatureFlagService, reconciliationOrgScopeCriteriaHelper);

    Criteria criteria = new Criteria();
    Pageable pageable = PageRequest.of(0, 10);
    String accountId = "testAccountId";
    String sortProperty = PlanExecutionSummaryKeys.startTs;

    when(pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_OPTIMIZE_EXECUTIONS_LIST_VIEW_WITH_HINT))
        .thenReturn(true);
    when(pmsExecutionSummaryReadHelper.findCount(any(Query.class))).thenReturn(1L);
    when(pmsExecutionSummaryReadHelper.find(any(Query.class))).thenReturn(Collections.emptyList());

    pmsExecutionSummaryRepositoryCustomWithMocks.findAll(criteria, pageable, accountId, sortProperty);

    verify(pmsFeatureFlagService).isEnabled(accountId, FeatureName.PIE_OPTIMIZE_EXECUTIONS_LIST_VIEW_WITH_HINT);

    assertTrue(pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_OPTIMIZE_EXECUTIONS_LIST_VIEW_WITH_HINT));
    verify(pmsExecutionSummaryReadHelper, times(1))
        .find(argThat(query
            -> "accountId_parentUniqueId_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx".equals(
                query.getHint())));

    sortProperty = PlanExecutionSummaryKeys.name;
    pmsExecutionSummaryRepositoryCustomWithMocks.findAll(criteria, pageable, accountId, sortProperty);
    verify(pmsExecutionSummaryReadHelper, times(1))
        .find(argThat(query
            -> "accountId_parentUniqueId_name_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx"
                   .equals(query.getHint())));

    sortProperty = PlanExecutionSummaryKeys.status;
    pmsExecutionSummaryRepositoryCustomWithMocks.findAll(criteria, pageable, accountId, sortProperty);
    verify(pmsExecutionSummaryReadHelper, times(1))
        .find(argThat(query
            -> "accountId_parentUniqueId_status_startTs_repo_branch_pipelineIds_modules_parent_info_range_idx".equals(
                query.getHint())));

    sortProperty = "unknown";
    pmsExecutionSummaryRepositoryCustomWithMocks.findAll(criteria, pageable, accountId, sortProperty);
    verify(pmsExecutionSummaryReadHelper, times(1)).find(argThat(query -> query.getHint() == null));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFindAllWithProjectionWithoutPagination() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom.findAllWithProjectionWithoutPagination(criteria, PageRequest.of(0, 10),
            List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.accountId,
                PlanExecutionSummaryKeys.projectIdentifier),
            null);

    assertThat((long) response.size()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.get(1).getPlanExecutionId())).isTrue();

    assertThat(response.get(0).getPlanExecutionId()).isNotNull();
    assertThat(response.get(0).getAccountId()).isNotNull();
    assertThat(response.get(0).getProjectIdentifier()).isNotNull();
    // orgId is null because it was not in the projections.
    assertThat(response.get(0).getOrgIdentifier()).isNull();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFindAllWithProjectionWithoutPaginationWithHint() {
    PmsExecutionSummaryRepositoryCustom pmsExecutionSummaryRepositoryCustomWithMocks =
        new PmsExecutionSummaryRepositoryCustomImpl(
            null, pmsExecutionSummaryReadHelper, pmsFeatureFlagService, reconciliationOrgScopeCriteriaHelper);
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    String hintIndex = "accountId_parentUniqueId_startTs_planExecutionId_status_pipelineIdentifier";

    when(pmsExecutionSummaryReadHelper.find(any(Query.class))).thenReturn(Collections.emptyList());
    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustomWithMocks.findAllWithProjectionWithoutPagination(criteria,
            PageRequest.of(0, 10),
            List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.accountId,
                PlanExecutionSummaryKeys.projectIdentifier),
            hintIndex);

    verify(pmsExecutionSummaryReadHelper, times(1)).find(argThat(query -> hintIndex.equals(query.getHint())));
  }
}