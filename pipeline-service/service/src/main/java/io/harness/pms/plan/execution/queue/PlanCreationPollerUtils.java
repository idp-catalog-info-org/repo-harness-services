/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.queue;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreationPollerUtils {
  private static final int THREAD_SLEEP_TIME_IN_MILLIS = 200;
  private static final int DEFAULT_BATCH_SIZE = 10;
  @Inject @Named("queueServiceClientConfig") private QueueServiceClientConfig queueServiceClientConfig;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Inject @Named("PlanCreationExecutorService") private Executor planCreationExecutorService;
  @Inject @Named("planCreationHsqsDequeueConfig") private HsqsDequeueConfig planCreationHsqsDequeueConfig;
  private static final String planCreationTopic = "_plan_creation";

  public String getModuleName() {
    return queueServiceClientConfig.getTopic() + planCreationTopic;
  }

  public int getBatchSize() {
    return planCreationHsqsDequeueConfig.getBatchSize() > 0 ? planCreationHsqsDequeueConfig.getBatchSize()
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
                                                                     .topic(this.getModuleName())
                                                                     .maxWaitDuration(5)
                                                                     .build());
      for (DequeueResponse message : messages) {
        planCreationExecutorService.execute(() -> processMessage(message));
      }
      sleepForMessageSize(messages);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    HsqsProcessMessageResponse processMessageResponse = planCreationQueueRequestHelper.processMessage(message);
    processResults(processMessageResponse, message, null);
  }

  private void sleepForMessageSize(List<DequeueResponse> messages) throws Exception {
    if (messages.size() < getBatchSize()) {
      TimeUnit.MILLISECONDS.sleep(planCreationHsqsDequeueConfig.getThreadSleepTimeInMillis());
    }
  }

  public void processResults(
      HsqsProcessMessageResponse processMessageResponse, DequeueResponse message, String accountId) {
    try {
      if (processMessageResponse.getSuccess()) {
        hsqsClientService.ack(AckRequest.builder()
                                  .itemId(message.getItemId())
                                  .topic(this.getModuleName())
                                  .consumerName(this.getModuleName())
                                  .subTopic(EmptyPredicate.isNotEmpty(processMessageResponse.getSubtopic())
                                          ? processMessageResponse.getSubtopic()
                                          : processMessageResponse.getAccountId())
                                  .build());
      } else {
        UnAckRequest unAckRequest = UnAckRequest.builder()
                                        .itemId(message.getItemId())
                                        .topic(this.getModuleName())
                                        .subTopic(EmptyPredicate.isNotEmpty(processMessageResponse.getSubtopic())
                                                ? processMessageResponse.getSubtopic()
                                                : processMessageResponse.getAccountId())
                                        .build();
        hsqsClientService.unack(unAckRequest);
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client for message id: {}", message.getItemId(), ex);
    }
  }
}