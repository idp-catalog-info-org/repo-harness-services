/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfexecution.SalesforceExecutionOrchestrationService;
import io.harness.ng.core.sfexecution.services.SalesforceExecutionService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectSalesforceExecutionsApi;

import com.google.inject.Inject;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@NextGenManagerAuth
public class ProjectSalesforceExecutionsApiImpl
    extends AbstractSalesforceExecutionsApiImpl implements ProjectSalesforceExecutionsApi {
  @Inject
  public ProjectSalesforceExecutionsApiImpl(SalesforceExecutionService service, ScopeInfoService scopeInfoService,
      SalesforceExecutionOrchestrationService orchestrationService) {
    super(service, scopeInfoService, orchestrationService);
  }

  @Override
  public Response getProjectScopedSalesforceExecution(
      String org, String project, String salesforceExecution, String harnessAccount) {
    return super.getExecution(org, project, salesforceExecution, harnessAccount);
  }

  @Override
  public Response getProjectScopedSalesforceExecutions(String org, String project, Integer page,
      @Max(1000L) Integer limit, String searchTerm, String typeFilter, String changesetId, String harnessAccount) {
    return super.getExecutions(org, project, page, limit, searchTerm, typeFilter, changesetId, harnessAccount);
  }
}
