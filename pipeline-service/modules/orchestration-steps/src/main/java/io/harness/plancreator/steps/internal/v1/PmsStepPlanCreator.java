/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal.v1;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public abstract class PmsStepPlanCreator<T extends PmsAbstractStepNodeV1> implements PartialPlanCreator<T> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T stepNode) {
    final boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stepNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, stepNode.getName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stepNode.getId()))
            .stepType(stepNode.getSpec().getStepType())
            .group(StepOutcomeGroup.STEP.name())
            // TODO: send rollback parameters to this method which can be extracted from dependency
            .stepParameters(stepNode.getStepParameters(ctx, kryoSerializer))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(stepNode.getSpec().getFacilitatorType()).build())
                    .build())
            .whenCondition(RunInfoUtilsV1.getStepWhenCondition(stepNode.getWhen(), isStepInsideRollback))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                    .timeout(TimeoutUtils.getTimeoutParameterFieldString(stepNode.getTimeout()))
                                    .build())
                    .build())
            .skipUnresolvedExpressionsCheck(stepNode.getSpec().skipUnresolvedExpressionsCheck())
            .expressionMode(stepNode.getSpec().getExpressionMode());

    List<AdviserObtainment> adviserObtainmentList = PlanCreatorUtilsV1.getAdviserObtainmentsForStep(
        kryoSerializer, ctx.getDependency(), stepNode.getFailure() != null ? stepNode.getFailure().getValue() : null);
    if (ParameterField.isNull(stepNode.getStrategy())) {
      builder.adviserObtainments(adviserObtainmentList);
    }
    Map<String, HarnessValue> dependencyMetadata = StrategyUtilsV1.getStrategyFieldDependencyMetadataIfPresent(
        kryoSerializer, ctx, stepNode.getUuid(), dependenciesNodeMap, adviserObtainmentList);
    return PlanCreationResponse.builder()
        .planNode(builder.build())
        .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                          .toBuilder()
                          .putDependencyMetadata(stepNode.getUuid(),
                              Dependency.newBuilder()
                                  .setNodeMetadata(HarnessStruct.newBuilder().putAllData(dependencyMetadata).build())
                                  .build())
                          .build())
        .build();
  }

  public abstract Set<String> getSupportedStepTypes();

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    if (EmptyPredicate.isEmpty(stepTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(STEP, stepTypes);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}