/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public class PollingResponseHandler extends PmsBaseEventHandler<PollingResponse> {
  @Inject private BuildTriggerEventMapper mapper;
  @Inject private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject private TriggerFailureNotificationHelper triggerFailureNotificationHelper;
  @Inject private PmsFeatureFlagHelper featureFlagHelper;
  private static final String EVENT_TYPE = "polling_response_event";

  @Override
  protected Map<String, String> extraLogProperties(PollingResponse event) {
    return ImmutableMap.<String, String>builder()
        .put("eventType", EVENT_TYPE)
        .put("pollingDocId", event.getPollingDocId())
        .put("accountId", event.getAccountId())
        .build();
  }

  @Override
  protected Ambiance extractAmbiance(PollingResponse event) {
    return Ambiance.newBuilder().putSetupAbstractions("accountId", event.getAccountId()).build();
  }

  @Override
  protected String getEventType(PollingResponse message) {
    return EVENT_TYPE;
  }

  @Override
  public void handleEventWithContext(PollingResponse response) {
    SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
    TriggerNotificationDataBuilder triggerNotificationDataBuilder = TriggerNotificationData.builder();
    if (response == null) {
      return;
    }

    try (AccountLogContext ignore1 = new AccountLogContext(response.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new NgPollingAutoLogContext(response.getPollingDocId(), OVERRIDE_ERROR)) {
      WebhookEventMappingResponse webhookEventMappingResponse = mapper.consumeBuildTriggerEvent(response);
      if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
        List<TriggerEventResponse> responses = triggerEventExecutionHelper.processTriggersForActivation(
            webhookEventMappingResponse.getTriggers(), response, triggerNotificationDataBuilder);
        if (isNotEmpty(responses)) {
          // TODO: This can be converted to a saveAll call rather
          responses.forEach(resp -> {
            TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(resp);
            triggerFailureNotificationHelper.sendTriggerNotification(
                triggerEventHistory, resp.getFinalStatus(), triggerNotificationDataBuilder);
            triggerEventExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
            triggerEventHistoryRepository.save(triggerEventHistory);
          });
        }
      }
    }
  }
}
