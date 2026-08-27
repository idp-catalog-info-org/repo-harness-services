/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NamedContext {
  private String name;
  private ContextSpec context;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ContextSpec {
    private String cluster;
    private String user;
    private String namespace;
  }
}
