/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.LogStreamingLimits;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesConfigResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateResponseDTO;
import io.harness.pms.accountoverrides.LogStreamingLimitsDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.dataretention.mappers.AccountOverridesApiMapper;
import io.harness.pms.pipeline.BlockExecutionResponseDTO;
import io.harness.pms.pipeline.FeatureFlagCacheClearRequest;
import io.harness.pms.pipeline.ForceAbortExecutionsRequestDTO;
import io.harness.pms.pipeline.ForceAbortExecutionsResponseDTO;
import io.harness.pms.pipeline.PipelineAdminResource;
import io.harness.pms.pipeline.PlanConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.StepConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.service.PipelineAdminResourceService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.UserHelperService;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineAdminResourceImpl implements PipelineAdminResource {
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";
  private final PipelineAdminResourceService pipelineAdminResourceService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final UserHelperService userHelperService;

  @Override
  public ResponseDTO<BlockExecutionResponseDTO> blockExecutionPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    checkAccessPermissionsForAccountOverrides();
    return ResponseDTO.newResponse(pipelineAdminResourceService.blockPipelineExecution(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier));
  }

  @Override
  public ResponseDTO<BlockExecutionResponseDTO> unblockExecutionPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    checkAccessPermissionsForAccountOverrides();
    return ResponseDTO.newResponse(pipelineAdminResourceService.unblockPipelineExecution(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier));
  }

  @Override
  public ResponseDTO<AccountOverridesConfigResponseDTO> getPipelineDataRetentionConfig(String accountIdentifier) {
    DataRetentionEntity entity = pipelineAdminResourceService.getPipelineDataRetentionConfig(accountIdentifier);
    return ResponseDTO.newResponse(AccountOverridesConfigResponseDTO.builder()
                                       .accountIdentifier(entity.getAccountIdentifier())
                                       .retentionPeriodInMonths(entity.getRetentionPeriodInMonths())
                                       .maxConcurrentExecutions(entity.getMaxConcurrentExecutions())
                                       .maxInputParameterSize(entity.getMaxInputParameterSize())
                                       .maxOutcomeResponseSize(entity.getMaxOutcomeResponseSize())
                                       .maxQueuedExecutionLimit(entity.getMaxQueuedExecutionLimit())
                                       .maxLeafStepConcurrency(entity.getMaxLeafStepConcurrency())
                                       .maxTriggerCreationLimit(entity.getMaxTriggerCreationLimit())
                                       .maxFileSize(entity.getMaxFileSize())
                                       .maxPipelineCreationLimit(entity.getMaxPipelineCreationLimit())
                                       .logStreamingLimits(this.getLogStreamingLimits(entity.getLogStreamingLimits()))
                                       .maxCustomWebhookPayloadSize(entity.getMaxCustomWebhookPayloadSize())
                                       .build());
  }

  @Override
  public ResponseDTO<AccountOverridesCreateResponseDTO> createAccountOverrides(
      String accountIdentifier, AccountOverridesCreateRequestDTO createRequest) {
    checkAccessPermissionsForAccountOverrides();
    AccountOverridesConfigDTO configDTO = pipelineAdminResourceService.createAccountOverrides(
        AccountOverridesApiMapper.toDTO(accountIdentifier, createRequest));
    return ResponseDTO.newResponse(AccountOverridesApiMapper.toCreateResponseDTO(configDTO));
  }

  @Override
  public ResponseDTO<AccountOverridesUpdateResponseDTO> updateAccountOverrides(
      String accountIdentifier, AccountOverridesUpdateRequestDTO updateRequest) {
    checkAccessPermissionsForAccountOverrides();
    AccountOverridesConfigDTO updateConfigDTO = pipelineAdminResourceService.updateAccountOverrides(
        accountIdentifier, AccountOverridesApiMapper.toDTO(accountIdentifier, updateRequest));
    return ResponseDTO.newResponse(AccountOverridesApiMapper.toUpdateResponseDTO(updateConfigDTO));
  }

  @Override
  public ResponseDTO<Void> replayNodeExecutionEvents(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String module, long startTs, long endTs) {
    pipelineAdminResourceService.replayNodeExecutions(
        accountIdentifier, orgIdentifier, projectIdentifier, module, startTs, endTs);
    return ResponseDTO.newResponse();
  }

  @Override
  public ResponseDTO<String> clearFeatureFlagCacheForAccount(String accountId, FeatureFlagCacheClearRequest request) {
    if (EmptyPredicate.isEmpty(request.getAccountIdentifier())) {
      throw new InvalidRequestException("Account ID cannot be null or empty");
    }
    checkUserAuthorization(
        String.format("User : %s not allowed to clear pipeline service feature flag cache for account %s",
            USER_ID_PLACEHOLDER, request.getAccountIdentifier()));
    pmsFeatureFlagHelper.clearCacheForAccount(request.getAccountIdentifier());
    return ResponseDTO.newResponse(
        "Feature flag cache cleared successfully for account: " + request.getAccountIdentifier());
  }

  @Override
  public ResponseDTO<ForceAbortExecutionsResponseDTO> forceAbortExecutions(
      String accountIdentifier, ForceAbortExecutionsRequestDTO request) {
    checkAccessPermissionsForAccountOverrides();
    return ResponseDTO.newResponse(pipelineAdminResourceService.forceAbortPlanExecutions(request));
  }

  @Override
  public ResponseDTO<Void> recomputeStepConcurrencyCounters() {
    checkUserAuthorization(
        String.format("User : %s not allowed to recompute step-concurrency counters", USER_ID_PLACEHOLDER));
    pipelineAdminResourceService.recomputeStepConcurrencyCounters();
    return ResponseDTO.newResponse();
  }

  @Override
  public ResponseDTO<StepConcurrencyCounterResponseDTO> getStepConcurrencyCounter(
      String scope, String accountIdentifier) {
    checkUserAuthorization(
        String.format("User : %s not allowed to read step-concurrency counters", USER_ID_PLACEHOLDER));
    return ResponseDTO.newResponse(pipelineAdminResourceService.getStepConcurrencyCounter(scope, accountIdentifier));
  }

  @Override
  public ResponseDTO<Void> recomputePlanConcurrencyCounters() {
    checkUserAuthorization(
        String.format("User : %s not allowed to recompute plan-concurrency counters", USER_ID_PLACEHOLDER));
    pipelineAdminResourceService.recomputePlanConcurrencyCounters();
    return ResponseDTO.newResponse();
  }

  @Override
  public ResponseDTO<PlanConcurrencyCounterResponseDTO> getPlanConcurrencyCounters(String accountIdentifier) {
    checkUserAuthorization(
        String.format("User : %s not allowed to read plan-concurrency counters", USER_ID_PLACEHOLDER));
    return ResponseDTO.newResponse(pipelineAdminResourceService.getPlanConcurrencyCounters(accountIdentifier));
  }

  private void checkUserAuthorization(String errorMessageIfAuthorizationFailed) {
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    if (!userHelperService.isHarnessSupportUser(userId)) {
      log.error(errorMessageIfAuthorizationFailed.replace(USER_ID_PLACEHOLDER, userId));
      throw new AccessDeniedException("Not Authorized", WingsException.USER);
    }
  }

  private void checkAccessPermissionsForAccountOverrides() {
    final Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null || principal.getType() != PrincipalType.SERVICE) {
      // This API will only be called by CG manager(using admin portal) and not directly by any customer
      // Due to which we are adding a SERVICE principal check
      throw new AccessDeniedException("[PIPELINE ADMIN]: The API is called using an external user!", USER);
    }
  }

  private LogStreamingLimitsDTO getLogStreamingLimits(LogStreamingLimits logStreamingLimits) {
    if (logStreamingLimits == null) {
      return null;
    }
    return LogStreamingLimitsDTO.builder()
        .maxLogLines(logStreamingLimits.getMaxLogLines())
        .maxLogLineLength(logStreamingLimits.getMaxLogLineLength())
        .streamExpirationSeconds(logStreamingLimits.getStreamExpirationSeconds())
        .maxLogSizeBytes(logStreamingLimits.getMaxLogSizeBytes())
        .maxWriteLogLinesPerMinute(logStreamingLimits.getMaxWriteLogLinesPerMinute())
        .build();
  }
}
