/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.ProjectEnvironmentsApi;
import io.harness.spec.server.ng.v1.model.EnvironmentCreateRequest;
import io.harness.spec.server.ng.v1.model.EnvironmentUpdateRequest;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
public class ProjectEnvironmentsApiImpl extends AbstractEnvironmentsApiImpl implements ProjectEnvironmentsApi {
  @Inject
  ProjectEnvironmentsApiImpl(EnvironmentService environmentService, AccessControlClient accessControlClient,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper,
      EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper, EnvironmentRbacHelper environmentRbacHelper,
      EnvironmentFilterHelper environmentFilterHelper, ServiceResourceApiUtils serviceResourceApiUtils,
      ScopeResolutionHelper scopeResolutionHelper, ScopeInfoService scopeInfoService) {
    super(environmentService, accessControlClient, orgAndProjectValidationHelper, environmentEntityYamlSchemaHelper,
        environmentRbacHelper, environmentFilterHelper, serviceResourceApiUtils, scopeResolutionHelper,
        scopeInfoService);
  }

  @Override
  public Response createEnvironment(
      @Valid EnvironmentCreateRequest environmentRequest, String org, String project, String harnessAccount) {
    return super.createEnvironmentEntity(environmentRequest, org, project, harnessAccount);
  }

  @Override
  public Response getEnvironment(String org, String project, String environment, String harnessAccount) {
    return super.getEnvironmentEntity(org, project, environment, harnessAccount);
  }

  @Override
  public Response getEnvironments(String org, String project, Integer page, @Max(1000L) Integer limit,
      String searchTerm, List<String> environmentIds, String sort, Boolean isAccessList, String harnessAccount,
      String order) {
    return super.getEnvironmentEntities(
        org, project, page, limit, searchTerm, environmentIds, sort, isAccessList, harnessAccount, order);
  }

  @Override
  public Response searchEnvironmentsFiltered(String org, String project, Integer page, @Max(1000L) Integer limit,
      String searchTerm, List<String> environmentIds, String sort, String order, List<String> environmentNames,
      String description, String filterIdentifier, Boolean includeAllAccessibleAtScope, String repoName,
      List<String> tags, String environmentType, String harnessAccount) {
    return super.searchEnvironmentEntitiesFiltered(org, project, page, limit, searchTerm, environmentIds, sort, order,
        environmentNames, description, filterIdentifier, includeAllAccessibleAtScope, repoName, tags, environmentType,
        harnessAccount);
  }

  @Override
  public Response updateEnvironment(
      EnvironmentUpdateRequest environmentRequest, String org, String project, String environment, String account) {
    return super.updateEnvironmentEntity(environmentRequest, org, project, environment, account);
  }

  @Override
  public Response deleteEnvironment(
      String org, String project, String environment, String harnessAccount, Boolean forceDelete) {
    return super.deleteEnvironmentEntity(org, project, environment, harnessAccount, forceDelete);
  }
}
