/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.TIER_GROUP_LOCK_FORMAT;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.TIER_GROUP_LOCK_TIMEOUT_MINUTES;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.TIER_GROUP_LOCK_WAIT_TIMEOUT_SECONDS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class TierGroupLockHelper {
  private final ResourceLocker resourceLocker;

  public <T> T executeWithTierGroupLock(String accountIdentifier, String tierGroupIdentifier, Supplier<T> supplier) {
    String lockName = String.format(TIER_GROUP_LOCK_FORMAT, accountIdentifier, tierGroupIdentifier);
    AcquiredLock lock =
        resourceLocker.acquireLock(lockName, TIER_GROUP_LOCK_TIMEOUT_MINUTES, TIER_GROUP_LOCK_WAIT_TIMEOUT_SECONDS);
    if (lock == null) {
      throw new InvalidRequestException(
          String.format("Tier group '%s' is currently being updated. Please try again.", tierGroupIdentifier));
    }
    try {
      return supplier.get();
    } finally {
      resourceLocker.releaseLock(lock);
    }
  }

  public void executeWithTierGroupLock(String accountIdentifier, String tierGroupIdentifier, Runnable runnable) {
    executeWithTierGroupLock(accountIdentifier, tierGroupIdentifier, () -> {
      runnable.run();
      return null;
    });
  }
}
