/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfexecution.SalesforceExecutionOrchestrationService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.SalesforceExecutionApi;
import io.harness.spec.server.ng.v1.model.SalesforceExecuteRequest;
import io.harness.spec.server.ng.v1.model.SalesforceExecution;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@OwnedBy(HarnessTeam.CDC)
public class SalesforceExecutionApiImpl implements SalesforceExecutionApi {
  private final SalesforceExecutionOrchestrationService orchestrationService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ScopeInfoService scopeInfoService;

  @Override
  public Response executeProjectScopedSalesforcePipeline(
      @Valid SalesforceExecuteRequest request, String org, String project, String harnessAccount) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
    SalesforceExecution execution = orchestrationService.execute(harnessAccount, org, project, request, scopeInfo);
    return Response.status(Response.Status.CREATED).entity(execution).build();
  }
}
