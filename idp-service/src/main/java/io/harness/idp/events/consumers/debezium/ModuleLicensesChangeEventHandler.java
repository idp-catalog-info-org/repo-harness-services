/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.licensing.Edition;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ModuleLicensesChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject ProvisionService provisionService;
  @Inject NamespaceService namespaceService;
  @Inject GitIntegrationServiceImpl gitIntegrationService;

  @Override
  @SneakyThrows
  public boolean handleCreateEvent(String id, String value) {
    JsonNode node = objectMapper.readTree(value);
    if (node.get("accountIdentifier") != null) {
      String accountIdentifier = node.get("accountIdentifier").asText();
      if (node.get("moduleType") != null && ModuleType.CODE.name().equals(node.get("moduleType").asText())
          && namespaceService.getAccountIdpStatus(accountIdentifier)) {
        log.info("CODE Module License create event received for accountIdentifier = {} Check and Set default connector "
                + "less HCR git integration",
            accountIdentifier);
        gitIntegrationService.setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(accountIdentifier);
        return true;
      }
      if (node.get("moduleType") != null && ModuleType.IDP.name().equals(node.get("moduleType").asText())
          && node.get("edition") != null && Edition.ENTERPRISE.name().equalsIgnoreCase(node.get("edition").asText())) {
        log.info("IDP Module License create event received for accountIdentifier = {}", accountIdentifier);
        provisionService.provision(accountIdentifier);
        return true;
      }
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    return true;
  }
}
