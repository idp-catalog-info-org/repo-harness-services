/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts step information (label + FQN) from pipeline YAML (Harness V0 or unified V1) for use in
 * step-level notification configuration. Mirrors {@code StageExecutionSelectorHelper} for stage
 * discovery, then traverses into the step hierarchy using version-specific rules (including V1
 * {@code group: { id, stages }} <b>stage groups</b> and {@code group: { id, steps }} <b>step groups</b>).
 */
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class StepNotificationSelectorHelper {
  private static final String FQN_SEPARATOR = ".";

  public List<BasicStepInfo> getStepInfoList(String pipelineYaml) {
    try {
      YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
      YamlField pipelineField = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
      if (pipelineField == null) {
        return new ArrayList<>();
      }
      YamlNode pipelineNode = pipelineField.getNode();
      if (isV1UnifiedPipelineYaml(pipelineNode)) {
        return getStepInfoListV1(pipelineNode);
      }
      return getStepInfoListV0(pipelineNode);
    } catch (IOException e) {
      log.error("Could not read pipeline yaml while extracting step info list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting step info list");
    }
  }

  private List<BasicStepInfo> getStepInfoListV0(YamlNode pipelineNode) {
    List<BasicStepInfo> stepInfoList = new ArrayList<>();
    YamlField stagesField = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    if (stagesField == null) {
      return stepInfoList;
    }
    List<YamlNode> stagesYamlNodes = stagesField.getNode().asArray();
    for (YamlNode stageYamlNode : stagesYamlNodes) {
      processStageNode(stageYamlNode, stepInfoList);
    }
    return stepInfoList;
  }

  /**
   * V1 unified pipelines use {@code id} on stages/steps (not the V0 {@code stage}/{@code step}
   * wrappers) and nest parallel stages as {@code parallel: { stages: [...] }}.
   */
  @VisibleForTesting
  private boolean isV1UnifiedPipelineYaml(YamlNode pipelineNode) {
    YamlField stagesField = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    if (stagesField == null || !stagesField.getNode().isArray()) {
      return false;
    }
    List<YamlNode> stagesYamlNodes = stagesField.getNode().asArray();
    boolean sawV1 = false;
    for (YamlNode stageYamlNode : stagesYamlNodes) {
      if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null
          || stageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        // Fast-fail: if any V0 stage wrapper is present, treat the YAML as V0.
        return false;
      }
      YamlField parallelField = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (parallelField != null) {
        YamlNode parallelNode = parallelField.getNode();
        if (parallelNode.isArray()) {
          // Fast-fail: top-level V0 parallel stages are a YAML array under `parallel`.
          return false;
        } else if (parallelNode.getField(YAMLFieldNameConstants.STAGES) != null) {
          sawV1 = true;
        }
      }
      if (stageYamlNode.getField(YAMLFieldNameConstants.ID) != null
          && stageYamlNode.getField(YAMLFieldNameConstants.STAGE) == null) {
        sawV1 = true;
      }
      YamlField groupField = stageYamlNode.getField(YAMLFieldNameConstants.GROUP);
      if (groupField != null) {
        YamlNode groupNode = groupField.getNode();
        if (groupNode.getField(YAMLFieldNameConstants.STAGES) != null) {
          // V1 stage group (GroupStages): group: { id, stages } — not a step-level group (group.steps).
          sawV1 = true;
        }
      }
    }
    return sawV1;
  }

  private List<BasicStepInfo> getStepInfoListV1(YamlNode pipelineNode) {
    List<BasicStepInfo> stepInfoList = new ArrayList<>();
    YamlField stagesField = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    if (stagesField == null || !stagesField.getNode().isArray()) {
      return stepInfoList;
    }
    for (YamlNode stageYamlNode : stagesField.getNode().asArray()) {
      emitV1PipelineStageItems(stageYamlNode, "", stepInfoList);
    }
    return stepInfoList;
  }

  /**
   * Walks entries under {@code pipeline.stages}: {@code parallel: { stages }}, V1 <b>stage groups</b>
   * {@code group: { id, stages }} (see {@code GroupStages} in the V1 schema), and leaf stages with {@code id}
   * and {@code steps}. Parallel branches do not add a path segment; stage groups prefix inner stage FQNs
   * with {@code <stageGroupId>.} so results align with execution ambiance for {@link
   * io.harness.steps.group.GroupStepV1} when {@code isInsideStages} is true.
   */
  private void emitV1PipelineStageItems(YamlNode node, String stagePathPrefix, List<BasicStepInfo> stepInfoList) {
    if (hasTemplateRef(node)) {
      return;
    }
    YamlField parallelField = node.getField(YAMLFieldNameConstants.PARALLEL);
    if (parallelField != null) {
      YamlNode parallelNode = parallelField.getNode();
      YamlField innerStagesField = parallelNode.getField(YAMLFieldNameConstants.STAGES);
      if (innerStagesField != null && innerStagesField.getNode().isArray()) {
        for (YamlNode child : innerStagesField.getNode().asArray()) {
          emitV1PipelineStageItems(child, stagePathPrefix, stepInfoList);
        }
      }
      return;
    }
    YamlField groupField = node.getField(YAMLFieldNameConstants.GROUP);
    if (groupField != null) {
      YamlNode groupSpec = groupField.getNode();
      if (hasTemplateRef(groupSpec)) {
        return;
      }
      YamlField innerStagesForStageGroup = groupSpec.getField(YAMLFieldNameConstants.STAGES);
      if (innerStagesForStageGroup != null && innerStagesForStageGroup.getNode().isArray()) {
        String groupId = groupSpec.getId();
        if (EmptyPredicate.isEmpty(groupId)) {
          log.warn("Skipping unnamed V1 stage group (missing/empty id) while extracting step FQNs. stagePathPrefix={}, "
                  + "yamlPath={}",
              stagePathPrefix, groupSpec.getYamlPath());
          return;
        }
        String nestedPrefix =
            EmptyPredicate.isEmpty(stagePathPrefix) ? groupId : stagePathPrefix + FQN_SEPARATOR + groupId;
        for (YamlNode child : innerStagesForStageGroup.getNode().asArray()) {
          emitV1PipelineStageItems(child, nestedPrefix, stepInfoList);
        }
        return;
      }
    }
    String stageId = node.getId();
    if (EmptyPredicate.isEmpty(stageId)) {
      return;
    }
    String fullStagePath =
        EmptyPredicate.isEmpty(stagePathPrefix) ? stageId : stagePathPrefix + FQN_SEPARATOR + stageId;
    YamlField stepsField = node.getField(YAMLFieldNameConstants.STEPS);
    if (stepsField != null && stepsField.getNode().isArray()) {
      walkV1StepItems(stepsField.getNode().asArray(), fullStagePath, stepInfoList);
    }
  }

  private void walkV1StepItems(List<YamlNode> items, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    for (YamlNode item : items) {
      if (hasTemplateRef(item)) {
        continue;
      }
      YamlField parallelField = item.getField(YAMLFieldNameConstants.PARALLEL);
      if (parallelField != null) {
        YamlNode parallelNode = parallelField.getNode();
        YamlField innerStepsField = parallelNode.getField(YAMLFieldNameConstants.STEPS);
        if (innerStepsField != null && innerStepsField.getNode().isArray()) {
          walkV1StepItems(innerStepsField.getNode().asArray(), fqnPrefix, stepInfoList);
        }
        continue;
      }
      YamlField groupField = item.getField(YAMLFieldNameConstants.GROUP);
      if (groupField != null) {
        YamlNode groupNode = groupField.getNode();
        if (hasTemplateRef(groupNode)) {
          continue;
        }
        String groupId = groupNode.getId();
        if (EmptyPredicate.isEmpty(groupId)) {
          continue;
        }
        String newPrefix = fqnPrefix + FQN_SEPARATOR + groupId;
        YamlField innerStepsField = groupNode.getField(YAMLFieldNameConstants.STEPS);
        if (innerStepsField != null && innerStepsField.getNode().isArray()) {
          walkV1StepItems(innerStepsField.getNode().asArray(), newPrefix, stepInfoList);
        }
        continue;
      }
      String stepId = item.getId();
      if (EmptyPredicate.isEmpty(stepId)) {
        continue;
      }
      String fqn = fqnPrefix + FQN_SEPARATOR + stepId;
      String label = Optional.ofNullable(item.getName()).filter(n -> !EmptyPredicate.isEmpty(n)).orElse(stepId);
      stepInfoList.add(BasicStepInfo.builder().label(label).stepFqn(fqn).build());
    }
  }

  private void processStageNode(YamlNode stageYamlNode, List<BasicStepInfo> stepInfoList) {
    if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
      extractStepsFromStage(stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode(), stepInfoList);
    } else if (stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
      List<YamlNode> parallelStages = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
      for (YamlNode parallelStageNode : parallelStages) {
        processStageNode(parallelStageNode, stepInfoList);
      }
    } else if (stageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
      processInsertNode(stageYamlNode, stepInfoList);
    }
  }

  private void processInsertNode(YamlNode stageYamlNode, List<BasicStepInfo> stepInfoList) {
    YamlNode insertNode = stageYamlNode.getField(YAMLFieldNameConstants.INSERT).getNode();
    YamlField insertStagesField = insertNode.getField(YAMLFieldNameConstants.STAGES);
    if (insertStagesField == null || !insertStagesField.getNode().isArray()) {
      return;
    }
    List<YamlNode> insertStages = insertStagesField.getNode().asArray();
    for (YamlNode insertedStage : insertStages) {
      processStageNode(insertedStage, stepInfoList);
    }
  }

  @VisibleForTesting
  void extractStepsFromStage(YamlNode stageNode, List<BasicStepInfo> stepInfoList) {
    String stageIdentifier = stageNode.getIdentifier();
    if (stageIdentifier == null) {
      return;
    }

    if (hasTemplateRef(stageNode)) {
      return;
    }

    YamlNode stepsNode = findStepsNode(stageNode);
    if (stepsNode != null && stepsNode.isArray()) {
      walkExecutionWrappers(stepsNode.asArray(), stageIdentifier, stepInfoList);
    }
  }

  private YamlNode findStepsNode(YamlNode stageNode) {
    // V0 path: spec.execution.steps
    YamlField specField = stageNode.getField(YAMLFieldNameConstants.SPEC);
    if (specField == null) {
      return null;
    }
    YamlNode specNode = specField.getNode();

    YamlField executionField = specNode.getField(YAMLFieldNameConstants.EXECUTION);
    if (executionField != null) {
      YamlField stepsField = executionField.getNode().getField(YAMLFieldNameConstants.STEPS);
      if (stepsField != null) {
        return stepsField.getNode();
      }
    }

    // Fallback: spec.steps (some stage types)
    YamlField directStepsField = specNode.getField(YAMLFieldNameConstants.STEPS);
    if (directStepsField != null) {
      return directStepsField.getNode();
    }

    return null;
  }

  @VisibleForTesting
  void walkExecutionWrappers(List<YamlNode> wrappers, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    for (YamlNode wrapper : wrappers) {
      if (wrapper.getField(YAMLFieldNameConstants.STEP) != null) {
        processStepWrapper(wrapper, fqnPrefix, stepInfoList);
      } else if (wrapper.getField(YAMLFieldNameConstants.STEP_GROUP) != null) {
        processStepGroupWrapper(wrapper, fqnPrefix, stepInfoList);
      } else if (wrapper.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        processParallelWrapper(wrapper, fqnPrefix, stepInfoList);
      } else if (wrapper.getField(YAMLFieldNameConstants.INSERT) != null) {
        processInsertStepsWrapper(wrapper, fqnPrefix, stepInfoList);
      }
    }
  }

  private void processStepWrapper(YamlNode wrapper, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    YamlNode stepNode = wrapper.getField(YAMLFieldNameConstants.STEP).getNode();
    if (hasTemplateRef(stepNode)) {
      return;
    }
    String stepIdentifier = stepNode.getIdentifier();
    if (stepIdentifier == null) {
      return;
    }
    String fqn = fqnPrefix + FQN_SEPARATOR + stepIdentifier;
    stepInfoList.add(BasicStepInfo.builder().label(fqn).stepFqn(fqn).build());
  }

  private void processStepGroupWrapper(YamlNode wrapper, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    YamlNode stepGroupNode = wrapper.getField(YAMLFieldNameConstants.STEP_GROUP).getNode();
    if (hasTemplateRef(stepGroupNode)) {
      return;
    }
    String sgIdentifier = stepGroupNode.getIdentifier();
    if (sgIdentifier == null) {
      return;
    }
    String newPrefix = fqnPrefix + FQN_SEPARATOR + sgIdentifier;
    YamlField innerStepsField = stepGroupNode.getField(YAMLFieldNameConstants.STEPS);
    if (innerStepsField != null && innerStepsField.getNode().isArray()) {
      walkExecutionWrappers(innerStepsField.getNode().asArray(), newPrefix, stepInfoList);
    }
  }

  private void processParallelWrapper(YamlNode wrapper, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    List<YamlNode> parallelChildren = wrapper.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    walkExecutionWrappers(parallelChildren, fqnPrefix, stepInfoList);
  }

  private void processInsertStepsWrapper(YamlNode wrapper, String fqnPrefix, List<BasicStepInfo> stepInfoList) {
    YamlNode insertNode = wrapper.getField(YAMLFieldNameConstants.INSERT).getNode();
    String insertIdentifier = insertNode.getIdentifier();
    if (insertIdentifier == null) {
      return;
    }
    String newPrefix = fqnPrefix + FQN_SEPARATOR + insertIdentifier;
    YamlField innerStepsField = insertNode.getField(YAMLFieldNameConstants.STEPS);
    if (innerStepsField != null && innerStepsField.getNode().isArray()) {
      walkExecutionWrappers(innerStepsField.getNode().asArray(), newPrefix, stepInfoList);
    }
  }

  private boolean hasTemplateRef(YamlNode node) {
    return node.getField(YAMLFieldNameConstants.TEMPLATE) != null
        || node.getField(YAMLFieldNameConstants.TEMPLATE_REF) != null;
  }
}
