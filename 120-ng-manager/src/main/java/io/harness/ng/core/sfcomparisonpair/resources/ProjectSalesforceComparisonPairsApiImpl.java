/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfcomparisonpair.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfcomparisonpair.services.SalesforceComparisonPairService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectSalesforceComparisonPairsApi;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairUpdateRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@NextGenManagerAuth
public class ProjectSalesforceComparisonPairsApiImpl
    extends AbstractSalesforceComparisonPairsApiImpl implements ProjectSalesforceComparisonPairsApi {
  @Inject
  public ProjectSalesforceComparisonPairsApiImpl(SalesforceComparisonPairService service,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper, ScopeInfoService scopeInfoService) {
    super(service, orgAndProjectValidationHelper, scopeInfoService);
  }

  @Override
  public Response createProjectScopedSalesforceComparisonPair(
      @Valid SalesforceComparisonPairCreateRequest request, String org, String project, String harnessAccount) {
    return super.createComparisonPair(request, org, project, harnessAccount);
  }

  @Override
  public Response deleteProjectScopedSalesforceComparisonPair(
      String org, String project, String salesforceComparisonPair, String harnessAccount) {
    return super.deleteComparisonPair(org, project, salesforceComparisonPair, harnessAccount);
  }

  @Override
  public Response getProjectScopedSalesforceComparisonPair(
      String org, String project, String salesforceComparisonPair, String harnessAccount) {
    return super.getComparisonPair(org, project, salesforceComparisonPair, harnessAccount);
  }

  @Override
  public Response getProjectScopedSalesforceComparisonPairs(String org, String project, Integer page,
      @Max(1000L) Integer limit, String searchTerm, String sourceRefFilter, String metadataTypeFilter,
      String harnessAccount) {
    return super.getComparisonPairs(
        org, project, page, limit, searchTerm, sourceRefFilter, metadataTypeFilter, harnessAccount);
  }

  @Override
  public Response updateProjectScopedSalesforceComparisonPair(@Valid SalesforceComparisonPairUpdateRequest request,
      String org, String project, String salesforceComparisonPair, String harnessAccount) {
    return super.updateComparisonPair(request, org, project, salesforceComparisonPair, harnessAccount);
  }
}
