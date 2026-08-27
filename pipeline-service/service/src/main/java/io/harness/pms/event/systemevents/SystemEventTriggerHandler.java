/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.event.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.webhookpayloads.webhookdata.PipelineSystemEvent;
import io.harness.eventsframework.webhookpayloads.webhookdata.SystemEventEnvelope;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventPayload;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PipelineEventHook;
import io.harness.product.ci.scm.proto.SystemEventHook;
import io.harness.serializer.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class SystemEventTriggerHandler implements PmsCommonsBaseEventHandler<SystemEventEnvelope> {
  private final TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2;

  @Inject
  public SystemEventTriggerHandler(TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2) {
    this.triggerWebhookExecutionServiceV2 = triggerWebhookExecutionServiceV2;
  }

  @Override
  public void handleEvent(
      SystemEventEnvelope envelope, Map<String, String> metadataMap, Map<String, Object> metricInfo) {
    if (!envelope.hasPipelineSystemEvent()) {
      log.warn("Received system event envelope without pipelineSystemEvent");
      return;
    }
    WebhookDTO webhookDTO = toWebhookDTO(envelope.getPipelineSystemEvent());
    triggerWebhookExecutionServiceV2.handleEvent(webhookDTO, metadataMap, metricInfo);
  }

  private WebhookDTO toWebhookDTO(PipelineSystemEvent event) {
    PipelineEventHook pipelineEventHook = PipelineEventHook.newBuilder()
                                              .setAccountId(event.getAccountId())
                                              .setOrgIdentifier(event.getOrgIdentifier())
                                              .setProjectIdentifier(event.getProjectIdentifier())
                                              .setSourcePipelineIdentifier(event.getSourcePipelineIdentifier())
                                              .setEventType(event.getEventType())
                                              .setPlanExecutionId(event.getPlanExecutionId())
                                              .build();
    ParseWebhookResponse parsedResponse =
        ParseWebhookResponse.newBuilder()
            .setSystemEvent(SystemEventHook.newBuilder().setPipelineEvent(pipelineEventHook).build())
            .build();
    String payload = JsonUtils.asJson(SystemEventPayload.builder()
                                          .eventType(event.getEventType())
                                          .sourcePipelineIdentifier(event.getSourcePipelineIdentifier())
                                          .planExecutionId(event.getPlanExecutionId())
                                          .build());
    return WebhookDTO.newBuilder()
        .setAccountId(event.getAccountId())
        .setWebhookTriggerType(WebhookTriggerType.SYSTEM_EVENTS)
        .setEventId(generateUuid())
        .setJsonPayload(payload)
        .setParsedResponse(parsedResponse)
        .setTime(event.getTime())
        .build();
  }
}
