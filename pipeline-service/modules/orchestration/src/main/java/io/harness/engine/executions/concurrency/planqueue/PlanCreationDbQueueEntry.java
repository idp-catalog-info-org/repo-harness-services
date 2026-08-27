/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.planqueue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Row shape of {@code plan_creation_queue} — the Postgres FIFO queue that replaces the hsqs-based
 * queue-based plan creation flow and backs the per-project concurrency skip-ahead drain.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class PlanCreationDbQueueEntry {
  String planExecutionId;
  String accountId;
  String orgId;
  String projectId;
  // The project's stable DB uniqueId (Ambiance parentUniqueId). Keys the move-safe per-project
  // concurrency counter; org/project can change on a move, this cannot.
  String parentUniqueId;
  // Stored as PriorityType.name() (HIGH/LOW/NORMAL); null tolerated for pre-priority rows.
  String priorityType;
  Instant createdAt;
}
