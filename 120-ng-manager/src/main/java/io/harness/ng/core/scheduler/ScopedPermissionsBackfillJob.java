/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.config.ScopedPermissionsBackfillConfig;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.entities.Token;
import io.harness.repositories.ng.core.spring.TokenRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Recurring job that backfills the new {@code permissions} list on legacy scoped-token
 * entries. An entry is considered "legacy" when its deprecated single {@code permission}
 * field is set but the {@code permissions} list is missing or empty. The job fetches
 * tokens in batches of {@link ScopedPermissionsBackfillConfig#getBatchSize()} and keeps
 * draining until the repository query reports no more matches, or a batch yields zero
 * modifications (progress guard — avoids spinning when every token in the batch fails).
 *
 * <p>Idempotent: once an entry has its {@code permissions} list populated the repository
 * query will not match the token again, so re-running the job on already-migrated data is
 * a no-op. The job can therefore safely run forever on a schedule.
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ScopedPermissionsBackfillJob implements Runnable {
  private static final String LOCK_NAME = "ScopedPermissionsBackfillJobLock";

  private final TokenRepository tokenRepository;
  private final PersistentLocker persistentLocker;
  private final ScopedPermissionsBackfillConfig config;

  @Inject
  public ScopedPermissionsBackfillJob(TokenRepository tokenRepository, PersistentLocker persistentLocker,
      @Named("scopedPermissionsBackfillConfig") ScopedPermissionsBackfillConfig config) {
    this.tokenRepository = tokenRepository;
    this.persistentLocker = persistentLocker;
    this.config = config;
  }

  @Override
  public void run() {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.debug("ScopedPermissionsBackfillJob: could not acquire lock; skipping execution");
        return;
      }

      int batchSize = config.getBatchSize();
      int totalProcessed = 0;
      int totalModified = 0;
      while (true) {
        List<Token> batch = tokenRepository.findScopedTokensNeedingPermissionsBackfill(batchSize);
        if (batch.isEmpty()) {
          break;
        }
        int batchModified = 0;
        for (Token token : batch) {
          try {
            if (processToken(token)) {
              batchModified++;
              totalModified++;
            }
            totalProcessed++;
          } catch (Exception ex) {
            log.error("ScopedPermissionsBackfillJob: backfill failed for token {}", token.getUuid(), ex);
          }
        }
        // A matched batch that saves nothing means every token failed (or was unexpectedly a
        // no-op). Breaking avoids an infinite loop: failed docs keep matching the query.
        if (batchModified == 0) {
          log.warn("ScopedPermissionsBackfillJob: no progress on batch of {}; ending run", batch.size());
          break;
        }
        if (batch.size() < batchSize) {
          break;
        }
      }
      if (totalProcessed > 0) {
        log.info(
            "ScopedPermissionsBackfillJob: processed {} tokens, modified {} this run", totalProcessed, totalModified);
      }
    } catch (Exception ex) {
      log.error("ScopedPermissionsBackfillJob: failed while acquiring lock or running", ex);
    }
  }

  /**
   * Normalize legacy entries on a single token. For each {@link ScopedResourcePermission}
   * entry where {@code permission} is set but {@code permissions} is empty, set
   * {@code permissions = [permission]}. The deprecated field is intentionally left in place
   * for backward compatibility during rollout; it is removed in a follow-up cleanup PR.
   *
   * @return {@code true} if any entry was modified and the token was saved
   */
  @VisibleForTesting
  boolean processToken(Token token) {
    if (isEmpty(token.getScopedResourcePermissions())) {
      return false;
    }
    boolean modified = false;
    for (ScopedResourcePermission entry : token.getScopedResourcePermissions()) {
      if (isNotEmpty(entry.getPermission()) && isEmpty(entry.getPermissions())) {
        entry.setPermissions(new ArrayList<>(List.of(entry.getPermission())));
        modified = true;
      }
    }
    if (modified) {
      tokenRepository.save(token);
    }
    return modified;
  }
}
