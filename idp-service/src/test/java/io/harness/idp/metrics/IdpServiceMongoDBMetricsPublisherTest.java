/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.mongo.metrics.HarnessConnectionPoolStatistics;
import io.harness.rule.Owner;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ServerId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMongoDBMetricsPublisherTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private IdpServiceMongoDBMetricsPublisher publisher;
  @Mock private HarnessConnectionPoolListener harnessConnectionPoolListener;
  @Mock private MetricService metricService;

  private static final String METRIC_PREFIX = "idp_mongo_pool_";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithActiveConnections() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> statsMap = new ConcurrentHashMap<>();
    ServerId serverId = createServerId("localhost:27017", "test-cluster");
    HarnessConnectionPoolStatistics stats = createStats(10, 5, 20);
    statsMap.put(serverId, stats);

    when(harnessConnectionPoolListener.getStatistics()).thenReturn(statsMap);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "size"), eq(10.0));
    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "checked_out"), eq(5.0));
    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "max_size"), eq(20.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithMultipleServers() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> statsMap = new ConcurrentHashMap<>();
    ServerId serverId1 = createServerId("localhost:27017", "cluster1");
    ServerId serverId2 = createServerId("localhost:27018", "cluster2");
    HarnessConnectionPoolStatistics stats1 = createStats(10, 5, 20);
    HarnessConnectionPoolStatistics stats2 = createStats(15, 8, 25);
    statsMap.put(serverId1, stats1);
    statsMap.put(serverId2, stats2);

    when(harnessConnectionPoolListener.getStatistics()).thenReturn(statsMap);

    publisher.recordMetrics();

    verify(metricService, times(2)).recordMetric(eq(METRIC_PREFIX + "size"), anyDouble());
    verify(metricService, times(2)).recordMetric(eq(METRIC_PREFIX + "checked_out"), anyDouble());
    verify(metricService, times(2)).recordMetric(eq(METRIC_PREFIX + "max_size"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithEmptyConnectionPool() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> statsMap = new ConcurrentHashMap<>();
    when(harnessConnectionPoolListener.getStatistics()).thenReturn(statsMap);

    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(eq(METRIC_PREFIX + "size"), anyDouble());
    verify(metricService, never()).recordMetric(eq(METRIC_PREFIX + "checked_out"), anyDouble());
    verify(metricService, never()).recordMetric(eq(METRIC_PREFIX + "max_size"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithZeroCheckedOutConnections() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> statsMap = new ConcurrentHashMap<>();
    ServerId serverId = createServerId("localhost:27017", "test-cluster");
    HarnessConnectionPoolStatistics stats = createStats(10, 0, 20);
    statsMap.put(serverId, stats);

    when(harnessConnectionPoolListener.getStatistics()).thenReturn(statsMap);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "size"), eq(10.0));
    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "checked_out"), eq(0.0));
    verify(metricService, times(1)).recordMetric(eq(METRIC_PREFIX + "max_size"), eq(20.0));
  }

  private ServerId createServerId(String address, String clusterDescription) {
    ServerAddress serverAddress = new ServerAddress(address);
    ClusterId clusterId = new ClusterId(clusterDescription);
    return new ServerId(clusterId, serverAddress);
  }

  private HarnessConnectionPoolStatistics createStats(int size, int checkedOut, int maxSize) {
    HarnessConnectionPoolStatistics stats = mock(HarnessConnectionPoolStatistics.class);
    when(stats.getSize()).thenReturn(size);
    when(stats.getCheckedOutCount()).thenReturn(checkedOut);
    when(stats.getMaxSize()).thenReturn(maxSize);
    return stats;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
