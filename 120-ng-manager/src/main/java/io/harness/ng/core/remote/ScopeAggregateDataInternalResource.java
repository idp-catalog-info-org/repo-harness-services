/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.ScopeAggregateDataBatchRequest;
import io.harness.ng.core.dto.ScopeAggregateDataBatchResponse;
import io.harness.ng.core.dto.ScopeAggregateDataRequest;
import io.harness.ng.core.dto.ScopeAggregateDataResponse;
import io.harness.ng.core.services.ScopeAggregateDataProvider;
import io.harness.security.annotations.InternalApi;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("scope-aggregate-data")
@Path("scope-aggregate-data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class ScopeAggregateDataInternalResource {
  private final ScopeAggregateDataProvider scopeAggregateDataProvider;

  @POST
  @ApiOperation(value = "Get aggregate data for a scope", nickname = "getScopeAggregateData", hidden = true)
  @InternalApi
  @Timed
  @ResponseMetered
  public ResponseDTO<ScopeAggregateDataResponse> getScopeAggregateData(
      @NotNull @Valid ScopeAggregateDataRequest request) {
    return ResponseDTO.newResponse(scopeAggregateDataProvider.getAggregateData(request));
  }

  @POST
  @Path("batch")
  @ApiOperation(
      value = "Get aggregate data for a batch of scopes", nickname = "getScopeAggregateDataBatch", hidden = true)
  @InternalApi
  @Timed
  @ResponseMetered
  public ResponseDTO<ScopeAggregateDataBatchResponse>
  getScopeAggregateDataBatch(@NotNull @Valid ScopeAggregateDataBatchRequest request) {
    return ResponseDTO.newResponse(scopeAggregateDataProvider.getAggregateDataBatch(request));
  }
}
