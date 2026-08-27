/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.eventsframework.EventsFrameworkConstants.TRIGGER_CUSTOM_WEBHOOK_EVENT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.triggers.webhook.service.TriggerCustomWebhookExecutionService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class CustomTriggerWebhookEventQueueProcessorTest extends CategoryTest {
  @Mock TriggerCustomWebhookExecutionService triggerCustomWebhookExecutionService;
  @Mock HsqsClientService hsqsClientService;
  @Mock ExecutorService executorService;
  @Mock HsqsDequeueConfig hsqsDequeueConfig;
  @InjectMocks CustomTriggerWebhookEventQueueProcessor customTriggerWebhookEventQueueProcessor;

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetTopicName() {
    assertEquals(customTriggerWebhookEventQueueProcessor.getTopicName(), "pms" + TRIGGER_CUSTOM_WEBHOOK_EVENT);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessResponse() {
    String payload =
        "{\"__recast\":\"io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventPayload\",\"accountId\":\"accountId\",\"eventCorrelationId\":\"eventId\"}";
    doReturn(true).when(triggerCustomWebhookExecutionService).processMessage(eq("accountId"), eq("eventId"));
    HsqsProcessMessageResponse processMessageResponse = customTriggerWebhookEventQueueProcessor.processResponse(
        DequeueResponse.builder().itemId("itemId").payload(payload).build());
    verify(triggerCustomWebhookExecutionService, times(1)).processMessage(eq("accountId"), eq("eventId"));
    assertTrue(processMessageResponse.getSuccess());
    assertEquals(processMessageResponse.getAccountId(), "accountId");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testPollAndProcessMessages() {
    List<DequeueResponse> messages = new ArrayList<>();
    messages.add(DequeueResponse.builder().itemId("itemId").payload("payload").build());
    messages.add(DequeueResponse.builder().itemId("itemId1").payload("payload1").build());
    doReturn(messages).when(hsqsClientService).dequeue(any());
    customTriggerWebhookEventQueueProcessor.pollAndProcessMessages();
    verify(executorService, times(2)).execute(any(Runnable.class));
  }
}
