/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.queue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Row shape of {@code step_concurrency_queue}. See TechSpec "The queue store". */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class StepConcurrencyQueueEntry {
  String nodeExecutionId;
  String planExecutionId;
  String accountId;
  Instant createdAt;
}
