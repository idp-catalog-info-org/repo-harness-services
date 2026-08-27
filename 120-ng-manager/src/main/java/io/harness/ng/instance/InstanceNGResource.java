/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.instance;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.cdng.execution.service.StageExecutionInstanceInfoService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.instance.InstanceService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Api("instanceng")
@Path("instanceng")
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
@ScopeInfoResolutionApi
public class InstanceNGResource {
  public static final String INSTANCE_INFO_POD_NAME = "instanceInfoPodName";
  public static final String INSTANCE_INFO_NAMESPACE = "instanceInfoNamespace";
  private final InstanceService instanceService;
  private final StageExecutionInstanceInfoService stageExecutionInstanceInfoService;
  private final ScopeInfoService scopeInfoService;

  @GET
  @Path("/")
  @Timed
  @ResponseMetered
  @InternalApi
  @ApiOperation(value = "Get instance NG data", nickname = "getInstanceNGData")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<Optional<HarnessServiceInfoNG>> getInstanceNGData(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(INSTANCE_INFO_POD_NAME) String instanceInfoPodName,
      @NotNull @QueryParam(INSTANCE_INFO_NAMESPACE) String instanceInfoNamespace) {
    log.info("Received instance NG request");
    List<InstanceDTO> instanceList =
        instanceService.getActiveInstancesByInstanceInfo(accountIdentifier, instanceInfoNamespace, instanceInfoPodName);
    log.info("instanceList: {}", instanceList);
    if (!instanceList.isEmpty()) {
      InstanceDTO instanceDTO = instanceList.get(0);
      String orgIdentifier = getOrgIdentifier(instanceDTO);
      String projectIdentifier = getProjectIdentifier(instanceDTO);
      return ResponseDTO.newResponse(Optional.of(new HarnessServiceInfoNG(instanceDTO.getServiceIdentifier(),
          orgIdentifier, projectIdentifier, instanceDTO.getEnvIdentifier(), instanceDTO.getInfrastructureMappingId())));
    }
    return ResponseDTO.newResponse(Optional.empty());
  }

  @GET
  @Path("/stage-instance-info")
  @Timed
  @ResponseMetered
  @InternalApi
  @ApiOperation(value = "Get instance info deployed in previous steps of stage", nickname = "getStageInstanceInfo")
  public ResponseDTO<List<StepExecutionInstanceInfo>> getInstanceInfo(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_EXECUTION_ID) String pipelineExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.STAGE_KEY) String stageExecutionId, @Context ScopeInfo scopeInfo) {
    if (scopeInfo != null) {
      return ResponseDTO.newResponse(
          stageExecutionInstanceInfoService.get(scopeInfo, pipelineExecutionId, stageExecutionId));
    }
    return ResponseDTO.newResponse(stageExecutionInstanceInfoService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineExecutionId, stageExecutionId));
  }
  @GET
  @Path("/stage-instance-info/v2")
  @Timed
  @ResponseMetered
  @InternalApi
  @ApiOperation(value = "Get instance info deployed in previous steps of stage", nickname = "getStageInstanceInfoV2")
  public ResponseDTO<List<StepExecutionInstanceInfo>> getInstanceInfoV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_EXECUTION_ID) String pipelineExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.STAGE_KEY) String stageExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.STEP_FQN) String stepFqn, @Context ScopeInfo scopeInfo) {
    if (scopeInfo != null) {
      return ResponseDTO.newResponse(
          stageExecutionInstanceInfoService.get(scopeInfo, pipelineExecutionId, stageExecutionId, stepFqn));
    }
    return ResponseDTO.newResponse(stageExecutionInstanceInfoService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineExecutionId, stageExecutionId, stepFqn));
  }

  /**
   * Gets orgIdentifier from InstanceDTO using parentUniqueId-based scope resolution when available.
   */
  private String getOrgIdentifier(InstanceDTO instanceDTO) {
    if (EmptyPredicate.isNotEmpty(instanceDTO.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfoOpt = scopeInfoService.getScopeInfoFromUniqueId(
          instanceDTO.getAccountIdentifier(), instanceDTO.getParentUniqueId());
      if (scopeInfoOpt.isPresent()) {
        return scopeInfoOpt.get().getOrgIdentifier();
      }
    }
    return instanceDTO.getOrgIdentifier();
  }

  /**
   * Gets projectIdentifier from InstanceDTO using parentUniqueId-based scope resolution when available.
   */
  private String getProjectIdentifier(InstanceDTO instanceDTO) {
    if (EmptyPredicate.isNotEmpty(instanceDTO.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfoOpt = scopeInfoService.getScopeInfoFromUniqueId(
          instanceDTO.getAccountIdentifier(), instanceDTO.getParentUniqueId());
      if (scopeInfoOpt.isPresent()) {
        return scopeInfoOpt.get().getProjectIdentifier();
      }
    }
    return instanceDTO.getProjectIdentifier();
  }
}
