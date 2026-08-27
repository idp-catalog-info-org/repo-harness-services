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
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.mappers.PluginRequestMapper;
import io.harness.idp.configmanager.service.PluginInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.PluginInfoV2Api;
import io.harness.spec.server.idp.v1.model.PluginRequestResponseListV2;
import io.harness.spec.server.idp.v1.model.PluginRequestResponseV2;
import io.harness.spec.server.idp.v1.model.RequestPluginByIdAndStatus;
import io.harness.spec.server.idp.v1.model.RequestPluginByStatus;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.stream.Collectors;
import javax.validation.Valid;
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
public class PluginInfoV2ApiImpl implements PluginInfoV2Api {
  private PluginInfoService pluginInfoService;
  private IdpCommonService idpCommonService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response createPluginRequestV2(
      @Valid RequestPluginByIdAndStatus requestPluginByIdAndStatus, @AccountIdentifier String harnessAccount) {
    pluginInfoService.savePluginRequestV2(harnessAccount, requestPluginByIdAndStatus);
    return Response.status(Response.Status.CREATED).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_PLUGIN, permission = IDP_PLUGIN_EDIT)
  public Response updatePluginRequestV2(
      String pluginId, @Valid RequestPluginByStatus requestPluginByIdAndStatus, String harnessAccount) {
    PluginRequestEntity pluginRequestEntity =
        pluginInfoService.updatePluginRequest(harnessAccount, pluginId, requestPluginByIdAndStatus.getStatus());
    PluginRequestResponseV2 response = new PluginRequestResponseV2();
    response.setPluginRequest(PluginRequestMapper.toDTOV2(pluginRequestEntity));
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getPluginRequestV2(@AccountIdentifier String harnessAccount, Integer page, Integer limit) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    Page<PluginRequestEntity> pluginRequestEntities =
        pluginInfoService.getPluginRequests(harnessAccount, pageIndex, pageLimit);
    PluginRequestResponseListV2 pluginRequestResponseListV2 = new PluginRequestResponseListV2();
    pluginRequestResponseListV2.setPluginRequests(
        pluginRequestEntities.getContent().stream().map(PluginRequestMapper::toDTOV2).collect(Collectors.toList()));
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, pluginRequestEntities.getTotalElements(), pluginRequestResponseListV2);
  }
}
