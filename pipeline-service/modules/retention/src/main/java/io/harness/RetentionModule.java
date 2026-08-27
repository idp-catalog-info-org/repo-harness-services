/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.jobs.service.impl.ExecutionRetentionIteratorEntityServiceImpl;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dataretention.service.impl.ExecutionRetentionMetadataServiceImpl;
import io.harness.dataretention.service.impl.ExecutionRetentionServiceImpl;
import io.harness.elasticsearch.ElasticSearchDBConfig;
import io.harness.elasticsearch.ElasticSearchModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.ObjectStoreClientFactory;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationEntityService;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationMonitorEntityService;
import io.harness.reconciliation.service.impl.ExecutionRetentionReconciliationEntityServiceImpl;
import io.harness.reconciliation.service.impl.ExecutionRetentionReconciliationMonitorEntityServiceImpl;
import io.harness.search.service.PipelineSearchIndexMigrationService;
import io.harness.search.service.PipelineSearchService;
import io.harness.search.service.impl.PipelineSearchIndexMigrationServiceImpl;
import io.harness.search.service.impl.PipelineSearchServiceImpl;
import io.harness.threading.ThreadPool;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH, HarnessModuleComponent.CDS_DATA_RETENTION})
public class RetentionModule extends AbstractModule {
  private static RetentionModule instance;
  private final RetentionModuleConfig config;
  private final MetricRegistry threadPoolMetricRegistry;

  public static RetentionModule getInstance(
      RetentionModuleConfig retentionModuleConfig, MetricRegistry threadPoolMetricRegistry) {
    if (instance == null) {
      instance = new RetentionModule(retentionModuleConfig, threadPoolMetricRegistry);
    }
    return instance;
  }

  private RetentionModule(RetentionModuleConfig config, MetricRegistry threadPoolMetricRegistry) {
    this.config = config;
    this.threadPoolMetricRegistry = threadPoolMetricRegistry;
  }

  @Override
  protected void configure() {
    bind(PipelineSearchIndexMigrationService.class).to(PipelineSearchIndexMigrationServiceImpl.class);
    install(ElasticSearchModule.getInstance(config.getElasticSearchDBConfig()));
    bind(PipelineSearchService.class).to(PipelineSearchServiceImpl.class);
    bind(ExecutionRetentionReconciliationEntityService.class)
        .to(ExecutionRetentionReconciliationEntityServiceImpl.class);
    bind(ExecutionRetentionReconciliationMonitorEntityService.class)
        .to(ExecutionRetentionReconciliationMonitorEntityServiceImpl.class);
    bind(ExecutionRetentionMetadataService.class).to(ExecutionRetentionMetadataServiceImpl.class).in(Singleton.class);
    bind(ExecutionRetentionService.class).to(ExecutionRetentionServiceImpl.class).in(Singleton.class);
    bind(ExecutionRetentionIteratorEntityService.class).to(ExecutionRetentionIteratorEntityServiceImpl.class);
  }

  @Provides
  @Singleton
  DataRetentionConfig dataRetentionConfig() {
    if (config.getDataRetentionConfig() == null) {
      return DataRetentionConfig.builder().enabled(false).build();
    }
    return config.getDataRetentionConfig();
  }

  @Provides
  @Singleton
  ElasticSearchDBConfig elasticSearchDBConfig() {
    if (config.getElasticSearchDBConfig() == null) {
      return ElasticSearchDBConfig.builder().enabled(false).build();
    }
    return config.getElasticSearchDBConfig();
  }

  @Provides
  @Singleton
  @Named("ExecutionRetentionSyncService")
  public Executor executionRetentionSyncService() {
    return ThreadPool.getInstrumentedExecutorService(
        config.getExecutionRetentionSyncServicePoolConfig(), "ExecutionRetentionSyncService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("DataRetentionObjectStoreClient")
  @Inject
  ObjectStoreClient objectStoreClient(@Nullable MetricService metricService) {
    if (config.getDataRetentionConfig() != null && config.getDataRetentionConfig().isEnabled()) {
      return ObjectStoreClientFactory.getClient(config.getStoreConfig(), config.getBucketConfig(), metricService);
    }
    return null;
  }
}
