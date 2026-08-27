/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.resource;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.AUTHSETTING;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.exception.InvalidRequestException;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.dto.IPAllowlistFilterDTO;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.spec.server.ng.v1.IpAllowlistApi;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigRequest;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigResponse;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigValidateResponse;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Timed
@ResponseMetered
public class IpAllowlistApiImpl implements IpAllowlistApi {
  @Inject private final IPAllowlistService ipAllowlistService;
  @Inject private final IPAllowlistResourceUtils ipAllowlistResourceUtil;
  @Inject private final AccessControlClient accessControlClient;

  @Override
  public Response createIpAllowlistConfig(
      @Valid IPAllowlistConfigRequest ipAllowlistConfigRequest, String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    IPAllowlistEntity ipAllowlistEntity =
        ipAllowlistResourceUtil.toIPAllowlistEntity(ipAllowlistConfigRequest.getIpAllowlistConfig(), accountIdentifier);
    IPAllowlistEntity createdIpAllowlistEntity = ipAllowlistService.create(ipAllowlistEntity);

    return Response.status(Response.Status.CREATED)
        .entity(ipAllowlistResourceUtil.toIPAllowlistConfigResponse(createdIpAllowlistEntity))
        .build();
  }

  @Override
  public Response updateIpAllowlistConfig(
      String ipConfigIdentifier, @Valid IPAllowlistConfigRequest ipAllowlistConfigRequest, String harnessAccount) {
    if (!Objects.equals(ipAllowlistConfigRequest.getIpAllowlistConfig().getIdentifier(), ipConfigIdentifier)) {
      throw new InvalidRequestException(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM, USER);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, ipConfigIdentifier), EDIT_AUTHSETTING_PERMISSION);
    IPAllowlistEntity newIpAllowlistEntity =
        ipAllowlistResourceUtil.toIPAllowlistEntity(ipAllowlistConfigRequest.getIpAllowlistConfig(), harnessAccount);
    IPAllowlistEntity updatedIpAllowlistEntity = ipAllowlistService.update(ipConfigIdentifier, newIpAllowlistEntity);

    return Response.status(Response.Status.OK)
        .entity(ipAllowlistResourceUtil.toIPAllowlistConfigResponse(updatedIpAllowlistEntity))
        .build();
  }

  @Override
  public Response validateIpAddressAllowlistedOrNot(
      @NotNull String ipAddress, String harnessAccount, String customIpAddressBlock, Boolean includeDisabledConfigs) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);

    return Response.status(Response.Status.OK)
        .entity(ipAllowlistService.validateIpAddressAllowlistedOrNot(
            ipAddress, harnessAccount, customIpAddressBlock, includeDisabledConfigs))
        .build();
  }

  @Override
  public Response deleteIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, ipConfigIdentifier), DELETE_AUTHSETTING_PERMISSION);

    ipAllowlistService.delete(harnessAccount, ipConfigIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getIpAllowlistConfig(String ipConfigIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, ipConfigIdentifier), VIEW_AUTHSETTING_PERMISSION);
    IPAllowlistEntity ipAllowlistEntity = ipAllowlistService.get(harnessAccount, ipConfigIdentifier);

    return Response.status(Response.Status.OK)
        .entity(ipAllowlistResourceUtil.toIPAllowlistConfigResponse(ipAllowlistEntity))
        .build();
  }

  @Override
  public Response getIpAllowlistConfigs(String searchTerm, Integer page, @Max(1000L) Integer limit,
      String harnessAccount, String sort, String order, String allowedSourceType) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    IPAllowlistFilterDTO ipAllowlistFilterDTO =
        ipAllowlistResourceUtil.getFilterProperties(searchTerm, allowedSourceType);
    Pageable pageable = ipAllowlistResourceUtil.getPageRequest(page, limit, sort, order);
    Page<IPAllowlistEntity> ipAllowlistEntityPage =
        ipAllowlistService.list(harnessAccount, pageable, ipAllowlistFilterDTO);
    Page<IPAllowlistConfigResponse> ipAllowlistConfigResponsePage =
        ipAllowlistEntityPage.map(ipAllowlistResourceUtil::toIPAllowlistConfigResponse);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, ipAllowlistConfigResponsePage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(ipAllowlistConfigResponsePage.getContent()).build();
  }

  public Response allowedIpAddress(@NotNull String ipAddress, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);

    boolean isEnabled = ipAllowlistService.ipAllowlistEnabled(harnessAccount);
    if (!isEnabled) {
      IPAllowlistConfigValidateResponse response = new IPAllowlistConfigValidateResponse();
      response.allowedForCustomBlock(true);
      response.allowedForApi(true);
      response.allowedForUi(true);
      return Response.status(Response.Status.OK).entity(response).build();
    }
    return Response.status(Response.Status.OK)
        .entity(ipAllowlistService.validateIpAddressAllowlistedOrNot(ipAddress, harnessAccount, "", false))
        .build();
  }
  @Override
  public Response validateUniqueIpAllowlistConfigIdentifier(String ipConfigIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, ipConfigIdentifier), EDIT_AUTHSETTING_PERMISSION);
    return Response.ok().entity(ipAllowlistService.validateUniqueness(harnessAccount, ipConfigIdentifier)).build();
  }
}