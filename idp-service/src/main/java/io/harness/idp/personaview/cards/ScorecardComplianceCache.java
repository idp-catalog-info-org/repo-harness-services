/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.cards;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Singleton;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * In-process TTL cache of Gold/Silver/Bronze counts for a hierarchy node, used by the
 * Comparison-by-Hierarchy card to avoid re-running the expensive latest-scores aggregation on every load.
 *
 * <p>Counts are keyed by {@code accountIdentifier | scope | nodeKey} and expire 10 minutes after write,
 * which matches how often scorecard scores are recomputed closely enough for a leadership dashboard. The
 * value is independent of the requested aggregation-rule columns, so it is reused across card loads
 * regardless of column selection. Cached values are immutable.
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class ScorecardComplianceCache {
  private static final long MAX_CACHE_SIZE = 10000;
  private static final long EXPIRE_AFTER_WRITE_MINUTES = 10;
  private static final String KEY_SEPARATOR = "|";

  private final LoadingCache<String, Counts> cache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHE_SIZE)
          .expireAfterWrite(EXPIRE_AFTER_WRITE_MINUTES, TimeUnit.MINUTES)
          .build(CacheLoader.from(key -> {
            // Loading is always done through getOrCompute's Callable; this is never reached.
            throw new UnsupportedOperationException("Direct cache loading not supported. Use getOrCompute.");
          }));

  public Counts getOrCompute(String accountIdentifier, String scope, String nodeKey, Callable<Counts> loader) {
    String cacheKey =
        String.join(KEY_SEPARATOR, nullToEmpty(accountIdentifier), nullToEmpty(scope), nullToEmpty(nodeKey));
    try {
      return cache.get(cacheKey, loader);
    } catch (ExecutionException e) {
      log.warn("Error computing scorecard compliance counts for key {}", cacheKey, e);
      try {
        return loader.call();
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to compute scorecard compliance counts", ex);
      }
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  @Value
  public static class Counts {
    int gold;
    int silver;
    int bronze;
  }
}
