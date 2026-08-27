/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.stages.dynamic.DynamicStageNode;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class DynamicStageVariableCreator extends AbstractStageVariableCreator<DynamicStageNode> {
  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.DYNAMIC_STAGE));
  }

  @Override
  public Class<DynamicStageNode> getFieldClass() {
    return DynamicStageNode.class;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    return null;
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, DynamicStageNode config) {
    VariableCreationResponse response = super.createVariablesForParentNodeV2(ctx, config);
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>(response.getYamlProperties());

    // Add Git store fields as output variables so they can be accessed by later stages
    YamlField specField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC);
    if (specField != null) {
      addGitStoreVariables(specField, yamlPropertiesMap);
    }

    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  private void addGitStoreVariables(YamlField specField, Map<String, YamlProperties> yamlPropertiesMap) {
    // Check for new structure: sourceConfig.spec.*
    YamlField sourceConfigField = specField.getNode().getField(YAMLFieldNameConstants.SOURCE_CONFIG);
    if (sourceConfigField != null) {
      // Verify that sourceConfig type is "Git" before processing Git-specific fields
      YamlField typeField = sourceConfigField.getNode().getField(YAMLFieldNameConstants.TYPE);
      if (typeField != null && "Git".equals(typeField.getNode().getCurrJsonNode().textValue())) {
        YamlField sourceConfigSpecField = sourceConfigField.getNode().getField(YAMLFieldNameConstants.SPEC);
        if (sourceConfigSpecField != null) {
          // New structure: sourceConfig.spec.connectorRef, sourceConfig.spec.branchName, etc.
          addVariableIfPresent(sourceConfigSpecField, yamlPropertiesMap, "connectorRef", "connectorRef");
          addVariableIfPresent(sourceConfigSpecField, yamlPropertiesMap, "branchName", "branch");
          addVariableIfPresent(sourceConfigSpecField, yamlPropertiesMap, "commitId", "commitId");
          addVariableIfPresent(sourceConfigSpecField, yamlPropertiesMap, "filePath", "filePath");
          addVariableIfPresent(sourceConfigSpecField, yamlPropertiesMap, "repoName", "repoName");
        }
      }
    }
  }

  private void addVariableIfPresent(
      YamlField parentField, Map<String, YamlProperties> yamlPropertiesMap, String fieldName, String outputName) {
    YamlField field = parentField.getNode().getField(fieldName);
    if (field != null) {
      addVariable(field, yamlPropertiesMap, fieldName, outputName);
    }
  }

  private void addVariable(
      YamlField field, Map<String, YamlProperties> yamlPropertiesMap, String actualFieldName, String outputName) {
    YamlNode node = field.getNode();
    String fqn = YamlUtils.getFullyQualifiedName(node);
    String localName = YamlUtils.getQualifiedNameTillGivenField(node, YAMLFieldNameConstants.STAGE);
    // Replace spec.actualFieldName with output.outputName for output access
    String outputFqn = fqn.replace(YAMLFieldNameConstants.SPEC + "." + actualFieldName, "output." + outputName);
    // Convert local name to stage format
    String[] split = localName.split("\\.");
    String stageLocalName = localName.replaceFirst(split[0], YAMLFieldNameConstants.STAGE);
    String outputLocalName =
        stageLocalName.replace(YAMLFieldNameConstants.SPEC + "." + actualFieldName, "output." + outputName);

    if (node.getCurrJsonNode() != null && node.getCurrJsonNode().isValueNode()) {
      yamlPropertiesMap.put(node.getCurrJsonNode().textValue(),
          YamlProperties.newBuilder().setLocalName(outputLocalName).setFqn(outputFqn).build());
    }
  }
}
