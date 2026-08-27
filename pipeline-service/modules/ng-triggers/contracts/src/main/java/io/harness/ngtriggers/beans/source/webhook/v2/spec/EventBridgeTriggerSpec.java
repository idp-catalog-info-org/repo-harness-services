/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.spec;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PIPELINE)
public class EventBridgeTriggerSpec implements WebhookTriggerSpecV2, PayloadAware {
  String webhookIdentifier;
  List<TriggerEventDataCondition> payloadConditions;
  List<TriggerEventDataCondition> headerConditions;
  String jexlCondition;

  @Override
  public GitAware fetchGitAware() {
    return null;
  }

  @Override
  public PayloadAware fetchPayloadAware() {
    return this;
  }

  @Override
  public List<TriggerEventDataCondition> fetchHeaderConditions() {
    return headerConditions;
  }

  @Override
  public List<TriggerEventDataCondition> fetchPayloadConditions() {
    return payloadConditions;
  }

  @Override
  public String fetchJexlCondition() {
    return jexlCondition;
  }
}