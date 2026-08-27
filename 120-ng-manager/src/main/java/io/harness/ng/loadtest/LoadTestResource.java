/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.loadtest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit.http.Body;

@OwnedBy(HarnessTeam.CHAOS)
@Api(value = "loadtest", hidden = true)
@Path("loadtest")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Slf4j
@NextGenManagerAuth
public class LoadTestResource {
  LoadTestService loadTestService;

  @POST
  @Path("/notify")
  @InternalApi
  @ApiOperation(value = "Notify on completion of load test run", nickname = "loadTestStepNotify", hidden = true)
  public ResponseDTO<Boolean> loadTestStepNotify(@Body LoadTestStepNotifyResponse stepNotifyResponse) {
    try {
      String notifyId = stepNotifyResponse != null ? stepNotifyResponse.getNotifyId() : null;
      log.info("Received load test step notify request for notifyId: {}, data: {}", notifyId,
          stepNotifyResponse != null && stepNotifyResponse.getData() != null ? stepNotifyResponse.getData().getStatus()
                                                                             : "null");
      if (stepNotifyResponse == null) {
        log.warn("Received null stepNotifyResponse for notifyId: {}", notifyId);
        return ResponseDTO.newResponse(false);
      }
      loadTestService.notifyStep(notifyId, stepNotifyResponse.getData());
      log.info("Successfully processed load test step notify for notifyId: {}", notifyId);
      return ResponseDTO.newResponse(true);
    } catch (Exception e) {
      log.error("Failed to notify load test step completion for notifyId: {}",
          stepNotifyResponse != null ? stepNotifyResponse.getNotifyId() : "null", e);
      return ResponseDTO.newResponse(false);
    }
  }
}
