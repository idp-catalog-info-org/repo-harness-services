/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.idp.integrations.service.catalog.CatalogIntegrationServiceImpl;
import io.harness.idp.integrations.service.common.CommonIntegrationService;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;

import com.google.inject.Inject;
import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IntegrationServiceImpl implements IntegrationService {
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject CatalogIntegrationServiceImpl catalogIntegrationService;

  @Override
  public BaseIntegrationResponse save(String accountIdentifier, String integration, AbstractIntegrationRequest request,
      boolean dryRun, boolean writeValidation) {
    return getServiceForIntegration(integration).save(accountIdentifier, request.getRequest(), dryRun, writeValidation);
  }

  @Override
  public BaseIntegrationResponse update(String accountIdentifier, String integration, String identifier,
      AbstractIntegrationRequest request, boolean dryRun) {
    return getServiceForIntegration(integration).update(accountIdentifier, identifier, request.getRequest(), dryRun);
  }

  @Override
  public List<BaseIntegrationResponse> get(
      String accountIdentifier, String integration, Pageable pageRequest, String searchTerm) {
    return getServiceForIntegration(integration).get(accountIdentifier, pageRequest, searchTerm);
  }

  @Override
  public BaseIntegrationResponse get(String accountIdentifier, String integration, String identifier) {
    return getServiceForIntegration(integration).get(accountIdentifier, identifier);
  }

  @Override
  public void delete(String accountIdentifier, String integration) {
    getServiceForIntegration(integration).delete(accountIdentifier);
  }

  @Override
  public DiscoverEntitiesDTO discoverEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String integration, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds, Integer prevOffset, Integer nextOffset) {
    return getServiceForIntegration(integration)
        .discoverEntities(harnessAccount, orgIdentifier, projectIdentifier, integrationId, pageIndex, pageLimit, sort,
            searchTerm, kinds, prevOffset, nextOffset);
  }

  @Override
  public void saveDiscoverEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String integration, String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest) {
    getServiceForIntegration(integration)
        .saveDiscoverEntities(
            harnessAccount, orgIdentifier, projectIdentifier, integrationId, saveDiscoverEntitiesRequest);
  }

  @Override
  public UnlinkIntegrationEntitiesResponse unlinkIntegrationEntities(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String integration, String integrationId, List<String> entityRefs) {
    return getServiceForIntegration(integration)
        .unlinkIntegrationEntities(harnessAccount, orgIdentifier, projectIdentifier, integrationId, entityRefs);
  }

  @Override
  public ImportedEntitiesDTO getImportedEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String integration, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds) {
    return getServiceForIntegration(integration)
        .getImportedEntities(harnessAccount, orgIdentifier, projectIdentifier, integrationId, pageIndex, pageLimit,
            sort, searchTerm, kinds);
  }

  private <T extends BaseIntegrationRequest, U extends BaseIntegrationResponse> CommonIntegrationService<T, U>
  getServiceForIntegration(String integration) {
    if (integration.equals(BaseIntegrationRequest.TypeEnum.GIT.value())) {
      return (CommonIntegrationService<T, U>) gitIntegrationService;
    }
    if (integration.equals(BaseIntegrationRequest.TypeEnum.CATALOG.value())) {
      return (CommonIntegrationService<T, U>) catalogIntegrationService;
    }
    throw new InvalidRequestException("Integration " + integration + " not supported yet");
  }
}
