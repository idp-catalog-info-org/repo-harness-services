/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.source.systemevents.PipelineSystemEventSpec;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventPayload;
import io.harness.ngtriggers.beans.source.webhook.SystemEventTriggerConfig;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.serializer.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class SystemEventTriggerFilter implements TriggerFilter {
  private final NGTriggerElementMapper ngTriggerElementMapper;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder builder = initWebhookEventMappingResponse(filterRequestData);
    TriggerWebhookEvent triggerWebhookEvent = filterRequestData.getWebhookPayloadData().getOriginalEvent();

    SystemEventPayload systemEventPayload = null;
    try {
      systemEventPayload = JsonUtils.read(triggerWebhookEvent.getPayload(), SystemEventPayload.class);
    } catch (Exception e) {
      log.warn("Failed to parse system event payload: {}", triggerWebhookEvent.getPayload(), e);
    }
    String eventType = systemEventPayload != null ? systemEventPayload.getEventType() : null;
    String sourcePipelineIdentifier = extractSourcePipelineIdentifier(filterRequestData);

    List<TriggerDetails> matched = filterRequestData.getDetails()
                                       .stream()
                                       .filter(details -> {
                                         try {
                                           return matchesEvent(details, eventType, sourcePipelineIdentifier);
                                         } catch (Exception e) {
                                           log.warn("Failed to evaluate system event trigger [{}]",
                                               details.getNgTriggerEntity().getIdentifier(), e);
                                           return false;
                                         }
                                       })
                                       .collect(toList());

    if (isEmpty(matched)) {
      String msg = "No SYSTEM_EVENT trigger matched event type [" + eventType + "] in project "
          + triggerWebhookEvent.getProjectIdentifier();
      log.info(msg);
      builder.failedToFindTrigger(true).webhookEventResponse(TriggerEventResponseHelper.toResponse(
          NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO, triggerWebhookEvent, null, null, msg, null));
    } else {
      addDetails(builder, filterRequestData, matched);
    }

    return builder.build();
  }

  private String extractSourcePipelineIdentifier(FilterRequestData filterRequestData) {
    ParseWebhookResponse parsedResponse = filterRequestData.getWebhookPayloadData().getParseWebhookResponse();
    if (parsedResponse != null && parsedResponse.hasSystemEvent()) {
      return parsedResponse.getSystemEvent().getPipelineEvent().getSourcePipelineIdentifier();
    }
    return null;
  }

  private boolean matchesEvent(TriggerDetails details, String eventType, String sourcePipelineIdentifier) {
    NGTriggerConfigV2 config = ngTriggerElementMapper.toTriggerConfigV2(details.getNgTriggerEntity().getYaml());
    if (config.getSource() == null || config.getSource().getSpec() == null) {
      return false;
    }
    if (!(config.getSource().getSpec() instanceof SystemEventTriggerConfig)) {
      return false;
    }
    SystemEventTriggerConfig systemEventConfig = (SystemEventTriggerConfig) config.getSource().getSpec();
    if (!(systemEventConfig.getSpec() instanceof PipelineSystemEventSpec)) {
      return false;
    }
    PipelineSystemEventSpec pipelineSpec = (PipelineSystemEventSpec) systemEventConfig.getSpec();
    if (pipelineSpec.getEventType() == null || !pipelineSpec.getEventType().eventTypeString().equals(eventType)) {
      return false;
    }
    if (isNotEmpty(pipelineSpec.getPayloadConditions())) {
      return pipelineSpec.getPayloadConditions().stream().allMatch(condition
          -> !"sourcePipeline".equals(condition.getKey())
              || ConditionEvaluator.evaluate(
                  sourcePipelineIdentifier, condition.getValue(), condition.getOperator().getValue()));
    }
    return true;
  }
}
