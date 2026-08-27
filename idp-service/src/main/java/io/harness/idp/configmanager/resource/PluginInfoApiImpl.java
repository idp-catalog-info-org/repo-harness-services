/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import static io.harness.idp.common.RbacConstants.IDP_PLUGIN;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.mappers.PluginInfoMapper;
import io.harness.idp.configmanager.mappers.PluginRequestMapper;
import io.harness.idp.configmanager.service.PluginInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.PluginInfoApi;
import io.harness.spec.server.idp.v1.model.CustomPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.CustomPluginInfoRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginInfoResponse;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfoResponse;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginRequestResponseList;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class PluginInfoApiImpl implements PluginInfoApi {
  private IdpCommonService idpCommonService;
  private PluginInfoService pluginInfoService;
  @Override
  public Response getPlugins(@AccountIdentifier String harnessAccount) {
    List<PluginInfo> plugins = pluginInfoService.getAllPluginsInfo(harnessAccount, false);
    return Response.status(Response.Status.OK).entity(PluginInfoMapper.toResponseList(plugins)).build();
  }

  @Override
  public Response getPluginsInfoPluginId(String pluginId, @AccountIdentifier String harnessAccount, Boolean meta) {
    try {
      // set default to false
      meta = meta != null && meta;
      PluginDetailedInfo pluginDetailedInfo = pluginInfoService.getPluginDetailedInfo(pluginId, harnessAccount, meta);
      PluginDetailedInfoResponse response = new PluginDetailedInfoResponse();
      response.setPlugin(pluginDetailedInfo);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      log.error("Could not get plugin details", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_DELETE)
  public Response deleteCustomPluginInfo(String pluginId, @AccountIdentifier String harnessAccount, String pluginName) {
    pluginInfoService.deletePluginInfo(pluginId, harnessAccount, pluginName);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response getBackstagePlugins(String harnessAccount) {
    List<PluginInfo> plugins = pluginInfoService.getAllPluginsInfo(harnessAccount, false);
    return Response.status(Response.Status.OK).entity(PluginInfoMapper.toResponseList(plugins)).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response postPluginRequest(@Valid RequestPlugin pluginRequest, @AccountIdentifier String harnessAccount) {
    pluginInfoService.savePluginRequest(harnessAccount, pluginRequest);
    return Response.status(Response.Status.CREATED).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response saveCustomPluginsInfo(@Valid CustomPluginInfoRequest body, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginDetailedInfo info =
          pluginInfoService.generateIdentifierAndSaveCustomPluginInfo(harnessAccount, body.getInfo());
      CustomPluginInfoResponse response = new CustomPluginInfoResponse();
      response.setInfo(info);
      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (Exception e) {
      log.error("Could not save custom plugin", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response updateCustomPluginsInfo(
      String pluginId, @Valid CustomPluginInfoRequest body, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginDetailedInfo info = pluginInfoService.updatePluginInfo(pluginId, body.getInfo(), harnessAccount);
      CustomPluginInfoResponse response = new CustomPluginInfoResponse();
      response.setInfo(info);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (NotFoundException e) {
      log.error("Could not find custom plugin with id {} in account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not update custom plugin info for id {} and account {}", pluginId, harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getPluginRequest(String harnessAccount, Integer page, Integer limit) {
    idpCommonService.checkUserAuthorization();
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    Page<PluginRequestEntity> pluginRequestEntities =
        pluginInfoService.getPluginRequests(harnessAccount, pageIndex, pageLimit);
    PluginRequestResponseList pluginRequestResponseList = new PluginRequestResponseList();
    pluginRequestResponseList.setPluginRequests(
        pluginRequestEntities.getContent().stream().map(PluginRequestMapper::toDTO).collect(Collectors.toList()));
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, pluginRequestEntities.getTotalElements(), pluginRequestResponseList);
  }
}
