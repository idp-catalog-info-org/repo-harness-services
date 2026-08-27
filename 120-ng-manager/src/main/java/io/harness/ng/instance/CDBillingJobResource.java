/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.instance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.publishing.BillingEventPublisher;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.stats.billing.CDBillingMetricJob;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * On-demand trigger for CDBillingMetricJob. Collects CD/GitOps billing events for the
 * 4-hour window ending at the given timestamp (same logic as the cron run, without the
 * distributed lock). Collect-only by default; publish=true also pushes events to Kafka.
 */
@OwnedBy(HarnessTeam.CDP)
@Api(value = "/cd-billing", hidden = true)
@Path("/cd-billing")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CDBillingJobResource {
  private final CDBillingMetricJob cdBillingMetricJob;
  private final BillingEventPublisher billingEventPublisher;

  @POST
  @Path("/collect")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Collects CD billing events for the 4-hour window ending at the given timestamp",
      nickname = "collectCDBillingEventsForWindow")
  public ResponseDTO<CDBillingCollectionResponse>
  collectBillingEvents(@QueryParam("timestamp") Long timestamp,
      @QueryParam("publish") @DefaultValue("false") boolean publish) throws Exception {
    if (timestamp == null) {
      throw new BadRequestException("timestamp query param (epoch millis) is required");
    }

    String executionDate = Instant.ofEpochMilli(timestamp).atOffset(ZoneOffset.UTC).toLocalDate().toString();
    log.info("On-demand CD billing collection requested: timestamp={}, executionDate={}, publish={}", timestamp,
        executionDate, publish);

    List<BillingEvent> events = cdBillingMetricJob.collectBillingEvents(executionDate, timestamp);

    boolean publishAttempted = publish && !events.isEmpty();
    if (publishAttempted) {
      billingEventPublisher.batchPublish(events, AuthorizationServiceHeader.NG_MANAGER);
    }

    return ResponseDTO.newResponse(
        CDBillingCollectionResponse.builder()
            .executionDate(executionDate)
            .windowEndTimestamp(timestamp)
            .eventCount(events.size())
            .publishAttempted(publishAttempted)
            .events(events.stream().map(CDBillingJobResource::toEventSummary).collect(Collectors.toList()))
            .build());
  }

  private static CDBillingEventSummary toEventSummary(BillingEvent event) {
    return CDBillingEventSummary.builder()
        .idempotencyKey(event.getIdempotencyKey())
        .accountId(event.getAccountId())
        .metric(event.getMetric().name())
        .value(event.getValue())
        .eventTimestamp(event.getEventTimestamp().getSeconds() * 1000 + event.getEventTimestamp().getNanos() / 1000000)
        .resourceUniqueIdentifier(event.getResourceUniqueIdentifier())
        .resourceParentUniqueIdentifier(event.getResourceParentUniqueIdentifier())
        .tags(event.getTagsMap())
        .build();
  }

  @Value
  @Builder
  public static class CDBillingCollectionResponse {
    String executionDate;
    long windowEndTimestamp;
    int eventCount;
    boolean publishAttempted;
    List<CDBillingEventSummary> events;
  }

  @Value
  @Builder
  public static class CDBillingEventSummary {
    String idempotencyKey;
    String accountId;
    String metric;
    double value;
    long eventTimestamp;
    String resourceUniqueIdentifier;
    String resourceParentUniqueIdentifier;
    Map<String, String> tags;
  }
}
