/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.idp.catalog.cache.CatalogOrgCache.CACHE_NAME;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

public class CatalogOrgCacheTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String CACHE_KEY = "account1:org1";

  private AutoCloseable openMocks;
  @Mock private RedisConfig redisConfig;
  private RedissonClient redissonClient;
  @SuppressWarnings("rawtypes") private RMapCache cache;
  private MockedStatic<RedissonClientFactory> redissonClientFactoryMock;
  private CatalogOrgCache catalogOrgCache;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    redissonClientFactoryMock = mockStatic(RedissonClientFactory.class);
    redissonClient = mock(RedissonClient.class);
    cache = mock(RMapCache.class);

    when(RedissonClientFactory.getClient(any())).thenReturn(redissonClient);
    when(redissonClient.getMapCache(CACHE_NAME)).thenReturn(cache);

    catalogOrgCache = new CatalogOrgCache(redisConfig);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_Success() {
    CachedOrgInfo expectedInfo = CachedOrgInfo.builder().identifier(ORG_ID).name("Org One").build();

    when(cache.get(CACHE_KEY)).thenReturn(expectedInfo);

    CachedOrgInfo result = catalogOrgCache.get(ACCOUNT_ID, ORG_ID);

    assertEquals(expectedInfo, result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_CacheMiss() {
    when(cache.get(CACHE_KEY)).thenReturn(null);

    CachedOrgInfo result = catalogOrgCache.get(ACCOUNT_ID, ORG_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_ExceptionHandling() {
    when(cache.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis connection failed"));

    CachedOrgInfo result = catalogOrgCache.get(ACCOUNT_ID, ORG_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAll_Success() {
    Set<String> orgIds = new HashSet<>();
    orgIds.add("org1");
    orgIds.add("org2");

    Set<String> cacheKeys = new HashSet<>();
    cacheKeys.add("account1:org1");
    cacheKeys.add("account1:org2");

    Map<String, CachedOrgInfo> cachedMap = new HashMap<>();
    cachedMap.put("account1:org1", CachedOrgInfo.builder().identifier("org1").name("Org One").build());
    cachedMap.put("account1:org2", CachedOrgInfo.builder().identifier("org2").name("Org Two").build());

    when(cache.getAll(cacheKeys)).thenReturn(cachedMap);

    Map<String, CachedOrgInfo> result = catalogOrgCache.getAll(ACCOUNT_ID, orgIds);

    assertEquals(2, result.size());
    assertEquals("Org One", result.get("org1").getName());
    assertEquals("Org Two", result.get("org2").getName());
    verify(cache).getAll(cacheKeys);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAll_ExceptionHandling() {
    Set<String> orgIds = new HashSet<>();
    orgIds.add("org1");

    Set<String> cacheKeys = new HashSet<>();
    cacheKeys.add(CACHE_KEY);

    when(cache.getAll(cacheKeys)).thenThrow(new RuntimeException("Redis connection failed"));

    Map<String, CachedOrgInfo> result = catalogOrgCache.getAll(ACCOUNT_ID, orgIds);

    assertTrue(result.isEmpty());
    verify(cache).getAll(cacheKeys);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_Success() {
    CachedOrgInfo info = CachedOrgInfo.builder().identifier(ORG_ID).name("Org One").build();

    catalogOrgCache.put(ACCOUNT_ID, ORG_ID, info);

    verify(cache).put(CACHE_KEY, info);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_ExceptionHandling() {
    CachedOrgInfo info = CachedOrgInfo.builder().identifier(ORG_ID).name("Org One").build();
    when(cache.put(CACHE_KEY, info)).thenThrow(new RuntimeException("Redis write failed"));

    catalogOrgCache.put(ACCOUNT_ID, ORG_ID, info);

    verify(cache).put(CACHE_KEY, info);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPutAll_Success() {
    Map<String, CachedOrgInfo> entries = new HashMap<>();
    entries.put("org1", CachedOrgInfo.builder().identifier("org1").name("Org One").build());
    entries.put("org2", CachedOrgInfo.builder().identifier("org2").name("Org Two").build());

    Map<String, CachedOrgInfo> accountScopedEntries = new HashMap<>();
    accountScopedEntries.put("account1:org1", entries.get("org1"));
    accountScopedEntries.put("account1:org2", entries.get("org2"));

    catalogOrgCache.putAll(ACCOUNT_ID, entries);

    verify(cache).putAll(accountScopedEntries);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPutAll_ExceptionHandling() {
    Map<String, CachedOrgInfo> entries = new HashMap<>();
    entries.put("org1", CachedOrgInfo.builder().identifier("org1").name("Org One").build());

    Map<String, CachedOrgInfo> accountScopedEntries = new HashMap<>();
    accountScopedEntries.put(CACHE_KEY, entries.get("org1"));

    doThrow(new RuntimeException("Redis write failed")).when(cache).putAll(accountScopedEntries);

    catalogOrgCache.putAll(ACCOUNT_ID, entries);

    verify(cache).putAll(accountScopedEntries);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildKey() {
    String key = CatalogOrgCache.buildKey("account1", "org1");
    assertEquals("account1:org1", key);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
    redissonClientFactoryMock.close();
  }
}
