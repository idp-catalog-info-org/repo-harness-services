/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.mapper.model.ParsedKubeConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses a raw kubeconfig (~/.kube/config) YAML string into the local {@link ParsedKubeConfig} model.
 * Uses jackson {@link YAMLMapper} (no new dependency); unknown fields are ignored by the model POJOs.
 */
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class KubeconfigParser {
  private static final ObjectMapper YAML_MAPPER = new YAMLMapper();
  private static final String CONFIG_KIND = "Config";

  public static ParsedKubeConfig parse(String content) {
    if (StringUtils.isBlank(content)) {
      throw new InvalidRequestException("Uploaded kubeConfig file is empty");
    }

    final ParsedKubeConfig kubeConfig;
    try {
      kubeConfig = YAML_MAPPER.readValue(content, ParsedKubeConfig.class);
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to parse the uploaded kubeConfig: not a valid kubeConfig YAML", e);
    }

    if (kubeConfig == null) {
      throw new InvalidRequestException("Failed to parse the uploaded kubeConfig: empty document");
    }

    // A kubeConfig declares kind: Config. Be lenient if omitted, but reject a clearly wrong kind.
    if (StringUtils.isNotBlank(kubeConfig.getKind()) && !CONFIG_KIND.equalsIgnoreCase(kubeConfig.getKind())) {
      throw new InvalidRequestException(
          String.format("Uploaded file is not a kubeConfig (kind=%s, expected Config)", kubeConfig.getKind()));
    }

    return kubeConfig;
  }
}
