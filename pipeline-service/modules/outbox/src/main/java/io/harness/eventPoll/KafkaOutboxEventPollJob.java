/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventPoll;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.outbox.OutboxSDKConstants.SERVICE_ID_FOR_OUTBOX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.eventpoll.OutboxEventPollJob;
import io.harness.outbox.monitor.OutboxMetricsServiceImpl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class KafkaOutboxEventPollJob extends OutboxEventPollJob {
  @Inject
  public KafkaOutboxEventPollJob(@Named("kafkaOutboxService") OutboxService kafkaOutboxService,
      KafkaOutboxEventHandler kafkaOutboxEventHandler, PersistentLocker persistentLocker,
      @Named("kafkaOutboxEventPollConfig") OutboxPollConfiguration outboxPollConfiguration,
      OutboxMetricsServiceImpl outboxMetricsService, @Named(SERVICE_ID_FOR_OUTBOX) String serviceId) {
    super(kafkaOutboxService, kafkaOutboxEventHandler, persistentLocker, outboxPollConfiguration, outboxMetricsService,
        serviceId);
  }
}
