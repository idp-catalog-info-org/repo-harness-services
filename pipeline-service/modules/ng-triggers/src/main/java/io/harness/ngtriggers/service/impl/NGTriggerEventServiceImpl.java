/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.PerpetualTaskInfoResponse;
import io.harness.delegate.TaskId;
import io.harness.dto.PerpetualTaskInfoForTriggers;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventsDTOResponse;
import io.harness.ngtriggers.beans.dto.SearchParams;
import io.harness.ngtriggers.beans.dto.TriggerFilters;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventStatusHelper;
import io.harness.ngtriggers.mapper.NGTriggerEventsMapper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.polling.client.PollingResourceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGTriggerEventServiceImpl implements NGTriggerEventsService {
  private TriggerEventHistoryRepository triggerEventHistoryRepository;
  private PollingResourceClient pollingResourceClient;
  private DelegateServiceGrpcClient delegateServiceGrpcClient;
  private NGTriggerEventsService ngTriggerEventsService;

  @Override
  public Criteria formEventCriteria(String accountId, String eventCorrelationId, List<ExecutionStatus> statusList) {
    Criteria criteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(accountId)) {
      criteria.and(TriggerEventHistoryKeys.accountId).is(accountId);
    }

    if (EmptyPredicate.isNotEmpty(eventCorrelationId)) {
      criteria.and(TriggerEventHistoryKeys.eventCorrelationId).is(eventCorrelationId);
    }

    if (EmptyPredicate.isNotEmpty(statusList)) {
      criteria.and(TriggerEventHistoryKeys.finalStatus).in(statusList);
    }

    Criteria searchCriteria = new Criteria();
    criteria.andOperator(searchCriteria);
    return criteria;
  }

  @Override
  public ResponseDTO<PollingInfoForTriggers> getPollingInfo(String accountId, String pollingDocId) {
    try {
      PollingInfoForTriggers pollingInfoForTriggers =
          NGRestUtils.getResponse(pollingResourceClient.getPollingInfoForTriggers(accountId, pollingDocId));
      String perpetualTaskId = pollingInfoForTriggers.getPerpetualTaskId();

      PerpetualTaskInfoResponse response =
          delegateServiceGrpcClient.getPerpetualTask(TaskId.newBuilder().setId(perpetualTaskId).build());

      PerpetualTaskInfoForTriggers perpetualTaskInfoForTriggers = PerpetualTaskInfoForTriggers.builder()
                                                                      .delegateId(response.getDelegateId())
                                                                      .createdAt(response.getCreatedAt())
                                                                      .state(response.getState())
                                                                      .unassignedReason(response.getUnassignedReason())
                                                                      .taskDescription(response.getTaskDescription())
                                                                      .delegateHostName(response.getDelegateHostName())
                                                                      .build();

      pollingInfoForTriggers.setPerpetualTaskInfoForTriggers(perpetualTaskInfoForTriggers);
      return ResponseDTO.newResponse(pollingInfoForTriggers);
    } catch (Exception exception) {
      String msg = "Failed to get Polling Response" + exception;
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  @Override
  public Criteria formTriggerEventCriteria(String accountId, String orgId, String projectId, String targetIdentifier,
      String artifactType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(TriggerEventHistoryKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      if (EmptyPredicate.isNotEmpty(accountId)) {
        criteria.and(TriggerEventHistoryKeys.accountId).is(accountId);
      }
      if (EmptyPredicate.isNotEmpty(orgId)) {
        criteria.and(TriggerEventHistoryKeys.orgIdentifier).is(orgId);
      }
      if (EmptyPredicate.isNotEmpty(projectId)) {
        criteria.and(TriggerEventHistoryKeys.projectIdentifier).is(projectId);
      }
    }
    if (EmptyPredicate.isNotEmpty(targetIdentifier)) {
      criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(targetIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(artifactType)) {
      criteria.and(TriggerEventHistoryKeys.buildSourceType).is(artifactType);
    }

    return criteria;
  }

  @Override
  public Criteria formTriggerEventCriteria(String accountId, String orgId, String projectId, String targetIdentifier,
      String identifier, String searchTerm, List<ExecutionStatus> statusList, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(TriggerEventHistoryKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      if (EmptyPredicate.isNotEmpty(accountId)) {
        criteria.and(TriggerEventHistoryKeys.accountId).is(accountId);
      }
      if (EmptyPredicate.isNotEmpty(orgId)) {
        criteria.and(TriggerEventHistoryKeys.orgIdentifier).is(orgId);
      }
      if (EmptyPredicate.isNotEmpty(projectId)) {
        criteria.and(TriggerEventHistoryKeys.projectIdentifier).is(projectId);
      }
    }
    if (EmptyPredicate.isNotEmpty(targetIdentifier)) {
      criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(targetIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(identifier)) {
      criteria.and(TriggerEventHistoryKeys.triggerIdentifier).is(identifier);
    }
    if (EmptyPredicate.isNotEmpty(statusList)) {
      criteria.and(TriggerEventHistoryKeys.finalStatus).in(statusList);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      try {
        searchCriteria.orOperator(where(TriggerEventHistoryKeys.triggerIdentifier)
                                      .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }
    criteria.andOperator(searchCriteria);
    return criteria;
  }

  @Override
  public Page<TriggerEventHistory> getEventHistory(Criteria criteria, Pageable pageable) {
    return triggerEventHistoryRepository.findAll(criteria, pageable);
  }

  @Override
  public void deleteAllForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String parentUniqueId) {
    Criteria criteria = new Criteria();
    criteria = criteria.and(TriggerEventHistoryKeys.parentUniqueId).is(parentUniqueId);

    criteria = criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(pipelineIdentifier);
    triggerEventHistoryRepository.deleteBatch(criteria);
  }

  public void deleteTriggerEventHistory(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String triggerIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(TriggerEventHistoryKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      criteria.and(TriggerEventHistoryKeys.accountId).is(accountId);
      criteria.and(TriggerEventHistoryKeys.orgIdentifier).is(orgIdentifier);
      criteria.and(TriggerEventHistoryKeys.projectIdentifier).is(projectIdentifier);
    }
    criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(pipelineIdentifier);
    criteria.and(TriggerEventHistoryKeys.triggerIdentifier).is(triggerIdentifier);
    DeleteResult deleteResult = triggerEventHistoryRepository.deleteTriggerEventHistoryForTriggerIdentifier(criteria);
    if (!deleteResult.wasAcknowledged()) {
      log.error(String.format("Unable to delete event history for trigger [%s]", triggerIdentifier));
      return;
    }
    log.info("NGTrigger {} event history delete successful", triggerIdentifier);
  }

  @Override
  public Page<NGTriggerEventsDTOResponse> getTriggerEvents(Scope scope, String targetIdentifier,
      TriggerFilters triggerFilters, SearchParams searchParams, boolean isParentIdQueryingEnabled) {
    Set<TriggerEventResponse.FinalStatus> finalStatus =
        TriggerEventStatusHelper.toListOfStatus(triggerFilters.getStatusList());

    Criteria criteria =
        ngTriggerEventsService.formTriggerEventsCriteria(scope, targetIdentifier, triggerFilters.getTriggerIdentifier(),
            finalStatus, triggerFilters.getNgTriggerType(), isParentIdQueryingEnabled);
    Pageable pageRequest = PageRequest.of(searchParams.getPage(), searchParams.getSize(),
        Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);
    return eventHistoryList.map(triggerEventHistory
        -> NGTriggerEventsMapper.toTriggerEventsDto(triggerEventHistory, scope, isParentIdQueryingEnabled));
  }

  @Override
  public Criteria formTriggerEventsCriteria(Scope scope, String targetIdentifier, String triggerIdentifier,
      Set<TriggerEventResponse.FinalStatus> finalStatus, NGTriggerType ngTriggerType,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(TriggerEventHistoryKeys.parentUniqueId).is(scope.getParentUniqueId());
    } else {
      criteria.and(TriggerEventHistoryKeys.accountId).is(scope.getAccountIdentifier());
      criteria.and(TriggerEventHistoryKeys.orgIdentifier).is(scope.getOrgIdentifier());
      criteria.and(TriggerEventHistoryKeys.projectIdentifier).is(scope.getProjectIdentifier());
    }
    criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(targetIdentifier);

    if (EmptyPredicate.isNotEmpty(triggerIdentifier)) {
      criteria.and(TriggerEventHistoryKeys.triggerIdentifier).is(triggerIdentifier);
    }

    if (EmptyPredicate.isNotEmpty(finalStatus)) {
      criteria.and(TriggerEventHistoryKeys.finalStatus)
          .in(finalStatus.stream().map(TriggerEventResponse.FinalStatus::name).collect(Collectors.toList()));
    }

    if (ngTriggerType != null && EmptyPredicate.isNotEmpty(String.valueOf(ngTriggerType))) {
      criteria.and(TriggerEventHistoryKeys.ngTriggerType).in(ngTriggerType);
    }

    return criteria;
  }
}
