/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.beans.FeatureName.CDS_INPUT_SET_MIGRATION;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.project.remote.ProjectClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class InputSetRolesMigration implements Runnable {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PersistentLocker persistentLocker;
  private final String DEBUG_MESSAGE = "InputSetRolesMigrationJob: ";
  private static final String LOCK_NAME = "InputSetRolesMigrationJobLock";
  @Inject private ProjectClient projectClient;
  @Inject private AccessControlAdminClient accessControlClient;
  @Inject @Named("roleMigrationCache") private Cache<String, Boolean> eventsCache;
  @Inject private AccountUtils accountUtils;
  @Inject private RoleResourceMigration roleResourceMigration;

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
  }

  @VisibleForTesting
  void execute() {
    Set<String> targetAccounts = getAccountsForFFEnabled();
    log.info("Account Size: " + targetAccounts.size());
    if (EmptyPredicate.isEmpty(targetAccounts)) {
      return;
    }
    for (String accountId : targetAccounts) {
      if (eventsCache.containsKey(accountId)) {
        log.info("Migration Already done for the account " + accountId);
      } else {
        try {
          updateRoles(accountId);
          roleResourceMigration.updateRoleResource(accountId);
          eventsCache.put(accountId, true);
        } catch (Exception ex) {
          log.error(DEBUG_MESSAGE + " Migration failed for account account: " + accountId, ex);
        }
      }
    }
  }

  /*
  Update Roles for each project in given account
   */
  private void updateRoles(String accountId) {
    List<ProjectResponse> projects;
    int page = 0;
    while (page < 100) {
      projects =
          getResponse(projectClient.listProject(accountId, null, false, null, null, page, 500, null)).getContent();
      if (EmptyPredicate.isEmpty(projects)) {
        break;
      }
      page++;
      for (ProjectResponse projectResponse : projects) {
        updateRolesForProject(accountId, page, projectResponse);
      }
    }
  }

  private void updateRolesForProject(String accountId, int page, ProjectResponse projectResponse) {
    List<RoleResponseDTO> rolesResponse;
    int pageRole = 0;
    while (pageRole < 100) {
      try {
        rolesResponse = getResponse(
            accessControlClient.getRoles(pageRole, 100, accountId, projectResponse.getProject().getOrgIdentifier(),
                projectResponse.getProject().getIdentifier(), null))
                            .getContent();
        if (EmptyPredicate.isEmpty(rolesResponse)) {
          break;
        }
      } catch (Exception ex) {
        throw new UnexpectedException("Error in fetching roles for accountIdentifier " + accountId + " | page " + page);
      }
      pageRole++;
      updateRole(rolesResponse, accountId, projectResponse.getProject().getOrgIdentifier(),
          projectResponse.getProject().getIdentifier());
    }
  }

  private void updateRole(List<RoleResponseDTO> rolesResponse, String accountId, String orgId, String projectId) {
    for (int i = 0; i < rolesResponse.size(); i++) {
      RoleDTO roleDTO = rolesResponse.get(i).getRole();
      int permission_len = roleDTO.getPermissions().size();
      roleDTO = updateRoleDto(roleDTO);
      try {
        if (roleDTO.getPermissions().size() > permission_len) {
          getResponse(accessControlClient.updateRole(roleDTO.getIdentifier(), accountId, orgId, projectId, roleDTO));
        }
      } catch (InvalidRequestException exception) {
        log.info(String.format("Update Roles failed for %s", roleDTO.getIdentifier()));
      }
    }
  }

  /*
  Update RoleDTO - if role has pipeline view/edit/delete permission then
  add input set view/edit/delete permission
   */
  private RoleDTO updateRoleDto(RoleDTO roleDTO) {
    if (roleDTO.getPermissions().contains("core_pipeline_view")
        || roleDTO.getPermissions().contains("core_pipeline_execute")) {
      roleDTO.getPermissions().add("core_inputset_view");
    }

    if (roleDTO.getPermissions().contains("core_pipeline_edit")) {
      roleDTO.getPermissions().add("core_inputset_edit");
    }

    if (roleDTO.getPermissions().contains("core_pipeline_delete")) {
      roleDTO.getPermissions().add("core_inputset_delete");
    }
    return roleDTO;
  }
  private Set<String> getAccountsForFFEnabled() {
    try {
      List<String> accountIds = accountUtils.getAllAccountIds();
      return accountIds.stream()
          .filter(accountId -> featureFlagService.isEnabled(CDS_INPUT_SET_MIGRATION, accountId))
          .collect(Collectors.toSet());
    } catch (Exception ex) {
      log.error("Failed to filter accounts for FF CDS_INPUT_SET_MIGRATION");
    }
    return Collections.emptySet();
  }
}
