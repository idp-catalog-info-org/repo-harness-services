/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.idp.catalog.helpers.VerificationHelper;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class CatalogEntitiesVerificationHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private static final String CATALOG_ENTITIES_VERIFICATION_HANDLER = "CatalogEntitiesVerificationHandler";
  private VerificationHelper verificationHelper;
  private IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @Override
  public void handle(IteratorEntity entity) {
    Criteria criteria = new Criteria()
                            .andOperator(Criteria.where(NamespaceEntity.NamespaceKeys.isDeleted).is(false))
                            .orOperator(Criteria
                                            .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                                + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                                      .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                            .exists(true),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                              .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                    .is(true));
    List<NamespaceEntity> namespaceEntities = mongoTemplate.find(new Query(criteria), NamespaceEntity.class);
    namespaceEntities.forEach(namespaceEntity -> {
      String accountIdentifier = namespaceEntity.getAccountIdentifier();
      try {
        verificationHelper.verifyHarnessAndIDPEntities(accountIdentifier);
        idpIteratorMetricRecorder.recordSuccess(CATALOG_ENTITIES_VERIFICATION_HANDLER, accountIdentifier);
      } catch (Exception exception) {
        idpIteratorMetricRecorder.recordFailure(CATALOG_ENTITIES_VERIFICATION_HANDLER, accountIdentifier);
      }
    });
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(CATALOG_ENTITIES_VERIFICATION_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(15))
            .build(),
        CatalogEntitiesVerificationHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(
                    where(IteratorEntity.IteratorsKeys.name).is(CATALOG_ENTITIES_VERIFICATION_HANDLER)))
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
