/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event;

import static io.harness.annotations.dev.HarnessTeam.HAR;
import static io.harness.ngtriggers.Constants.HAR_ARTIFACT_TRIGGER_TYPE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HAR)
public enum HarTriggerEvent {
  @JsonProperty(HAR_ARTIFACT_TRIGGER_TYPE) ARTIFACT(HAR_ARTIFACT_TRIGGER_TYPE);

  private String value;

  HarTriggerEvent(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
