/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.queue.CIInitPollerUtils;
import io.harness.ci.execution.queue.CITaskMessageProcessor;
import io.harness.ci.execution.queue.ProcessMessageResponse;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExecutionPollerUtilsTest extends CategoryTest {
  @InjectMocks CIInitPollerUtils executionPollerUtils;
  @Mock HsqsClientService hsqsClientService;
  @Mock CIExecutionServiceConfig ciExecutionserviceConfig;
  @Mock QueueServiceClientConfig queueServiceClientConfig;
  @Mock CITaskMessageProcessor ciInitTaskMessageProcessor;
  private static final String IDP = "idp";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(queueServiceClientConfig.getTopic()).thenReturn(IDP);
    when(queueServiceClientConfig.getBatchSize()).thenReturn(100);
    when(ciExecutionserviceConfig.getQueueServiceClientConfig()).thenReturn(queueServiceClientConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessMessageWhenMessageListIsEmptyWithOrder() {
    when(hsqsClientService.dequeue(any())).thenReturn(Collections.emptyList());
    executionPollerUtils.pollAndProcessMessages();

    verify(hsqsClientService, times(0)).ack(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessMessageWhenMessageListIsEmpty() {
    when(hsqsClientService.dequeue(any())).thenReturn(Collections.emptyList());
    executionPollerUtils.pollAndProcessMessages();

    verify(hsqsClientService, times(0)).ack(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessMessageWhenOnlyOneMessageWithOrder() {
    List<DequeueResponse> list = new ArrayList<>(List.of(getDequeueResponse("account1", "item1", 1L)));
    when(hsqsClientService.dequeue(any())).thenReturn(list);
    when(ciInitTaskMessageProcessor.processMessage(any()))
        .thenReturn(ProcessMessageResponse.builder().success(true).build());
    when(ciInitTaskMessageProcessor.getTopic()).thenReturn(IDP);

    executionPollerUtils.pollAndProcessMessagesWithOrder();

    AckRequest expectedAck = getExpectedAckRequest("item1", "account1");
    verify(hsqsClientService, times(1)).ack(eq(expectedAck));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessMessageWhenOnlyOneMessage() {
    List<DequeueResponse> list = new ArrayList<>(List.of(getDequeueResponse("account1", "item1", 1L)));
    when(hsqsClientService.dequeue(any())).thenReturn(list);
    when(ciInitTaskMessageProcessor.processMessage(any()))
        .thenReturn(ProcessMessageResponse.builder().success(true).build());
    when(ciInitTaskMessageProcessor.getTopic()).thenReturn(IDP);

    executionPollerUtils.pollAndProcessMessages();

    AckRequest expectedAck = getExpectedAckRequest("item1", "account1");
    verify(hsqsClientService, times(1)).ack(eq(expectedAck));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessUnorderedMessages() {
    DequeueResponse item1 = getDequeueResponse("account1", "item1", 5L);
    DequeueResponse item2 = getDequeueResponse("account1", "item2", 10L);
    DequeueResponse item3 = getDequeueResponse("account2", "item3", 12L);
    DequeueResponse item4 = getDequeueResponse("account1", "item4", 2L);
    DequeueResponse item5 = getDequeueResponse("account2", "item5", 1L);
    DequeueResponse item6 = getDequeueResponse("account1", "item6", 7L);

    List<DequeueResponse> list = new ArrayList<>(List.of(item1, item2, item3, item4, item5, item6));
    when(hsqsClientService.dequeue(any())).thenReturn(list);

    when(ciInitTaskMessageProcessor.processMessage(eq(item1)))
        .thenReturn(ProcessMessageResponse.builder().success(true).build());
    when(ciInitTaskMessageProcessor.processMessage(eq(item4)))
        .thenReturn(ProcessMessageResponse.builder().success(true).build());
    when(ciInitTaskMessageProcessor.processMessage(eq(item5)))
        .thenReturn(ProcessMessageResponse.builder().success(false).build());
    when(ciInitTaskMessageProcessor.processMessage(eq(item6)))
        .thenReturn(ProcessMessageResponse.builder().success(false).build());
    when(ciInitTaskMessageProcessor.getTopic()).thenReturn(IDP);

    executionPollerUtils.pollAndProcessMessagesWithOrder();

    // only 2 ack
    verify(hsqsClientService, times(1)).ack(eq(getExpectedAckRequest("item1", "account1")));
    verify(hsqsClientService, times(1)).ack(eq(getExpectedAckRequest("item4", "account1")));
    // 4 unAck
    verify(hsqsClientService, times(1)).unack(eq(getExpectedUnAckRequest("item2", "account1")));
    verify(hsqsClientService, times(1)).unack(eq(getExpectedUnAckRequest("item3", "account2")));
    verify(hsqsClientService, times(1)).unack(eq(getExpectedUnAckRequest("item5", "account2")));
    verify(hsqsClientService, times(1)).unack(eq(getExpectedUnAckRequest("item6", "account1")));

    verify(ciInitTaskMessageProcessor, times(0)).processMessage(item2);
    verify(ciInitTaskMessageProcessor, times(0)).processMessage(item3);
  }

  private DequeueResponse getDequeueResponse(String accountId, String itemId, Long startedAt) {
    // Include queueKey with subtopic in the format: hsqs:localhost:streams:topic:subtopic:queue
    String queueKey = "hsqs:localhost:streams:" + IDP + ":" + accountId + ":queue";
    return DequeueResponse.builder()
        .itemId(itemId)
        .queueKey(queueKey)
        .payload(RecastOrchestrationUtils.toJson(
            CIInitTaskArgs.builder()
                .ambiance(Ambiance.newBuilder()
                              .setStartTs(startedAt)
                              .putAllSetupAbstractions(Map.of(SetupAbstractionKeys.accountId, accountId))
                              .build())
                .build()))
        .build();
  }

  private AckRequest getExpectedAckRequest(String item, String account) {
    return AckRequest.builder().itemId(item).topic(IDP).subTopic(account).consumerName(IDP).build();
  }

  private UnAckRequest getExpectedUnAckRequest(String item, String account) {
    return UnAckRequest.builder().itemId(item).topic(IDP).subTopic(account).build();
  }
}
