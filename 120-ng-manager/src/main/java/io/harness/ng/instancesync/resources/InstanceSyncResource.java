/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.instancesync.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.InvalidPerpetualTaskResponse;
import io.harness.dtos.DeleteDuplicateInstancesResponseDTO;
import io.harness.dtos.iterator.InstanceSyncTaskScheduleDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.perpetualtask.instancesync.InstanceSyncResponseV2;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.cleanup.InstanceDuplicateService;
import io.harness.service.instanceorphans.InstanceOrphansService;
import io.harness.service.instancesync.InstanceSyncService;
import io.harness.service.instancesyncperpetualtask.InvalidPerpetualTaskInfoService;
import io.harness.service.iterator.InstanceSyncTaskScheduleService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(HarnessTeam.DX)
@Api("instancesync")
@Path("instancesync")
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
public class InstanceSyncResource {
  private static final String LOG_ERROR_TEMPLATE =
      "Received instance sync perpetual task response for accountId : {} and perpetualTaskId : {} : {}";
  private static final int MAX_LIMIT = 1000;

  private final InstanceSyncService instanceSyncService;
  private final InvalidPerpetualTaskInfoService invalidPerpetualTaskInfoService;
  private final Optional<InstanceSyncTaskScheduleService> taskScheduleService;
  private final InstanceOrphansService instanceOrphansService;
  private final InstanceDuplicateService instanceDuplicateService;
  private final ScopeInfoService scopeInfoService;

  @Named("instance-orphans-cleanup") private final ExecutorService instanceCleanupExecutor;

  @POST
  @Path("/response")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance sync perpetual task response", nickname = "getInstanceSyncPerpetualTaskResponse")
  public ResponseDTO<Boolean> processInstanceSyncPerpetualTaskResponse(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId,
      @Body DelegateResponseData delegateResponseData) {
    InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse =
        (InstanceSyncPerpetualTaskResponse) delegateResponseData;
    log.info("Received instance sync perpetual task response for accountId : {} and perpetualTaskId : {} : {}",
        accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskResponse.toString());
    instanceSyncService.processInstanceSyncByPerpetualTask(
        accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskResponse);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @POST
  @Path("/v3/response")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get instance sync perpetual task v2 response", nickname = "getInstanceSyncPerpetualTaskV2Response")
  public ResponseDTO<Boolean>
  processInstanceSyncPerpetualTaskV2Response(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId,
      @Body InstanceSyncResponseV2 instanceSyncResponseV2) {
    if (log.isDebugEnabled()) {
      log.debug(LOG_ERROR_TEMPLATE, accountIdentifier, perpetualTaskId, instanceSyncResponseV2.toString());
    }

    instanceSyncService.processInstanceSyncByPerpetualTaskV2(
        accountIdentifier, perpetualTaskId, instanceSyncResponseV2);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @GET
  @Path("/task/{perpetualTaskId}/details")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance sync perpetual task details", nickname = "fetchTaskDetails")
  public ResponseDTO<InstanceSyncTaskDetails> fetchTaskDetails(@PathParam("perpetualTaskId") String perpetualTaskId,
      @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @QueryParam(NGCommonEntityConstants.PAGE_SIZE) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    InstanceSyncTaskDetails details =
        instanceSyncService.fetchTaskDetails(perpetualTaskId, accountIdentifier, page, size);
    log.info("Found {} instance sync perpetual task details for accountId {} and perpetualTaskId {}",
        details != null ? (long) details.getDetails().getContent().size() : 0, accountIdentifier, perpetualTaskId);
    return ResponseDTO.newResponse(details);
  }

  @POST
  @Path("/v2/response")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get instance sync perpetual task response", nickname = "getInstanceSyncPerpetualTaskResponseV2")
  public ResponseDTO<Boolean>
  processInstanceSyncPerpetualTaskResponseV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PERPETUAL_TASK_ID) String perpetualTaskId,
      @Body DelegateResponseData delegateResponseData) {
    InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse =
        (InstanceSyncPerpetualTaskResponse) delegateResponseData;
    if (log.isDebugEnabled()) {
      log.debug(LOG_ERROR_TEMPLATE, accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskResponse.toString());
    }
    instanceSyncService.processInstanceSyncByPerpetualTask(
        accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskResponse);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @POST
  @Path("/invalidTaskResponse")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get details of invalid perpetual task", nickname = "processInvalidPerpetualTaskResponse")
  public ResponseDTO<Boolean> processInvalidPerpetualTaskResponse(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body InvalidPerpetualTaskResponse invalidPerpetualTaskResponse) {
    invalidPerpetualTaskInfoService.save(accountIdentifier, invalidPerpetualTaskResponse);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @POST
  @Path("/iterator/schedule")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create or update iterator schedule", nickname = "upsertIteratorSchedule")
  public ResponseDTO<InstanceSyncTaskScheduleDTO> upsertIteratorSchedule(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam("interval") Long interval) {
    if (taskScheduleService.isEmpty()) {
      throw new InternalServerErrorException("Instance sync iterator module is not enabled");
    }

    return ResponseDTO.newResponse(taskScheduleService.get().save(
        InstanceSyncTaskScheduleDTO.builder().accountId(accountIdentifier).interval(interval).build()));
  }

  @DELETE
  @Path("/instances/orphans")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Trigger delete of instance orphans", nickname = "deleteInstanceOrphans")
  public ResponseDTO<Boolean> deleteInstanceOrphans(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @QueryParam("infrastructureKind") List<String> infrastructureKind) {
    instanceCleanupExecutor.submit(() -> {
      instanceOrphansService.deleteOrphanInstances(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, infrastructureKind);
    });
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @DELETE
  @Path("/instances/duplicates")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Delete duplicate instances", nickname = "deleteDuplicateInstances")
  public ResponseDTO<DeleteDuplicateInstancesResponseDTO> deleteDuplicateInstances(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @QueryParam("infrastructureKind") Set<String> infrastructureKinds, @QueryParam("dryRun") boolean dryRun,
      @QueryParam("maxInstances") int maxInstances) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    if (dryRun) {
      return ResponseDTO.newResponse(
          instanceDuplicateService.deduplicateInstances(scopeInfo, serviceId, infrastructureKinds, true, maxInstances));
    } else {
      instanceCleanupExecutor.submit(() -> {
        instanceDuplicateService.deduplicateInstances(scopeInfo, serviceId, infrastructureKinds, false, maxInstances);
      });

      return ResponseDTO.newResponse(DeleteDuplicateInstancesResponseDTO.builder().async(true).build());
    }
  }
}
