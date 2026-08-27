/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.gitlab.action;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public enum GitlabPRAction implements GitAction {
  @JsonProperty(YamlSimplConstants.OPEN_EVENT_TYPE) OPEN("open", YamlSimplConstants.OPEN_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.CLOSE_EVENT_TYPE) CLOSE("close", YamlSimplConstants.CLOSE_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.REOPEN_EVENT_TYPE) REOPEN("reopen", YamlSimplConstants.REOPEN_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.MERGE_EVENT_TYPE) MERGE("merge", YamlSimplConstants.MERGE_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.UPDATE_EVENT_TYPE) UPDATE("update", YamlSimplConstants.UPDATE_EVENT_TYPE),
  @JsonProperty(YamlSimplConstants.SYNC_EVENT_TYPE) SYNC("sync", YamlSimplConstants.SYNC_EVENT_TYPE);

  private String value;
  private String parsedValue;

  GitlabPRAction(String parsedValue, String value) {
    this.parsedValue = parsedValue;
    this.value = value;
  }

  public String getParsedValue() {
    return parsedValue;
  }

  public String getValue() {
    return value;
  }
}
