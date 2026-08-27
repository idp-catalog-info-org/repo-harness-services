/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.YamlUtils.loadYamlStringAsMap;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Migration to update harness.io/services annotation URLs for all existing Harness CD catalog entities
 * when the harnessCiCdAnnotationsServiceUrl configuration changes.
 */
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class UpdateHarnessCDServiceAnnotationUrlMigration implements NGMigration {
  private static final String HARNESS_IO_SERVICES = "harness.io/services";
  private static final String HARNESS_IO_SERVICES_ENCODED = "harness__dot__io/services";

  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogService catalogService;
  @Inject IDPGitXHelper idpGitXHelper;
  @Inject IdpCommonService idpCommonService;
  @Inject @Named("harnessCiCdAnnotationsServiceUrl") String harnessCiCdAnnotationsServiceUrl;
  @Inject @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig;

  @Override
  public void migrate() {
    log.info("Starting UpdateHarnessCDServiceAnnotationUrlMigration.");

    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    List<String> accountIdentifiers = namespaceService.getAccountIds();

    for (String accountIdentifier : accountIdentifiers) {
      try {
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
        migrateAccount(accountIdentifier);
      } catch (Exception e) {
        log.error("UpdateHarnessCDServiceAnnotationUrlMigration failed for account {}", accountIdentifier, e);
      }
    }

    log.info("Completed UpdateHarnessCDServiceAnnotationUrlMigration.");
  }

  private void migrateAccount(String accountIdentifier) {
    // Find all catalog entities with harnessService spec
    Criteria criteria =
        new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier).is(accountIdentifier),
            Criteria.where("spec.harnessService").exists(true));

    Query query = new Query(criteria);
    List<CatalogEntity> entities = mongoTemplate.find(query, CatalogEntity.class);

    log.info("Found {} catalog entities with harnessService in account {}", entities.size(), accountIdentifier);

    int updated = 0;
    int skipped = 0;
    int errors = 0;

    String accountBaseUrl = getAccountBaseUrl(accountIdentifier);

    for (CatalogEntity entity : entities) {
      try {
        if (migrateEntity(entity, accountBaseUrl)) {
          updated++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        errors++;
        log.error("Error updating harness.io/services annotation for entity {}", entity.getIdentifier(), e);
      }
    }

    log.info("Account {}: updated {}, skipped {}, errors {}", accountIdentifier, updated, skipped, errors);
  }

  @SuppressWarnings("unchecked")
  private boolean migrateEntity(CatalogEntity catalogEntity, String accountBaseUrl) {
    String yaml = catalogEntity.getYaml();
    if (isEmpty(yaml)) {
      return false;
    }

    final Map<String, Object> yamlMap;
    try {
      yamlMap = loadYamlStringAsMap(yaml);
    } catch (Exception e) {
      log.warn("Skipping entity {} — could not parse YAML", catalogEntity.getIdentifier(), e);
      return false;
    }

    // Extract harnessService details
    Map<String, Object> harnessServiceInfo = extractHarnessServiceInfo(yamlMap);
    if (harnessServiceInfo == null) {
      log.debug("Skipping entity {} — no harnessService found", catalogEntity.getIdentifier());
      return false;
    }

    String serviceAccountId = catalogEntity.getAccountIdentifier();
    String serviceOrgId = (String) harnessServiceInfo.get("orgIdentifier");
    String serviceProjectId = (String) harnessServiceInfo.get("projectIdentifier");
    String serviceIdentifier = (String) harnessServiceInfo.get("identifier");

    if (isEmpty(serviceIdentifier)) {
      log.warn("Skipping entity {} — harnessService has no identifier", catalogEntity.getIdentifier());
      return false;
    }

    // Generate new annotation URL
    String newAnnotationUrl = generateServiceAnnotationUrl(
        serviceAccountId, serviceOrgId, serviceProjectId, serviceIdentifier, accountBaseUrl);

    // Update the annotation
    Map<String, Object> metadata = (Map<String, Object>) yamlMap.computeIfAbsent("metadata", k -> new HashMap<>());
    Map<String, Object> annotations =
        (Map<String, Object>) metadata.computeIfAbsent("annotations", k -> new HashMap<>());

    String currentAnnotation = (String) annotations.get(HARNESS_IO_SERVICES);
    if (newAnnotationUrl.equals(currentAnnotation)) {
      log.debug("Skipping entity {} — annotation already up to date", catalogEntity.getIdentifier());
      return false;
    }

    annotations.put(HARNESS_IO_SERVICES, newAnnotationUrl);
    String fixedYaml = writeObjectAsYaml(yamlMap);
    catalogEntity.setYaml(fixedYaml);

    // Update entity metadata for MongoDB persistence
    catalogEntity.getMetadata().computeIfAbsent("annotations", k -> new HashMap<>());
    ((Map<String, Object>) catalogEntity.getMetadata().get("annotations")).put(HARNESS_IO_SERVICES, newAnnotationUrl);

    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      updateGitXEntity(catalogEntity, fixedYaml);
    } else {
      catalogEntityRepository.save(catalogEntity);
    }

    log.info(
        "Updated harness.io/services annotation for entity {}: {}", catalogEntity.getIdentifier(), newAnnotationUrl);
    return true;
  }

  private void updateGitXEntity(CatalogEntity catalogEntity, String fixedYaml) {
    String accountIdentifier = catalogEntity.getAccountIdentifier();
    String entityRef = CatalogUtils.entityRef(catalogEntity);

    EntityResponse getEntityResponse = catalogService.getEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
        catalogEntity.getProjectIdentifier(), entityRef, false, false, true, false);

    EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();
    entityUpdateRequest.setYaml(fixedYaml);

    GitDetails gitDetails = getEntityResponse.getGitDetails();
    if (gitDetails != null) {
      GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
      gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.valueOf(gitDetails.getStoreType().name()));
      gitUpdateDetails.setRepoName(gitDetails.getRepoName());
      gitUpdateDetails.setLastObjectId(gitDetails.getObjectId());
      gitUpdateDetails.setLastCommitId(gitDetails.getCommitId());
      gitUpdateDetails.setIsHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());
      gitUpdateDetails.setFilePath(gitDetails.getFilePath());
      gitUpdateDetails.setConnectorRef(gitDetails.getConnectorRef());
      gitUpdateDetails.setCommitMessage("Update harness.io/services annotation URL");
      gitUpdateDetails.setBranchName(gitDetails.getBranchName());
      gitUpdateDetails.setBaseBranch(gitDetails.getBaseBranch());
      entityUpdateRequest.setGitDetails(gitUpdateDetails);
      GitAwareContextHelper.populateGitDetails(
          idpGitXHelper.populateGitUpdateDetails(entityUpdateRequest.getGitDetails()));
    }

    catalogService.updateEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
        catalogEntity.getProjectIdentifier(), entityRef, entityUpdateRequest, false, true, false, false);
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractHarnessServiceInfo(Map<String, Object> yamlMap) {
    Object specObj = yamlMap.get("spec");
    if (!(specObj instanceof Map)) {
      return null;
    }
    Map<String, Object> spec = (Map<String, Object>) specObj;
    Object hsObj = spec.get("harnessService");
    if (!(hsObj instanceof Map)) {
      return null;
    }
    return (Map<String, Object>) hsObj;
  }

  private String generateServiceAnnotationUrl(
      String accountId, String orgId, String projectId, String serviceId, String accountBaseUrl) {
    String url = serviceId + ": " + accountBaseUrl
        + harnessCiCdAnnotationsServiceUrl.replace(ACCOUNT_KEY, accountId)
              .replace(ORG_KEY, !isEmpty(orgId) ? orgId : "")
              .replace(PROJECT_KEY, !isEmpty(projectId) ? projectId : "")
              .replace(SERVICE_IDENTIFIER_KEY, serviceId)
        + "\n";
    url = url.replaceAll("/orgs//", "/");
    url = url.replaceAll("/projects//", "/");
    return url.trim();
  }

  private String getAccountBaseUrl(String accountIdentifier) {
    AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
    String baseUrl = harnessCodeRepoConfig.getBaseUrl();

    try {
      if (accountDTO != null && !isEmpty(accountDTO.getSubdomainURL())) {
        baseUrl = accountDTO.getSubdomainURL();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
          baseUrl = "https://" + baseUrl;
        }
      }
    } catch (Exception e) {
      log.warn("Could not get account base URL for account {}, using default", accountIdentifier, e);
    }

    return baseUrl;
  }
}
