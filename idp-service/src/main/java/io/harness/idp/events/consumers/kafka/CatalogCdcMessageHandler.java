/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.kafka;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.idp.events.consumers.debezium.CatalogChangeEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@Singleton
@Slf4j
public class CatalogCdcMessageHandler implements MessageHandler<DebeziumChangeEvent> {
  static final int MAX_RETRIES = 3;
  static final long RETRY_BACKOFF_MS = 500;

  private final CatalogChangeEventHandler eventHandler;

  @Inject
  public CatalogCdcMessageHandler(CatalogChangeEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public void onMessage(DebeziumChangeEvent message, Map<String, String> metadata, Map<String, Object> metricInfo) {
    if (message == null) {
      log.warn("Received null DebeziumChangeEvent for Catalog CDC");
      return;
    }

    log.info("Processing Catalog CDC event: key={}, optype={}", message.getKey(), message.getOptype());

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        boolean result = eventHandler.handleEvent(message);
        if (!result) {
          log.warn("Catalog CDC event processing returned false for key={}, optype={}", message.getKey(),
              message.getOptype());
        }
        return;
      } catch (Exception e) {
        if (attempt < MAX_RETRIES) {
          log.warn("Catalog CDC event processing failed (attempt {}/{}), retrying: key={}, optype={}, error={}",
              attempt, MAX_RETRIES, message.getKey(), message.getOptype(), e.getMessage());
          try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error(
                "Retry interrupted for Catalog CDC event key={}, optype={}", message.getKey(), message.getOptype());
            return;
          }
        } else {
          log.error("Catalog CDC event processing failed after {} attempts for key={}, optype={}: {}", MAX_RETRIES,
              message.getKey(), message.getOptype(), e.getMessage(), e);
        }
      }
    }
  }
}
