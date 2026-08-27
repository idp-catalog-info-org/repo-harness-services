/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.ccp.utils.CatalogCustomPropertiesUtils.insertMap;
import static io.harness.idp.ccp.utils.CatalogCustomPropertiesUtils.removePropertiesFromYaml;
import static io.harness.idp.common.CommonUtils.buildMap;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.JacksonUtils.readValueForSingleEntity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.repositories.CatalogCustomPropertiesRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.CustomPropertiesBase;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CatalogDecoratorPropertyMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject IdpCommonService idpCommonService;
  @Inject CatalogCustomPropertiesRepository ccpRepository;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject IDPGitXHelper idpGitXHelper;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding catalog decorator property.");
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    accountIdentifiers.forEach(accountIdentifier -> {
      try {
        log.info("Starting the migration for adding catalog decorator property for accountId {}.", accountIdentifier);
        boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
        List<CatalogCustomPropertyEntity> ccpEntities = ccpRepository.findByAccountIdentifier(accountIdentifier);
        Map<String, List<CatalogCustomPropertyEntity>> ccpEntityMap =
            ccpEntities.stream().collect(Collectors.groupingBy(CatalogCustomPropertyEntity::getEntityRef));
        Set<String> entityRefs = new HashSet<>();
        for (Map.Entry<String, List<CatalogCustomPropertyEntity>> ccpEntity : ccpEntityMap.entrySet()) {
          entityRefs.add(ccpEntity.getKey());
        }
        List<CatalogEntity> existingCatalogEntities =
            catalogEntityRepository
                .getEntities(accountIdentifier,
                    catalogServiceHelper
                        .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, null, String.join(",", entityRefs))
                        .getLeft(),
                    null, null, null, null, null, String.join(",", entityRefs), null, null, null, null, null, null,
                    null)
                .getContent();
        List<CatalogEntity> modifiedCatalogEntities = new ArrayList<>();
        for (CatalogEntity existingCatalogEntity : existingCatalogEntities) {
          List<CatalogCustomPropertyEntity> ccpEntitiesByEntityRef =
              ccpEntityMap.get(CatalogUtils.entityRef(existingCatalogEntity, isIdpV2Enabled));
          if (!isEmpty(ccpEntitiesByEntityRef)) {
            Map<String, Object> decorator = existingCatalogEntity.getFailSafeDecorator();
            Map<String, Object> processedData = existingCatalogEntity.getFailSafeProcessedData(decorator);
            for (CatalogCustomPropertyEntity ccpEntityByEntityRef : ccpEntitiesByEntityRef) {
              String field = ccpEntityByEntityRef.getField();
              Object value = readValueForSingleEntity(ccpEntityByEntityRef.getValue(), Object.class);
              CustomPropertiesBase.ModeEnum mode = ccpEntityByEntityRef.getMode();
              insertMap(processedData, buildMap(field, value), mode);
            }
            String yaml = removePropertiesFromYaml(existingCatalogEntity.getYaml(),
                ccpEntitiesByEntityRef.stream()
                    .map(CatalogCustomPropertyEntity::getField)
                    .collect(Collectors.toList()));
            ScopeInfo scopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(existingCatalogEntity.getAccountIdentifier())
                                      .orgIdentifier(existingCatalogEntity.getOrgIdentifier())
                                      .projectIdentifier(existingCatalogEntity.getProjectIdentifier())
                                      .uniqueId(existingCatalogEntity.getParentUniqueId())
                                      .build();
            if (existingCatalogEntity instanceof GitReferencedCatalogEntity) {
              GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
            } else {
              GitAwareContextHelper.updateGitEntityContextWithInlineStoreType();
            }
            CatalogEntity modifiedEntity = CatalogMapper.yamlToEntity(
                scopeInfo, existingCatalogEntity.getIdentifier(), existingCatalogEntity.getKind(), yaml, null);
            modifiedEntity.setId(existingCatalogEntity.getId());
            modifiedEntity.setCreatedAt(existingCatalogEntity.getCreatedAt());
            modifiedEntity.setCreatedBy(existingCatalogEntity.getCreatedBy());
            modifiedEntity.setLastUpdatedAt(existingCatalogEntity.getLastUpdatedAt());
            modifiedEntity.setLastUpdatedBy(existingCatalogEntity.getLastUpdatedBy());
            idpGitXHelper.addGitParamsFromExistingEntity(modifiedEntity, existingCatalogEntity);
            decorator.put(PROCESSED_DATA, processedData);
            modifiedEntity.setDecorator(decorator);
            modifiedCatalogEntities.add(modifiedEntity);
          }
        }
        if (!isEmpty(modifiedCatalogEntities)) {
          modifiedCatalogEntities.forEach(catalogEntity
              -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
          catalogEntityRepository.saveAll(modifiedCatalogEntities);
        }
        log.info("Completed the migration for adding catalog decorator property for accountId {}.", accountIdentifier);
      } catch (Exception e) {
        log.error("Error occurred while running the migration for adding catalog decorator property for accountId {}",
            accountIdentifier);
      }
    });
    log.info("Completed the migration for adding catalog decorator property.");
  }
}
