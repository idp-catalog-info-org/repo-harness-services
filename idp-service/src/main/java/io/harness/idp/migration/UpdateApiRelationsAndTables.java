/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.support.TransactionTemplate;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class UpdateApiRelationsAndTables implements NGMigration {
  @Inject private CatalogService catalogService;
  @Inject private NamespaceService namespaceService;
  @Inject private NamespaceRepository namespaceRepository;
  @Inject private CatalogEntityRepository catalogEntityRepository;
  @Inject private IDPGitXHelper idpGitXHelper;
  @Inject private TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Override
  public void migrate() {
    log.info("Starting UpdateApiRelationsAndTables migration");
    try {
      Set<String> accountIds = namespaceRepository.findAllByIsDeleted(false)
                                   .stream()
                                   .map(NamespaceEntity::getAccountIdentifier)
                                   .collect(Collectors.toSet());

      if (CollectionUtils.isEmpty(accountIds)) {
        log.info("No accounts found for UpdateApiRelationsAndTables migration");
        return;
      }
      log.info("Found {} accounts to process UpdateApiRelationsAndTables migration", accountIds.size());
      processApiRelationsAndTablesForAllAccounts(accountIds);
      log.info("Successfully completed UpdateApiRelationsAndTables migration");
    } catch (Exception e) {
      log.error("Error during UpdateApiRelationsAndTables migration", e);
    }
  }
  private void processApiRelationsAndTablesForAllAccounts(Set<String> accountIds) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    for (String accountId : accountIds) {
      try {
        GetEntitiesDTO getEntitiesDTO = catalogService.getEntities(accountId, 0, -1, null, null, false, "account.*",
            null, false, false, "component,api,resource", null, null, null, null, null, false);
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
        List<EntityResponse> catalogEntities = getEntitiesDTO.getEntityResponses();
        log.info("Found {} entities for account {}", catalogEntities.size(), accountId);
        catalogEntities =
            catalogEntities.stream()
                .filter(catalogEntity -> {
                  if (catalogEntity.getRelations() != null) {
                    Map<String, Set<String>> relations = (Map<String, Set<String>>) catalogEntity.getRelations();
                    return (relations.get("consumesApis") != null && !relations.get("consumesApis").isEmpty());
                  }
                  return false;
                })
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(catalogEntities)) {
          log.info("Processing {} entities for account {}", catalogEntities.size(), accountId);
          processRelationsAndTablesForEachAccount(catalogEntities, accountId);
        } else {
          log.info("No entities found for account {}", accountId);
        }
      } catch (Exception e) {
        log.error("Failed to update Api relations and tables for account {}: {}", accountId, e.getMessage(), e);
      }
    }
  }

  private void processRelationsAndTablesForEachAccount(List<EntityResponse> catalogEntities, String accountIdentifier) {
    for (EntityResponse catalogEntity : catalogEntities) {
      try {
        String entityRef = catalogEntity.getEntityRef();
        EntityUpdateRequest updateRequest = new EntityUpdateRequest();
        updateRequest.setYaml(catalogEntity.getYaml());

        if (catalogEntity.getReferenceType().equals(EntityResponse.ReferenceTypeEnum.INLINE)) {
          updateRelationsAndUpdateEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
              catalogEntity.getProjectIdentifier(), entityRef, updateRequest, false, false, false, false);
          log.info("Successfully updated catalog entity {}", catalogEntity.getName());
        } else {
          GitUpdateDetails gitUpdateDetails = createGitUpdateDetails(catalogEntity);
          updateRequest.setGitDetails(gitUpdateDetails);

          if (updateRequest.getGitDetails() != null) {
            try {
              GitAwareContextHelper.populateGitDetails(
                  idpGitXHelper.populateGitUpdateDetails(updateRequest.getGitDetails()));
              updateRelationsAndUpdateEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
                  catalogEntity.getProjectIdentifier(), entityRef, updateRequest, false, true, false, false);
              log.info("Successfully updated catalog entity {}", catalogEntity.getName());
            } finally {
              GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
            }
          } else {
            log.info(
                "Updating catalog entity {} failed because git details are not available", catalogEntity.getName());
          }
        }
      } catch (Exception e) {
        log.error("Failed to update Api relations and tables for catalog entity {}: {}", catalogEntity.getIdentifier(),
            e.getMessage(), e);
      }
    }
  }
  private void updateRelationsAndUpdateEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, EntityUpdateRequest body, boolean shouldValidateRBAC, boolean shouldUpdateOnGit,
      boolean shouldCheckExistingSourceValidation, boolean metadataEnrichmentByUser) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      String originalYaml = body.getYaml();
      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(originalYaml);
      Map<String, Object> spec = (Map<String, Object>) yamlMap.get("spec");

      // Find and temporarily remove consumesApis relation if present
      String relationToRemove = null;
      Object removedValue = null;

      if (spec != null && spec.containsKey("consumesApis") && spec.get("consumesApis") != null) {
        Object consumesApisValue = spec.get("consumesApis");
        if (consumesApisValue instanceof List) {
          List<String> relationValues = (List<String>) consumesApisValue;
          if (!relationValues.isEmpty()) {
            relationToRemove = "consumesApis";
            removedValue = spec.remove("consumesApis");
          }
        }
      }

      // Update entity with removed relation
      if (relationToRemove != null) {
        String modifiedYaml = YamlUtils.writeObjectAsYaml(yamlMap);
        EntityUpdateRequest modifiedRequest = new EntityUpdateRequest();
        modifiedRequest.setYaml(modifiedYaml);
        if (shouldUpdateOnGit) {
          modifiedRequest.setGitDetails(body.getGitDetails());
        }
        catalogService.updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, modifiedRequest,
            shouldValidateRBAC, shouldUpdateOnGit, shouldCheckExistingSourceValidation, metadataEnrichmentByUser);
        // Re-add the removed relation and update again

        EntityResponse updatedEntity = catalogService.getEntity(
            harnessAccount, orgIdentifier, projectIdentifier, entityRef, false, false, false, shouldValidateRBAC);

        spec.put(relationToRemove, removedValue);
        String restoredYaml = YamlUtils.writeObjectAsYaml(yamlMap);
        EntityUpdateRequest restoredRequest = new EntityUpdateRequest();
        restoredRequest.setYaml(restoredYaml);
        if (shouldUpdateOnGit) {
          restoredRequest.setGitDetails(createGitUpdateDetails(updatedEntity));
        }
        catalogService.updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, restoredRequest,
            shouldValidateRBAC, shouldUpdateOnGit, shouldCheckExistingSourceValidation, metadataEnrichmentByUser);
      } else {
        catalogService.updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, body,
            shouldValidateRBAC, shouldUpdateOnGit, shouldCheckExistingSourceValidation, metadataEnrichmentByUser);
      }

      return true;
    }));
  }

  private GitUpdateDetails createGitUpdateDetails(EntityResponse catalogEntity) {
    GitDetails gitDetails = catalogEntity.getGitDetails();
    GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
    gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.valueOf(gitDetails.getStoreType().name()));
    gitUpdateDetails.setRepoName(gitDetails.getRepoName());
    gitUpdateDetails.setLastObjectId(gitDetails.getObjectId());
    gitUpdateDetails.setLastCommitId(gitDetails.getCommitId());
    gitUpdateDetails.setIsHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());
    gitUpdateDetails.setFilePath(gitDetails.getFilePath());
    gitUpdateDetails.setConnectorRef(gitDetails.getConnectorRef());
    gitUpdateDetails.setCommitMessage(String.format(
        "Update Idp catalog %s as part of UpdateApiRelationsAndTables migration", catalogEntity.getName()));
    gitUpdateDetails.setBranchName(gitDetails.getBranchName());
    gitUpdateDetails.setBaseBranch(gitDetails.getBaseBranch());
    return gitUpdateDetails;
  }
}
