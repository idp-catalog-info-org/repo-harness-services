/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.YamlUtils.loadYamlStringAsMap;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

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
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class HarnessCDMetadataToIntegrationPropertiesMigration implements NGMigration {
  private static final Set<String> PROPS_TO_MOVE = Set.of("deploymentFrequencyPerSprint", "changeFailureRatePercent");
  private static final String HARNESS_CD_TYPE = "HarnessCD";
  private static final String HARNESS_CD_INTEGRATION_ID = "harness-cd-integration";
  private static final String ACCOUNT_KEYWORD = "account";
  private static final String METADATA_PREFIX = "metadata.";
  private static final String INTEGRATION_PROPERTIES_PREFIX =
      METADATA_PREFIX + CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES + "." + HARNESS_CD_TYPE + ".";
  private static final String AGGREGATION_RULES_COLLECTION = "aggregationRules";
  private static final String FIELD_FOR_AGG = "fieldForAgg";

  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogServiceHelper catalogServiceHelper;

  @Override
  public void migrate() {
    log.info("Starting HarnessCD metadata to integration_properties migration.");

    migrateCatalogEntities();
    migrateAggregationRules();

    log.info("Completed HarnessCD metadata to integration_properties migration.");
  }

  private void migrateCatalogEntities() {
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        log.info("Starting HarnessCD metadata migration for accountId {}.", accountIdentifier);
        migrateCatalogEntitiesForAccount(accountIdentifier);
        log.info("Completed HarnessCD metadata migration for accountId {}.", accountIdentifier);
      } catch (Exception e) {
        log.error("Error during HarnessCD metadata migration for accountId {}", accountIdentifier, e);
      }
    });
  }

  private void migrateCatalogEntitiesForAccount(String accountIdentifier) {
    Criteria criteria =
        Criteria.where("accountIdentifier")
            .is(accountIdentifier)
            .and(String.format("decorator.%s.metadata.%s.%s.%s", PROCESSED_DATA,
                CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX, ACCOUNT_KEYWORD, HARNESS_CD_INTEGRATION_ID))
            .exists(true);

    Query query = new Query(criteria);
    List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);

    log.info("Found {} HarnessCD linked entities for account {}", catalogEntities.size(), accountIdentifier);

    int successCount = 0;
    int skippedCount = 0;
    int errorCount = 0;

    for (CatalogEntity catalogEntity : catalogEntities) {
      try {
        if (migrateEntity(catalogEntity)) {
          successCount++;
        } else {
          skippedCount++;
        }
      } catch (Exception e) {
        log.error("Error migrating entity {}", catalogEntity.getIdentifier(), e);
        errorCount++;
      }
    }

    log.info("Catalog entity migration complete for account {}: {} succeeded, {} skipped, {} errors", accountIdentifier,
        successCount, skippedCount, errorCount);
  }

  @SuppressWarnings("unchecked")
  private boolean migrateEntity(CatalogEntity catalogEntity) {
    String yaml = catalogEntity.getYaml();
    if (isEmpty(yaml)) {
      return false;
    }

    Map<String, Object> yamlMap = loadYamlStringAsMap(yaml);
    Object metadataObj = yamlMap.get("metadata");
    if (!(metadataObj instanceof Map)) {
      return false;
    }
    Map<String, Object> yamlMetadata = (Map<String, Object>) metadataObj;

    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);
    Map<String, Object> metadataInProcessedData =
        (Map<String, Object>) processedData.computeIfAbsent("metadata", k -> new HashMap<>());
    Map<String, Object> integrationProperties = (Map<String, Object>) metadataInProcessedData.computeIfAbsent(
        CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES, k -> new HashMap<>());
    Map<String, Object> harnessCdProps =
        (Map<String, Object>) integrationProperties.computeIfAbsent(HARNESS_CD_TYPE, k -> new HashMap<>());

    boolean migrated = false;

    for (String prop : PROPS_TO_MOVE) {
      if (harnessCdProps.containsKey(prop)) {
        if (yamlMetadata.containsKey(prop)) {
          yamlMetadata.remove(prop);
          migrated = true;
        }
        continue;
      }

      if (yamlMetadata.containsKey(prop)) {
        Object value = yamlMetadata.remove(prop);
        harnessCdProps.put(prop, value);
        migrated = true;
        log.info("Moved '{}' from YAML metadata to integration_properties.{} for entity {}", prop, HARNESS_CD_TYPE,
            catalogEntity.getIdentifier());
      }
    }

    if (migrated) {
      catalogEntity.setYaml(writeObjectAsYaml(yamlMap));

      Map<String, Object> entityMetadata = catalogEntity.getMetadata();
      if (entityMetadata != null) {
        PROPS_TO_MOVE.forEach(entityMetadata::remove);
        catalogEntity.setMetadata(entityMetadata);
      }

      PROPS_TO_MOVE.forEach(prop -> {
        integrationProperties.remove(prop);
        metadataInProcessedData.remove(prop);
      });

      decorator.put(PROCESSED_DATA, processedData);
      catalogEntity.setDecorator(decorator);
      catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
      catalogEntityRepository.save(catalogEntity);
    }

    return migrated;
  }

  private void migrateAggregationRules() {
    log.info("Starting aggregation rules fieldForAgg migration.");

    for (String prop : PROPS_TO_MOVE) {
      // Handle original field: metadata.prop -> metadata.integration_properties.harnesscd.prop
      String oldField = METADATA_PREFIX + prop;
      String newField = INTEGRATION_PROPERTIES_PREFIX + prop;

      Query query = new Query(Criteria.where(FIELD_FOR_AGG).is(oldField));
      Update update = new Update().set(FIELD_FOR_AGG, newField);

      var result = mongoTemplate.updateMulti(query, update, AGGREGATION_RULES_COLLECTION);
      log.info("Updated {} aggregation rules: fieldForAgg '{}' -> '{}'", result.getModifiedCount(), oldField, newField);

      String buggyField = METADATA_PREFIX + CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES + "." + prop;
      if (!buggyField.equals(newField)) {
        Query buggyQuery = new Query(Criteria.where(FIELD_FOR_AGG).is(buggyField));
        Update buggyUpdate = new Update().set(FIELD_FOR_AGG, newField);

        var buggyResult = mongoTemplate.updateMulti(buggyQuery, buggyUpdate, AGGREGATION_RULES_COLLECTION);
        log.info("Updated {} aggregation rules (buggy path): fieldForAgg '{}' -> '{}'", buggyResult.getModifiedCount(),
            buggyField, newField);
      }
    }

    log.info("Completed aggregation rules fieldForAgg migration.");
  }
}
