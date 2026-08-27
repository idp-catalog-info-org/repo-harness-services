/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.ngtriggers.Constants.SYSTEM_EVENTS_WEBHOOK;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.CUSTOM;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.HARNESS_ARTIFACT_REGISTRY;

import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class WebhookEventMapperHelper {
  private final GitWebhookEventToTriggerMapper gitWebhookEventToTriggerMapper;
  private final CustomWebhookEventToTriggerMapper customWebhookEventToTriggerMapper;
  private final HarnessRegistryWebhookEventToTriggerMapper harnessRegistryWebhookEventToTriggerMapper;
  private final SystemEventToTriggerMapper systemEventToTriggerMapper;

  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();
    if (SYSTEM_EVENTS_WEBHOOK.equals(triggerWebhookEvent.getSourceRepoType())) {
      return systemEventToTriggerMapper.mapWebhookEventToTriggers(mappingRequestData);
    } else if (CUSTOM.name().equals(triggerWebhookEvent.getSourceRepoType())) {
      return customWebhookEventToTriggerMapper.mapWebhookEventToTriggers(mappingRequestData);
    } else if (HARNESS_ARTIFACT_REGISTRY.name().equals(triggerWebhookEvent.getSourceRepoType())) {
      return harnessRegistryWebhookEventToTriggerMapper.mapWebhookEventToTriggers(mappingRequestData);
    }

    return gitWebhookEventToTriggerMapper.mapWebhookEventToTriggers(mappingRequestData);
  }
}
