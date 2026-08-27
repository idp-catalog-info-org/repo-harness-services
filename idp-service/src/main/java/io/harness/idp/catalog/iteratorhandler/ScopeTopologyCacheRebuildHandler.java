/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
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
public class ScopeTopologyCacheRebuildHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private static final String SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER = "ScopeTopologyCacheRebuildHandler";

  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private CatalogEntityRepository catalogEntityRepository;
  private CatalogScopeResolver catalogScopeResolver;
  private IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @Override
  public void handle(IteratorEntity entity) {
    log.info("Scope topology cache rebuild iterator started");
    try {
      List<String> accountIds = catalogEntityRepository.findDistinctAccountIdentifiers();
      log.info("Rebuilding scope topology cache for {} accounts", accountIds.size());
      for (String accountId : accountIds) {
        try {
          catalogScopeResolver.buildScopeTopology(accountId);
          idpIteratorMetricRecorder.recordSuccess(SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER, accountId);
        } catch (Exception ex) {
          idpIteratorMetricRecorder.recordFailure(SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER, accountId);
          log.error("Error rebuilding scope topology cache for account={}. Error={}", accountId, ex.getMessage(), ex);
        }
      }
      log.info("Scope topology cache rebuild completed for {} accounts", accountIds.size());
    } catch (Exception ex) {
      idpIteratorMetricRecorder.recordFailure(SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER, null);
      log.error("Error in scope topology cache rebuild iterator. Error={}", ex.getMessage(), ex);
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(15))
            .build(),
        ScopeTopologyCacheRebuildHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(SCOPE_TOPOLOGY_CACHE_REBUILD_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(360))
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
