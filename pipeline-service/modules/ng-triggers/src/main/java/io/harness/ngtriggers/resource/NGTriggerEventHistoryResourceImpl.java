/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryBaseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventsApiResponse;
import io.harness.ngtriggers.beans.dto.NGTriggerEventsDTOResponse;
import io.harness.ngtriggers.beans.dto.SearchParams;
import io.harness.ngtriggers.beans.dto.TriggerFilters;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.mapper.NGTriggerEventHistoryBaseMapper;
import io.harness.ngtriggers.mapper.NGTriggerEventHistoryMapper;
import io.harness.ngtriggers.mapper.NGTriggerEventsMapper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Singleton
@Slf4j
@ScopeInfoResolutionApi
public class NGTriggerEventHistoryResourceImpl implements NGTriggerEventHistoryResource {
  private final NGTriggerService ngTriggerService;
  private final NGTriggerEventsService ngTriggerEventsService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final AccessControlClient accessControlClient;

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> listTriggerEventHistory(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String artifactType, String searchTerm,
      int page, int size, List<String> sort, ScopeInfo scopeInfo) {
    // Log introduced to monitor large page size requests
    if (size > 100) {
      log.info(
          String.format("Large page size requested for artifact trigger history: %d. Account: %s, Org: %s, Project: %s",
              size, accountIdentifier, orgIdentifier, projectIdentifier));
    }
    if (EmptyPredicate.isNotEmpty(targetIdentifier)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);
    }
    boolean isParentIdQueryingEnabled = true;
    Criteria criteria = ngTriggerEventsService.formTriggerEventCriteria(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, artifactType, scopeInfo, isParentIdQueryingEnabled);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);

    Page<NGTriggerEventHistoryDTO> ngTriggerEventHistoryDTOS = eventHistoryList.map(eventHistory
        -> NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(eventHistory, scopeInfo, isParentIdQueryingEnabled));

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<NGTriggerEventsApiResponse>> getTriggerEvents(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String targetIdentifier, String triggerIdentifier, List<String> status,
      NGTriggerType triggerType, int page, int size, ScopeInfo scopeInfo) {
    // Log introduced to monitor large page size requests
    if (size > 100) {
      log.info(String.format("Large page size requested for trigger events: %d. Account: %s, Org: %s, Project: %s",
          size, accountIdentifier, orgIdentifier, projectIdentifier));
    }
    Scope scope = Scope.of(scopeInfo);
    TriggerFilters triggerFilters = TriggerFilters.builder()
                                        .triggerIdentifier(triggerIdentifier)
                                        .statusList(status)
                                        .ngTriggerType(triggerType)
                                        .build();
    SearchParams searchParams = SearchParams.builder().page(page).size(size).build();

    Page<NGTriggerEventsDTOResponse> ngTriggerEventsDTOResponse =
        ngTriggerEventsService.getTriggerEvents(scope, targetIdentifier, triggerFilters, searchParams, true);
    return ResponseDTO.newResponse(ngTriggerEventsDTOResponse.map(NGTriggerEventsMapper::toNGTriggerApiResponse));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> getTriggerEventHistory(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String targetIdentifier, String triggerIdentifier, String searchTerm, int page, int size,
      List<String> sort, boolean shouldSendTriggerPayload, ScopeInfo scopeInfo) {
    // Log introduced to monitor large page size requests
    if (size > 100) {
      log.info(
          String.format("Large page size requested for trigger event history: %d. Account: %s, Org: %s, Project: %s",
              size, accountIdentifier, orgIdentifier, projectIdentifier));
    }
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    Criteria criteria =
        ngTriggerEventsService.formTriggerEventCriteria(accountIdentifier, orgIdentifier, projectIdentifier,
            targetIdentifier, triggerIdentifier, searchTerm, new ArrayList<>(), scopeInfo, isParentIdQueryingEnabled);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);

    Page<NGTriggerEventHistoryDTO> ngTriggerEventHistoryDTOS = eventHistoryList.map(eventHistory
        -> NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(
            eventHistory, ngTriggerEntity.get(), shouldSendTriggerPayload, scopeInfo, isParentIdQueryingEnabled));

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryBaseDTO>> getTriggerHistoryEventCorrelation(
      String accountIdentifier, String eventCorrelationId, int page, int size, List<String> sort) {
    // Log introduced to monitor large page size requests
    if (size > 100) {
      log.info(String.format(
          "Large page size requested for trigger history correlation: %d. Account: %s", size, accountIdentifier));
    }
    Criteria criteria =
        ngTriggerEventsService.formEventCriteria(accountIdentifier, eventCorrelationId, new ArrayList<>());
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);

    Page<NGTriggerEventHistoryBaseDTO> ngTriggerEventHistoryDTOS =
        eventHistoryList.map(eventHistory -> NGTriggerEventHistoryBaseMapper.toEventHistory(eventHistory));

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> getTriggerHistoryEventCorrelationV2(
      String accountIdentifier, String eventCorrelationId, int page, int size, List<String> sort) {
    // Log introduced to monitor large page size requests
    if (size > 100) {
      log.info(String.format(
          "Large page size requested for trigger history event correlation: %d. Account: %s", size, accountIdentifier));
    }
    Criteria criteria =
        ngTriggerEventsService.formEventCriteria(accountIdentifier, eventCorrelationId, new ArrayList<>());
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TriggerEventHistory> eventHistoryList = ngTriggerEventsService.getEventHistory(criteria, pageRequest);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolutionHelper.getScopeInfos(accountIdentifier,
        eventHistoryList.getContent()
            .stream()
            .map(TriggerEventHistory::getParentUniqueId)
            .distinct()
            .collect(Collectors.toList()));
    Page<NGTriggerEventHistoryDTO> ngTriggerEventHistoryDTOS = eventHistoryList.map(eventHistory -> {
      ScopeInfo scopeInfo = scopeInfoMap.getOrDefault(eventHistory.getParentUniqueId(), Optional.empty()).orElse(null);
      return NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(eventHistory, scopeInfo, true);
    });

    return ResponseDTO.newResponse(ngTriggerEventHistoryDTOS);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PollingInfoForTriggers> getPolledResponseForTrigger(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String targetIdentifier, String triggerIdentifier, ScopeInfo scopeInfo) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, true);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }
    if (ngTriggerEntity.get().getType() == NGTriggerType.ARTIFACT
        || ngTriggerEntity.get().getType() == NGTriggerType.MANIFEST) {
      String pollingDocId = ngTriggerEntity.get().getMetadata().getBuildMetadata().getPollingConfig().getPollingDocId();
      return ngTriggerEventsService.getPollingInfo(accountIdentifier, pollingDocId);
    } else {
      throw new InvalidRequestException(
          String.format("Trigger %s is not of Artifact or Manifest type", triggerIdentifier));
    }
  }
}
