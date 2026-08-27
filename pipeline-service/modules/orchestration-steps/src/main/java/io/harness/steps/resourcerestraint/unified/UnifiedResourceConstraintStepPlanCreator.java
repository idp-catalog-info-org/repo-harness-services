/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.unified;

import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Set;

public class UnifiedResourceConstraintStepPlanCreator extends PMSStepPlanCreatorV2<UnifiedResourceConstraintStepNode> {
  @Override
  public Class<UnifiedResourceConstraintStepNode> getFieldClass() {
    return UnifiedResourceConstraintStepNode.class;
  }

  @Override
  public UnifiedResourceConstraintStepNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedResourceConstraintStepNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse resource constraint step yaml.", e);
    }
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.RESOURCE_CONSTRAINT);
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, UnifiedResourceConstraintStepNode rcStepNode) {
    StepParameters stepParameters = getStepParameters(ctx, rcStepNode);
    String nextId = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, ctx.getDependency());
    PlanNode resourceConstraintPlanNode =
        PlanNode.builder()
            .uuid(rcStepNode.getUuid())
            .name(rcStepNode.getName())
            .identifier(rcStepNode.getIdentifier())
            .stepType(rcStepNode.getStepSpecType().getStepType())
            .group(StepOutcomeGroup.STEP.name())
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(
                        FacilitatorType.newBuilder().setType(rcStepNode.getStepSpecType().getFacilitatorType()).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextId).build())))
                    .build())
            .whenCondition(RunInfoUtils.getRunConditionForStep(rcStepNode.getWhen()))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(
                        AbsoluteSdkTimeoutTrackerParameters.builder().timeout(getTimeoutString(rcStepNode)).build())
                    .build())
            .skipUnresolvedExpressionsCheck(rcStepNode.getStepSpecType().skipUnresolvedExpressionsCheck())
            .expressionMode(rcStepNode.getStepSpecType().getExpressionMode())
            .build();
    return PlanCreationResponse.builder().planNode(resourceConstraintPlanNode).build();
  }
}
