/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.stages.StageExecutionResponse.StageExecutionResponseBuilder;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class StageExecutionSelectorHelper {
  private static final List<String> stageReferenceKeys =
      Collections.singletonList(YAMLFieldNameConstants.USE_FROM_STAGE);

  public List<StageExecutionResponse> getStageExecutionResponse(String pipelineYaml, String pipelineVersion) {
    List<BasicStageInfo> stagesInfo;
    if (HarnessYamlVersion.V0.equals(pipelineVersion)) {
      stagesInfo = getStageInfoListWithStagesRequired(pipelineYaml);
    } else {
      stagesInfo = getStageInfoListWithStagesRequiredV1(pipelineYaml);
    }

    List<StageExecutionResponse> executionResponses = new ArrayList<>();
    for (BasicStageInfo stageInfo : stagesInfo) {
      StageExecutionResponseBuilder builder = StageExecutionResponse.builder()
                                                  .stageIdentifier(stageInfo.getIdentifier())
                                                  .stageName(stageInfo.getName())
                                                  .stagesRequired(stageInfo.getStagesRequired())
                                                  .isToBeBlocked(stageInfo.isToBeBlocked());
      if (EmptyPredicate.isNotEmpty(stageInfo.getType()) && stageInfo.getType().equals("Approval")) {
        builder.message("Running an approval stage individually can be redundant");
      }
      if (stageInfo.isToBeBlocked()) {
        builder.message("This stage has a \"useFromStage\" dependency on " + stageInfo.getStagesRequired().toString());
      }
      executionResponses.add(builder.build());
    }
    return executionResponses;
  }

  @VisibleForTesting
  List<BasicStageInfo> getStageInfoListWithStagesRequired(String pipelineYaml) {
    List<BasicStageInfo> stageYamlList = getStageInfoList(pipelineYaml);
    return addStagesRequired(stageYamlList);
  }

  @VisibleForTesting
  List<BasicStageInfo> getStageInfoListWithStagesRequiredV1(String pipelineYaml) {
    List<BasicStageInfo> stageYamlList = getStageInfoListV1(pipelineYaml);
    return addStagesRequired(stageYamlList);
  }

  public List<BasicStageInfo> getStageInfoList(String pipelineYaml) {
    List<BasicStageInfo> stageInfoList = new ArrayList<>();
    try {
      YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
      List<YamlNode> stagesYamlNodes = pipelineYamlField.getNode()
                                           .getField(YAMLFieldNameConstants.PIPELINE)
                                           .getNode()
                                           .getField(YAMLFieldNameConstants.STAGES)
                                           .getNode()
                                           .asArray();
      for (YamlNode stageYamlNode : stagesYamlNodes) {
        if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
          stageInfoList.add(getBasicStageInfo(stageYamlNode));
          continue;
        }
        if (null != stageYamlNode && stageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
          addInjectStages(stageYamlNode, stageInfoList);
          continue;
        }
        addParallelStages(stageYamlNode, stageInfoList);
      }
      return stageInfoList;
    } catch (IOException e) {
      log.error("Could not read pipeline yaml while extracting stage yaml list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting stage yaml list");
    }
  }

  private static void addInjectStages(YamlNode stageYamlNode, List<BasicStageInfo> stageInfoList) {
    YamlNode injectYamlNode = stageYamlNode.getField(YAMLFieldNameConstants.INSERT)
                                  .getNode()
                                  .getField(YAMLFieldNameConstants.STAGES)
                                  .getNode();
    if (injectYamlNode == null || !injectYamlNode.isArray()
        || injectYamlNode.asText().equals(YAMLFieldNameConstants.INPUT_EXPRESSION)) {
      return;
    }
    List<YamlNode> injectStagesYamlNode = injectYamlNode.asArray();
    for (YamlNode injectedStage : injectStagesYamlNode) {
      if (injectedStage.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        addParallelStages(injectedStage, stageInfoList);
        continue;
      }
      stageInfoList.add(getBasicStageInfo(injectedStage));
    }
  }

  private static void addParallelStages(YamlNode stageYamlNode, List<BasicStageInfo> stageInfoList) {
    List<YamlNode> parallelStagesYamlNode = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    for (YamlNode parallelStageYamlNode : parallelStagesYamlNode) {
      if (null != parallelStageYamlNode && parallelStageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        addInjectStages(parallelStageYamlNode, stageInfoList);
        continue;
      }
      stageInfoList.add(getBasicStageInfo(parallelStageYamlNode));
    }
  }

  @VisibleForTesting
  public List<BasicStageInfo> getStageInfoListV1(String pipelineYaml) {
    List<BasicStageInfo> stageInfoList = new ArrayList<>();
    try {
      YamlField pipelineYamlField =
          YamlUtils.readTree(pipelineYaml).getNode().getField(YAMLFieldNameConstants.PIPELINE);
      List<YamlNode> stagesYamlNodes =
          pipelineYamlField.getNode().getField(YAMLFieldNameConstants.STAGES).getNode().asArray();
      for (YamlNode stageYamlNode : stagesYamlNodes) {
        YamlField parallelField = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL);
        if (parallelField != null) {
          buildStageInfoListForYamlField(parallelField, stageInfoList);
          continue;
        }

        if (stageYamlNode.getField(YAMLFieldNameConstants.ID) != null) {
          stageInfoList.add(getBasicStageInfoV1(stageYamlNode));
        }
      }
      return stageInfoList;
    } catch (IOException e) {
      log.error("Could not read pipeline yaml while extracting stage yaml list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting stage yaml list");
    }
  }

  private void buildStageInfoListForYamlField(YamlField yamlField, List<BasicStageInfo> stageInfoList) {
    List<YamlNode> stagesYamlNode;
    if (yamlField.getNode().getField(YAMLFieldNameConstants.STAGES) != null) {
      stagesYamlNode = yamlField.getNode().getField(YAMLFieldNameConstants.STAGES).getNode().asArray();
    } else {
      stagesYamlNode = yamlField.getNode().asArray();
    }
    for (YamlNode stageYamlNode : stagesYamlNode) {
      if (stageYamlNode.getField(YAMLFieldNameConstants.ID) != null) {
        stageInfoList.add(getBasicStageInfoV1(stageYamlNode));
      }
    }
  }

  @VisibleForTesting
  BasicStageInfo getBasicStageInfo(YamlNode stageYamlNode) {
    String identifier = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getIdentifier();
    String name = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getName();
    String type = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getType();
    return BasicStageInfo.builder().identifier(identifier).name(name).type(type).stageYamlNode(stageYamlNode).build();
  }

  @VisibleForTesting
  BasicStageInfo getBasicStageInfoV1(YamlNode stageYamlNode) {
    String id = stageYamlNode.getId();
    String name = stageYamlNode.getName();
    String type = stageYamlNode.getType();
    if (EmptyPredicate.isEmpty(name) && EmptyPredicate.isNotEmpty(id)) {
      name = id;
    }
    return BasicStageInfo.builder().identifier(id).name(name).type(type).stageYamlNode(stageYamlNode).build();
  }

  @VisibleForTesting
  List<BasicStageInfo> addStagesRequired(List<BasicStageInfo> stageYamlList) {
    List<BasicStageInfo> fullStageInfoList = new ArrayList<>();
    for (BasicStageInfo basicStageInfo : stageYamlList) {
      YamlNode stageYamlNode = basicStageInfo.getStageYamlNode();
      Set<String> references = new HashSet<>();
      getNonExpressionReferences(stageYamlNode, references);
      references.remove(basicStageInfo.getIdentifier());
      fullStageInfoList.add(
          basicStageInfo.withStagesRequired(new ArrayList<>(references)).withToBeBlocked(!references.isEmpty()));
    }
    return fullStageInfoList;
  }

  @VisibleForTesting
  static void getNonExpressionReferences(YamlNode yamlNode, Set<String> references) {
    if (yamlNode.isObject()) {
      getNonExpressionReferencesForObject(yamlNode, references);
    } else if (yamlNode.isArray()) {
      getNonExpressionReferencesForArray(yamlNode, references);
    }
  }

  @VisibleForTesting
  static void getNonExpressionReferencesForArray(YamlNode yamlNode, Set<String> references) {
    List<YamlNode> yamlNodes = yamlNode.asArray();
    for (YamlNode node : yamlNodes) {
      getNonExpressionReferences(node, references);
    }
  }

  @VisibleForTesting
  static void getNonExpressionReferencesForObject(YamlNode yamlNode, Set<String> references) {
    List<String> keys = yamlNode.fetchKeys();
    for (String key : keys) {
      if (stageReferenceKeys.contains(key)) {
        String stage = "";
        if (key.equals(YAMLFieldNameConstants.USE_FROM_STAGE)) {
          if (yamlNode.getField(key).getNode().isObject()) {
            stage = yamlNode.getField(key).getNode().getField(YAMLFieldNameConstants.STAGE).getNode().asText();
          } else {
            stage = yamlNode.getField(key).getNode().asText();
          }
          references.add(stage);
        }
        continue;
      }
      if (yamlNode.getField(key) == null) {
        throw new InvalidYamlException("Could not fetch the stage references while extracting stage yaml list, please "
            + "validate the pipeline yaml if it's correct");
      }
      getNonExpressionReferences(yamlNode.getField(key).getNode(), references);
    }
  }

  /**
   * Not in use right now. But keeping this just in case the product requirement comes back
   */
  @VisibleForTesting
  List<BasicStageInfo> addApprovalStagesRequired(List<BasicStageInfo> stageYamlList) {
    List<BasicStageInfo> fullStageInfoList = new ArrayList<>();

    List<String> currRequiredStages = new ArrayList<>();
    boolean isPreviousApproval = false;
    for (BasicStageInfo currStageInfo : stageYamlList) {
      boolean isCurrentApproval = currStageInfo.getType().equals("Approval");
      if (!isCurrentApproval) {
        fullStageInfoList.add(BasicStageInfo.builder()
                                  .identifier(currStageInfo.getIdentifier())
                                  .name(currStageInfo.getName())
                                  .type(currStageInfo.getType())
                                  .stagesRequired(new ArrayList<>(currRequiredStages))
                                  .build());
        isPreviousApproval = false;
      } else if (!isPreviousApproval) {
        fullStageInfoList.add(BasicStageInfo.builder()
                                  .identifier(currStageInfo.getIdentifier())
                                  .name(currStageInfo.getName())
                                  .type(currStageInfo.getType())
                                  .stagesRequired(Collections.emptyList())
                                  .build());
        currRequiredStages = new ArrayList<>();
        currRequiredStages.add(currStageInfo.getIdentifier());
        isPreviousApproval = true;
      } else {
        fullStageInfoList.add(BasicStageInfo.builder()
                                  .identifier(currStageInfo.getIdentifier())
                                  .name(currStageInfo.getName())
                                  .type(currStageInfo.getType())
                                  .stagesRequired(new ArrayList<>(currRequiredStages))
                                  .build());
        currRequiredStages.add(currStageInfo.getIdentifier());
        isPreviousApproval = true;
      }
    }

    return fullStageInfoList;
  }
}
