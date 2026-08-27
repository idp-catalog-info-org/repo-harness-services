/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.sto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

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
import retrofit.http.Body;

@OwnedBy(HarnessTeam.STO)
@Api(value = "sto", hidden = true)
@Path("sto")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@NextGenManagerAuth
public class StoResource {
  StoService stoService;

  @POST
  @Path("/trigger-notification/exemption")
  @InternalApi
  @ApiOperation(value = "Trigger notification for STO exemption", nickname = "stoExemptionNotification", hidden = true)
  public ResponseDTO<Void> stoExemptionNotificationTrigger(@Body StoExemptionNotificationRequest request) {
    stoService.exemptionNotificationTrigger(request);
    return ResponseDTO.newResponse();
  }

  @POST
  @Path("/trigger-notification/qwiet-trial")
  @InternalApi
  @ApiOperation(
      value = "Trigger notification for Qwiet trial activation", nickname = "stoQwietTrialNotification", hidden = true)
  public ResponseDTO<Void>
  stoQwietTrialNotificationTrigger(@Body StoQwietTrialNotificationRequest request) {
    stoService.qwietTrialNotificationTrigger(request);
    return ResponseDTO.newResponse();
  }

  @POST
  @Path("/trigger-notification/qwiet-trial-expiry")
  @InternalApi
  @ApiOperation(value = "Trigger notification for Qwiet trial expiry", nickname = "stoQwietTrialExpiryNotification",
      hidden = true)
  public ResponseDTO<Void>
  stoQwietTrialExpiryNotificationTrigger(@Valid @NotNull @Body StoQwietTrialExpiryNotificationRequest request) {
    stoService.qwietTrialExpiryNotificationTrigger(request);
    return ResponseDTO.newResponse();
  }
}
