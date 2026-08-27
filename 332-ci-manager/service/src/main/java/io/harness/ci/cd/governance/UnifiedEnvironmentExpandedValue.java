/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ITEMS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SEQUENTIAL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.cd.governance.UnifiedInfrastructureExpandedValue.UnifiedInfrastructureExpandedValueKeys;
import io.harness.ci.cd.governance.UnifiedSingleEnvironmentExpandedValue.UnifiedSingleEnvironmentExpandedValueKeys;
import io.harness.common.utils.CdStepsInputsMergeUtility;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.sdk.core.governance.handler.ExpandedValue;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

@OwnedBy(CI)
@Data
@Builder
public class UnifiedEnvironmentExpandedValue implements ExpandedValue {
  private Boolean sequential;
  private Boolean isMultiEnv;
  private Boolean isEnvGroup;
  private UnifiedSingleEnvironmentExpandedValue environment;
  private List<UnifiedSingleEnvironmentExpandedValue> environments;
  private UnifiedEnvGroupExpandedValue environmentGroup;

  @Override
  public String getKey() {
    return YAMLFieldNameConstants.ENVIRONMENT;
  }

  @SneakyThrows
  @Override
  public String toJson() {
    if (isMultiEnv) {
      return processMultiEnvironments();
    }
    return processSingleEnvironment();
  }

  @SneakyThrows
  private String processSingleEnvironment() {
    String json = JsonPipelineUtils.writeJsonString(environment);
    YamlConfig yamlConfig = new YamlConfig(json);
    JsonNode envExpandedNode = yamlConfig.getYamlMap();
    moveInfraNodesToParentLevel(envExpandedNode);
    return envExpandedNode.toPrettyString();
  }

  @SneakyThrows
  private String processMultiEnvironments() {
    Map<String, Object> environmentsAndMetadataMap = getEnvironmentsAndMetadataMap();
    String json = JsonPipelineUtils.writeJsonString(environmentsAndMetadataMap);
    YamlConfig yamlConfig = new YamlConfig(json);
    JsonNode multiEnvExpandedNode = yamlConfig.getYamlMap();
    JsonNode environmentNodes = getEnvironmentNodes(multiEnvExpandedNode);
    environmentNodes.forEach(environmentNode -> {
      JsonNode infraNodes = environmentNode.get(UnifiedSingleEnvironmentExpandedValueKeys.infrastructure);
      if (infraNodes != null) {
        moveInfraNodesToParentLevel(infraNodes);
      }
    });
    return multiEnvExpandedNode.toPrettyString();
  }

  private JsonNode getEnvironmentNodes(JsonNode envExpandedNode) {
    if (isEnvGroup) {
      return envExpandedNode.get(GROUP).get(ITEMS);
    }
    return envExpandedNode.get(ITEMS);
  }

  private Map<String, Object> getEnvironmentsAndMetadataMap() {
    Map<String, Object> environmentsAndMetadataMap = new HashMap<>();
    environmentsAndMetadataMap.put(SEQUENTIAL, Boolean.TRUE.equals(sequential));
    if (isEnvGroup) {
      environmentsAndMetadataMap.put(GROUP, environmentGroup);
    } else {
      environmentsAndMetadataMap.put(ITEMS, environments);
    }
    return environmentsAndMetadataMap;
  }

  private void moveInfraNodesToParentLevel(JsonNode infraNodes) {
    if (infraNodes.isArray()) {
      infraNodes.forEach(infraNode
          -> CdStepsInputsMergeUtility.moveFieldsToParentLevel(
              (ObjectNode) infraNode, UnifiedInfrastructureExpandedValueKeys.infraNode));
    }
  }
}
