/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS_ANY;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_INCLUDING_ENDS;
import static io.harness.elasticsearch.framework.OperatorEnum.SHOULD_MATCH_AT_LEAST_ONE;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK_CUSTOM;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.engine.GovernanceServiceHelper;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.remote.GitSyncManagerClient;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.governance.GovernanceMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.EvaluationDetailsResponse;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.interrupts.SystemIssuer;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.filter.utils.ModuleInfoFilterUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetDetailsDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.ModuleInfoOperators;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionInterruptTypeMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.CustomPage;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionModeFilter;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineFilterDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.search.helper.PipelineSearchHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.service.PipelineSearchService;
import io.harness.search.utils.PipelineSearchUtils;
import io.harness.security.PrincipalHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.ProtoUtils;
import io.harness.service.GraphGenerationService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.NGLabel;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jooq.tools.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSExecutionServiceImpl implements PMSExecutionService {
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  @Inject private GraphGenerationService graphGenerationService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private FilterService filterService;
  @Inject private TriggeredByHelper triggeredByHelper;
  @Inject private YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Inject private ValidateAndMergeHelper validateAndMergeHelper;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private GitSyncSdkService gitSyncSdkService;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private PipelineTelemetryHelper pipelineTelemetryHelper;
  @Inject private PlanExecutionService planExecutionService;

  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject GitSyncManagerClient gitSyncManagerClient;
  @Inject ExecutionRetentionService executionRetentionService;
  @Inject PipelineSearchService pipelineSearchService;
  @Inject OpaServiceClientHelper opaServiceClientHelper;
  @Inject private PMSInputSetService pmsInputSetService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private NodeExecutionService nodeExecutionService;

  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";

  private static final String BRANCH_LIST_SIZE_EXCEPTION = "The size of unique branches list is greater than [%d]";

  private static final String PARENT_PATH_MODULE_INFO = "moduleInfo";

  private static final String COMMON_MODULE_FOR_CUSTOM_STAGE = "common";
  private static final String EMAIL = "email";
  private static final List<String> PROJECTION_FIELDS_FOR_OUTLINE =
      Arrays.asList(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.orgIdentifier,
          PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
          PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.status,
          PlanExecutionSummaryKeys.failureInfo, PlanExecutionSummaryKeys.startTs, PlanExecutionSummaryKeys.endTs,
          PlanExecutionSummaryKeys.layoutNodeMap, PlanExecutionSummaryKeys.modules, PlanExecutionSummaryKeys.createdAt,
          PlanExecutionSummaryKeys.lastUpdatedAt, PlanExecutionSummaryKeys.resolvedUserInputSetYaml,
          PlanExecutionSummaryKeys.startingNodeId, PlanExecutionSummaryKeys.runSequence);

  private static final String INDEX_TO_HINT_FOR_OUTLINE =
      "accountId_parentUniqueId_startTs_planExecutionId_status_pipelineIdentifier";
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  private boolean checkIfCriteriaIsPopulated(Criteria customCriteria) {
    return !customCriteria.equals(new Criteria());
  }

  @Override
  public Criteria formFilterCriteria(String accountId, String orgId, String projectId,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, Long lastSeenStartTime,
      String lastSeenExecutionId, Criteria criteria) {
    if (isNotEmpty(pipelineExecutionOutlineFilterDTO.getStatus())) {
      List<ExecutionStatus> statusList = pipelineExecutionOutlineFilterDTO.getStatus();
      if (isQueueBasedPlanCreationFFEnabled(accountId)) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      criteria.and(PlanExecutionSummaryKeys.status).in(statusList);
    }

    if (isNotEmpty(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier())) {
      criteria.and(PlanExecutionSummaryKeys.pipelineIdentifier)
          .is(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier());
    }

    if (isNotEmpty(pipelineExecutionOutlineFilterDTO.getPlanExecutionIds())) {
      criteria.and(PlanExecutionSummaryKeys.planExecutionId)
          .in(pipelineExecutionOutlineFilterDTO.getPlanExecutionIds());
    }
    formTimeBasedCriteria(
        pipelineExecutionOutlineFilterDTO, criteria, lastSeenStartTime, lastSeenExecutionId, accountId);
    return criteria;
  }

  private void checkAndThrowIfExecutionIdsNotPermitted(
      String accountId, String orgId, String projectId, List<String> planExecutionIds) {
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        fetchExecutionSummaries(accountId, planExecutionIds,
            Arrays.asList(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.pipelineIdentifier));
    Map<String, List<String>> pipelinePlanExecutionIdMapping = new HashMap<>();
    for (PipelineExecutionSummaryEntity entity : pipelineExecutionSummaryEntities) {
      pipelinePlanExecutionIdMapping.computeIfAbsent(entity.getPipelineIdentifier(), k -> new ArrayList<>())
          .add(entity.getPlanExecutionId());
    }

    Set<String> allPipelineIds = pipelinePlanExecutionIdMapping.keySet();

    List<String> permittedPipelineIds = pmsPipelineService.getPermittedToViewPipelineIdentifiers(
        accountId, orgId, projectId, new ArrayList<>(allPipelineIds));

    // Remove permitted pipeline IDs from requested pipeline IDs
    permittedPipelineIds.forEach(allPipelineIds::remove);

    if (!allPipelineIds.isEmpty()) {
      List<String> nonPermittedPlanExecutionIds =
          allPipelineIds.stream()
              .flatMap(pipelineId -> pipelinePlanExecutionIdMapping.get(pipelineId).stream())
              .toList();

      throw new AccessDeniedException(
          String.format("Missing permission %s on %s for executionIds: %s", PipelineRbacPermissions.PIPELINE_VIEW,
              "pipeline", StringUtils.join(nonPermittedPlanExecutionIds, ",")),
          ErrorCode.NG_ACCESS_DENIED, USER);
    }
  }

  private void updatePipelineExecutionOutlineFilterDTOTimeRange(
      String accountIdentifier, PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO) {
    TimeRange timeRange = pipelineExecutionOutlineFilterDTO.getTimeRange();
    if (timeRange == null) {
      timeRange = setTimeRangeIfNotAlreadySet();
      pipelineExecutionOutlineFilterDTO.setTimeRange(timeRange);
    }
  }

  private void formTimeBasedCriteria(PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO,
      Criteria criteria, Long lastSeenStartTime, String lastSeenExecutionId, String accountId) {
    // updating time range if it's null
    updatePipelineExecutionOutlineFilterDTOTimeRange(accountId, pipelineExecutionOutlineFilterDTO);
    TimeRange timeRange = pipelineExecutionOutlineFilterDTO.getTimeRange();

    List<Criteria> orCriteria = new ArrayList<>();
    boolean endTimeEqualCriteriaMet = false;

    // Time range may or may not be defined by the user. Irrespective of the time range being available,
    // lastSeenStartTime should always be set whenever it is not null since that is our data set
    if (isNotEmpty(lastSeenExecutionId) && lastSeenStartTime != null) {
      // set lastSeenStartTime and lastSeenExecutionId
      orCriteria.add(Criteria.where(PlanExecutionSummaryKeys.startTs)
                         .is(lastSeenStartTime)
                         .and(PlanExecutionSummaryKeys.planExecutionId)
                         .lt(lastSeenExecutionId));
      // update the range for startTs, since the previous range is already retrieved
      timeRange.setEndTime(lastSeenStartTime);

      // this indicates that the equality criteria for endStartTs is already met. Hence, the range passed to the filter
      // can exclude the equality criteria for endTime. We do this to avoid duplicates in the result set.
      endTimeEqualCriteriaMet = true;
    }

    Criteria timeRangeCriteria = buildTimeRangeCriteria(timeRange, endTimeEqualCriteriaMet);

    if (timeRangeCriteria != null) {
      orCriteria.add(timeRangeCriteria);
    }

    if (!orCriteria.isEmpty()) {
      criteria.orOperator(orCriteria);
    }
  }

  public Criteria buildTimeRangeCriteria(TimeRange timeRange, boolean endTimeEqualCriteriaMet) {
    if (timeRange == null) {
      return null;
    }
    Long startTime = timeRange.getStartTime();
    Long endTime = timeRange.getEndTime();

    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.startTs);

    boolean isCriteriaSet = false;
    if (startTime != null) {
      criteria.gte(startTime);
      isCriteriaSet = true;
    }
    if (endTime != null) {
      if (endTimeEqualCriteriaMet) {
        criteria.lt(endTime);
      } else {
        criteria.lte(endTime);
      }
      isCriteriaSet = true;
    }

    return isCriteriaSet ? criteria : null;
  }

  private void setScopeCriteria(Criteria criteria, String accountId, String orgId, String projectId) {
    if (isNotEmpty(accountId)) {
      criteria.and(PlanExecutionSummaryKeys.accountId).is(accountId);
    }
    if (isNotEmpty(orgId)) {
      criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projectId)) {
      criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(projectId);
    }
  }

  private void setScopeCriteria(Criteria criteria, ScopeInfo scopeInfo) {
    criteria.and(PlanExecutionSummaryKeys.accountId)
        .is(scopeInfo.getAccountIdentifier())
        .and(PlanExecutionSummaryKeys.parentUniqueId)
        .is(scopeInfo.getUniqueId());
  }

  private List<PipelineExecutionSummaryEntity> getOutlineExecutionSummaryEntityListFromElastic(String accountIdentifier,
      String orgIdentifier, String projectIdentifier,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, String lastSeenExecutionId,
      Long lastSeenStartTime, int size, ScopeInfo scopeInfo) {
    Query elaticQuery = formQueryForListingExecutionOutlines(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineExecutionOutlineFilterDTO, lastSeenExecutionId, lastSeenStartTime, scopeInfo);

    // sorting fields with ordered preserved
    LinkedHashMap<String, SortOrder> sortingFields = new LinkedHashMap<>();
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.startTs, SortOrder.Desc);
    sortingFields.put(PipelineSearchExecutionSummaryDTOKeys.planExecutionId, SortOrder.Desc);

    // last sorted values
    List<Object> lastSortedValues = null;
    if (isNotEmpty(lastSeenExecutionId) && lastSeenStartTime != null) {
      lastSortedValues = List.of(lastSeenStartTime, lastSeenExecutionId);
    }

    List<String> planExecutionIds = pipelineSearchService.listExecutionIdsWithSearchAfter(
        accountIdentifier, elaticQuery, sortingFields, lastSortedValues, size);
    if (isEmpty(planExecutionIds)) {
      return Collections.emptyList();
    }
    return PipelineSearchUtils.getSummaryEntitiesOrderedByExecutionIds(
        planExecutionIds, fetchExecutionSummaries(accountIdentifier, planExecutionIds, PROJECTION_FIELDS_FOR_OUTLINE));
  }

  @Override
  public CustomPage<PipelineExecutionOutlineDTO> getListOfExecutionsOutline(String accountId, String orgId,
      String projectId, PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, String lastSeenExecutionId,
      Long lastSeenStartTime, int size) {
    if (pipelineExecutionOutlineFilterDTO == null) {
      pipelineExecutionOutlineFilterDTO = PipelineExecutionOutlineFilterDTO.builder().build();
    }
    checkForPipelineViewPermissions(accountId, orgId, projectId, pipelineExecutionOutlineFilterDTO);

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);

    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities;
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      pipelineExecutionSummaryEntities = getOutlineExecutionSummaryEntityListFromElastic(accountId, orgId, projectId,
          pipelineExecutionOutlineFilterDTO, lastSeenExecutionId, lastSeenStartTime, size, scopeInfo);
    } else {
      Criteria criteria = new Criteria();
      setScopeCriteria(criteria, scopeInfo);
      // rbac
      if (isEmpty(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier())
          && isEmpty(pipelineExecutionOutlineFilterDTO.getPlanExecutionIds())) {
        // if pipelineId, planExecutionIds are not available, we should fetch only executions of pipelines for which the
        // user has view access
        pmsPipelineServiceHelper.setCriteriaForPermittedPipelines(
            accountId, orgId, projectId, criteria, PlanExecutionSummaryKeys.pipelineIdentifier);
      }

      formFilterCriteria(accountId, orgId, projectId, pipelineExecutionOutlineFilterDTO, lastSeenStartTime,
          lastSeenExecutionId, criteria);

      Pageable pageRequest = PageRequest.of(0, size,
          Sort.by(Sort.Direction.DESC, PlanExecutionSummaryKeys.startTs, PlanExecutionSummaryKeys.planExecutionId));
      pipelineExecutionSummaryEntities = getPipelineExecutionSummaryEntityWithProjectionWithoutPagination(
          criteria, pageRequest, PROJECTION_FIELDS_FOR_OUTLINE, INDEX_TO_HINT_FOR_OUTLINE);
    }

    List<PipelineExecutionOutlineDTO> outlineDTOS =
        pipelineExecutionSummaryEntities.stream()
            .map(entity -> PipelineExecutionSummaryDtoMapper.toOutlineDto(entity, scopeInfo))
            .toList();

    int currentSize = outlineDTOS.size();
    String newLastSeenExecutionId = currentSize > 0 ? outlineDTOS.get(currentSize - 1).getPlanExecutionId() : null;
    Long newLastSeenStartTime = currentSize > 0 ? outlineDTOS.get(currentSize - 1).getStartTs() : null;

    /* TODO make hasMore return T/F based on the presence of more elements instead of currentSize. This way 1 extra API
     call at the end can be avoided in some cases where there are no new elements after the lastSeenExecutionId */
    return CustomPage.<PipelineExecutionOutlineDTO>builder()
        .content(outlineDTOS)
        .currentSize(currentSize)
        .lastSeenExecutionId(newLastSeenExecutionId)
        .lastSeenStartTime(newLastSeenStartTime)
        .hasMore(currentSize >= size)
        .build();
  }

  private Query formQueryForListingExecutionOutlines(String accountId, String orgId, String projectId,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, String lastSeenExecutionId,
      Long lastSeenStartTime, ScopeInfo scopeInfo) {
    List<Query> matchQueries = scopeInfo != null
        ? PipelineSearchHelper.getScopeQuery(accountId, scopeInfo.getUniqueId())
        : PipelineSearchHelper.getScopeQuery(accountId, orgId, projectId);
    if (isEmpty(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier())
        && isEmpty(pipelineExecutionOutlineFilterDTO.getPlanExecutionIds())) {
      addQueryForAllPermittedPipelines(accountId, orgId, projectId, matchQueries);
    }

    List<Query> filterQueries = PipelineSearchHelper.formFilterQueryForExecutionOutlines(
        pipelineExecutionOutlineFilterDTO.getStatus(), pipelineExecutionOutlineFilterDTO.getPipelineIdentifier(),
        pipelineExecutionOutlineFilterDTO.getPlanExecutionIds(), isQueueBasedPlanCreationFFEnabled(accountId));
    if (isNotEmpty(filterQueries)) {
      matchQueries.addAll(filterQueries);
    }

    // updating time range if it's null
    updatePipelineExecutionOutlineFilterDTOTimeRange(accountId, pipelineExecutionOutlineFilterDTO);
    Query timeBasedQuery = PipelineSearchHelper.buildTimeRangeQuery(pipelineExecutionOutlineFilterDTO.getTimeRange());
    if (timeBasedQuery != null) {
      matchQueries.add(timeBasedQuery);
    }
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  private boolean isQueueBasedPlanCreationFFEnabled(String accountId) {
    return pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION)
        || pmsFeatureFlagService.isEnabled(
            accountId, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS);
  }

  private void checkForPipelineViewPermissions(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO) {
    // pipelineId takes the highest priority for checking permissions
    if (isNotEmpty(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier())) {
      checkAndThrowIfPipelineNotPermitted(accountIdentifier, orgIdentifier, projectIdentifier,
          Set.of(pipelineExecutionOutlineFilterDTO.getPipelineIdentifier()));
    } else if (isNotEmpty(pipelineExecutionOutlineFilterDTO.getPlanExecutionIds())) {
      // check if any of the planExecutionIds have no view access. If so, throw an exception and stop
      checkAndThrowIfExecutionIdsNotPermitted(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineExecutionOutlineFilterDTO.getPlanExecutionIds());
    }
  }

  @Override
  public List<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntityWithProjectionWithoutPagination(
      Criteria criteria, Pageable pageable, List<String> projections, String hintIndex) {
    return pmsExecutionSummaryRespository.findAllWithProjectionWithoutPagination(
        criteria, pageable, projections, hintIndex);
  }

  @Override
  public Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      boolean showAllExecutions, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    Criteria criteria = new Criteria();
    if (useScopeInfo) {
      setScopeCriteria(criteria, scopeInfo);
    } else {
      setScopeCriteria(criteria, accountId, orgId, projectId);
    }
    PipelineExecutionFilterPropertiesDTO filterPropertiesDTO = useScopeInfo
        ? (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(scopeInfo, filterIdentifier, filterProperties)
        : (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(
              accountId, orgId, projectId, filterIdentifier, filterProperties);

    if (isNotEmpty(pipelineIdentifier)) {
      addCriteriaForPermittedPipeline(
          accountId, orgId, projectId, Collections.singletonList(pipelineIdentifier), criteria);
    } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getPipelineIdentifiers())) {
      // This will be only used for internal purposes to support IDP plugin to fetch the executions
      addCriteriaForPermittedPipeline(
          accountId, orgId, projectId, filterPropertiesDTO.getPipelineIdentifiers(), criteria);
    } else {
      // If the user does not have permission for all pipelines then add the criteria for only view permission pipeline
      pmsPipelineServiceHelper.setPermittedPipelines(
          accountId, orgId, projectId, criteria, PlanExecutionSummaryKeys.pipelineIdentifier);
    }
    // currently both filter and params takes status list, ignoring the status passed here in case of filters
    if (isNotEmpty(statusList)) {
      if (isQueueBasedPlanCreationFFEnabled(accountId)) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      criteria.and(PlanExecutionSummaryKeys.status).in(statusList);
    }
    // This condition is being used by some customers so we are not removing it at the moment.
    // showAllExecution will be handled by the ExecutionModeFilter once this condition has been removed.

    Criteria filterCriteria = new Criteria();
    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    if (filterPropertiesDTO != null) {
      // updating myDeployments based on filter value if filter has a value. Will select true if any of the value is
      // true
      if (filterPropertiesDTO.getMyDeployments() != null) {
        myDeployments = myDeployments || filterPropertiesDTO.getMyDeployments().booleanValue();
      }
      // handling bug where status are added from both query param and filters. Considering only params
      if (isNotEmpty(statusList)) {
        filterPropertiesDTO.setStatus(new ArrayList<>());
      }

      populatePipelineFilterANDOperator(filterCriteria, filterPropertiesDTO, accountId);
    } else {
      // If filterIdentifier and filterCriteria both are null then we need default behaviour.
      // So instead of duplicating the logic here, we are calling the same flow with filterCriteria with default
      // executionMode value
      populatePipelineFilterANDOperator(filterCriteria,
          PipelineExecutionFilterPropertiesDTO.builder().executionModeFilter(ExecutionModeFilter.DEFAULT).build(),
          accountId);
    }

    if (myDeployments) {
      if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS)) {
        List<Criteria> criteriaForMyDeployments = new ArrayList<>();
        // Criteria to fetch executions that were triggered manually
        criteriaForMyDeployments.add(Criteria.where(PlanExecutionSummaryKeys.triggerType)
                                         .is(MANUAL)
                                         .and(PlanExecutionSummaryKeys.triggeredByEmail)
                                         .is(triggeredByHelper.getFromSecurityContext().getExtraInfoMap().get(EMAIL)));
        String userIdentifier = triggeredByHelper.getFromSecurityContext().getUuid();
        List<String> userNamesForGitProviders = getUserNamesForGitProviders(accountId, userIdentifier);
        // Criteria to fetch executions that were triggered by Git webhook events.
        criteriaForMyDeployments.add(Criteria.where(PlanExecutionSummaryKeys.triggerType)
                                         .is(WEBHOOK)
                                         .and(PlanExecutionSummaryKeys.triggeredByGitUser)
                                         .in(userNamesForGitProviders));
        // Criteria to fetch executions fired by custom webhook triggers with authorization.
        criteriaForMyDeployments.add(Criteria.where(PlanExecutionSummaryKeys.triggerType)
                                         .is(WEBHOOK_CUSTOM)
                                         .and(PlanExecutionSummaryKeys.triggeredByEmail)
                                         .is(triggeredByHelper.getFromSecurityContext().getExtraInfoMap().get(EMAIL)));
        criteria.orOperator(criteriaForMyDeployments);
      } else {
        // Criteria to fetch executions that were triggered manually
        criteria.and(PlanExecutionSummaryKeys.triggerType)
            .is(MANUAL)
            .and(PlanExecutionSummaryKeys.triggeredByEmail)
            .is(triggeredByHelper.getFromSecurityContext().getExtraInfoMap().get(EMAIL));
      }
    }

    Criteria moduleCriteria = new Criteria();
    if (isNotEmpty(moduleName)) {
      // Pipelines having only pipeline stages like custom and approval
      moduleCriteria.orOperator(Criteria.where(PlanExecutionSummaryKeys.modules)
                                    .is(Collections.singletonList(ModuleType.PMS.name().toLowerCase())),
          Criteria.where(PlanExecutionSummaryKeys.modules).in(COMMON_MODULE_FOR_CUSTOM_STAGE),
          // Pipelines for checking in actual module
          Criteria.where(PlanExecutionSummaryKeys.modules).in(moduleName));
    }
    Criteria searchCriteria = new Criteria();
    if (isNotEmpty(searchTerm)) {
      try {
        searchCriteria.orOperator(where(PlanExecutionSummaryKeys.pipelineIdentifier)
                                      .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.name)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.tags + "." + NGTagKeys.key)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.tags + "." + NGTagKeys.value)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }

    Criteria gitCriteria = new Criteria();

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();

    if (gitEntityInfo != null) {
      //      Adding the branch filter if the branch is not null or default
      if (isNotEmpty(gitEntityInfo.getBranch()) && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getBranch())) {
        gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsBranch).is(gitEntityInfo.getBranch());
      } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getBranchName())) {
        gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsBranch).is(filterPropertiesDTO.getBranchName());
      }
      if (gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
        //     Adding the repoIdentifier for the old git sync flow
        if (isNotEmpty(gitEntityInfo.getYamlGitConfigId())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getYamlGitConfigId())) {
          gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoIdentifier)
              .is(gitEntityInfo.getYamlGitConfigId());
        }
      } else {
        //     Adding the repoName for the new git experience flow
        if (isNotEmpty(gitEntityInfo.getRepoName())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getRepoName())) {
          gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is(gitEntityInfo.getRepoName());
        } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getRepo())) {
          gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is(filterPropertiesDTO.getRepo());
        }
      }
    }

    List<Criteria> criteriaList = new LinkedList<>();
    if (checkIfCriteriaIsPopulated(gitCriteria)) {
      criteriaList.add(gitCriteria);
    }
    if (checkIfCriteriaIsPopulated(filterCriteria)) {
      criteriaList.add(filterCriteria);
    }
    if (checkIfCriteriaIsPopulated(moduleCriteria)) {
      criteriaList.add(moduleCriteria);
    }
    if (checkIfCriteriaIsPopulated(searchCriteria)) {
      criteriaList.add(searchCriteria);
    }

    if (!criteriaList.isEmpty()) {
      criteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    }
    return criteria;
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> listExecutionsFromElastic(
      String accountId, Pageable pageRequest, Query query, List<String> projections) {
    Page<String> pipelineSearchExecutionSummaryDTOS =
        pipelineSearchService.listExecutions(accountId, pageRequest, query);
    Map<String, PipelineExecutionSummaryEntity> mongoData = new HashMap<>();

    // We want to fetch all fields
    fetchExecutionSummaries(accountId, pipelineSearchExecutionSummaryDTOS.getContent(), projections)
        .forEach(e -> mongoData.put(e.getPlanExecutionId(), e));

    // The below map function automatically creates a list by putting elements one by one
    return pipelineSearchExecutionSummaryDTOS.map(id -> mongoData.computeIfAbsent(id, key -> {
      log.error(String.format("[ELASTIC_SEARCH]: Execution id not found in Mongo DB/Object store: %s", key));
      throw new InvalidRequestException(
          "The execution data seems to have been deleted, please reduce the page no. to a valid page");
    }));
  }

  @Override
  public Query formQueryForSearch(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    List<Query> matchQueries = useScopeInfo ? PipelineSearchHelper.getScopeQuery(accountId, scopeInfo.getUniqueId())
                                            : PipelineSearchHelper.getScopeQuery(accountId, orgId, projectId);
    PipelineExecutionFilterPropertiesDTO filterPropertiesDTO = useScopeInfo
        ? (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(scopeInfo, filterIdentifier, filterProperties)
        : (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(
              accountId, orgId, projectId, filterIdentifier, filterProperties);

    if (isNotEmpty(pipelineIdentifier)) {
      matchQueries.add(
          getPermittedPipelinesQuery(accountId, orgId, projectId, Collections.singletonList(pipelineIdentifier)));
    } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getPipelineIdentifiers())) {
      // This will be only used for internal purposes to support IDP plugin to fetch the executions
      matchQueries.add(
          getPermittedPipelinesQuery(accountId, orgId, projectId, filterPropertiesDTO.getPipelineIdentifiers()));
    } else {
      // If the user does not have permission for all pipelines then add the criteria for only view permission pipeline
      addQueryForAllPermittedPipelines(accountId, orgId, projectId, matchQueries);
    }

    if (isNotEmpty(statusList)) {
      if (isQueueBasedPlanCreationFFEnabled(accountId)) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      matchQueries.add(PipelineSearchHelper.getStatusQuery(statusList));
    }

    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    matchQueries.add(PipelineSearchHelper.getExecutionModeQuery());

    if (filterPropertiesDTO == null) {
      filterPropertiesDTO = PipelineExecutionFilterPropertiesDTO.builder().build();
    }
    // As per old MongoDB flow we don't require a case for else
    // as we are not considering execution mode filter in elastic
    // updating myDeployments based on filter value if filter has a value. Will select true if any of the value is
    // true
    if (filterPropertiesDTO.getMyDeployments() != null) {
      myDeployments = myDeployments || filterPropertiesDTO.getMyDeployments();
    }
    // handling bug where status are added from both query param and filters. Considering only params
    if (isNotEmpty(statusList)) {
      filterPropertiesDTO.setStatus(new ArrayList<>());
    }

    matchQueries.addAll(
        populatePipelineExecutionFilterProperties(accountId, filterPropertiesDTO, ModuleInfoOperators.AND, null));

    if (myDeployments) {
      matchQueries.addAll(getMyDeploymentsQueries(accountId));
    }

    if (isNotEmpty(moduleName)) {
      matchQueries.add(PipelineSearchHelper.getModuleNameQuery(moduleName));
    }
    if (isNotEmpty(searchTerm)) {
      matchQueries.add(PipelineSearchHelper.getSearchTermQuery(searchTerm));
    }

    matchQueries.addAll(getGitEntityQuery(accountId, orgId, projectId, filterPropertiesDTO));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  @Override
  public Query formQueryForSearchOROperator(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifiers, String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties,
      ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    List<Query> matchQueries = useScopeInfo
        ? PipelineSearchHelper.getScopeQuery(scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId())
        : PipelineSearchHelper.getScopeQuery(accountId, orgId, projectId);
    PipelineExecutionFilterPropertiesDTO filterPropertiesDTO = useScopeInfo
        ? (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(scopeInfo, filterIdentifier, filterProperties)
        : (PipelineExecutionFilterPropertiesDTO) fetchFilterPropertiesDTO(
              accountId, orgId, projectId, filterIdentifier, filterProperties);

    Query pipelineQuery = null;
    if (isNotEmpty(pipelineIdentifiers)) {
      pipelineQuery = getPermittedPipelinesQuery(accountId, orgId, projectId, pipelineIdentifiers);
    } else {
      // If the user does not have permission for all pipelines then add the criteria for only view permission pipeline
      addQueryForAllPermittedPipelines(accountId, orgId, projectId, matchQueries);
    }

    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    // As per old MongoDB flow we don't require a case for else
    // as we are not considering execution mode filter in elastic
    List<Query> filterModuleQueries = new ArrayList<>();

    // The inner method already has null check handling
    matchQueries.addAll(populatePipelineExecutionFilterProperties(
        accountId, filterPropertiesDTO, ModuleInfoOperators.OR, filterModuleQueries));
    List<Query> combinedOrQueries = new ArrayList<>();
    if (pipelineQuery != null) {
      combinedOrQueries.add(pipelineQuery);
    }
    if (isNotEmpty(filterModuleQueries)) {
      combinedOrQueries.addAll(filterModuleQueries);
    }
    if (isNotEmpty(combinedOrQueries)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, combinedOrQueries));
    }
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  private void addQueryForAllPermittedPipelines(String accountId, String orgId, String projectId, List<Query> queries) {
    Criteria criteria = new Criteria();
    setScopeCriteria(criteria, accountId, orgId, projectId);
    List<String> permittedPipelines =
        pmsPipelineServiceHelper.getPermittedPipelines(criteria, accountId, orgId, projectId);
    if (isNotEmpty(permittedPipelines)) {
      queries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, permittedPipelines));
    }
  }

  private List<Query> getMyDeploymentsQueries(String accountId) {
    List<Query> myDeploymentQueries = new ArrayList<>();
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS)) {
      List<Query> queryForMyDeployments = new ArrayList<>();
      TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();

      // Criteria to fetch executions that were triggered manually
      queryForMyDeployments.add(PipelineSearchHelper.getTriggeredByQuery(MANUAL.toString(), triggeredBy));

      // Criteria to fetch executions that were triggered by Git webhook events.
      String userIdentifier = triggeredBy.getUuid();
      List<String> userNamesForGitProviders = getUserNamesForGitProviders(accountId, userIdentifier);
      Query triggerTypeQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.triggerType, WEBHOOK.toString());
      Query gitUserQuery = ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.triggeredByGitUser, userNamesForGitProviders);
      queryForMyDeployments.add(
          ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, Arrays.asList(triggerTypeQuery, gitUserQuery)));

      // Criteria to fetch executions fired by custom webhook triggers with authorization.
      queryForMyDeployments.add(PipelineSearchHelper.getTriggeredByQuery(WEBHOOK_CUSTOM.toString(), triggeredBy));

      myDeploymentQueries.add(
          ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, queryForMyDeployments));
    } else {
      // Criteria to fetch executions that were triggered manually
      myDeploymentQueries.add(
          PipelineSearchHelper.getTriggeredByQuery(MANUAL.toString(), triggeredByHelper.getFromSecurityContext()));
    }
    return myDeploymentQueries;
  }

  private List<String> getUserNamesForGitProviders(String accountId, String userIdentifier) {
    List<String> userNamesForGitProviders = new ArrayList<>();
    try {
      // Inter-service call to fetch git providers info
      UserSourceCodeManagerResponseDTOList userSourceCodeManagerResponseDTOLists =
          NGRestUtils.getResponse(gitSyncManagerClient.get(accountId, userIdentifier));
      userNamesForGitProviders = userSourceCodeManagerResponseDTOLists.getUserSourceCodeManagerResponseDTOList()
                                     .stream()
                                     .map(UserSourceCodeManagerResponseDTO::getUserName)
                                     .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Failed to retrieve git providers information for accountId: " + accountId
              + " and userIdentifier: " + userIdentifier,
          e);
    }
    return userNamesForGitProviders;
  }

  private List<Query> getGitEntityQuery(
      String accountId, String orgId, String projectId, PipelineExecutionFilterPropertiesDTO filterPropertiesDTO) {
    List<Query> gitEntityQueries = new ArrayList<>();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (gitEntityInfo != null) {
      //      Adding the branch filter if the branch is not null or default
      if (isNotEmpty(gitEntityInfo.getBranch()) && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getBranch())) {
        gitEntityQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.entityGitDetailsBranch, gitEntityInfo.getBranch()));
      } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getBranchName())) {
        gitEntityQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.entityGitDetailsBranch, filterPropertiesDTO.getBranchName()));
      }
      if (gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
        //     Adding the repoIdentifier for the old git sync flow
        if (isNotEmpty(gitEntityInfo.getYamlGitConfigId())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getYamlGitConfigId())) {
          gitEntityQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(EQUALS,
              PipelineSearchExecutionSummaryDTOKeys.entityGitDetailsRepoIdentifier,
              gitEntityInfo.getYamlGitConfigId()));
        }
      } else {
        //     Adding the repoName for the new git experience flow
        if (isNotEmpty(gitEntityInfo.getRepoName())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getRepoName())) {
          gitEntityQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
              EQUALS, PipelineSearchExecutionSummaryDTOKeys.entityGitDetailsRepoName, gitEntityInfo.getRepoName()));
        } else if (filterPropertiesDTO != null && isNotEmpty(filterPropertiesDTO.getRepo())) {
          gitEntityQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
              EQUALS, PipelineSearchExecutionSummaryDTOKeys.entityGitDetailsRepoName, filterPropertiesDTO.getRepo()));
        }
      }
    }
    return gitEntityQueries;
  }

  private Query getPermittedPipelinesQuery(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifiers) {
    List<String> permittedPipelineIdentifier =
        pmsPipelineService.getPermittedPipelineIdentifier(accountId, orgId, projectId, pipelineIdentifiers);

    if (isNotEmpty(permittedPipelineIdentifier)) {
      return ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, pipelineIdentifiers);
    } else {
      throw new AccessDeniedException(
          String.format("Missing permission %s on %s", PipelineRbacPermissions.PIPELINE_VIEW, "pipeline"),
          ErrorCode.NG_ACCESS_DENIED, USER);
    }
  }

  private void setTimeRangeFilter(String accountId, TimeRange timeRange, List<Query> queries) {
    if (timeRange == null) {
      timeRange = setTimeRangeIfNotAlreadySet();
    }
    if (timeRange != null && timeRange.resolveTimeRangeFilter()) {
      queries.add(ElasticSearchQueryBuilder.buildRangeQuery(RANGE_INCLUDING_ENDS,
          PipelineSearchExecutionSummaryDTOKeys.startTs, timeRange.getStartTime(), timeRange.getEndTime()));
    }
  }

  private List<Query> populatePipelineExecutionFilterProperties(String accountId,
      @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, ModuleInfoOperators operatorOnModules,
      List<Query> orQueryList) {
    List<Query> matchQueries = new ArrayList<>();
    if (pipelineFilter == null) {
      pipelineFilter = PipelineExecutionFilterPropertiesDTO.builder().build();
    }
    setTimeRangeFilter(accountId, pipelineFilter.getTimeRange(), matchQueries);

    if (isNotEmpty(pipelineFilter.getStatus())) {
      List<ExecutionStatus> statusList = pipelineFilter.getStatus();
      if (isQueueBasedPlanCreationFFEnabled(accountId)) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      matchQueries.add(PipelineSearchHelper.getStatusQuery(statusList));
    }

    if (isNotEmpty(pipelineFilter.getPipelineName())) {
      matchQueries.add(PipelineSearchHelper.getPipelineNameQuery(pipelineFilter.getPipelineName()));
    }
    if (pipelineFilter.getPipelineTagsV2() != null) {
      matchQueries.add(PipelineSearchHelper.getPipelineTagsQueryV2(pipelineFilter.getPipelineTagsV2()));
    } else if (isNotEmpty(pipelineFilter.getPipelineTags())) {
      matchQueries.add(PipelineSearchHelper.getPipelineTagsQuery(pipelineFilter.getPipelineTags()));
    }
    if (isNotEmpty(pipelineFilter.getTriggerIdentifiers())) {
      matchQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.triggerIdentifier, pipelineFilter.getTriggerIdentifiers()));
    }
    if (isNotEmpty(pipelineFilter.getTriggerTypes())) {
      matchQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(EQUALS_ANY,
          PipelineSearchExecutionSummaryDTOKeys.triggerType,
          pipelineFilter.getTriggerTypes().stream().map(Enum::toString).toList()));
    }
    if (isNotEmpty(pipelineFilter.getPlanExecutionIds())) {
      matchQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, pipelineFilter.getPlanExecutionIds()));
    }
    if (isNotEmpty(pipelineFilter.getInputSetIdentifiers())) {
      matchQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(EQUALS_ANY,
          PipelineSearchExecutionSummaryDTOKeys.inputSetIdentifiers, pipelineFilter.getInputSetIdentifiers()));
    }
    if (pipelineFilter.getModuleProperties() != null) {
      List<Query> modulePropertiesQuery = PipelineSearchHelper.getModulePropertiesQuery(
          pipelineFilter.getModuleProperties(), PARENT_PATH_MODULE_INFO, operatorOnModules);
      if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
        orQueryList.addAll(modulePropertiesQuery);
      } else {
        matchQueries.addAll(modulePropertiesQuery);
      }
    }
    if (isNotEmpty(pipelineFilter.getExecutionNotes())) {
      Query notesQuery = PipelineSearchHelper.getNotesQuery(pipelineFilter.getExecutionNotes());
      if (notesQuery != null) {
        matchQueries.add(notesQuery);
      }
    }
    return matchQueries;
  }

  private void setTimeRangeIfNotAlreadySet(PipelineExecutionFilterPropertiesDTO filterPropertiesDTO) {
    // passing default time range of 1 month if not sent in params
    if (filterPropertiesDTO.getTimeRange() == null) {
      LocalDateTime oneMonthAgoStartOfDay = LocalDate.now().minusMonths(1).atStartOfDay();
      filterPropertiesDTO.setTimeRange(
          TimeRange.builder()
              .startTime(oneMonthAgoStartOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
              .endTime(System.currentTimeMillis())
              .build());
    }
  }

  private TimeRange setTimeRangeIfNotAlreadySet() {
    // passing default time range of 1 month if not sent in params
    LocalDateTime oneMonthAgoStartOfDay = LocalDate.now().minusMonths(1).atStartOfDay();
    return TimeRange.builder()
        .startTime(oneMonthAgoStartOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        .endTime(System.currentTimeMillis())
        .build();
  }

  private FilterPropertiesDTO fetchFilterPropertiesDTO(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filterIdentifier, FilterPropertiesDTO filterProperties) {
    if (filterIdentifier != null) {
      FilterDTO filterDTO = filterService.get(
          accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINEEXECUTION);
      if (filterDTO == null) {
        throw new InvalidRequestException(
            format("Could not find a pipeline filter with the identifier %s", filterIdentifier));
      }
      return filterDTO.getFilterProperties();
    }
    return filterProperties;
  }

  private FilterPropertiesDTO fetchFilterPropertiesDTO(
      ScopeInfo scopeInfo, String filterIdentifier, FilterPropertiesDTO filterProperties) {
    if (filterIdentifier != null) {
      FilterDTO filterDTO = filterService.get(scopeInfo, filterIdentifier, FilterType.PIPELINEEXECUTION);
      if (filterDTO == null) {
        throw new InvalidRequestException(
            format("Could not find a pipeline filter with the identifier %s", filterIdentifier));
      }
      return filterDTO.getFilterProperties();
    }
    return filterProperties;
  }

  @Override
  public Criteria formCriteriaOROperatorOnModules(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String filterIdentifier) {
    Criteria criteria = new Criteria();
    setScopeCriteria(criteria, accountId, orgId, projectId);
    Criteria pipelineCriteria = new Criteria();
    if (isNotEmpty(pipelineIdentifier)) {
      addCriteriaForPermittedPipeline(accountId, orgId, projectId, pipelineIdentifier, pipelineCriteria);
    } else {
      pmsPipelineServiceHelper.setPermittedPipelines(
          accountId, orgId, projectId, criteria, PlanExecutionSummaryKeys.pipelineIdentifier);
    }

    Criteria filterCriteria = new Criteria();
    List<Criteria> filterCriteriaList = new LinkedList<>();
    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (isNotEmpty(filterIdentifier) && filterProperties == null) {
      populatePipelineFilterUsingIdentifierOROperator(filterCriteria, accountId, orgId, projectId, filterIdentifier,
          filterCriteriaList, isNotEmpty(pipelineIdentifier));
    } else if (isEmpty(filterIdentifier)) {
      filterProperties =
          filterProperties == null ? PipelineExecutionFilterPropertiesDTO.builder().build() : filterProperties;
      populatePipelineFilterOROperator(filterCriteria, filterProperties, filterCriteriaList, accountId);
    }

    List<Criteria> criteriaList = new LinkedList<>();
    if (!pipelineCriteria.equals(new Criteria())) {
      criteriaList.add(pipelineCriteria);
    }

    if (!filterCriteria.equals(new Criteria())) {
      criteria.andOperator(filterCriteria);
    }

    if (!filterCriteriaList.isEmpty()) {
      criteriaList.addAll(filterCriteriaList);
    }

    if (criteriaList.isEmpty()) {
      return criteria;
    }

    return criteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
  }

  private void checkAndThrowIfPipelineNotPermitted(
      String accountId, String orgId, String projectId, Set<String> requestedPipelineIdentifiers) {
    List<String> permittedPipelineIdentifiers = pmsPipelineService.getPermittedToViewPipelineIdentifiers(
        accountId, orgId, projectId, new ArrayList<>(requestedPipelineIdentifiers));

    // TODO find and throw not permitted pipeline names as needed
    if (permittedPipelineIdentifiers.size() != requestedPipelineIdentifiers.size()) {
      throw new AccessDeniedException(
          String.format("Missing permission %s on %s", PipelineRbacPermissions.PIPELINE_VIEW, "pipeline"),
          ErrorCode.NG_ACCESS_DENIED, USER);
    }
  }

  private void addCriteriaForPermittedPipeline(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifiers, Criteria pipelineCriteria) {
    List<String> permittedPipelineIdentifier =
        pmsPipelineService.getPermittedPipelineIdentifier(accountId, orgId, projectId, pipelineIdentifiers);

    if (isNotEmpty(permittedPipelineIdentifier)) {
      pipelineCriteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).in(pipelineIdentifiers);
    } else {
      throw new AccessDeniedException(
          String.format("Missing permission %s on %s", PipelineRbacPermissions.PIPELINE_VIEW, "pipeline"),
          ErrorCode.NG_ACCESS_DENIED, USER);
    }
  }

  private void populatePipelineFilterUsingIdentifierOROperator(Criteria criteria, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier, List<Criteria> criteriaList,
      boolean isPipelineIdentifierPresent) {
    populatePipelineFilterUsingIdentifierParametrisedOperatorOnModules(criteria, accountIdentifier, orgIdentifier,
        projectIdentifier, filterIdentifier, ModuleInfoOperators.OR, criteriaList, isPipelineIdentifierPresent);
  }

  private void populatePipelineFilterUsingIdentifierParametrisedOperatorOnModules(Criteria criteria,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier,
      ModuleInfoOperators operatorOnModules, List<Criteria> criteriaList, boolean isPipelineIdentifierPresent) {
    FilterDTO pipelineFilterDTO = this.filterService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINEEXECUTION);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    }
    if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
      this.populatePipelineFilterOROperator(criteria,
          (PipelineExecutionFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties(), criteriaList,
          accountIdentifier);
    } else {
      this.populatePipelineFilterANDOperator(
          criteria, (PipelineExecutionFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties(), accountIdentifier);
    }
  }

  // This is the function created and parametrized on operator to apply on modules in filterProperties to obtain the
  // criteria.
  private void populatePipelineFilterParametrisedOperatorOnModules(Criteria criteria,
      @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, ModuleInfoOperators operatorOnModules,
      List<Criteria> criteriaList, String accountId) {
    if (pipelineFilter == null) {
      pipelineFilter = PipelineExecutionFilterPropertiesDTO.builder().build();
    }
    setTimeRangeFilter(accountId, pipelineFilter.getTimeRange(), criteria);
    if (isNotEmpty(pipelineFilter.getStatus())) {
      List<ExecutionStatus> statusList = pipelineFilter.getStatus();
      if (isQueueBasedPlanCreationFFEnabled(accountId)) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      criteria.and(PlanExecutionSummaryKeys.status).in(statusList);
    }
    if (isNotEmpty(pipelineFilter.getPlanExecutionIds())) {
      criteria.and(PlanExecutionSummaryKeys.planExecutionId).in(pipelineFilter.getPlanExecutionIds());
    }
    if (isNotEmpty(pipelineFilter.getInputSetIdentifiers())) {
      criteria.and(PlanExecutionSummaryKeys.inputSetIdentifiers).in(pipelineFilter.getInputSetIdentifiers());
    }
    criteria.and(PlanExecutionSummaryKeys.executionMode).ne(ExecutionMode.PIPELINE_ROLLBACK);

    if (isNotEmpty(pipelineFilter.getPipelineName())) {
      criteria.orOperator(
          where(PlanExecutionSummaryKeys.pipelineIdentifier)
              .regex(pipelineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PlanExecutionSummaryKeys.name)
              .regex(pipelineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    List<Criteria> combinedAndCriteriaList = new ArrayList<>();
    if (pipelineFilter.getPipelineTagsV2() != null) {
      PMSPipelineServiceHelper.addPipelineTagsCriteriaV2(combinedAndCriteriaList, pipelineFilter.getPipelineTagsV2());
    } else if (isNotEmpty(pipelineFilter.getPipelineTags())) {
      PMSPipelineServiceHelper.addPipelineTagsCriteria(combinedAndCriteriaList, pipelineFilter.getPipelineTags());
    }
    if (isNotEmpty(pipelineFilter.getPipelineLabels())) {
      addPipelineLabelsCriteria(combinedAndCriteriaList, pipelineFilter.getPipelineLabels());
    }
    if (isNotEmpty(pipelineFilter.getTriggerIdentifiers())) {
      Criteria triggerIdentifierCriteria = new Criteria();
      populateInFilter(triggerIdentifierCriteria, PlanExecutionSummaryKeys.triggerIdentifier,
          pipelineFilter.getTriggerIdentifiers());
      combinedAndCriteriaList.add(triggerIdentifierCriteria);
    }
    if (isNotEmpty(pipelineFilter.getTriggerTypes())) {
      Criteria triggerTypeCriteria = new Criteria();
      populateInFilter(triggerTypeCriteria, PlanExecutionSummaryKeys.triggerType, pipelineFilter.getTriggerTypes());
      combinedAndCriteriaList.add(triggerTypeCriteria);
    }
    if (combinedAndCriteriaList.size() > 0) {
      criteria.andOperator(combinedAndCriteriaList.toArray(new Criteria[combinedAndCriteriaList.size()]));
    }
    if (pipelineFilter.getModuleProperties() != null) {
      if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
        ModuleInfoFilterUtils.processModulePropertiesOROperator(
            pipelineFilter.getModuleProperties(), PARENT_PATH_MODULE_INFO, criteriaList);
      } else {
        ModuleInfoFilterUtils.processModuleProperties(
            pipelineFilter.getModuleProperties(), PARENT_PATH_MODULE_INFO, criteria);
      }
    }
    if (isNotEmpty(pipelineFilter.getExecutionNotes())) {
      String alternation = pipelineFilter.getExecutionNotes()
                               .stream()
                               .filter(t -> t != null && !t.isBlank())
                               .map(Pattern::quote)
                               .collect(Collectors.joining("|"));
      if (isNotEmpty(alternation)) {
        Pattern combined = Pattern.compile("(?:" + alternation + ")", Pattern.CASE_INSENSITIVE);
        criteria.and(PlanExecutionSummaryKeys.notes).regex(combined);
      }
    }
  }

  private void setTimeRangeFilter(String accountId, TimeRange timeRange, Criteria criteria) {
    if (timeRange == null) {
      timeRange = setTimeRangeIfNotAlreadySet();
    }
    if (timeRange != null && timeRange.resolveTimeRangeFilter()) {
      criteria.and(PlanExecutionSummaryKeys.startTs).gte(timeRange.getStartTime()).lte(timeRange.getEndTime());
    }
  }

  private void populatePipelineFilterANDOperator(
      Criteria criteria, @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, String accountId) {
    populatePipelineFilterParametrisedOperatorOnModules(
        criteria, pipelineFilter, ModuleInfoOperators.AND, null, accountId);
  }

  private void populatePipelineFilterOROperator(Criteria criteria,
      @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, List<Criteria> criteriaList, String accountId) {
    populatePipelineFilterParametrisedOperatorOnModules(
        criteria, pipelineFilter, ModuleInfoOperators.OR, criteriaList, accountId);
  }

  private void addPipelineLabelsCriteria(List<Criteria> criteriaList, List<NGLabel> pipelineLabels) {
    List<String> labelKeys = new ArrayList<>();
    List<String> labelValues = new ArrayList<>();
    pipelineLabels.forEach(o -> {
      labelKeys.add(o.getKey());
      labelValues.add(o.getValue());
    });
    Criteria labelsCriteria = new Criteria();
    labelsCriteria.orOperator(where(PlanExecutionSummaryKeys.labelsKey).in(labelKeys),
        where(PlanExecutionSummaryKeys.labelsValue).in(labelValues));
    criteriaList.add(labelsCriteria);
  }

  @Override
  public InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions,
      ResolveInputYamlType resolveExpressionsType) {
    // ToDo: Use Mongo Projections
    PipelineExecutionSummaryEntity executionSummaryEntity;
    try {
      executionSummaryEntity = fetchExecutionSummary(accountId, planExecutionId, pipelineDeleted);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          "Invalid request : Input Set did not exist or pipeline execution has been deleted");
    }

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    // InputSet yaml used during execution
    String yaml =
        resolveExpressionsInYaml(executionSummaryEntity, resolveExpressions, planExecutionId, resolveExpressionsType);

    StagesExecutionMetadata stagesExecutionMetadata = executionSummaryEntity.getStagesExecutionMetadata();

    // Get inputSet details (Id-Name)
    List<InputSetDetailsDTO> inputSetDetails = getInputSetDetails(accountId, orgId, projectId, executionSummaryEntity);
    return InputSetYamlWithTemplateDTO
        .builder()
        // template for pipelineYaml at the time of execution.
        .inputSetTemplateYaml(executionSummaryEntity.getPipelineTemplate())
        .inputSetYaml(yaml)
        .expressionValues(stagesExecutionMetadata != null ? stagesExecutionMetadata.getExpressionValues() : null)
        .inputSetDetails(inputSetDetails)
        .inputSetBranchName(executionSummaryEntity.getInputSetBranchName())
        .resolvedYaml(getResolvedPipelineYaml(accountId, planExecutionId, executionSummaryEntity.getPipelineVersion()))
        .build();
  }

  private String getResolvedPipelineYaml(String accountId, String planExecutionId, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return getExecutionData(accountId, planExecutionId).getExecutionYaml();
    }
    return null;
  }

  @Override
  public String getInputSetYamlForRerun(String accountId, String planExecutionId, boolean pipelineDeleted) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
        accountId, planExecutionId, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    return planExecutionMetadata.getInputSetYaml();
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String planExecutionId, boolean pipelineDeleted) {
    return fetchExecutionSummary(accountId, planExecutionId, pipelineDeleted);
  }

  @Override
  public PipelineExecutionSummaryEntity fetchExecutionSummary(
      String accountId, String planExecutionId, boolean pipelineDeleted) {
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountId,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);
    if (executionSummary == null) {
      Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
          pmsExecutionSummaryRespository.findByPlanExecutionIdAndPipelineDeletedNot(planExecutionId, !pipelineDeleted);
      if (pipelineExecutionSummaryEntityOptional.isPresent()) {
        return pipelineExecutionSummaryEntityOptional.get();
      }
      throw new EntityNotFoundException(
          "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
    } else {
      if (!executionSummary.getPipelineDeleted().equals(pipelineDeleted)) {
        throw new EntityNotFoundException(
            "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
      }
    }
    return executionSummary;
  }

  @Override
  public PipelineExecutionSummaryEntity fetchExecutionSummary(String accountId, String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountId,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);
    if (executionSummary == null) {
      Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
          pmsExecutionSummaryRespository.findByPlanExecutionId(planExecutionId);
      if (pipelineExecutionSummaryEntityOptional.isPresent()) {
        return pipelineExecutionSummaryEntityOptional.get();
      }
      throw new EntityNotFoundException(
          "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
    }
    return executionSummary;
  }

  @Override
  public PipelineExecutionSummaryEntity fetchExecutionSummaryFromDb(String planExecutionId, Set<String> projections) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    return pmsExecutionSummaryRespository.getPipelineExecutionSummaryWithProjections(criteria, projections);
  }

  private List<String> getProjectionsWithPlanExecutionIdKey(List<String> projections) {
    List<String> updatedProjections = new ArrayList<>();
    if (isNotEmpty(projections)) {
      for (String projection : projections) {
        if (PlanExecutionSummaryKeys.planExecutionId.equals(projection)) {
          return projections;
        }
        updatedProjections.add(projection);
      }
      // adding planExecutionId key as it's not present
      updatedProjections.add(PlanExecutionSummaryKeys.planExecutionId);
    }
    return updatedProjections;
  }

  private Map<String, PipelineExecutionSummaryEntity> getExecutionIdsFromMongoToSummariesMap(
      List<String> planExecutionIds, List<String> projections) {
    if (isNotEmpty(planExecutionIds)) {
      Criteria criteria = new Criteria();
      criteria.and(PlanExecutionSummaryKeys.planExecutionId).in(planExecutionIds);

      return pmsExecutionSummaryRespository
          .findAllWithProjectionWithoutPagination(criteria, getProjectionsWithPlanExecutionIdKey(projections))
          .stream()
          .collect(Collectors.toMap(PipelineExecutionSummaryEntity::getPlanExecutionId, entity -> entity));
    }
    return Collections.emptyMap();
  }

  @Override
  public List<PipelineExecutionSummaryEntity> fetchExecutionSummaries(
      String accountIdentifier, List<String> planExecutionIds, List<String> projections) {
    Map<String, Object> planExecutionIdsToObjectsMap =
        executionRetentionService.readExpiredRecordsFromObjectStore(accountIdentifier, planExecutionIds,
            ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class);

    List<String> planExecutionIdsToBeFetchedFromMongo =
        planExecutionIds.stream().filter(id -> !planExecutionIdsToObjectsMap.containsKey(id)).toList();

    Map<String, PipelineExecutionSummaryEntity> executionIdsToSummariesMap =
        getExecutionIdsFromMongoToSummariesMap(planExecutionIdsToBeFetchedFromMongo, projections);

    return planExecutionIds.stream()
        .map(planExecutionId -> {
          if (planExecutionIdsToObjectsMap.containsKey(planExecutionId)) {
            return (PipelineExecutionSummaryEntity) planExecutionIdsToObjectsMap.get(planExecutionId);
          } else {
            return executionIdsToSummariesMap.getOrDefault(planExecutionId, null);
          }
        })
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(String accountId, String planExecutionId) {
    return fetchExecutionSummary(accountId, planExecutionId);
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(String accountId, String orgId,
      String projectId, List<String> pipelineIdentifiers, String filterIdentifier,
      PipelineExecutionFilterPropertiesDTO filterProperties, Pageable pageable, ScopeInfo scopeInfo) {
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      Query query = formQueryForSearchOROperator(
          accountId, orgId, projectId, pipelineIdentifiers, filterIdentifier, filterProperties, scopeInfo);
      return listExecutionsFromElastic(accountId, pageable, query, null);
    } else {
      Criteria criteria = formCriteriaOROperatorOnModules(
          accountId, orgId, projectId, pipelineIdentifiers, filterProperties, filterIdentifier);
      return pmsExecutionSummaryRespository.findAll(criteria, pageable);
    }
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(
      Criteria criteria, Pageable pageable, String accountId, String sortProperty) {
    return pmsExecutionSummaryRespository.findAll(criteria, pageable, accountId, sortProperty);
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntityWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections) {
    return pmsExecutionSummaryRespository.findAllWithProjection(criteria, pageable, projections);
  }

  @Override
  public void sendGraphUpdateEvent(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    graphGenerationService.sendUpdateEventIfAny(pipelineExecutionSummaryEntity);
  }

  @Override
  public OrchestrationGraphDTO getOrchestrationGraph(
      String accountIdentifier, String stageNodeId, String planExecutionId, String stageNodeExecutionId) {
    if (isEmpty(stageNodeId)) {
      return graphGenerationService.generateOrchestrationGraphV2(accountIdentifier, planExecutionId);
    }
    return graphGenerationService.generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
        accountIdentifier, stageNodeId, planExecutionId, stageNodeExecutionId);
  }

  @Override
  public SimplifiedOrchestrationGraphDTO getSimplifiedOrchestrationGraph(
      String accountIdentifier, String planExecutionId) {
    return graphGenerationService.generateSimplifiedOrchestrationGraphV2(accountIdentifier, planExecutionId);
  }
  @Override
  public OrchestrationGraphDTO getOrchestrationGraphForAllStages(String accountIdentifier, String planExecutionId) {
    return graphGenerationService.generateOrchestrationGraphV2(accountIdentifier, planExecutionId);
  }

  @Override
  public InterruptDTO registerInterrupt(
      PlanExecutionInterruptType executionInterruptType, String planExecutionId, String nodeExecutionId) {
    final Principal principal = SecurityContextBuilder.getPrincipal();
    if (nodeExecutionId != null) {
      NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
      if (nodeExecution == null) {
        throw new InvalidRequestException("Node Execution not found");
      }
      checkIfInputInterruptIsApplicableForManualIntervention(executionInterruptType, nodeExecution);
    }
    InterruptConfig interruptConfig;

    if (PrincipalHelper.isManualIssuer(principal)) {
      interruptConfig = InterruptConfig.newBuilder()
                            .setIssuedBy(IssuedBy.newBuilder()
                                             .setManualIssuer(ManualIssuer.newBuilder()
                                                                  .setType(principal.getType().toString())
                                                                  .setIdentifier(principal.getName())
                                                                  .setEmailId(PrincipalHelper.getEmail(principal))
                                                                  .setUserId(PrincipalHelper.getUsername(principal))
                                                                  .build())
                                             .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                                             .build())
                            .build();
    } else {
      interruptConfig = InterruptConfig.newBuilder()
                            .setIssuedBy(IssuedBy.newBuilder()
                                             .setSystemIssuer(SystemIssuer.newBuilder().build())
                                             .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                                             .build())
                            .build();
    }
    return registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId, interruptConfig);
  }

  @Override
  public InterruptDTO registerInterrupt(PlanExecutionInterruptType executionInterruptType, String planExecutionId,
      String nodeExecutionId, InterruptConfig interruptConfig) {
    InterruptPackage interruptPackage = InterruptPackage.builder()
                                            .interruptType(executionInterruptType.getExecutionInterruptType())
                                            .planExecutionId(planExecutionId)
                                            .nodeExecutionId(nodeExecutionId)
                                            .interruptConfig(interruptConfig)
                                            .metadata(getMetadata(executionInterruptType))
                                            .build();
    Interrupt interrupt = orchestrationService.registerInterrupt(interruptPackage);
    // Telemetry event
    pipelineTelemetryHelper.sendInterruptTelemetryEvent(interruptPackage);
    return InterruptDTO.builder()
        .id(interrupt.getUuid())
        .planExecutionId(interrupt.getPlanExecutionId())
        .type(executionInterruptType)
        .build();
  }

  private Map<String, String> getMetadata(PlanExecutionInterruptType planExecutionInterruptType) {
    if (planExecutionInterruptType == PlanExecutionInterruptType.STAGEROLLBACK
        || planExecutionInterruptType == PlanExecutionInterruptType.STEPGROUPROLLBACK
        || planExecutionInterruptType == PlanExecutionInterruptType.PIPELINEROLLBACK) {
      return Collections.singletonMap("ROLLBACK", planExecutionInterruptType.getDisplayName());
    }
    return Collections.emptyMap();
  }

  @Override
  public long getCountOfExecutions(Criteria criteria) {
    return pmsExecutionSummaryRespository.getCountOfExecutionSummary(criteria);
  }

  @Override
  public ExecutionDataResponseDTO getExecutionData(String accountIdentifier, String planExecutionId) {
    Optional<PlanExecutionMetadata> planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, planExecutionId);

    if (!planExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(
          String.format("Execution with id [%s] is not present or deleted", planExecutionId));
    }
    String executionYaml = planExecutionMetadata.get().getYaml();

    return ExecutionDataResponseDTO.builder().executionYaml(executionYaml).executionId(planExecutionId).build();
  }

  public ExecutionMetaDataResponseDetailsDTO getExecutionDataDetails(String planExecutionId, String accountId) {
    Optional<PlanExecutionMetadata> planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId);

    if (!planExecutionMetadata.isPresent()) {
      throw new ResourceNotFoundException(
          String.format("Execution with id [%s] is not present or deleted", planExecutionId));
    }
    PlanExecutionMetadata metadata = planExecutionMetadata.get();
    boolean readSwitchEnabled =
        pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional =
          planExecutionService.getWithFieldsIncludedOptional(planExecutionId, Set.of(PlanExecutionKeys.triggerPayload));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    TriggerPayload triggerPayload =
        PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(metadata, planExecution);
    return ExecutionMetaDataResponseDetailsDTO.builder()
        .executionYaml(metadata.getYaml())
        .planExecutionId(planExecutionId)
        .inputYaml(metadata.getInputSetYaml())
        .triggerPayload(triggerPayload)
        .build();
  }

  @Override
  public String mergeRuntimeInputIntoPipelineForRerun(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, ScopeInfo scopeInfo) {
    PipelineEntity pipelineEntity = validateAndMergeHelper.getPipelineEntity(
        scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, false);
    String pipelineYaml = pipelineEntity.getYaml();
    String pipelineTemplate = isEmpty(stageIdentifiers)
        ? createTemplateFromPipeline(pipelineYaml)
        : createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
    if (isEmpty(pipelineTemplate)) {
      return "";
    }
    String mergedRuntimeInputYaml = getInputSetYamlForRerun(accountId, planExecutionId, false);
    if (isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineTemplate, mergedRuntimeInputYaml, false);
    }
    return mergeInputSetIntoPipelineForGivenStages(
        pipelineTemplate, mergedRuntimeInputYaml, false, stageIdentifiers, pipelineEntity.getHarnessVersion());
  }

  @Override
  public String mergeRuntimeInputIntoPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String planExecutionId, boolean resolveExpressions, ResolveInputYamlType resolveExpressionsType) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        getPipelineExecutionSummaryEntity(accountId, planExecutionId);
    String pipelineTemplate = pipelineExecutionSummaryEntity.getPipelineTemplate();
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineExecutionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);
    String inputSetYaml = resolveExpressionsInYaml(
        pipelineExecutionSummaryEntity, resolveExpressions, planExecutionId, resolveExpressionsType);
    if (isEmpty(pipelineTemplate)) {
      return "";
    }

    return InputSetMergeHelper.mergeInputSetIntoPipeline(
        pipelineTemplate, inputSetYaml == null ? "" : inputSetYaml, false);
  }

  @Override
  public String getPipelineIdentifier(String accountIdentifier, String planExecutionId) {
    return pmsExecutionSummaryService
        .getPipelineExecutionSummaryWithProjections(
            accountIdentifier, planExecutionId, Set.of(PlanExecutionSummaryKeys.pipelineIdentifier))
        .getPipelineIdentifier();
  }

  @Override
  public Page<GovernanceMetadata> getListOfEvaluatedPolicy(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String planExecutionId, int pageSize, int pageNumber) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
        accountIdentifier, planExecutionId, Set.of(PlanExecutionMetadataKeys.evaluatedPolicyIds));
    if (isEmpty(planExecutionMetadata.getEvaluatedPolicyIds())) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNumber, pageSize), 0);
    }
    EvaluationDetailsResponse response = opaServiceClientHelper.listOpaPolicyEvaluationsWithRetry(
        accountIdentifier, pageSize, pageNumber, planExecutionMetadata.getEvaluatedPolicyIds());
    List<GovernanceMetadata> governanceMetadataList =
        response.getEvaluations()
            .stream()
            .map(GovernanceServiceHelper::mapResponseToMetadata)
            .map(metadata -> GovernanceServiceHelper.updateOrgIdForProjectLevelPolicies(metadata, orgIdentifier))
            .toList();
    return new PageImpl<>(governanceMetadataList, PageRequest.of(response.getPageIndex(), response.getPageSize()),
        response.getTotalItems());
  }

  private String resolveExpressionsInYaml(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity,
      boolean resolveExpressions, String planExecutionId, ResolveInputYamlType resolveExpressionsType) {
    return pipelineExecutionSummaryEntity.getResolvedUserInputSetYaml();
  }

  //  Fetches inputSet details (ID and name) for a given execution summary.
  private List<InputSetDetailsDTO> getInputSetDetails(
      String accountId, String orgId, String projectId, PipelineExecutionSummaryEntity executionSummaryEntity) {
    List<String> inputSetIds = executionSummaryEntity.getInputSetIdentifiers();

    if (isEmpty(inputSetIds)) {
      return Collections.emptyList();
    }
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);

    BulkInputSetsResponseDTO response =
        pmsInputSetService.getBulkInputSets(scopeInfo, executionSummaryEntity.getPipelineIdentifier(),
            BulkInputSetsRequestDTO.builder().inputSetIdentifiers(inputSetIds).build());

    Map<String, String> idToNameMap =
        Optional.ofNullable(response)
            .map(BulkInputSetsResponseDTO::getInputSets)
            .filter(CollectionUtils::isNotEmpty)
            .map(inputSets
                -> inputSets.stream().collect(Collectors.toMap(
                    InputSetSummaryResponseDTOPMS::getIdentifier, InputSetSummaryResponseDTOPMS::getName)))
            .orElse(Collections.emptyMap());

    return inputSetIds.stream()
        .map(id -> InputSetDetailsDTO.builder().identifier(id).name(idToNameMap.get(id)).build())
        .toList();
  }

  private void checkIfInputInterruptIsApplicableForManualIntervention(
      PlanExecutionInterruptType executionInterruptType, NodeExecution nodeExecution) {
    List<RepairActionCode> manualInterventionAvailableActions =
        NodeExecutionContextUtils.getManualInterventionAvailableActions(nodeExecution);
    if (isEmpty(manualInterventionAvailableActions)) {
      return;
    }
    if (!manualInterventionAvailableActions.contains(
            PlanExecutionInterruptTypeMapper.toRepairActionCode(executionInterruptType))) {
      throw new InvalidRequestException(
          String.format("Execution interrupt type [%s] is not part of available manual intervention actions [%s]",
              executionInterruptType,
              manualInterventionAvailableActions.stream().map(String::valueOf).collect(Collectors.joining(", "))));
    }
  }
}
