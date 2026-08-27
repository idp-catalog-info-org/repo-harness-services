/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.upload.unified;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.steps.StepSpecTypeConstants.UPLOAD_STEP_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ci.plan.creator.step.unified.UnifiedPmsAbstractStepPlanCreator;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.unified.UnifiedPmsAbstractStepNode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.upload.FilesUploadStepParameters;
import io.harness.yaml.utils.v1.NGVariablesUtilsV1;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class UnifiedFilesUploadStepPlanCreator extends UnifiedPmsAbstractStepPlanCreator<UnifiedFilesUploadStepNode> {
  @Override
  public Class<UnifiedFilesUploadStepNode> getFieldClass() {
    return UnifiedFilesUploadStepNode.class;
  }

  @Override
  public UnifiedFilesUploadStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedFilesUploadStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse files upload step yaml.", e);
    }
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Set.of(YAMLFieldNameConstants.FILES_UPLOAD_V1);
  }

  @Override
  protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
    UnifiedFilesUploadStepNode filesUploadStepNode = (UnifiedFilesUploadStepNode) stepNode;
    UnifiedFilesUploadStepInfo stepInfo = filesUploadStepNode.getUnifiedFilesUploadStepInfo();

    Map<String, Object> inputVariables = new HashMap<>();
    Map<String, Object> outputVariables = new HashMap<>();

    // Convert v1 inputs to v0 inputVariables and outputVariables
    if (stepInfo != null && stepInfo.getInputs() != null && !isEmpty(stepInfo.getInputs().getMap())) {
      // Convert inputs to variables map
      Map<String, Object> variablesMap = NGVariablesUtilsV1.getMapOfVariables(stepInfo.getInputs().getMap());

      // For FilesUploadStep, all inputs become inputVariables
      // OutputVariables are typically empty or can be derived from inputs if needed
      inputVariables.putAll(variablesMap);
      // Note: outputVariables can be populated separately if there's a way to identify outputs
      // For now, keeping it empty as per v0 FilesUploadStep behavior
    }

    return FilesUploadStepParameters.infoBuilder()
        .inputVariables(inputVariables)
        .outputVariables(outputVariables)
        .build();
  }

  @Override
  protected StepType getStepType() {
    return UPLOAD_STEP_TYPE;
  }
}
