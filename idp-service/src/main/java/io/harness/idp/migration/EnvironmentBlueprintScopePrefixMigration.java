/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_KIND;
import static io.harness.idp.common.CommonUtils.from;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentBlueprintScopePrefixMigration implements NGMigration {
  private static final int PAGE_SIZE = 1000;
  private static final String ACCOUNT_PREFIX = "account.";

  @Inject private CatalogEntityRepository catalogEntityRepository;

  @Override
  public void migrate() {
    log.info("Starting EnvironmentBlueprintScopePrefixMigration");
    try {
      int updatedCount = 0;
      int totalCount = 0;
      int pageNumber = 0;
      Page<CatalogEntity> page;
      do {
        page = catalogEntityRepository.findAll(
            Criteria.where("kind").is(ENVIRONMENT_KIND), PageRequest.of(pageNumber, PAGE_SIZE));
        for (CatalogEntity environment : page.getContent()) {
          totalCount++;
          try {
            Map<String, Object> spec = environment.getSpec();
            if (spec == null) {
              continue;
            }
            Map<String, Object> envBlueprint = from(spec, "environmentBlueprint", Map.class);
            if (envBlueprint == null) {
              continue;
            }
            String identifier = from(envBlueprint, "identifier", String.class);
            if (isEmpty(identifier)) {
              continue;
            }
            // Skip if already has a scope prefix
            if (identifier.startsWith("account.") || identifier.startsWith("org.")) {
              continue;
            }

            // Prefix with account. since all pre-existing blueprints are at account level
            envBlueprint.put("identifier", ACCOUNT_PREFIX + identifier);
            spec.put("environmentBlueprint", envBlueprint);
            environment.setSpec(spec);
            environment.setYaml(CatalogMapper.presentationYaml(environment));
            catalogEntityRepository.save(environment);
            updatedCount++;
          } catch (Exception ex) {
            log.error("Error migrating environment blueprint scope prefix for entity id={}, identifier={}, "
                    + "accountIdentifier={}. Error={}",
                environment.getId(), environment.getIdentifier(), environment.getAccountIdentifier(), ex.getMessage(),
                ex);
          }
        }
        pageNumber++;
      } while (page.hasNext());
      log.info("EnvironmentBlueprintScopePrefixMigration completed. Updated {} environments out of {} total.",
          updatedCount, totalCount);
    } catch (Exception ex) {
      log.error("Error in EnvironmentBlueprintScopePrefixMigration. Error={}", ex.getMessage(), ex);
    }
  }
}
