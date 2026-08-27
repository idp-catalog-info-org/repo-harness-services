/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;

import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface IntegrationService {
  BaseIntegrationResponse save(String accountIdentifier, String integration, AbstractIntegrationRequest request,
      boolean dryRun, boolean writeValidation);
  BaseIntegrationResponse update(String accountIdentifier, String integration, String identifier,
      AbstractIntegrationRequest request, boolean dryRun);
  List<BaseIntegrationResponse> get(
      String accountIdentifier, String integration, Pageable pageRequest, String searchTerm);
  BaseIntegrationResponse get(String accountIdentifier, String integration, String identifier);
  void delete(String accountIdentifier, String integration);
  DiscoverEntitiesDTO discoverEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String integration, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds, Integer prevOffset, Integer nextOffset);
  void saveDiscoverEntities(String harnessAccount, String orgIdentifier, String projectIdentifier, String integration,
      String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest);
  UnlinkIntegrationEntitiesResponse unlinkIntegrationEntities(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String integration, String integrationId, List<String> entityRefs);
  ImportedEntitiesDTO getImportedEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String integration, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds);
}
