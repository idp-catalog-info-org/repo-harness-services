/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.entities.LayoutType;
import io.harness.idp.layout.repositories.LayoutEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class LayoutMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject LayoutEntityRepository layoutEntityRepository;

  @Override
  public void migrate() {
    log.info("Starting the migration for layouts");

    List<String> activeIdpAccounts = namespaceService.getAccountIds();
    activeIdpAccounts.forEach(accountIdentifier -> {
      try {
        Object entity = getGeneralResponse(backstageResourceClient.getAllLayouts(accountIdentifier));
        Map<String, Object> entityResponse = (Map<String, Object>) entity;
        List<Map<String, Object>> layouts = (List<Map<String, Object>>) entityResponse.get("response");
        List<LayoutEntity> existingLayoutEntities =
            layoutEntityRepository.findAllByAccountIdentifier(accountIdentifier);
        Map<String, LayoutEntity> existingLayoutMap = existingLayoutEntities.stream().collect(
            Collectors.toMap(e -> e.getAccountIdentifier() + ":" + e.getName(), Function.identity(), (a, b) -> a));
        List<LayoutEntity> layoutEntities = new ArrayList<>();
        layouts.forEach(layout -> {
          LayoutEntity layoutEntity = new LayoutEntity();
          layoutEntity.setAccountIdentifier(accountIdentifier);
          layoutEntity.setParentUniqueId(accountIdentifier);
          layoutEntity.setName((String) layout.get("name"));
          layoutEntity.setDisplayName((String) layout.get("displayName"));
          layoutEntity.setYaml((String) layout.get("yaml"));
          layoutEntity.setDefaultYaml((String) layout.get("defaultYaml"));
          layoutEntity.setDescription((String) layout.get("description"));
          layoutEntity.setType(LayoutType.valueOf((String) layout.get("type")));
          layoutEntity.setEntityKind((String) layout.get("entity_kind"));
          layoutEntity.setEntityType((String) layout.get("entity_type"));
          layoutEntity.setHarnessManaged((Boolean) layout.get("harness_managed"));
          String key = accountIdentifier + ":" + layout.get("name");
          if (existingLayoutMap.containsKey(key)) {
            layoutEntity.setId(existingLayoutMap.get(key).getId());
          }
          layoutEntities.add(layoutEntity);
        });
        layoutEntityRepository.saveAll(layoutEntities);
      } catch (Exception ex) {
        log.warn("Error in migration for layout for account = {} Error = {}", accountIdentifier, ex.getMessage(), ex);
      }
    });

    log.info("Completed the migration for layouts");
  }
}
