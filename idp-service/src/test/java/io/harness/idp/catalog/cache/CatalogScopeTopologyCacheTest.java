/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.idp.catalog.cache.CatalogScopeTopologyCache.CACHE_NAME;
import static io.harness.idp.catalog.cache.CatalogScopeTopologyCache.EXPIRY_IN_MINUTES;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
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
import org.redisson.client.codec.StringCodec;

public class CatalogScopeTopologyCacheTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AutoCloseable openMocks;
  @Mock private RedisConfig redisConfig;
  private RedissonClient redissonClient;
  @SuppressWarnings("rawtypes") private RMapCache cache;
  private MockedStatic<RedissonClientFactory> redissonClientFactoryMock;
  private CatalogScopeTopologyCache catalogScopeTopologyCache;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    redissonClientFactoryMock = mockStatic(RedissonClientFactory.class);
    redissonClient = mock(RedissonClient.class);
    cache = mock(RMapCache.class);

    when(RedissonClientFactory.getClient(any())).thenReturn(redissonClient);
    when(redissonClient.getMapCache(eq(CACHE_NAME), eq(StringCodec.INSTANCE))).thenReturn(cache);

    catalogScopeTopologyCache = new CatalogScopeTopologyCache(redisConfig);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_Success() throws Exception {
    ScopeTopology expectedTopology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(new HashMap<>()).build();
    String json = MAPPER.writeValueAsString(expectedTopology);

    when(cache.get(ACCOUNT_ID)).thenReturn(json);

    ScopeTopology result = catalogScopeTopologyCache.get(ACCOUNT_ID);

    assertEquals(expectedTopology, result);
    verify(cache).get(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_CacheMiss() {
    when(cache.get(ACCOUNT_ID)).thenReturn(null);

    ScopeTopology result = catalogScopeTopologyCache.get(ACCOUNT_ID);

    assertNull(result);
    verify(cache).get(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGet_ExceptionHandling() {
    when(cache.get(ACCOUNT_ID)).thenThrow(new RuntimeException("Redis connection failed"));

    ScopeTopology result = catalogScopeTopologyCache.get(ACCOUNT_ID);

    assertNull(result);
    verify(cache).get(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_Success() {
    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(new HashMap<>()).build();

    catalogScopeTopologyCache.put(ACCOUNT_ID, topology);

    verify(cache).put(eq(ACCOUNT_ID), anyString(), eq(EXPIRY_IN_MINUTES), eq(TimeUnit.MINUTES));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testPut_ExceptionHandling() {
    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(new HashMap<>()).build();
    when(cache.put(eq(ACCOUNT_ID), any(), anyLong(), any())).thenThrow(new RuntimeException("Redis write failed"));

    catalogScopeTopologyCache.put(ACCOUNT_ID, topology);

    verify(cache).put(eq(ACCOUNT_ID), anyString(), eq(EXPIRY_IN_MINUTES), eq(TimeUnit.MINUTES));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRemove_Success() {
    catalogScopeTopologyCache.remove(ACCOUNT_ID);

    verify(cache).remove(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRemove_ExceptionHandling() {
    when(cache.remove(ACCOUNT_ID)).thenThrow(new RuntimeException("Redis delete failed"));

    catalogScopeTopologyCache.remove(ACCOUNT_ID);

    verify(cache).remove(ACCOUNT_ID);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
    redissonClientFactoryMock.close();
  }
}
