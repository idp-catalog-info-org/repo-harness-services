/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_EVENT_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.WebhookTriggerSpecV2;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class EventActionTriggerFilter implements TriggerFilter {
  private NGTriggerElementMapper ngTriggerElementMapper;
  private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public TriggerEventResponse getFailureResponse(FilterRequestData filterRequestData) {
    return TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_EVENT_ACTION,
        filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
        "No Trigger matched conditions for payload event for Account: " + filterRequestData.getAccountId(), null);
  }

  @Override
  public List<TriggerDetails> applyFilterV2(
      List<TriggerDetails> triggerDetailsList, FilterRequestData filterRequestData) {
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = triggerDetailsList.stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);

    } else {
      parentUniqueIdToScopeInfoMap = null;
    }

    triggerDetailsList.forEach(trigger -> {
      try {
        NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
        ScopeInfo scopeInfo = isParentIdQueryingEnabled
            ? parentUniqueIdToScopeInfoMap
                  .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                  .orElse(null)
            : null;
        if (ngTriggerConfig == null) {
          ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(
              trigger.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
        }

        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerConfigV2(ngTriggerConfig)
                                            .ngTriggerEntity(trigger.getNgTriggerEntity())
                                            .build();
        if (checkTriggerEligibility(filterRequestData, triggerDetails)) {
          matchedTriggers.add(triggerDetails);
        }
      } catch (Exception e) {
        log.warn(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    });
    return matchedTriggers;
  }

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = filterRequestData.getDetails()
                                         .stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);
    }
    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      try {
        NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
        ScopeInfo scopeInfo = isParentIdQueryingEnabled
            ? parentUniqueIdToScopeInfoMap
                  .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                  .orElse(null)
            : null;
        if (ngTriggerConfig == null) {
          ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(
              trigger.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
        }

        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerConfigV2(ngTriggerConfig)
                                            .ngTriggerEntity(trigger.getNgTriggerEntity())
                                            .build();
        if (checkTriggerEligibility(filterRequestData, triggerDetails)) {
          matchedTriggers.add(triggerDetails);
        }
      } catch (Exception e) {
        log.warn(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    }

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched payload after condition evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_EVENT_ACTION,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No Trigger matched conditions for payload event for Account: " + filterRequestData.getAccountId(), null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails) {
    try {
      NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
      if (!WebhookTriggerConfigV2.class.isAssignableFrom(spec.getClass())) {
        log.error("Trigger spec is not a WebhookTriggerConfig");
        return false;
      }

      WebhookTriggerSpecV2 triggerSpec = ((WebhookTriggerConfigV2) spec).getSpec();
      return WebhookTriggerFilterUtils.evaluateEventAndActionFilters(
          filterRequestData.getWebhookPayloadData(), triggerSpec);
    } catch (Exception e) {
      NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
      log.error("Failed while evaluating Trigger: " + ngTriggerEntity.getIdentifier()
          + ", For Account: " + filterRequestData.getAccountId() + ", correlationId for event is: "
          + filterRequestData.getWebhookPayloadData().getOriginalEvent().getUuid());
      return false;
    }
  }
}
