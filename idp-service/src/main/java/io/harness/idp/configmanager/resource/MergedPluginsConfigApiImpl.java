/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.service.PluginsProxyInfoService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.MergedPluginsConfigApi;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.ConfigurationEntities;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigResponse;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class MergedPluginsConfigApiImpl implements MergedPluginsConfigApi {
  private final ConfigManagerService configManagerService;
  BackstageEnvVariableService backstageEnvVariableService;

  PluginsProxyInfoService pluginsProxyInfoService;
  private final IdpCommonService idpCommonService;
  private final NamespaceService namespaceService;
  private final String backstageAppBaseUrl;
  private final String backstageBackendBaseUrl;
  private final String backstagePostgresHost;

  @Inject
  public MergedPluginsConfigApiImpl(@Named("backstageAppBaseUrl") String backstageAppBaseUrl,
      @Named("backstagePostgresHost") String backstagePostgresHost,
      @Named("backstageHttpClientConfig") ServiceHttpClientConfig backstageHttpClientConfig,
      NamespaceService namespaceService, IdpCommonService idpCommonService, ConfigManagerService configManagerService,
      BackstageEnvVariableService backstageEnvVariableService, PluginsProxyInfoService pluginsProxyInfoService) {
    this.backstageAppBaseUrl = backstageAppBaseUrl;
    this.backstageBackendBaseUrl = backstageHttpClientConfig.getBaseUrl();
    this.backstagePostgresHost = backstagePostgresHost;
    this.namespaceService = namespaceService;
    this.idpCommonService = idpCommonService;
    this.configManagerService = configManagerService;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.pluginsProxyInfoService = pluginsProxyInfoService;
  }

  @Override
  public Response getMergedPluginsConfig(@AccountIdentifier String accountIdentifier) {
    try {
      MergedPluginConfigs mergedEnabledPluginAppConfigsForAccount =
          configManagerService.mergeEnabledPluginConfigsForAccount(accountIdentifier);
      MergedPluginConfigResponse mergedPluginConfigResponse = new MergedPluginConfigResponse();
      mergedPluginConfigResponse.setMergedConfig(mergedEnabledPluginAppConfigsForAccount);
      return Response.status(Response.Status.OK).entity(mergedPluginConfigResponse).build();
    } catch (Exception e) {
      log.error("Error in merging configs for enabled plugins for account - {}", accountIdentifier, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response updateConfigurationEntities(ConfigurationEntities body, @AccountIdentifier String accountIdentifier) {
    try {
      List<BackstageEnvVariable> resposneBackstageEnvVariablesList =
          backstageEnvVariableService.updateAndAuditEnvironmentVariables(body.getEnvVariables(), accountIdentifier);

      List<ProxyHostDetail> responseProxyHostDetailList =
          pluginsProxyInfoService.updateProxyHostDetailsForHostValues(body.getProxy(), accountIdentifier);

      ConfigurationEntities configurationEntities = new ConfigurationEntities();
      configurationEntities.setEnvVariables(resposneBackstageEnvVariablesList);
      configurationEntities.setProxy(responseProxyHostDetailList);
      return Response.status(Response.Status.OK).entity(configurationEntities).build();
    } catch (Exception e) {
      log.error("Error in updating the configuration entities - {}", accountIdentifier, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
