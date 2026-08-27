/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.redis.RedisPersistentLocker;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ResourceLocker {
  RedisPersistentLocker redisLocker;

  public AcquiredLock acquireLock(String lockName) {
    return acquireLock(lockName, 1);
  }
  public AcquiredLock acquireLock(String lockName, long lockTimeoutInMins, long waitTimeoutInSecs) {
    AcquiredLock lock = redisLocker.waitToAcquireLockOptionalWithRetry(
        lockName, Duration.ofMinutes(lockTimeoutInMins), Duration.ofSeconds(waitTimeoutInSecs), 3);
    if (lock == null) {
      log.warn("Lock not acquired for {}, will attempt in next delivery", lockName);
    }
    return lock;
  }
  public AcquiredLock acquireLock(String lockName, long minutes) {
    AcquiredLock lock = redisLocker.tryToAcquireLock(lockName, Duration.ofMinutes(minutes));
    if (lock == null) {
      log.warn("Lock not acquired for {}, will attempt in next delivery", lockName);
    }
    return lock;
  }

  public void releaseLock(AcquiredLock lock) {
    redisLocker.destroy(lock);
    log.debug("Lock released for {}", lock.getLock());
  }
}
