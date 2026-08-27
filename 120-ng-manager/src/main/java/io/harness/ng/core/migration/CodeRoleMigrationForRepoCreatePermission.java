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
@OwnedBy(HarnessTeam.CODE)
public class CodeRoleMigrationForRepoCreatePermission implements NGMigration {
  private static final String MIGRATION_PREFIX = "[Migration] -";
  private static final String MIGRATION_PURPOSE =
      "CODE Roles migration: add code_repo_create permission to roles with code_repo_edit permission due to the split "
      + "of code_repo_edit into repo_edit and repo_create";
  @Inject private io.harness.account.utils.AccountUtils accountUtils;
  @Inject private ProjectService projectService;
  @Inject private OrganizationService organizationService;
  @Inject private AccessControlAdminClient accessControlAdminClient;
  private static final String CODE_REPO_EDIT_PERMISSION = "code_repo_edit";
  private static final String CODE_REPO_CREATE_PERMISSION = "code_repo_create";

  @Override
  public void migrate() {
    log.info("{} Starting the {}.", MIGRATION_PREFIX, MIGRATION_PURPOSE);
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      List<String> targetAccounts = accountUtils.getAllAccountIds();
      for (String accountId : targetAccounts) {
        updateAccountLevelRoles(accountId);
        updateOrgLevelRoles(accountId);
        updateProjectLevelRoles(accountId);
      }
    } catch (Exception e) {
      log.error("{} Failed to perform migration for {}", MIGRATION_PREFIX, MIGRATION_PURPOSE, e);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
      log.info("{} Completed the {}.", MIGRATION_PREFIX, MIGRATION_PURPOSE);
    }
  }

  private void addRepoCreatePermissionToRoles(String accountId, String orgId, String projectId) {
    PageResponse<RoleResponseDTO> roleResponse;
    int page = 0;
    boolean hasContent;
    do {
      try {
        roleResponse = getResponse(accessControlAdminClient.getRoles(page, 100, accountId, orgId, projectId, null));
      } catch (Exception e) {
        throw new UnexpectedException(
            "Error in fetching roles for accountIdentifier " + accountId + " | page " + page, e);
      }

      hasContent = (roleResponse != null && isNotEmpty(roleResponse.getContent()));
      if (hasContent) {
        processRoleResponse(roleResponse, accountId, orgId, projectId);
      }
      page++;
    } while (hasContent);
  }

  private void processRoleResponse(
      PageResponse<RoleResponseDTO> roleResponse, String accountId, String orgId, String projectId) {
    List<RoleResponseDTO> roleResponseDTOS = roleResponse.getContent();
    for (RoleResponseDTO roleResponseDTO : roleResponseDTOS) {
      RoleDTO roleDTO = roleResponseDTO.getRole();
      boolean isHarnessManaged = roleResponseDTO.isHarnessManaged();
      if (Boolean.TRUE.equals(isHarnessManaged)) {
        continue;
      }

      Set<String> rolePermissions = roleDTO.getPermissions();
      if (!rolePermissions.contains(CODE_REPO_EDIT_PERMISSION)
          || rolePermissions.contains(CODE_REPO_CREATE_PERMISSION)) {
        continue;
      }
      String roleIdentifier = roleDTO.getIdentifier();
      log.info("{} Adding {} permission for role: {}", MIGRATION_PREFIX, CODE_REPO_CREATE_PERMISSION, roleIdentifier);
      rolePermissions.add(CODE_REPO_CREATE_PERMISSION);
      updateRole(roleIdentifier, accountId, orgId, projectId, roleDTO);
    }
  }

  private void updateRole(String roleIdentifier, String accountId, String orgId, String projectId, RoleDTO roleDTO) {
    try {
      getResponse(accessControlAdminClient.updateRole(roleIdentifier, accountId, orgId, projectId, roleDTO));
    } catch (Exception ex) {
      log.error("{} Error updating role = {} in account = {}", MIGRATION_PREFIX, roleIdentifier, accountId, ex);
    }
  }

  /*
  Update Roles for the given account
  */
  private void updateAccountLevelRoles(String accountId) {
    log.info("{} Started {} for accountIdentifier = {}", MIGRATION_PREFIX, MIGRATION_PURPOSE, accountId);
    addRepoCreatePermissionToRoles(accountId, null, null);
    log.info("{} Account level Migration completed {} for accountIdentifier = {}", MIGRATION_PREFIX, MIGRATION_PURPOSE,
        accountId);
  }

  /*
  Update Roles for each org in the given account
  */
  private void updateOrgLevelRoles(String accountId) {
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountId)
                            .and(OrganizationKeys.deleted)
                            .is(Boolean.FALSE);
    List<Organization> organizations = organizationService.list(criteria);
    if (EmptyPredicate.isEmpty(organizations)) {
      return;
    }
    for (Organization organization : organizations) {
      log.info("{} Started: {} for orgIdentifier = {} with accountId = {}", MIGRATION_PREFIX, MIGRATION_PURPOSE,
          organization.getIdentifier(), accountId);
      addRepoCreatePermissionToRoles(accountId, organization.getIdentifier(), null);
    }
    log.info("{} Org level Migration completed: {} for all organizations with accountId = {}", MIGRATION_PREFIX,
        MIGRATION_PURPOSE, accountId);
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
      log.info("{} Started: {} for projectId = {}, orgId = {} with accountId = {}", MIGRATION_PREFIX, MIGRATION_PURPOSE,
          project, project.getOrgIdentifier(), accountId);
      addRepoCreatePermissionToRoles(accountId, project.getOrgIdentifier(), project.getIdentifier());
    }
    log.info("{} Project level Migration completed: {} for all projects with accountId = {}", MIGRATION_PREFIX,
        MIGRATION_PURPOSE, accountId);
  }
}
