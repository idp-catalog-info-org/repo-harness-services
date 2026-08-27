/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.spec.server.ci.v1.ProjectEnvironmentsApi;
import io.harness.spec.server.ci.v1.model.EnvironmentRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

public class ProjectEnvironmentsApiImpl extends AbstractEnvironmentsApiImpl implements ProjectEnvironmentsApi {
  @Inject
  ProjectEnvironmentsApiImpl(
      EnvironmentEntityService environmentEntityService, AccessControlClient accessControlClient, Validator validator) {
    super(environmentEntityService, accessControlClient, validator);
  }

  @Override
  public Response createEnvironment(
      @Valid EnvironmentRequest body, @NotNull String harnessAccount, String orgIdentifier, String projectIdentifier) {
    return super.createEnvironmentEntity(body, harnessAccount, orgIdentifier, projectIdentifier);
  }

  @Override
  public Response deleteEnvironmentByIdentifier(
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, @NotNull String accountIdentifier) {
    return super.deleteEnvironmentEntityByIdentifier(
        orgIdentifier, projectIdentifier, environmentIdentifier, accountIdentifier);
  }

  @Override
  public Response getEnvironmentByIdentifier(
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, @NotNull String accountIdentifier) {
    return super.getEnvironmentEntityByIdentifier(
        orgIdentifier, projectIdentifier, environmentIdentifier, accountIdentifier);
  }

  @Override
  public Response getEnvironments(String orgIdentifier, String projectIdentifier, @NotNull String harnessAccount,
      Integer page, @Max(1000L) Integer limit, String sort, Boolean isAccessList, String searchTerm,
      Boolean includeChildrenScope) {
    return super.getEnvironmentEntities(orgIdentifier, projectIdentifier, harnessAccount, page, limit, sort,
        isAccessList, searchTerm, includeChildrenScope);
  }

  @Override
  public Response updateEnvironment(@Valid EnvironmentRequest body, @NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    return super.updateEnvironmentEntity(body, accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
