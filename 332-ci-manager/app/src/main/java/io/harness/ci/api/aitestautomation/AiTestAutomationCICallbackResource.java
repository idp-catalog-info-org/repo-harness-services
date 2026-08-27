/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api.aitestautomation;

import io.harness.aitestautomation.models.AiTestAutomationCallbackRequest;
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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.AI)
@Api(value = "aiTestAutomationCI", hidden = false)
@Path("aiTestAutomationCI")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json"})
@Consumes({"application/json"})
@Slf4j
@NextGenManagerAuth
public class AiTestAutomationCICallbackResource {
  private final AiTestAutomationCICallbackService callbackService;

  @POST
  @Path("/notify")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Notify on completion of AI test execution in CI", nickname = "aiTestCINotify", hidden = false)
  public ResponseDTO<Boolean> aiTestCINotify(AiTestAutomationCallbackRequest callbackRequest) {
    try {
      log.info("Received AI test CI callback notification for test suite: {}",
          callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getName() : "unknown");
      boolean result = callbackService.notifyCompletion(callbackRequest);
      return ResponseDTO.newResponse(result);
    } catch (Exception e) {
      log.error("Error processing AI test CI callback notification", e);
      return ResponseDTO.newResponse(false);
    }
  }
}
