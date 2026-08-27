/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.UnexpectedException;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.OPA)
public class OpaRbacRoleMigration implements NGMigration {
  private static final String MIGRATION_PURPOSE =
      "OPA RBAC migration: add governance _create permission to roles with _edit permission";
  @Inject private AccountUtils accountUtils;
  @Inject private ProjectService projectService;
  @Inject private OrganizationService organizationService;
  @Inject private AccessControlAdminClient accessControlAdminClient;
  private static final String OPA_POLICY_EDIT_PERMISSION = "core_governancePolicy_edit";
  private static final String OPA_POLICY_CREATE_PERMISSION = "core_governancePolicy_create";
  private static final String OPA_POLICYSET_EDIT_PERMISSION = "core_governancePolicySets_edit";
  private static final String OPA_POLICYSET_CREATE_PERMISSION = "core_governancePolicySets_create";

  @Override
  public void migrate() {
    log.info("Starting the migration for {}.", MIGRATION_PURPOSE);
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      List<String> targetAccounts = accountUtils.getAllAccountIds();
      for (String accountId : targetAccounts) {
        updateAccountLevelRoles(accountId);
        updateOrgLevelRoles(accountId);
        updateProjectLevelRoles(accountId);
      }
    } catch (Exception e) {
      log.error("Failed to perform migration for {}", MIGRATION_PURPOSE, e);
    }
  }

  private void addGovernanceCreatePermissionToRoles(String accountId, String orgId, String projectId) {
    PageResponse<RoleResponseDTO> roleResponse;
    int page = 0;
    do {
      try {
        roleResponse = getResponse(accessControlAdminClient.getRoles(page, 100, accountId, orgId, projectId, null));
      } catch (Exception ex) {
        throw new UnexpectedException("Error in fetching roles for accountIdentifier " + accountId + " | page " + page);
      }
      if (roleResponse != null && isNotEmpty(roleResponse.getContent())) {
        processRoleResponse(roleResponse, accountId, orgId, projectId);
      }
      page++;
    } while (roleResponse != null && isNotEmpty(roleResponse.getContent()));
  }

  private void processRoleResponse(
      PageResponse<RoleResponseDTO> roleResponse, String accountId, String orgId, String projectId) {
    List<RoleResponseDTO> roleResponseDTOS = roleResponse.getContent();
    for (RoleResponseDTO roleResponseDTO : roleResponseDTOS) {
      RoleDTO roleDTO = roleResponseDTO.getRole();
      boolean isHarnessManaged = roleResponseDTO.isHarnessManaged();
      String roleIdentifier = roleDTO.getIdentifier();
      Set<String> rolePermissions = roleDTO.getPermissions();
      if (Boolean.FALSE.equals(isHarnessManaged) && !roleDTO.getIdentifier().startsWith("_")) {
        boolean permissionChanged = false;
        permissionChanged |= processPermission(
            rolePermissions, OPA_POLICY_EDIT_PERMISSION, OPA_POLICY_CREATE_PERMISSION, roleIdentifier);
        permissionChanged |= processPermission(
            rolePermissions, OPA_POLICYSET_EDIT_PERMISSION, OPA_POLICYSET_CREATE_PERMISSION, roleIdentifier);
        if (permissionChanged) {
          updateRole(roleIdentifier, accountId, orgId, projectId, roleDTO);
        }
      }
    }
  }

  private void updateRole(String roleIdentifier, String accountId, String orgId, String projectId, RoleDTO roleDTO) {
    try {
      getResponse(accessControlAdminClient.updateRole(roleIdentifier, accountId, orgId, projectId, roleDTO));
    } catch (Exception ex) {
      log.error("Error updating role = {} in account = {}", roleIdentifier, accountId, ex);
    }
  }

  private boolean processPermission(
      Set<String> rolePermissions, String opaEditPermission, String opaCreatePermission, String roleIdentifier) {
    if (rolePermissions.contains(opaEditPermission) && !rolePermissions.contains(opaCreatePermission)) {
      log.info("Adding {} permission for role: {}", opaCreatePermission, roleIdentifier);
      rolePermissions.add(opaCreatePermission);
      return true;
    }
    return false;
  }

  /*
  Update Roles for given account
  */
  private void updateAccountLevelRoles(String accountId) {
    log.info("Migration started {} for accountIdentifier = {}", MIGRATION_PURPOSE, accountId);
    addGovernanceCreatePermissionToRoles(accountId, null, null);
    log.info("Account level Migration completed {} for accountIdentifier = {}", MIGRATION_PURPOSE, accountId);
  }

  /*
  Update Roles for each org in given account
  */
  private void updateOrgLevelRoles(String accountId) {
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountId)
                            .and(OrganizationKeys.deleted)
                            .is(Boolean.FALSE);
    List<Organization> orgIds = organizationService.list(criteria);
    if (EmptyPredicate.isEmpty(orgIds)) {
      return;
    }
    for (Organization organization : orgIds) {
      log.info("Migration started: {} for orgIdentifier = {} with accountId = {}", MIGRATION_PURPOSE, organization,
          accountId);
      addGovernanceCreatePermissionToRoles(accountId, organization.getIdentifier(), null);
    }
    log.info(
        "Org level Migration completed: {} for all organizations with accountId = {}", MIGRATION_PURPOSE, accountId);
  }

  /*
  Update Roles for each project in given account
  */
  private void updateProjectLevelRoles(String accountId) {
    Criteria criteria =
        Criteria.where(ProjectKeys.accountIdentifier).is(accountId).and(ProjectKeys.deleted).is(Boolean.FALSE);

    List<Project> projects = projectService.list(criteria);
    if (EmptyPredicate.isEmpty(projects)) {
      return;
    }
    for (Project project : projects) {
      log.info("Migration started: {} for projectId = {}, orgId = {} with accountId = {}", MIGRATION_PURPOSE, project,
          project.getOrgIdentifier(), accountId);
      addGovernanceCreatePermissionToRoles(accountId, project.getOrgIdentifier(), project.getIdentifier());
    }
    log.info(
        "Project level Migration completed: {} for all projects with accountId = {}", MIGRATION_PURPOSE, accountId);
  }
}
