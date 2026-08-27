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
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.iterators.entity.IteratorEntity;
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
public class HarnessToIDPUserGroupSyncHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private HarnessToIDPHelper harnessToIDPHelper;
  private static final String HARNESS_TO_USER_GROUP_SYNC_HANDLER = "HarnessToIDPUserGroupSyncHandler";

  @Override
  public void handle(IteratorEntity entity) {
    Criteria criteria = new Criteria()
                            .andOperator(Criteria.where(NamespaceEntity.NamespaceKeys.isDeleted).is(false),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                              .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                    .exists(true),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys
                                              .migrateCatalogEntitiesFromBackstageToHarnessCompleted)
                                    .is(true))
                            .orOperator(Criteria
                                            .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                                + NamespaceEntity.Metadata.NamespaceMetadataKeys.userGroupSyncCompleted)
                                            .exists(false),
                                Criteria
                                    .where(NamespaceEntity.NamespaceKeys.metadata + "."
                                        + NamespaceEntity.Metadata.NamespaceMetadataKeys.userGroupSyncCompleted)
                                    .is(false));
    Query query = new Query(criteria).limit(5);
    List<NamespaceEntity> namespaceEntities = mongoTemplate.find(query, NamespaceEntity.class);
    namespaceEntities.forEach(
        namespaceEntity -> harnessToIDPHelper.syncUserGroupsIdentifierStartingWithUnderscore(namespaceEntity));
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(HARNESS_TO_USER_GROUP_SYNC_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(5))
            .build(),
        HarnessToIDPUserGroupSyncHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(HARNESS_TO_USER_GROUP_SYNC_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(240))
            .acceptableNoAlertDelay(ofSeconds(120))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
