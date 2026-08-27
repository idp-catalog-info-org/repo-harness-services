/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResponseDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineBulkAbortResultDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineExecutionDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineFilterDTO;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineListResponse;
import io.harness.pms.plan.execution.service.QueuedPipelineService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.search.helper.PipelineSearchHelper;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO.PipelineSearchReadExecutionSummaryDTOKeys;
import io.harness.search.service.PipelineSearchService;
import io.harness.search.utils.PipelineSearchUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class QueuedPipelineServiceImpl implements QueuedPipelineService {
  @Inject private AccessControlClient accessControlClient;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private FilterService filterService;
  @Inject private MetricService metricService;

  private static final String ACCOUNT_RESOURCE_TYPE = "ACCOUNT";
  private static final String ACCOUNT_EDIT_PERMISSION = "core_account_edit";

  private static final String SOURCE_LABEL = "source";
  private static final String SOURCE_MONGO = "mongo";
  private static final String SOURCE_ELASTICSEARCH = "elasticsearch";
  private static final String QUEUED_PIPELINES_LIST_COUNT = "queued_pipelines_list_count";
  private static final String QUEUED_PIPELINES_LIST_DURATION = "queued_pipelines_list_duration";
  private static final String QUEUED_PIPELINES_TOTAL_IN_ACCOUNT = "queued_pipelines_total_in_account";
  private static final String STATUS_LABEL = "status";
  private static final String STATUS_QUEUED = "queued";
  private static final String STATUS_WAITING = "waiting";
  private static final String STATUS_RUNNING = "running";
  private static final String QUEUED_PIPELINES_FETCH_DURATION = "queued_pipelines_fetch_duration";
  private static final String QUEUED_PIPELINES_BULK_ABORT_COUNT = "queued_pipelines_bulk_abort_count";
  private static final String QUEUED_PIPELINES_BULK_ABORT_DURATION = "queued_pipelines_bulk_abort_duration";
  private static final String QUEUED_PIPELINES_ES_MONGO_STATUS_DRIFT = "queued_pipelines_es_mongo_status_drift";
  private static final String STATUS_SUCCESS = "success";
  private static final String STATUS_FAILURE = "failure";
  // ElasticSearchStream handles pagination internally in batches of 1000 (DEFAULT_BATCH_LIMIT),
  // so this limit can exceed the per-request ES cap without any manual batching needed.
  private static final int MAX_QUEUED_FETCH_LIMIT = 2000;
  private static final long MAX_LOOKBACK_MS = TimeUnit.DAYS.toMillis(30); // 30 days
  private static final int MAX_BULK_ABORT_LIMIT = 500;
  private static final String EXECUTION_NOT_ABORTABLE_ERROR_MESSAGE =
      "Execution not found or not in an abortable state";
  private static final List<Status> QUEUED_INTERNAL_STATUSES =
      Arrays.asList(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, Status.QUEUED_PLAN_CREATION);
  private static final List<ExecutionStatus> QUEUED_EXECUTION_STATUSES =
      Arrays.asList(ExecutionStatus.QUEUED_EXECUTION_CONCURRENCY_REACHED, ExecutionStatus.QUEUED_PLAN_CREATION);
  private static final List<ExecutionStatus> WAITING_EXECUTION_STATUSES =
      Arrays.asList(ExecutionStatus.APPROVALWAITING, ExecutionStatus.INTERVENTIONWAITING, ExecutionStatus.INPUTWAITING,
          ExecutionStatus.RESOURCEWAITING, ExecutionStatus.TASKWAITING, ExecutionStatus.ASYNCWAITING,
          ExecutionStatus.TIMEDWAITING, ExecutionStatus.UPLOADWAITING);
  // By default the listing surfaces queued, waiting, and running executions; callers narrow to one
  // group (or specific statuses) via the status filter on QueuedPipelineFilterDTO.
  private static final List<Status> ALL_INTERNAL_STATUSES =
      Arrays.asList(Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, Status.QUEUED_PLAN_CREATION, Status.APPROVAL_WAITING,
          Status.INTERVENTION_WAITING, Status.INPUT_WAITING, Status.RESOURCE_WAITING, Status.TASK_WAITING,
          Status.ASYNC_WAITING, Status.TIMED_WAITING, Status.UPLOAD_WAITING, Status.RUNNING);
  private static final List<ExecutionStatus> ALL_EXECUTION_STATUSES =
      Arrays.asList(ExecutionStatus.QUEUED_EXECUTION_CONCURRENCY_REACHED, ExecutionStatus.QUEUED_PLAN_CREATION,
          ExecutionStatus.APPROVALWAITING, ExecutionStatus.INTERVENTIONWAITING, ExecutionStatus.INPUTWAITING,
          ExecutionStatus.RESOURCEWAITING, ExecutionStatus.TASKWAITING, ExecutionStatus.ASYNCWAITING,
          ExecutionStatus.TIMEDWAITING, ExecutionStatus.UPLOADWAITING, ExecutionStatus.RUNNING);
  private static final List<String> PROJECTION_FIELDS =
      Arrays.asList(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.pipelineIdentifier,
          PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.orgIdentifier,
          PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.internalStatus,
          PlanExecutionSummaryKeys.priorityType, PlanExecutionSummaryKeys.startTs, PlanExecutionSummaryKeys.createdAt,
          PlanExecutionSummaryKeys.executionTriggerInfo, PlanExecutionSummaryKeys.runSequence,
          PlanExecutionSummaryKeys.tags, PlanExecutionSummaryKeys.labels, PlanExecutionSummaryKeys.parentUniqueId);

  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject private PMSExecutionService pmsExecutionService;

  @Override
  public QueuedPipelineListResponse listQueuedPipelines(String accountId, String filterIdentifier,
      QueuedPipelineFilterDTO filter, String searchTerm, int page, int size) {
    long startTime = System.currentTimeMillis();
    String source = SOURCE_MONGO;
    try {
      validateAccess(accountId);
      if (page < 0 || size <= 0 || size > 100) {
        throw new InvalidRequestException(
            "Invalid page request: page should be >= 0 and size should be > 0 and <= 100");
      }
      if (EmptyPredicate.isNotEmpty(filterIdentifier) && filter != null) {
        throw new InvalidRequestException(
            "Cannot apply both a saved filter (filterIdentifier) and inline filter properties at the same time");
      }
      if (EmptyPredicate.isNotEmpty(filterIdentifier)) {
        filter = resolveSavedFilter(accountId, filterIdentifier);
      }
      validateScopeFilter(filter);

      boolean useScopeInfo = true;
      Set<String> resolvedParentUniqueIds = resolveParentUniqueIds(accountId, filter);

      boolean useElastic = pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH);
      source = useElastic ? SOURCE_ELASTICSEARCH : SOURCE_MONGO;

      long fetchStart = System.currentTimeMillis();
      List<PipelineExecutionSummaryEntity> entities;
      if (useElastic) {
        Set<ExecutionStatus> expectedStatuses = (filter != null && EmptyPredicate.isNotEmpty(filter.getStatuses()))
            ? new HashSet<>(filter.getStatuses())
            : new HashSet<>(ALL_EXECUTION_STATUSES);
        entities = fetchExecutionsFromElastic(accountId, ALL_EXECUTION_STATUSES, expectedStatuses);
      } else {
        entities = fetchExecutionsFromMongo(accountId, ALL_INTERNAL_STATUSES);
      }
      recordFetchDuration(accountId, source, System.currentTimeMillis() - fetchStart);

      // Queue positions are assigned only to queued rows
      int[] positionCounter = {1};
      List<QueuedPipelineExecutionDTO> numberedDtos =
          entities.stream()
              .map(entity -> toDto(entity, isQueued(entity) ? positionCounter[0]++ : null))
              .collect(Collectors.toList());

      int totalQueuedInAccount = (int) numberedDtos.stream().filter(dto -> isQueuedStatus(dto.getStatus())).count();
      int totalWaitingInAccount = (int) numberedDtos.stream().filter(dto -> isWaitingStatus(dto.getStatus())).count();
      int totalRunningInAccount = numberedDtos.size() - totalQueuedInAccount - totalWaitingInAccount;

      List<QueuedPipelineExecutionDTO> filtered =
          applyFilters(numberedDtos, filter, searchTerm, useScopeInfo, resolvedParentUniqueIds);

      filtered.sort((a, b) -> {
        Long aCreatedAt = a.getCreatedAt() != null ? a.getCreatedAt() : Long.MAX_VALUE;
        Long bCreatedAt = b.getCreatedAt() != null ? b.getCreatedAt() : Long.MAX_VALUE;
        return aCreatedAt.compareTo(bCreatedAt);
      });

      int fromIndex = page * size;
      int toIndex = Math.min(fromIndex + size, filtered.size());
      List<QueuedPipelineExecutionDTO> pageContent =
          fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);

      Page<QueuedPipelineExecutionDTO> pagedResult =
          new PageImpl<>(pageContent, PageRequest.of(page, size), filtered.size());

      QueuedPipelineListResponse response =
          QueuedPipelineListResponse.builder()
              .queuedExecutions(pagedResult)
              .totalQueuedInAccount(totalQueuedInAccount)
              .totalWaitingInAccount(totalWaitingInAccount)
              .totalRunningInAccount(totalRunningInAccount)
              .maxConcurrency(pipelineSettingsService.getMaxConcurrency(accountId))
              .currentRunning(pipelineSettingsService.getCurrentExecutionCount(accountId))
              .build();

      recordDepthMetric(accountId, source, STATUS_QUEUED, totalQueuedInAccount);
      recordDepthMetric(accountId, source, STATUS_WAITING, totalWaitingInAccount);
      recordDepthMetric(accountId, source, STATUS_RUNNING, totalRunningInAccount);
      return response;
    } finally {
      recordListInvocation(accountId, source, System.currentTimeMillis() - startTime);
    }
  }

  private boolean isQueued(PipelineExecutionSummaryEntity entity) {
    return QUEUED_EXECUTION_STATUSES.contains(entity.getStatus());
  }

  private boolean isQueuedStatus(ExecutionStatus status) {
    return QUEUED_EXECUTION_STATUSES.contains(status);
  }

  private boolean isWaitingStatus(ExecutionStatus status) {
    return WAITING_EXECUTION_STATUSES.contains(status);
  }

  private List<PipelineExecutionSummaryEntity> fetchExecutionsFromMongo(String accountId, List<Status> statuses) {
    Query query = buildExecutionsQuery(accountId, statuses);
    try (var stream = pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(query)) {
      return stream.collect(Collectors.toList());
    }
  }

  private List<PipelineExecutionSummaryEntity> fetchExecutionsFromElastic(
      String accountId, List<ExecutionStatus> statuses, Set<ExecutionStatus> expectedStatuses) {
    co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery =
        PipelineSearchHelper.buildQueuedExecutionsQuery(accountId, statuses);

    // Sort by createdAt ASC so global queue positions are stable.
    LinkedHashMap<String, SortOrder> sortingFields = new LinkedHashMap<>();
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.createdAt, SortOrder.Asc);
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.planExecutionId, SortOrder.Asc);

    // ElasticSearchStream paginates internally in batches of 1000, so MAX_QUEUED_FETCH_LIMIT
    // can exceed the single-request ES cap without hitting the batch size validation.
    List<String> planExecutionIds;
    try (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> stream =
             pipelineSearchService.fetchPipelineSearchReadExecutionSummaryDTO(accountId, esQuery,
                 Set.of(PipelineSearchReadExecutionSummaryDTOKeys.planExecutionId), sortingFields)) {
      planExecutionIds = StreamSupport.stream(stream.spliterator(), false)
                             .limit(MAX_QUEUED_FETCH_LIMIT)
                             .map(PipelineSearchReadExecutionSummaryDTO::getPlanExecutionId)
                             .collect(Collectors.toList());
    }

    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return Collections.emptyList();
    }

    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).in(planExecutionIds);
    List<PipelineExecutionSummaryEntity> hydrated =
        pmsExecutionSummaryRepository.findAllWithProjectionWithoutPagination(criteria, PROJECTION_FIELDS);

    List<PipelineExecutionSummaryEntity> filtered = dropDriftedStatuses(accountId, hydrated, expectedStatuses);

    // getSummaryEntitiesOrderedByExecutionIds enforces planExecutionIds.size() == entities.size(),
    // so trim the id list to only the surviving entities after the drift filter.
    if (filtered.size() != planExecutionIds.size()) {
      Set<String> survivingIds =
          filtered.stream().map(PipelineExecutionSummaryEntity::getPlanExecutionId).collect(Collectors.toSet());
      planExecutionIds = planExecutionIds.stream().filter(survivingIds::contains).collect(Collectors.toList());
    }

    return PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(planExecutionIds, filtered);
  }

  private List<PipelineExecutionSummaryEntity> dropDriftedStatuses(
      String accountId, List<PipelineExecutionSummaryEntity> hydrated, Set<ExecutionStatus> expectedStatuses) {
    if (EmptyPredicate.isEmpty(hydrated) || EmptyPredicate.isEmpty(expectedStatuses)) {
      return hydrated;
    }
    List<PipelineExecutionSummaryEntity> kept = new ArrayList<>(hydrated.size());
    int dropped = 0;
    for (PipelineExecutionSummaryEntity entity : hydrated) {
      if (expectedStatuses.contains(entity.getStatus())) {
        kept.add(entity);
      } else {
        dropped++;
      }
    }
    if (dropped > 0) {
      log.info("Dropped {} ES-hydrated row(s) for account {} whose Mongo status no longer matches expected statuses",
          dropped, accountId);
      recordEsMongoStatusDrift(accountId, dropped);
    }
    return kept;
  }

  private QueuedPipelineFilterDTO resolveSavedFilter(String accountId, String filterIdentifier) {
    FilterDTO filterDTO = filterService.get(accountId, null, null, filterIdentifier, FilterType.QUEUED_PIPELINE);
    if (filterDTO == null) {
      throw new InvalidRequestException(
          String.format("Could not find a queued pipeline filter with the identifier '%s'", filterIdentifier));
    }
    return (QueuedPipelineFilterDTO) filterDTO.getFilterProperties();
  }

  /**
   * Validates the scope filter input contract, regardless of FF state.
   * Case 3: projectIdentifiers without orgIdentifiers is always invalid.
   * Case 4: when projectIdentifiers are given, exactly one orgIdentifier must be supplied.
   */
  private void validateScopeFilter(QueuedPipelineFilterDTO filter) {
    if (filter == null) {
      return;
    }
    boolean hasOrgFilter = EmptyPredicate.isNotEmpty(filter.getOrgIdentifiers());
    boolean hasProjectFilter = EmptyPredicate.isNotEmpty(filter.getProjectIdentifiers());
    if (!hasOrgFilter && hasProjectFilter) {
      throw new InvalidRequestException(
          "Invalid scope: projectIdentifiers cannot be provided without at least one orgIdentifier");
    }
    if (hasOrgFilter && hasProjectFilter && filter.getOrgIdentifiers().size() != 1) {
      throw new InvalidRequestException(
          "Invalid scope: exactly one orgIdentifier must be provided when projectIdentifiers are specified");
    }
  }

  /**
   * Resolves parentUniqueIds for scope filtering when ScopeInfo FF is enabled.
   *
   * <p>Case 1: no org/project filter → return null (account-wide, no scope filtering needed).
   * <p>Case 2: org IDs only → fetch all project uniqueIds under each org via ScopeResolutionHelper.
   * <p>Case 4: org + project IDs → fetch project uniqueIds for the single org (single-org contract
   *     is enforced earlier in {@link #validateScopeFilter}).
   *
   * <p>Note: Cases 3 and 4 input validation are both handled by {@link #validateScopeFilter},
   *     which fires regardless of FF state.
   */
  private Set<String> resolveParentUniqueIds(String accountId, QueuedPipelineFilterDTO filter) {
    if (filter == null) {
      return null;
    }
    boolean hasOrgFilter = EmptyPredicate.isNotEmpty(filter.getOrgIdentifiers());
    boolean hasProjectFilter = EmptyPredicate.isNotEmpty(filter.getProjectIdentifiers());

    if (!hasOrgFilter) {
      return null;
    }

    try {
      if (!hasProjectFilter) {
        // Case 2: org(s) only → expand to all project uniqueIds under those orgs
        return resolveProjectUniqueIdsForOrgs(accountId, filter.getOrgIdentifiers());
      } else {
        // Case 4: org + projects — single-org contract already enforced by validateScopeFilter
        return resolveProjectUniqueIdsForProjects(
            accountId, filter.getOrgIdentifiers().get(0), filter.getProjectIdentifiers());
      }
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to resolve scope info while filtering queued pipelines. "
              + "Please verify the provided org/project identifiers are correct. Error: " + e.getMessage(),
          e);
    }
  }

  private Set<String> resolveProjectUniqueIdsForOrgs(String accountId, List<String> orgIdentifiers) {
    List<ScopeInfo> orgScopeInfos =
        scopeResolutionHelper.getScopeInfoListForOrgs(accountId, new HashSet<>(orgIdentifiers));

    Set<String> resolvedOrgIds = orgScopeInfos.stream().map(ScopeInfo::getOrgIdentifier).collect(Collectors.toSet());
    Set<String> unresolvedOrgs =
        orgIdentifiers.stream().filter(id -> !resolvedOrgIds.contains(id)).collect(Collectors.toSet());
    if (!unresolvedOrgs.isEmpty()) {
      throw new InvalidRequestException("Could not resolve scope info for org identifier(s): " + unresolvedOrgs
          + ". Please verify they exist and have not been deleted.");
    }

    List<CompletableFuture<List<String>>> futures =
        orgScopeInfos.stream()
            .map(orgScopeInfo
                -> CompletableFuture.supplyAsync(
                    () -> scopeResolutionHelper.getProjectUniqueIds(accountId, orgScopeInfo.getUniqueId())))
            .collect(Collectors.toList());

    return futures.stream()
        .map(CompletableFuture::join)
        .filter(EmptyPredicate::isNotEmpty)
        .flatMap(List::stream)
        .collect(Collectors.toSet());
  }

  private Set<String> resolveProjectUniqueIdsForProjects(
      String accountId, String orgIdentifier, List<String> projectIdentifiers) {
    List<ScopeInfo> projectScopeInfos =
        scopeResolutionHelper.getScopeInfoListForProjects(accountId, orgIdentifier, new HashSet<>(projectIdentifiers));

    Set<String> resolvedProjectIds =
        projectScopeInfos.stream().map(ScopeInfo::getProjectIdentifier).collect(Collectors.toSet());
    Set<String> unresolvedProjects =
        projectIdentifiers.stream().filter(id -> !resolvedProjectIds.contains(id)).collect(Collectors.toSet());
    if (!unresolvedProjects.isEmpty()) {
      throw new InvalidRequestException("Could not resolve scope info for project identifier(s): " + unresolvedProjects
          + " under org '" + orgIdentifier + "'. Please verify they exist and have not been deleted.");
    }

    return projectScopeInfos.stream().map(ScopeInfo::getUniqueId).collect(Collectors.toSet());
  }

  @Override
  public QueuedPipelineBulkAbortResponseDTO bulkAbortQueuedPipelines(String accountId, List<String> planExecutionIds) {
    long startTime = System.currentTimeMillis();
    try {
      validateAccess(accountId);
      validateBulkAbortInput(planExecutionIds);

      Set<String> validIds = findValidExecutionIds(accountId, planExecutionIds);

      List<QueuedPipelineBulkAbortResultDTO> results = new ArrayList<>();
      for (String planExecutionId : planExecutionIds) {
        results.add(abortSingleExecution(planExecutionId, validIds));
      }

      int successCount = (int) results.stream().filter(QueuedPipelineBulkAbortResultDTO::isSuccess).count();
      int failureCount = results.size() - successCount;

      recordBulkAbortCounts(accountId, successCount, failureCount);

      return QueuedPipelineBulkAbortResponseDTO.builder()
          .results(results)
          .successCount(successCount)
          .failureCount(failureCount)
          .build();
    } finally {
      recordBulkAbortInvocation(accountId, System.currentTimeMillis() - startTime);
    }
  }

  private Criteria buildStatusCriteria(String accountId, List<Status> statuses) {
    return Criteria.where(PlanExecutionSummaryKeys.accountId)
        .is(accountId)
        .and(PlanExecutionSummaryKeys.internalStatus)
        .in(statuses);
  }

  private Query buildExecutionsQuery(String accountId, List<Status> statuses) {
    Query query = new Query(buildStatusCriteria(accountId, statuses));
    query.with(Sort.by(Sort.Direction.ASC, PlanExecutionSummaryKeys.createdAt));
    query.limit(MAX_QUEUED_FETCH_LIMIT);
    for (String field : PROJECTION_FIELDS) {
      query.fields().include(field);
    }
    return query;
  }

  private QueuedPipelineExecutionDTO toDto(PipelineExecutionSummaryEntity entity, Integer position) {
    return QueuedPipelineExecutionDTO.builder()
        .queuePosition(position)
        .planExecutionId(entity.getPlanExecutionId())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .pipelineName(entity.getName())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .status(entity.getStatus())
        .priorityType(entity.getPriorityType())
        .startTs(entity.getStartTs())
        .createdAt(entity.getCreatedAt())
        .executionTriggerInfo(entity.getExecutionTriggerInfo())
        .runSequence(entity.getRunSequence())
        .tags(entity.getTags())
        .labels(entity.getLabels())
        .parentUniqueId(entity.getParentUniqueId())
        .build();
  }

  private List<QueuedPipelineExecutionDTO> applyFilters(List<QueuedPipelineExecutionDTO> dtos,
      QueuedPipelineFilterDTO filter, String searchTerm, boolean useScopeInfo, Set<String> resolvedParentUniqueIds) {
    TimeRange effectiveTimeRange = buildEffectiveTimeRange(filter);

    if (filter == null && EmptyPredicate.isEmpty(searchTerm)) {
      return dtos.stream().filter(dto -> matchesTimeRange(dto, effectiveTimeRange)).collect(Collectors.toList());
    }

    return dtos.stream()
        .filter(dto -> matchesScopeFilter(dto, filter, useScopeInfo, resolvedParentUniqueIds))
        .filter(dto -> matchesPipelineFilter(dto, filter))
        .filter(dto -> matchesStatusFilter(dto, filter))
        .filter(dto -> matchesPriorityFilter(dto, filter))
        .filter(dto -> matchesTriggerTypeFilter(dto, filter))
        .filter(dto -> matchesTagFilter(dto, filter))
        .filter(dto -> matchesTimeRange(dto, effectiveTimeRange))
        .filter(dto -> matchesSearchTerm(dto, searchTerm))
        .collect(Collectors.toList());
  }

  private boolean matchesScopeFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter,
      boolean useScopeInfo, Set<String> resolvedParentUniqueIds) {
    if (filter == null) {
      return true;
    }

    boolean hasOrgFilter = EmptyPredicate.isNotEmpty(filter.getOrgIdentifiers());
    boolean hasProjectFilter = EmptyPredicate.isNotEmpty(filter.getProjectIdentifiers());

    if (!hasOrgFilter && !hasProjectFilter) {
      return true;
    }

    if (resolvedParentUniqueIds != null) {
      return dto.getParentUniqueId() != null && resolvedParentUniqueIds.contains(dto.getParentUniqueId());
    }

    if (hasOrgFilter && !filter.getOrgIdentifiers().contains(dto.getOrgIdentifier())) {
      return false;
    }
    if (hasProjectFilter && !filter.getProjectIdentifiers().contains(dto.getProjectIdentifier())) {
      return false;
    }
    return true;
  }

  private boolean matchesPipelineFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter) {
    if (filter == null || EmptyPredicate.isEmpty(filter.getPipelineIdentifiers())) {
      return true;
    }
    return filter.getPipelineIdentifiers().contains(dto.getPipelineIdentifier());
  }

  private boolean matchesStatusFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter) {
    if (filter == null || EmptyPredicate.isEmpty(filter.getStatuses())) {
      return true;
    }
    return filter.getStatuses().contains(dto.getStatus());
  }

  private boolean matchesPriorityFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter) {
    if (filter == null || EmptyPredicate.isEmpty(filter.getPriorityTypes())) {
      return true;
    }
    return filter.getPriorityTypes().contains(dto.getPriorityType());
  }

  private boolean matchesTriggerTypeFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter) {
    if (filter == null || EmptyPredicate.isEmpty(filter.getTriggerTypes())) {
      return true;
    }
    if (dto.getExecutionTriggerInfo() == null) {
      return false;
    }
    return filter.getTriggerTypes().contains(dto.getExecutionTriggerInfo().getTriggerType());
  }

  private boolean matchesTagFilter(QueuedPipelineExecutionDTO dto, QueuedPipelineFilterDTO filter) {
    if (filter == null || EmptyPredicate.isEmpty(filter.getPipelineTags())) {
      return true;
    }
    if (EmptyPredicate.isEmpty(dto.getTags())) {
      return false;
    }
    Set<String> entityTagKeys = dto.getTags().stream().map(NGTag::getKey).collect(Collectors.toSet());
    return filter.getPipelineTags().stream().anyMatch(filterTag -> entityTagKeys.contains(filterTag.getKey()));
  }

  private TimeRange buildEffectiveTimeRange(QueuedPipelineFilterDTO filter) {
    long now = System.currentTimeMillis();
    long lowerBound = now - MAX_LOOKBACK_MS;

    TimeRange callerRange = filter != null ? filter.getQueuedTimeRange() : null;
    if (callerRange == null) {
      return TimeRange.builder().startTime(lowerBound).endTime(now).build();
    }
    callerRange.resolveTimeRangeFilter();

    Long startTime = callerRange.getStartTime();
    Long endTime = callerRange.getEndTime();

    long effectiveStart = (startTime == null) ? lowerBound : Math.max(startTime, lowerBound);
    long effectiveEnd = (endTime == null) ? now : endTime;

    return TimeRange.builder().startTime(effectiveStart).endTime(effectiveEnd).build();
  }

  private boolean matchesTimeRange(QueuedPipelineExecutionDTO dto, TimeRange range) {
    // QUEUED_PLAN_CREATION rows have no startTs, so fall back to createdAt to keep them
    Long timestamp = dto.getStartTs() != null ? dto.getStartTs() : dto.getCreatedAt();
    if (timestamp == null) {
      return false;
    }
    return timestamp >= range.getStartTime() && timestamp <= range.getEndTime();
  }

  private boolean matchesSearchTerm(QueuedPipelineExecutionDTO dto, String searchTerm) {
    if (EmptyPredicate.isEmpty(searchTerm)) {
      return true;
    }
    String lowerSearch = searchTerm.toLowerCase();
    boolean nameMatch = dto.getPipelineName() != null && dto.getPipelineName().toLowerCase().contains(lowerSearch);
    boolean idMatch =
        dto.getPipelineIdentifier() != null && dto.getPipelineIdentifier().toLowerCase().contains(lowerSearch);
    return nameMatch || idMatch;
  }

  private void validateAccess(String accountId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, null, null),
        Resource.of(ACCOUNT_RESOURCE_TYPE, accountId), ACCOUNT_EDIT_PERMISSION);
  }

  private void validateBulkAbortInput(List<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      throw new InvalidRequestException("planExecutionIds cannot be empty");
    }
    if (planExecutionIds.size() > MAX_BULK_ABORT_LIMIT) {
      throw new InvalidRequestException("Cannot abort more than " + MAX_BULK_ABORT_LIMIT + " executions at once");
    }
  }

  private Set<String> findValidExecutionIds(String accountId, List<String> planExecutionIds) {
    Criteria criteria = buildStatusCriteria(accountId, ALL_INTERNAL_STATUSES)
                            .and(PlanExecutionSummaryKeys.planExecutionId)
                            .in(planExecutionIds);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);

    try (var stream = pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(query)) {
      return stream.map(PipelineExecutionSummaryEntity::getPlanExecutionId).collect(Collectors.toSet());
    }
  }

  private QueuedPipelineBulkAbortResultDTO abortSingleExecution(String planExecutionId, Set<String> validIds) {
    if (!validIds.contains(planExecutionId)) {
      return QueuedPipelineBulkAbortResultDTO.builder()
          .planExecutionId(planExecutionId)
          .success(false)
          .errorMessage(EXECUTION_NOT_ABORTABLE_ERROR_MESSAGE)
          .build();
    }
    try {
      pmsExecutionService.registerInterrupt(PlanExecutionInterruptType.ABORTALL, planExecutionId, null);
      return QueuedPipelineBulkAbortResultDTO.builder().planExecutionId(planExecutionId).success(true).build();
    } catch (Exception e) {
      log.error("Failed to abort execution {}", planExecutionId, e);
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return QueuedPipelineBulkAbortResultDTO.builder()
          .planExecutionId(planExecutionId)
          .success(false)
          .errorMessage(message)
          .build();
    }
  }

  private void recordListInvocation(String accountId, String source, long durationMs) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(
             ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID, accountId, SOURCE_LABEL, source))) {
      metricService.incCounter(QUEUED_PIPELINES_LIST_COUNT);
      metricService.recordDuration(QUEUED_PIPELINES_LIST_DURATION, Duration.ofMillis(durationMs));
    }
  }

  private void recordDepthMetric(String accountId, String source, String status, int total) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(ImmutableMap.of(
             PmsEventMonitoringConstants.ACCOUNT_ID, accountId, SOURCE_LABEL, source, STATUS_LABEL, status))) {
      metricService.recordMetric(QUEUED_PIPELINES_TOTAL_IN_ACCOUNT, total);
    }
  }

  private void recordFetchDuration(String accountId, String source, long durationMs) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(
             ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID, accountId, SOURCE_LABEL, source))) {
      metricService.recordDuration(QUEUED_PIPELINES_FETCH_DURATION, Duration.ofMillis(durationMs));
    }
  }

  private void recordEsMongoStatusDrift(String accountId, int droppedCount) {
    try (PmsMetricContextGuard ignore =
             new PmsMetricContextGuard(ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID, accountId))) {
      metricService.recordMetric(QUEUED_PIPELINES_ES_MONGO_STATUS_DRIFT, droppedCount);
    }
  }

  private void recordBulkAbortInvocation(String accountId, long durationMs) {
    try (PmsMetricContextGuard ignore =
             new PmsMetricContextGuard(ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID, accountId))) {
      metricService.recordDuration(QUEUED_PIPELINES_BULK_ABORT_DURATION, Duration.ofMillis(durationMs));
    }
  }

  private void recordBulkAbortCounts(String accountId, int successCount, int failureCount) {
    if (successCount > 0) {
      try (
          PmsMetricContextGuard ignore = new PmsMetricContextGuard(ImmutableMap.of(
              PmsEventMonitoringConstants.ACCOUNT_ID, accountId, PmsEventMonitoringConstants.STATUS, STATUS_SUCCESS))) {
        metricService.recordMetric(QUEUED_PIPELINES_BULK_ABORT_COUNT, successCount);
      }
    }
    if (failureCount > 0) {
      try (
          PmsMetricContextGuard ignore = new PmsMetricContextGuard(ImmutableMap.of(
              PmsEventMonitoringConstants.ACCOUNT_ID, accountId, PmsEventMonitoringConstants.STATUS, STATUS_FAILURE))) {
        metricService.recordMetric(QUEUED_PIPELINES_BULK_ABORT_COUNT, failureCount);
      }
    }
  }
}
