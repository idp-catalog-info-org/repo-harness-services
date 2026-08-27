/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.spec.server.ci.v1.OrganizationEnvironmentsApi;
import io.harness.spec.server.ci.v1.model.EnvironmentRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

public class OrganizationEnvironmentsApiImpl
    extends AbstractEnvironmentsApiImpl implements OrganizationEnvironmentsApi {
  @Inject
  OrganizationEnvironmentsApiImpl(
      EnvironmentEntityService environmentEntityService, AccessControlClient accessControlClient, Validator validator) {
    super(environmentEntityService, accessControlClient, validator);
  }

  @Override
  public Response createEnvironment(
      @Valid EnvironmentRequest body, @NotNull String harnessAccount, String orgIdentifier) {
    return super.createEnvironmentEntity(body, harnessAccount, orgIdentifier, null);
  }

  @Override
  public Response deleteEnvironmentByIdentifier(
      String orgIdentifier, String environmentIdentifier, @NotNull String harnessAccount) {
    return super.deleteEnvironmentEntityByIdentifier(orgIdentifier, null, environmentIdentifier, harnessAccount);
  }

  @Override
  public Response getEnvironmentByIdentifier(
      String orgIdentifier, String environmentIdentifier, @NotNull String harnessAccount) {
    return super.getEnvironmentEntityByIdentifier(orgIdentifier, null, environmentIdentifier, harnessAccount);
  }

  @Override
  public Response getEnvironments(String orgIdentifier, @NotNull String harnessAccount, Integer page,
      @Max(1000L) Integer limit, String sort, Boolean isAccessList, String searchTerm, Boolean includeChildrenScope) {
    return super.getEnvironmentEntities(
        orgIdentifier, null, harnessAccount, page, limit, sort, isAccessList, searchTerm, includeChildrenScope);
  }

  @Override
  public Response updateEnvironment(
      @Valid EnvironmentRequest body, @NotNull String harnessAccount, String orgIdentifier) {
    return super.updateEnvironmentEntity(body, harnessAccount, orgIdentifier, null);
  }
}
