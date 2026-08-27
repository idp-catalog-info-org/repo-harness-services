/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.mongo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsCollector;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Managed job for entity migration metrics collection.
 * This class configures and manages the entity migration metrics collector for tracking service layer changes.
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ProjectEntityMigrationMetricsJob implements Managed {
  private static final String SERVICE_NAME = "pipeline-service";

  private final MongoTemplate mongoTemplate;
  private final HarnessMetricRegistry metricRegistry;
  private final PersistentLocker persistentLocker;
  private ProjectEntityMigrationMetricsConfig metricsConfig;
  private ProjectEntityMigrationMetricsCollector metricsCollector;

  @Inject
  public ProjectEntityMigrationMetricsJob(
      MongoTemplate mongoTemplate, HarnessMetricRegistry metricRegistry, PersistentLocker persistentLocker) {
    this.mongoTemplate = mongoTemplate;
    this.metricRegistry = metricRegistry;
    this.persistentLocker = persistentLocker;
  }

  /**
   * Configure the metrics collector with the provided configuration.
   * @param metricsConfig Configuration for the metrics collector
   */
  public void configure(ProjectEntityMigrationMetricsConfig metricsConfig) {
    this.metricsConfig = metricsConfig;
    this.metricsCollector = new ProjectEntityMigrationMetricsCollector(mongoTemplate, metricRegistry,
        AddUniqueIdParentIdToEntitiesTask.entityWithOrgProjectKeysMap(), SERVICE_NAME, persistentLocker);
  }

  @Override
  public void start() throws Exception {
    if (metricsCollector == null) {
      throw new IllegalStateException(
          "ProjectEntityMigrationMetricsCollector in pipeline-service has not been configured yet");
    }
    if (metricsConfig == null) {
      throw new IllegalStateException(
          "ProjectEntityMigrationMetricsConfig in pipeline-service has not been configured yet");
    }

    int initialDelayMinutes = metricsConfig.getInitialDelayMinutes();
    int frequencyMinutes = metricsConfig.getFrequencyMinutes();

    metricsCollector.configure(initialDelayMinutes, frequencyMinutes);
    metricsCollector.start();
    log.info("Entity migration metrics collector in pipeline-service has been started successfully");
  }

  @Override
  public void stop() throws Exception {
    if (metricsCollector != null) {
      metricsCollector.stop();
    }
  }
}
