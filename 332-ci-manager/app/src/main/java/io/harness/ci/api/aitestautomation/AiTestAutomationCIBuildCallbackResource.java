/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api.aitestautomation;

import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.AI)
@Api(value = "aiTestAutomationCIBuild", hidden = false)
@Path("aiTestAutomationCIBuild")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json"})
@Consumes({"application/json"})
@Slf4j
@NextGenManagerAuth
public class AiTestAutomationCIBuildCallbackResource {
  private final AiTestAutomationCIBuildCallbackService callbackService;

  @POST
  @Path("/notify")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Notify on completion of AI build run execution in CI", nickname = "aiBuildRunCINotify", hidden = false)
  public ResponseDTO<Boolean>
  aiBuildRunCINotify(AiTestAutomationPlaywrightCallbackRequest callbackRequest) {
    try {
      log.info("Received AI build run CI callback notification for build: {}", callbackRequest.getBuildName());
      boolean result = callbackService.notifyCompletion(callbackRequest);
      return ResponseDTO.newResponse(result);
    } catch (Exception e) {
      log.error("Error processing AI build run CI callback notification", e);
      return ResponseDTO.newResponse(false);
    }
  }

  @GET
  @Path("/tiToken")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Generate a short-lived TI service token for Relicx to upload test results",
      nickname = "generateTiToken", hidden = false)
  public ResponseDTO<String>
  generateTiToken(@QueryParam("accountId") String accountId) {
    try {
      log.info("Received TI token request for accountId: {}", accountId);
      String token = callbackService.generateTiToken(accountId);
      return ResponseDTO.newResponse(token);
    } catch (Exception e) {
      log.error("Error generating TI token for accountId: {}", accountId, e);
      throw e;
    }
  }
}
