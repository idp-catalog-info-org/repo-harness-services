/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.executionretention.ExecutionRetentionCleanupEvent;
import io.harness.ng.core.event.MessageListener;
import io.harness.timescaledb.Tables;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

/**
 * Listener for execution retention cleanup events.
 * Receives events from pipeline-service when executions are cleaned up
 * and deletes corresponding records from TimescaleDB (pipeline_execution_summary_cd).
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
public class ExecutionRetentionCleanupListener implements MessageListener {
  private static final String LOG_PREFIX = "[EXECUTION_RETENTION_CLEANUP_LISTENER]";
  private static final int MAX_RETRIES = 3;

  private final DSLContext dsl;

  @Inject
  public ExecutionRetentionCleanupListener(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message == null || !message.hasMessage()) {
      return true;
    }

    ExecutionRetentionCleanupEvent event;
    try {
      event = ExecutionRetentionCleanupEvent.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("{} Failed to parse ExecutionRetentionCleanupEvent", LOG_PREFIX, e);
      return false;
    }

    return processCleanupEvent(event);
  }

  private boolean processCleanupEvent(ExecutionRetentionCleanupEvent event) {
    String accountId = event.getAccountIdentifier();
    List<String> planExecutionIds = event.getPlanExecutionIdsList();

    if (planExecutionIds.isEmpty()) {
      return true;
    }

    log.info("{} Processing cleanup for account: {}, planExecutionIds count: {}", LOG_PREFIX, accountId,
        planExecutionIds.size());

    try {
      int deletedCount = deleteFromPipelineExecutionSummaryCd(planExecutionIds);

      log.info(
          "{} Successfully cleaned up {} rows from TimescaleDB for account: {}", LOG_PREFIX, deletedCount, accountId);

      return true;

    } catch (Exception ex) {
      log.error("{} Failed to cleanup TimescaleDB for account: {}", LOG_PREFIX, accountId, ex);
      return false; // Will retry
    }
  }

  @SuppressWarnings("resource") // JOOQ delete doesn't hold resources that need closing for simple execute()
  private int deleteFromPipelineExecutionSummaryCd(List<String> planExecutionIds) {
    int retryCount = 0;

    while (retryCount < MAX_RETRIES) {
      try {
        int deletedCount = dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
                               .where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID.in(planExecutionIds))
                               .execute();

        log.debug("{} Deleted {} rows from pipeline_execution_summary_cd", LOG_PREFIX, deletedCount);
        return deletedCount;

      } catch (DataAccessException ex) {
        retryCount++;
        if (retryCount >= MAX_RETRIES) {
          throw ex;
        }
        log.warn("{} Failed to delete from pipeline_execution_summary_cd, retry {}/{}", LOG_PREFIX, retryCount,
            MAX_RETRIES, ex);
        try {
          Thread.sleep(1000L * retryCount);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during retry", ie);
        }
      }
    }
    return 0;
  }
}
