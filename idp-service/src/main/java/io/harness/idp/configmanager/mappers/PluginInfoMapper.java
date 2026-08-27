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
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginInfoResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class PluginInfoMapper {
  public PluginInfo toDTO(PluginInfoEntity pluginInfoEntity, boolean isEnabled) {
    PluginInfo pluginInfo = new PluginInfo();
    pluginInfo.setId(pluginInfoEntity.getIdentifier());
    pluginInfo.setName(pluginInfoEntity.getName());
    pluginInfo.setCreatedBy(pluginInfoEntity.getCreator());
    pluginInfo.setIconUrl(pluginInfoEntity.getIconUrl());
    pluginInfo.setImageUrl(pluginInfoEntity.getImageUrl());
    pluginInfo.setImages(pluginInfoEntity.getImages());
    pluginInfo.setDocumentation(pluginInfoEntity.getDocumentation());
    pluginInfo.setDescription(pluginInfoEntity.getDescription());
    pluginInfo.setCategory(pluginInfoEntity.getCategory());
    pluginInfo.setSource(pluginInfoEntity.getSource());
    if (PluginInfo.PluginTypeEnum.DEFAULT.equals(pluginInfoEntity.getType())) {
      pluginInfo.setCore(((DefaultPluginInfoEntity) pluginInfoEntity).isCore());
    }
    pluginInfo.setPluginType(pluginInfoEntity.getType());
    pluginInfo.setEnabled(isEnabled);
    pluginInfo.setPluginType(
        pluginInfoEntity.getType() == null ? PluginInfo.PluginTypeEnum.DEFAULT : pluginInfoEntity.getType());
    return pluginInfo;
  }

  public static List<PluginInfoResponse> toResponseList(List<PluginInfo> plugins) {
    List<PluginInfoResponse> response = new ArrayList<>();
    plugins.forEach(plugin -> response.add(new PluginInfoResponse().plugin(plugin)));
    return response;
  }
}
