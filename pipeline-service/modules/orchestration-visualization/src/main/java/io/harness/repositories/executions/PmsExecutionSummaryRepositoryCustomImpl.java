/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.PmsCommonConstants.EXECUTION_TTL_IN_DAYS;

import static java.lang.String.format;
import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.execution.utils.PipelineExecutionSummaryEntityProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.PmsExecutionSummaryReadHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.springdata.PersistenceUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ReconciliationOrgScopeCriteriaHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PmsExecutionSummaryRepositoryCustomImpl implements PmsExecutionSummaryRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final PmsExecutionSummaryReadHelper pmsExecutionSummaryReadHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ReconciliationOrgScopeCriteriaHelper reconciliationOrgScopeCriteriaHelper;

  @Override
  public PipelineExecutionSummaryEntity update(Query query, Update update) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed updating PipelineExecutionSummary; attempt: {}",
            "[Failed]: Failed updating PipelineExecutionSummary; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineExecutionSummaryEntity.class));
  }

  @Override
  public void multiUpdate(Query query, Update update) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed updating PipelineExecutionSummary; attempt: {}",
            "[Failed]: Failed updating PipelineExecutionSummary; attempt: {}");
    Failsafe.with(retryPolicy)
        .get(() -> mongoTemplate.updateMulti(query, update, PipelineExecutionSummaryEntity.class));
  }

  @Override
  public UpdateResult deleteAllExecutionsWhenPipelineDeleted(Query query, Update update) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed deleting PipelineExecutionSummary; attempt: {}",
            "[Failed]: Failed deleting PipelineExecutionSummary; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(() -> mongoTemplate.updateMulti(query, update, PipelineExecutionSummaryEntity.class));
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> findAll(Criteria criteria, Pageable pageable) {
    return findAll(criteria, pageable, null, null);
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> findAll(
      Criteria criteria, Pageable pageable, String accountId, String sortProperty) {
    try {
      Query query = new Query(criteria).with(pageable);
      if (!isEmpty(accountId)
          && pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_OPTIMIZE_EXECUTIONS_LIST_VIEW_WITH_HINT)
          && !isEmpty(sortProperty)) {
        addHintToQuery(query, sortProperty);
      }
      // Do not add directly the read helper inside the lambda, as secondary mongo reads were not going through if used
      // inside lambda in PageableExecutionUtils
      long count = pmsExecutionSummaryReadHelper.findCount(query);
      List<PipelineExecutionSummaryEntity> summaryEntities = pmsExecutionSummaryReadHelper.find(query);
      return PageableExecutionUtils.getPage(summaryEntities, pageable, () -> count);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Execution Status not found", ex);
    }
  }

  private void addHintToQuery(Query query, String sortProperty) {
    switch (sortProperty) {
      case PlanExecutionSummaryKeys.startTs -> query.withHint(
              "accountId_parentUniqueId_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx");
      case PlanExecutionSummaryKeys.status -> query.withHint(
              "accountId_parentUniqueId_status_startTs_repo_branch_pipelineIds_modules_parent_info_range_idx");
      case PlanExecutionSummaryKeys.name -> query.withHint(
              "accountId_parentUniqueId_name_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx");
      default -> log.warn(format("New sort property found in the list executions query when index is specified with hint(), skipping %s", sortProperty));
    }
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> findAllWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections) {
    try {
      Query query = new Query(criteria).with(pageable);

      for (String key : projections) {
          query.fields().include(key);
        }
        // Do not add directly the read helper inside the lambda, as secondary mongo reads were not going through if
        // used inside lambda in PageableExecutionUtils
        long count = pmsExecutionSummaryReadHelper.findCount(query);
        List<PipelineExecutionSummaryEntity> summaryEntities = pmsExecutionSummaryReadHelper.find(query);
        return PageableExecutionUtils.getPage(summaryEntities, pageable, () -> count);
    }
    catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Execution Status not found", ex);
    }
  }

  // Required in a migration . May be removed in the future.
  @Override
  public Stream<PipelineExecutionSummaryEntity> findAllWithRequiredProjectionUsingAnalyticsNode(
      Criteria criteria, List<String> projections) {
    try {
      Query query = new Query(criteria);
      addRequiredFieldsForProjectionOfPipelineExecutionSummaryEntity(query);
      for (String key : projections) {
        query.fields().include(key);
      }
      return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Execution Status not found", ex);
    }
  }

  private void addRequiredFieldsForProjectionOfPipelineExecutionSummaryEntity(Query query) {
    query.fields().include(PlanExecutionSummaryKeys.uuid);
    query.fields().include(PlanExecutionSummaryKeys.runSequence);
    query.fields().include(PlanExecutionSummaryKeys.accountId);
    query.fields().include(PlanExecutionSummaryKeys.projectIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.orgIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.pipelineIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.name);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.createdAt);
    query.fields().include(PlanExecutionSummaryKeys.lastUpdatedAt);
  }

  @Override
  public long getCountOfExecutionSummary(Criteria criteria) {
    Query query = new Query(criteria);
    return pmsExecutionSummaryReadHelper.findCount(query);
  }

  private void queryFieldsForPipelineExecutionSummaryEntity(Query query) {
    query.fields().include(PlanExecutionSummaryKeys.uuid);
    query.fields().include(PlanExecutionSummaryKeys.runSequence);
    query.fields().include(PlanExecutionSummaryKeys.accountId);
    query.fields().include(PlanExecutionSummaryKeys.projectIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.orgIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.pipelineIdentifier);
    query.fields().include(PlanExecutionSummaryKeys.name);
    query.fields().include(PlanExecutionSummaryKeys.retryExecutionMetadata);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.createdAt);
    query.fields().include(PlanExecutionSummaryKeys.lastUpdatedAt);
    query.fields().include(PlanExecutionSummaryKeys.version);
  }

  @Override
  public String fetchRootRetryExecutionId(String planExecutionId) {
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));

    queryFieldsForPipelineExecutionSummaryEntity(query);

    PipelineExecutionSummaryEntity entity = mongoTemplate.findOne(query, PipelineExecutionSummaryEntity.class);
    if (entity == null) {
      return null;
    }
    return entity.getRetryExecutionMetadata().getRootExecutionId();
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
      String rootParentId) {
    Query query = query(where(PlanExecutionSummaryKeys.rootExecutionId).is(rootParentId));

    queryFieldsForPipelineExecutionSummaryEntity(query);

    // RequiredFields
    query.fields().include(PlanExecutionSummaryKeys.startTs);
    query.fields().include(PlanExecutionSummaryKeys.endTs);
    query.fields().include(PlanExecutionSummaryKeys.status);

    query.with(by(Sort.Direction.DESC, PlanExecutionSummaryKeys.createdAt));
    return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
  }

  @Override
  public PipelineExecutionSummaryEntity fetchLatestExecutionUsingRootParentIdFromSecondary(String rootParentId) {
    Query query = query(where(PlanExecutionSummaryKeys.rootExecutionId).is(rootParentId));

    for (String field : PipelineExecutionSummaryEntityProjectionConstants.fieldsForRetryHistory) {
      query.fields().include(field);
    }

    query.with(by(Sort.Direction.DESC, PlanExecutionSummaryKeys.createdAt));

    return pmsExecutionSummaryReadHelper.findExecutionSummaryEntityFromSecondary(query);
  }

  @Override
  public PipelineExecutionSummaryEntity fetchFromSecondaryWithProjections(
      String planExecutionId, Set<String> projections) {
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    for (String field : projections) {
      query.fields().include(field);
    }
    return pmsExecutionSummaryReadHelper.findExecutionSummaryEntityFromSecondary(query);
  }

  @Override
  public PipelineExecutionSummaryEntity fetchByPlanExecutionIdFromSecondary(String planExecutionId) {
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    return pmsExecutionSummaryReadHelper.findExecutionSummaryEntityFromSecondary(query);
  }

  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntityFromSecondaryMongoWithProjections(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted,
      List<String> projections, ScopeInfo scopeInfo) {
    Query query = scopeInfo != null ? query(where(PlanExecutionSummaryKeys.accountId)
                                                .is(scopeInfo.getAccountIdentifier())
                                                .and(PlanExecutionSummaryKeys.parentUniqueId)
                                                .is(scopeInfo.getUniqueId())
                                                .and(PlanExecutionSummaryKeys.planExecutionId)
                                                .is(planExecutionId)
                                                .and(PlanExecutionSummaryKeys.pipelineDeleted)
                                                .is(pipelineDeleted))
                                    : query(where(PlanExecutionSummaryKeys.accountId)
                                                .is(accountId)
                                                .and(PlanExecutionSummaryKeys.orgIdentifier)
                                                .is(orgId)
                                                .and(PlanExecutionSummaryKeys.projectIdentifier)
                                                .is(projectId)
                                                .and(PlanExecutionSummaryKeys.planExecutionId)
                                                .is(planExecutionId)
                                                .and(PlanExecutionSummaryKeys.pipelineDeleted)
                                                .is(pipelineDeleted));
    for (String field : projections) {
      query.fields().include(field);
    }
    try (Stream<PipelineExecutionSummaryEntity> stream =
             pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query)) {
      Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
      if (iterator.hasNext()) {
        return iterator.next();
      }
    }
    throw new EntityNotFoundException(
        "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, Set<String> fieldsToInclude) {
    return fetchPlanExecutionIdsBetweenEndTsFromSecondary(accountId, startTime, endTime, fieldsToInclude, null);
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, Set<String> fieldsToInclude, String orgIdentifier) {
    Criteria criteria = new Criteria();
    if (accountId != null) {
      // Uses accountId_endTs_idx / accountId_orgIdentifier_endTs_idx / accountId_parentUniqueId_endTs_idx
      criteria.and(PlanExecutionSummaryKeys.accountId).is(accountId);
    }
    // Uses endTs_idx index
    criteria.and(PlanExecutionSummaryKeys.endTs).gte(startTime).lte(endTime);
    reconciliationOrgScopeCriteriaHelper.applyOrgScopeFilter(criteria, accountId, orgIdentifier,
        PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.parentUniqueId);

    Query query = query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.endTs);
    if (isNotEmpty(fieldsToInclude)) {
      for (String field : fieldsToInclude) {
        if (EmptyPredicate.isNotEmpty(field)) {
          query.fields().include(field);
        }
      }
    }
    query.with(Sort.by(Sort.Direction.ASC, PlanExecutionSummaryKeys.endTs));
    return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsWithGTEEndTsFromSecondary(
      String accountId, Long currentTime) {
    return fetchPlanExecutionIdsWithGTEEndTsFromSecondary(accountId, currentTime, null);
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsWithGTEEndTsFromSecondary(
      String accountId, Long currentTime, String orgIdentifier) {
    Criteria criteria = new Criteria();
    if (accountId != null) {
      // Uses accountId_endTs_idx / accountId_orgIdentifier_endTs_idx / accountId_parentUniqueId_endTs_idx
      criteria.and(PlanExecutionSummaryKeys.accountId).is(accountId);
    }
    // Uses endTs_idx index
    // We are doing -5 mins to account for any replication delays
    criteria.and(PlanExecutionSummaryKeys.endTs)
        .gte(currentTime)
        .lt(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
    reconciliationOrgScopeCriteriaHelper.applyOrgScopeFilter(criteria, accountId, orgIdentifier,
        PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.parentUniqueId);

    Query query = query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.endTs);
    query.with(Sort.by(Sort.Direction.ASC, PlanExecutionSummaryKeys.endTs));
    return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchRunningStuckPlanExecutions() {
    Query query = query(where(PlanExecutionSummaryKeys.internalStatus)
                            .in(StatusUtils.activeStatuses())
                            .and(PlanExecutionSummaryKeys.startTs)
                            .lt(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(EXECUTION_TTL_IN_DAYS)));

    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    query.fields().include(PlanExecutionSummaryKeys.endTs);
    query.fields().include(PlanExecutionSummaryKeys.status);
    query.with(Sort.by(Sort.Direction.ASC, PlanExecutionSummaryKeys.startTs));
    return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  public Stream<PipelineExecutionSummaryEntity> fetchExecutionSummaryEntityFromAnalytics(Query query) {
    return pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(query);
  }

  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      Criteria criteria, Set<String> fieldsToInclude) {
    Query query = query(criteria);
    if (EmptyPredicate.isEmpty(fieldsToInclude)) {
      throw new InvalidRequestException("Provided empty field names for projection");
    }

    for (String field : fieldsToInclude) {
      if (EmptyPredicate.isNotEmpty(field)) {
        query.fields().include(field);
      }
    }
    return mongoTemplate.findOne(query, PipelineExecutionSummaryEntity.class);
  }

  @Override
  public List<PipelineExecutionSummaryEntity> findAllWithProjectionWithoutPagination(
      Criteria criteria, Pageable pageable, List<String> projections, String hintIndex) {
    try {
      Query query;

      if (pageable != null) {
        query = new Query(criteria).with(pageable);
      } else {
        query = new Query(criteria);
      }

      if (isNotEmpty(hintIndex)) {
        query.withHint(hintIndex);
      }

      for (String key : projections) {
        query.fields().include(key);
      }
      return pmsExecutionSummaryReadHelper.find(query);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Execution summary not found", ex);
    }
  }

  @Override
  public List<PipelineExecutionSummaryEntity> findAllWithProjectionWithoutPagination(
      Criteria criteria, List<String> projections) {
    return findAllWithProjectionWithoutPagination(criteria, null, projections, null);
  }
}
