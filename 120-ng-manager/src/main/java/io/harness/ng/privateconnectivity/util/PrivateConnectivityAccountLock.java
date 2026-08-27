/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.PersistentLockException;
import io.harness.exception.UnexpectedException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityConflictException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Serializes all durable private-connectivity mutations for one account.
 *
 * <p>Background workers use {@link #tryRun} so they cannot race an API operation that saves the
 * same account document.
 */
@OwnedBy(CI)
@Singleton
@Slf4j
public class PrivateConnectivityAccountLock {
  private static final String LOCK_PREFIX = "PRIVATE_CONNECTIVITY_OPERATION/";
  private static final Duration ACQUISITION_ATTEMPT_TIMEOUT = Duration.ofMillis(100);
  private static final long WAIT_INTERVAL_MILLIS = 100L;

  private final PersistentLocker persistentLocker;

  @Inject
  public PrivateConnectivityAccountLock(PersistentLocker persistentLocker) {
    this.persistentLocker = persistentLocker;
  }

  public <T> T executeOrConflict(String accountIdentifier, Supplier<T> operation) {
    return executeWithWaitOrConflict(accountIdentifier, Duration.ZERO, operation);
  }

  public <T> T executeWithWaitOrConflict(String accountIdentifier, Duration waitTimeout, Supplier<T> operation) {
    long waitNanos = waitTimeout == null ? 0L : Math.max(0L, waitTimeout.toNanos());
    long deadline = System.nanoTime() + waitNanos;
    int attempts = 0;
    do {
      attempts++;
      try (AcquiredLock<?> lock = tryAcquire(accountIdentifier)) {
        if (lock != null) {
          log.info("Private Connectivity account lock acquired account={} attempts={} waitTimeoutMs={}",
              accountIdentifier, attempts, waitTimeout == null ? 0 : waitTimeout.toMillis());
          return operation.get();
        }
      }
      if (System.nanoTime() >= deadline) {
        break;
      }
      sleepUntilRetry(deadline);
    } while (true);

    log.info("Private Connectivity account operation conflicted account={} attempts={} waitTimeoutMs={}",
        accountIdentifier, attempts, waitTimeout == null ? 0 : waitTimeout.toMillis());
    throw new PrivateConnectivityConflictException(
        "Another private connectivity operation is in progress for account " + accountIdentifier);
  }

  /**
   * Runs an asynchronous mutation only when the account lock is immediately available.
   *
   * @return true when the operation ran, false when another worker owns the lock.
   */
  public boolean tryRun(String accountIdentifier, Runnable operation) {
    try (AcquiredLock<?> lock = tryAcquire(accountIdentifier)) {
      if (lock == null) {
        return false;
      }
      operation.run();
      return true;
    }
  }

  private AcquiredLock<?> tryAcquire(String accountIdentifier) {
    try {
      return persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(
          LOCK_PREFIX + accountIdentifier, ACQUISITION_ATTEMPT_TIMEOUT);
    } catch (PersistentLockException exception) {
      // RedisPersistentLocker reports ordinary contention with PersistentLockException. Preserve
      // interruption separately; every other locker/infrastructure failure must propagate.
      if (Thread.currentThread().isInterrupted()) {
        throw new UnexpectedException(
            "Interrupted while acquiring a private connectivity account operation lock", exception);
      }
      return null;
    } catch (UnexpectedException exception) {
      // In Redis Sentinel mode, Redisson reports an ordinary acquisition timeout through the
      // async Future as TimeoutException, which RedisPersistentLocker wraps in UnexpectedException.
      // Treat only that exact cause as contention so bounded-wait callers can retry. Preserve all
      // other Redis/infrastructure failures as errors instead of misreporting them as contention.
      if (hasCause(exception, TimeoutException.class)) {
        return null;
      }
      throw exception;
    }
  }

  private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (causeType.isInstance(current)) {
        return true;
      }
    }
    return false;
  }

  private static void sleepUntilRetry(long deadlineNanos) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0L) {
      return;
    }
    long sleepMillis = Math.min(WAIT_INTERVAL_MILLIS, Math.max(1L, Duration.ofNanos(remainingNanos).toMillis()));
    try {
      Thread.sleep(sleepMillis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new UnexpectedException(
          "Interrupted while waiting for a private connectivity account operation", exception);
    }
  }
}
