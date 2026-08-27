/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class SettingConfigMapCleanupMigration implements NGMigration {
  private static final String SETTINGS_CONFIG = "settings-config";
  K8sClient k8sClient;
  BackstagePermissionsService backstagePermissionsService;
  NamespaceService namespaceService;

  @Override
  public void migrate() {
    log.info("Starting migration for settings config map cleanup");
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();

    for (NamespaceEntity namespace : activeAccounts) {
      try {
        Optional<BackstagePermissions> permissionOptional =
            backstagePermissionsService.findByAccountIdentifier(namespace.getAccountIdentifier());
        permissionOptional.ifPresent(
            permission -> backstagePermissionsService.updatePermissions(permission, namespace.getAccountIdentifier()));
        k8sClient.deleteConfigMap(namespace.getAccountIdentifier(), namespace.getId(), SETTINGS_CONFIG);
        log.info("Settings config map cleanup migration done for namespace - {}", namespace.getAccountIdentifier());
      } catch (Exception e) {
        log.warn(
            "Settings config map cleanup migration failed for namespace - {}", namespace.getAccountIdentifier(), e);
      }
    }
    log.info("Migration for settings config map cleanup is completed");
  }
}
