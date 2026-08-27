/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.UNRECOGNIZED_WEBHOOK;
import static io.harness.constants.Constants.UNRECOGNIZED_WEBHOOK_TYPE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.WebhookConstants;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookPayloadService;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.serializer.JsonUtils;
import io.harness.telemetry.helpers.WebhookInstrumentationHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.StreamingOutput;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api(WebhookConstants.WEBHOOK_ENDPOINT)
@Path(WebhookConstants.WEBHOOK_ENDPOINT)
@Produces({"application/json", "application/yaml", "text/plain", MediaType.APPLICATION_OCTET_STREAM})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Webhook Event Handler", description = "Contains APIs corresponding to Webhook Triggers.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(PIPELINE)
public class NgWebhookResource {
  private WebhookService webhookService;
  private WebhookHelper webhookHelper;
  private WebhookPayloadService webhookPayloadService;
  private GitXWebhookService gitXWebhookService;
  private WebhookInstrumentationHelper webhookInstrumentationHelper;

  @POST
  @Operation(operationId = "processWebhookEvent", summary = "Process event payload for webhook triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns UUID of newly created webhook processing event.")
      })
  @ApiOperation(value = "accept webhook event", nickname = "webhookEndpoint")
  @Timed
  @ResponseMetered
  @PublicApi
  public Object
  processWebhookEvent(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.WEBHOOK_IDENTIFIER) String webhookIdentifier, @NotNull String eventPayload,
      @Context HttpHeaders httpHeaders) {
    if (EmptyPredicate.isEmpty(webhookIdentifier)) {
      return processWebhook(accountIdentifier, eventPayload, httpHeaders.getRequestHeaders());
    } else {
      return processNonGitWebhook(accountIdentifier, orgIdentifier, projectIdentifier, webhookIdentifier, eventPayload,
          httpHeaders.getRequestHeaders());
    }
  }

  @Hidden
  public ResponseDTO<String> processWebhook(
      String accountIdentifier, String eventPayload, MultivaluedMap<String, String> httpHeaders) {
    WebhookEvent eventEntity = WebhookHelper.toNGTriggerWebhookEvent(
        eventPayload, httpHeaders, Scope.builder().accountIdentifier(accountIdentifier).build(), null);
    if (eventEntity != null) {
      WebhookEvent newEvent = webhookService.addEventToQueue(eventEntity);
      return ResponseDTO.newResponse(newEvent.getUuid());
    } else {
      return ResponseDTO.newResponse(UNRECOGNIZED_WEBHOOK);
    }
  }

  @Hidden
  private Object processNonGitWebhook(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String webhookIdentifier, String eventPayload, MultivaluedMap<String, String> httpHeaders) {
    var webhook = gitXWebhookService
                      .getWebhookByIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, webhookIdentifier)
                      .orElseThrow(()
                                       -> new BadRequestException(
                                           String.format("Webhook with identifier %s not found", webhookIdentifier)));
    if (!Lists.newArrayList(NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE, NGCommonEntityConstants.SLACK_WEBHOOK_TYPE)
             .contains(webhook.getWebhookType())) {
      throw new BadRequestException(UNRECOGNIZED_WEBHOOK_TYPE);
    }
    if (NGCommonEntityConstants.SLACK_WEBHOOK_TYPE.equals(webhook.getWebhookType())) {
      JsonNode jsonNode = JsonUtils.readTree(eventPayload);
      if (("url_verification").equals(jsonNode.get("type").textValue())) {
        return eventPayload;
      }
    }
    Scope scope = Scope.of(
        accountIdentifier, webhook.getOrgIdentifier(), webhook.getProjectIdentifier(), webhook.getParentUniqueId());
    var newEvent = webhookService.createWebhookEvent(scope, webhook, httpHeaders, eventPayload);
    webhookInstrumentationHelper.sendNonGitWebhookEvent(accountIdentifier, webhook);
    return ResponseDTO.newResponse(newEvent.getUuid());
  }

  @GET
  @Path("/payload")
  @Hidden
  @ApiOperation(value = "return webhook payload data as stream", nickname = "payload")
  @InternalApi
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public StreamingOutput getWebhookPayload(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.UUID) String uuid) {
    // `accountIdentifier` is present in the signature solely for routing purposes.
    return webhookPayloadService.readWebhookAllPayloadDataToStreamingOutput(uuid);
  }
}