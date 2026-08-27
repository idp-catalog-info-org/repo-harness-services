/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.event;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitEvent;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.CI)
public enum AzureRepoTriggerEvent implements GitEvent {
  @JsonProperty(YamlSimplConstants.PR_EVENT_TYPE) PULL_REQUEST(YamlSimplConstants.PR_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.PUSH_EVENT_TYPE) PUSH(YamlSimplConstants.PUSH_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.ISSUE_COMMENT_EVENT_TYPE) ISSUE_COMMENT(YamlSimplConstants.ISSUE_COMMENT_EVENT_TYPE);

  private final String value;

  AzureRepoTriggerEvent(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }
}
