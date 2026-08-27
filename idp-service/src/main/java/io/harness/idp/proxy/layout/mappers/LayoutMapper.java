/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
public class LayoutMapper {
  public LayoutEntity fromDTO(LayoutRequest layoutRequest, String accountIdentifier) {
    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .defaultYaml(layoutRequest.getDefaultYaml())
                                    .yaml(layoutRequest.getYaml())
                                    .identifier(layoutRequest.getId())
                                    .name(layoutRequest.getName())
                                    .type(layoutRequest.getType())
                                    .accountIdentifier(accountIdentifier)
                                    .displayName(layoutRequest.getDisplayName())
                                    .entityKind(layoutRequest.getEntityKind())
                                    .entityType(layoutRequest.getEntityType())
                                    .build();

    if (layoutRequest.isHarnessManaged() != null) {
      layoutEntity.setHarnessManaged(layoutRequest.isHarnessManaged());
    } else {
      layoutEntity.setHarnessManaged(true);
    }
    return layoutEntity;
  }

  public LayoutRequest toDTO(LayoutEntity layoutEntity) {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setType(layoutEntity.getType());
    layoutRequest.setId(layoutEntity.getIdentifier());
    layoutRequest.setName(layoutEntity.getName());
    layoutRequest.setDisplayName(layoutEntity.getDisplayName());
    layoutRequest.setDefaultYaml(layoutEntity.getDefaultYaml());
    layoutRequest.entityKind(layoutRequest.getEntityKind());
    layoutRequest.setEntityType(layoutRequest.getEntityType());
    layoutRequest.setYaml(layoutEntity.getYaml());
    layoutRequest.setHarnessManaged(layoutEntity.isHarnessManaged());
    return layoutRequest;
  }
}
