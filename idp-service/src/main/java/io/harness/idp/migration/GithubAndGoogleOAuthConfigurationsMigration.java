/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.AUTH_GITHUB_CLIENT_SECRET;
import static io.harness.idp.common.Constants.AUTH_GOOGLE_CLIENT_SECRET;
import static io.harness.idp.common.Constants.GITHUB_AUTH;
import static io.harness.idp.common.Constants.GOOGLE_AUTH;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.entities.PluginConfigEnvVariablesEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.ConfigEnvVariablesRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubAndGoogleOAuthConfigurationsMigration implements NGMigration {
  AppConfigRepository appConfigRepository;
  ConfigEnvVariablesRepository configEnvVariablesRepository;

  @Override
  public void migrate() {
    List<AppConfigEntity> appConfigEntities = appConfigRepository.findAllByConfigType(ConfigType.AUTH);
    List<PluginConfigEnvVariablesEntity> pluginConfigEnvVariablesEntities = new ArrayList<>();
    appConfigEntities.forEach(appConfigEntity -> {
      String envName = appConfigEntity.getConfigId().equals(GITHUB_AUTH)
          ? AUTH_GITHUB_CLIENT_SECRET
          : ((appConfigEntity.getConfigId().equals(GOOGLE_AUTH) ? AUTH_GOOGLE_CLIENT_SECRET : ""));
      if (!isEmpty(envName)) {
        PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity =
            PluginConfigEnvVariablesEntity.builder()
                .accountIdentifier(appConfigEntity.getAccountIdentifier())
                .pluginId(appConfigEntity.getConfigId())
                .pluginName(appConfigEntity.getConfigName())
                .envName(envName)
                .build();
        pluginConfigEnvVariablesEntities.add(pluginConfigEnvVariablesEntity);
      }
    });
    configEnvVariablesRepository.saveAll(pluginConfigEnvVariablesEntities);
  }
}
