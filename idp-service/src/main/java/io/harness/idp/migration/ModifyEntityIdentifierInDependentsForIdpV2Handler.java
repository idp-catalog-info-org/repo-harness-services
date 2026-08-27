/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.backstage.utils.BackstageUtils;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.ccp.service.CatalogCustomPropertiesService;
import io.harness.idp.groups.service.GroupsService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.service.ScoreService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class ModifyEntityIdentifierInDependentsForIdpV2Handler
    implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private HPersistence persistence;
  private MongoTemplate mongoTemplate;
  private NamespaceService namespaceService;
  private ScoreService scoreService;
  private ScorecardService scorecardService;
  private CheckService checkService;
  private BackstageService backstageService;
  private CatalogEntityRepository catalogEntityRepository;
  private CatalogCustomPropertiesService catalogCustomPropertiesService;
  private GroupsService groupsService;
  private static final String MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_HANDLER =
      "ModifyEntityIdentifierInDependentsForIdpV2Handler";
  private static final String METADATA_ENTITY_CONFLICT = "metadata.entity_conflict";

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
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInBackstageCompleted", true)
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInDependentsCompleted", false)
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInDependentsFrom",
                new BasicDBObject("$lte", System.currentTimeMillis()));
    try (DBCursor records = collection.find(queryOps).limit(5)) {
      while (records.hasNext()) {
        DBObject record = records.next();
        NamespaceEntity namespaceEntity = persistence.convertToEntity(NamespaceEntity.class, record);
        String accountIdentifier = namespaceEntity.getAccountIdentifier();
        List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesForArbitraryFields(
            accountIdentifier, Map.of(METADATA_ENTITY_CONFLICT, true), null);
        Set<String> conflictedEntityUids =
            catalogEntities.stream()
                .map(catalogEntity
                    -> "account/"
                        + (WORKFLOW_KIND.equals(catalogEntity.getKind()) ? TEMPLATE_KIND : catalogEntity.getKind())
                        + "/" + catalogEntity.getIdentifier())
                .collect(Collectors.toSet());
        Set<String> conflictedEntityRefs =
            conflictedEntityUids.stream().map(BackstageUtils::getEntityRefFromUid).collect(Collectors.toSet());
        long start = System.currentTimeMillis();
        log.info("Starting the migration for modifying entity identifiers in dependents for IDP V2 for account {}",
            accountIdentifier);
        try {
          backstageService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityUids);
          backstageService.modifyEntityRefInScaffolderTaskForIdpV2(accountIdentifier, conflictedEntityRefs);
          groupsService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityUids);
          catalogCustomPropertiesService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityRefs);
          scorecardService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityUids);
          checkService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityUids);
          scoreService.modifyEntityIdentifierForIdpV2(accountIdentifier, conflictedEntityUids);
          updateIdpV2MigrationInfo(namespaceEntity);
          log.info("Total time taken to complete the migration for modifying entity identifiers in dependents for IDP "
                  + "V2 for account {} - {}",
              accountIdentifier, System.currentTimeMillis() - start);
        } catch (Exception e) {
          log.error("Error occurred during the migration for modifying entity identifiers in dependents for IDP V2 for "
                  + "account {}",
              accountIdentifier, e);
        }
      }
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(30))
            .build(),
        ModifyEntityIdentifierInDependentsForIdpV2Handler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name)
                                         .is(MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_HANDLER)))
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
      idpV2MigrationInfo.setMigrateDefaultToAccountNamespaceInDependentsCompleted(true);
      metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
      namespaceEntity.setMetadata(metadata);
      namespaceService.save(namespaceEntity);
    }
  }
}
