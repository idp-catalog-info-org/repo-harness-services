/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.onboarding.dto.KubeconfigUploadResponseDTO;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingExecuteRequestDTO;
import io.harness.ng.core.onboarding.dto.OnboardingExecuteResponseDTO;
import io.harness.ng.core.onboarding.dto.OnboardingInputDTO;
import io.harness.ng.core.onboarding.services.KubeconfigOnboardingService;
import io.harness.ng.core.onboarding.services.OnboardingOrchestrationService;
import io.harness.pms.rbac.CDNGRbacPermissions;
import io.harness.pms.rbac.NGResourceType;
import io.harness.secrets.SecretPermissions;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.glassfish.jersey.media.multipart.FormDataParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/onboarding")
@Path("/onboarding")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Onboarding", description = "This contains APIs related to platform onboarding flows")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CDC)
public class OnboardingResource {
  private final KubeconfigOnboardingService kubeconfigOnboardingService;
  private final OnboardingOrchestrationService onboardingOrchestrationService;
  private final AccessControlClient accessControlClient;

  @POST
  @Path("/kubeconfig/upload")
  @Consumes(MULTIPART_FORM_DATA)
  @ApiOperation(value = "Upload a kubeConfig file for onboarding", nickname = "uploadKubeConfig")
  @Operation(operationId = "uploadKubeConfig", summary = "Upload and process a kubeconfig file for onboarding",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Per-context connector-creation hints detected from the kubeConfig", content = {
              @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
                  schema = @Schema(implementation = KubeconfigUploadResponseDTO.class))
              ,
                  @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
                      schema = @Schema(implementation = KubeconfigUploadResponseDTO.class))
            })
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<KubeconfigUploadResponseDTO>
  uploadKubeConfig(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The kubeConfig (~/.kube/config) file to upload",
          schema = @Schema(type = "string", format = "binary")) @FormDataParam("file")
      InputStream uploadedInputStream) {
    return ResponseDTO.newResponse(kubeconfigOnboardingService.processKubeconfig(
        accountIdentifier, orgIdentifier, projectIdentifier, uploadedInputStream));
  }

  @POST
  @Path("/execute")
  @ApiOperation(value = "Provision onboarding resources from a declarative context", nickname = "executeOnboarding")
  @Operation(operationId = "executeOnboarding",
      summary = "Provisions secrets, connectors, and a Kubernetes service (with GitHub manifest and DockerHub "
          + "artifact) in dependency order from a single declarative context",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Identifiers of the resources created during onboarding", content = {
              @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
                  schema = @Schema(implementation = OnboardingExecuteResponseDTO.class))
              ,
                  @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
                      schema = @Schema(implementation = OnboardingExecuteResponseDTO.class))
            })
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<OnboardingExecuteResponseDTO>
  executeOnboarding(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The onboarding execute request") @NotNull OnboardingExecuteRequestDTO request) {
    OnboardingInputDTO input = request.getInput();
    OnboardingContextDTO context = input == null ? null : input.getContext();

    // The orchestration service invokes the underlying beans directly, bypassing their resource-layer RBAC
    // validators, so enforce edit permission on each resource type the chain creates.
    ResourceScope resourceScope = ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    accessControlClient.checkForAccessOrThrow(resourceScope, Resource.of(SecretPermissions.SECRET_RESOURCE_TYPE, null),
        SecretPermissions.SECRET_EDIT_PERMISSION);
    accessControlClient.checkForAccessOrThrow(resourceScope, Resource.of(NGResourceType.CONNECTOR, null),
        ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION);
    accessControlClient.checkForAccessOrThrow(
        resourceScope, Resource.of(NGResourceType.SERVICE, null), CDNGRbacPermissions.SERVICE_CREATE_PERMISSION);

    return ResponseDTO.newResponse(
        onboardingOrchestrationService.execute(accountIdentifier, orgIdentifier, projectIdentifier, context));
  }
}
