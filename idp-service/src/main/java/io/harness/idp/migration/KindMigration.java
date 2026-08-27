/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.catalog.beans.KindType.BUILT_IN;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.JacksonUtils.readValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.migration.beans.NGMigration;
import io.harness.springdata.TransactionHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class KindMigration implements NGMigration {
  static final String KIND_MIGRATIONS_FOLDER_PATH = "migrations/";

  @Inject TransactionHelper transactionHelper;
  @Inject KindEntityRepository kindEntityRepository;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding built in kind to kind collection");

    String kindsJson = loadResourceFileAsString(KIND_MIGRATIONS_FOLDER_PATH + "kinds.json");
    log.info("Loaded kind entities json as string");

    List<KindEntity> kindEntities = readValue(kindsJson, KindEntity.class);
    log.info("Converted entities json string to corresponding list<> pojo's");

    kindEntities.forEach(kindEntity -> {
      kindEntity.setParentUniqueId(GLOBAL_ACCOUNT_ID);
      kindEntity.setKindType(BUILT_IN);
    });

    List<KindEntity> existingKindEntities = kindEntityRepository.findByKindType(BUILT_IN.name());
    kindEntities.forEach(kindEntity
        -> existingKindEntities.stream()
               .filter(existingKindEntity
                   -> existingKindEntity.getAccountIdentifier().equals(kindEntity.getAccountIdentifier())
                       && existingKindEntity.getIdentifier().equals(kindEntity.getIdentifier()))
               .findFirst()
               .ifPresent(existing -> {
                 kindEntity.setId(existing.getId());
                 kindEntity.setCreatedAt(existing.getCreatedAt());
               }));

    transactionHelper.performTransaction(() -> {
      kindEntityRepository.saveAll(kindEntities);
      return null;
    });

    log.info("Migration complete for adding built in kind to kind collection");
  }

  private String loadResourceFileAsString(String resourcePath) {
    try {
      return Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading resource {} as string. Error = {}", resourcePath, e.getMessage(), e);
      throw new UnexpectedException(
          "Error in loading resource " + resourcePath + " as string. Error = " + e.getMessage());
    }
  }
}
