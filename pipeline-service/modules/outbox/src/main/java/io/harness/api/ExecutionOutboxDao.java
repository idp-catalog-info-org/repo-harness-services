/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ExecutionOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.outbox.filter.OutboxMetricsFilter;

import java.util.List;
import java.util.Map;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface ExecutionOutboxDao {
  ExecutionOutboxEvent save(ExecutionOutboxEvent executionOutboxEvent);

  List<ExecutionOutboxEvent> list(OutboxEventFilter outboxEventFilter);

  long count(OutboxMetricsFilter outboxMetricsFilter);

  Map<String, Long> countPerEventType(OutboxMetricsFilter outboxMetricsFilter);

  boolean delete(String outboxEventId);
}