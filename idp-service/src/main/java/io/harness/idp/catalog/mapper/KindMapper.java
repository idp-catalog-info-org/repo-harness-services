/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.beans.KindRequestDTO;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.BuiltInKindEntity;
import io.harness.idp.catalog.entities.CustomKindEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.spec.server.idp.v1.model.KindResponseBody;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class KindMapper {
  public <T extends KindEntity> KindResponseBody entityToDto(T kindEntity) {
    KindResponseBody kindResponseBody = new KindResponseBody();
    kindResponseBody.setIdentifier(kindEntity.getIdentifier());
    kindResponseBody.setName(kindEntity.getName());
    kindResponseBody.setDescription(kindEntity.getDescription());
    kindResponseBody.setIcon(kindEntity.getIcon());
    kindResponseBody.setSchema(kindEntity.getSchema());
    kindResponseBody.setCustom(KindType.CUSTOM.equals(kindEntity.getKindType()));
    kindResponseBody.setGroupingKind(kindEntity.isGroupingKind());
    return kindResponseBody;
  }

  public <T extends KindEntity> List<KindResponseBody> entityToDto(List<T> kindEntities) {
    List<KindResponseBody> kindResponseBodyList = new ArrayList<>();
    kindEntities.forEach(kindEntity -> kindResponseBodyList.add(entityToDto(kindEntity)));
    return kindResponseBodyList;
  }

  public <T extends KindEntity> T dtoToEntity(String accountIdentifier, KindRequestDTO kindRequestDTO, boolean custom) {
    KindEntity kindEntity;
    if (custom) {
      kindEntity = CustomKindEntity.builder().kindType(KindType.CUSTOM).build();
    } else {
      kindEntity = BuiltInKindEntity.builder().kindType(KindType.BUILT_IN).build();
    }
    kindEntity.setAccountIdentifier(accountIdentifier);
    kindEntity.setIdentifier(kindRequestDTO.getIdentifier());
    kindEntity.setName(kindRequestDTO.getName());
    kindEntity.setDescription(kindRequestDTO.getDescription());
    kindEntity.setSchema(kindRequestDTO.getSchema());
    kindEntity.setIcon(kindRequestDTO.getIcon());
    kindEntity.setParentUniqueId(accountIdentifier);
    kindEntity.setDisplayName(kindRequestDTO.getName());
    kindEntity.setGroupingKind(kindRequestDTO.isGroupingKind());
    return (T) kindEntity;
  }

  public <T extends KindEntity> T fromExistingEntity(KindEntity existingKindEntity, KindEntity kindEntity) {
    kindEntity.setId(existingKindEntity.getId());
    kindEntity.setAccountIdentifier(existingKindEntity.getAccountIdentifier());
    kindEntity.setUniqueId(existingKindEntity.getUniqueId());
    kindEntity.setParentUniqueId(existingKindEntity.getParentUniqueId());
    kindEntity.setKindType(existingKindEntity.getKindType());
    kindEntity.setGroupingKind(existingKindEntity.isGroupingKind());
    kindEntity.setIdentifier(existingKindEntity.getIdentifier());
    kindEntity.setCreatedAt(existingKindEntity.getCreatedAt());
    kindEntity.setCreatedBy(existingKindEntity.getCreatedBy());
    return (T) kindEntity;
  }
}
