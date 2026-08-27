/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.triggers.webhook.service;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookEventPublisher;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookEventExecutionServiceImpl;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PipelineEventHook;
import io.harness.product.ci.scm.proto.SystemEventHook;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class TriggerWebhookEventExecutionServiceImplTest extends CategoryTest {
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock NGTriggerRepository ngTriggerRepository;
  @Mock TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Mock TriggerExecutionHelper triggerExecutionHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock TriggerWebhookEventPublisher triggerWebhookEventPublisher;
  @InjectMocks @Spy TriggerWebhookEventExecutionServiceImpl triggerWebhookEventExecutionService;

  private static final String ACCOUNT_ID = "accId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projId";
  private static final String SOURCE_PIPELINE = "upstreamPipeline";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessEvent() {
    TriggerExecutionDTO triggerExecutionDTO =
        TriggerExecutionDTO.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .setOrgIdentifier(ORG_ID)
            .setProjectIdentifier(PROJECT_ID)
            .setTriggerIdentifier("triggerId")
            .setTargetIdentifier("pipId")
            .setWebhookDto(WebhookDTO.newBuilder()
                               .addHeaders(EventHeader.newBuilder().setKey("key1").addValues("value1").build())
                               .build())
            .build();
    when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doReturn(TriggerWebhookEvent.builder())
        .when(ngTriggerElementMapper)
        .toNGTriggerWebhookEvent(anyString(), any(), any(), anyString(), any(), any());
    doReturn(Optional.of(NGTriggerEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_ID)
                             .projectIdentifier(PROJECT_ID)
                             .targetIdentifier("targetId")
                             .identifier("triggerId")
                             .build()))
        .when(ngTriggerRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
            anyString(), anyString(), anyString(), anyString(), anyString());
    doReturn(NGTriggerConfigV2.builder().build())
        .when(ngTriggerElementMapper)
        .toTriggerConfigV2((NGTriggerEntity) any(), any(), anyBoolean());
    doReturn(NGTriggerEntity.builder().build()).when(ngTriggerRepository).updateValidationStatus(any(), any());
    doNothing()
        .when(ngTriggerWebhookExecutionHelper)
        .updateWebhookRegistrationStatusAndTriggerPipelineExecution(
            any(), any(), any(), any(), any(), any(), anyBoolean());
    triggerWebhookEventExecutionService.processEvent(triggerExecutionDTO);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testProcessEvent_systemEventDto_flowsThroughProcessEvent() {
    TriggerExecutionDTO dto = buildSystemEventDto(SystemEventType.PIPELINE_SUCCESS.eventTypeString(), SOURCE_PIPELINE);

    when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doReturn(TriggerWebhookEvent.builder())
        .when(ngTriggerElementMapper)
        .toNGTriggerWebhookEvent(anyString(), any(), any(), anyString(), any(), any());
    doReturn(Optional.empty())
        .when(ngTriggerRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
            anyString(), anyString(), anyString(), anyString(), anyString());

    triggerWebhookEventExecutionService.processEvent(dto);

    verify(ngTriggerElementMapper).toNGTriggerWebhookEvent(anyString(), any(), any(), anyString(), any(), any());
    verify(triggerExecutionHelper, never())
        .resolveRuntimeInputAndSubmitExecutionRequest(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private TriggerExecutionDTO buildSystemEventDto(String eventType, String sourcePipeline) {
    return TriggerExecutionDTO.newBuilder()
        .setAccountId(ACCOUNT_ID)
        .setOrgIdentifier(ORG_ID)
        .setProjectIdentifier(PROJECT_ID)
        .setTriggerIdentifier("t1")
        .setTargetIdentifier("targetPipeline")
        .setWebhookDto(
            WebhookDTO.newBuilder()
                .setWebhookTriggerType(WebhookTriggerType.SYSTEM_EVENTS)
                .setJsonPayload(String.format("{\"eventType\":\"%s\"}", eventType))
                .setParsedResponse(
                    ParseWebhookResponse.newBuilder()
                        .setSystemEvent(SystemEventHook.newBuilder()
                                            .setPipelineEvent(PipelineEventHook.newBuilder()
                                                                  .setAccountId(ACCOUNT_ID)
                                                                  .setOrgIdentifier(ORG_ID)
                                                                  .setProjectIdentifier(PROJECT_ID)
                                                                  .setSourcePipelineIdentifier(sourcePipeline)
                                                                  .setEventType(eventType)
                                                                  .build())
                                            .build())
                        .build())
                .setEventId("corrId")
                .build())
        .build();
  }
}
