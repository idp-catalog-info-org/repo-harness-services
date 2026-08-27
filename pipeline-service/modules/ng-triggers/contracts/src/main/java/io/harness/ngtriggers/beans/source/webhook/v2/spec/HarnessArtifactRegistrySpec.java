/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.spec;

import static io.harness.annotations.dev.HarnessTeam.HAR;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarnessArtifactRegistryEventSpec;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HAR)
public class HarnessArtifactRegistrySpec implements WebhookTriggerSpecV2 {
  HarTriggerEvent type;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  HarnessArtifactRegistryEventSpec spec;

  @Builder
  public HarnessArtifactRegistrySpec(HarTriggerEvent type, HarnessArtifactRegistryEventSpec spec) {
    this.type = type;
    this.spec = spec;
  }

  @Override
  public GitAware fetchGitAware() {
    return null;
  }

  @Override
  public PayloadAware fetchPayloadAware() {
    return spec;
  }

  public boolean fetchAutoAbortPreviousExecutions() {
    return spec.fetchAutoAbortPreviousExecutions();
  }
}
