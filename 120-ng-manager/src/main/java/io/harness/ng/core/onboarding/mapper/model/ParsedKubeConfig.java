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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Thin local model of a kubeconfig (~/.kube/config) used only for reverse-engineering connector hints.
 * Unknown fields are ignored so that provider-specific extras (gcp cmd-path, exec installHint, etc.) do not break
 * parsing.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedKubeConfig {
  private String apiVersion;
  private String kind;
  @JsonProperty("current-context") private String currentContext;
  private List<NamedCluster> clusters;
  private List<NamedUser> users;
  private List<NamedContext> contexts;
}
