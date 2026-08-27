/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.common.beans.UserSource.MANUAL;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getDefaultResourceGroupIdentifier;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getDefaultResourceGroupIdentifierForAdmins;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getManagedAdminRole;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.code.CodeRepoResponseDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.Edition;
import io.harness.licensing.PricingType;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.code.services.HarnessCodeService;
import io.harness.ng.config.AutoProvisionLicenseConfig;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.event.manager.CIDefaultEntityManager;
import io.harness.ng.core.event.manager.HarnessLLMConnectorService;
import io.harness.ng.core.event.manager.HarnessSMManager;
import io.harness.ng.core.manifests.SampleManifestFileService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.user.remote.UserClient;
import io.harness.utils.CryptoUtils;
import io.harness.utils.ScopeUtils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGAccountSetupService {
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final NgUserService ngUserService;
  private final UserClient userClient;
  private final HarnessSMManager harnessSMManager;
  private final CIDefaultEntityManager ciDefaultEntityManager;
  private final boolean shouldAssignAdmins;
  private final NGAccountSettingService accountSettingService;
  private final FeatureFlagService featureFlagService;
  private final DefaultUserGroupService defaultUserGroupService;
  private final SampleManifestFileService sampleManifestFileService;
  private final ScopeInfoService scopeResolverService;
  private final HarnessCodeService harnessCodeService;
  private final LicenseService licenseService;
  private final AutoProvisionLicenseConfig autoProvisionLicenseConfig;
  private final HarnessLLMConnectorService harnessLLMConnectorService;

  @Inject
  public NGAccountSetupService(OrganizationService organizationService,
      @Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient, NgUserService ngUserService,
      UserClient userClient, HarnessSMManager harnessSMManager, CIDefaultEntityManager ciDefaultEntityManager,
      NextGenConfiguration nextGenConfiguration, NGAccountSettingService accountSettingService,
      ProjectService projectService, FeatureFlagService featureFlagService,
      SampleManifestFileService sampleManifestFileService, DefaultUserGroupService defaultUserGroupService,
      ScopeInfoService scopeResolverService, HarnessCodeService harnessCodeService, LicenseService licenseService,
      AutoProvisionLicenseConfig autoProvisionLicenseConfig, HarnessLLMConnectorService harnessLLMConnectorService) {
    this.organizationService = organizationService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.ngUserService = ngUserService;
    this.userClient = userClient;
    this.harnessSMManager = harnessSMManager;
    this.ciDefaultEntityManager = ciDefaultEntityManager;
    this.shouldAssignAdmins =
        nextGenConfiguration.getAccessControlAdminClientConfiguration().getMockAccessControlService().equals(
            Boolean.FALSE);
    this.accountSettingService = accountSettingService;
    this.projectService = projectService;
    this.featureFlagService = featureFlagService;
    this.sampleManifestFileService = sampleManifestFileService;
    this.defaultUserGroupService = defaultUserGroupService;
    this.scopeResolverService = scopeResolverService;
    this.harnessCodeService = harnessCodeService;
    this.licenseService = licenseService;
    this.autoProvisionLicenseConfig = autoProvisionLicenseConfig;
    this.harnessLLMConnectorService = harnessLLMConnectorService;
  }

  public void setupAccountForNG(String accountIdentifier, Edition edition, PricingType pricingType) {
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(accountIdentifier)
                                 .uniqueId(accountIdentifier)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .build();
    defaultUserGroupService.create(accountScope, emptyList());
    Organization defaultOrg = createDefaultOrg(accountIdentifier);
    if (featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)) {
      log.info(String.format("[NGAccountSetupService]: Setting up default project for account %s", accountIdentifier));
      Project defaultProject = createDefaultProject(accountIdentifier, defaultOrg.getIdentifier());
      log.info(String.format("[NGAccountSetupService]: Setting up all levels rbac for account %s", accountIdentifier));
      setupAllLevelRBAC(defaultOrg, defaultProject);
    } else {
      setupRBAC(defaultOrg);
    }
    log.info("[NGAccountSetupService]: Creating global SM for account{}", accountIdentifier);
    harnessSMManager.createGlobalSecretManager();
    log.info("[NGAccountSetupService]: Global SM Created Successfully for account{}", accountIdentifier);
    harnessSMManager.createHarnessSecretManager(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build());
    // Always provision Harness-managed LLM connectors, mirroring the Harness Secret Manager. Visibility is controlled
    // at read time by the rollout feature flag and the account-level setting (see DefaultConnectorServiceImpl).
    harnessLLMConnectorService.createHarnessManagedLLMConnectors(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build());
    ciDefaultEntityManager.createCIDefaultEntities(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build());
    log.info("[NGAccountSetupService]: CI Default Entities Created Successfully for account{}", accountIdentifier);
    try {
      CodeRepoResponseDTO codeRepoResponse = harnessCodeService.createHarnessDefaultRepository(accountIdentifier);

      log.info("[NGAccountSetupService]: Default account-level repo {} created for account {}",
          codeRepoResponse.getIdentifier(), accountIdentifier);
    } catch (Exception ex) {
      log.error("[NGAccountSetupService]: Failed to create default account-level repo for account {}. Error: {}",
          accountIdentifier, ex.getMessage());
    }
    accountSettingService.setUpDefaultAccountSettings(accountIdentifier);
    log.info("[NGAccountSetupService]: Default Account Settings Created Successfully for account{}", accountIdentifier);
    createSampleFiles(accountIdentifier);
    startFlexLicense(accountIdentifier, edition, pricingType);
  }

  private void startFlexLicense(String accountIdentifier, Edition edition, PricingType pricingType) {
    try {
      if (autoProvisionLicenseConfig != null && autoProvisionLicenseConfig.isEnabled()
          && PricingType.FLEX.equals(pricingType)) {
        List<ModuleType> modules = autoProvisionLicenseConfig.getModulesForEdition(edition);
        if (!modules.isEmpty()) {
          log.info("Auto-provisioning flex license for account {} with edition {} for modules {}", accountIdentifier,
              edition, modules);
          licenseService.startFlexLicense(accountIdentifier, edition, modules);
        } else {
          log.warn("No modules configured for auto-provisioning flex license for account {} with edition {}",
              accountIdentifier, edition);
        }
      }
    } catch (Exception e) {
      log.error("Failed to auto-provision flex license for account {}", accountIdentifier, e);
    }
  }

  private void createSampleFiles(String accountIdentifier) {
    try {
      SampleManifestFileService.SampleManifestFileCreateResponse fileCreateResponse =
          sampleManifestFileService.createDefaultFilesInFileStore(accountIdentifier);
      if (!fileCreateResponse.isCreated()) {
        log.error(String.format("Failed to create sample manifest files for account:%s. Reason %s", accountIdentifier,
            fileCreateResponse.getErrorMessage()));
      }
    } catch (Exception ex) {
      log.error("Failed to create sample manifest files for account:" + accountIdentifier, ex);
    }
  }

  private Organization createDefaultOrg(String accountIdentifier) {
    Optional<Organization> organization = organizationService.get(ScopeInfo.builder()
                                                                      .accountIdentifier(accountIdentifier)
                                                                      .scopeType(ScopeLevel.ACCOUNT)
                                                                      .uniqueId(accountIdentifier)
                                                                      .build(),
        DEFAULT_ORG_IDENTIFIER);
    if (organization.isPresent()) {
      log.info(String.format(
          "[NGAccountSetupService]: Default Organization for account %s already present", accountIdentifier));
      return organization.get();
    }
    OrganizationDTO createOrganizationDTO = OrganizationDTO.builder().build();
    createOrganizationDTO.setIdentifier(DEFAULT_ORG_IDENTIFIER);
    createOrganizationDTO.setName("default");
    createOrganizationDTO.setTags(emptyMap());
    createOrganizationDTO.setDescription("Default Organization");
    createOrganizationDTO.setHarnessManaged(true);
    Organization defaultOrganization = organizationService.create(ScopeInfo.builder()
                                                                      .accountIdentifier(accountIdentifier)
                                                                      .scopeType(ScopeLevel.ACCOUNT)
                                                                      .uniqueId(accountIdentifier)
                                                                      .build(),
        createOrganizationDTO);
    log.info(String.format("[NGAccountSetupService]: Created default org for account %s", accountIdentifier));
    return defaultOrganization;
  }

  private Project createDefaultProject(String accountIdentifier, String organizationIdentifier) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, organizationIdentifier, null);
    Optional<Project> project = projectService.get(scopeInfo, DEFAULT_PROJECT_IDENTIFIER);
    if (project.isPresent()) {
      log.info(String.format("[NGAccountSetupService]: Default Project for account %s organization %s already present",
          accountIdentifier, organizationIdentifier));
      return project.get();
    }
    ProjectDTO createProjectDTO = ProjectDTO.builder().build();
    createProjectDTO.setIdentifier(DEFAULT_PROJECT_IDENTIFIER);
    createProjectDTO.setName(DEFAULT_PROJECT_NAME);
    Project defaultProject = projectService.create(scopeInfo, createProjectDTO);
    log.info(String.format("[NGAccountSetupService]: Default project created for account %s", accountIdentifier));
    return defaultProject;
  }

  private void setupAllLevelRBAC(Organization organization, Project project) {
    Collection<UserInfo> cgUsers = getCGUsers(organization.getAccountIdentifier());
    Collection<String> cgAdmins =
        cgUsers.stream().filter(UserInfo::isAdmin).map(UserInfo::getUuid).collect(Collectors.toSet());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(organization.getAccountIdentifier())
                                     .uniqueId(organization.getAccountIdentifier())
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();
    if (featureFlagService.isNotEnabled(
            FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, organization.getAccountIdentifier())) {
      cgUsers.forEach(user -> upsertUserMembership(accountScopeInfo, user.getUuid()));
    } else {
      cgAdmins.forEach(user -> upsertUserMembership(accountScopeInfo, user));
    }
    assignAdminRoleToUsers(accountScopeInfo, cgAdmins);
    if (shouldAssignAdmins && !hasAdmin(accountScopeInfo)) {
      throw new GeneralException(
          String.format("No Admin could be assigned in scope %s", ScopeUtils.toString(accountScopeInfo)));
    }

    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(organization.getAccountIdentifier())
                                 .orgIdentifier(organization.getIdentifier())
                                 .uniqueId(organization.getUniqueId())
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .build();
    if (!hasAdmin(orgScopeInfo)) {
      cgAdmins.forEach(user -> upsertUserMembership(orgScopeInfo, user));
      assignAdminRoleToUsers(orgScopeInfo, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(orgScopeInfo)) {
        throw new GeneralException(
            String.format("No Admin could be assigned in scope %s", ScopeUtils.toString(orgScopeInfo)));
      }
    }

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(project.getAccountIdentifier())
                                     .orgIdentifier(project.getOrgIdentifier())
                                     .projectIdentifier(project.getIdentifier())
                                     .uniqueId(project.getUniqueId())
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    if (!hasAdmin(projectScopeInfo)) {
      cgAdmins.forEach(user -> upsertUserMembership(projectScopeInfo, user));
      assignAdminRoleToUsers(projectScopeInfo, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(projectScopeInfo)) {
        throw new GeneralException(
            String.format("No Admin could be assigned in scope %s", ScopeUtils.toString(projectScopeInfo)));
      }
    }
    log.info(String.format(
        "[NGAccountSetupService]: Rbac setup completed for account: %s", organization.getAccountIdentifier()));
  }

  private void setupRBAC(Organization organization) {
    Collection<UserInfo> cgUsers = getCGUsers(organization.getAccountIdentifier());
    Collection<String> cgAdmins =
        cgUsers.stream().filter(UserInfo::isAdmin).map(UserInfo::getUuid).collect(Collectors.toSet());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(organization.getAccountIdentifier())
                                     .uniqueId(organization.getAccountIdentifier())
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();
    if (featureFlagService.isNotEnabled(
            FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, organization.getAccountIdentifier())) {
      cgUsers.forEach(user -> upsertUserMembership(accountScopeInfo, user.getUuid()));
    } else {
      cgAdmins.forEach(user -> upsertUserMembership(accountScopeInfo, user));
    }
    assignAdminRoleToUsers(accountScopeInfo, cgAdmins);
    if (shouldAssignAdmins && !hasAdmin(accountScopeInfo)) {
      throw new GeneralException(
          String.format("No Admin could be assigned in scope %s", ScopeUtils.toString(accountScopeInfo)));
    }

    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(organization.getAccountIdentifier())
                                 .orgIdentifier(organization.getIdentifier())
                                 .uniqueId(organization.getUniqueId())
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .build();
    if (!hasAdmin(orgScopeInfo)) {
      cgAdmins.forEach(user -> upsertUserMembership(orgScopeInfo, user));
      assignAdminRoleToUsers(orgScopeInfo, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(orgScopeInfo)) {
        throw new GeneralException(
            String.format("No Admin could be assigned in scope %s", ScopeUtils.toString(orgScopeInfo)));
      }
    }
    log.info(String.format(
        "[NGAccountSetupService]: Rbac setup completed for account: %s", organization.getAccountIdentifier()));
  }

  private boolean hasAdmin(ScopeInfo scopeInfo) {
    List<String> resourceGroupIdentifiers = new ArrayList<>();
    resourceGroupIdentifiers.add(getDefaultResourceGroupIdentifierForAdmins(scopeInfo));
    resourceGroupIdentifiers.add(
        getDefaultResourceGroupIdentifier(scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()));
    return !isEmpty(
        ngUserService.listUsersHavingRole(scopeInfo, getManagedAdminRole(scopeInfo), resourceGroupIdentifiers));
  }

  private void assignAdminRoleToUsers(ScopeInfo scopeInfo, Collection<String> users) {
    createRoleAssignments(Scope.of(scopeInfo),
        buildRoleAssignments(
            users, getManagedAdminRole(scopeInfo), getDefaultResourceGroupIdentifierForAdmins(scopeInfo)));
  }

  private List<RoleAssignmentDTO> buildRoleAssignments(
      Collection<String> userIds, String roleIdentifier, String resourceGroupIdentifier) {
    return userIds.stream()
        .map(userId
            -> RoleAssignmentDTO.builder()
                   .disabled(false)
                   .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
                   .roleIdentifier(roleIdentifier)
                   .resourceGroupIdentifier(resourceGroupIdentifier)
                   .principal(PrincipalDTO.builder().identifier(userId).type(PrincipalType.USER).build())
                   .build())
        .collect(Collectors.toList());
  }

  private void createRoleAssignments(Scope scope, List<RoleAssignmentDTO> roleAssignments) {
    List<List<RoleAssignmentDTO>> batchedRoleAssignments = Lists.partition(roleAssignments, 2);
    for (List<RoleAssignmentDTO> batchOfRoleAssignment : batchedRoleAssignments) {
      NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier(), false,
          RoleAssignmentCreateRequestDTO.builder().roleAssignments(batchOfRoleAssignment).build()));
    }
  }

  private void upsertUserMembership(ScopeInfo scopeInfo, String userId) {
    try {
      ngUserService.addUserToScope(userId,
          Scope.builder()
              .accountIdentifier(scopeInfo.getAccountIdentifier())
              .orgIdentifier(scopeInfo.getOrgIdentifier())
              .projectIdentifier(scopeInfo.getProjectIdentifier())
              .build(),
          emptyList(), emptyList(), UserMembershipUpdateSource.SYSTEM, scopeInfo);
      ngUserService.updateNGUserToCGWithSource(userId, Scope.of(scopeInfo), MANUAL);
    } catch (DuplicateKeyException | DuplicateFieldException duplicateException) {
      // ignore
    }
  }

  private Collection<UserInfo> getCGUsers(String accountId) {
    Set<UserInfo> users = new HashSet<>();
    Stopwatch stopwatch = Stopwatch.createStarted();
    while (users.isEmpty() && stopwatch.elapsed(TimeUnit.SECONDS) <= 5) {
      // From CG side, account setup event is fired before setting up users in the account first. To handle that, we are
      // waiting up to 5 seconds for users to get setup correctly on CG side.
      sleep();
      int offset = 0;
      int limit = 500;
      int maxIterations = 50;
      while (maxIterations > 0) {
        PageResponse<UserInfo> usersPage = CGRestUtils.getResponse(
            userClient.list(accountId, String.valueOf(offset), String.valueOf(limit), null, true));
        if (isEmpty(usersPage.getResponse())) {
          break;
        }
        users.addAll(usersPage.getResponse());
        maxIterations--;
        offset += limit;
      }
    }
    return users;
  }

  private void sleep() {
    try {
      TimeUnit.MILLISECONDS.sleep(500);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("Thread Interrupted", ex);
    }
  }

  public void cleanUsersFromAccountForNg(String accountIdentifier) {
    Scope scope =
        Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(null).projectIdentifier(null).build();
    List<String> ngUsers = ngUserService.listUserIds(scope,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .uniqueId(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .build());
    log.info("Number of NG users in account {} : {}", accountIdentifier, ngUsers.size());
    List<String> ngNonAdmins = ngUsers.stream()
                                   .filter(user -> !ngUserService.isAccountAdmin(user, accountIdentifier))
                                   .collect(Collectors.toList());
    log.info("Number of Non Admin users in account {} : {}", accountIdentifier, ngNonAdmins.size());
    if (!ngNonAdmins.isEmpty()) {
      ngUserService.cleanUsersFromAccountForNg(ngNonAdmins, accountIdentifier);
    }
    log.info("CleanUp for accountId {} is completed", accountIdentifier);
  }
}
