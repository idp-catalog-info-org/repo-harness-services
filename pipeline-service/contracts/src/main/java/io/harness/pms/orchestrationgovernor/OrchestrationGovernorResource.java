/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.orchestrationgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Admin control surface for the pipeline-execution-events flow governor. All mutating endpoints
 * write to the shared Redis-backed {@code FlowGovernorStateStore}; every governed
 * {@code ThrottledKafkaConsumer} observes the new mode within the cache refresh + poll window
 * (~35s worst case). Endpoints are SERVICE-principal only.
 */
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@Api("orchestration-governor")
@Path("/orchestration/governor")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "OrchestrationGovernor",
    description = "Internal admin API to halt / throttle / resume the pipeline orchestration event flow.")
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface OrchestrationGovernorResource {
  @POST
  @Path("/halt")
  @ApiOperation(value = "Halt all governed orchestration Kafka consumers.", nickname = "haltOrchestrationGovernor")
  @Operation(operationId = "haltOrchestrationGovernor",
      summary = "Halts all governed orchestration Kafka consumers. Workers drop in-flight records; the poll loop keeps"
          + " turning so no rebalance is triggered.")
  @Hidden
  ResponseDTO<FlowGovernorStateDTO>
  halt();

  @POST
  @Path("/resume/throttled")
  @ApiOperation(
      value = "Resume in THROTTLED mode with a per-pod RPS cap.", nickname = "resumeOrchestrationGovernorThrottled")
  @Operation(operationId = "resumeOrchestrationGovernorThrottled",
      summary = "Switches the governor into THROTTLED mode. `rps` is per-pod (cluster RPS = rps × replica count)."
          + " When `consumer` is supplied the RPS becomes a per-consumer override; otherwise it is the default RPS"
          + " applied to any consumer without an override.")
  @Hidden
  ResponseDTO<FlowGovernorStateDTO>
  resumeThrottled(@Parameter(description = "Per-pod requests-per-second cap. Must be between 1 and 10000.",
                      required = true) @QueryParam("rps") Integer rps,
      @Parameter(description = "Optional consumer key for a per-consumer override. Must be one of the"
              + " FlowGovernorConsumerKeys constants (e.g. \"initiateNode\", \"sdkStepResponse\").")
      @QueryParam("consumer") String consumer);

  @POST
  @Path("/resume/full")
  @ApiOperation(value = "Resume in NORMAL mode (no throttle).", nickname = "resumeOrchestrationGovernorFull")
  @Operation(operationId = "resumeOrchestrationGovernorFull",
      summary = "Returns the governor to NORMAL mode. NORMAL-mode capacity is defined by yaml config"
          + " (flowGovernorConfig.normalRps + normalRpsByConsumer); this call clears the throttled overrides.")
  @Hidden
  ResponseDTO<FlowGovernorStateDTO>
  resumeFull();

  @GET
  @Path("/state")
  @ApiOperation(value = "Get the current governor state.", nickname = "getOrchestrationGovernorState")
  @Operation(operationId = "getOrchestrationGovernorState",
      summary = "Returns the current FlowGovernorState as persisted in Redis. Consumers observe this state after their"
          + " cache refresh + mode poll window (~35s worst case).")
  @Hidden
  ResponseDTO<FlowGovernorStateDTO>
  getState();
}
