/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.AgentStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentStepPlanCreator extends CIPMSStepPlanCreatorV2<AgentStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.AGENT.getDisplayName());
  }

  @Override
  public Class<AgentStepNode> getFieldClass() {
    return AgentStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AgentStepNode stepElement) {
    log.error("Agent step '{}' was not expanded during YAML preprocessing. "
            + "This indicates that AgentStepPreprocessor did not run or failed for this step. "
            + "Agent steps must be expanded into StepGroups before plan creation.",
        stepElement.getIdentifier());
    throw new InvalidRequestException(String.format(
        "Agent step '%s' was not expanded during preprocessing. Please check pipeline-service logs for errors.",
        stepElement.getIdentifier()));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
