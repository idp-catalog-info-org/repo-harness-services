/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.KafkaOutboxEvent;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventPoll.KafkaOutboxEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class KafkaOutboxEventHandlerImpl implements KafkaOutboxEventHandler {
  private final PipelineOutboxEventHandler pipelineOutboxEventHandler;

  @Override
  public boolean processEvent(KafkaOutboxEvent kafkaOutboxEvent) {
    log.debug("Delegating KafkaOutboxEvent processing to PipelineOutboxEventHandler for event type: {}",
        kafkaOutboxEvent.getEventType());
    return pipelineOutboxEventHandler.handle(kafkaOutboxEvent);
  }
}
