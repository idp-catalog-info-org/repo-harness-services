/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.events.consumers.kafka;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.idp.events.consumers.IdpRedisConsumer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpEventConsumerController implements Managed {
  private ExecutorService executorService =
      Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("idp-event-consumer-%d").build());
  private List<IdpRedisConsumer> redisConsumers = new ArrayList<>();

  private final CdcKafkaConfig cdcKafkaConfig;
  private final ExecutorService cdcKafkaExecutorService;
  private final Provider<CatalogKafkaConsumer> catalogKafkaConsumerProvider;
  private final Provider<ScaffolderTasksKafkaConsumer> scaffolderTasksKafkaConsumerProvider;
  private final Provider<ChecksKafkaConsumer> checksKafkaConsumerProvider;
  private final Provider<ScorecardsKafkaConsumer> scorecardsKafkaConsumerProvider;
  private final Provider<ModuleLicensesKafkaConsumer> moduleLicensesKafkaConsumerProvider;
  private final Provider<AppConfigsKafkaConsumer> appConfigsKafkaConsumerProvider;

  @Inject
  public IdpEventConsumerController(CdcKafkaConfig cdcKafkaConfig,
      @Named(CdcKafkaConstants.CDC_KAFKA_EXECUTOR_SERVICE) ExecutorService cdcKafkaExecutorService,
      Provider<CatalogKafkaConsumer> catalogKafkaConsumerProvider,
      Provider<ScaffolderTasksKafkaConsumer> scaffolderTasksKafkaConsumerProvider,
      Provider<ChecksKafkaConsumer> checksKafkaConsumerProvider,
      Provider<ScorecardsKafkaConsumer> scorecardsKafkaConsumerProvider,
      Provider<ModuleLicensesKafkaConsumer> moduleLicensesKafkaConsumerProvider,
      Provider<AppConfigsKafkaConsumer> appConfigsKafkaConsumerProvider) {
    this.cdcKafkaConfig = cdcKafkaConfig;
    this.cdcKafkaExecutorService = cdcKafkaExecutorService;
    this.catalogKafkaConsumerProvider = catalogKafkaConsumerProvider;
    this.scaffolderTasksKafkaConsumerProvider = scaffolderTasksKafkaConsumerProvider;
    this.checksKafkaConsumerProvider = checksKafkaConsumerProvider;
    this.scorecardsKafkaConsumerProvider = scorecardsKafkaConsumerProvider;
    this.moduleLicensesKafkaConsumerProvider = moduleLicensesKafkaConsumerProvider;
    this.appConfigsKafkaConsumerProvider = appConfigsKafkaConsumerProvider;
  }

  public void register(IdpRedisConsumer consumer, int threads) {
    IntStream.rangeClosed(1, threads).forEach((int value) -> {
      redisConsumers.add(consumer);
      executorService.submit(consumer);
    });
  }

  /* (non-Javadoc)
   * @see io.dropwizard.lifecycle.Managed#start()
   */
  @Override
  public void start() throws Exception {
    // Start CDC Kafka consumers if enabled
    if (cdcKafkaConfig != null && cdcKafkaConfig.isEnabled()) {
      startKafkaCdcConsumers();
    }
  }

  private void startKafkaCdcConsumers() {
    log.info("Starting CDC Kafka consumers...");

    // Start Catalog Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.CATALOG_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(catalogKafkaConsumerProvider.get());
        log.info("Started Catalog Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start Catalog Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }

    // Start ScaffolderTasks Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.SCAFFOLDER_TASKS_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(scaffolderTasksKafkaConsumerProvider.get());
        log.info("Started ScaffolderTasks Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start ScaffolderTasks Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }

    // Start Checks Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.CHECKS_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(checksKafkaConsumerProvider.get());
        log.info("Started Checks Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start Checks Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }

    // Start Scorecards Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.SCORECARDS_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(scorecardsKafkaConsumerProvider.get());
        log.info("Started Scorecards Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start Scorecards Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }

    // Start ModuleLicenses Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.MODULE_LICENSES_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(moduleLicensesKafkaConsumerProvider.get());
        log.info("Started ModuleLicenses Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start ModuleLicenses Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }

    // Start AppConfigs Kafka consumer if enabled
    if (cdcKafkaConfig.isConsumerEnabled(CdcKafkaConfig.APP_CONFIGS_CONSUMER)) {
      try {
        cdcKafkaExecutorService.submit(appConfigsKafkaConsumerProvider.get());
        log.info("Started AppConfigs Kafka CDC consumer");
      } catch (Exception ex) {
        log.error("Failed to start AppConfigs Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    }
  }

  /* (non-Javadoc)
   * @see io.dropwizard.lifecycle.Managed#stop()
   */
  @Override
  public void stop() throws Exception {
    redisConsumers.forEach(IdpRedisConsumer::shutDown);
    executorService.shutdownNow();
    executorService.awaitTermination(1, TimeUnit.HOURS);

    // Shutdown CDC Kafka executor service
    if (cdcKafkaExecutorService != null) {
      cdcKafkaExecutorService.shutdownNow();
      cdcKafkaExecutorService.awaitTermination(30, TimeUnit.SECONDS);
    }
  }
}
