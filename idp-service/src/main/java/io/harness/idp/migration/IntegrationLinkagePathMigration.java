/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.PROCESSED_DATA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class IntegrationLinkagePathMigration implements NGMigration {
  private static final Set<String> OLD_FORMAT_KEYS =
      Set.of("entity_uuid", "entity_kind", "entity_identifier", "entity_action", "entity_action_destination");

  private static final Map<String, String> INTEGRATION_MAPPINGS =
      Map.of("HarnessCD", "harness-cd-integration", "HarnessScope", "harness-scope-integration");

  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogServiceHelper catalogServiceHelper;

  @Override
  public void migrate() {
    log.info("Starting migration for integration linkage path restructuring.");

    List<String> accountIdentifiers = namespaceService.getAccountIds();
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        log.info("Starting integration linkage path migration for accountId {}.", accountIdentifier);
        migrateIntegrationLinkages(accountIdentifier);
        log.info("Completed integration linkage path migration for accountId {}.", accountIdentifier);
      } catch (Exception e) {
        log.error("Error occurred during integration linkage path migration for accountId {}", accountIdentifier, e);
      }
    });

    log.info("Completed migration for integration linkage path restructuring.");
  }

  private void migrateIntegrationLinkages(String accountIdentifier) {
    for (Map.Entry<String, String> integration : INTEGRATION_MAPPINGS.entrySet()) {
      String integrationType = integration.getKey();
      String integrationId = integration.getValue();

      try {
        log.info(
            "Processing {} integration with id {} for account {}", integrationType, integrationId, accountIdentifier);

        // Query for entities with old format keys present
        Criteria criteria =
            Criteria.where("accountIdentifier")
                .is(accountIdentifier)
                .and(String.format("decorator.%s.metadata.%s.entity_uuid", PROCESSED_DATA, integrationType))
                .exists(true);

        Query query = new Query(criteria);
        List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);

        log.info("Found {} entities with old format for {} integration", catalogEntities.size(), integrationType);

        int notMigratedCount = 0;
        int partiallyMigratedCount = 0;
        int skippedCount = 0;

        for (CatalogEntity catalogEntity : catalogEntities) {
          try {
            MigrationResult result = migrateEntity(catalogEntity, integrationType, integrationId);
            switch (result) {
              case NOT_MIGRATED:
                notMigratedCount++;
                break;
              case PARTIALLY_MIGRATED:
                partiallyMigratedCount++;
                break;
              case SKIPPED:
                skippedCount++;
                break;
            }
          } catch (Exception e) {
            log.error(
                "Error migrating entity {} for integration {}", catalogEntity.getIdentifier(), integrationType, e);
          }
        }

        log.info("Migration complete for {} integration: {} not migrated (moved), {} partially migrated (cleaned up), "
                + "{} skipped",
            integrationType, notMigratedCount, partiallyMigratedCount, skippedCount);
      } catch (Exception e) {
        log.error("Error processing {} integration for account {}", integrationType, accountIdentifier, e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private MigrationResult migrateEntity(CatalogEntity catalogEntity, String integrationType, String integrationId) {
    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);

    if (isEmpty(processedData)) {
      return MigrationResult.SKIPPED;
    }

    Object metadataObj = processedData.get("metadata");
    if (!(metadataObj instanceof Map)) {
      return MigrationResult.SKIPPED;
    }

    Map<String, Object> metadata = (Map<String, Object>) metadataObj;
    Object integrationTypeObj = metadata.get(integrationType);
    if (!(integrationTypeObj instanceof Map)) {
      return MigrationResult.SKIPPED;
    }

    Map<String, Object> integrationTypeMap = (Map<String, Object>) integrationTypeObj;

    boolean hasOldFormat = integrationTypeMap.containsKey("entity_uuid");
    boolean hasNewFormat = integrationTypeMap.containsKey(integrationId);

    if (!hasOldFormat) {
      return MigrationResult.SKIPPED;
    }

    if (hasNewFormat) {
      log.info("Entity {} has both old and new format for {}, removing old keys", catalogEntity.getIdentifier(),
          integrationType);
      OLD_FORMAT_KEYS.forEach(integrationTypeMap::remove);
      saveEntity(catalogEntity, decorator, processedData);
      return MigrationResult.PARTIALLY_MIGRATED;
    } else {
      log.info("Entity {} has only old format for {}, migrating to new format", catalogEntity.getIdentifier(),
          integrationType);
      Map<String, Object> oldData = new HashMap<>();
      OLD_FORMAT_KEYS.forEach(key -> {
        if (integrationTypeMap.containsKey(key)) {
          oldData.put(key, integrationTypeMap.remove(key));
        }
      });
      integrationTypeMap.put(integrationId, oldData);
      saveEntity(catalogEntity, decorator, processedData);
      return MigrationResult.NOT_MIGRATED;
    }
  }

  private void saveEntity(
      CatalogEntity catalogEntity, Map<String, Object> decorator, Map<String, Object> processedData) {
    decorator.put(PROCESSED_DATA, processedData);
    catalogEntity.setDecorator(decorator);
    catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
    catalogEntityRepository.save(catalogEntity);
  }

  private enum MigrationResult {
    NOT_MIGRATED, // Entity was migrated from old to new format
    PARTIALLY_MIGRATED, // Entity had both formats, old format cleaned up
    SKIPPED // Entity already fully migrated or no valid data
  }
}
