/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.integrations.service.catalog.CatalogIntegrationEntityConstants;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class IntegrationConfigRefBackfillMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject IntegrationManagerClientHelper integrationManagerClientHelper;

  @Override
  public void migrate() {
    log.info("Starting integration_config_ref backfill migration.");

    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

    List<String> accountIdentifiers = namespaceService.getAccountIds();
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        log.info("Starting integration_config_ref backfill for accountId {}.", accountIdentifier);
        migrateAccount(accountIdentifier);
        log.info("Completed integration_config_ref backfill for accountId {}.", accountIdentifier);
      } catch (Exception e) {
        log.error("Error during integration_config_ref backfill for accountId {}", accountIdentifier, e);
      }
    });

    log.info("Completed integration_config_ref backfill migration.");
  }

  @SuppressWarnings("unchecked")
  private void migrateAccount(String accountIdentifier) {
    Criteria criteria = Criteria.where("accountIdentifier")
                            .is(accountIdentifier)
                            .and(String.format("decorator.%s.metadata.%s", PROCESSED_DATA,
                                CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX))
                            .exists(true)
                            .and(String.format("decorator.%s.integration_config_ref", PROCESSED_DATA))
                            .exists(false);

    Query query = new Query(criteria);
    List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);

    log.info("Found {} entities to backfill integration_config_ref for account {}", catalogEntities.size(),
        accountIdentifier);

    Map<String, TypesIntegrationConfig.EnumIntegrationType> configTypeCache = new HashMap<>();

    int successCount = 0;
    int skippedCount = 0;
    int errorCount = 0;

    for (CatalogEntity catalogEntity : catalogEntities) {
      try {
        if (migrateEntity(catalogEntity, accountIdentifier, configTypeCache)) {
          successCount++;
        } else {
          skippedCount++;
        }
      } catch (Exception e) {
        log.error("Error backfilling integration_config_ref for entity {}", catalogEntity.getIdentifier(), e);
        errorCount++;
      }
    }

    log.info("integration_config_ref backfill complete for account {}: {} succeeded, {} skipped, {} errors",
        accountIdentifier, successCount, skippedCount, errorCount);
  }

  @SuppressWarnings("unchecked")
  private boolean migrateEntity(CatalogEntity catalogEntity, String accountIdentifier,
      Map<String, TypesIntegrationConfig.EnumIntegrationType> configTypeCache) {
    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);

    if (isEmpty(processedData)) {
      return false;
    }

    Object metadataObj = processedData.get("metadata");
    if (!(metadataObj instanceof Map)) {
      return false;
    }

    Map<String, Object> metadata = (Map<String, Object>) metadataObj;
    Object integrationObj = metadata.get(CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX);
    if (!(integrationObj instanceof Map)) {
      return false;
    }

    Map<String, Object> integrationConfigRef = new HashMap<>();
    Map<String, Object> integrationMap = (Map<String, Object>) integrationObj;

    for (Map.Entry<String, Object> spacePathEntry : integrationMap.entrySet()) {
      String spacePath = spacePathEntry.getKey();
      if (!(spacePathEntry.getValue() instanceof Map)) {
        continue;
      }

      Map<String, Object> configIdMap = (Map<String, Object>) spacePathEntry.getValue();
      for (Map.Entry<String, Object> configIdEntry : configIdMap.entrySet()) {
        String configId = configIdEntry.getKey();
        if (!(configIdEntry.getValue() instanceof Map)) {
          continue;
        }

        TypesIntegrationConfig.EnumIntegrationType integrationType =
            resolveIntegrationType(accountIdentifier, spacePath, configId, configTypeCache);
        if (integrationType == null) {
          log.warn("Could not resolve integration type for spacePath={}, configId={}, entity={}", spacePath, configId,
              catalogEntity.getIdentifier());
          continue;
        }

        Map<String, Object> kindMap = (Map<String, Object>) configIdEntry.getValue();
        for (String kind : kindMap.keySet()) {
          String refKey = integrationType.name() + "." + kind;
          String refValue = spacePath + "." + configId;
          List<String> refList = (List<String>) integrationConfigRef.computeIfAbsent(refKey, k -> new ArrayList<>());
          if (!refList.contains(refValue)) {
            refList.add(refValue);
          }
        }
      }
    }

    if (integrationConfigRef.isEmpty()) {
      return false;
    }

    processedData.put("integration_config_ref", integrationConfigRef);
    decorator.put(PROCESSED_DATA, processedData);
    catalogEntity.setDecorator(decorator);
    catalogEntityRepository.save(catalogEntity);
    return true;
  }

  private TypesIntegrationConfig.EnumIntegrationType resolveIntegrationType(String accountIdentifier, String spacePath,
      String configId, Map<String, TypesIntegrationConfig.EnumIntegrationType> configTypeCache) {
    String cacheKey = spacePath + "." + configId;
    if (configTypeCache.containsKey(cacheKey)) {
      return configTypeCache.get(cacheKey);
    }

    try {
      String denormalizedSpacePath = spacePath.replaceFirst("^account", accountIdentifier);
      String[] parts = denormalizedSpacePath.split("/");
      String orgIdentifier = parts.length > 1 ? parts[1] : null;
      String projectIdentifier = parts.length > 2 ? parts[2] : null;

      TypesIntegrationConfig config = getGeneralResponse(integrationManagerClientHelper.getIntegrationConfig(
          accountIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, configId));

      TypesIntegrationConfig.EnumIntegrationType type = config.getIntegrationType();
      configTypeCache.put(cacheKey, type);
      return type;
    } catch (Exception e) {
      log.warn(
          "Failed to fetch integration config for spacePath={}, configId={}: {}", spacePath, configId, e.getMessage());
      configTypeCache.put(cacheKey, null);
      return null;
    }
  }
}
