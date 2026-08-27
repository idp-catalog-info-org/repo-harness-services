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
import io.harness.idp.backstage.entities.BackstageCatalogDomainEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.ng.core.dto.OrganizationDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class HarnessOrgToBackstageDomain
    implements HarnessEntityToBackstageEntity<OrganizationDTO, BackstageCatalogDomainEntity> {
  public final List<String> entityNamesSeenSoFar = new ArrayList<>();

  @Override
  public BackstageCatalogDomainEntity map(OrganizationDTO organizationDTO) {
    BackstageCatalogDomainEntity backstageCatalogDomainEntity = new BackstageCatalogDomainEntity();

    Map<String, Object> metadataObject = new HashMap<>();
    metadataObject.put(MetadataFieldConstants.IDENTIFIER, organizationDTO.getIdentifier());
    metadataObject.put(MetadataFieldConstants.ABSOLUTE_IDENTIFIER, organizationDTO.getIdentifier());
    metadataObject.put(MetadataFieldConstants.NAME, truncateName(organizationDTO.getIdentifier()));
    metadataObject.put(MetadataFieldConstants.TITLE, organizationDTO.getName());
    metadataObject.put(MetadataFieldConstants.DESCRIPTION, organizationDTO.getDescription());
    metadataObject.put(MetadataFieldConstants.TAGS, getTags(organizationDTO.getTags()));
    metadataObject.put(MetadataFieldConstants.ANNOTATIONS, null);
    backstageCatalogDomainEntity.setMetadata(metadataObject);

    BackstageCatalogDomainEntity.Spec spec = new BackstageCatalogDomainEntity.Spec();
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    backstageCatalogDomainEntity.setSpec(spec);

    if (entityNamesSeenSoFar.contains(organizationDTO.getIdentifier())) {
      String absoluteIdentifier = BackstageCatalogEntity.getValue(
          backstageCatalogDomainEntity.getMetadata(), MetadataFieldConstants.ABSOLUTE_IDENTIFIER, String.class);
      if (absoluteIdentifier != null) {
        backstageCatalogDomainEntity.getMetadata().put(MetadataFieldConstants.NAME, truncateName(absoluteIdentifier));
      }
    }

    entityNamesSeenSoFar.add(organizationDTO.getIdentifier());

    return backstageCatalogDomainEntity;
  }
}
