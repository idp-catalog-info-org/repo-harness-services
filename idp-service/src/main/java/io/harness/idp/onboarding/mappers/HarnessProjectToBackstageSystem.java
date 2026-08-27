/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.idp.backstage.Constants.ENTITY_UNKNOWN_OWNER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogSystemEntity;
import io.harness.ng.core.dto.ProjectDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class HarnessProjectToBackstageSystem
    implements HarnessEntityToBackstageEntity<ProjectDTO, BackstageCatalogSystemEntity> {
  public final List<String> entityNamesSeenSoFar = new ArrayList<>();

  @Override
  public BackstageCatalogSystemEntity map(ProjectDTO projectDTO) {
    BackstageCatalogSystemEntity backstageCatalogSystemEntity = new BackstageCatalogSystemEntity();

    Map<String, Object> metadataObject = new HashMap<>();
    metadataObject.put(MetadataFieldConstants.IDENTIFIER, projectDTO.getIdentifier());
    metadataObject.put(
        MetadataFieldConstants.ABSOLUTE_IDENTIFIER, projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier());
    metadataObject.put(MetadataFieldConstants.NAME, truncateName(projectDTO.getIdentifier()));
    metadataObject.put(MetadataFieldConstants.TITLE, projectDTO.getName());
    metadataObject.put(MetadataFieldConstants.DESCRIPTION, projectDTO.getDescription());
    metadataObject.put(MetadataFieldConstants.TAGS, getTags(projectDTO.getTags()));
    metadataObject.put(MetadataFieldConstants.ANNOTATIONS, null);
    backstageCatalogSystemEntity.setMetadata(metadataObject);

    BackstageCatalogSystemEntity.Spec spec = new BackstageCatalogSystemEntity.Spec();
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    spec.setDomain(truncateName(projectDTO.getOrgIdentifier()));
    backstageCatalogSystemEntity.setSpec(spec);

    if (entityNamesSeenSoFar.contains(projectDTO.getIdentifier())) {
      String absoluteIdentifier = BackstageCatalogEntity.getValue(
          backstageCatalogSystemEntity.getMetadata(), MetadataFieldConstants.ABSOLUTE_IDENTIFIER, String.class);
      if (absoluteIdentifier != null) {
        backstageCatalogSystemEntity.getMetadata().put(MetadataFieldConstants.NAME, truncateName(absoluteIdentifier));
      }
    }

    entityNamesSeenSoFar.add(projectDTO.getIdentifier());

    return backstageCatalogSystemEntity;
  }
}
