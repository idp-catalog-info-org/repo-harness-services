/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.oidc.gcp.accesstoken.GcpOidcAccessTokenUtility.getOidcServiceAccountAccessTokenV2;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenResponse;
import io.harness.oidc.aws.credential.AwsOidcCredentialUtility;
import io.harness.oidc.aws.dto.AwsOidcCredentialRequestDto;
import io.harness.oidc.aws.dto.AwsOidcCredentialResponseDto;
import io.harness.oidc.aws.utility.AwsOidcTokenUtility;
import io.harness.oidc.azure.credential.AzureOidcCredentialUtility;
import io.harness.oidc.azure.dto.AzureOidcCredentialExchangeRequestDTO;
import io.harness.oidc.azure.dto.AzureOidcCredentialRequestDTO;
import io.harness.oidc.azure.dto.AzureOidcCredentialResponseDTO;
import io.harness.oidc.azure.utility.AzureOidcTokenUtility;
import io.harness.oidc.gcp.constants.GcpOidcServiceAccountAccessTokenResponse;
import io.harness.oidc.gcp.dto.GcpOidcAccessTokenRequestDTO;
import io.harness.oidc.gcp.utility.GcpOidcTokenUtility;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.cloud.iam.credentials.v1.GenerateAccessTokenResponse;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Path("/oidc/access-token")
@Api("/oidc/access-token")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "Oidc-Access-Token",
    description = "This contains APIs related to OIDC Access Token generation as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NgOidcAccessTokenResource {
  GcpOidcTokenUtility gcpOidcTokenUtility;
  AwsOidcCredentialUtility awsOidcCredentialUtility;
  AwsOidcTokenUtility awsOidcTokenUtility;
  AzureOidcTokenUtility azureOidcTokenUtility;
  AzureOidcCredentialUtility azureOidcCredentialUtility;
  @POST
  @Path("gcp/workload-access")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(
      value = "Generate an OIDC Workload Access Token for GCP", nickname = "generateOidcWorkloadAccessTokenForGcp")
  @Operation(operationId = "OidcWorkloadAccessTokenResponse",
      summary = "Generates an OIDC Workload Access Token for GCP",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC Workload Access Token response")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<OidcWorkloadAccessTokenResponse>
  getOidcWorkloadAccessTokenForGcp(@RequestBody(required = true, description = "Details of GCP Workload Identity")
      @Valid GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO1 = new GcpOidcAccessTokenRequestDTO();

    // Check if the ID Token is empty.
    if (StringUtils.isEmpty(gcpOidcAccessTokenRequestDTO.getOidcIdToken())) {
      gcpOidcAccessTokenRequestDTO1.setOidcIdToken(
          gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO()));
      gcpOidcAccessTokenRequestDTO1.setGcpOidcTokenRequestDTO(gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO());
    }

    // Get the Workload Access Token
    return ResponseDTO.newResponse(gcpOidcTokenUtility.exchangeOidcWorkloadAccessToken(gcpOidcAccessTokenRequestDTO1));
  }

  @POST
  @Path("gcp/service-account-access")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(
      value = "Generate an OIDC Service Account Access Token for GCP", nickname = "generateOidcSAAccessTokenForGcp")
  @Operation(operationId = "getOidcServiceAccountAccessTokenForGcp",
      summary = "Generates an OIDC Service Account Access Token for GCP",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns OIDC Service Account Access Token response")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<GcpOidcServiceAccountAccessTokenResponse>
  getOidcServiceAccountAccessTokenForGcp(@RequestBody(required = true, description = "Details of GCP Workload Identity")
      @Valid GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    // Get the Workload Access Token to be used as bearer
    // for getting the Service Account Access Token.
    OidcWorkloadAccessTokenResponse oidcWorkloadAccessTokenResponse =
        gcpOidcTokenUtility.exchangeOidcWorkloadAccessToken(gcpOidcAccessTokenRequestDTO);
    try {
      GenerateAccessTokenResponse generateAccessTokenResponse =
          getOidcServiceAccountAccessTokenV2(oidcWorkloadAccessTokenResponse.getAccess_token(),
              gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO().getServiceAccountEmail());
      if (generateAccessTokenResponse != null) {
        return ResponseDTO.newResponse(new GcpOidcServiceAccountAccessTokenResponse(
            generateAccessTokenResponse.getAccessToken(), generateAccessTokenResponse.getExpireTime().getSeconds()));
      }
    } catch (IOException ex) {
      log.error(String.format("Unable to exchange for OIDC Access Token for GCP Service Account - %s " + ex));
    }
    return ResponseDTO.newResponse(null);
  }

  @POST
  @Path("aws/webidentity-session-access")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(
      value = "Generate an OIDC IAM Role Credential for AWS", nickname = "generateOidcIAMRoleCredentialForAws")
  @Operation(operationId = "generateOidcIAMRoleCredentialForAws",
      summary = "Generate an OIDC IAM Role Credential for AWS",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Generate an OIDC IAM Role Credential for AWS")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<AwsOidcCredentialResponseDto>
  getOidcIamRoleCredentialForAws(@RequestBody(required = true, description = "Details of AWS WebIdentity credential")
      @Valid AwsOidcCredentialRequestDto awsOidcCredentialRequestDto) {
    // Check if the ID Token is empty.
    String oidcToken = awsOidcCredentialRequestDto.getOidcIdToken();
    if (StringUtils.isEmpty(oidcToken)) {
      oidcToken = awsOidcTokenUtility.generateAwsOidcIdToken(awsOidcCredentialRequestDto.getAwsOidcTokenRequestDto());
    }
    return ResponseDTO.newResponse(
        awsOidcCredentialUtility.getOidcIamRoleCredential(oidcToken, awsOidcCredentialRequestDto));
  }

  @POST
  @Path("azure")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Exchange OIDC token for Azure credentials", nickname = "exchangeOidcTokenForAzureCredentials")
  @Operation(operationId = "exchangeOidcTokenForAzureCredentials",
      summary = "Exchanges an OIDC token for Azure credentials",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Azure credentials")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<AzureOidcCredentialResponseDTO>
  getOidcTokenForAzureCredentials(@RequestBody(required = true,
      description = "Azure token exchange request") @Valid AzureOidcCredentialExchangeRequestDTO requestDTO) {
    // Extract ID token from request or generate a new one if not provided
    String idToken = requestDTO.getOidcToken();
    if (StringUtils.isEmpty(idToken)) {
      idToken = azureOidcTokenUtility.generateAzureOidcIdToken(requestDTO.getAzureOidcTokenRequestDTO());
    }

    // Exchange ID token for Azure access token
    AzureOidcCredentialRequestDTO credentialRequestDTO = AzureOidcCredentialRequestDTO.builder()
                                                             .clientId(requestDTO.getClientId())
                                                             .tenantId(requestDTO.getTenantId())
                                                             .resource(requestDTO.getResource())
                                                             .build();

    AzureOidcCredentialResponseDTO response =
        azureOidcCredentialUtility.getOidcFederatedCredential(idToken, credentialRequestDTO);

    return ResponseDTO.newResponse(response);
  }
}
