/*
 * Copyright 2024 Harness Inc. All rights reserved.
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
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginRequestEntity;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class PluginInfoToPluginRequestMapper {
  public static PluginRequestEntity toPluginRequestFromPluginInfo(PluginInfoEntity pluginInfoEntity) {
    return PluginRequestEntity.builder()
        .identifier(pluginInfoEntity.getIdentifier())
        .name(pluginInfoEntity.getName())
        .creator(pluginInfoEntity.getCreator())
        .packageLink(pluginInfoEntity.getSource())
        .docLink(pluginInfoEntity.getDocumentation())
        .build();
  }
}
