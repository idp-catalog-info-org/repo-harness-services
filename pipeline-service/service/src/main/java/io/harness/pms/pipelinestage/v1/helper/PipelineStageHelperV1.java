/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.helper;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessor;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageHelperV1 {
  @Inject private YamlPreProcessorFactory yamlPreProcessorFactory;

  public void containsPipelineStage(String yaml) {
    YamlField pipelineYamlField = getPipelineYamlField(yaml);
    List<YamlNode> stages = pipelineYamlField.getNode().getField(STAGES).getNode().asArray();
    containsPipelineStage(stages);
  }

  private void containsPipelineStage(List<YamlNode> stages) {
    for (YamlNode stageNode : stages) {
      if (stageNode == null || stageNode.getType() == null) {
        continue;
      }
      if (stageNode.getType().equals(YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN)) {
        throw new InvalidRequestException("Nested pipeline is not supported");
      } else if (stageNode.getType().equals(PARALLEL)) {
        containsPipelineStageInParallelNode(stageNode.getField(PARALLEL).getNode());
      } else if (stageNode.getType().equals(GROUP)) {
        containsPipelineStageInGroupNode(stageNode.getField(GROUP).getNode());
      }
    }
  }

  private void containsPipelineStageInGroupNode(YamlNode yamlNode) {
    List<YamlNode> stagesInGroup = yamlNode.getField(STAGES).getNode().asArray();
    containsPipelineStage(stagesInGroup);
  }

  private void containsPipelineStageInParallelNode(YamlNode yamlNode) {
    List<YamlNode> stageInParallel = yamlNode.getField(STAGES).getNode().asArray();
    containsPipelineStage(stageInParallel);
  }

  public YamlField getChainedPipelineInputField(YamlField chainPipelineRootField) {
    if (chainPipelineRootField == null || chainPipelineRootField.getNode() == null) {
      return null;
    }

    YamlNode chainPipelineNode = chainPipelineRootField.getNode();
    YamlField chainYamlField = chainPipelineNode.getField(YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN);
    if (chainYamlField == null || chainYamlField.getNode() == null) {
      return null;
    }

    YamlNode chainNode = chainYamlField.getNode();
    YamlField withField = chainNode.getField(YAMLFieldNameConstants.WITH);
    if (withField == null || withField.getNode() == null) {
      return null;
    }

    return withField.getNode().getField(YAMLFieldNameConstants.INPUTS);
  }

  public JsonNode getInputSetJsonNode(YamlField pipelineInputs) {
    JsonNode inputJsonNode = null;
    if (pipelineInputs != null) {
      Map<String, JsonNode> map = getInputSetMapInternal(pipelineInputs);
      inputJsonNode = JsonPipelineUtils.asTree(map);
    }
    return inputJsonNode;
  }

  private Map<String, JsonNode> getInputSetMapInternal(YamlField pipelineInputs) {
    JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode();
    YamlUtils.removeUuid(inputJsonNode);
    Map<String, JsonNode> map = new HashMap<>();
    map.put("inputs", inputJsonNode);
    return map;
  }

  private YamlField getPipelineYamlField(String yaml) {
    try {
      yaml = preProcessPipelineYaml(yaml);
      return YamlUtils.readTreeWithDefaultObjectMapper(yaml).getNode().getField(YAMLFieldNameConstants.PIPELINE);
    } catch (Exception e) {
      throw new InvalidRequestException("Invalid YAML");
    }
  }

  private String preProcessPipelineYaml(String yaml) {
    YamlPreProcessor preProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
    if (preProcessor != null) {
      yaml = preProcessor.injectTypeField("", YamlUtils.readAsJsonNode(yaml));
    }
    return yaml;
  }

  public boolean validateChildGraphToGenerate(Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO, String stageNodeId) {
    // Validates nodeType which should be chain
    return graphLayoutNodeDTO.containsKey(stageNodeId) && graphLayoutNodeDTO.get(stageNodeId).getNodeType() != null
        && YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN.equals(graphLayoutNodeDTO.get(stageNodeId).getNodeType());
  }

  public IdentifierRef getIdentifierRef(String uses, String accountIdentifier) {
    String[] parts = uses.split("/");
    String orgIdentifier = parts.length > 0 ? parts[0] : uses;
    String projectIdentifier = parts.length > 1 ? parts[1] : null;
    String pipelineIdentifier = parts.length > 2 ? parts[2] : null;
    return IdentifierRef.builder()
        .identifier(pipelineIdentifier)
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public void validateFailureStrategy(ParameterField<List<FailureConfigV1>> failureStrategies) {
    if (ParameterField.isNotNull(failureStrategies) && isNotEmpty(failureStrategies.getValue())) {
      List<String> unsupportedActions = new ArrayList<>();
      for (FailureConfigV1 failureConfigV1 : failureStrategies.getValue()) {
        if (failureConfigV1.getAction().getRetry() != null) {
          unsupportedActions.add("Retry");
        } else if (failureConfigV1.getAction().getManualIntervention() != null) {
          unsupportedActions.add("Manual Intervention");
        } else if (failureConfigV1.getAction().getPipelineRollback() != null) {
          unsupportedActions.add("Pipeline Rollback");
        }
      }
      if (isNotEmpty(unsupportedActions)) {
        throw new InvalidRequestException(
            String.format("Action %s is not supported in pipeline stage", String.join(", ", unsupportedActions)));
      }
    }
  }
}
