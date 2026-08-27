/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.mapper;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.entities.LayoutType;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class LayoutMapper {
  public Object entityToObject(LayoutEntity layoutEntity, KindEntity kindEntity) {
    HashMap<String, Object> layoutObject = new HashMap<>();
    layoutObject.put("id", layoutEntity.getId());
    layoutObject.put("name", layoutEntity.getName());
    layoutObject.put("displayName", layoutEntity.getDisplayName());
    layoutObject.put("type", layoutEntity.getType().name());
    layoutObject.put("yaml", layoutEntity.getYaml());
    layoutObject.put("defaultYaml", layoutEntity.getDefaultYaml());
    layoutObject.put("description", layoutEntity.getDescription());
    layoutObject.put("created_at", String.valueOf(layoutEntity.getCreatedAt()));
    layoutObject.put("updated_at", String.valueOf(layoutEntity.getLastUpdatedAt()));
    layoutObject.put("entity_kind", layoutEntity.getEntityKind());
    layoutObject.put("entity_type", layoutEntity.getEntityType());
    layoutObject.put("custom_kind", kindEntity != null && kindEntity.getKindType().equals(KindType.CUSTOM));
    layoutObject.put("kind_icon", kindEntity != null ? kindEntity.getIcon() : "");
    return layoutObject;
  }

  public List<Object> entitiesToObjects(List<LayoutEntity> layoutEntities, List<KindEntity> kindEntities) {
    Map<String, KindEntity> kindEntityMap = kindEntities.stream().collect(
        Collectors.toMap(k -> k.getAccountIdentifier() + ":" + k.getIdentifier(), Function.identity(), (a, b) -> a));
    List<Object> layoutObjects = new ArrayList<>();
    layoutEntities.forEach(layoutEntity -> {
      KindEntity kindEntity =
          kindEntityMap.get(layoutEntity.getAccountIdentifier() + ":" + layoutEntity.getEntityKind());
      if (kindEntity == null) {
        kindEntity = kindEntityMap.get(GLOBAL_ACCOUNT_ID + ":" + layoutEntity.getEntityKind());
      }
      if (kindEntity != null) {
        layoutObjects.add(entityToObject(layoutEntity, kindEntity));
      }
    });
    return layoutObjects;
  }

  public LayoutEntity DtoToEntity(String harnessAccount, LayoutRequest layoutRequest) {
    LayoutEntity layoutEntity = new LayoutEntity();
    layoutEntity.setAccountIdentifier(harnessAccount);
    layoutEntity.setParentUniqueId(harnessAccount);
    layoutEntity.setName(layoutRequest.getName());
    layoutEntity.setDisplayName(layoutRequest.getDisplayName());
    layoutEntity.setYaml(layoutRequest.getYaml());
    layoutEntity.setDefaultYaml(layoutRequest.getDefaultYaml());
    layoutEntity.setDescription(layoutRequest.getDescription());
    layoutEntity.setType(LayoutType.valueOf(layoutRequest.getType()));
    layoutEntity.setEntityKind(layoutRequest.getEntityKind());
    layoutEntity.setEntityType(layoutRequest.getEntityType());
    return layoutEntity;
  }

  public LayoutRequest EntityToDto(LayoutEntity layoutEntity) {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setId(layoutEntity.getId());
    layoutRequest.setName(layoutEntity.getName());
    layoutRequest.setDisplayName(layoutEntity.getDisplayName());
    layoutRequest.setYaml(layoutEntity.getYaml());
    layoutRequest.setDefaultYaml(layoutEntity.getDefaultYaml());
    layoutRequest.setDescription(layoutEntity.getDescription());
    layoutRequest.setType(layoutEntity.getType().name());
    layoutRequest.setEntityKind(layoutEntity.getEntityKind());
    layoutRequest.setEntityType(layoutEntity.getEntityType());
    layoutRequest.setHarnessManaged(layoutEntity.isHarnessManaged());
    return layoutRequest;
  }
}
