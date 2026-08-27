/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import static io.harness.idp.common.RbacConstants.IDP_PLUGIN;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_PLUGIN_TOGGLE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.AppConfigApi;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;
import io.harness.spec.server.idp.v1.model.AppConfigResponse;
import io.harness.spec.server.idp.v1.model.MergedAppConfigResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
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
public class AppConfigApiImpl implements AppConfigApi {
  private ConfigManagerService configManagerService;
  private ConfigEnvVariablesService configEnvVariablesService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response saveOrUpdatePluginAppConfig(@Valid AppConfigRequest body, @AccountIdentifier String harnessAccount) {
    try {
      AppConfig appConfig = body.getAppConfig();
      configManagerService.validateSchemaForPlugin(appConfig.getConfigs(), appConfig.getConfigId());
      configManagerService.validateForIntegrationsAndHost(harnessAccount, appConfig);
      configManagerService.validateForGitlabIntegrations(harnessAccount, appConfig);
      configManagerService.validateProxyEndpointsForPlugin(appConfig, harnessAccount, ConfigType.PLUGIN);
      configEnvVariablesService.validateConfigEnvVariables(appConfig);
      AppConfig savedOrUpdatedAppConfig =
          configManagerService.saveUpdateAndMergeConfigForAccount(appConfig, harnessAccount, ConfigType.PLUGIN, false);
      AppConfigResponse appConfigResponse = new AppConfigResponse();
      appConfigResponse.appConfig(savedOrUpdatedAppConfig);
      return Response.status(Response.Status.OK).entity(appConfigResponse).build();
    } catch (Exception e) {
      log.error("Error in saving or updating configs for Plugin id - {} in account - {}",
          body.getAppConfig().getConfigId(), harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_TOGGLE)
  public Response togglePluginForAccount(
      String pluginId, Boolean isEnabled, @AccountIdentifier String harnessAccount, String pluginName) {
    try {
      AppConfig disabledPluginAppConfig =
          configManagerService.toggleAndSave(harnessAccount, pluginId, isEnabled, ConfigType.PLUGIN, pluginName);
      AppConfigResponse appConfigResponse = new AppConfigResponse();
      appConfigResponse.appConfig(disabledPluginAppConfig);
      return Response.status(Response.Status.OK).entity(appConfigResponse).build();
    } catch (Exception e) {
      log.error("Error in enabling - {} for plugin id - {} in account - {}", isEnabled, pluginId, harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getMergedAppConfigForAccount(@AccountIdentifier String harnessAccount) {
    try {
      String mergedAppConfigForAccount = configManagerService.mergeAllAppConfigsForAccount(harnessAccount);
      MergedAppConfigResponse mergedAppConfigResponse = new MergedAppConfigResponse();
      mergedAppConfigResponse.setMergedAppConfig(mergedAppConfigForAccount);
      return Response.status(Response.Status.OK).entity(mergedAppConfigResponse).build();
    } catch (Exception e) {
      log.error("Error in getting the merged app config for account - {}", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
