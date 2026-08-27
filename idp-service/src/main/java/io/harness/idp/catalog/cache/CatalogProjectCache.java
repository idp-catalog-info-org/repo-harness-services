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
public class CatalogProjectCache {
  static final String CACHE_NAME = "idp-catalog-project-cache-v2";
  private static final String KEY_SEPARATOR = ":";
  private final RedissonClient redisson;

  @Inject
  public CatalogProjectCache(@Named("lock") RedisConfig redisConfig) {
    redisson = RedissonClientFactory.getClient(redisConfig);
  }

  public CachedProjectInfo get(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      RMapCache<String, CachedProjectInfo> cache = redisson.getMapCache(CACHE_NAME);
      return cache.get(buildKey(accountIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception ex) {
      log.warn("Failed to get project from cache for account={}, org={}, project={}. Error={}", accountIdentifier,
          orgIdentifier, projectIdentifier, ex.getMessage(), ex);
      return null;
    }
  }

  public Map<String, CachedProjectInfo> getAll(String accountIdentifier, Set<String> keys) {
    Map<String, CachedProjectInfo> result = new HashMap<>();
    try {
      Map<String, String> cacheKeyToProjectKey = new HashMap<>();
      for (String key : keys) {
        cacheKeyToProjectKey.put(buildKeyForProjectKey(accountIdentifier, key), key);
      }
      RMapCache<String, CachedProjectInfo> cache = redisson.getMapCache(CACHE_NAME);
      Map<String, CachedProjectInfo> cached = cache.getAll(cacheKeyToProjectKey.keySet());
      for (Map.Entry<String, CachedProjectInfo> entry : cached.entrySet()) {
        result.put(cacheKeyToProjectKey.get(entry.getKey()), entry.getValue());
      }
    } catch (Exception ex) {
      log.warn("Failed to get projects from cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    }
    return result;
  }

  public void put(String accountIdentifier, String orgIdentifier, String projectIdentifier, CachedProjectInfo info) {
    try {
      RMapCache<String, CachedProjectInfo> cache = redisson.getMapCache(CACHE_NAME);
      cache.put(buildKey(accountIdentifier, orgIdentifier, projectIdentifier), info);
    } catch (Exception ex) {
      log.warn("Failed to put project in cache for account={}, org={}, project={}. Error={}", accountIdentifier,
          orgIdentifier, projectIdentifier, ex.getMessage(), ex);
    }
  }

  public void putAll(String accountIdentifier, Map<String, CachedProjectInfo> entries) {
    try {
      Map<String, CachedProjectInfo> accountScopedEntries = new HashMap<>();
      for (Map.Entry<String, CachedProjectInfo> entry : entries.entrySet()) {
        accountScopedEntries.put(buildKeyForProjectKey(accountIdentifier, entry.getKey()), entry.getValue());
      }
      RMapCache<String, CachedProjectInfo> cache = redisson.getMapCache(CACHE_NAME);
      cache.putAll(accountScopedEntries);
    } catch (Exception ex) {
      log.warn("Failed to putAll projects in cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    }
  }

  public static String buildKey(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return accountIdentifier + KEY_SEPARATOR + orgIdentifier + KEY_SEPARATOR + projectIdentifier;
  }

  public static String buildProjectKey(String orgIdentifier, String projectIdentifier) {
    return orgIdentifier + KEY_SEPARATOR + projectIdentifier;
  }

  private static String buildKeyForProjectKey(String accountIdentifier, String projectKey) {
    return accountIdentifier + KEY_SEPARATOR + projectKey;
  }
}
