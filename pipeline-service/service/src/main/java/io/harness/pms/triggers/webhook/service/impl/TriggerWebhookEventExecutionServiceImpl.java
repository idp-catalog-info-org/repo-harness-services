/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.tracing.TracingUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerWebhookEventExecutionServiceImpl
    extends PmsBaseEventHandler<TriggerExecutionDTO> implements TriggerWebhookEventExecutionService {
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject NGTriggerRepository ngTriggerRepository;
  @Inject private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Inject private TriggerFailureNotificationHelper triggerFailureNotificationHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  public static final Tracer tracer = GlobalOpenTelemetry.getTracer(
      "io.harness.pms.triggers.webhook.service.impl.TriggerWebhookEventExecutionServiceImpl");
  private static final String EVENT_TYPE = "trigger_execution_event";

  @Override
  public void processEvent(TriggerExecutionDTO triggerExecutionDTO) {
    try {
      TriggerNotificationDataBuilder triggerNotificationDataBuilder = TriggerNotificationData.builder();
      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? scopeResolutionHelper.getScopeInfo(
                triggerExecutionDTO.getAccountId(), triggerExecutionDTO.getParentUniqueId())
          : null;
      TriggerWebhookEvent triggerWebhookEvent =
          ngTriggerElementMapper
              .toNGTriggerWebhookEvent(triggerExecutionDTO.getWebhookDto().getAccountId(), null, null,
                  triggerExecutionDTO.getWebhookDto().getJsonPayload(),
                  ngTriggerWebhookExecutionHelper.prepareHeaders(triggerExecutionDTO.getWebhookDto()), null)
              .uuid(triggerExecutionDTO.getWebhookDto().getEventId())
              .createdAt(triggerExecutionDTO.getWebhookDto().getTime())
              .build();
      Optional<NGTriggerEntity> optionalNGTriggerEntity = isParentIdQueryingEnabled
          ? ngTriggerRepository.findByParentUniqueIdAndTargetIdentifierAndIdentifier(
                triggerExecutionDTO.getParentUniqueId(), triggerExecutionDTO.getTargetIdentifier(),
                triggerExecutionDTO.getTriggerIdentifier())
          : ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
                triggerExecutionDTO.getAccountId(), triggerExecutionDTO.getOrgIdentifier(),
                triggerExecutionDTO.getProjectIdentifier(), triggerExecutionDTO.getTargetIdentifier(),
                triggerExecutionDTO.getTriggerIdentifier());
      List<TriggerEventResponse> eventResponses = new ArrayList<>();
      if (optionalNGTriggerEntity.isPresent()) {
        try (TracingUtils.TracingContext tracingContext =
                 TriggerExecutionHelper.generateTraceIdAndStartSpan(tracer, optionalNGTriggerEntity.get())) {
          NGTriggerEntity ngTriggerEntity = optionalNGTriggerEntity.get();
          NGTriggerConfigV2 ngTriggerConfigV2 =
              ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
          TriggerDetails triggerDetails = TriggerDetails.builder()
                                              .ngTriggerEntity(ngTriggerEntity)
                                              .ngTriggerConfigV2(ngTriggerConfigV2)
                                              .authenticated(triggerExecutionDTO.getAuthenticated())
                                              .build();
          ngTriggerWebhookExecutionHelper.updateWebhookRegistrationStatusAndTriggerPipelineExecution(
              triggerExecutionDTO.getWebhookDto().getParsedResponse(), triggerWebhookEvent, eventResponses,
              triggerDetails, new HashSet<>(triggerExecutionDTO.getChangedFilesList()), triggerNotificationDataBuilder,
              isParentIdQueryingEnabled);
        }
      }

      if (isNotEmpty(eventResponses)) {
        eventResponses = eventResponses.stream().filter(Objects::nonNull).collect(toList());
      }

      saveTriggerExecutionHistoryRecords(eventResponses, triggerNotificationDataBuilder);
    } catch (Exception e) {
      log.error(
          "Exception while processing Trigger for webhook event with trigger identifier {} and pipeline identifier {}",
          triggerExecutionDTO.getTriggerIdentifier(), triggerExecutionDTO.getTargetIdentifier(), e);
    }
  }

  private void saveTriggerExecutionHistoryRecords(
      List<TriggerEventResponse> responseList, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    responseList.forEach(response -> {
      try {
        TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(response);
        triggerFailureNotificationHelper.sendTriggerNotification(
            triggerEventHistory, response.getFinalStatus(), triggerNotificationDataBuilder);
        ngTriggerWebhookExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
        triggerEventHistoryRepository.save(triggerEventHistory);
      } catch (Exception e) {
        log.error("Failed to generate and save TriggerExecutionHistoryRecord: " + response);
      }
    });
  }

  @Override
  protected Map<String, String> extraLogProperties(TriggerExecutionDTO event) {
    return ImmutableMap.<String, String>builder()
        .put("eventType", EVENT_TYPE)
        .put("eventId", event.getWebhookDto().getEventId())
        .put("accountId", event.getAccountId())
        .put("pipelineIdentifier", event.getTargetIdentifier())
        .put("triggerIdentifier", event.getTriggerIdentifier())
        .build();
  }

  @Override
  protected Ambiance extractAmbiance(TriggerExecutionDTO event) {
    return Ambiance.newBuilder().putSetupAbstractions("accountId", event.getAccountId()).build();
  }

  @Override
  protected String getEventType(TriggerExecutionDTO message) {
    return EVENT_TYPE;
  }

  @Override
  public void handleEventWithContext(TriggerExecutionDTO triggerExecutionDTO) {
    SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
    if (triggerExecutionDTO == null) {
      return;
    }

    try {
      processEvent(triggerExecutionDTO);
    } catch (Exception e) {
      throw new InvalidRequestException("Exception while processing TriggerExecutionDto event", e);
    }
  }
}
