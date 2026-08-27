/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-through Caffeine cache over {@link FlowGovernorStateStore}. Refreshes the shared
 * {@code "orchestration"} entry asynchronously every 30s so throttled consumers cheaply observe
 * the current mode.
 *
 * <p>The old and new flows are completely segregated: {@link #getState()} is only meaningful when
 * {@link FlowGovernorConfig#isEnabled()} is true. Callers on the vanilla path never construct or
 * inject this cache, so Redis is not touched at all when the config is disabled.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class FlowGovernorStateCache {
  @VisibleForTesting static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);
  private static final String CACHE_KEY = "state";

  private final FlowGovernorStateStore store;
  private final LoadingCache<String, FlowGovernorState> cache;
  /**
   * Non-null only when this instance owns its executor (production path). Injected executors
   * (test path) are the caller's to manage.
   */
  private final ExecutorService ownedRefreshExecutor;

  @Inject
  public FlowGovernorStateCache(FlowGovernorStateStore store) {
    this.store = store;
    this.ownedRefreshExecutor = defaultRefreshExecutor();
    this.cache = Caffeine.newBuilder()
                     .refreshAfterWrite(REFRESH_INTERVAL)
                     .executor(ownedRefreshExecutor)
                     .build(new StoreLoader());
  }

  @VisibleForTesting
  FlowGovernorStateCache(FlowGovernorStateStore store, Executor refreshExecutor) {
    this.store = store;
    this.ownedRefreshExecutor = null;
    this.cache =
        Caffeine.newBuilder().refreshAfterWrite(REFRESH_INTERVAL).executor(refreshExecutor).build(new StoreLoader());
  }

  public FlowGovernorState getState() {
    try {
      FlowGovernorState state = cache.get(CACHE_KEY);
      return state == null ? FlowGovernorState.normal() : state;
    } catch (Exception ex) {
      log.warn("Failed to resolve flow-governor state from cache; falling back to NORMAL.", ex);
      return FlowGovernorState.normal();
    }
  }

  /**
   * Drains the owned refresh executor. Wire into a Dropwizard {@code Managed.stop()} if a
   * clean shutdown is required; otherwise the daemon thread simply exits with the JVM.
   */
  public void shutdown() {
    if (ownedRefreshExecutor == null) {
      return;
    }
    ownedRefreshExecutor.shutdown();
    try {
      if (!ownedRefreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        ownedRefreshExecutor.shutdownNow();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      ownedRefreshExecutor.shutdownNow();
    }
  }

  private static ExecutorService defaultRefreshExecutor() {
    return Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("flow-governor-cache-refresh-%d").setDaemon(true).build());
  }

  private final class StoreLoader implements CacheLoader<String, FlowGovernorState> {
    @Override
    public FlowGovernorState load(String key) {
      return store.get();
    }

    /**
     * Explicit override so background refresh failures are logged. Caffeine's default {@code
     * reload()} delegates to {@code load()} and silently retains the previous value if it throws;
     * without this override an operator would have no signal that the cache had gone stale.
     * {@link FlowGovernorStateStore#get()} today catches Redis exceptions internally, but keeping
     * this belt-and-braces log means a future change there can't quietly break refresh visibility.
     */
    @Override
    public FlowGovernorState reload(String key, FlowGovernorState oldValue) {
      try {
        return store.get();
      } catch (Exception ex) {
        log.warn("Background flow-governor cache refresh failed; retaining stale state.", ex);
        throw ex;
      }
    }
  }
}
