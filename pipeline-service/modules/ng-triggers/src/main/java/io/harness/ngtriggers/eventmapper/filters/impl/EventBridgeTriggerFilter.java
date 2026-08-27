/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.EVENT_RELAY;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.EventBridgeTriggerSpec;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.product.ci.scm.proto.EventBridge;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class EventBridgeTriggerFilter implements TriggerFilter {
  private NGTriggerElementMapper ngTriggerElementMapper;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();

    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      try {
        NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
        if (ngTriggerConfig == null) {
          ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2WithoutYmlVersion(trigger.getNgTriggerEntity());
        }
        if (filterRequestData.getWebhookPayloadData().getParseWebhookResponse().hasEventBridge()
            && ((WebhookTriggerConfigV2) (ngTriggerConfig.getSource().getSpec())).getType() == EVENT_RELAY) {
          EventBridge eventBridge =
              filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getEventBridge();
          String webhookIdFromEvent = (NGCommonEntityConstants.PROJECT).equals(eventBridge.getScope())
              ? eventBridge.getWebhookId()
              : eventBridge.getScope() + "." + eventBridge.getWebhookId();
          String webhookIdInTrigger =
              ((EventBridgeTriggerSpec) (((WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec()).getSpec()))
                  .getWebhookIdentifier();
          if (webhookIdInTrigger.equals(webhookIdFromEvent)) {
            TriggerDetails triggerDetails = TriggerDetails.builder()
                                                .ngTriggerConfigV2(ngTriggerConfig)
                                                .ngTriggerEntity(trigger.getNgTriggerEntity())
                                                .build();
            matchedTriggers.add(triggerDetails);
          }
        }
      } catch (Exception e) {
        log.warn(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    }

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched payload after condition evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No Trigger matched conditions for payload event for Account: " + filterRequestData.getAccountId(), null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }
}