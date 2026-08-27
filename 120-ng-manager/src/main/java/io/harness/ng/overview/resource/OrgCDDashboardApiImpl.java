/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ng.overview.util.CDDashboardUtils;
import io.harness.spec.server.ng.v1.OrgCdDashboardApi;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Size;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class OrgCDDashboardApiImpl extends AbstractCDDashboardApi implements OrgCdDashboardApi {
  @Inject
  public OrgCDDashboardApiImpl(CDOverviewDashboardService cdOverviewDashboardService,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, ScopeInfoService scopeResolverService,
      FilterService filterService) {
    super(cdOverviewDashboardService, ngFeatureFlagHelperService, scopeResolverService, filterService);
  }

  @Override
  public Response getOrgScopedDashboardServices(String org, String harnessAccount, Integer page,
      @Max(1000L) Integer size, String searchTerm, String sort, String order, String filterIdentifier,
      List<String> serviceIdentifiers, List<String> serviceNames, @Size(max = 128) List<String> tags,
      List<String> serviceTypes, String repoName) {
    ServiceFilterPropertiesDTO filterProperties =
        CDDashboardUtils.createFilterProperties(serviceIdentifiers, serviceNames, tags, serviceTypes);
    return super.getApiServices(harnessAccount, org, null, sort != null ? List.of(sort) : List.of(), repoName, page,
        size, searchTerm, filterIdentifier, filterProperties);
  }
}
