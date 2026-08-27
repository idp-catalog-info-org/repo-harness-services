/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.StagesDAGUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineYamlUtils {
  public JsonNode getStagesNodeFromRootNode(JsonNode rootNode) {
    return rootNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
  }

  public JsonNode getStagesNodeFromParallelNode(JsonNode parallelNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return parallelNode.get(YAMLFieldNameConstants.PARALLEL).get(YAMLFieldNameConstants.STAGES);
    }
    return parallelNode.get(YAMLFieldNameConstants.PARALLEL);
  }

  public JsonNode getStagesNodeFromInjectNode(JsonNode injectNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      throw new InvalidRequestException("Inject is not supported for V1 pipeline version.");
    }
    if (null != injectNode && null != injectNode.get(YAMLFieldNameConstants.INSERT)) {
      return injectNode.get(YAMLFieldNameConstants.INSERT).get(YAMLFieldNameConstants.STAGES);
    }
    throw new InvalidRequestException("Inject not cannot be null");
  }

  public JsonNode getStageNodeFromStagesNode(ArrayNode stagesNode, int stageIndex, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return stagesNode.get(stageIndex);
    }
    return stagesNode.get(stageIndex).get(YAMLFieldNameConstants.STAGE);
  }

  public boolean isParallelNode(JsonNode jsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return YAMLFieldNameConstants.PARALLEL.equals(jsonNode.get(YAMLFieldNameConstants.TYPE).asText());
    }
    return jsonNode.get(YAMLFieldNameConstants.PARALLEL) != null;
  }

  public boolean isStageGroupNode(JsonNode jsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return jsonNode.get(YAMLFieldNameConstants.TYPE) != null
          && YAMLFieldNameConstants.GROUP.equals(jsonNode.get(YAMLFieldNameConstants.TYPE).asText())
          && jsonNode.get(YAMLFieldNameConstants.GROUP).has(YAMLFieldNameConstants.STAGES);
    }
    return false;
  }

  public boolean isInjectNode(JsonNode jsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return false;
    }
    if (null != jsonNode) {
      return jsonNode.get(YAMLFieldNameConstants.INSERT) != null;
    }
    return false;
  }

  public JsonNode getStageNodeFromStagesElement(JsonNode currentJsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      if (currentJsonNode.has(YAMLFieldNameConstants.GROUP)) {
        return currentJsonNode.get(YAMLFieldNameConstants.GROUP);
      }
      return currentJsonNode;
    }
    return currentJsonNode.get(YAMLFieldNameConstants.STAGE);
  }

  public String getIdentifierFromStageNode(JsonNode stage, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return stage.get(YAMLFieldNameConstants.ID).textValue();
    }
    return stage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).textValue();
  }

  public List<String> getStageIdentifiersFromParallelStagesNode(JsonNode parallelStagesNode) {
    ArrayNode stagesOfParallelGroup =
        (ArrayNode) parallelStagesNode.get(YAMLFieldNameConstants.PARALLEL).get(YAMLFieldNameConstants.STAGES);
    return extractStageIdentifiersFromStagesArray(stagesOfParallelGroup);
  }

  public List<String> getStageIdentifiersFromStageGroup(JsonNode stageGroupNode) {
    ArrayNode stagesOfStageGroup =
        (ArrayNode) stageGroupNode.get(YAMLFieldNameConstants.GROUP).get(YAMLFieldNameConstants.STAGES);
    return extractStageIdentifiersFromStagesArray(stagesOfStageGroup);
  }

  private List<String> extractStageIdentifiersFromStagesArray(ArrayNode stagesArray) {
    List<String> stageIdentifiers = new ArrayList<>();
    for (JsonNode stageNode : stagesArray) {
      if (stageNode.has(YAMLFieldNameConstants.GROUP)) {
        stageIdentifiers.addAll(getStageIdentifiersFromStageGroup(stageNode));
      } else if (stageNode.has(YAMLFieldNameConstants.PARALLEL)) {
        stageIdentifiers.addAll(getStageIdentifiersFromParallelStagesNode(stageNode));
      } else {
        stageIdentifiers.add(PipelineYamlUtils.getIdentifierFromStageNode(stageNode, HarnessYamlVersion.V1));
      }
    }
    return stageIdentifiers;
  }

  public BasicPipeline getBasicPipelineObject(String pipelineYaml) {
    try {
      return YamlUtils.read(pipelineYaml, BasicPipeline.class);
    } catch (Exception ex) {
      throw new InvalidYamlException("Could not parse the pipelineYaml. It maybe be invalid.", ex);
    }
  }

  public boolean isFixedInputsOnRerun(String pipelineYaml) {
    if (EmptyPredicate.isNotEmpty(pipelineYaml)) {
      BasicPipeline basicPipeline = getBasicPipelineObject(pipelineYaml);
      return basicPipeline.isFixedInputsOnRerun();
    }
    return false;
  }

  public static String convertSequentialPipelineToDAG(String pipelineYaml) {
    try {
      ObjectNode rootNode = (ObjectNode) YamlUtils.readTree(pipelineYaml).getNode().getCurrJsonNode();

      ObjectNode pipelineNode = (ObjectNode) rootNode.get(YAMLFieldNameConstants.PIPELINE);
      if (pipelineNode == null) {
        throw new InvalidRequestException("Invalid pipeline YAML: missing 'pipeline' field");
      }

      ArrayNode stagesArrayNode = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.STAGES);
      if (stagesArrayNode == null || stagesArrayNode.isEmpty()) {
        throw new InvalidRequestException("Pipeline has no stages to convert");
      }

      List<ObjectNode> flattenedStages = StagesDAGUtils.processStagesWithDAGDependencies(stagesArrayNode);
      StagesDAGUtils.replaceStagesArrayInParent(pipelineNode, flattenedStages);

      return YamlUtils.writeYamlString(rootNode);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to transform pipeline YAML: " + e.getMessage(), e);
    }
  }
}
