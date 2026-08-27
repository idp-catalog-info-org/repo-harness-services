/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.aitestautomation;

import io.harness.aitestautomation.models.AiTestAutomationCallbackRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

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
@Api(value = "aiTestAutomation", hidden = false)
@Path("aiTestAutomation")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json"})
@Consumes({"application/json"})
@Slf4j
public class AiTestAutomationCallbackResource {
  private final AiTestAutomationCallbackService callbackService;

  @POST
  @Path("/notify")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Notify on completion of AI test execution", nickname = "aiTestNotify", hidden = false)
  public ResponseDTO<Boolean> aiTestNotify(AiTestAutomationCallbackRequest callbackRequest) {
    try {
      log.info("Received AI test callback notification for test suite: {}",
          callbackRequest.getTestSuite() != null ? callbackRequest.getTestSuite().getName() : "unknown");
      boolean result = callbackService.notifyCompletion(callbackRequest);
      return ResponseDTO.newResponse(result);
    } catch (Exception e) {
      log.error("Error processing AI test callback notification", e);
      return ResponseDTO.newResponse(false);
    }
  }
}