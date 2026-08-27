/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.annotations.dev.HarnessTeam.HAR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactWebhookEvent;
import io.harness.beans.ParsedRegistryWebhook;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.WebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.filter.TriggerFilterStore;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HAR)
public class HarnessRegistryWebhookEventToTriggerMapper implements WebhookEventToTriggerMapper {
  private final TriggerFilterStore triggerFilterStore;
  private final TriggerMapperHelper triggerMapperHelper;

  private final Gson gson = new Gson();

  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    ParsedRegistryWebhook parsedRegistryWebhook = parseRegistry(mappingRequestData.getWebhookDTO().getJsonPayload());
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .originalEvent(mappingRequestData.getTriggerWebhookEvent())
            .registryWebhook(parsedRegistryWebhook)
            .webhookEvent(ArtifactWebhookEvent.builder()
                              .name(parsedRegistryWebhook.getArtifactInfo().getName())
                              .version(parsedRegistryWebhook.getArtifactInfo().getVersion())
                              .build())
            .build();

    // Generate list of all filters to be applied
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(webhookPayloadData.getOriginalEvent().getAccountId())
                                              .webhookPayloadData(webhookPayloadData)
                                              .isCustomTrigger(false)
                                              .build();
    List<TriggerFilter> triggerFilters = triggerFilterStore.getWebhookTriggerFilters(webhookPayloadData);

    return triggerMapperHelper.applyFilters(triggerFilters, filterRequestData);
  }

  public ParsedRegistryWebhook parseRegistry(String payload) {
    try {
      return gson.fromJson(payload, ParsedRegistryWebhook.class);
    } catch (JsonSyntaxException e) {
      log.error("Failed to parse Harness Artifact Registry webhook payload", e);
      return null;
    }
  }
}
