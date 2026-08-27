/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class ProxyConfigMigration implements NGMigration {
  @Inject ConfigManagerService configManagerService;

  private static final String PROXY_KEY = "proxy";
  private static final String ENDPOINTS_KEY = "endpoints";

  @Override
  public void migrate() {
    log.info("Migration started for proxy config update......");

    List<AppConfigEntity> appConfigEntities = configManagerService.getAllConfigs();
    List<String> accountsMigrated = new ArrayList<>();

    for (AppConfigEntity appConfigEntity : appConfigEntities) {
      log.info("Proxy config update started  for account - {} and plugin - {}", appConfigEntity.getAccountIdentifier(),
          appConfigEntity.getConfigId());
      Yaml yaml = new Yaml();
      if (appConfigEntity.getConfigs() != null) {
        Map<String, Object> yamlMap = yaml.load(appConfigEntity.getConfigs());
        if (!isEmpty(yamlMap) && yamlMap.containsKey(PROXY_KEY)) {
          Map<String, Object> proxyMap = (Map<String, Object>) yamlMap.remove(PROXY_KEY);
          if (!proxyMap.containsKey(ENDPOINTS_KEY)) {
            Map<String, Object> endpointsMap = new HashMap<>();
            Map<String, Object> finalMap = new HashMap<>();
            endpointsMap.put(ENDPOINTS_KEY, proxyMap);
            finalMap.put(PROXY_KEY, endpointsMap);
            String modifiedYaml = yaml.dump(finalMap);
            appConfigEntity.setConfigs(modifiedYaml);
            accountsMigrated.add(appConfigEntity.getAccountIdentifier());
            log.info("Proxy config updated for account - {} and plugin - {}", appConfigEntity.getAccountIdentifier(),
                appConfigEntity.getConfigId());
            try {
              configManagerService.updateAppConfig(appConfigEntity, appConfigEntity.getConfigType());
            } catch (Exception e) {
              log.error("Error in updating the proxy config migration changes for account - {} for plugin - {}",
                  appConfigEntity.getAccountIdentifier(), appConfigEntity.getConfigId());
            }
          }
        }
      }
    }

    for (String accountId : accountsMigrated) {
      try {
        log.info("Merging and saving app configs for account - {}", accountId);
        /* Passing empty here as restart is needed after migration */
        configManagerService.mergeAndUpdateConfigInNamespace(accountId, "");
      } catch (Exception e) {
        log.error("Error in merging and saving configs in proxy config migration for account - {}", accountId);
      }
    }

    log.info("Migration completed for proxy config update......");
  }
}
