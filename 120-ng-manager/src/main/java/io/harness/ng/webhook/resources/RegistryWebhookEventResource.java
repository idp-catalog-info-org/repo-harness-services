/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resources;

import static io.harness.annotations.dev.HarnessTeam.HAR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookRequestDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookResponseDTO;
import io.harness.ng.webhook.services.api.RegistryWebhookEventService;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("registrywebhookevent")
@Path("registrywebhookevent")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(HAR)
public class RegistryWebhookEventResource {
  private RegistryWebhookEventService webhookEventService;

  @POST
  @InternalApi
  @ApiOperation(hidden = true, value = "Upsert a registry webhook event", nickname = "registryWebhookUpsert")
  public ResponseDTO<UpsertRegistryWebhookResponseDTO> upsertWebhook(
      @Valid UpsertRegistryWebhookRequestDTO upsertWebhookRequest) {
    final UpsertRegistryWebhookResponseDTO upsertWebhookResponse =
        webhookEventService.upsertWebhook(upsertWebhookRequest);
    return ResponseDTO.newResponse(upsertWebhookResponse);
  }
}
