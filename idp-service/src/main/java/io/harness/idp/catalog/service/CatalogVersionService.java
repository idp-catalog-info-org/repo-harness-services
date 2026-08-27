/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.beans.GetEntityVersionsDTO;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogVersionService {
  EntityVersionResponse createEntityVersion(CatalogEntity catalogEntity, String entityYaml, String version,
      String description, Boolean deprecated, Boolean stable, String orgName, String projectName);

  EntityVersionResponse updateEntityVersion(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String version, EntityVersionUpdateRequest body, CatalogEntity existingCatalogEntity);

  EntityVersionResponse getEntityVersion(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, String version);

  GetEntityVersionsDTO getEntityVersions(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, Integer page, Integer limit, String versionSearchTerm,
      Boolean deprecated);

  void deleteEntityVersion(String harnessAccount, String orgIdentifier, String projectIdentifier,
      CatalogEntity existingCatalogEntity, String version);
}
