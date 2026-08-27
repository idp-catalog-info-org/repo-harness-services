/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.ThreadLocalRandom;
import javax.cache.Cache;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the orchestrationLogCache expired-entry listener with retries.
 * Redisson subscription-lock timeouts during cold start are often transient under
 * multi-pod Redis pub/sub contention; retrying avoids a fatal Guice failure on first attempt.
 *
 * <p>Retries can be disabled via config {@code shouldRetryExpiredListenerRegistration} /
 * env {@code SHOULD_RETRY_ORCHESTRATION_LOG_CACHE_LISTENER_REGISTRATION=false} for rollback.
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationLogCacheRegistrar {
  static final int MAX_ATTEMPTS = 3;
  static final long BASE_BACKOFF_MS = 1000L;

  public void registerExpiredListener(
      Cache<String, Long> cache, OrchestrationLogCacheListener orchestrationLogCacheListener, boolean shouldRetry) {
    int maxAttempts = shouldRetry ? MAX_ATTEMPTS : 1;
    if (!shouldRetry) {
      log.info("orchestrationLogCache expired-listener registration retries disabled; using single attempt");
    }
    registerExpiredListener(cache, orchestrationLogCacheListener, maxAttempts, BASE_BACKOFF_MS);
  }

  @VisibleForTesting
  void registerExpiredListener(Cache<String, Long> cache, OrchestrationLogCacheListener orchestrationLogCacheListener,
      int maxAttempts, long baseBackoffMs) {
    MutableCacheEntryListenerConfiguration<String, Long> listenerConfiguration =
        new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(orchestrationLogCacheListener),
            FactoryBuilder.factoryOf(orchestrationLogCacheListener), false, false);

    RuntimeException lastException = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        cache.registerCacheEntryListener(listenerConfiguration);
        if (attempt > 1) {
          log.info("Registered orchestrationLogCache expired listener on attempt {}/{}", attempt, maxAttempts);
        }
        return;
      } catch (RuntimeException e) {
        lastException = e;
        if (attempt == maxAttempts) {
          log.error("Failed to register orchestrationLogCache expired listener after {} attempts", maxAttempts, e);
          throw e;
        }
        long backoffMs = computeBackoffMs(attempt, baseBackoffMs);
        log.warn("Failed to register orchestrationLogCache expired listener (attempt {}/{}), retrying in {}ms: {}",
            attempt, maxAttempts, backoffMs, e.getMessage());
        sleep(ofMillis(backoffMs));
      }
    }
    throw lastException;
  }

  @VisibleForTesting
  long computeBackoffMs(int attempt, long baseBackoffMs) {
    long exponential = baseBackoffMs * (1L << (attempt - 1));
    long jitter = ThreadLocalRandom.current().nextLong(baseBackoffMs + 1);
    return exponential + jitter;
  }
}
