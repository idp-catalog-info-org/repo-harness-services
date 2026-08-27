/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.redisson.client.RedisException;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationLogCacheRegistrarTest extends CategoryTest {
  private static final long FAST_BACKOFF_MS = 1L;

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRegisterExpiredListenerSucceedsOnFirstAttempt() {
    Cache<String, Long> cache = mock(Cache.class);
    OrchestrationLogCacheListener listener = mock(OrchestrationLogCacheListener.class);

    OrchestrationLogCacheRegistrar.registerExpiredListener(cache, listener, 3, FAST_BACKOFF_MS);

    verify(cache, times(1)).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRegisterExpiredListenerRetriesThenSucceeds() {
    Cache<String, Long> cache = mock(Cache.class);
    OrchestrationLogCacheListener listener = mock(OrchestrationLogCacheListener.class);
    doThrow(new RedisException("Unable to acquire subscription lock after 7500ms"))
        .doNothing()
        .when(cache)
        .registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));

    OrchestrationLogCacheRegistrar.registerExpiredListener(cache, listener, 3, FAST_BACKOFF_MS);

    verify(cache, times(2)).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRegisterExpiredListenerFailsAfterMaxAttempts() {
    Cache<String, Long> cache = mock(Cache.class);
    OrchestrationLogCacheListener listener = mock(OrchestrationLogCacheListener.class);
    RedisException timeoutException = new RedisException("Unable to acquire subscription lock after 7500ms");
    doThrow(timeoutException).when(cache).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));

    assertThatThrownBy(
        () -> OrchestrationLogCacheRegistrar.registerExpiredListener(cache, listener, 3, FAST_BACKOFF_MS))
        .isSameAs(timeoutException);

    verify(cache, times(3)).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRegisterExpiredListenerRetriesDisabledDoesSingleAttempt() {
    Cache<String, Long> cache = mock(Cache.class);
    OrchestrationLogCacheListener listener = mock(OrchestrationLogCacheListener.class);
    RedisException timeoutException = new RedisException("Unable to acquire subscription lock after 7500ms");
    doThrow(timeoutException).when(cache).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));

    assertThatThrownBy(() -> OrchestrationLogCacheRegistrar.registerExpiredListener(cache, listener, false))
        .isSameAs(timeoutException);

    verify(cache, times(1)).registerCacheEntryListener(any(CacheEntryListenerConfiguration.class));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testComputeBackoffMsIncludesExponentialComponentAndJitter() {
    long baseBackoffMs = 1000L;
    long backoffAttempt1 = OrchestrationLogCacheRegistrar.computeBackoffMs(1, baseBackoffMs);
    long backoffAttempt2 = OrchestrationLogCacheRegistrar.computeBackoffMs(2, baseBackoffMs);

    assertThat(backoffAttempt1).isBetween(baseBackoffMs, 2 * baseBackoffMs);
    assertThat(backoffAttempt2).isBetween(2 * baseBackoffMs, 3 * baseBackoffMs);
  }
}
