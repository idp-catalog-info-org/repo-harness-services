/*
 * Copyright 2024 Harness Inc. All rights reserved.
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
import io.harness.dtos.DuplicateInstanceStat;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Api("/instancestats")
@Path("/instancestats")
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
@ScopeInfoResolutionApi
public class InstanceStatsResource {
  private final InstanceService instanceService;

  @GET
  @Path("/duplicates")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets list of duplicate instances", nickname = "getDuplicateInstances")
  public ResponseDTO<List<DuplicateInstanceStat>> getDuplicateInstances(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_ID) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    List<DuplicateInstanceStat> duplicateInstanceStats;
    if (scopeInfo != null) {
      duplicateInstanceStats = fetchFromDB(scopeInfo);
    } else {
      duplicateInstanceStats = fetchFromDB(accountId, orgIdentifier, projectIdentifier);
    }
    return ResponseDTO.newResponse(duplicateInstanceStats);
  }

  private List<DuplicateInstanceStat> fetchFromDB(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return instanceService.getDuplicateInstanceStat(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private List<DuplicateInstanceStat> fetchFromDB(ScopeInfo scopeInfo) {
    return instanceService.getDuplicateInstanceStat(scopeInfo);
  }
}
