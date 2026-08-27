/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.migration.beans.NGMigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class SnykSecurityPluginConfigUpdateMigration implements NGMigration {
  private static final String SNYK_SECURITY_CONFIG_ID = "snyk-security";
  private static final String PROXY_KEY = "proxy";
  private static final String ENDPOINTS_KEY = "endpoints";
  private static final String SNYK_ENDPOINT = "/snyk";
  private static final String HEADERS_KEY = "headers";
  private static final String AUTHORIZATION_KEY = "Authorization";
  private static final String TOKEN_PREFIX = "token ";

  @Inject private AppConfigRepository appConfigRepository;

  @Override
  public void migrate() {
    log.info("Starting SnykSecurityPluginConfigUpdateMigration to update snyk-security plugin configuration.");

    List<AppConfigEntity> snykSecurityConfigs =
        appConfigRepository.findAllByConfigTypeAndConfigId(ConfigType.PLUGIN, SNYK_SECURITY_CONFIG_ID);
    for (AppConfigEntity configEntity : snykSecurityConfigs) {
      try {
        String config = configEntity.getConfigs();
        if (isEmpty(config)) {
          continue;
        }

        JsonNode rootNode = ConfigManagerUtils.asJsonNode(config);

        if (updateAuthorizationHeader(rootNode)) {
          String updatedConfig = ConfigManagerUtils.asYaml(rootNode.toString());
          configEntity.setConfigs(updatedConfig);
          appConfigRepository.save(configEntity);
          log.info("Updated snyk-security plugin configuration for accountIdentifier {}",
              configEntity.getAccountIdentifier());
        }
      } catch (Exception ex) {
        log.error("Error updating snyk security plugin configuration for accountIdentifier {}, exception {}",
            configEntity.getAccountIdentifier(), ex.getMessage(), ex);
      }
    }

    log.info("Completed SnykSecurityPluginConfigUpdateMigration to update snyk-security plugin configuration.");
  }

  private boolean updateAuthorizationHeader(JsonNode rootNode) {
    JsonNode headersNode = rootNode.path(PROXY_KEY).path(ENDPOINTS_KEY).path(SNYK_ENDPOINT).path(HEADERS_KEY);
    if (!(headersNode instanceof ObjectNode) || !headersNode.has(AUTHORIZATION_KEY)) {
      return false;
    }

    String authValue = headersNode.get(AUTHORIZATION_KEY).asText();
    if (authValue.startsWith(TOKEN_PREFIX)) {
      return false;
    }

    ((ObjectNode) headersNode).set(AUTHORIZATION_KEY, new TextNode(TOKEN_PREFIX + authValue));
    return true;
  }
}
