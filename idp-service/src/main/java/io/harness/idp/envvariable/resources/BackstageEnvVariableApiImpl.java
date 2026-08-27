/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.resources;

import static io.harness.idp.common.RbacConstants.IDP_PLUGIN;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.BackstageEnvVariableApi;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableBatchRequest;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableRequest;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableResponse;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class BackstageEnvVariableApiImpl implements BackstageEnvVariableApi {
  private IdpCommonService idpCommonService;
  private BackstageEnvVariableService backstageEnvVariableService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  @Timed
  @ResponseMetered
  public Response createBackstageEnvVariables(
      @Valid BackstageEnvVariableBatchRequest body, @AccountIdentifier String harnessAccount) {
    List<BackstageEnvVariable> responseSecrets;
    try {
      responseSecrets = backstageEnvVariableService.createOrUpdate(body.getEnvVariables(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not create all environment variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.CREATED)
        .entity(BackstageEnvVariableMapper.toResponseList(responseSecrets))
        .build();
  }

  @Override
  public Response deleteBackstageEnvVariables(List<String> backstageEnvVariables, String accountIdentifier) {
    idpCommonService.checkUserAuthorization();
    try {
      backstageEnvVariableService.deleteMulti(backstageEnvVariables, accountIdentifier);
    } catch (Exception e) {
      log.error("Could not delete all backstage env variables [{}]", backstageEnvVariables, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Deprecated(forRemoval = true)
  @Override
  public Response getBackstageEnvVariable(String backstageEnvVariable, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    Optional<BackstageEnvVariable> backstageEnvVariableOpt =
        backstageEnvVariableService.findByIdAndAccountIdentifier(backstageEnvVariable, harnessAccount);
    if (backstageEnvVariableOpt.isEmpty()) {
      log.warn("Could not fetch backstage env variable for id {}", backstageEnvVariable);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    BackstageEnvVariableResponse secretResponse = new BackstageEnvVariableResponse();
    secretResponse.setEnvVariable(backstageEnvVariableOpt.get());
    return Response.status(Response.Status.OK).entity(secretResponse).build();
  }

  @Deprecated(forRemoval = true)
  @Override
  public Response getBackstageEnvVariables(String harnessAccount, Integer page, Integer limit, String sort) {
    idpCommonService.checkUserAuthorization();
    List<BackstageEnvVariable> secrets = backstageEnvVariableService.findByAccountIdentifier(harnessAccount);
    return Response.status(Response.Status.OK).entity(BackstageEnvVariableMapper.toResponseList(secrets)).build();
  }

  @Deprecated(forRemoval = true)
  @Override
  public Response reloadBackstageEnvVariables(String namespace, String harnessAccount) {
    backstageEnvVariableService.reloadSecrets(harnessAccount, namespace);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response resolveBackstageEnvVariables(String harnessAccount, String namespace) {
    ResolvedEnvVariableResponse resolvedEnvVariableResponse =
        backstageEnvVariableService.resolveEnvVariables(harnessAccount, namespace);
    return Response.status(Response.Status.OK).entity(resolvedEnvVariableResponse).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response syncBackstageEnvVariables(String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      backstageEnvVariableService.findAndSync(harnessAccount);
    } catch (Exception e) {
      log.error("Could not sync all backstage env variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Deprecated(forRemoval = true)
  @Override
  public Response updateBackstageEnvVariable(
      String backstageEnvVariableId, @Valid BackstageEnvVariableRequest request, String harnessAccount) {
    idpCommonService.checkUserAuthorization();
    try {
      BackstageEnvVariable backstageEnvVariable =
          backstageEnvVariableService.update(request.getEnvVariable(), harnessAccount);
      BackstageEnvVariableResponse backstageEnvVariableResponse = new BackstageEnvVariableResponse();
      backstageEnvVariableResponse.setEnvVariable(backstageEnvVariable);
      return Response.status(Response.Status.OK).entity(backstageEnvVariableResponse).build();
    } catch (Exception e) {
      log.error("Could not update backstage env variable for id {}", backstageEnvVariableId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Deprecated(forRemoval = true)
  @Override
  public Response updateBackstageEnvVariables(@Valid BackstageEnvVariableBatchRequest body, String accountIdentifier) {
    idpCommonService.checkUserAuthorization();
    try {
      List<BackstageEnvVariable> responseVariables =
          backstageEnvVariableService.createOrUpdate(body.getEnvVariables(), accountIdentifier);
      return Response.status(Response.Status.OK)
          .entity(BackstageEnvVariableMapper.toResponseList(responseVariables))
          .build();
    } catch (Exception e) {
      log.error("Could not update all environment variables", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
