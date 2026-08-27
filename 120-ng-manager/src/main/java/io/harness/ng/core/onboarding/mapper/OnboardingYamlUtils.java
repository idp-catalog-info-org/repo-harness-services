/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/**
 * Shared serialization helpers for the onboarding builders.
 *
 * <p>The onboarding builders assemble bean graphs (service, infrastructure) that set only the fields the caller
 * actually supplied, leaving every other {@code ParameterField} unset. Those beans are serialized through the shared
 * {@link YamlPipelineUtils} mapper, which is configured with {@code NON_NULL} inclusion (its earlier {@code NON_EMPTY}
 * call is superseded). Because a {@code ParameterField} is serialized as its wrapped value, an unset field (e.g.
 * {@code tagRegex}, {@code digest}, {@code folderPath}, {@code repoName}, {@code valuesPaths}, {@code provisioner})
 * is written as an explicit {@code field: null} rather than being omitted, and that null then surfaces verbatim in the
 * persisted entity YAML.
 *
 * <p>{@link #toPrunedYaml(Object)} serializes and then strips every {@code null}-valued key, so the persisted YAML is
 * limited to the fields the onboarding request supplied. This is deliberately scoped to the onboarding flow rather
 * than changing the shared {@link YamlPipelineUtils} mapper, which is used across the platform.
 */
@OwnedBy(HarnessTeam.CDC)
public final class OnboardingYamlUtils {
  private OnboardingYamlUtils() {}

  /** Serializes the bean graph to YAML with the shared mapper, then drops every key whose value is an explicit null. */
  public static String toPrunedYaml(Object bean) throws JsonProcessingException {
    JsonNode root = YamlPipelineUtils.readAsJsonNode(YamlPipelineUtils.getYamlString(bean));
    pruneNulls(root);
    return YamlPipelineUtils.getYamlString(root);
  }

  /** Recursively removes object entries whose value is a JSON null, and prunes nulls nested inside arrays. */
  private static void pruneNulls(JsonNode node) {
    if (node instanceof ObjectNode) {
      ObjectNode objectNode = (ObjectNode) node;
      Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        if (entry.getValue().isNull()) {
          fields.remove();
        } else {
          pruneNulls(entry.getValue());
        }
      }
    } else if (node != null && node.isArray()) {
      node.forEach(OnboardingYamlUtils::pruneNulls);
    }
  }
}
