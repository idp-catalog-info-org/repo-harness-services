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
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.migration.beans.NGMigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DxPluginConfigUpdateMigration implements NGMigration {
  private static final String DX_CONFIG_ID = "dx";
  private static final String PROXY_KEY = "proxy";
  private static final String ENDPOINTS_KEY = "endpoints";
  private static final String DX_ENDPOINT = "/dx";
  private static final String DX_WEB_API_ENDPOINT = "/dx-web-api";
  private static final String TARGET_KEY = "target";
  private static final String NEW_TARGET_URL = "https://api.getdx.com";
  private static final String PATH_REWRITE_KEY = "pathRewrite";
  private static final String HEADERS_KEY = "headers";
  private static final String ALLOWED_HEADERS_KEY = "allowedHeaders";
  private static final String DX_KEY = "dx";
  private static final String APP_ID_KEY = "appId";

  @Inject private AppConfigRepository appConfigRepository;
  @Inject private ConfigManagerService configManagerService;

  @Override
  public void migrate() {
    log.info("Starting DxPluginConfigUpdateMigration to update dx plugin configurations.");

    try {
      List<AppConfigEntity> dxPluginConfigs =
          appConfigRepository.findAllByConfigTypeAndConfigId(ConfigType.PLUGIN, DX_CONFIG_ID);

      log.info("Found {} dx plugin configurations to update.", dxPluginConfigs.size());

      int updatedCount = 0;

      for (AppConfigEntity configEntity : dxPluginConfigs) {
        try {
          String configYaml = configEntity.getConfigs();
          if (configYaml == null || configYaml.trim().isEmpty()) {
            continue;
          }

          JsonNode rootNode = ConfigManagerUtils.asJsonNode(configYaml);
          boolean updated = false;

          if (rootNode.has(PROXY_KEY)) {
            JsonNode proxyNode = rootNode.get(PROXY_KEY);
            if (proxyNode.has(ENDPOINTS_KEY)) {
              JsonNode endpointsNode = proxyNode.get(ENDPOINTS_KEY);
              if (endpointsNode.has(DX_ENDPOINT) && endpointsNode instanceof ObjectNode) {
                ObjectNode endpointsObjectNode = (ObjectNode) endpointsNode;
                JsonNode dxEndpointNode = endpointsObjectNode.get(DX_ENDPOINT);

                if (dxEndpointNode instanceof ObjectNode) {
                  ObjectNode dxEndpointObjectNode = (ObjectNode) dxEndpointNode;
                  dxEndpointObjectNode.put(TARGET_KEY, NEW_TARGET_URL);

                  if (dxEndpointNode.has(PATH_REWRITE_KEY)
                      && dxEndpointNode.get(PATH_REWRITE_KEY) instanceof ObjectNode) {
                    ObjectNode pathRewriteNode = (ObjectNode) dxEndpointNode.get(PATH_REWRITE_KEY);
                    pathRewriteNode.fieldNames().forEachRemaining(field -> {
                      if (field.startsWith("/api/proxy/dx/?")) {
                        pathRewriteNode.remove(field);
                      }
                    });
                    pathRewriteNode.put("/api/proxy/dx-web-api/?", "/");
                  } else {
                    // If no pathRewrite exists, create it
                    ObjectNode pathRewriteNode = dxEndpointObjectNode.putObject(PATH_REWRITE_KEY);
                    pathRewriteNode.put("/api/proxy/dx-web-api/?", "/");
                  }

                  if (!dxEndpointNode.has(ALLOWED_HEADERS_KEY)) {
                    ArrayNode arrayNode = dxEndpointObjectNode.putArray(ALLOWED_HEADERS_KEY);
                    arrayNode.add("X-Client-Type");
                    arrayNode.add("X-Client-Version");
                    log.info("Added allowedHeaders for account: {}", configEntity.getAccountIdentifier());
                  }

                  endpointsObjectNode.set(DX_WEB_API_ENDPOINT, dxEndpointNode);
                  endpointsObjectNode.remove(DX_ENDPOINT);

                  updated = true;
                  log.info(
                      "Renamed '/dx' endpoint to '/dx-web-api' for account: {}", configEntity.getAccountIdentifier());
                }
              }
            }
          }

          if (rootNode.has(DX_KEY) && rootNode instanceof ObjectNode) {
            ObjectNode rootObjectNode = (ObjectNode) rootNode;
            JsonNode dxSection = rootNode.get(DX_KEY);

            if (dxSection.has(APP_ID_KEY)) {
              ObjectNode newDxSection = rootObjectNode.objectNode();
              newDxSection.set(APP_ID_KEY, dxSection.get(APP_ID_KEY));

              rootObjectNode.set(DX_KEY, newDxSection);
              updated = true;
              log.info("Cleaned up 'dx' section, keeping only the appId for account: {}",
                  configEntity.getAccountIdentifier());
            }
          }

          if (updated) {
            String updatedYaml = ConfigManagerUtils.asYaml(rootNode.toString());
            configEntity.setConfigs(updatedYaml);

            appConfigRepository.save(configEntity);
            updatedCount++;
            log.info(
                "Successfully updated dx plugin configuration for accountId: {}", configEntity.getAccountIdentifier());
          }
        } catch (Exception e) {
          log.error("Error updating dx plugin configuration for accountId: {}, error: {}",
              configEntity.getAccountIdentifier(), e.getMessage(), e);
        }
      }

      log.info("Successfully updated {} out of {} dx plugin configurations.", updatedCount, dxPluginConfigs.size());
      log.info("Completed DxPluginConfigUpdateMigration.");
    } catch (Exception e) {
      log.error("Error during DxPluginConfigUpdateMigration: {}", e.getMessage(), e);
    }
  }
}
