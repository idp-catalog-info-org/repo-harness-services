/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfchangeset.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfchangeset.services.SalesforceChangesetService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectSalesforceChangesetsApi;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetUpdateRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@NextGenManagerAuth
public class ProjectSalesforceChangesetsApiImpl
    extends AbstractSalesforceChangesetsApiImpl implements ProjectSalesforceChangesetsApi {
  @Inject
  public ProjectSalesforceChangesetsApiImpl(SalesforceChangesetService service,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper, ScopeInfoService scopeInfoService) {
    super(service, orgAndProjectValidationHelper, scopeInfoService);
  }

  @Override
  public Response createProjectScopedSalesforceChangeset(
      @Valid SalesforceChangesetCreateRequest request, String org, String project, String harnessAccount) {
    return super.createChangeset(request, org, project, harnessAccount);
  }

  @Override
  public Response deleteProjectScopedSalesforceChangeset(
      String org, String project, String salesforceChangeset, String harnessAccount) {
    return super.deleteChangeset(org, project, salesforceChangeset, harnessAccount);
  }

  @Override
  public Response getProjectScopedSalesforceChangeset(
      String org, String project, String salesforceChangeset, String harnessAccount) {
    return super.getChangeset(org, project, salesforceChangeset, harnessAccount);
  }

  @Override
  public Response getProjectScopedSalesforceChangesets(String org, String project, Integer page,
      @Max(1000L) Integer limit, String searchTerm, String sourceFilter, String metadataTypeFilter,
      String comparisonPairRefFilter, String harnessAccount) {
    return super.getChangesets(org, project, page, limit, searchTerm, sourceFilter, metadataTypeFilter,
        comparisonPairRefFilter, harnessAccount);
  }

  @Override
  public Response updateProjectScopedSalesforceChangeset(@Valid SalesforceChangesetUpdateRequest request, String org,
      String project, String salesforceChangeset, String harnessAccount) {
    return super.updateChangeset(request, org, project, salesforceChangeset, harnessAccount);
  }
}
