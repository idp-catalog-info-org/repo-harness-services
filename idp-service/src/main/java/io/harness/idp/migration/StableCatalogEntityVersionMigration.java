/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.from;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.beans.Kind;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.CatalogEntityVersionRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.MigrationException;
import io.harness.mongo.MongoPersistence;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class StableCatalogEntityVersionMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private CatalogEntityVersionRepository catalogEntityVersionRepository;
  @Inject private CatalogEntityRepository catalogEntityRepository;
  @Inject private CatalogService catalogService;
  @Inject private CatalogServiceHelper catalogServiceHelper;

  @Override
  public void migrate() {
    try {
      log.info("Creating a default version of existing blueprints with no versions.");
      catalogEntityRepository
          .findAll(Criteria.where("kind").is(Kind.environmentblueprint.name()), PageRequest.ofSize(1000))
          .forEach(catalogEntity -> {
            try {
              Page<CatalogEntityVersion> versions =
                  catalogEntityVersionRepository.findByEntityId(catalogEntity.getId(), null, 1, null, null);
              if (versions.isEmpty()) {
                CatalogEntityVersion defaultVersion = CatalogEntityVersion.builder()
                                                          .entityId(catalogEntity.getId())
                                                          .version("v1.0.0")
                                                          .stable(true)
                                                          .yaml(catalogEntity.getYaml())
                                                          .description("Default version")
                                                          .build();
                catalogEntityVersionRepository.save(defaultVersion);
                log.info("Created default version for Catalog Entity with id {}", catalogEntity.getId());
              } else {
                Optional<CatalogEntityVersion> version =
                    catalogEntityVersionRepository.getStableVersionForEntity(catalogEntity.getId());
                if (version.isEmpty()) {
                  CatalogEntityVersion latestVersion = versions.getContent().get(0);
                  latestVersion.setStable(true);
                  catalogEntityVersionRepository.save(latestVersion);
                  log.info("Marked version {} as stable for Catalog Entity with id {}", latestVersion.getVersion(),
                      catalogEntity.getId());
                }
              }
            } catch (Exception e) {
              log.error("Error migration Catalog Entity version with id {}. Error = {}", catalogEntity.getId(),
                  e.getMessage(), e);
            }
          });
    } catch (Exception ex) {
      log.error("Error creating default versions for existing blueprints. Error = {}", ex.getMessage(), ex);
    }

    try {
      log.info("Migrating existing environments to a versioned blueprint.");
      catalogEntityRepository.findAll(Criteria.where("kind").is(Kind.environment.name()), PageRequest.ofSize(1000))
          .forEach(environment -> {
            try {
              Map<String, Object> spec = environment.getSpec();
              Map<String, Object> envBlueprint = spec != null ? from(spec, "environmentBlueprint", Map.class) : null;
              if (envBlueprint == null) {
                throw new IllegalStateException(
                    "Environment blueprint is missing in environment with id " + environment.getId());
              }

              String identifier = from(envBlueprint, "identifier", String.class);
              if (identifier == null) {
                throw new IllegalStateException(
                    "Environment blueprint identifier is missing in environment with id " + environment.getId());
              }

              String version = from(envBlueprint, "version", String.class);
              if (!isEmpty(version)) {
                // Version is already specified, no migration needed
                return;
              }

              // blueprints are always account level, so we can just use accountIdentifier as parentUniqueID
              CatalogEntity blueprintEntity = catalogServiceHelper.catalogEntity(
                  environment.getAccountIdentifier(), Kind.environmentblueprint.name(), identifier);
              if (blueprintEntity == null) {
                throw new IllegalStateException("Referenced environment blueprint with identifier " + identifier
                    + " not found for environment with id " + environment.getId());
              }

              Optional<CatalogEntityVersion> stableVersionOpt =
                  catalogEntityVersionRepository.getStableVersionForEntity(blueprintEntity.getId());

              if (stableVersionOpt.isEmpty()) {
                throw new IllegalStateException("No stable version found for environment blueprint with id "
                    + blueprintEntity.getId() + " referenced in environment with id " + environment.getId());
              }

              String entityRef = CatalogUtils.entityRef(Kind.environment.name(), environment.getOrgIdentifier(),
                  environment.getProjectIdentifier(), environment.getIdentifier());

              EntityUpdateRequest updateRequest = new EntityUpdateRequest();
              Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(environment.getYaml());
              envBlueprint.put("version", stableVersionOpt.get().getVersion());
              spec.put("environmentBlueprint", envBlueprint);
              entityYamlMap.put("spec", spec);

              String updatedYaml = YamlUtils.writeObjectAsYaml(entityYamlMap);
              updateRequest.setYaml(updatedYaml);

              // Update the environment's spec to include the stable version
              catalogService.updateEntity(environment.getAccountIdentifier(), environment.getOrgIdentifier(),
                  environment.getProjectIdentifier(), entityRef, updateRequest, false, false, false, false);
            } catch (Exception e) {
              log.error("Error migrating Environment with id {}. Error = {}", environment.getId(), e.getMessage(), e);
            }
          });
    } catch (Exception ex) {
      log.error("Error creating default versions for existing blueprints. Error = {}", ex.getMessage(), ex);
    }
  }
}
