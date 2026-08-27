/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deployable.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.deployable.services.DeployableEntityService;
import io.harness.ng.core.deployable.services.impl.DeployableYamlSchemaHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.AccountDeployablesApi;
import io.harness.spec.server.ng.v1.model.DeployableCreateRequest;
import io.harness.spec.server.ng.v1.model.DeployableUpdateRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@NextGenManagerAuth
public class AccountDeployablesApiImpl extends AbstractDeployablesApiImpl implements AccountDeployablesApi {
  @Inject
  public AccountDeployablesApiImpl(DeployableEntityService deployableEntityService,
      OrgAndProjectValidationHelper orgAndProjectValidationHelper, ScopeInfoService scopeInfoService,
      DeployableYamlSchemaHelper deployableYamlSchemaHelper) {
    super(deployableEntityService, orgAndProjectValidationHelper, scopeInfoService, deployableYamlSchemaHelper);
  }

  @Override
  public Response createAccountScopedDeployable(
      @Valid DeployableCreateRequest deployableRequest, String harnessAccount) {
    return super.createDeployableEntity(deployableRequest, null, null, harnessAccount);
  }

  @Override
  public Response deleteAccountScopedDeployable(String deployable, String harnessAccount) {
    return super.deleteDeployableEntity(null, null, deployable, harnessAccount);
  }

  @Override
  public Response getAccountScopedDeployable(String deployable, String harnessAccount) {
    return super.getDeployableEntity(null, null, deployable, harnessAccount);
  }

  @Override
  public Response getAccountScopedDeployables(
      Integer page, @Max(1000L) Integer limit, String searchTerm, String deployableType, String harnessAccount) {
    return super.getDeployableEntities(null, null, page, limit, searchTerm, deployableType, harnessAccount);
  }

  @Override
  public Response updateAccountScopedDeployable(
      @Valid DeployableUpdateRequest deployableRequest, String deployable, String harnessAccount) {
    return super.updateDeployableEntity(deployableRequest, null, null, deployable, harnessAccount);
  }
}
