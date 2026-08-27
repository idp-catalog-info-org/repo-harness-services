/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.exceptions;

import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;

import lombok.Getter;

public class TriggerProcessingException extends RuntimeException {
  @Getter private final WebhookEventMappingResponse webhookEventMappingResponse;
  public TriggerProcessingException(
      TriggerFilter triggerFilterInAction, FilterRequestData filterRequestData, Exception cause) {
    this.webhookEventMappingResponse = triggerFilterInAction.getWebhookResponseForException(filterRequestData, cause);
  }
}
