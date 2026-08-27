/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.resources;

import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.mappers.ConnectorDetailsMapper;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ConnectorInfoApi;
import io.harness.spec.server.idp.v1.model.ConnectorInfoRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
@Timed
@ResponseMetered
public class ConnectorInfoApiImpl implements ConnectorInfoApi {
  @Inject GitIntegrationService gitIntegrationService;

  @Override
  public Response getConnectorInfo(@AccountIdentifier String harnessAccount) {
    CatalogConnectorEntity catalogConnectorEntity = gitIntegrationService.findDefaultConnectorDetails(harnessAccount);
    if (catalogConnectorEntity == null) {
      log.warn("Could not fetch connector details for accountId: {}", harnessAccount);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    String errorMessage = gitIntegrationService.validateConnectorAndSecret(catalogConnectorEntity, harnessAccount);
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponse(catalogConnectorEntity, errorMessage))
        .build();
  }

  @Override
  public Response getConnectorInfoByProviderType(String providerType, @AccountIdentifier String harnessAccount) {
    Optional<CatalogConnectorEntity> catalogConnector =
        gitIntegrationService.findByAccountIdAndProviderType(harnessAccount, providerType);
    if (catalogConnector.isEmpty()) {
      log.warn("Could not fetch connector details for accountId: {}, providerType: {}", harnessAccount, providerType);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    String errorMessage = gitIntegrationService.validateConnectorAndSecret(catalogConnector.get(), harnessAccount);
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponse(catalogConnector.get(), errorMessage))
        .build();
  }

  @Override
  public Response getConnectorsInfo(@AccountIdentifier String harnessAccount) {
    List<CatalogConnectorEntity> catalogConnectorEntities =
        gitIntegrationService.getAllConnectorDetails(harnessAccount);
    return Response.status(Response.Status.OK)
        .entity(ConnectorDetailsMapper.toResponseList(catalogConnectorEntities))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_EDIT)
  public Response saveConnectorInfo(@Valid ConnectorInfoRequest body, @AccountIdentifier String harnessAccount) {
    try {
      CatalogConnectorEntity catalogConnectorEntity =
          gitIntegrationService.saveConnectorDetails(harnessAccount, body.getConnectorDetails());
      return Response.status(Response.Status.CREATED)
          .entity(ConnectorDetailsMapper.toResponse(catalogConnectorEntity, null))
          .build();
    } catch (Exception e) {
      String errorMessage =
          String.format("Error occurred while saving connectorInfo for accountId: [%s], connectorId: [%s]",
              harnessAccount, body.getConnectorDetails().getIdentifier());
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
