/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

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
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineAbortPermissionMigration extends RoleMigration implements Runnable {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PersistentLocker persistentLocker;
  private static final String LOCK_NAME = "PipelineAbortPermissionMigrationLock";
  @Inject
  @Named("pipelineAbortPermissionMigrationCache")
  private Cache<String, Boolean> pipelineAbortPermissionMigrationCache;
  @Inject private AccountUtils accountUtils;

  @Override
  public void run() {
    log.info("PipelineAbortPermissionMigrationJob: started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info("PipelineAbortPermissionMigrationJob: failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
        execute();
      } catch (Exception ex) {
        log.error("PipelineAbortPermissionMigrationJob: unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception ex) {
      log.warn("PipelineAbortPermissionMigrationJob: failed to acquire lock", ex);
    }
  }

  void execute() {
    Set<String> targetAccounts = getAccountsWithFFEnabled();
    log.info("Account Size: " + targetAccounts.size());
    if (EmptyPredicate.isEmpty(targetAccounts)) {
      return;
    }
    for (String accountId : targetAccounts) {
      if (pipelineAbortPermissionMigrationCache.containsKey(accountId)) {
        log.info("Migration Already done for the account " + accountId);
      } else {
        try {
          super.updateRoles(accountId);
          pipelineAbortPermissionMigrationCache.put(accountId, true);
        } catch (Exception ex) {
          log.warn("Migration failed for the account " + accountId);
        }
      }
    }
  }

  public Set<String> getAccountsWithFFEnabled() {
    try {
      List<String> accountIds = accountUtils.getAllAccountIds();
      return accountIds.stream()
          .filter(accountId
              -> featureFlagService.isEnabled(FeatureName.CDS_PIPELINE_ABORT_RBAC_PERMISSION_MIGRATION, accountId))
          .collect(Collectors.toSet());
    } catch (Exception ex) {
      log.error("Failed to filter accounts for FF CDS_PIPELINE_ABORT_RBAC_PERMISSION_MIGRATION");
    }
    return Collections.emptySet();
  }

  RoleDTO updateRoleDto(RoleDTO roleDTO) {
    if (roleDTO.getPermissions().contains("core_pipeline_execute")) {
      roleDTO.getPermissions().add("core_pipeline_abort");
    }
    return roleDTO;
  }
}
