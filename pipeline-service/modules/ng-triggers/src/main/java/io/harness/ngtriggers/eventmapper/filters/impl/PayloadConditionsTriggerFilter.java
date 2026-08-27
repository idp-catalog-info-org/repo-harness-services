/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.utils.PmsFeatureFlagHelper;

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
public class PayloadConditionsTriggerFilter implements TriggerFilter {
  private NGTriggerElementMapper ngTriggerElementMapper;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public TriggerEventResponse getFailureResponse(FilterRequestData filterRequestData) {
    return TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS,
        filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
        "No Trigger matched conditions for payload event for Account: " + filterRequestData.getAccountId(), null);
  }

  @Override
  public List<TriggerDetails> applyFilterV2(
      List<TriggerDetails> triggerDetailsList, FilterRequestData filterRequestData) {
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        getTriggerExpressionEvaluator(filterRequestData.getWebhookPayloadData());

    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    triggerDetailsList.forEach(trigger -> {
      NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
      if (ngTriggerConfig == null) {
        ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2WithoutYmlVersion(trigger.getNgTriggerEntity());
      }

      TriggerDetails triggerDetails = TriggerDetails.builder()
                                          .ngTriggerConfigV2(ngTriggerConfig)
                                          .ngTriggerEntity(trigger.getNgTriggerEntity())
                                          .build();

      if (checkTriggerEligibility(filterRequestData, triggerDetails, triggerExpressionEvaluator)) {
        matchedTriggers.add(triggerDetails);
      }
    });
    return matchedTriggers;
  }

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        getTriggerExpressionEvaluator(filterRequestData.getWebhookPayloadData());

    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      try {
        NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
        if (ngTriggerConfig == null) {
          ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2WithoutYmlVersion(trigger.getNgTriggerEntity());
        }

        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerConfigV2(ngTriggerConfig)
                                            .ngTriggerEntity(trigger.getNgTriggerEntity())
                                            .build();
        if (checkTriggerEligibility(filterRequestData, triggerDetails, triggerExpressionEvaluator)) {
          matchedTriggers.add(triggerDetails);
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

  boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails,
      TriggerExpressionEvaluator triggerExpressionEvaluator) {
    NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
    if (!WebhookTriggerConfigV2.class.isAssignableFrom(spec.getClass())) {
      log.error("Trigger spec is not a WebhookTriggerConfig");
      return false;
    }

    WebhookTriggerConfigV2 webhookTriggerConfigV1 = (WebhookTriggerConfigV2) spec;
    return WebhookTriggerFilterUtils.checkIfPayloadConditionsMatch(filterRequestData.getWebhookPayloadData(),
        webhookTriggerConfigV1.getSpec(), triggerExpressionEvaluator, filterRequestData.getAccountId(),
        pmsFeatureFlagHelper);
  }

  private TriggerExpressionEvaluator getTriggerExpressionEvaluator(WebhookPayloadData webhookPayloadData) {
    return WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
  }
}
