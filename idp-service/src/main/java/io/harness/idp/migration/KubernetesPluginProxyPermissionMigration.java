/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.Constants.KUBERNETES_PLUGIN;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.service.ConfigManagerServiceImpl;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class KubernetesPluginProxyPermissionMigration implements NGMigration {
  AppConfigRepository appConfigRepository;
  ConfigManagerServiceImpl configManagerService;

  @Override
  public void migrate() {
    log.info(
        "Starting the migration for adding kubernetes.proxy permission for accounts with kubernetes plugin enabled");
    List<AppConfigEntity> kubernetesPluginConfigs =
        appConfigRepository.findAllByConfigIdIn(Collections.singletonList(KUBERNETES_PLUGIN));
    kubernetesPluginConfigs.forEach(kubernetesPluginConfig -> {
      if (kubernetesPluginConfig.getEnabled()) {
        configManagerService.handleKubernetesProxyPermissionIfApplicable(
            kubernetesPluginConfig.getAccountIdentifier(), KUBERNETES_PLUGIN, true);
      }
    });
    log.info(
        "Completed the migration for adding kubernetes.proxy permission for accounts with kubernetes plugin enabled");
  }
}
