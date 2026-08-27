/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.artifactory.ArtifactoryOidcTokenExchangeDetails;
import io.harness.oidc.aws.delegate.AwsOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.azure.delegate.AzureOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.idtoken.OidcIdTokenConstants.ID_TOKEN_CONTEXT;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.instancesyncoidcinfo.InstanceSyncOidcService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit.http.Body;

@OwnedBy(HarnessTeam.DX)
@Api("oidc/delegate/token-exchange-details/")
@Path("oidc/delegate/token-exchange-details/")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NgDelegateOidcResource {
  private final InstanceSyncOidcService instanceSyncOidcService;

  @POST
  @Path("gcp")
  @ApiOperation(value = "Get instance sync perpetual task oidc token exchange details for gcp",
      nickname = "getOidcDelegateTokenExchangeDetailsForGcp")
  @Timed
  @ResponseMetered
  public ResponseDTO<GcpOidcTokenExchangeDetailsForDelegate>
  getOidcDelegateTokenExchangeDetailsForGcp(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
                                            String accountIdentifier, @Body ConnectorConfigDTO connectorConfigDTO,
      @QueryParam(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorIdentifier,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_NAME_KEY) String connectorName) {
    GcpOidcTokenExchangeDetailsForDelegate gcpOidcTokenExchangeDetailsForDelegate =
        instanceSyncOidcService.fetchOidcDetailsGcp(accountIdentifier, connectorConfigDTO, orgIdentifier,
            projectIdentifier, connectorIdentifier, connectorName);

    return ResponseDTO.newResponse(gcpOidcTokenExchangeDetailsForDelegate);
  }

  @POST
  @Path("aws")
  @ApiOperation(value = "Get instance sync perpetual task oidc token exchange details for aws",
      nickname = "getOidcDelegateTokenExchangeDetailsForAws")
  @Timed
  @ResponseMetered
  public ResponseDTO<AwsOidcTokenExchangeDetailsForDelegate>
  getOidcDelegateTokenExchangeDetailsForAws(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body ConnectorConfigDTO connectorConfigDTO,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorIdentifier,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_NAME_KEY) String connectorName,
      @QueryParam("context") ID_TOKEN_CONTEXT context) {
    ID_TOKEN_CONTEXT effectiveContext = context == null ? ID_TOKEN_CONTEXT.PERPETUAL_TASK : context;
    AwsOidcTokenExchangeDetailsForDelegate awsOidcTokenExchangeDetailsForDelegate =
        instanceSyncOidcService.fetchOidcDetailsAws(accountIdentifier, orgIdentifier, projectIdentifier,
            connectorConfigDTO, connectorIdentifier, connectorName, effectiveContext);

    return ResponseDTO.newResponse(awsOidcTokenExchangeDetailsForDelegate);
  }

  @POST
  @Path("azure")
  @ApiOperation(value = "Get instance sync perpetual task oidc token exchange details for azure",
      nickname = "getOidcDelegateTokenExchangeDetailsForAzure")
  @Timed
  @ResponseMetered
  public ResponseDTO<AzureOidcTokenExchangeDetailsForDelegate>
  getOidcDelegateTokenExchangeDetailsForAzure(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body ConnectorConfigDTO connectorConfigDTO,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorIdentifier,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_NAME_KEY) String connectorName) {
    AzureOidcTokenExchangeDetailsForDelegate azureOidcTokenExchangeDetailsForDelegate =
        instanceSyncOidcService.fetchOidcDetailsAzure(accountIdentifier, orgIdentifier, projectIdentifier,
            connectorConfigDTO, connectorIdentifier, connectorName);

    return ResponseDTO.newResponse(azureOidcTokenExchangeDetailsForDelegate);
  }

  @POST
  @Path("artifactory")
  @ApiOperation(value = "Get instance sync perpetual task oidc token exchange details for artifactory",
      nickname = "getOidcDelegateTokenExchangeDetailsForArtifactory")
  @InternalApi
  @Timed
  @ResponseMetered
  public ResponseDTO<ArtifactoryOidcTokenExchangeDetails>
  getOidcDelegateTokenExchangeDetailsForArtifactory(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body ConnectorConfigDTO connectorConfigDTO,
      @QueryParam(value = NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorIdentifier) {
    ArtifactoryOidcTokenExchangeDetails artifactoryOidcTokenExchangeDetails =
        instanceSyncOidcService.fetchOidcDetailsArtifactory(
            accountIdentifier, orgIdentifier, projectIdentifier, connectorConfigDTO, connectorIdentifier);

    return ResponseDTO.newResponse(artifactoryOidcTokenExchangeDetails);
  }
}
