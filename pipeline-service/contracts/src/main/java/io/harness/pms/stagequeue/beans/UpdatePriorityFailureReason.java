/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(PIPELINE)
@Schema(name = "UpdatePriorityFailureReason",
    description = "Reason a single stage selector did not get reprioritized."
        + " NOT_FOUND: no live NodeExecution for (pipelineExecutionId, stageIdentifier);"
        + " OUT_OF_SCOPE: the stage's metadata scope falls outside the request scope;"
        + " NOT_QUEUED: the stage is RUNNING (or terminal) and cannot be reprioritized;"
        + " UPSTREAM_REJECTED: the DMS gRPC call returned success=false or threw.")
public enum UpdatePriorityFailureReason {
  NOT_FOUND,
  OUT_OF_SCOPE,
  NOT_QUEUED,
  UPSTREAM_REJECTED
}
