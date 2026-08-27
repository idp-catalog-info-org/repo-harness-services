/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Platform event types that system-event triggers can react to.
 */
@OwnedBy(PIPELINE)
public enum SystemEventType {
  @JsonProperty("PipelineFailure") PIPELINE_FAILURE("harness.pipeline.failed"),
  @JsonProperty("PipelineSuccess") PIPELINE_SUCCESS("harness.pipeline.completed");

  private final String eventTypeString;

  SystemEventType(String eventTypeString) {
    this.eventTypeString = eventTypeString;
  }

  /** Returns the canonical event-type string used in the trigger execution stream. */
  public String eventTypeString() {
    return eventTypeString;
  }
}
