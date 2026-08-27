/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.idp.catalog.cache.CatalogRbacPermissionsCache.CACHE_NAME;
import static io.harness.idp.catalog.cache.CatalogRbacPermissionsCache.EXPIRY_IN_MINUTES;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

public class CatalogRbacPermissionsCacheTest extends CategoryTest {
  private static final String USER_ID = "test-user";
  private static final String ACCOUNT_ID = "test-account";
  private static final String CACHE_KEY = USER_ID + ":" + ACCOUNT_ID;

  private AutoCloseable openMocks;
  @Mock private RedisConfig redisConfig;
  private RedissonClient redissonClient;
  @SuppressWarnings("rawtypes") private RMapCache cache;
  private MockedStatic<RedissonClientFactory> redissonClientFactoryMock;
  private CatalogRbacPermissionsCache catalogRbacPermissionsCache;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    redissonClientFactoryMock = mockStatic(RedissonClientFactory.class);
    redissonClient = mock(RedissonClient.class);
    cache = mock(RMapCache.class);

    when(RedissonClientFactory.getClient(any())).thenReturn(redissonClient);
    when(redissonClient.getMapCache(CACHE_NAME)).thenReturn(cache);

    catalogRbacPermissionsCache = new CatalogRbacPermissionsCache(redisConfig);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_Success() {
    Map<String, List<String>> scopePermissions = new HashMap<>();
    scopePermissions.put("VIEW", List.of("scope1", "scope2"));
    RbacPermissions expectedPermissions =
        RbacPermissions.builder().scopePermissions(scopePermissions).allowedEntityRefs(new ArrayList<>()).build();

    when(cache.get(CACHE_KEY)).thenReturn(expectedPermissions);

    RbacPermissions result = catalogRbacPermissionsCache.get(USER_ID, ACCOUNT_ID);

    assertEquals(expectedPermissions, result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_CacheMiss() {
    when(cache.get(CACHE_KEY)).thenReturn(null);

    RbacPermissions result = catalogRbacPermissionsCache.get(USER_ID, ACCOUNT_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_ExceptionHandling() {
    when(cache.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis connection failed"));

    RbacPermissions result = catalogRbacPermissionsCache.get(USER_ID, ACCOUNT_ID);

    assertNull(result);
    verify(cache).get(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_Success() {
    Map<String, List<String>> scopePermissions = new HashMap<>();
    scopePermissions.put("VIEW", List.of("scope1", "scope2"));
    RbacPermissions permissions =
        RbacPermissions.builder().scopePermissions(scopePermissions).allowedEntityRefs(new ArrayList<>()).build();

    catalogRbacPermissionsCache.put(USER_ID, ACCOUNT_ID, permissions);

    verify(cache).put(eq(CACHE_KEY), eq(permissions), eq(EXPIRY_IN_MINUTES), eq(TimeUnit.MINUTES));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_ExceptionHandling() {
    RbacPermissions permissions = RbacPermissions.builder().build();
    when(cache.put(eq(CACHE_KEY), any(), anyLong(), any())).thenThrow(new RuntimeException("Redis write failed"));

    catalogRbacPermissionsCache.put(USER_ID, ACCOUNT_ID, permissions);

    verify(cache).put(eq(CACHE_KEY), eq(permissions), eq(EXPIRY_IN_MINUTES), eq(TimeUnit.MINUTES));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRemove_Success() {
    catalogRbacPermissionsCache.remove(USER_ID, ACCOUNT_ID);

    verify(cache).remove(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRemove_ExceptionHandling() {
    when(cache.remove(CACHE_KEY)).thenThrow(new RuntimeException("Redis delete failed"));

    catalogRbacPermissionsCache.remove(USER_ID, ACCOUNT_ID);

    verify(cache).remove(CACHE_KEY);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildKey() {
    RbacPermissions permissions = RbacPermissions.builder().build();
    catalogRbacPermissionsCache.put(USER_ID, ACCOUNT_ID, permissions);

    verify(cache).put(eq(CACHE_KEY), any(), anyLong(), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
    redissonClientFactoryMock.close();
  }
}
