/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.triggers.webhook.service;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventStatus;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookConfirmationHelper;
import io.harness.pms.triggers.webhook.service.impl.TriggerCustomWebhookExecutionServiceImpl;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerCustomWebhookExecutionServiceImplTest extends PipelineServiceTestBase {
  @Mock private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Mock private TriggerWebhookConfirmationHelper ngTriggerWebhookConfirmationHelper;

  @Mock private NGTriggerService ngTriggerService;
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private TriggerFailureNotificationHelper triggerFailureNotificationHelper;
  @InjectMocks TriggerCustomWebhookExecutionServiceImpl triggerCustomWebhookExecutionService;

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessageSuccess() {
    String accountId = "account123";
    String eventCorrelationId = "event123";
    TriggerCustomWebhookEvent event = TriggerCustomWebhookEvent.builder()
                                          .uuid(eventCorrelationId)
                                          .accountId(accountId)
                                          .processingStatus(TriggerCustomWebhookEventStatus.QUEUED.name())
                                          .build();
    WebhookEventProcessingResult processingResult =
        WebhookEventProcessingResult.builder()
            .responses(Arrays.asList(
                TriggerEventResponse.builder().finalStatus(FinalStatus.TARGET_EXECUTION_REQUESTED).build()))
            .mappedToTriggers(true)
            .build();
    when(ngTriggerService.updateTriggerCustomWebhookEvent(eventCorrelationId, null,
             TriggerCustomWebhookEventStatus.PROCESSING.name(),
             Arrays.asList(TriggerCustomWebhookEventStatus.QUEUED.name())))
        .thenReturn(event);
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TriggerWebhookEvent.builder());
    when(ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(any(), any())).thenReturn(processingResult);
    boolean result = triggerCustomWebhookExecutionService.processMessage(accountId, eventCorrelationId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessageEventNotFound() {
    String accountId = "account123";
    String eventCorrelationId = "event123";

    when(ngTriggerService.updateTriggerCustomWebhookEvent(eventCorrelationId, null,
             TriggerCustomWebhookEventStatus.PROCESSING.name(),
             Arrays.asList(TriggerCustomWebhookEventStatus.QUEUED.name())))
        .thenReturn(null);
    boolean result = triggerCustomWebhookExecutionService.processMessage(accountId, eventCorrelationId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessageWithNullResponses() {
    String accountId = "account123";
    String eventCorrelationId = "event123";
    TriggerCustomWebhookEvent event = TriggerCustomWebhookEvent.builder()
                                          .uuid(eventCorrelationId)
                                          .accountId(accountId)
                                          .processingStatus(TriggerCustomWebhookEventStatus.QUEUED.name())
                                          .build();
    WebhookEventProcessingResult processingResult =
        WebhookEventProcessingResult.builder()
            .responses(Arrays.asList(
                null, TriggerEventResponse.builder().finalStatus(FinalStatus.TARGET_EXECUTION_REQUESTED).build(), null))
            .mappedToTriggers(true)
            .build();
    when(ngTriggerService.updateTriggerCustomWebhookEvent(eventCorrelationId, null,
             TriggerCustomWebhookEventStatus.PROCESSING.name(),
             Arrays.asList(TriggerCustomWebhookEventStatus.QUEUED.name())))
        .thenReturn(event);
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TriggerWebhookEvent.builder());
    when(ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(any(), any())).thenReturn(processingResult);
    boolean result = triggerCustomWebhookExecutionService.processMessage(accountId, eventCorrelationId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessageWithException() {
    String accountId = "account123";
    String eventCorrelationId = "event123";
    TriggerCustomWebhookEvent event = TriggerCustomWebhookEvent.builder()
                                          .uuid(eventCorrelationId)
                                          .accountId(accountId)
                                          .processingStatus(TriggerCustomWebhookEventStatus.QUEUED.name())
                                          .build();
    when(ngTriggerService.updateTriggerCustomWebhookEvent(eventCorrelationId, null,
             TriggerCustomWebhookEventStatus.PROCESSING.name(),
             Arrays.asList(TriggerCustomWebhookEventStatus.QUEUED.name())))
        .thenReturn(event);
    when(ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TriggerWebhookEvent.builder());
    when(ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(any(), any()))
        .thenThrow(new RuntimeException("Processing failed"));
    boolean result = triggerCustomWebhookExecutionService.processMessage(accountId, eventCorrelationId);
    assertThat(result).isFalse();
  }
}
