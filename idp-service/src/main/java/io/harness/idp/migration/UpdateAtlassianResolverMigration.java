/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class UpdateAtlassianResolverMigration implements NGMigration {
  private static final String ATLASSIAN_CONFIG_ID = "atlassian-auth";
  private static final String NODE_SIGN_IN = "signIn";
  private static final String NODE_RESOLVERS = "resolvers";
  private static final String ATTR_RESOLVER = "resolver";
  private static final String RESOLVER_EMAIL_MATCHING = "emailMatchingUserEntityProfileEmail";
  private static final String RESOLVER_EMAIL_LOCAL_PART = "emailLocalPartMatchingUserEntityName";
  private static final String RESOLVER_USERNAME_MATCHING = "usernameMatchingUserEntityName";

  @Inject private AppConfigRepository appConfigRepository;
  @Inject private ConfigManagerService configManagerService;
  @Override
  public void migrate() {
    log.info(
        "Starting Atlassian resolver migration - updating resolver configuration for all Atlassian auth providers");
    try {
      List<AppConfigEntity> atlassianConfigEntities =
          appConfigRepository.findAllByConfigTypeAndConfigId(ConfigType.AUTH, ATLASSIAN_CONFIG_ID);

      log.info("Found {} Atlassian auth config entities to process", atlassianConfigEntities.size());

      for (AppConfigEntity configEntity : atlassianConfigEntities) {
        try {
          String accountId = configEntity.getAccountIdentifier();
          log.info(
              "Processing Atlassian auth config for account: {}, config ID: {}", accountId, configEntity.getConfigId());

          String configYaml = configEntity.getConfigs();
          if (configYaml == null || configYaml.trim().isEmpty()) {
            log.info("Skipping empty Atlassian auth config for account: {}", accountId);
            continue;
          }

          Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(configYaml);

          Object signInObject = CommonUtils.findObjectByName(yamlMap, NODE_SIGN_IN);
          if (signInObject == null || !(signInObject instanceof Map)) {
            log.info("Skipping Atlassian config for account: {} - cannot find signIn node", accountId);
            continue;
          }

          Map<String, Object> signInMap = (Map<String, Object>) signInObject;

          if (signInMap.containsKey(NODE_RESOLVERS)) {
            log.info("Removing existing resolvers from Atlassian auth config for account: {}", accountId);
            signInMap.remove(NODE_RESOLVERS);
          }

          log.info("Creating standardized resolver configuration for Atlassian auth");

          List<Map<String, String>> resolversList = new ArrayList<>();

          Map<String, String> resolver1 = new HashMap<>();
          resolver1.put(ATTR_RESOLVER, RESOLVER_EMAIL_MATCHING);
          resolversList.add(resolver1);

          Map<String, String> resolver2 = new HashMap<>();
          resolver2.put(ATTR_RESOLVER, RESOLVER_EMAIL_LOCAL_PART);
          resolversList.add(resolver2);

          Map<String, String> resolver3 = new HashMap<>();
          resolver3.put(ATTR_RESOLVER, RESOLVER_USERNAME_MATCHING);
          resolversList.add(resolver3);

          signInMap.put(NODE_RESOLVERS, resolversList);

          String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);
          configEntity.setConfigs(updatedYaml);

          appConfigRepository.save(configEntity);

          log.info(
              "Successfully updated Atlassian auth resolvers for account: {}. Added standard resolver configuration.",
              accountId);
        } catch (Exception e) {
          log.error("Error updating Atlassian resolvers for account ID: {}, config ID: {}. Error: {}",
              configEntity.getAccountIdentifier(), configEntity.getConfigId(), e.getMessage(), e);
        }
      }

      log.info("Atlassian resolver migration completed.");
    } catch (Exception e) {
      log.error("Atlassian resolver migration failed with error", e);
    }
  }
}
