/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventPoll;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.outbox.OutboxSDKConstants.SERVICE_ID_FOR_OUTBOX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.eventpoll.OutboxEventPollJob;
import io.harness.outbox.monitor.OutboxMetricsServiceImpl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ExecutionOutboxEventPollJob extends OutboxEventPollJob {
  @Inject
  public ExecutionOutboxEventPollJob(@Named("executionOutboxService") OutboxService executionOutboxService,
      OutboxEventHandler outboxEventHandler, PersistentLocker persistentLocker,
      @Named("executionOutboxEventPollConfig") OutboxPollConfiguration outboxPollConfiguration,
      OutboxMetricsServiceImpl outboxMetricsService, @Named(SERVICE_ID_FOR_OUTBOX) String serviceId) {
    super(executionOutboxService, outboxEventHandler, persistentLocker, outboxPollConfiguration, outboxMetricsService,
        serviceId);
  }
}