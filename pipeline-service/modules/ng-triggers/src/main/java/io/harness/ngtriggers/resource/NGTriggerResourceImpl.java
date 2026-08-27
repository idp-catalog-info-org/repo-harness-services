/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerYamlRequestDTO;
import io.harness.ngtriggers.beans.dto.NGTriggersFilterPropertiesDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.remote.client.NGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.utils.CryptoUtils;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.http.Body;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
@ScopeInfoResolutionApi
public class NGTriggerResourceImpl implements NGTriggerResource {
  private final NGTriggerService ngTriggerService;

  private final NGTriggerEventsService ngTriggerEventsService;

  private final NGTriggerEventHistoryResource ngTriggerEventHistoryResource;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final NGSettingsClient settingsClient;
  private final FilterService filterService;
  private final TriggerTelemetryHelper triggerTelemetryHelper;

  @Override
  public ResponseDTO<NGTriggerResponseDTO> create(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, @NotNull String yaml, boolean ignoreError,
      boolean withServiceV2, ScopeInfo scopeInfo) {
    return ngTriggerService.createTriggerWithValidation(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, yaml, null, ignoreError, withServiceV2, scopeInfo);
  }

  @Override
  public ResponseDTO<NGTriggerResponseDTO> createV2(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, @NotNull NGTriggerYamlRequestDTO request,
      boolean ignoreError, boolean withServiceV2, ScopeInfo scopeInfo) {
    String yaml = request.getYaml();
    TriggerExecutorDTO executorInfo = request.getExecutorInfo();
    return ngTriggerService.createTriggerWithValidation(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, yaml, executorInfo, ignoreError, withServiceV2, scopeInfo);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<NGTriggerResponseDTO> get(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);

    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    NGTriggerEntity entity = ngTriggerEntity.get();
    NGTriggerResponseDTO responseDTO =
        ngTriggerElementMapper.toResponseDTO(entity, scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(entity.getVersion().toString(), responseDTO);
  }

  @Override
  public ResponseDTO<NGTriggerResponseDTO> update(String ifMatch, @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, @NotNull String yaml,
      boolean ignoreError, ScopeInfo scopeInfo) {
    return ngTriggerService.updateTriggerWithValidation(ifMatch, accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, yaml, null, ignoreError, scopeInfo);
  }

  @Override
  public ResponseDTO<NGTriggerResponseDTO> updateV2(String ifMatch,
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String targetIdentifier,
      String triggerIdentifier, @NotNull NGTriggerYamlRequestDTO request, boolean ignoreError, ScopeInfo scopeInfo) {
    String yaml = request.getYaml();
    TriggerExecutorDTO executorInfo = request.getExecutorInfo();
    return ngTriggerService.updateTriggerWithValidation(ifMatch, accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, yaml, executorInfo, ignoreError, scopeInfo);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<Boolean> updateTriggerStatus(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, @NotNull boolean status,
      ScopeInfo scopeInfo) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, true);
    return ResponseDTO.newResponse(
        ngTriggerService.updateTriggerStatus(ngTriggerEntity.get(), status, scopeInfo, true));
  }

  @Override
  @InternalApi
  public ResponseDTO<Boolean> updateTriggerPollingStatus(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull PollingTriggerStatusUpdateDTO statusUpdate) {
    return ResponseDTO.newResponse(ngTriggerService.updateTriggerPollingStatus(accountIdentifier, statusUpdate));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<Boolean> delete(String ifMatch, @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    boolean triggerDeleted =
        ngTriggerService.delete(accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier,
            triggerIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, scopeInfo, isParentIdQueryingEnabled);
    if (triggerDeleted) {
      ngTriggerEventsService.deleteTriggerEventHistory(accountIdentifier, orgIdentifier, projectIdentifier,
          targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);
    }
    return ResponseDTO.newResponse(triggerDeleted);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PageResponse<NGTriggerDetailsResponseDTO>> getListForTarget(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String targetIdentifier,
      String filterQuery, int page, int size, List<String> sort, String searchTerm,
      NGTriggersFilterPropertiesDTO filterProperties, ScopeInfo scopeInfo) {
    FilterDTO triggerFilterDTO = null;
    if (filterQuery != null) {
      triggerFilterDTO =
          filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterQuery, FilterType.TRIGGER);
    }
    boolean isParentIdQueryingEnabled = true;
    Criteria criteria = TriggerFilterHelper.createCriteriaForGetList(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, null, searchTerm, filterQuery, filterProperties, triggerFilterDTO,
        scopeInfo, isParentIdQueryingEnabled);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    boolean mandatoryAuth =
        getMandatoryAuthForCustomWebhookTriggers(accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(getNGPageResponse(ngTriggerService.list(criteria, pageRequest).map(triggerEntity -> {
      NGTriggerDetailsResponseDTO responseDTO = ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(
          triggerEntity, true, false, false, mandatoryAuth, scopeInfo, isParentIdQueryingEnabled);
      return responseDTO;
    })));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<NGTriggerDetailsResponseDTO> getTriggerDetails(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String triggerIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format(
          "Trigger %s does not exist in project %s in org %s", triggerIdentifier, projectIdentifier, orgIdentifier));
    }
    NGTriggerEntity entity = ngTriggerEntity.get();
    NGTriggerDetailsResponseDTO details = ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(entity, true, true,
        false, getMandatoryAuthForCustomWebhookTriggers(accountIdentifier, orgIdentifier, projectIdentifier), scopeInfo,
        isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(entity.getVersion().toString(), details);
  }

  @Timed
  @ExceptionMetered
  public RestResponse<String> generateWebhookToken() {
    return new RestResponse<>(CryptoUtils.secureRandAlphaNumString(40));
  }

  @Override
  public ResponseDTO<NGTriggerCatalogDTO> getTriggerCatalog(String accountIdentifier) {
    List<TriggerCatalogItem> triggerCatalog = ngTriggerService.getTriggerCatalog(accountIdentifier);
    return ResponseDTO.newResponse(ngTriggerElementMapper.toCatalogDTO(triggerCatalog));
  }

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> getTriggerEventHistory(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String triggerIdentifier,
      String searchTerm, int page, int size, List<String> sort, ScopeInfo scopeInfo) {
    return ngTriggerEventHistoryResource.getTriggerEventHistory(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, searchTerm, page, size, sort, false, scopeInfo);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<TriggerYamlDiffDTO> getTriggerReconciliationYamlDiff(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String targetIdentifier,
      String triggerIdentifier, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }
    TriggerDetails triggerDetails = ngTriggerService.fetchTriggerEntity(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, triggerIdentifier, ngTriggerEntity.get().getYaml(),
        ngTriggerEntity.get().getWithServiceV2(), scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(
        ngTriggerService.getTriggerYamlDiff(triggerDetails, scopeInfo, isParentIdQueryingEnabled));
  }

  @Override
  @Hidden
  public ResponseDTO<NGTriggerConfigV2> getNGTriggerConfigV2() {
    return null;
  }

  @Override
  @InternalApi
  public ResponseDTO<TriggerUpdateCount> updateBranchName(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, GitMoveOperationType operationType,
      String pipelineBranchName, ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(ngTriggerService.updateBranchName(accountIdentifier, orgIdentifier,
        projectIdentifier, targetIdentifier, operationType, pipelineBranchName, scopeInfo, true));
  }

  private boolean getMandatoryAuthForCustomWebhookTriggers(
      String accountId, String orgIdentifier, String projectIdentifier) {
    return Objects.equals(NGRestUtils
                              .getResponse(settingsClient.getSetting(
                                  MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION, accountId, orgIdentifier, projectIdentifier))
                              .getValue(),
        "true");
  }

  public ResponseDTO<BulkTriggersResponseDTO> bulkToggleTriggers(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @Body BulkTriggersRequestDTO bulkTriggersRequestDTO) {
    long timeStart = System.currentTimeMillis();

    BulkTriggersResponseDTO bulkTriggersResponseDTO =
        ngTriggerService.toggleTriggersInBulk(accountIdentifier, bulkTriggersRequestDTO);

    long timeTaken = System.currentTimeMillis() - timeStart;

    try {
      triggerTelemetryHelper.sendBulkToggleTriggersApiEvent(
          accountIdentifier, bulkTriggersRequestDTO, bulkTriggersResponseDTO, timeTaken);
    } catch (Exception e) {
      log.error("Error while publishing telemetry for the Bulk Toggle Triggers API.");
    }

    return ResponseDTO.newResponse(bulkTriggersResponseDTO);
  }
}
