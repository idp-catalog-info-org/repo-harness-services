/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogOrgCache {
  static final String CACHE_NAME = "idp-catalog-org-cache-v2";
  private static final String KEY_SEPARATOR = ":";
  private final RedissonClient redisson;

  @Inject
  public CatalogOrgCache(@Named("lock") RedisConfig redisConfig) {
    redisson = RedissonClientFactory.getClient(redisConfig);
  }

  public CachedOrgInfo get(String accountIdentifier, String orgIdentifier) {
    try {
      RMapCache<String, CachedOrgInfo> cache = redisson.getMapCache(CACHE_NAME);
      return cache.get(buildKey(accountIdentifier, orgIdentifier));
    } catch (Exception ex) {
      log.warn("Failed to get org from cache for account={}, orgId={}. Error={}", accountIdentifier, orgIdentifier,
          ex.getMessage(), ex);
      return null;
    }
  }

  public Map<String, CachedOrgInfo> getAll(String accountIdentifier, Set<String> orgIdentifiers) {
    Map<String, CachedOrgInfo> result = new HashMap<>();
    try {
      Map<String, String> cacheKeyToOrgIdentifier = new HashMap<>();
      for (String orgIdentifier : orgIdentifiers) {
        cacheKeyToOrgIdentifier.put(buildKey(accountIdentifier, orgIdentifier), orgIdentifier);
      }
      RMapCache<String, CachedOrgInfo> cache = redisson.getMapCache(CACHE_NAME);
      Map<String, CachedOrgInfo> cached = cache.getAll(cacheKeyToOrgIdentifier.keySet());
      for (Map.Entry<String, CachedOrgInfo> entry : cached.entrySet()) {
        result.put(cacheKeyToOrgIdentifier.get(entry.getKey()), entry.getValue());
      }
    } catch (Exception ex) {
      log.warn("Failed to get orgs from cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    }
    return result;
  }

  public void put(String accountIdentifier, String orgIdentifier, CachedOrgInfo info) {
    try {
      RMapCache<String, CachedOrgInfo> cache = redisson.getMapCache(CACHE_NAME);
      cache.put(buildKey(accountIdentifier, orgIdentifier), info);
    } catch (Exception ex) {
      log.warn("Failed to put org in cache for account={}, orgId={}. Error={}", accountIdentifier, orgIdentifier,
          ex.getMessage(), ex);
    }
  }

  public void putAll(String accountIdentifier, Map<String, CachedOrgInfo> entries) {
    try {
      Map<String, CachedOrgInfo> accountScopedEntries = new HashMap<>();
      for (Map.Entry<String, CachedOrgInfo> entry : entries.entrySet()) {
        accountScopedEntries.put(buildKey(accountIdentifier, entry.getKey()), entry.getValue());
      }
      RMapCache<String, CachedOrgInfo> cache = redisson.getMapCache(CACHE_NAME);
      cache.putAll(accountScopedEntries);
    } catch (Exception ex) {
      log.warn("Failed to putAll orgs in cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    }
  }

  public static String buildKey(String accountIdentifier, String orgIdentifier) {
    return accountIdentifier + KEY_SEPARATOR + orgIdentifier;
  }
}
