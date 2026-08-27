/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.JacksonUtils.readValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.repositories.LayoutEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
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
public class LayoutSeedMigration implements NGMigration {
  static final String LAYOUT_MIGRATIONS_FOLDER_PATH = "migrations/";

  @Inject NamespaceService namespaceService;
  @Inject LayoutEntityRepository layoutEntityRepository;
  @Inject TransactionHelper transactionHelper;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding built in kind layouts to layout collection");

    String layoutsJson = loadResourceFileAsString(LAYOUT_MIGRATIONS_FOLDER_PATH + "layouts.json");
    log.info("Loaded layout entities json as string");

    List<String> idpActiveAccounts = namespaceService.getAccountIds();

    idpActiveAccounts.forEach(idpActiveAccount -> {
      try {
        List<LayoutEntity> layoutEntitiesForAccount =
            layoutEntityRepository.findAllByAccountIdentifier(idpActiveAccount);
        if (!isEmpty(layoutEntitiesForAccount)) {
          return;
        }
        List<LayoutEntity> layoutEntities = readValue(layoutsJson, LayoutEntity.class);
        layoutEntities.forEach(layoutEntity -> {
          layoutEntity.setParentUniqueId(idpActiveAccount);
          layoutEntity.setAccountIdentifier(idpActiveAccount);
        });
        transactionHelper.performTransaction(() -> {
          layoutEntityRepository.saveAll(layoutEntities);
          return null;
        });
      } catch (Exception ex) {
        log.warn("Error in migration for adding built in kind layouts to layout collection for account = {} Error = {}",
            idpActiveAccount, ex.getMessage(), ex);
      }
    });

    log.info("Migration complete for adding built in kind layouts to layout collection");
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
