/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.idp.catalog.cache.CatalogProjectCache.CACHE_NAME;
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

public class CatalogProjectCacheTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "proj1";
  private static final String PROJECT_KEY = "org1:proj1";
  private static final String CACHE_KEY = "account1:org1:proj1";

  private AutoCloseable openMocks;
  @Mock private RedisConfig redisConfig;
  private RedissonClient redissonClient;
  @SuppressWarnings("rawtypes") private RMapCache cache;
  private MockedStatic<RedissonClientFactory> redissonClientFactoryMock;
  private CatalogProjectCache catalogProjectCache;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    redissonClientFactoryMock = mockStatic(RedissonClientFactory.class);
    redissonClient = mock(RedissonClient.class);
    cache = mock(RMapCache.class);

    when(RedissonClientFactory.getClient(any())).thenReturn(redissonClient);
    when(redissonClient.getMapCache(CACHE_NAME)).thenReturn(cache);

    catalogProjectCache = new CatalogProjectCache(redisConfig);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_Success() {
    CachedProjectInfo expectedInfo =
        CachedProjectInfo.builder().identifier(PROJECT_ID).orgIdentifier(ORG_ID).name("Project One").build();

    when(cache.get(CACHE_KEY)).thenReturn(expectedInfo);

    CachedProjectInfo result = catalogProjectCache.get(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertEquals(expectedInfo, result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_CacheMiss() {
    when(cache.get(CACHE_KEY)).thenReturn(null);

    CachedProjectInfo result = catalogProjectCache.get(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_ExceptionHandling() {
    when(cache.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis connection failed"));

    CachedProjectInfo result = catalogProjectCache.get(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAll_Success() {
    Set<String> keys = new HashSet<>();
    keys.add("org1:proj1");
    keys.add("org1:proj2");

    Set<String> cacheKeys = new HashSet<>();
    cacheKeys.add("account1:org1:proj1");
    cacheKeys.add("account1:org1:proj2");

    Map<String, CachedProjectInfo> cachedMap = new HashMap<>();
    cachedMap.put("account1:org1:proj1",
        CachedProjectInfo.builder().identifier("proj1").orgIdentifier("org1").name("Project One").build());
    cachedMap.put("account1:org1:proj2",
        CachedProjectInfo.builder().identifier("proj2").orgIdentifier("org1").name("Project Two").build());

    when(cache.getAll(cacheKeys)).thenReturn(cachedMap);

    Map<String, CachedProjectInfo> result = catalogProjectCache.getAll(ACCOUNT_ID, keys);

    assertEquals(2, result.size());
    assertEquals("Project One", result.get("org1:proj1").getName());
    assertEquals("Project Two", result.get("org1:proj2").getName());
    verify(cache).getAll(cacheKeys);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAll_ExceptionHandling() {
    Set<String> keys = new HashSet<>();
    keys.add(PROJECT_KEY);

    Set<String> cacheKeys = new HashSet<>();
    cacheKeys.add(CACHE_KEY);

    when(cache.getAll(cacheKeys)).thenThrow(new RuntimeException("Redis connection failed"));

    Map<String, CachedProjectInfo> result = catalogProjectCache.getAll(ACCOUNT_ID, keys);

    assertTrue(result.isEmpty());
    verify(cache).getAll(cacheKeys);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_Success() {
    CachedProjectInfo info =
        CachedProjectInfo.builder().identifier(PROJECT_ID).orgIdentifier(ORG_ID).name("Project One").build();

    catalogProjectCache.put(ACCOUNT_ID, ORG_ID, PROJECT_ID, info);

    verify(cache).put(CACHE_KEY, info);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_ExceptionHandling() {
    CachedProjectInfo info =
        CachedProjectInfo.builder().identifier(PROJECT_ID).orgIdentifier(ORG_ID).name("Project One").build();
    when(cache.put(CACHE_KEY, info)).thenThrow(new RuntimeException("Redis write failed"));

    catalogProjectCache.put(ACCOUNT_ID, ORG_ID, PROJECT_ID, info);

    verify(cache).put(CACHE_KEY, info);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPutAll_Success() {
    Map<String, CachedProjectInfo> entries = new HashMap<>();
    entries.put("org1:proj1",
        CachedProjectInfo.builder().identifier("proj1").orgIdentifier("org1").name("Project One").build());
    entries.put("org1:proj2",
        CachedProjectInfo.builder().identifier("proj2").orgIdentifier("org1").name("Project Two").build());

    Map<String, CachedProjectInfo> accountScopedEntries = new HashMap<>();
    accountScopedEntries.put("account1:org1:proj1", entries.get("org1:proj1"));
    accountScopedEntries.put("account1:org1:proj2", entries.get("org1:proj2"));

    catalogProjectCache.putAll(ACCOUNT_ID, entries);

    verify(cache).putAll(accountScopedEntries);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPutAll_ExceptionHandling() {
    Map<String, CachedProjectInfo> entries = new HashMap<>();
    entries.put("org1:proj1",
        CachedProjectInfo.builder().identifier("proj1").orgIdentifier("org1").name("Project One").build());

    Map<String, CachedProjectInfo> accountScopedEntries = new HashMap<>();
    accountScopedEntries.put(CACHE_KEY, entries.get("org1:proj1"));

    doThrow(new RuntimeException("Redis write failed")).when(cache).putAll(accountScopedEntries);

    catalogProjectCache.putAll(ACCOUNT_ID, entries);

    verify(cache).putAll(accountScopedEntries);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildKey() {
    String key = CatalogProjectCache.buildKey("account1", "org1", "proj1");
    assertEquals("account1:org1:proj1", key);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildProjectKey() {
    String key = CatalogProjectCache.buildProjectKey("org1", "proj1");
    assertEquals("org1:proj1", key);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
    redissonClientFactoryMock.close();
  }
}
