/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.harness.action;

import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum HarnessTagAction implements GitAction {
  @JsonProperty("Create") CREATE("create", "Create"),
  @JsonProperty("Delete") DELETE("delete", "Delete");

  private String value;
  private String parsedValue;

  HarnessTagAction(String parsedValue, String value) {
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
