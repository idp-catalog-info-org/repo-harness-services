/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

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
import io.harness.idp.configmanager.service.CustomPluginService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.CustomPluginsApi;
import io.harness.spec.server.idp.v1.model.CustomPluginStatus;
import io.harness.spec.server.idp.v1.model.CustomPluginStatusResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class CustomPluginsApiImpl implements CustomPluginsApi {
  private CustomPluginService customPluginService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response customPluginsTrigger(String pluginId, @AccountIdentifier String harnessAccount) {
    customPluginService.triggerBuildPipeline(harnessAccount, pluginId);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getCustomPluginStatusLogs(@AccountIdentifier String harnessAccount, String accountId, String orgId,
      String projectId, String pipelineId, String logKey) {
    try {
      String logs = customPluginService.getCustomPluginStatusLogs(accountId, orgId, projectId, pipelineId, logKey);
      return Response.status(Response.Status.OK).entity(logs).build();
    } catch (Exception e) {
      log.error("Error in getting logs for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getCustomPluginStatusPluginId(String pluginId, @AccountIdentifier String harnessAccount) {
    try {
      CustomPluginStatus customPluginStatus = customPluginService.getCustomPluginStatus(pluginId, harnessAccount);
      CustomPluginStatusResponse customPluginStatusResponse = new CustomPluginStatusResponse();
      customPluginStatusResponse.pluginStatus(customPluginStatus);
      return Response.status(Response.Status.OK).entity(customPluginStatusResponse).build();
    } catch (Exception e) {
      log.error("Error in getting plugin status for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
