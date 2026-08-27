/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event;

import static io.harness.annotations.dev.HarnessTeam.HAR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction;

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
@OwnedBy(HAR)
public class HarArtifactEventSpec implements HarnessArtifactRegistryEventSpec {
  String registryName;
  List<HarArtifactAction> actions;
  List<TriggerEventDataCondition> headerConditions;
  List<TriggerEventDataCondition> payloadConditions;
  String jexlCondition;
  boolean autoAbortPreviousExecutions;

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

  @Override
  public boolean fetchAutoAbortPreviousExecutions() {
    return autoAbortPreviousExecutions;
  }
}
