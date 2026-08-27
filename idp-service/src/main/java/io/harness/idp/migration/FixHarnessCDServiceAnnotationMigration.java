/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class FixHarnessCDServiceAnnotationMigration implements NGMigration {
  static final String HARNESS_IO_SERVICES = "harness.io/services";
  static final String HARNESS_IO_SERVICES_ENCODED = "harness__dot__io/services";

  private static final Pattern YAML_SERVICES_LIST_BLOCK =
      Pattern.compile("harness\\.io/services:\\s*\\n((?:\\s+- [^\\n]+\\n?)+)", Pattern.MULTILINE);

  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject MongoTemplate mongoTemplate;
  @Inject CatalogService catalogService;
  @Inject IDPGitXHelper idpGitXHelper;

  @Override
  public void migrate() {
    log.info("Starting FixHarnessCDServiceAnnotationMigration.");

    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    for (String accountIdentifier : accountIdentifiers) {
      try {
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
        migrateAccount(accountIdentifier);
      } catch (Exception e) {
        log.error("FixHarnessCDServiceAnnotationMigration failed for account {}", accountIdentifier, e);
      }
    }

    log.info("Completed FixHarnessCDServiceAnnotationMigration.");
  }

  private void migrateAccount(String accountIdentifier) {
    Criteria criteria =
        new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier).is(accountIdentifier),
            new Criteria().orOperator(
                Criteria.where("metadata.annotations." + HARNESS_IO_SERVICES_ENCODED).type(BsonType.ARRAY.getValue()),
                Criteria.where(CatalogEntity.CatalogKeys.yaml).regex(YAML_SERVICES_LIST_BLOCK)));

    Query query = new Query(criteria);
    List<CatalogEntity> entities = mongoTemplate.find(query, CatalogEntity.class);

    log.info("Found {} candidate catalog entities for harness.io/services fix in account {}", entities.size(),
        accountIdentifier);

    int fixed = 0;
    int skipped = 0;
    int errors = 0;

    for (CatalogEntity entity : entities) {
      try {
        if (migrateEntity(entity)) {
          fixed++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        errors++;
        log.error("Error fixing harness.io/services for entity {}", entity.getIdentifier(), e);
      }
    }

    log.info("Account {}: fixed {}, skipped {}, errors {}", accountIdentifier, fixed, skipped, errors);
  }

  @SuppressWarnings("unchecked")
  private boolean migrateEntity(CatalogEntity catalogEntity) {
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

    List<String> flawedItems = extractFlawedListFromEntityMetadata(catalogEntity.getMetadata());
    if (isEmpty(flawedItems)) {
      return false;
    }

    String harnessServiceIdentifier = extractHarnessServiceIdentifier(yamlMap);
    String resolved = resolveServicesAnnotationValue(flawedItems, harnessServiceIdentifier);
    if (isEmpty(resolved)) {
      return false;
    }

    applyResolvedAnnotation(yamlMap, catalogEntity, resolved);
    String fixedYaml = writeObjectAsYaml(yamlMap);
    catalogEntity.setYaml(fixedYaml);

    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      updateGitXEntity(catalogEntity, fixedYaml);
    } else {
      catalogEntityRepository.save(catalogEntity);
    }

    log.info("Fixed harness.io/services annotation for entity {}", catalogEntity.getIdentifier());
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
      gitUpdateDetails.setCommitMessage(gitDetails.getCommitMessage());
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
  private static void applyResolvedAnnotation(Map<String, Object> yamlMap, CatalogEntity entity, String resolved) {
    Map<String, Object> metadata = (Map<String, Object>) yamlMap.computeIfAbsent("metadata", k -> new HashMap<>());
    Map<String, Object> annotations =
        (Map<String, Object>) metadata.computeIfAbsent("annotations", k -> new HashMap<>());
    annotations.remove(HARNESS_IO_SERVICES);
    annotations.remove(HARNESS_IO_SERVICES_ENCODED);
    annotations.put(HARNESS_IO_SERVICES, resolved);

    // Replace top-level metadata with a new map so Spring Data Mongo persists nested changes (in-place nested
    // mutation on the loaded entity is often not detected on save).
    entity.setMetadata(shallowCopyMetadataWithFixedHarnessServicesAnnotation(entity.getMetadata(), resolved));
  }

  /**
   * Copies all top-level metadata keys by reference except {@code annotations}, which is copied and updated so the
   * document field {@code metadata} is persisted on save.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> shallowCopyMetadataWithFixedHarnessServicesAnnotation(
      Map<String, Object> existing, String resolved) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (existing != null) {
      for (Map.Entry<String, Object> e : existing.entrySet()) {
        if ("annotations".equals(e.getKey())) {
          continue;
        }
        out.put(e.getKey(), e.getValue());
      }
    }
    Map<String, Object> ann = new LinkedHashMap<>();
    if (existing != null && existing.get("annotations") instanceof Map) {
      ann.putAll((Map<String, Object>) existing.get("annotations"));
    }
    ann.remove(HARNESS_IO_SERVICES);
    ann.remove(HARNESS_IO_SERVICES_ENCODED);
    ann.put(HARNESS_IO_SERVICES, resolved);
    out.put("annotations", ann);
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<String> extractFlawedListFromEntityMetadata(Map<String, Object> entityMetadata) {
    if (entityMetadata == null) {
      return null;
    }
    Object annotationsObj = entityMetadata.get("annotations");
    if (!(annotationsObj instanceof Map)) {
      return null;
    }
    Map<String, Object> annotations = (Map<String, Object>) annotationsObj;
    for (String key : List.of(HARNESS_IO_SERVICES, HARNESS_IO_SERVICES_ENCODED)) {
      Object value = annotations.get(key);
      if (value instanceof List) {
        return toTrimmedStringList((List<?>) value);
      }
    }
    return null;
  }

  private static List<String> toTrimmedStringList(List<?> raw) {
    List<String> out = new ArrayList<>();
    for (Object o : raw) {
      if (o == null) {
        continue;
      }
      String s = o.toString().trim();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static String extractHarnessServiceIdentifier(Map<String, Object> yamlMap) {
    Object specObj = yamlMap.get("spec");
    if (!(specObj instanceof Map)) {
      return null;
    }
    Map<String, Object> spec = (Map<String, Object>) specObj;
    Object hsObj = spec.get("harnessService");
    if (!(hsObj instanceof Map)) {
      return null;
    }
    Map<String, Object> harnessService = (Map<String, Object>) hsObj;
    Object id = harnessService.get("identifier");
    if (id instanceof String && !isEmpty((String) id)) {
      return (String) id;
    }
    return null;
  }

  /**
   * Picks the annotation line that starts with {@code "<identifier>:"} when {@code harnessServiceIdentifier} is
   * present; otherwise uses the last list entry.
   */
  private static String resolveServicesAnnotationValue(List<String> flawedItems, String harnessServiceIdentifier) {
    if (isEmpty(flawedItems)) {
      return null;
    }
    if (!isEmpty(harnessServiceIdentifier)) {
      String prefix = harnessServiceIdentifier + ":";
      for (String item : flawedItems) {
        if (item != null && item.startsWith(prefix)) {
          return item.trim();
        }
      }
    }
    return flawedItems.get(flawedItems.size() - 1).trim();
  }
}
