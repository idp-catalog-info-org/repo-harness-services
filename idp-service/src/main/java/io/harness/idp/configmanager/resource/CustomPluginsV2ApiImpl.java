/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.idp.configmanager.mappers.CustomPluginV2Mapper;
import io.harness.idp.configmanager.service.CustomPluginsV2Service;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.CustomPluginsV2Api;
import io.harness.spec.server.idp.v1.model.CustomPluginV2CreateRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;
import io.harness.spec.server.idp.v1.model.CustomPluginV2UpdateRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class CustomPluginsV2ApiImpl implements CustomPluginsV2Api {
  private CustomPluginsV2Service customPluginV2Service;
  private IdpCommonService idpCommonService;

  @Override
  public Response createCustomPluginsV2(
      @Valid CustomPluginV2CreateRequest body, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginV2Response response = customPluginV2Service.createCustomPlugin(harnessAccount, body);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (DuplicateKeyException e) {
      String errorMessage = "Custom Plugin already exists with the same identifier";
      log.info("Custom Plugin conflict for accountId: [{}], identifier: [{}]", harnessAccount, body.getIdentifier());
      return Response.status(Response.Status.CONFLICT)
          .entity(ResponseMessage.builder().message(errorMessage).build())
          .build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while creating custom plugin for accountId: [%s], pluginId: [%s]",
              harnessAccount, body.getIdentifier());
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to create custom plugin").build())
          .build();
    }
  }

  @Override
  public Response deleteCustomPluginV2(String pluginId, String harnessAccount) {
    try {
      customPluginV2Service.deleteCustomPlugin(harnessAccount, pluginId);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while deleting custom plugin details for accountId: [%s], pluginId: [%s]",
              harnessAccount, pluginId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to delete custom plugin").build())
          .build();
    }
  }

  @Override
  public Response getCustomPluginV2(String pluginId, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginV2Response response = customPluginV2Service.getCustomPlugin(harnessAccount, pluginId);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while fetching custom plugin details for accountId: [%s], pluginId: [%s]",
              harnessAccount, pluginId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to fetch custom plugin").build())
          .build();
    }
  }

  @Override
  public Response getCustomPluginsV2(
      @AccountIdentifier String harnessAccount, Integer page, Integer limit, String sort, String searchTerm) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    Page<CustomPluginV2Entity> customPluginV2EntityPage =
        customPluginV2Service.getAllCustomPlugins(harnessAccount, pageIndex, pageLimit, sort, searchTerm);
    return idpCommonService.buildPageResponse(pageIndex, pageLimit, customPluginV2EntityPage.getTotalElements(),
        CustomPluginV2Mapper.toResponses(customPluginV2EntityPage.getContent()));
  }

  @Override
  public Response updateCustomPluginV2(
      String pluginId, @Valid CustomPluginV2UpdateRequest body, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginV2Response response = customPluginV2Service.updateCustomPlugin(harnessAccount, pluginId, body);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      String logMessage = String.format(
          "Error occurred while updating custom plugin for accountId: [%s], pluginId: [%s]", harnessAccount, pluginId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to update custom plugin").build())
          .build();
    }
  }
}
