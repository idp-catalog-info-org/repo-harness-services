/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.spec.server.idp.v1.model.RequestPlugin;
import io.harness.spec.server.idp.v1.model.RequestPluginV2;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class PluginRequestMapper {
  public PluginRequestEntity fromDTO(String accountIdentifier, RequestPlugin pluginRequest) {
    PluginRequestEntity pluginRequestEntity = new PluginRequestEntity();
    pluginRequestEntity.setAccountIdentifier(accountIdentifier);
    pluginRequestEntity.setName(pluginRequest.getName());
    pluginRequestEntity.setCreator(pluginRequest.getCreator());
    pluginRequestEntity.setPackageLink(pluginRequest.getPackageLink());
    pluginRequestEntity.setDocLink(pluginRequest.getDocLink());
    return pluginRequestEntity;
  }

  public RequestPlugin toDTO(PluginRequestEntity pluginRequestEntity) {
    RequestPlugin pluginRequest = new RequestPlugin();
    pluginRequest.setName(pluginRequestEntity.getName());
    pluginRequest.setCreator(pluginRequestEntity.getCreator());
    pluginRequest.setPackageLink(pluginRequestEntity.getPackageLink());
    pluginRequest.setDocLink(pluginRequestEntity.getDocLink());
    return pluginRequest;
  }

  public RequestPluginV2 toDTOV2(PluginRequestEntity pluginRequestEntity) {
    RequestPluginV2 pluginRequest = new RequestPluginV2();
    pluginRequest.setName(pluginRequestEntity.getName());
    pluginRequest.setCreator(pluginRequestEntity.getCreator());
    pluginRequest.setPackageLink(pluginRequestEntity.getPackageLink());
    pluginRequest.setDocLink(pluginRequestEntity.getDocLink());
    pluginRequest.setIdentifier(pluginRequestEntity.getIdentifier());
    pluginRequest.setStatus(pluginRequestEntity.getStatus());
    return pluginRequest;
  }
}
