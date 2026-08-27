/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for extracting barrier identifiers from pipeline YAML structures.
 * Used to identify barriers in chained pipeline contexts to enable cross-pipeline barrier functionality.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class PipelineBarrierExtractor {
  /**
   * Helper method to extract the child pipeline's inputs field from the context.
   *
   * @param ctx The plan creation context
   * @return The YAML field containing the child pipeline's inputs, or null if not found
   */
  public YamlField getChildPipelineInputsField(PlanCreationContext ctx) {
    return Optional.ofNullable(ctx)
        .map(PlanCreationContext::getCurrentField)
        .map(YamlField::getNode)
        .map(node -> node.getField(YAMLFieldNameConstants.SPEC))
        .map(YamlField::getNode)
        .map(node -> node.getField(YAMLFieldNameConstants.INPUTS))
        .orElse(null);
  }

  /**
   * Traverses the YAML structure of a child pipeline to find all barrier references.
   * Searches through all stages and their steps to identify any barrier steps.
   *
   * @param inputsOfChildPipeline The YAML field containing the inputs for the child pipeline
   * @return List of barrier references found in the child pipeline
   */
  public List<String> getAllBarriersUsedInChildPipeline(YamlField inputsOfChildPipeline) {
    List<String> barrierRefs = new ArrayList<>();

    if (inputsOfChildPipeline == null || inputsOfChildPipeline.getNode() == null) {
      return barrierRefs;
    }

    YamlNode inputsNode = inputsOfChildPipeline.getNode();

    // Try direct format: inputs.stages
    YamlField directStagesField = inputsNode.getField(YAMLFieldNameConstants.STAGES);
    if (directStagesField != null && directStagesField.getNode() != null) {
      Optional.ofNullable(directStagesField.getNode().asArray())
          .ifPresent(stages -> stages.forEach(stageWrapper -> processStageForBarriers(stageWrapper, barrierRefs)));
    }

    // Try template format: inputs.template.templateInputs.stages
    YamlField templateField = inputsNode.getField(YAMLFieldNameConstants.TEMPLATE);
    if (templateField != null && templateField.getNode() != null) {
      YamlField templateInputsField = templateField.getNode().getField(YAMLFieldNameConstants.TEMPLATE_INPUTS);
      if (templateInputsField != null && templateInputsField.getNode() != null) {
        YamlField templateStagesField = templateInputsField.getNode().getField(YAMLFieldNameConstants.STAGES);
        if (templateStagesField != null && templateStagesField.getNode() != null) {
          Optional.ofNullable(templateStagesField.getNode().asArray())
              .ifPresent(stages -> stages.forEach(stageWrapper -> processStageForBarriers(stageWrapper, barrierRefs)));
        }
      }
    }

    return barrierRefs.stream().distinct().collect(Collectors.toList());
  }

  /**
   * Processes a stage node to extract barrier references from its execution steps.
   *
   * @param stageWrapper The YAML node containing the stage
   * @param barrierRefs The list to collect barrier references
   */
  private void processStageForBarriers(YamlNode stageWrapper, List<String> barrierRefs) {
    if (stageWrapper == null) {
      return;
    }

    if (stageWrapper.getField(YAMLFieldNameConstants.STAGE) != null) {
      // Process individual stage
      processIndividualStage(stageWrapper.getField(YAMLFieldNameConstants.STAGE), barrierRefs);
    } else if (stageWrapper.getField(YAMLFieldNameConstants.PARALLEL) != null) {
      // Process parallel stages
      processParallelStages(stageWrapper.getField(YAMLFieldNameConstants.PARALLEL), barrierRefs);
    } else if (stageWrapper.getField(YAMLFieldNameConstants.INSERT) != null) {
      // Process insert stages
      processInsertStages(stageWrapper.getField(YAMLFieldNameConstants.INSERT), barrierRefs);
    }
  }

  /**
   * Processes an individual stage to extract barrier references from its execution steps.
   *
   * @param stageField The YAML field containing the stage
   * @param barrierRefs The list to collect barrier references
   */
  private void processIndividualStage(YamlField stageField, List<String> barrierRefs) {
    Optional<YamlNode> stageNode = Optional.ofNullable(stageField).map(YamlField::getNode);

    // Try direct format: stage.spec.execution.steps
    Optional<YamlNode> specNode =
        stageNode.map(stage -> stage.getField(YAMLFieldNameConstants.SPEC)).map(YamlField::getNode);

    // Fallback to template format: stage.template.templateInputs.spec
    if (specNode.isEmpty()) {
      specNode = stageNode.map(stage -> stage.getField(YAMLFieldNameConstants.TEMPLATE))
                     .map(YamlField::getNode)
                     .map(template -> template.getField(YAMLFieldNameConstants.TEMPLATE_INPUTS))
                     .map(YamlField::getNode)
                     .map(templateInputs -> templateInputs.getField(YAMLFieldNameConstants.SPEC))
                     .map(YamlField::getNode);
    }

    specNode.map(spec -> spec.getField(YAMLFieldNameConstants.EXECUTION))
        .map(YamlField::getNode)
        .map(execution -> execution.getField(YAMLFieldNameConstants.STEPS))
        .map(YamlField::getNode)
        .map(YamlNode::asArray)
        .ifPresent(steps -> verifyStepsNodeAndPopulateRefs(steps, barrierRefs));
  }

  /**
   * Processes parallel stages to extract barrier references.
   *
   * @param parallelField The parallel field containing stages
   * @param barrierRefs The list to collect barrier references
   */
  private void processParallelStages(YamlField parallelField, List<String> barrierRefs) {
    Optional.ofNullable(parallelField)
        .map(YamlField::getNode)
        .map(YamlNode::asArray)
        .ifPresent(parallelStages
            -> parallelStages.forEach(stageWrapper -> processStageForBarriers(stageWrapper, barrierRefs)));
  }

  /**
   * Processes insert stages to extract barrier references.
   *
   * @param insertField The insert field containing stages
   * @param barrierRefs The list to collect barrier references
   */
  private void processInsertStages(YamlField insertField, List<String> barrierRefs) {
    Optional.ofNullable(insertField)
        .map(YamlField::getNode)
        .map(node -> node.getField(YAMLFieldNameConstants.STAGES))
        .map(YamlField::getNode)
        .map(YamlNode::asArray)
        .ifPresent(
            insertStages -> insertStages.forEach(stageWrapper -> processStageForBarriers(stageWrapper, barrierRefs)));
  }

  /**
   * Recursively processes a list of step nodes to find all barrier references.
   * Handles step, step-group, insert, and parallel node types.
   *
   * @param steps The list of step nodes to process
   * @param barrierRefs The list to collect barrier references
   */
  private void verifyStepsNodeAndPopulateRefs(List<YamlNode> steps, List<String> barrierRefs) {
    if (steps == null) {
      return;
    }

    for (YamlNode stepWrapper : steps) {
      if (stepWrapper == null) {
        continue;
      }

      if (stepWrapper.getField(YAMLFieldNameConstants.STEP) != null) {
        // Process individual step
        verifyStepNodeAndPopulateRefs(stepWrapper, barrierRefs);
      } else if (stepWrapper.getField(YAMLFieldNameConstants.STEP_GROUP) != null) {
        // Process step group
        verifyStepGroupNodeAndPopulateRefs(stepWrapper.getField(YAMLFieldNameConstants.STEP_GROUP), barrierRefs);
      } else if (stepWrapper.getField(YAMLFieldNameConstants.INSERT) != null) {
        // Process insert
        verifyInsertNodeAndPopulateRefs(stepWrapper.getField(YAMLFieldNameConstants.INSERT), barrierRefs);
      } else if (stepWrapper.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        // Process parallel steps
        verifyParallelNodeAndPopulateRefs(stepWrapper.getField(YAMLFieldNameConstants.PARALLEL), barrierRefs);
      }
    }
  }

  /**
   * Processes a step group field to extract steps for barrier identification.
   *
   * @param stepGroupField The step group field to process
   * @param barrierRefs The list to collect barrier references
   */
  private void verifyStepGroupNodeAndPopulateRefs(YamlField stepGroupField, List<String> barrierRefs) {
    Optional.ofNullable(stepGroupField)
        .map(YamlField::getNode)
        .map(node -> node.getField(YAMLFieldNameConstants.STEPS))
        .map(YamlField::getNode)
        .filter(YamlNode::isArray)
        .map(YamlNode::asArray)
        .ifPresent(steps -> verifyStepsNodeAndPopulateRefs(steps, barrierRefs));
  }

  /**
   * Processes an insert field to extract steps for barrier identification.
   *
   * @param insertField The insert field to process
   * @param barrierRefs The list to collect barrier references
   */
  private void verifyInsertNodeAndPopulateRefs(YamlField insertField, List<String> barrierRefs) {
    Optional.ofNullable(insertField)
        .map(YamlField::getNode)
        .map(node -> node.getField(YAMLFieldNameConstants.STEPS))
        .map(YamlField::getNode)
        .filter(YamlNode::isArray) // Empty Insert is a valid case
        .map(YamlNode::asArray)
        .ifPresent(steps -> verifyStepsNodeAndPopulateRefs(steps, barrierRefs));
  }

  /**
   * Processes parallel steps to extract barrier references.
   *
   * @param parallelField The parallel field to process
   * @param barrierRefs The list to collect barrier references
   */
  private void verifyParallelNodeAndPopulateRefs(YamlField parallelField, List<String> barrierRefs) {
    Optional.ofNullable(parallelField)
        .map(YamlField::getNode)
        .filter(YamlNode::isArray)
        .map(YamlNode::asArray)
        .ifPresent(parallelSteps -> {
          parallelSteps.stream().filter(step -> step != null).forEach(step -> {
            if (step.getField(YAMLFieldNameConstants.STEP) != null) {
              verifyStepNodeAndPopulateRefs(step, barrierRefs);
            } else if (step.getField(YAMLFieldNameConstants.STEP_GROUP) != null) {
              verifyStepGroupNodeAndPopulateRefs(step.getField(YAMLFieldNameConstants.STEP_GROUP), barrierRefs);
            } else if (step.getField(YAMLFieldNameConstants.INSERT) != null) {
              verifyInsertNodeAndPopulateRefs(step.getField(YAMLFieldNameConstants.INSERT), barrierRefs);
            }
          });
        });
  }

  /**
   * Processes a single step node to check if it's a Barrier step and extract the barrier reference.
   *
   * @param stepWrapper The YAML node containing the step
   * @param barrierRefs The list to collect barrier references
   */
  private void verifyStepNodeAndPopulateRefs(YamlNode stepWrapper, List<String> barrierRefs) {
    Optional<YamlNode> stepNodeOpt = Optional.ofNullable(stepWrapper)
                                         .map(wrapper -> wrapper.getField(YAMLFieldNameConstants.STEP))
                                         .map(YamlField::getNode);

    if (stepNodeOpt.isEmpty()) {
      return;
    }

    YamlNode stepNode = stepNodeOpt.get();

    // Try direct format: step.type and step.spec
    Optional<String> typeOpt = Optional.ofNullable(stepNode.getField(YAMLFieldNameConstants.TYPE))
                                   .map(YamlField::getNode)
                                   .map(YamlNode::asText);
    Optional<YamlNode> specNodeOpt =
        Optional.ofNullable(stepNode.getField(YAMLFieldNameConstants.SPEC)).map(YamlField::getNode);

    // Fallback to template format: step.template.templateInputs.type and step.template.templateInputs.spec
    if (typeOpt.isEmpty() && specNodeOpt.isEmpty()) {
      Optional<YamlNode> templateInputsOpt =
          Optional.ofNullable(stepNode.getField(YAMLFieldNameConstants.TEMPLATE))
              .map(YamlField::getNode)
              .map(template -> template.getField(YAMLFieldNameConstants.TEMPLATE_INPUTS))
              .map(YamlField::getNode);

      typeOpt = templateInputsOpt.map(ti -> ti.getField(YAMLFieldNameConstants.TYPE))
                    .map(YamlField::getNode)
                    .map(YamlNode::asText);
      specNodeOpt = templateInputsOpt.map(ti -> ti.getField(YAMLFieldNameConstants.SPEC)).map(YamlField::getNode);
    }

    if (typeOpt.isPresent() && YAMLFieldNameConstants.BARRIER.equals(typeOpt.get())) {
      specNodeOpt.map(spec -> spec.getField(YAMLFieldNameConstants.BARRIER_REF))
          .map(YamlField::getNode)
          .map(YamlNode::asText)
          .ifPresent(barrierRefs::add);
    }
  }
}
