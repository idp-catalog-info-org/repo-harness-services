/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notificationbodyresolution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.NotificationBodyResolutionRequest;
import io.harness.pms.pipeline.NotificationBodyResolutionResponse;
import io.harness.security.annotations.InternalApi;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Hidden;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Path("/notification")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@InternalApi
public interface NotificationBodyResolutionInterface {
  @POST
  @Path("/resolve-body")
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<NotificationBodyResolutionResponse> resolveNotificationBody(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Body NotificationBodyResolutionRequest notificationBodyResolutionRequest);
}
