/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CustomPluginV2Mapper {
  public CustomPluginV2Response toDTO(CustomPluginV2Entity entity) {
    CustomPluginV2Response response = new CustomPluginV2Response();
    response.setIdentifier(entity.getIdentifier());
    response.setName(entity.getName());
    response.setDescription(entity.getDescription());
    response.setIcon(entity.getIcon());
    response.setFileUrl(entity.getFileUrl());
    if (entity.getCreatedBy() != null) {
      response.createdBy(entity.getCreatedBy().getEmail());
    }
    response.setCreatedAt(BigDecimal.valueOf(entity.getCreatedAt()));
    response.setUpdatedAt(BigDecimal.valueOf(entity.getLastUpdatedAt()));
    return response;
  }

  public List<CustomPluginV2Response> toResponses(List<CustomPluginV2Entity> entities) {
    List<CustomPluginV2Response> responses = new ArrayList<>();
    entities.forEach(entity -> responses.add(toDTO(entity)));
    return responses;
  }
}
