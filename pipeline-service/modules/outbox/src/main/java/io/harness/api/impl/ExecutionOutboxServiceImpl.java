/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_EVENT_FILTER;
import static io.harness.outbox.api.impl.OutboxServiceImpl.createOutboxEvent;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ExecutionOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.api.ExecutionOutboxDao;
import io.harness.event.Event;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ExecutionOutboxServiceImpl implements OutboxService {
  @Inject private ExecutionOutboxDao nodeExecutionOutboxDao;

  @Override
  public ExecutionOutboxEvent save(Event event) {
    return nodeExecutionOutboxDao.save(
        fromOutboxEventToExecutionOutboxEventMapper(createOutboxEvent(NG_DEFAULT_OBJECT_MAPPER, event)));
  }

  @Override
  public ExecutionOutboxEvent update(OutboxEvent executionOutboxEvent) {
    return nodeExecutionOutboxDao.save(fromOutboxEventToExecutionOutboxEventMapper(executionOutboxEvent));
  }

  @Override
  public List<OutboxEvent> list(OutboxEventFilter outboxEventFilter) {
    if (outboxEventFilter == null) {
      outboxEventFilter = DEFAULT_OUTBOX_EVENT_FILTER;
    }
    return nodeExecutionOutboxDao.list(outboxEventFilter).stream().map(o -> (OutboxEvent) o).toList();
  }

  private ExecutionOutboxEvent fromOutboxEventToExecutionOutboxEventMapper(OutboxEvent outboxEvent) {
    return ExecutionOutboxEvent.builder()
        .resourceScope(outboxEvent.getResourceScope())
        .resource(outboxEvent.getResource())
        .eventData(outboxEvent.getEventData())
        .eventType(outboxEvent.getEventType())
        .globalContext(outboxEvent.getGlobalContext())
        .build();
  }

  @Override
  public boolean delete(String outboxEventId) {
    nodeExecutionOutboxDao.delete(outboxEventId);
    return false;
  }
}