/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.persistence.HPersistence;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class ModifyDefaultToAccountNamespaceInBackstageForIdpV2Handler
    implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private HPersistence persistence;
  private MongoTemplate mongoTemplate;
  private NamespaceService namespaceService;
  private CatalogService catalogService;
  private static final String MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_HANDLER =
      "ModifyDefaultToAccountNamespaceInBackstageForIdpV2Handler";

  @Override
  public void handle(IteratorEntity entity) {
    final DBCollection collection = persistence.getCollection(NamespaceEntity.class);
    BasicDBObject queryOps =
        new BasicDBObject("isDeleted", false)
            .append(
                "metadata.migrateCatalogEntitiesFromBackstageToHarnessCompleted", new BasicDBObject("$exists", true))
            .append("metadata.migrateCatalogEntitiesFromBackstageToHarnessCompleted", true)
            .append("metadata.postgresIdpV2MigrationCompleted", new BasicDBObject("$exists", true))
            .append("metadata.postgresIdpV2MigrationCompleted", true)
            .append("metadata.idpV2MigrationInfo", new BasicDBObject("$exists", true))
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInBackstageCompleted", false)
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInBackstageFrom",
                new BasicDBObject("$lte", System.currentTimeMillis()));
    try (DBCursor records = collection.find(queryOps).limit(5)) {
      while (records.hasNext()) {
        DBObject record = records.next();
        NamespaceEntity namespaceEntity = persistence.convertToEntity(NamespaceEntity.class, record);
        String accountIdentifier = namespaceEntity.getAccountIdentifier();

        long start = System.currentTimeMillis();
        log.info(
            "Starting the migration for modifying the namespace in backstage from default to account for account - {}",
            accountIdentifier);

        // First, migrate catalog entities from Backstage to Harness as inline entities
        try {
          log.info("Running catalog entities migration from Backstage to Harness for account - {}", accountIdentifier);
          catalogService.migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(accountIdentifier);
          log.info(
              "Completed catalog entities migration from Backstage to Harness for account - {}", accountIdentifier);
        } catch (Exception e) {
          log.error("Error occurred during catalog entities migration from Backstage to Harness for account {}",
              accountIdentifier, e);
        }

        // Then, recreate catalogs with account as namespace for IDP V2
        try {
          catalogService.recreateCatalogsWithAccountAsNamespaceForIDPV2(accountIdentifier);
          updateIdpV2MigrationInfo(namespaceEntity);
          log.info("Total time taken to complete the migration modifying the namespace in backstage from default to "
                  + "account for account {} - {}",
              accountIdentifier, System.currentTimeMillis() - start);
        } catch (Exception e) {
          log.error("Error occurred during the migration for modifying the namespace in backstage from default to "
                  + "account for account {}",
              accountIdentifier, e);
        }
      }
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(30))
            .build(),
        ModifyDefaultToAccountNamespaceInBackstageForIdpV2Handler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name)
                                         .is(MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(480))
            .acceptableNoAlertDelay(ofSeconds(120))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  private void updateIdpV2MigrationInfo(NamespaceEntity namespaceEntity) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    NamespaceEntity.Metadata.IdpV2MigrationInfo idpV2MigrationInfo =
        Objects.isNull(namespaceEntity.getMetadata().getIdpV2MigrationInfo())
        ? NamespaceEntity.Metadata.IdpV2MigrationInfo.builder().build()
        : namespaceEntity.getMetadata().getIdpV2MigrationInfo();
    if (idpV2MigrationInfo != null) {
      idpV2MigrationInfo.setMigrateDefaultToAccountNamespaceInBackstageCompleted(true);
      metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
      namespaceEntity.setMetadata(metadata);
      namespaceService.save(namespaceEntity);
    }
  }
}
