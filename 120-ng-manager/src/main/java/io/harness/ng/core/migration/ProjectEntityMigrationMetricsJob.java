/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsCollector;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.persistence.UniqueIdAware;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Managed job for entity migration metrics collection.
 * This class configures and manages the entity migration metrics collector for tracking service layer changes.
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ProjectEntityMigrationMetricsJob implements Managed {
  private static final String SERVICE_NAME = "ng-manager";

  private final MongoTemplate mongoTemplate;
  private final HarnessMetricRegistry metricRegistry;
  private final PersistentLocker persistentLocker;
  private ProjectEntityMigrationMetricsConfig metricsConfig;
  private ProjectEntityMigrationMetricsCollector metricsCollector;
  private static final String UNIQUE_ID_KEY = "uniqueIdKey";

  private static final String ORG_ID_KEY = "orgIdKey";
  private static final String PROJECT_ID_KEY = "projectIdKey";

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
    this.metricsCollector = new ProjectEntityMigrationMetricsCollector(
        mongoTemplate, metricRegistry, getEntityWithOrgProjectKeysMap(), SERVICE_NAME, persistentLocker);
  }

  private Map<Class<? extends UniqueIdAware>, Map<String, String>> getEntityWithOrgProjectKeysMap() {
    Map<Class<? extends UniqueIdAware>, Map<String, String>> entityWithOrgProjectKeysMap =
        new HashMap<>(AddUniqueIdParentIdToEntitiesTask.getEntityWithOrgProjectKeysMap());
    entityWithOrgProjectKeysMap.put(UserMembership.class,
        Map.of(ORG_ID_KEY, NGCommonEntityConstants.ORG_ID, PROJECT_ID_KEY, NGCommonEntityConstants.PROJECT_ID,
            UNIQUE_ID_KEY, "_id"));
    return entityWithOrgProjectKeysMap;
  }

  @Override
  public void start() throws Exception {
    if (metricsCollector == null) {
      throw new IllegalStateException("ProjectEntityMigrationMetricsCollector has not been configured yet");
    }
    if (metricsConfig == null) {
      throw new IllegalStateException("ProjectEntityMigrationMetricsConfig has not been configured yet");
    }

    int initialDelayMinutes = metricsConfig.getInitialDelayMinutes();
    int frequencyMinutes = metricsConfig.getFrequencyMinutes();

    metricsCollector.configure(initialDelayMinutes, frequencyMinutes);
    metricsCollector.start();
    log.info("Entity migration metrics collector started successfully");
  }

  @Override
  public void stop() throws Exception {
    if (metricsCollector != null) {
      metricsCollector.stop();
    }
  }
}
