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
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogRbacPermissionsCache {
  static final String CACHE_NAME = "idp-catalog-rbac-permissions-cache";
  static final long EXPIRY_IN_MINUTES = 2;
  private static final String KEY_SEPARATOR = ":";
  private final RedissonClient redisson;

  @Inject
  public CatalogRbacPermissionsCache(@Named("lock") RedisConfig redisConfig) {
    redisson = RedissonClientFactory.getClient(redisConfig);
  }

  public RbacPermissions get(String userId, String accountIdentifier) {
    try {
      RMapCache<String, RbacPermissions> cache = redisson.getMapCache(CACHE_NAME);
      return cache.get(buildKey(userId, accountIdentifier));
    } catch (Exception ex) {
      log.warn("Failed to get RBAC permissions from cache for user={}, account={}. Error={}", userId, accountIdentifier,
          ex.getMessage(), ex);
      return null;
    }
  }

  public void put(String userId, String accountIdentifier, RbacPermissions permissions) {
    try {
      RMapCache<String, RbacPermissions> cache = redisson.getMapCache(CACHE_NAME);
      cache.put(buildKey(userId, accountIdentifier), permissions, EXPIRY_IN_MINUTES, TimeUnit.MINUTES);
    } catch (Exception ex) {
      log.warn("Failed to put RBAC permissions in cache for user={}, account={}. Error={}", userId, accountIdentifier,
          ex.getMessage(), ex);
    }
  }

  public void remove(String userId, String accountIdentifier) {
    try {
      RMapCache<String, RbacPermissions> cache = redisson.getMapCache(CACHE_NAME);
      cache.remove(buildKey(userId, accountIdentifier));
    } catch (Exception ex) {
      log.warn("Failed to remove RBAC permissions from cache for user={}, account={}. Error={}", userId,
          accountIdentifier, ex.getMessage(), ex);
    }
  }

  private String buildKey(String userId, String accountIdentifier) {
    return userId + KEY_SEPARATOR + accountIdentifier;
  }
}
