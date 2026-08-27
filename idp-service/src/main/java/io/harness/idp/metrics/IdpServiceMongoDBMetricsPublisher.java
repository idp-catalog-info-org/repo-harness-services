/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.mongo.metrics.HarnessConnectionPoolStatistics;
import io.harness.mongo.metrics.MongoMetricsContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.connection.ServerId;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class IdpServiceMongoDBMetricsPublisher implements MetricsPublisher {
  private final HarnessConnectionPoolListener harnessConnectionPoolListener;
  private final MetricService metricService;
  private static final String METRIC_PREFIX = "idp_mongo_pool_";
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9_]");
  private static final String CONNECTION_POOL_SIZE = "size";
  private static final String CONNECTIONS_CHECKED_OUT = "checked_out";
  private static final String CONNECTION_POOL_MAX_SIZE = "max_size";
  private static final String NAMESPACE = getEnvOrDefault("NAMESPACE", "local");
  private static final String CONTAINER_NAME = getEnvOrDefault("CONTAINER_NAME", "idp-service");

  @Override
  public void recordMetrics() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> map = harnessConnectionPoolListener.getStatistics();
    map.forEach((serverId, stats) -> {
      String serverAddress = sanitizeName(serverId.getAddress().toString());
      String clientDescription = sanitizeName(serverId.getClusterId().getDescription());
      try (MongoMetricsContext ignore =
               new MongoMetricsContext(NAMESPACE, CONTAINER_NAME, serverAddress, clientDescription)) {
        recordMetric(CONNECTION_POOL_MAX_SIZE, stats.getMaxSize());
        recordMetric(CONNECTION_POOL_SIZE, stats.getSize());
        recordMetric(CONNECTIONS_CHECKED_OUT, stats.getCheckedOutCount());
      }
    });
  }

  private static String sanitizeName(String labelName) {
    String name = METRIC_NAME_RE.matcher(labelName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }

  private void recordMetric(String name, double value) {
    metricService.recordMetric(METRIC_PREFIX + name, value);
  }

  private static String getEnvOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null && !value.isEmpty() ? value : defaultValue;
  }
}
