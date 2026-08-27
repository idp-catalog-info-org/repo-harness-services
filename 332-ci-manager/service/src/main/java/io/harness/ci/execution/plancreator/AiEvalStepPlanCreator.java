/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.AiEvalStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.AI)
public class AiEvalStepPlanCreator extends CIPMSStepPlanCreatorV2<AiEvalStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.AI_EVAL.getDisplayName());
  }

  @Override
  public Class<AiEvalStepNode> getFieldClass() {
    return AiEvalStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AiEvalStepNode stepElement) {
    log.error("AiEval step '{}' was not expanded during YAML preprocessing. "
            + "This indicates that AiEvalStepPreprocessor did not run or failed for this step. "
            + "AiEval steps must be expanded into StepGroups before plan creation.",
        stepElement.getIdentifier());
    throw new InvalidRequestException(String.format("AiEval step '%s' was not expanded during preprocessing. "
            + "Ensure feature flag AI_ENABLE_EVAL_STEP is enabled. Check pipeline-service logs for errors.",
        stepElement.getIdentifier()));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
