/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.idp.integrations.service.catalog.CatalogIntegrationEntityConstants;
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
public class IntegrationKeyedStructureMigration implements NGMigration {
  // Old integration types that lived directly under metadata as keys
  private static final Set<String> INTEGRATION_TYPES = Set.of("HarnessCD", "HarnessScope", "ServiceNow");
  private static final String ACCOUNT_KEYWORD = "account";

  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogServiceHelper catalogServiceHelper;

  @Override
  public void migrate() {
    log.info("Starting integration keyed structure migration for all accounts.");

    List<String> accountIdentifiers = namespaceService.getAccountIds();
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        log.info("Starting integration keyed structure migration for accountId {}.", accountIdentifier);
        migrateIntegrations(accountIdentifier);
        log.info("Completed integration keyed structure migration for accountId {}.", accountIdentifier);
      } catch (Exception e) {
        log.error("Error during integration keyed structure migration for accountId {}", accountIdentifier, e);
      }
    });

    log.info("Completed integration keyed structure migration for all accounts.");
  }

  private void migrateIntegrations(String accountIdentifier) {
    try {
      Criteria criteria = Criteria.where("accountIdentifier")
                              .is(accountIdentifier)
                              .and(String.format("decorator.%s.metadata", PROCESSED_DATA))
                              .exists(true);

      Query query = new Query(criteria);
      List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);

      log.info("Found {} entities to check for integration migration", catalogEntities.size());

      int successCount = 0;
      int skippedCount = 0;
      int errorCount = 0;

      for (CatalogEntity catalogEntity : catalogEntities) {
        try {
          MigrationResult result = migrateEntity(catalogEntity);
          switch (result) {
            case SUCCESS:
              successCount++;
              break;
            case SKIPPED:
              skippedCount++;
              break;
            case ERROR:
              errorCount++;
              break;
          }
        } catch (Exception e) {
          log.error("Error migrating entity {}", catalogEntity.getIdentifier(), e);
          errorCount++;
        }
      }

      log.info("Migration complete: {} succeeded, {} skipped, {} errors", successCount, skippedCount, errorCount);
    } catch (Exception e) {
      log.error("Error processing integration migration for account {}", accountIdentifier, e);
    }
  }

  @SuppressWarnings("unchecked")
  private MigrationResult migrateEntity(CatalogEntity catalogEntity) {
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
    boolean migrated = false;

    Map<String, Object> accountMap = new HashMap<>();

    for (String integrationType : INTEGRATION_TYPES) {
      Object integrationTypeObj = metadata.get(integrationType);
      if (!(integrationTypeObj instanceof Map)) {
        continue;
      }

      Map<String, Object> integrationTypeMap = (Map<String, Object>) integrationTypeObj;
      log.info("Migrating integrationType '{}' with {} integrationIds for entity {}", integrationType,
          integrationTypeMap.size(), catalogEntity.getIdentifier());

      for (Map.Entry<String, Object> integrationIdEntry : integrationTypeMap.entrySet()) {
        String integrationId = integrationIdEntry.getKey();
        Object integrationIdObj = integrationIdEntry.getValue();

        if (!(integrationIdObj instanceof Map)) {
          continue;
        }

        Map<String, Object> propsMap = (Map<String, Object>) integrationIdObj;

        Object entityKindObj = propsMap.get("entity_kind");
        if (!(entityKindObj instanceof String) || ((String) entityKindObj).isEmpty()) {
          log.warn("Skipping integrationId '{}' for entity {} - missing or invalid entity_kind", integrationId,
              catalogEntity.getIdentifier());
          continue;
        }

        String entityKind = (String) entityKindObj;

        Map<String, Object> kindData = new HashMap<>();
        if (propsMap.containsKey("entity_uuid")) {
          kindData.put("entity_uuid", propsMap.get("entity_uuid"));
        }
        if (propsMap.containsKey("entity_action")) {
          kindData.put("entity_action", propsMap.get("entity_action"));
        }

        if (kindData.isEmpty()) {
          log.warn("Skipping integrationId '{}' kind '{}' for entity {} - missing entity_uuid and entity_action",
              integrationId, entityKind, catalogEntity.getIdentifier());
          continue;
        }

        Map<String, Object> newIntegrationIdMap = accountMap.containsKey(integrationId)
            ? (Map<String, Object>) accountMap.get(integrationId)
            : new HashMap<>();
        newIntegrationIdMap.put(entityKind, kindData);
        accountMap.put(integrationId, newIntegrationIdMap);
      }

      metadata.remove(integrationType);
      migrated = true;
    }

    if (migrated && !accountMap.isEmpty()) {
      Map<String, Object> integrationMap = new HashMap<>();
      integrationMap.put(ACCOUNT_KEYWORD, accountMap);
      metadata.put(CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX, integrationMap);

      saveEntity(catalogEntity, decorator, processedData);
      return MigrationResult.SUCCESS;
    }

    return MigrationResult.SKIPPED;
  }

  private void saveEntity(
      CatalogEntity catalogEntity, Map<String, Object> decorator, Map<String, Object> processedData) {
    decorator.put(PROCESSED_DATA, processedData);
    catalogEntity.setDecorator(decorator);
    catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
    catalogEntityRepository.save(catalogEntity);
  }

  private enum MigrationResult { SUCCESS, SKIPPED, ERROR }
}
