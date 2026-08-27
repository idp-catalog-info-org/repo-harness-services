/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Set;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class AddEnvRollbackPermissionToPipelineExecutorRoles implements NGMigration {
  @Inject private ProjectService projectService;
  @Inject private OrganizationService organizationService;
  @Inject @Named("PRIVILEGED") private AccessControlAdminClient accessControlClient;
  @Inject private AccountUtils accountUtils;
  private static final String EXECUTOR_PERMISSION = "core_pipeline_execute";
  private static final String ROLLBACK_PERMISSION = "core_environment_rollback";
  private static final String MIGRATION_LOG = "AddEnvRollbackPermissionToPipelineExecutorRoles :"
      + " Migration of {} level roles pipeline executor permission to env rollback permission for account id: {} is completed";
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  @Inject @Named("envPermissionMigrationCache") private Cache<String, Boolean> eventsCache;
  private static final Integer PAGE_SIZE = 100;

  @VisibleForTesting
  public void migrate() {
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      List<String> targetAccounts = accountUtils.getAllNGAccountIds();
      for (String accountId : targetAccounts) {
        if (!eventsCache.containsKey(accountId)) {
          updateAccountLevelRoles(accountId);
          updateOrgLevelRoles(accountId);
          updateProjectLevelRoles(accountId);
          eventsCache.put(accountId, true);
        }
      }
      eventsCache.clear();
    } catch (Exception e) {
      log.error("Failed to perform EnvRollbackPermission migration on access control db", e);
    }
  }

  /*
  Update Roles for given account
   */
  private void updateAccountLevelRoles(String accountId) {
    addEnvironmentRollbackPermissionToRoles(accountId, null, null);
    log.info(MIGRATION_LOG, ACCOUNT, accountId);
  }

  /*
  Update Roles for each project in given account
   */
  private void updateProjectLevelRoles(String accountId) {
    int page = 0;
    while (true) {
      Pageable pageRequest = PageRequest.of(page, PAGE_SIZE);
      Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier).is(accountId);
      Page<Project> projects = projectService.list(criteria, pageRequest);
      if (isEmpty(projects) || isEmpty(projects.getContent())) {
        break;
      }
      for (Project project : projects.getContent()) {
        addEnvironmentRollbackPermissionToRoles(accountId, project.getOrgIdentifier(), project.getIdentifier());
      }
      page++;
    }
    log.info(MIGRATION_LOG, PROJECT, accountId);
  }

  /*
   Update Roles for each org in given account
 */
  private void updateOrgLevelRoles(String accountId) {
    int page = 0;
    while (true) {
      Pageable pageRequest = PageRequest.of(page, PAGE_SIZE);
      Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier).is(accountId);
      Page<Organization> orgs = organizationService.list(criteria, pageRequest);
      if (isEmpty(orgs) || isEmpty(orgs.getContent())) {
        break;
      }
      for (Organization organization : orgs.getContent()) {
        addEnvironmentRollbackPermissionToRoles(accountId, organization.getIdentifier(), null);
      }
      page++;
    }
    log.info(MIGRATION_LOG, ORG, accountId);
  }

  private void addEnvironmentRollbackPermissionToRoles(String accountId, String orgId, String projectId) {
    PageResponse<RoleResponseDTO> rolesResponse;
    int page = 0;
    while (true) {
      try {
        rolesResponse = getResponse(accessControlClient.getRoles(page, PAGE_SIZE, accountId, orgId, projectId, null));
        if (isNull(rolesResponse) || isEmpty(rolesResponse.getContent())) {
          break;
        }
      } catch (Exception ex) {
        throw new UnexpectedException("Error in fetching roles for accountIdentifier " + accountId);
      }

      List<RoleResponseDTO> roleResponseDTOList = rolesResponse.getContent();

      for (RoleResponseDTO roleResponseDTO : roleResponseDTOList) {
        RoleDTO roleDTO = roleResponseDTO.getRole();
        boolean isHarnessManaged = roleResponseDTO.isHarnessManaged();
        Set<String> rolePermission = roleDTO.getPermissions();

        if (Boolean.FALSE.equals(isHarnessManaged) && !roleDTO.getIdentifier().startsWith("_")) {
          boolean permissionChanged = updatePermission(rolePermission);
          if (permissionChanged) {
            try {
              getResponse(
                  accessControlClient.updateRole(roleDTO.getIdentifier(), accountId, orgId, projectId, roleDTO));
            } catch (InvalidRequestException exception) {
              log.info(String.format("Update Roles failed for %s", roleResponseDTO.getRole().getIdentifier()));
            }
          }
        }
      }
      page++;
    }
  }

  /*
  Update RoleDTO - if role has pipeline execute permission then
  add Env rollback permission
   */

  private boolean updatePermission(Set<String> rolePermissions) {
    if (rolePermissions.contains(EXECUTOR_PERMISSION) && !rolePermissions.contains(ROLLBACK_PERMISSION)) {
      rolePermissions.add(ROLLBACK_PERMISSION);
      return true;
    }
    return false;
  }
}
