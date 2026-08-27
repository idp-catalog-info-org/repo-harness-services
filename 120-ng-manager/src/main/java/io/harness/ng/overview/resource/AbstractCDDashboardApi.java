/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ng.overview.util.CDDashboardUtils;
import io.harness.utils.ApiUtils;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@RequiredArgsConstructor(onConstructor_ = @Inject)
public abstract class AbstractCDDashboardApi {
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final ScopeInfoService scopeResolverService;
  private final FilterService filterService;

  protected Response getApiServices(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<String> sort, String repoName, int page, int size, String searchTerm, String filterId,
      ServiceFilterPropertiesDTO filterPropertiesDTO) {
    try {
      var serviceListDto = getServices(accountIdentifier, orgIdentifier, projectIdentifier, sort, repoName, page, size,
          searchTerm, filterId, filterPropertiesDTO);
      var services = CDDashboardUtils.mapToServiceDashaboardResponseList(serviceListDto.getContent());
      var response = Response.ok(services);
      ApiUtils.addLinksHeader(
          response, serviceListDto.getTotalItems(), serviceListDto.getPageIndex(), serviceListDto.getPageSize());
      return response.build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e));
    }
  }

  protected PageResponse<ServiceDashboardResponseDTO> getServices(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> sort, String repoName, int page, int size, String searchTerm,
      String filterId, ServiceFilterPropertiesDTO filterPropertiesDTO) throws Exception {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    if (isNotEmpty(filterId)) {
      filterPropertiesDTO = getFilter(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, filterId);
    }

    return cdOverviewDashboardService.getServicesList(accountIdentifier, orgIdentifier, projectIdentifier, sort,
        repoName, size, page, searchTerm, scopeInfo, filterPropertiesDTO);
  }

  protected ServiceFilterPropertiesDTO getFilter(
      String accountId, String orgId, String projectId, ScopeInfo scopeInfo, String filterId) {
    FilterDTO filterDTO;
    if (scopeInfo != null) {
      filterDTO = filterService.get(scopeInfo, filterId, FilterType.SERVICE);
    } else {
      filterDTO = filterService.get(accountId, orgId, projectId, filterId, FilterType.SERVICE);
    }

    if (filterDTO.getFilterProperties() instanceof ServiceFilterPropertiesDTO serviceFilterProperties) {
      return serviceFilterProperties;
    }

    throw new InvalidRequestException(String.format(
        "Unexpected filter properties type %s", filterDTO.getFilterProperties().getClass().getSimpleName()));
  }
}
