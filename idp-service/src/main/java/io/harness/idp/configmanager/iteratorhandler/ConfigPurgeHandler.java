/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.iteratorhandler;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class ConfigPurgeHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private ConfigManagerService configManagerService;
  private static final String CONFIG_PURGE_HANDLER = "ConfigPurgeHandler";
  private IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @Override
  public void handle(IteratorEntity entity) {
    log.info("App Config purge iterator started for disabled plugins....");
    try {
      List<AppConfigEntity> appConfigEntities =
          configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
      if (appConfigEntities.isEmpty()) {
        log.info(
            "No config shortlisted for Purging - either all plugins are enabled or they are disabled within one week");
      }
      appConfigEntities.forEach(appConfigEntity -> {
        String accountIdentifier = appConfigEntity.getAccountIdentifier();
        String configId = appConfigEntity.getConfigId();
        log.info("App config purged for account {} and plugin id - {}", accountIdentifier, configId);
        idpIteratorMetricRecorder.recordSuccess(CONFIG_PURGE_HANDLER, accountIdentifier);
      });
      log.info("Weekly Config purge iterator completed");
    } catch (Exception e) {
      idpIteratorMetricRecorder.recordFailure(CONFIG_PURGE_HANDLER, null);
      log.error("Weekly App config purge iterator unsuccessful Error - ", e);
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(CONFIG_PURGE_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() / 7))
            .build(),
        ConfigPurgeHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(
                query -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(CONFIG_PURGE_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(90))
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
