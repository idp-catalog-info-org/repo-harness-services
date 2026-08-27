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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogScopeTopologyCache {
  static final String CACHE_NAME = "idp-catalog-scope-topology-cache-v2";
  static final long EXPIRY_IN_MINUTES = 15; // Coz trust issues
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final RMapCache<String, String> cache;

  @Inject

  public CatalogScopeTopologyCache(@Named("lock") RedisConfig redisConfig) {
    RedissonClient redisson = RedissonClientFactory.getClient(redisConfig);
    this.cache = redisson.getMapCache(CACHE_NAME, StringCodec.INSTANCE);
  }

  public ScopeTopology get(String accountIdentifier) {
    try {
      String json = cache.get(accountIdentifier);
      if (json == null) {
        return null;
      }
      return MAPPER.readValue(json, ScopeTopology.class);
    } catch (Exception ex) {
      log.warn(
          "Failed to get scope topology from cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
      return null;
    }
  }

  public void put(String accountIdentifier, ScopeTopology topology) {
    try {
      String json = MAPPER.writeValueAsString(topology);
      cache.put(accountIdentifier, json, EXPIRY_IN_MINUTES, TimeUnit.MINUTES);
    } catch (JsonProcessingException ex) {
      log.warn("Failed to serialize scope topology for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    } catch (Exception ex) {
      log.warn(
          "Failed to put scope topology in cache for account={}. Error={}", accountIdentifier, ex.getMessage(), ex);
    }
  }

  public void remove(String accountIdentifier) {
    try {
      cache.remove(accountIdentifier);
    } catch (Exception ex) {
      log.warn("Failed to remove scope topology from cache for account={}. Error={}", accountIdentifier,
          ex.getMessage(), ex);
    }
  }
}
