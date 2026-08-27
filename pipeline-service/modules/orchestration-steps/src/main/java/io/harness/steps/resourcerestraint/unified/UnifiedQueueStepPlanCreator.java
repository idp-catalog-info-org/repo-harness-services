/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.unified;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.steps.StepSpecTypeConstants.QUEUE_STEP_TYPE;

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

import java.io.IOException;
import java.util.Set;

@OwnedBy(CI)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class UnifiedQueueStepPlanCreator extends UnifiedPmsAbstractStepPlanCreator<UnifiedQueueStepNode> {
  @Override
  public Class<UnifiedQueueStepNode> getFieldClass() {
    return UnifiedQueueStepNode.class;
  }

  @Override
  public UnifiedQueueStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedQueueStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse queue step yaml.", e);
    }
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Set.of(YAMLFieldNameConstants.QUEUE_V1);
  }

  @Override
  protected SpecParameters getSpec(UnifiedPmsAbstractStepNode stepNode) {
    return ((UnifiedQueueStepNode) stepNode).getUnifiedQueueStepInfo().getSpecParameters();
  }

  @Override
  protected StepType getStepType() {
    return QUEUE_STEP_TYPE;
  }
}
