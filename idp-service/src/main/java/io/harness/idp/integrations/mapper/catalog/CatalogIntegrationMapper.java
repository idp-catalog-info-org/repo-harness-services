/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.mapper.catalog;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.CatalogIntegrationEntity;
import io.harness.idp.integrations.entities.catalog.HarnessCDIntegrationEntity;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationResponse;
import io.harness.spec.server.idp.v1.model.HarnessCDIntegrationResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class CatalogIntegrationMapper {
  public CatalogIntegrationResponse toResponse(IntegrationEntity integrationEntity) {
    CatalogIntegrationEntity catalogIntegrationEntity = (CatalogIntegrationEntity) integrationEntity;
    if (catalogIntegrationEntity instanceof HarnessCDIntegrationEntity harnessCDIntegrationEntity) {
      HarnessCDIntegrationResponse harnessCDIntegrationResponse = new HarnessCDIntegrationResponse();
      harnessCDIntegrationResponse.setType(BaseIntegrationResponse.TypeEnum.CATALOG);
      harnessCDIntegrationResponse.setCatalogIntegrationType(
          CatalogIntegrationResponse.CatalogIntegrationTypeEnum.HARNESS_CD);
      harnessCDIntegrationResponse.setEnabled(harnessCDIntegrationEntity.isEnabled());
      harnessCDIntegrationResponse.setScopes(harnessCDIntegrationEntity.getScopesToSync());
      harnessCDIntegrationResponse.setAutoDeletion(harnessCDIntegrationEntity.isAutoDeletion());
      return harnessCDIntegrationResponse;
    }
    return null;
  }

  public List<CatalogIntegrationResponse> toResponse(List<IntegrationEntity> entities) {
    List<CatalogIntegrationResponse> responses = new ArrayList<>();
    entities.forEach(entity -> responses.add(toResponse(entity)));
    return responses;
  }
}
