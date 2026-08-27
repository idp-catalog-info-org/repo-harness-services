/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.WebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.filter.TriggerFilterStore;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class SystemEventToTriggerMapper implements WebhookEventToTriggerMapper {
  private final TriggerFilterStore triggerFilterStore;
  private final TriggerMapperHelper triggerMapperHelper;

  @Override
  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                .originalEvent(mappingRequestData.getTriggerWebhookEvent())
                                                .parseWebhookResponse(mappingRequestData.getWebhookDTO() != null
                                                        ? mappingRequestData.getWebhookDTO().getParsedResponse()
                                                        : null)
                                                .build();

    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(webhookPayloadData.getOriginalEvent().getAccountId())
                                              .webhookPayloadData(webhookPayloadData)
                                              .build();

    List<TriggerFilter> triggerFilters =
        triggerFilterStore.getWebhookTriggerFilters(filterRequestData.getWebhookPayloadData());

    return triggerMapperHelper.applyFilters(triggerFilters, filterRequestData);
  }
}
