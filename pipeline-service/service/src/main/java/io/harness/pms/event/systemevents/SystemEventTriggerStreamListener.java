/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.event.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SystemEventEnvelope;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class SystemEventTriggerStreamListener
    extends PmsAbstractMessageListener<SystemEventEnvelope, SystemEventTriggerHandler> {
  @Inject
  public SystemEventTriggerStreamListener(
      @Named(SDK_SERVICE_NAME) String serviceName, SystemEventTriggerHandler handler) {
    super(serviceName, SystemEventEnvelope.class, handler);
  }

  @Override
  protected SystemEventEnvelope extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return SystemEventEnvelope.parseFrom(message);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
