/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;

import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface CommonIntegrationService<T extends BaseIntegrationRequest, U extends BaseIntegrationResponse> {
  U save(String accountIdentifier, T request, boolean dryRun, boolean writeValidation);

  U update(String accountIdentifier, String identifier, T request, boolean dryRun);

  U saveOrUpdate(String accountIdentifier, T request);

  List<U> get(String accountIdentifier, Pageable pageRequest, String searchTerm);

  U get(String accountIdentifier, String identifier);

  void delete(String accountIdentifier, String identifier, boolean forceDelete);

  void delete(String accountIdentifier);

  DiscoverEntitiesDTO discoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm, String kinds,
      List<String> filters, String includeFields, String includePaths, Integer prevOffset, Integer nextOffset);

  void saveDiscoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest);

  UnlinkIntegrationEntitiesResponse unlinkIntegrationEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, List<String> entityRefs);

  ImportedEntitiesDTO getImportedEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm, String kinds);
}
