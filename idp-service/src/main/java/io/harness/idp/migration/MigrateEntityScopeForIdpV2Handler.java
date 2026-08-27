/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.JacksonUtils.readValueForSingleEntity;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.context.GlobalContextData;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.ccp.service.CatalogCustomPropertiesService;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.idp.groups.repositories.GroupsRepository;
import io.harness.idp.groups.service.GroupsService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.manage.GlobalContextManager;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.User;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class MigrateEntityScopeForIdpV2Handler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private HPersistence persistence;
  private MongoTemplate mongoTemplate;
  private NamespaceService namespaceService;
  private ScopeInfoClient scopeInfoClient;
  private CatalogServiceHelper catalogServiceHelper;
  private CatalogService catalogService;
  private ScoreService scoreService;
  private ScorecardService scorecardService;
  private CheckService checkService;
  private CatalogEntityRepository catalogEntityRepository;
  private CatalogCustomPropertiesService catalogCustomPropertiesService;
  private GroupsRepository groupsRepository;
  private GroupsService groupsService;
  private static final String MIGRATE_ENTITY_SCOPE_FOR_IDP_V2_HANDLER = "MigrateEntityScopeForIdpV2Handler";

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
            .append("metadata.idpV2MigrationInfo.migrateDefaultToAccountNamespaceInDependentsCompleted", true)
            .append("metadata.idpV2MigrationInfo.migrateScopeInfo", new BasicDBObject("$exists", true))
            .append("metadata.idpV2MigrationInfo.migrateScopeInfo.isActive", true);
    try (DBCursor records = collection.find(queryOps).limit(5)) {
      if (records.hasNext()) {
        DBObject record = records.next();
        NamespaceEntity namespaceEntity = persistence.convertToEntity(NamespaceEntity.class, record);
        String accountIdentifier = namespaceEntity.getAccountIdentifier();
        GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
        long start = System.currentTimeMillis();
        log.info("Starting the IDP 2.0 MigrationAPI Operation for account {}", accountIdentifier);
        try {
          NamespaceEntity.Metadata metadata = namespaceEntity.getMetadata();
          NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo migrateScopeInfo =
              metadata.getIdpV2MigrationInfo().getMigrateScopeInfo();
          EntitiesMigrateRequest request =
              readValueForSingleEntity(migrateScopeInfo.getRequest(), EntitiesMigrateRequest.class);
          User user = migrateScopeInfo.getUpdatedBy();
          GlobalContextManager.upsertGlobalContextRecord(
              PrincipalContextData.builder()
                  .principal(new UserPrincipal(user.getUuid(), user.getEmail(), user.getName(), accountIdentifier))
                  .build());
          ScorecardFilter filter = request.getFilter();
          String kind = null, type = null, owner = null, tag = null, lifecycle = null;
          String entityRefs = String.join(",", request.getEntityRefs());
          String scopes = "account";
          if (filter != null) {
            kind = filter.getKind();
            type = filter.getType();
            owner = String.join(",", request.getFilter().getOwners());
            tag = String.join(",", request.getFilter().getTags());
            lifecycle = String.join(",", request.getFilter().getLifecycle());
            if (!isEmpty(filter.getScopes())) {
              scopes = String.join(",", request.getFilter().getScopes());
            }
          }

          List<CatalogEntity> catalogEntities = new ArrayList<>();
          Page<CatalogEntity> catalogEntitiesPaged;
          int page = 0;
          do {
            catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
                catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, null).getLeft(),
                page, 1000, null, null, null, entityRefs, kind, type, owner, lifecycle, tag, null, null);
            if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
              catalogEntities.addAll(catalogEntitiesPaged.getContent());
            }
            page++;
          } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

          if (isEmpty(catalogEntities)) {
            log.info("No entities found for account {}", accountIdentifier);
            return;
          }

          String destinationScope = request.getDestinationScope();
          Pair<String, String> orgProjectIdentifier = catalogServiceHelper.getOrgProjectFromScope(destinationScope);
          ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(
              accountIdentifier, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight()));
          catalogEntities.forEach(catalogEntity -> {
            if (catalogEntity instanceof InlineCatalogEntity) {
              String existingEntityRef = CatalogUtils.getEntityRef(catalogEntity);
              String existingEntityUid = CatalogUtils.getEntityUUId(catalogEntity);
              CatalogEntity modifiedEntity = catalogService.changeScope(catalogEntity, scopeInfo);
              String modifiedEntityRef = CatalogUtils.getEntityRef(modifiedEntity);
              String modifiedEntityUid = CatalogUtils.getEntityUUId(modifiedEntity);
              List<GroupEntity> groupsEntities = groupsRepository.findAllByAccountIdentifier(accountIdentifier);
              if (!isEmpty(groupsEntities)) {
                groupsService.modifyScopeForEntityIdentifier(
                    groupsEntities, accountIdentifier, existingEntityRef, modifiedEntityRef);
              }
              catalogCustomPropertiesService.modifyScopeForEntityRef(
                  accountIdentifier, existingEntityRef, modifiedEntityRef);
              scorecardService.modifyScopeForEntityIdentifier(accountIdentifier, existingEntityUid, modifiedEntityUid);
              checkService.modifyScopeForEntityIdentifier(accountIdentifier, existingEntityUid, modifiedEntityUid);
              scoreService.modifyScopeForEntityIdentifier(accountIdentifier, existingEntityUid, modifiedEntityUid);
            }
          });
          log.info("Total time taken to complete the IDP 2.0 MigrationAPI Operation for account {} - {}",
              accountIdentifier, System.currentTimeMillis() - start);
        } catch (Exception e) {
          log.error("Error occurred during the IDP 2.0 MigrationAPI Operation for account {}", accountIdentifier, e);
        } finally {
          updateIdpV2MigrationInfo(namespaceEntity);
          GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
        }
      }
    }
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(MIGRATE_ENTITY_SCOPE_FOR_IDP_V2_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(30))
            .build(),
        MigrateEntityScopeForIdpV2Handler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query
                -> query.addCriteria(
                    where(IteratorEntity.IteratorsKeys.name).is(MIGRATE_ENTITY_SCOPE_FOR_IDP_V2_HANDLER)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(600))
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
      idpV2MigrationInfo.setMigrateScopeInfo(null);
      metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
      namespaceEntity.setMetadata(metadata);
      namespaceService.save(namespaceEntity);
    }
  }
}
