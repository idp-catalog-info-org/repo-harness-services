/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.mappers;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.MarketPlacePluginInfoEntity;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class MarketPlacePluginInfoEntityMapper {
  public List<MarketPlacePluginInfoEntity> toEntityList(List<PluginInfo> marketplacePlugins) {
    List<MarketPlacePluginInfoEntity> marketplacePluginEntities = new ArrayList<>();
    for (PluginInfo pluginInfo : marketplacePlugins) {
      MarketPlacePluginInfoEntity marketPlacePluginInfoEntity = MarketPlacePluginInfoEntity.builder().build();
      marketPlacePluginInfoEntity.setIdentifier(pluginInfo.getId());
      marketPlacePluginInfoEntity.setName(pluginInfo.getName());
      marketPlacePluginInfoEntity.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
      marketPlacePluginInfoEntity.setDescription(pluginInfo.getDescription());
      marketPlacePluginInfoEntity.setDocumentation(pluginInfo.getDocumentation());
      marketPlacePluginInfoEntity.setIconUrl(pluginInfo.getIconUrl());
      marketPlacePluginInfoEntity.setCreator(pluginInfo.getCreatedBy());
      marketPlacePluginInfoEntity.setCategory(pluginInfo.getCategory());
      marketPlacePluginInfoEntity.setType(PluginInfo.PluginTypeEnum.MARKETPLACE);
      marketPlacePluginInfoEntity.setSource(pluginInfo.getSource());

      marketplacePluginEntities.add(marketPlacePluginInfoEntity);
    }
    return marketplacePluginEntities;
  }
}
