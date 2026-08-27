/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubCatalogDiscoveryPluginBackstageUpgradeMigration implements NGMigration {
  AppConfigRepository appConfigRepository;

  @Override
  public void migrate() {
    List<AppConfigEntity> appConfigEntities =
        appConfigRepository.findAllByConfigIdIn(Collections.singletonList("github-catalog-discovery"));
    Yaml yaml = new Yaml();
    appConfigEntities.forEach(appConfigEntity -> {
      Map<String, Object> appConfig = yaml.load(appConfigEntity.getConfigs());
      Map<String, Object> githubProviders =
          (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) appConfig.get("catalog"))
                                     .get("providers"))
              .get("github");
      for (Map.Entry<String, Object> entry : githubProviders.entrySet()) {
        Map<String, Object> providerIdMap = (Map<String, Object>) entry.getValue();
        providerIdMap.put("schedule",
            Map.of("frequency", Map.of("minutes", 30), "timeout", Map.of("minutes", 3), "initialDelay",
                Map.of("seconds", 120)));
      }
      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      Yaml yamlOut = new Yaml(options);
      String updatedAppConfig = yamlOut.dump(appConfig);
      appConfigEntity.setConfigs(updatedAppConfig);
      appConfigRepository.save(appConfigEntity);
    });
  }
}
