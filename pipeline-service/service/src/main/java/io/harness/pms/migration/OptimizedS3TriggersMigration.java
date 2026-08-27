/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.beans.FeatureName.CDS_OPTIMIZE_S3_TRIGGER_POLLING_TASK;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.AmazonS3RegistrySpec;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
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
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class OptimizedS3TriggersMigration implements Runnable {
  private static final String DEBUG_MESSAGE = "S3TriggersMigration: ";
  private static final String LOCK_NAME = "S3TriggersMigration";
  @Inject private AccountUtils accountUtils;
  @Inject @Named("triggersMigrationCache") private Cache<String, Boolean> eventsCache;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private NGTriggerService ngTriggerService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public void run() {
    log.info("{} started...", DEBUG_MESSAGE);
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info("{} failed to acquire lock", DEBUG_MESSAGE);
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
        execute();
      } catch (Exception ex) {
        log.error("{} unexpected error occurred while Setting SecurityContext", DEBUG_MESSAGE, ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception ex) {
      log.error("{} failed to acquire lock", DEBUG_MESSAGE, ex);
    }
  }

  void execute() {
    Set<String> targetAccounts = getAccountsForFFEnabled();
    log.info("{} Number of accounts: {}", DEBUG_MESSAGE, targetAccounts.size());
    if (EmptyPredicate.isEmpty(targetAccounts)) {
      return;
    }
    for (String accountId : targetAccounts) {
      if (eventsCache.containsKey(accountId)) {
        log.info("{} Migration Already done for the account {}", DEBUG_MESSAGE, accountId);
      } else {
        try {
          log.info("{} Starting migration for account {}", DEBUG_MESSAGE, accountId);
          resetS3Triggers(accountId);
          eventsCache.put(accountId, true);
        } catch (Exception ex) {
          log.error("{} Migration failed for the account {} with exception {}", DEBUG_MESSAGE, accountId,
              ex.getMessage(), ex);
        }
      }
    }
  }

  private void resetS3Triggers(String accountId) {
    Criteria criteria = TriggerFilterHelper.getCriteriaForTogglingPollingTriggersByBuildSourceType(
        accountId, null, null, NGTriggerType.ARTIFACT, AmazonS3RegistrySpec.class.getName());
    ngTriggerService.resetPollingTriggers(criteria, true, accountId);
  }

  private Set<String> getAccountsForFFEnabled() {
    try {
      List<String> accountIds = accountUtils.getAllAccountIds();
      return accountIds.stream()
          .filter(accountId -> featureFlagService.isEnabled(CDS_OPTIMIZE_S3_TRIGGER_POLLING_TASK, accountId))
          .collect(Collectors.toSet());
    } catch (Exception ex) {
      log.error(
          "{} Failed to filter accounts for FF {}", DEBUG_MESSAGE, CDS_OPTIMIZE_S3_TRIGGER_POLLING_TASK.name(), ex);
    }
    return Collections.emptySet();
  }
}
