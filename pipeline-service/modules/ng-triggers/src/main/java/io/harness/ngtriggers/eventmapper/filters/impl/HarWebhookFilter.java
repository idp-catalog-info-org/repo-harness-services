/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.HAR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.HARNESS_ARTIFACT_REGISTRY_WEBHOOK_NOT_EXECUTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_TRIGGERS_FOUND_FOR_HARNESS_ARTIFACT_REGISTRY_WEBHOOK;
import static io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction.CREATION;
import static io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction.DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ParsedRegistryWebhook;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HAR)
public class HarWebhookFilter implements TriggerFilter {
  private final PmsFeatureFlagService featureFlagService;
  private final NGTriggerService ngTriggerService;

  @Inject
  public HarWebhookFilter(PmsFeatureFlagService featureFlagService, NGTriggerService ngTriggerService) {
    this.featureFlagService = featureFlagService;
    this.ngTriggerService = ngTriggerService;
  }

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);

    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    TriggerWebhookEvent originalEvent = webhookPayloadData.getOriginalEvent();
    ParsedRegistryWebhook registryWebhook = webhookPayloadData.getRegistryWebhook();
    List<TriggerDetails> eligibleTriggers = new ArrayList<>();

    if (featureFlagService.isEnabled(originalEvent.getAccountId(), FeatureName.HAR_TRIGGERS)) {
      String action;
      try {
        action = mapToAction(registryWebhook.getTrigger()).name();
      } catch (IllegalArgumentException e) {
        String errorMsg = String.format("Invalid trigger action: %s, SourceRepoType: %s", registryWebhook.getTrigger(),
            originalEvent.getSourceRepoType());
        log.info(errorMsg);
        mappingResponseBuilder.failedToFindTrigger(true).webhookEventResponse(TriggerEventResponseHelper.toResponse(
            INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION, originalEvent, null, null, errorMsg, null));
        return mappingResponseBuilder.build();
      }

      List<NGTriggerEntity> triggersMatchingCriteria =
          ngTriggerService.findTriggersForHarnessArtifactRegistryByAccountIdAndRegistry(
              originalEvent.getAccountId(), registryWebhook.getRegistry().getName(), action);
      if (isEmpty(triggersMatchingCriteria)) {
        String errorMsg = String.format("No triggers found for: %s, accountId: %s, registry: %s, action: %s",
            originalEvent.getSourceRepoType(), originalEvent.getAccountId(), registryWebhook.getRegistry().getName(),
            action);
        log.info(errorMsg);
        mappingResponseBuilder.failedToFindTrigger(true).webhookEventResponse(TriggerEventResponseHelper.toResponse(
            NO_TRIGGERS_FOUND_FOR_HARNESS_ARTIFACT_REGISTRY_WEBHOOK, originalEvent, null, null, errorMsg, null));
        return mappingResponseBuilder.build();
      } else {
        eligibleTriggers.addAll(triggersMatchingCriteria.stream()
                                    .map(entity -> TriggerDetails.builder().ngTriggerEntity(entity).build())
                                    .toList());
        addDetails(mappingResponseBuilder, filterRequestData, eligibleTriggers);
        return mappingResponseBuilder.build();
      }
    }
    String errorMsg =
        String.format("Webhook: %s, accountId: %s, registry: %s, not executed. HAR_TRIGGERS FF not enabled.",
            originalEvent.getSourceRepoType(), originalEvent.getAccountId(), registryWebhook.getRegistry().getName());
    log.info(errorMsg);
    mappingResponseBuilder.failedToFindTrigger(true).webhookEventResponse(TriggerEventResponseHelper.toResponse(
        HARNESS_ARTIFACT_REGISTRY_WEBHOOK_NOT_EXECUTED, originalEvent, null, null, errorMsg, null));

    return mappingResponseBuilder.build();
  }

  private HarArtifactAction mapToAction(String trigger) {
    return switch (trigger) {
      case "artifact_created" -> CREATION;
      case "artifact_deleted" -> DELETION;
        default -> throw new IllegalArgumentException("Unexpected value for HAR trigger action: " + trigger);
    };
  }
}
