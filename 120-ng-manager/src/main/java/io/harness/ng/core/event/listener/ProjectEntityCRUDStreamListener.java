/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.CREATE_DEFAULT_PROJECT;
import static io.harness.beans.FeatureName.PIPE_SKIP_GITX_WEBHOOK_DELETION_ON_PROJECT_DELETE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.MOVE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.ng.accesscontrol.PlatformPermissions.INVITE_PERMISSION_IDENTIFIER;

import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.dbdevops.step.service.RemotePluginService;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteAllGitXWebhooksRequestDTO;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.api.DefaultUserGroupScopeService;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.impl.ScopeInfoHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.user.service.NgUserScopeService;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.security.dto.PrincipalType;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;
import io.harness.service.releasedetailsmapping.ReleaseDetailsMappingService;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.ScopeUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PL)
@Slf4j
@Singleton
public class ProjectEntityCRUDStreamListener implements MessageListener {
  private final EnvironmentService environmentService;
  private final ServiceEntityService serviceEntityService;
  private final ServiceOverrideService serviceOverrideService;
  private final ServiceOverridesServiceV2 serviceOverridesServiceV2;
  private final InfrastructureEntityService infraService;
  private final ClusterService clusterService;
  private final EnvironmentGroupService environmentGroupService;

  private final InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private final InfrastructureMappingService infrastructureMappingService;

  private final ReleaseDetailsMappingService releaseDetailsMappingService;
  private final ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;

  private final GitXWebhookService gitXWebhookService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final RemotePluginService remotePluginService;
  private final DefaultUserGroupScopeService defaultUserGroupService;
  private final NgUserScopeService ngUserService;
  private final AccessControlClient accessControlClient;
  private final FeatureFlagService featureFlagService;
  private final MetricService metricService;
  private final Cache<String, ScopeInfo> projectScopeInfoDataCache;
  private final Cache<String, ScopeInfo> orgScopeInfoDataCache;
  private final Cache<String, ScopeInfo> scopeInfoUniqueIdCache;
  private final ScopeInfoHelper scopeInfoHelper;

  @Inject
  public ProjectEntityCRUDStreamListener(EnvironmentService environmentService,
      ServiceOverrideService serviceOverrideService, ServiceOverridesServiceV2 serviceOverridesServiceV2,
      InfrastructureEntityService infraService, ServiceEntityService serviceEntityService,
      ClusterService clusterService, InfrastructureMappingService infrastructureMappingService,
      ReleaseDetailsMappingService releaseDetailsMappingService,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService,
      EnvironmentGroupService environmentGroupService, ServiceOverrideV2ValidationHelper overrideV2ValidationHelper,
      GitXWebhookService gitXWebhookService, NGFeatureFlagHelperService ngFeatureFlagHelperService,
      RemotePluginService remotePluginService,
      @Named(io.harness.ng.core.services.ProjectService.PROJECT_SCOPE_INFO_DATA_CACHE_KEY)
      Cache<String, ScopeInfo> projectScopeInfoDataCache,
      @Named(OrganizationService.ORG_SCOPE_INFO_DATA_CACHE_KEY) Cache<String, ScopeInfo> orgScopeInfoDataCache,
      @Named(io.harness.ng.core.services.ScopeInfoService.SCOPE_INFO_UNIQUE_ID_CACHE_KEY)
      Cache<String, ScopeInfo> scopeInfoUniqueIdCache, ScopeInfoHelper scopeInfoHelper,
      DefaultUserGroupScopeService defaultUserGroupService, NgUserScopeService ngUserService,
      AccessControlClient accessControlClient, FeatureFlagService featureFlagService, MetricService metricService) {
    this.environmentService = environmentService;
    this.serviceOverrideService = serviceOverrideService;
    this.serviceOverridesServiceV2 = serviceOverridesServiceV2;
    this.serviceEntityService = serviceEntityService;
    this.infraService = infraService;
    this.clusterService = clusterService;
    this.environmentGroupService = environmentGroupService;
    this.infrastructureMappingService = infrastructureMappingService;
    this.releaseDetailsMappingService = releaseDetailsMappingService;
    this.instanceSyncPerpetualTaskInfoService = instanceSyncPerpetualTaskInfoService;
    this.overrideV2ValidationHelper = overrideV2ValidationHelper;
    this.gitXWebhookService = gitXWebhookService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.remotePluginService = remotePluginService;
    this.defaultUserGroupService = defaultUserGroupService;
    this.ngUserService = ngUserService;
    this.accessControlClient = accessControlClient;
    this.featureFlagService = featureFlagService;
    this.metricService = metricService;
    this.projectScopeInfoDataCache = projectScopeInfoDataCache;
    this.orgScopeInfoDataCache = orgScopeInfoDataCache;
    this.scopeInfoUniqueIdCache = scopeInfoUniqueIdCache;
    this.scopeInfoHelper = scopeInfoHelper;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        String action = metadataMap.get(ACTION);
        if (ORGANIZATION_ENTITY.equals(entityType)) {
          return EntityCRUDStreamListenerMetrics.executeWithMetrics(entityType, action, metricService, () -> {
            handleOrgEvent(message);
            return true;
          });
        } else if (PROJECT_ENTITY.equals(entityType)) {
          return EntityCRUDStreamListenerMetrics.executeWithMetrics(entityType, action, metricService, () -> {
            handleProjectEvent(message);
            return true;
          });
        }
      }
    }
    return true;
  }

  private void handleOrgEvent(Message message) {
    final Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = metadataMap.get(ACTION);
    if (action != null) {
      boolean status = processOrganizationEntityChangeEvent(organizationEntityChangeDTO, action);
      if (!status) {
        log.warn("failed to process org {} {}", organizationEntityChangeDTO, action);
      }
    }
  }

  private void handleProjectEvent(Message message) {
    final Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = metadataMap.get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          boolean createStatus = processProjectCreateEvent(projectEntityChangeDTO);
          if (!createStatus) {
            log.warn("Failed to process project {} creation event", projectEntityChangeDTO);
          }
          break;
        case DELETE_ACTION:
          boolean status = processProjectDeleteEvent(projectEntityChangeDTO);
          if (!status) {
            log.warn("Failed to process project {} deletion event", projectEntityChangeDTO);
          }
          break;
        case MOVE_ACTION:
          processProjectMoveEvent(projectEntityChangeDTO);
          break;
        default:
      }
    }
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    final String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();
    final String orgIdentifier = projectEntityChangeDTO.getOrgIdentifier();
    final String projIdentifier = projectEntityChangeDTO.getIdentifier();
    final String uniqueId = projectEntityChangeDTO.getUniqueId();

    evictCaches(projectEntityChangeDTO);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    boolean envDeleted = processQuietly(() -> environmentService.forceDeleteAllInProject(scopeInfo));
    boolean infraDeleted = processQuietly(() -> infraService.forceDeleteAllInProject(scopeInfo));
    boolean clustersDeleted =
        processQuietly(() -> clusterService.deleteAllFromProj(accountIdentifier, orgIdentifier, projIdentifier));
    boolean serviceDeleted = processQuietly(() -> serviceEntityService.forceDeleteAllInProject(scopeInfo));
    boolean isOverridesV2Enabled = overrideV2ValidationHelper.isOverridesV2Enabled(accountIdentifier);
    boolean serviceOverridesDeleted =
        processQuietly(()
                           -> isOverridesV2Enabled ? (serviceOverridesServiceV2.deleteAllInProject(scopeInfo))
                                                   : (serviceOverrideService.deleteAllInProject(scopeInfo)));
    boolean infraMappingDeleted = processQuietly(() -> infrastructureMappingService.deleteAllFromProj(scopeInfo));

    boolean releaseDetailsMappingDeleted =
        processQuietly(() -> releaseDetailsMappingService.deleteAllFromProj(scopeInfo));

    boolean gitxWebhookDeleted;
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, PIPE_SKIP_GITX_WEBHOOK_DELETION_ON_PROJECT_DELETE)) {
      gitxWebhookDeleted = true;
    } else {
      gitxWebhookDeleted = processQuietly(
          ()
              -> gitXWebhookService
                     .deleteAllGitXWebhooks(DeleteAllGitXWebhooksRequestDTO.builder().scopeInfo(scopeInfo).build())
                     .isSuccessfullyDeleted());
    }

    boolean dbopsSchemaDeleted = false;
    try {
      remotePluginService.deleteSchemaForScope(scopeInfo);
      dbopsSchemaDeleted = true;
    } catch (Exception e) {
      log.error(String.format("failed to delete dbops schemas belonging to account [%s], organization [%s] and project "
                        + "[%s] with parentId [%s]",
                    scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
                    scopeInfo.getUniqueId()),
          e);
    }

    return envDeleted && infraDeleted && serviceDeleted && clustersDeleted && serviceOverridesDeleted
        && infraMappingDeleted && releaseDetailsMappingDeleted && gitxWebhookDeleted && dbopsSchemaDeleted;
  }

  private boolean processProjectCreateEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    final String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();
    final String orgIdentifier = projectEntityChangeDTO.getOrgIdentifier();
    final String projIdentifier = projectEntityChangeDTO.getIdentifier();
    final String uniqueId = projectEntityChangeDTO.getUniqueId();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    try {
      // Create default user group for the project
      defaultUserGroupService.create(scopeInfo, emptyList());
    } catch (Exception ex) {
      log.error("Default User Group Creation failed for Project: " + ScopeUtils.toString(scopeInfo), ex);
    }

    // Skip admin assignment for default project (handled by account setup)
    if (featureFlagService.isGlobalEnabled(CREATE_DEFAULT_PROJECT)) {
      if (DEFAULT_PROJECT_IDENTIFIER.equals(projIdentifier)) {
        return true;
      }
    }

    // Extract creator info from the event
    String principalId = projectEntityChangeDTO.getPrincipalId();
    String principalUniqueId = projectEntityChangeDTO.getPrincipalUniqueId();
    String principalTypeStr = projectEntityChangeDTO.getPrincipalType();

    if (isEmpty(principalId)) {
      log.error(
          "Cannot assign project admin - no principal info available for project: {}", ScopeUtils.toString(scopeInfo));
      return true; // Consider this a success since the project was created
    }

    PrincipalType principalType =
        isEmpty(principalTypeStr) ? PrincipalType.USER : PrincipalType.valueOf(principalTypeStr);

    try {
      assignProjectAdmin(scopeInfo, principalId, principalType, principalUniqueId);
      busyPollUntilProjectSetupCompletes(scopeInfo, principalId);
    } catch (Exception e) {
      log.error("Failed to complete post project creation steps for [{}]", ScopeUtils.toString(scopeInfo), e);
    }

    return true;
  }

  private void assignProjectAdmin(
      ScopeInfo scopeInfo, String principalId, PrincipalType principalType, String principalUniqueId) {
    switch (principalType) {
      case USER:
        ngUserService.addUserToScope(principalId,
            Scope.builder()
                .accountIdentifier(scopeInfo.getAccountIdentifier())
                .orgIdentifier(scopeInfo.getOrgIdentifier())
                .projectIdentifier(scopeInfo.getProjectIdentifier())
                .build(),
            singletonList(RoleBinding.builder()
                              .roleIdentifier("_project_admin")
                              .roleScopeLevel(ScopeLevel.PROJECT.name().toLowerCase())
                              .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .build()),
            emptyList(), io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM, scopeInfo);
        break;
      case SERVICE_ACCOUNT:
        ngUserService.addServiceAccountToScope(principalId,
            Scope.builder()
                .accountIdentifier(scopeInfo.getAccountIdentifier())
                .orgIdentifier(scopeInfo.getOrgIdentifier())
                .projectIdentifier(scopeInfo.getProjectIdentifier())
                .build(),
            RoleBinding.builder()
                .roleIdentifier("_project_admin")
                .roleScopeLevel(ScopeLevel.PROJECT.name().toLowerCase())
                .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                .build(),
            io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM, principalUniqueId);
        break;
      case API_KEY:
      case SERVICE:
        log.error("Cannot assign principal {} with type {} to project", principalId, principalType);
        break;
      default:
        log.error("Unknown principal type {} for project setup", principalType);
    }
  }

  private void busyPollUntilProjectSetupCompletes(ScopeInfo scopeInfo, String userId) {
    RetryConfig config = RetryConfig.custom()
                             .maxAttempts(50)
                             .waitDuration(Duration.ofMillis(200))
                             .retryOnResult(FALSE::equals)
                             .retryExceptions(Exception.class)
                             .ignoreExceptions(IOException.class)
                             .build();
    Retry retry = Retry.of("check user permissions", config);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(
        event -> log.info("Retrying for project {} {}", scopeInfo.getProjectIdentifier(), event.toString()));
    publisher.onSuccess(
        event -> log.info("Retrying for project {} {}", scopeInfo.getProjectIdentifier(), event.toString()));
    Supplier<Boolean> hasAccess = Retry.decorateSupplier(retry,
        ()
            -> accessControlClient.hasAccess(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                 scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
                Resource.of("USER", userId), INVITE_PERMISSION_IDENTIFIER));
    if (FALSE.equals(hasAccess.get())) {
      log.error(
          "Finishing project setup without confirm role assignment creation [{}]", ScopeUtils.toString(scopeInfo));
    }
  }

  /**
   * Evicts ng-manager's scope-info caches when a project is moved to another org. Once project move traffic is owned by
   * resource-hierarchy-service, ng-manager no longer evicts these caches inline (via ProjectServiceImpl#moveProject),
   * so it must react to the MOVE event on the ENTITY_CRUD stream. Mirrors the resource-hierarchy-service listener:
   * drops the entry under both the destination-org key and the source-org key, plus the uniqueId-keyed ScopeInfo (which
   * still points at the old org).
   */
  private void processProjectMoveEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    evictCaches(projectEntityChangeDTO);
  }

  boolean processQuietly(BooleanSupplier b) {
    try {
      b.getAsBoolean();
      // supplier processed
      return true;
    } catch (Exception ex) {
      log.error("failed to process entity deletion", ex);
      // ignore this
      return false;
    }
  }

  private boolean processOrganizationEntityChangeEvent(
      OrganizationEntityChangeDTO organizationEntityChangeDTO, String action) {
    switch (action) {
      case CREATE_ACTION:
        return processOrganizationCreateEvent(organizationEntityChangeDTO);
      case DELETE_ACTION:
        return processOrganizationDeleteEvent(organizationEntityChangeDTO);
      default:
        return true;
    }
  }

  private boolean processOrganizationCreateEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    final String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();
    final String orgIdentifier = organizationEntityChangeDTO.getIdentifier();
    final String uniqueId = organizationEntityChangeDTO.getUniqueId();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    try {
      // Create default user group for the organization
      defaultUserGroupService.create(scopeInfo, emptyList());
    } catch (Exception ex) {
      log.error("Default User Group Creation failed for Organization: " + ScopeUtils.toString(scopeInfo), ex);
    }

    // Skip admin assignment for default org (handled by account setup)
    if (DEFAULT_ORG_IDENTIFIER.equals(orgIdentifier)) {
      return true;
    }

    // Extract creator info from the event
    String principalId = organizationEntityChangeDTO.getPrincipalId();
    String principalUniqueId = organizationEntityChangeDTO.getPrincipalUniqueId();
    String principalTypeStr = organizationEntityChangeDTO.getPrincipalType();

    if (isEmpty(principalId)) {
      log.error(
          "Cannot assign org admin - no principal info available for organization: {}", ScopeUtils.toString(scopeInfo));
      return true;
    }

    PrincipalType principalType =
        isEmpty(principalTypeStr) ? PrincipalType.USER : PrincipalType.valueOf(principalTypeStr);

    try {
      assignOrgAdmin(scopeInfo, principalId, principalType, principalUniqueId);
      busyPollUntilOrgSetupCompletes(scopeInfo, principalId);
    } catch (Exception e) {
      log.error("Failed to complete post organization creation steps for [{}]", ScopeUtils.toString(scopeInfo), e);
    }

    return true;
  }

  private void assignOrgAdmin(
      ScopeInfo scopeInfo, String principalId, PrincipalType principalType, String principalUniqueId) {
    switch (principalType) {
      case USER:
        ngUserService.addUserToScope(principalId,
            Scope.builder()
                .accountIdentifier(scopeInfo.getAccountIdentifier())
                .orgIdentifier(scopeInfo.getOrgIdentifier())
                .build(),
            singletonList(RoleBinding.builder()
                              .roleIdentifier("_organization_admin")
                              .roleScopeLevel(ScopeLevel.ORGANIZATION.name().toLowerCase())
                              .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                              .build()),
            emptyList(), io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM, scopeInfo);
        break;
      case SERVICE_ACCOUNT:
        ngUserService.addServiceAccountToScope(principalId,
            Scope.builder()
                .accountIdentifier(scopeInfo.getAccountIdentifier())
                .orgIdentifier(scopeInfo.getOrgIdentifier())
                .build(),
            RoleBinding.builder()
                .roleIdentifier("_organization_admin")
                .roleScopeLevel(ScopeLevel.ORGANIZATION.name().toLowerCase())
                .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                .build(),
            io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM, principalUniqueId);
        break;
      case API_KEY:
      case SERVICE:
        log.error("Cannot assign principal {} with type {} to organization", principalId, principalType);
        break;
      default:
        log.error("Unknown principal type {} for organization setup", principalType);
    }
  }

  private void busyPollUntilOrgSetupCompletes(ScopeInfo scopeInfo, String userId) {
    RetryConfig config = RetryConfig.custom()
                             .maxAttempts(50)
                             .waitDuration(Duration.ofMillis(200))
                             .retryOnResult(FALSE::equals)
                             .retryExceptions(Exception.class)
                             .ignoreExceptions(IOException.class)
                             .build();
    Retry retry = Retry.of("check user permissions for org", config);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(
        event -> log.info("Retrying for organization {} {}", scopeInfo.getOrgIdentifier(), event.toString()));
    publisher.onSuccess(
        event -> log.info("Retrying for organization {} {}", scopeInfo.getOrgIdentifier(), event.toString()));
    Supplier<Boolean> hasAccess = Retry.decorateSupplier(retry,
        ()
            -> accessControlClient.hasAccess(
                ResourceScope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null),
                Resource.of("USER", userId), INVITE_PERMISSION_IDENTIFIER));
    if (FALSE.equals(hasAccess.get())) {
      log.error(
          "Finishing organization setup without confirm role assignment creation [{}]", ScopeUtils.toString(scopeInfo));
    }
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();
    String orgIdentifier = organizationEntityChangeDTO.getIdentifier();
    String orgUniqueId = organizationEntityChangeDTO.getUniqueId();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueId)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    evictOrgCaches(accountIdentifier, orgIdentifier, orgUniqueId);

    boolean envDeleted = processQuietly(() -> environmentService.forceDeleteAllInOrg(scopeInfo));
    boolean infraDeleted = processQuietly(() -> infraService.forceDeleteAllInOrg(scopeInfo));
    // delete org level clusters when clusters are supported at org/account level
    boolean serviceDeleted = processQuietly(() -> serviceEntityService.forceDeleteAllInOrg(scopeInfo));
    boolean isOverridesV2Enabled = overrideV2ValidationHelper.isOverridesV2Enabled(accountIdentifier);
    boolean serviceOverridesDeleted =
        processQuietly(()
                           -> isOverridesV2Enabled ? (serviceOverridesServiceV2.deleteAllInOrg(scopeInfo))
                                                   : (serviceOverrideService.deleteAllInOrg(scopeInfo)));
    boolean envGroupsDeleted = processQuietly(() -> environmentGroupService.deleteAtCurrentScope(scopeInfo));

    return envDeleted && infraDeleted && serviceDeleted && serviceOverridesDeleted && envGroupsDeleted;
  }

  private void evictOrgCaches(String accountIdentifier, String orgIdentifier, String orgUniqueId) {
    if (isEmpty(accountIdentifier) || isEmpty(orgIdentifier)) {
      log.warn("Skipping org cache eviction — accountId [{}] or orgId [{}] is empty", accountIdentifier, orgIdentifier);
      return;
    }
    String scopeInfoKey = scopeInfoHelper.getScopeInfoCacheKey(accountIdentifier, orgIdentifier, null);
    orgScopeInfoDataCache.remove(scopeInfoKey);
    log.info("Evicted orgScopeInfoDataCache for key [{}] on organization delete", scopeInfoKey);

    if (isNotEmpty(orgUniqueId)) {
      scopeInfoUniqueIdCache.remove(orgUniqueId);
      log.info("Evicted scopeInfoUniqueIdCache for uniqueId [{}] on organization delete", orgUniqueId);
    }
  }

  private void evictCaches(ProjectEntityChangeDTO dto) {
    String accountId = dto.getAccountIdentifier();
    String orgId = dto.getOrgIdentifier();
    String projectId = dto.getIdentifier();
    String uniqueId = dto.getUniqueId();
    String oldOrgId = dto.getOldOrgIdentifier();

    if (isEmpty(accountId) || isEmpty(orgId) || isEmpty(projectId)) {
      log.warn("Skipping project cache eviction — accountId [{}], orgId [{}], or projectId [{}] is empty", accountId,
          orgId, projectId);
      return;
    }

    String scopeInfoKey = scopeInfoHelper.getScopeInfoCacheKey(accountId, orgId, projectId);
    projectScopeInfoDataCache.remove(scopeInfoKey);

    log.info("Evicted projectScopeInfoDataCache for key [{}] on project delete", scopeInfoKey);

    if (isNotEmpty(oldOrgId)) {
      String oldScopeInfoKey = scopeInfoHelper.getScopeInfoCacheKey(accountId, oldOrgId, projectId);
      projectScopeInfoDataCache.remove(oldScopeInfoKey);
      log.info("Evicted projectScopeInfoDataCache for source-org key [{}] on project move", oldScopeInfoKey);
    }

    if (isNotEmpty(uniqueId)) {
      scopeInfoUniqueIdCache.remove(uniqueId);
      log.info("Evicted scopeInfoUniqueIdCache for uniqueId [{}] on project delete", uniqueId);
    }
  }
}
