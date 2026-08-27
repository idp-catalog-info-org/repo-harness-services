/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.SERVICEACCOUNT;
import static io.harness.ngtriggers.Constants.ENFORCE_EXECUTOR_IDENTITY_FOR_TRIGGERS;
import static io.harness.ngtriggers.Constants.ENFORCE_EXECUTOR_IDENTITY_TRUE_VALUE;
import static io.harness.ngtriggers.Constants.EXECUTOR_TYPE_SERVICE_ACCOUNT;
import static io.harness.ngtriggers.Constants.EXECUTOR_TYPE_USER;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.user.remote.UserClient;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerExecutorResolver {
  private final UserClient userClient;
  private final ServiceAccountClient serviceAccountClient;
  private final NGSettingsClient settingsClient;
  private final AccessControlClient accessControlClient;
  private final AccessControlClient privilegedAccessControlClient;
  private final MetricService metricService;
  private static final String TRIGGER_EXECUTOR_RBAC_VALIDATION_DURATION = "trigger_executor_rbac_validation_duration";

  @Inject
  public TriggerExecutorResolver(UserClient userClient, @Named("PRIVILEGED") ServiceAccountClient serviceAccountClient,
      NGSettingsClient settingsClient, AccessControlClient accessControlClient,
      @Named("PRIVILEGED") AccessControlClient privilegedAccessControlClient, MetricService metricService) {
    this.userClient = userClient;
    this.serviceAccountClient = serviceAccountClient;
    this.settingsClient = settingsClient;
    this.accessControlClient = accessControlClient;
    this.privilegedAccessControlClient = privilegedAccessControlClient;
    this.metricService = metricService;
  }

  public boolean isEnforceExecutorEnabled(String accountId, String orgId, String projectId) {
    try {
      String value = NGRestUtils
                         .getResponse(settingsClient.getSetting(
                             ENFORCE_EXECUTOR_IDENTITY_FOR_TRIGGERS, accountId, orgId, projectId))
                         .getValue();
      return ENFORCE_EXECUTOR_IDENTITY_TRUE_VALUE.equalsIgnoreCase(value);
    } catch (Exception e) {
      log.warn("Failed to read enforce_executor_identity_for_triggers setting for account {}, defaulting to false",
          accountId, e);
      return false;
    }
  }

  public Principal resolveExecutorPrincipal(NGTriggerEntity entity) {
    if (!hasPersistedExecutor(entity.getExecutorInfo())) {
      throw new InvalidRequestException(String.format(
          "Trigger '%s' has no executor configured. Set a valid executor on the trigger.", entity.getIdentifier()));
    }

    TriggerExecutorDTO executorInfo = entity.getExecutorInfo();
    if (executorInfo.getType() == null) {
      throw new InvalidRequestException(String.format(
          "Trigger '%s' is missing executor type. Set type to USER or SERVICE_ACCOUNT.", entity.getIdentifier()));
    }

    String executorId = executorInfo.getIdentifier();
    String executorTypeStr = executorInfo.getType().name();
    String executorAccountId = EmptyPredicate.isEmpty(executorInfo.getAccountIdentifier())
        ? entity.getAccountId()
        : executorInfo.getAccountIdentifier();

    if (EXECUTOR_TYPE_USER.equals(executorTypeStr)) {
      UserInfo user =
          loadActiveUserOrThrow(executorId, executorAccountId, "Update the trigger with a valid user executor.",
              "Choose an active user as executor.", "Unable to resolve executor user '%s': %s");
      return new UserPrincipal(user.getUuid(), user.getEmail(), user.getName(), executorAccountId);
    } else if (EXECUTOR_TYPE_SERVICE_ACCOUNT.equals(executorTypeStr)) {
      // Prefer executorInfo scope; fall back to trigger scope for legacy documents missing accountIdentifier.
      boolean hasExecutorScope = !EmptyPredicate.isEmpty(executorInfo.getAccountIdentifier());
      ServiceAccountDTOInternal sa = loadServiceAccountOrThrow(executorId, executorAccountId,
          hasExecutorScope ? executorInfo.getOrgIdentifier() : entity.getOrgIdentifier(),
          hasExecutorScope ? executorInfo.getProjectIdentifier() : entity.getProjectIdentifier());
      return new ServiceAccountPrincipal(
          sa.getIdentifier(), sa.getEmail(), sa.getName(), sa.getAccountIdentifier(), sa.getUniqueIdInternal());
    }
    throw new UnexpectedException(
        String.format("Trigger '%s' has invalid executor type '%s'. Expected USER or SERVICE_ACCOUNT.",
            entity.getIdentifier(), executorTypeStr));
  }

  public void setExecutorContext(Principal executorPrincipal) {
    // Keep pipeline-service's service identity as the auth context so outbound service-to-service
    // calls (access-control, settings, ng-manager, etc.) continue to be authenticated as PIPELINE_SERVICE.
    // The executor is placed only in the source principal so downstream RBAC treats it as impersonation
    // via X-Source-Principal. Overwriting the security context with a USER/SERVICE_ACCOUNT principal
    // makes non-privileged clients mint outbound tokens as that principal, which then fails
    // access-control's checkPreconditions for any body-principal check.
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    SourcePrincipalContextBuilder.setSourcePrincipal(executorPrincipal);
  }

  /**
   * Validates executor is present before execution when enforce setting is enabled.
   */
  public void validateExecutorRequiredForExecution(NGTriggerEntity entity, boolean executorFeatureEnabled) {
    if (!executorFeatureEnabled) {
      return;
    }
    if (!isEnforceExecutorEnabled(entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier())) {
      return;
    }
    if (!hasPersistedExecutor(entity.getExecutorInfo())) {
      throw new InvalidRequestException(String.format(
          "Trigger '%s' requires an executor before it can run. Configure executor identity on the trigger.",
          entity.getIdentifier()));
    }
  }

  /**
   * Re-validates executor pipeline permissions at execution time. Permissions are checked on create/update but may
   * be revoked later; trigger execution must fail if the executor no longer has access to the target pipeline.
   */
  public void validateExecutorPermissionsForExecution(NGTriggerEntity entity) {
    if (!hasPersistedExecutor(entity.getExecutorInfo())) {
      return;
    }

    TriggerExecutorDTO executorInfo = entity.getExecutorInfo();
    if (executorInfo.getType() == null) {
      throw new InvalidRequestException(String.format(
          "Trigger '%s' is missing executor type. Set type to USER or SERVICE_ACCOUNT.", entity.getIdentifier()));
    }

    TriggerExecutorDTO.ExecutorType executorType = executorInfo.getType();
    io.harness.accesscontrol.principals.PrincipalType principalType;
    if (executorType == TriggerExecutorDTO.ExecutorType.USER) {
      principalType = USER;
    } else if (executorType == TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT) {
      principalType = SERVICE_ACCOUNT;
    } else {
      throw new InvalidRequestException(
          String.format("Trigger '%s' has invalid executor type '%s'. Expected USER or SERVICE_ACCOUNT.",
              entity.getIdentifier(), executorType));
    }
    String principalUniqueId;
    if (!EmptyPredicate.isEmpty(executorInfo.getUniqueId())) {
      principalUniqueId = executorInfo.getUniqueId();
    } else if (executorType == TriggerExecutorDTO.ExecutorType.USER) {
      principalUniqueId = executorInfo.getIdentifier();
    } else {
      // SERVICE_ACCOUNT without stored uniqueId (legacy triggers). ACL matches grants on uniqueIdInternal,
      // so load the SA instead of falling back to the identifier. Use executorInfo scope when present;
      // otherwise fall back to the trigger's scope.
      boolean hasExecutorScope = !EmptyPredicate.isEmpty(executorInfo.getAccountIdentifier());
      principalUniqueId = loadServiceAccountOrThrow(executorInfo.getIdentifier(),
          hasExecutorScope ? executorInfo.getAccountIdentifier() : entity.getAccountId(),
          hasExecutorScope ? executorInfo.getOrgIdentifier() : entity.getOrgIdentifier(),
          hasExecutorScope ? executorInfo.getProjectIdentifier() : entity.getProjectIdentifier())
                              .getUniqueIdInternal();
    }
    long startMs = System.currentTimeMillis();
    try {
      validateExecutorHasPipelinePermissions(executorInfo.getIdentifier(), principalType, entity.getTargetIdentifier(),
          entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), principalUniqueId);
    } finally {
      recordRbacValidationDuration(entity.getAccountId(), executorType.name(), System.currentTimeMillis() - startMs);
    }
  }

  /**
   * Populates executor info based on feature flag and enforcement setting:
   * - Setting ON: executor required (uuid + type); create fails if omitted
   * - Setting OFF: executor optional; if provided, validates and persists; if omitted on create, clears executorInfo
   * Name and email in the body are ignored; canonical values come from user/SA services.
   * USER executor: Caller can only set themselves as executor.
   * SERVICE_ACCOUNT executor: Caller must have core_serviceaccount_manageapikey permission on the SA.
   */
  public void populateExecutorOnCreateOrUpdate(NGTriggerEntity entity, TriggerExecutorDTO requestedExecutorInfo,
      String accountId, String orgId, String projectId, boolean settingEnabled) {
    String requestedExecutorId = requestedExecutorInfo == null ? null : requestedExecutorInfo.getIdentifier();
    if (EmptyPredicate.isEmpty(requestedExecutorId)) {
      requestedExecutorId = null;
    }
    String requestedExecutorType = requestedExecutorInfo == null || requestedExecutorInfo.getType() == null
        ? null
        : requestedExecutorInfo.getType().name();

    // Setting OFF + no executor provided: clear executorInfo
    if (!settingEnabled && requestedExecutorId == null) {
      entity.setExecutorInfo(null);
      return;
    }

    // Setting ON: uuid and type are mandatory
    if (settingEnabled && (requestedExecutorId == null || EmptyPredicate.isEmpty(requestedExecutorType))) {
      throw new InvalidArgumentsException(
          "executorInfo with uuid and type (USER or SERVICE_ACCOUNT) is required when executor identity "
          + "enforcement is enabled.");
    }

    // Type is always required when executor is provided
    if (EmptyPredicate.isEmpty(requestedExecutorType)) {
      throw new InvalidArgumentsException(
          "executorInfo type (USER or SERVICE_ACCOUNT) is required when an executor uuid is provided.");
    }

    Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (currentPrincipal == null) {
      throw new UnexpectedException("Unable to determine the current user. Sign in again and retry the request.");
    }

    String resolvedType = requestedExecutorType.trim();
    if (!EXECUTOR_TYPE_USER.equals(resolvedType) && !EXECUTOR_TYPE_SERVICE_ACCOUNT.equals(resolvedType)) {
      throw new InvalidArgumentsException(
          String.format("Invalid executor type '%s'. Use USER or SERVICE_ACCOUNT.", resolvedType));
    }

    if (EXECUTOR_TYPE_USER.equals(resolvedType)) {
      // USER executor: can only assign self
      if (!(currentPrincipal instanceof UserPrincipal)) {
        throw new NGAccessDeniedException(
            "Service account cannot assign a user as trigger executor. Sign in as a user or use a "
                + "SERVICE_ACCOUNT executor.",
            null, null);
      }
      if (requestedExecutorId == null || !requestedExecutorId.equals(currentPrincipal.getName())) {
        throw new NGAccessDeniedException("User can only set themselves as the trigger executor.", null, null);
      }
      UserInfo resolvedUser = loadActiveUserOrThrow(requestedExecutorId, accountId, "Provide a valid user identifier.",
          "Choose an active user.", "Unable to validate executor user '%s': %s");
      validateExecutorHasPipelinePermissions(resolvedUser.getUuid(), USER, entity.getTargetIdentifier(), accountId,
          orgId, projectId, resolvedUser.getUuid());
      entity.setExecutorInfo(buildExecutorInfoFromUser(resolvedUser));
    } else {
      validateServiceAccountScopeInRequest(requestedExecutorInfo, accountId);
      ServiceAccountDTOInternal resolvedServiceAccount =
          loadServiceAccountOrThrow(requestedExecutorId, requestedExecutorInfo.getAccountIdentifier(),
              requestedExecutorInfo.getOrgIdentifier(), requestedExecutorInfo.getProjectIdentifier());
      String saOrgId = resolvedServiceAccount.getOrgIdentifier();
      String saProjectId = resolvedServiceAccount.getProjectIdentifier();
      String saAccountId = resolvedServiceAccount.getAccountIdentifier();
      validateCallerCanManageServiceAccount(resolvedServiceAccount, saAccountId, saOrgId, saProjectId);
      if (EmptyPredicate.isEmpty(resolvedServiceAccount.getUniqueIdInternal())) {
        throw new UnexpectedException(
            String.format("Service account '%s' cannot be used as trigger executor (missing unique ID).",
                resolvedServiceAccount.getIdentifier()));
      }

      validateExecutorHasPipelinePermissions(resolvedServiceAccount.getIdentifier(), SERVICE_ACCOUNT,
          entity.getTargetIdentifier(), accountId, orgId, projectId, resolvedServiceAccount.getUniqueIdInternal());
      entity.setExecutorInfo(buildExecutorInfoFromServiceAccount(resolvedServiceAccount));
    }
  }

  private TriggerExecutorDTO resolveLegacyExecutorForUpdate(TriggerExecutorDTO requestedExecutorInfo) {
    String requestedExecutorId = requestedExecutorInfo == null ? null : requestedExecutorInfo.getIdentifier();
    if (!EmptyPredicate.isEmpty(requestedExecutorId)) {
      return requestedExecutorInfo;
    }

    Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (!(currentPrincipal instanceof UserPrincipal)) {
      return requestedExecutorInfo;
    }

    UserPrincipal currentUser = (UserPrincipal) currentPrincipal;
    log.info("Updating legacy trigger with enforce executor identity enabled and no executor provided; "
            + "defaulting executor to current user '{}'",
        currentUser.getName());
    return TriggerExecutorDTO.builder()
        .identifier(currentUser.getName())
        .type(TriggerExecutorDTO.ExecutorType.USER)
        .build();
  }

  private void validateServiceAccountScopeInRequest(TriggerExecutorDTO requestedExecutorInfo, String triggerAccountId) {
    if (requestedExecutorInfo == null || EmptyPredicate.isEmpty(requestedExecutorInfo.getAccountIdentifier())) {
      throw new InvalidArgumentsException("executorInfo accountIdentifier is required for a SERVICE_ACCOUNT executor.");
    }
    if (!triggerAccountId.equals(requestedExecutorInfo.getAccountIdentifier())) {
      throw new InvalidArgumentsException("executorInfo accountIdentifier must match the trigger's account.");
    }
  }

  private boolean hasPersistedExecutor(TriggerExecutorDTO executorInfo) {
    return executorInfo != null && !EmptyPredicate.isEmpty(executorInfo.getIdentifier());
  }

  private TriggerExecutorDTO buildExecutorInfoFromUser(UserInfo user) {
    return TriggerExecutorDTO.builder()
        .identifier(user.getUuid())
        .name(user.getName())
        .email(user.getEmail())
        .uniqueId(user.getUuid())
        .type(TriggerExecutorDTO.ExecutorType.USER)
        .build();
  }

  private TriggerExecutorDTO buildExecutorInfoFromServiceAccount(ServiceAccountDTOInternal serviceAccount) {
    return TriggerExecutorDTO.builder()
        .identifier(serviceAccount.getIdentifier())
        .name(serviceAccount.getName())
        .email(serviceAccount.getEmail())
        .accountIdentifier(serviceAccount.getAccountIdentifier())
        .orgIdentifier(serviceAccount.getOrgIdentifier())
        .projectIdentifier(serviceAccount.getProjectIdentifier())
        .uniqueId(serviceAccount.getUniqueIdInternal())
        .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
        .build();
  }

  /**
   * Applies executor on update based on enforcement setting:
   * - Setting ON: executor required; preserves existing executor when omitted; auto-defaults to caller for legacy
   *   triggers with no executor
   * - Setting OFF: executor optional; preserves existing executor when omitted; if provided validates and persists
   */
  public void handleExecutorOnUpdate(NGTriggerEntity newEntity, NGTriggerEntity existingEntity,
      TriggerExecutorDTO requestedExecutorInfo, String accountId, String orgId, String projectId,
      boolean settingEnabled) {
    String requestedExecutorId = requestedExecutorInfo == null ? null : requestedExecutorInfo.getIdentifier();
    if (EmptyPredicate.isEmpty(requestedExecutorId)) {
      requestedExecutorId = null;
    }

    if (!settingEnabled) {
      if (requestedExecutorId == null) {
        if (hasPersistedExecutor(existingEntity.getExecutorInfo())) {
          newEntity.setExecutorInfo(existingEntity.getExecutorInfo());
        } else {
          newEntity.setExecutorInfo(null);
        }
        return;
      }
      // Setting OFF but executor provided: validate and persist
      populateExecutorOnCreateOrUpdate(newEntity, requestedExecutorInfo, accountId, orgId, projectId, false);
      return;
    }

    if (requestedExecutorId == null && hasPersistedExecutor(existingEntity.getExecutorInfo())) {
      newEntity.setExecutorInfo(existingEntity.getExecutorInfo());
      return;
    }

    requestedExecutorInfo = resolveLegacyExecutorForUpdate(requestedExecutorInfo);
    populateExecutorOnCreateOrUpdate(newEntity, requestedExecutorInfo, accountId, orgId, projectId, true);
  }

  private UserInfo loadActiveUserOrThrow(
      String userId, String accountId, String notFoundHint, String disabledHint, String failureMessageFormat) {
    try {
      Optional<UserInfo> userOpt = CGRestUtils.getResponse(userClient.getUserById(userId));
      if (userOpt.isEmpty()) {
        throw new EntityNotFoundException(String.format("User '%s' was not found. %s", userId, notFoundHint));
      }
      UserInfo user = userOpt.get();
      if (user.isDisabled()) {
        throw new InvalidRequestException(
            String.format("User '%s' is disabled and cannot be used as trigger executor. %s", userId, disabledHint));
      }
      return user;
    } catch (EntityNotFoundException | InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new UnexpectedException(String.format(failureMessageFormat, userId, e.getMessage()), e);
    }
  }

  private ServiceAccountDTOInternal loadServiceAccountOrThrow(
      String serviceAccountId, String accountId, String orgId, String projectId) {
    try {
      List<ServiceAccountDTOInternal> accounts =
          NGRestUtils.getResponse(serviceAccountClient.listServiceAccountsInternal(
              accountId, orgId, projectId, Collections.singletonList(serviceAccountId)));
      if (accounts == null || accounts.isEmpty()) {
        throw new EntityNotFoundException(
            String.format("Service account '%s' was not found in account '%s', org '%s', project '%s'.",
                serviceAccountId, accountId, orgId, projectId));
      }
      return accounts.get(0);
    } catch (EntityNotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new UnexpectedException(
          String.format("Unable to validate service account '%s': %s", serviceAccountId, e.getMessage()), e);
    }
  }

  private void validateCallerCanManageServiceAccount(
      ServiceAccountDTOInternal sa, String accountId, String orgId, String projectId) {
    try {
      boolean hasManageApiKeyPermission = accessControlClient.hasAccess(ResourceScope.of(accountId, orgId, projectId),
          Resource.of(SERVICEACCOUNT, sa.getIdentifier()), MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
      if (!hasManageApiKeyPermission) {
        throw new NGAccessDeniedException(
            String.format(
                "User does not have permission to assign service account '%s' as trigger executor. Required: %s.",
                sa.getIdentifier(), MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION),
            null, null);
      }
    } catch (NGAccessDeniedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnexpectedException(String.format("Unable to verify permissions for service account '%s': %s",
                                        sa.getIdentifier(), e.getMessage()),
          e);
    }
  }

  /**
   * Validates that the executor has at least one of the required pipeline permissions:
   * PIPELINE_EXECUTE, PIPELINE_EDIT, PIPELINE_CREATE, PIPELINE_DELETE, or PIPELINE_ABORT.
   */
  private void validateExecutorHasPipelinePermissions(String executorId,
      io.harness.accesscontrol.principals.PrincipalType executorType, String pipelineIdentifier, String accountId,
      String orgId, String projectId, String principalUniqueId) {
    try {
      ResourceScope scope = ResourceScope.of(accountId, orgId, projectId);
      io.harness.accesscontrol.acl.api.Principal aclPrincipal =
          io.harness.accesscontrol.acl.api.Principal.of(executorType, executorId, principalUniqueId);

      List<PermissionCheckDTO> checks =
          Arrays.asList(permissionCheck(scope, pipelineIdentifier, PipelineRbacPermissions.PIPELINE_EXECUTE),
              permissionCheck(scope, pipelineIdentifier, PipelineRbacPermissions.PIPELINE_EDIT),
              permissionCheck(scope, pipelineIdentifier, PipelineRbacPermissions.PIPELINE_CREATE),
              permissionCheck(scope, pipelineIdentifier, PipelineRbacPermissions.PIPELINE_DELETE),
              permissionCheck(scope, pipelineIdentifier, PipelineRbacPermissions.PIPELINE_ABORT));

      AccessCheckResponseDTO response = privilegedAccessControlClient.checkForAccess(aclPrincipal, checks);
      boolean hasAnyPermission =
          response.getAccessControlList().stream().anyMatch(dto -> Boolean.TRUE.equals(dto.isPermitted()));

      if (!hasAnyPermission) {
        throw new NGAccessDeniedException(
            String.format("'%s' does not have permission to run pipeline '%s'. Grant pipeline execute, edit, create, "
                    + "delete, or abort on that pipeline.",
                executorId, pipelineIdentifier),
            null, null);
      }
    } catch (NGAccessDeniedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnexpectedException(
          String.format("Unable to verify pipeline permissions for executor '%s': %s", executorId, e.getMessage()), e);
    }
  }

  private void recordRbacValidationDuration(String accountId, String executorType, long durationMs) {
    try (PmsMetricContextGuard ctx = new PmsMetricContextGuard(ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID,
             accountId, PmsEventMonitoringConstants.EXECUTOR_TYPE, executorType))) {
      metricService.recordDuration(TRIGGER_EXECUTOR_RBAC_VALIDATION_DURATION, Duration.ofMillis(durationMs));
    } catch (Exception e) {
      log.warn("Failed to record executor rbac validation duration metric", e);
    }
  }

  private PermissionCheckDTO permissionCheck(ResourceScope scope, String pipelineIdentifier, String permission) {
    return PermissionCheckDTO.builder()
        .resourceScope(scope)
        .resourceType("PIPELINE")
        .resourceIdentifier(pipelineIdentifier)
        .permission(permission)
        .build();
  }
}
