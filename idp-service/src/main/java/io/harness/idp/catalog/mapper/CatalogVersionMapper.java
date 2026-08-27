/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogVersionMapper {
  public CatalogEntityVersion yamlToEntity(
      String parentUniqueId, String yaml, String version, String description, Boolean deprecated, Boolean stable) {
    CatalogEntityVersion catalogEntityVersion = new CatalogEntityVersion();
    catalogEntityVersion.setEntityId(parentUniqueId);
    catalogEntityVersion.setVersion(version);
    catalogEntityVersion.setYaml(yaml);
    catalogEntityVersion.setDescription(description);
    catalogEntityVersion.setDeprecated(deprecated != null ? deprecated : false);
    catalogEntityVersion.setStable(stable != null ? stable : false);

    return catalogEntityVersion;
  }

  public static EntityVersionResponse entityVersionToResponse(CatalogEntityVersion catalogEntityVersion,
      String orgIdentifier, String orgName, String projectIdentifier, String projectName, String scope, String kind,
      String identifier) {
    EntityVersionResponse response = new EntityVersionResponse();

    response.setIdentifier(identifier);
    response.setVersion(catalogEntityVersion.getVersion());
    response.setKind(EntityVersionResponse.KindEnum.valueOf(kind.toUpperCase()));
    response.setDescription(catalogEntityVersion.getDescription());
    response.setDeprecated(catalogEntityVersion.isDeprecated());
    response.setDeprecatedAt(catalogEntityVersion.getDeprecatedAt());
    response.setStable(catalogEntityVersion.isStable());
    response.setYaml(catalogEntityVersion.getYaml());
    response.setProjectIdentifier(projectIdentifier);
    response.setOrgIdentifier(orgIdentifier);
    response.setScope(EntityVersionResponse.ScopeEnum.valueOf(scope));
    response.setOrgName(orgName);
    response.setProjectName(projectName);
    response.setCreated(catalogEntityVersion.getCreatedAt());
    response.setUpdated(catalogEntityVersion.getLastUpdatedAt());

    return response;
  }
}
