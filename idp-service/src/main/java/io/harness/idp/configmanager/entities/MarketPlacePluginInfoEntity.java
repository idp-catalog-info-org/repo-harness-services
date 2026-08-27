/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@StoreIn(DbAliases.IDP)
@FieldNameConstants(innerTypeName = "MarketPlacePluginInfoEntityKeys")
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.configmanager.entities.MarketPlacePluginInfoEntity")
public class MarketPlacePluginInfoEntity extends PluginInfoEntity {
  public static boolean hasChanged(MarketPlacePluginInfoEntity existingEntity, PluginInfoEntity updatedEntity) {
    return PluginInfoEntity.hasFieldChanged(existingEntity.getName(), updatedEntity.getName())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getDescription(), updatedEntity.getDescription())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getCreator(), updatedEntity.getCreator())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getCategory(), updatedEntity.getCategory())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getIconUrl(), updatedEntity.getIconUrl())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getDocumentation(), updatedEntity.getDocumentation())
        || PluginInfoEntity.hasFieldChanged(existingEntity.getSource(), updatedEntity.getSource());
  }
}
