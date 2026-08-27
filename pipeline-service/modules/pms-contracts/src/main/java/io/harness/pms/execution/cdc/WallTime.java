/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.cdc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Wall clock time when the MongoDB change event occurred.
 * Uses ISO-8601 format for Spark timestamp parsing compatibility.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class WallTime {
  /** ISO-8601 formatted timestamp string (e.g., "2025-02-10T14:30:00.000Z") */
  @JsonProperty("$date") String date;
}
