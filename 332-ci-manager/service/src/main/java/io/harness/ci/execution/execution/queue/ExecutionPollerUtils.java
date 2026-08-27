/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.execution.queue;

import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutionPollerUtils {
  @Inject(optional = true) CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject(optional = true) HsqsClientService hsqsClientService;

  private static final int THREAD_SLEEP_TIME_IN_MILLIS = 200;
  private static final int THREAD_SLEEP_TIME_IN_MILLIS_BUSY = 10;

  private static final int DEFAULT_BATCH_SIZE = 10;

  protected abstract CITaskMessageProcessor getCITaskMessageProcessor();

  public String getModuleName() {
    return ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
  }

  public int getBatchSize() {
    return ciExecutionServiceConfig.getQueueServiceClientConfig().getBatchSize() > 0
        ? ciExecutionServiceConfig.getQueueServiceClientConfig().getBatchSize()
        : DEFAULT_BATCH_SIZE;
  }

  public void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (Exception ex) {
      log.error("got error while reading messages from hsqs consumer. Retrying again...", ex);
    }
  }

  public void pollAndProcessMessages() {
    try {
      List<DequeueResponse> messages = hsqsClientService.dequeue(DequeueRequest.builder()
                                                                     .batchSize(getBatchSize())
                                                                     .consumerName(this.getModuleName())
                                                                     .topic(getCITaskMessageProcessor().getTopic())
                                                                     .maxWaitDuration(5)
                                                                     .build());
      for (DequeueResponse message : messages) {
        processMessage(message);
      }

      sleepForMessageSize(messages);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    ProcessMessageResponse processMessageResponse = getCITaskMessageProcessor().processMessage(message);
    processResults(processMessageResponse, message);
  }

  public void readEventsFrameworkMessagesWithOrder() {
    try {
      pollAndProcessMessagesWithOrder();
    } catch (Exception ex) {
      log.error("got error while reading messages from hsqs consumer. Retrying again...", ex);
    }
  }

  public void pollAndProcessMessagesWithOrder() {
    try {
      int batchSize = getBatchSize();
      List<DequeueResponse> messages = hsqsClientService.dequeue(DequeueRequest.builder()
                                                                     .batchSize(batchSize)
                                                                     .consumerName(this.getModuleName())
                                                                     .topic(getCITaskMessageProcessor().getTopic())
                                                                     .maxWaitDuration(5)
                                                                     .build());

      // for IaCM we want to keep order of pipelines in queue, so to do that we read (hopefully) all messages from queue
      // and sort them by started at value
      messages.sort(Comparator.comparingLong(dequeueResponse
          -> RecastOrchestrationUtils.fromJson(dequeueResponse.getPayload(), CIInitTaskArgs.class)
                 .getAmbiance()
                 .getStartTs()));

      processMessagesWithFailedSubtopicCheck(messages);

      sleepForMessageSize(messages);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void sleepForMessageSize(List<DequeueResponse> messages) throws Exception {
    if (messages.size() < getBatchSize()) {
      TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_TIME_IN_MILLIS);
    } else {
      // adding this log as this means that we didn't read all messages from the queue, in which case the order of the
      // execution might not be kept
      log.warn("Number of read messages in one batch is {}. Some messages might not be read in this iteration. "
              + "Consider increasing the batch size",
          messages.size());
      TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_TIME_IN_MILLIS_BUSY);
    }
  }

  private void processMessagesWithFailedSubtopicCheck(List<DequeueResponse> messages) {
    // here we are going to store accounts for which we already get False response. In the meantime result may change,
    // but we need to keep sorted order for account executions
    Set<String> failedSubtopics = new HashSet<>();
    for (DequeueResponse message : messages) {
      log.info("Read message with message id {} from hsqs", message.getItemId());

      String subtopic = extractSubtopicFromQueueKey(message.getQueueKey());
      ProcessMessageResponse processMessageResponse = failedSubtopics.contains(subtopic)
          ? ProcessMessageResponse.builder().success(Boolean.FALSE).build()
          : getCITaskMessageProcessor().processMessage(message);
      failedSubtopics.addAll(processResults(processMessageResponse, message));
    }
  }

  public Set<String> processResults(ProcessMessageResponse processMessageResponse, DequeueResponse message) {
    Set<String> failedSubtopics = new HashSet<>();
    try {
      String extractedSubtopic = extractSubtopicFromQueueKey(message.getQueueKey());
      if (processMessageResponse.getSuccess()) {
        hsqsClientService.ack(AckRequest.builder()
                                  .itemId(message.getItemId())
                                  .topic(getCITaskMessageProcessor().getTopic())
                                  .subTopic(extractedSubtopic)
                                  .consumerName(this.getModuleName())
                                  .build());
      } else {
        // add account in HashSet, so we don't do for this account
        failedSubtopics.add(extractedSubtopic);
        UnAckRequest unAckRequest = UnAckRequest.builder()
                                        .itemId(message.getItemId())
                                        .topic(getCITaskMessageProcessor().getTopic())
                                        .subTopic(extractedSubtopic)
                                        .build();
        hsqsClientService.unack(unAckRequest);
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client for message id: {}", message.getItemId(), ex);
    }
    return failedSubtopics;
  }

  /**
   * Extracts subtopic from queue key.
   * Example: "hsqs:localhost:streams:global_capacity_queue-ci:MacOS-Arm64:queue" -> "MacOS-Arm64"
   */
  private String extractSubtopicFromQueueKey(String queueKey) {
    if (queueKey == null || queueKey.isEmpty()) {
      return "";
    }
    String[] parts = queueKey.split(":");
    // Subtopic is the second-to-last segment
    if (parts.length >= 2) {
      return parts[parts.length - 2];
    }
    return "";
  }
}
