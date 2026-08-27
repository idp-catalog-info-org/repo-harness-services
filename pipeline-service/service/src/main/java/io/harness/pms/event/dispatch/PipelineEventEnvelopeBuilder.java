/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.dispatch;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.events.dispatch.v1.EventEnvelope;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.time.Instant;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineEventEnvelopeBuilder {
  private static final String SOURCE = "harness";
  private static final String SCHEMA_VERSION = "v1";

  public static EventEnvelope build(Ambiance ambiance, Status status) {
    String eventType = deriveEventType(status);
    Instant now = Instant.now();

    Struct payload = Struct.newBuilder()
                         .putFields("pipeline_identifier", stringValue(AmbianceUtils.getPipelineIdentifier(ambiance)))
                         .putFields("execution_id", stringValue(ambiance.getPlanExecutionId()))
                         .putFields("status", stringValue(status.name()))
                         .build();

    return EventEnvelope.newBuilder()
        .setId(UUID.randomUUID().toString())
        .setType(eventType)
        .setSource(SOURCE)
        .setAccountId(AmbianceUtils.getAccountId(ambiance))
        .setOrgId(AmbianceUtils.getOrgIdentifier(ambiance))
        .setProjectId(AmbianceUtils.getProjectIdentifier(ambiance))
        .setTime(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
        .setCorrelationId(ambiance.getPlanExecutionId())
        .setHopCount(0)
        .setSchemaVersion(SCHEMA_VERSION)
        .setPayload(payload)
        .build();
  }

  private static String deriveEventType(Status status) {
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return "harness.pipeline.completed";
    }
    if (StatusUtils.brokeStatuses().contains(status)) {
      return "harness.pipeline.failed";
    }
    return "harness.pipeline.status_update";
  }

  private static Value stringValue(String s) {
    return Value.newBuilder().setStringValue(s != null ? s : "").build();
  }
}
