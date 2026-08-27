/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.harnessartifactinstance.resources;

import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.harnessartifactinstance.HarnessArtifactInstanceDetailDTO;
import io.harness.dtos.harnessartifactinstance.HarnessArtifactInstanceDetailResponseDTO;
import io.harness.dtos.harnessartifactinstance.HarnessArtifactInstanceEnvMetadataDTO;
import io.harness.dtos.harnessartifactinstance.HarnessArtifactInstanceEnvMetadataRequestDTO;
import io.harness.dtos.harnessartifactinstance.HarnessArtifactInstanceEnvMetadataResponseDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.harnessartifactinstanceservice.HarnessArtifactInstanceService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.CDP)
@Api("/artifact/instance")
@Path("/artifact/instance")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = io.harness.ng.core.dto.FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = io.harness.ng.core.dto.ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class HarnessArtifactInstanceResource {
  public static final String REGISTRY_IDENTIFIER = "registry";
  public static final String ARTIFACT = "artifact";

  @Inject private HarnessArtifactInstanceService harnessArtifactInstanceService;

  @GET
  @Path("/detail")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance details for an artifact", nickname = "getHarnessArtifactInstanceDetails")
  public ResponseDTO<PageResponse<HarnessArtifactInstanceDetailResponseDTO>> getInstanceDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(REGISTRY_IDENTIFIER) String registry, @NotNull @QueryParam(ARTIFACT) String artifact,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_TYPE_KEY) EnvironmentType envType,
      @Parameter(description = "Specifies the sorting criteria of the list") @QueryParam("sort") List<String> sort,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Page<HarnessArtifactInstanceDetailDTO> instanceDetails =
        harnessArtifactInstanceService.getActiveInstanceDetailForHarnessArtifact(
            accountIdentifier, registry, artifact, page, size, sort, envType, searchTerm);

    return ResponseDTO.newResponse(getNGPageResponse(instanceDetails.map(
        instance -> HarnessArtifactInstanceDetailResponseDTO.builder().instanceDetail(instance).build())));
  }

  @POST
  @Path("/metadata")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance metadata for an artifact", nickname = "getHarnessArtifactInstanceMetadata")
  public ResponseDTO<HarnessArtifactInstanceEnvMetadataResponseDTO> getInstanceMetadata(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      HarnessArtifactInstanceEnvMetadataRequestDTO requestDTO) {
    List<HarnessArtifactInstanceEnvMetadataDTO> instanceMetadatas =
        harnessArtifactInstanceService.getActiveInstanceEnvCountForHarnessArtifact(
            accountIdentifier, requestDTO.getArtifactsMetadata());
    HarnessArtifactInstanceEnvMetadataResponseDTO responseDTO =
        HarnessArtifactInstanceEnvMetadataResponseDTO.builder().instanceMetadata(instanceMetadatas).build();
    return ResponseDTO.newResponse(responseDTO);
  }
}
