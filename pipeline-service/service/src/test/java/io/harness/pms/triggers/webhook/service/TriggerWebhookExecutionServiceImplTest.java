/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.service;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_PAYLOAD;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.QUEUED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookConfirmationHelper;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImpl;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerWebhookExecutionServiceImplTest extends PipelineServiceTestBase {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Mock TriggerWebhookConfirmationHelper ngTriggerWebhookConfirmationHelper;
  @Mock NGTriggerService ngTriggerService;
  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock TriggerFailureNotificationHelper triggerFailureNotificationHelper;
  @InjectMocks TriggerWebhookExecutionServiceImpl triggerWebhookExecutionServiceImpl;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    IteratorConfig iteratorConfig = IteratorConfig.builder().build();
    triggerWebhookExecutionServiceImpl.registerIterators(iteratorConfig);
    verify(persistenceIteratorFactory, times(1)).createPumpIteratorWithDedicatedThreadPool(any(), any(), any());
    verifyNoMoreInteractions(persistenceIteratorFactory);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleWithFalseSubscriptionConfirmationAndEmptyResponse() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder().responses(Collections.emptyList()).build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookExecutionHelper)
        .handleTriggerWebhookEvent(any(), any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(1)).updateTriggerWebhookEvent(any());
    verify(ngTriggerService, times(1)).deleteTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleWithSubscriptionConfirmationAndEmptyResponse() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder().responses(Collections.emptyList()).build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(1)).updateTriggerWebhookEvent(any());
    verify(ngTriggerService, times(1)).deleteTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleWithFailedScmConnectivity() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).attemptCount(0).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .responses(Collections.singletonList(
                TriggerEventResponse.builder().finalStatus(SCM_SERVICE_CONNECTION_FAILED).build()))
            .build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(2)).updateTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleInvalidPayload() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).attemptCount(0).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .responses(Collections.singletonList(TriggerEventResponse.builder().finalStatus(INVALID_PAYLOAD).build()))
            .build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(1)).updateTriggerWebhookEvent(any());
    verify(ngTriggerService, times(1)).deleteTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleValidPayload() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).attemptCount(0).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .responses(Collections.singletonList(
                TriggerEventResponse.builder().finalStatus(TARGET_EXECUTION_REQUESTED).build()))
            .mappedToTriggers(true)
            .build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    doReturn(null).when(triggerEventHistoryRepository).save(any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(1)).updateTriggerWebhookEvent(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleException() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).attemptCount(0).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .responses(Collections.singletonList(
                TriggerEventResponse.builder().finalStatus(TARGET_EXECUTION_REQUESTED).build()))
            .mappedToTriggers(true)
            .build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    doThrow(new InvalidRequestException("exception"))
        .when(triggerEventHistoryRepository)
        .upsert(any(TriggerEventHistory.class), any(Query.class));
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(ngTriggerService, times(2)).updateTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testValueGotUpserted() {
    TriggerWebhookEvent event =
        TriggerWebhookEvent.builder().isSubscriptionConfirmation(true).accountId("accountId").attemptCount(0).build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .responses(Collections.singletonList(
                TriggerEventResponse.builder().finalStatus(TARGET_EXECUTION_REQUESTED).build()))
            .mappedToTriggers(true)
            .build();
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookConfirmationHelper)
        .handleTriggerWebhookConfirmationEvent(any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(triggerEventHistoryRepository, times(1)).upsert(any(), any());
    verify(ngTriggerService, times(1)).deleteTriggerWebhookEvent(any());
    verifyNoMoreInteractions(triggerEventHistoryRepository);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testHandleUpsertMethod() {
    TriggerWebhookEvent event =
        TriggerWebhookEvent.builder().accountId("accountId").isSubscriptionConfirmation(false).build();
    WebhookEventProcessingResult webhookEventProcessingResult =
        WebhookEventProcessingResult.builder()
            .mappedToTriggers(true)
            .responses(List.of(TriggerEventResponse.builder()
                                   .finalStatus(QUEUED)
                                   .accountId("accountId")
                                   .orgIdentifier("orgId")
                                   .projectIdentifier("projId")
                                   .targetIdentifier("targetId")
                                   .eventCorrelationId("eventCorr")
                                   .payload("payload")
                                   .createdAt(123L)
                                   .message("Execution has been queued.")
                                   .build()))
            .build();
    doNothing().when(ngTriggerService).deleteTriggerWebhookEvent(event);
    doReturn(webhookEventProcessingResult)
        .when(ngTriggerWebhookExecutionHelper)
        .handleTriggerWebhookEvent(any(), any());
    triggerWebhookExecutionServiceImpl.handle(event);
    verify(triggerEventHistoryRepository, times(1)).upsert(any(), any());
    verify(ngTriggerService, times(1)).updateTriggerWebhookEvent(any());
    verify(ngTriggerService, times(1)).deleteTriggerWebhookEvent(any());
    verifyNoMoreInteractions(ngTriggerService);
  }
}
