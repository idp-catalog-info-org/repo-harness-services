/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.spec;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.v1.webhook.condition.Conditions;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Value
@OwnedBy(PIPELINE)
public class CustomTriggerSpec implements WebhookTriggerYamlSimplSpec, PayloadAware {
  Conditions conditions;

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
    return conditions != null ? conditions.getHeader() : null;
  }

  @Override
  public List<TriggerEventDataCondition> fetchPayloadConditions() {
    return conditions != null ? conditions.getPayload() : null;
  }

  @Override
  public String fetchJexlCondition() {
    return conditions != null ? conditions.getJexl() : null;
  }
}
