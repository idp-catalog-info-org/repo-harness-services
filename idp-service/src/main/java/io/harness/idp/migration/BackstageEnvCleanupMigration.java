/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class BackstageEnvCleanupMigration implements NGMigration {
  K8sClient k8sClient;
  NamespaceService namespaceService;
  BackstageEnvVariableService backstageEnvVariableService;

  @Override
  public void migrate() {
    log.info("Starting migration for backstage env cleanup");
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();

    for (NamespaceEntity namespace : activeAccounts) {
      try {
        backstageEnvVariableService.cleanupEnvSecret(namespace.getAccountIdentifier(), namespace.getId());
        log.info("Backstage env cleanup migration done for namespace - {}", namespace.getAccountIdentifier());
      } catch (Exception e) {
        log.warn("Backstage env cleanup migration failed for namespace - {}", namespace.getAccountIdentifier(), e);
      }
    }
    log.info("Migration for backstage env cleanup is completed");
  }
}
