/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/licenses/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
public class SystemEventPayload {
  String eventType;
  String sourcePipelineIdentifier;
  String planExecutionId;
}
