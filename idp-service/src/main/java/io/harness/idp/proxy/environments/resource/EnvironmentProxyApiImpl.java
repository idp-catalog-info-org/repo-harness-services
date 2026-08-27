/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.environments.resource;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.proxy.environments.service.EnvironmentProxyService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.EnvironmentProxyApi;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyCreateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyResponse;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyUpdateRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class EnvironmentProxyApiImpl implements EnvironmentProxyApi {
  @Inject private EnvironmentProxyService environmentProxyService;

  @Override
  public Response createCompileAndExecuteEnvironment(@Valid EnvironmentProxyCreateRequest body,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, Boolean dryRun) {
    try {
      EnvironmentProxyResponse response = environmentProxyService.createCompileAndExecuteEnvironment(
          body, harnessAccount, orgIdentifier, projectIdentifier, dryRun);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      log.error("Error in environment operation for account - {}, dry-run - {}", harnessAccount, dryRun, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response updateCompileAndExecuteEnvironment(String environmentId, @Valid EnvironmentProxyUpdateRequest body,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    try {
      EnvironmentProxyResponse response = environmentProxyService.updateCompileAndExecuteEnvironment(
          environmentId, body, harnessAccount, orgIdentifier, projectIdentifier);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      log.error("Error in updating, compiling and executing environment - {} in account - {}", environmentId,
          harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response deleteEnvironment(String environmentId, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    try {
      environmentProxyService.deleteEnvironment(environmentId, harnessAccount, orgIdentifier, projectIdentifier);
      return Response.status(Response.Status.OK).build();
    } catch (Exception e) {
      log.error("Error in deleting environment - {} in account - {}", environmentId, harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
