/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.KafkaOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.api.KafkaOutboxDao;
import io.harness.event.Event;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class KafkaOutboxServiceImpl implements OutboxService {
  private final KafkaOutboxDao kafkaOutboxDao;

  @Inject
  public KafkaOutboxServiceImpl(KafkaOutboxDao kafkaOutboxDao) {
    this.kafkaOutboxDao = kafkaOutboxDao;
  }

  @Override
  public OutboxEvent save(Event event) {
    throw new InvalidArgumentsException(
        "Kafka outbox requires explicit topic specification. Use save(Event event, String topic) method instead.");
  }

  public KafkaOutboxEvent save(Event event, String topic) {
    return kafkaOutboxDao.save(event, topic);
  }

  @Override
  public OutboxEvent update(OutboxEvent outboxEvent) {
    if (!(outboxEvent instanceof KafkaOutboxEvent)) {
      throw new InvalidArgumentsException(
          "Expected KafkaOutboxEvent but got: " + outboxEvent.getClass().getSimpleName());
    }
    return kafkaOutboxDao.save((KafkaOutboxEvent) outboxEvent);
  }

  @Override
  public List<OutboxEvent> list(OutboxEventFilter outboxEventFilter) {
    return new ArrayList<>(kafkaOutboxDao.list(outboxEventFilter));
  }

  @Override
  public boolean delete(String outboxEventId) {
    return kafkaOutboxDao.delete(outboxEventId);
  }
}
