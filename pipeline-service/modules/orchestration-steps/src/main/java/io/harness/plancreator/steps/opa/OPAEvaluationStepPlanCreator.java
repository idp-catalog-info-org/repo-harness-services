/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.plancreator.PmsStepPlanCreatorUtils;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.steps.pluginstep.AbstractContainerStepPlanCreator;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.template.TemplateReferenceSummary;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationStepNode;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public class OPAEvaluationStepPlanCreator extends AbstractContainerStepPlanCreator<OPAEvaluationStepNode> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<OPAEvaluationStepNode> getFieldClass() {
    return OPAEvaluationStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(StepSpecTypeConstants.OPA_EVALUATION));
  }

  /**
   * Override createPlanForField to bypass ChildrenPlanCreator pattern when inside a step group with infrastructure.
   *
   * ROOT CAUSE FIX: AbstractContainerStepPlanCreator extends ChildrenPlanCreator, which creates:
   * - createPlanForChildrenNodes() → creates init + step nodes
   * - createPlanForParentNode() → creates STEP_GROUP wrapper
   *
   * When inside a step group with infrastructure:
   * - Step group already provides infrastructure (InitContainerV2Step)
   * - We should NOT create another STEP_GROUP wrapper (Level[9])
   * - Instead, create step node directly (like PMSStepPlanCreatorV2)
   *
   * When standalone:
   * - Use ChildrenPlanCreator pattern (init + step + wrapper)
   */
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, OPAEvaluationStepNode config) {
    // Check if we're inside a step group with infrastructure
    YamlNode stepGroupNode = YamlUtils.findParentNode(ctx.getCurrentField().getNode(), STEP_GROUP);
    YamlField stepGroupInfraField = stepGroupNode != null ? stepGroupNode.getField("stepGroupInfra") : null;
    boolean isInsideStepGroupWithInfra = stepGroupInfraField != null && stepGroupInfraField.getNode() != null
        && stepGroupInfraField.getNode().getCurrJsonNode() != null
        && !stepGroupInfraField.getNode().getCurrJsonNode().isNull();

    if (isInsideStepGroupWithInfra) {
      // Inside step group with infrastructure - bypass ChildrenPlanCreator, create step node directly
      // This matches PMSStepPlanCreatorV2 pattern (no wrapper STEP_GROUP)
      final boolean isStepInsideRollback =
          YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null;

      List<AdviserObtainment> adviserObtainmentFromMetaData =
          PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(ctx.getDependency(), kryoSerializer,
              ctx.getCurrentField(), false, InjectUtils.IsFlexibleTemplatesEnabled(ctx));
      Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
      Map<String, ByteString> metadataMap = new HashMap<>();
      config.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, config.getIdentifier()));
      config.setName(StrategyUtils.getIdentifierWithExpression(ctx, config.getName()));

      StepParameters stepParameters = getStepParameters(config, ctx);
      StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, config.getUuid(), config.getIdentifier(),
          config.getName(), dependenciesNodeMap, metadataMap, adviserObtainmentFromMetaData);

      String stepNodeId = "step-" + ctx.getCurrentField().getNode().getUuid();
      PlanNode stepPlanNode = createPlanForStep(stepNodeId, stepParameters, adviserObtainmentFromMetaData);

      PlanNodeBuilder stepPlanNodeBuilder =
          stepPlanNode.toBuilder()
              .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, config.getUuid()))
              .name(PmsStepPlanCreatorUtils.getName(config))
              .identifier(config.getIdentifier())
              .skipCondition(SkipInfoUtils.getSkipCondition(config.getSkipCondition()))
              .whenCondition(isStepInsideRollback ? RunInfoUtils.getRunConditionForRollback(config.getWhen())
                                                  : RunInfoUtils.getRunConditionForStep(config.getWhen()))
              .timeoutObtainment(
                  SdkTimeoutObtainment.builder()
                      .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                      .parameters(
                          AbsoluteSdkTimeoutTrackerParameters.builder().timeout(getTimeoutString(config)).build())
                      .build())
              .skipUnresolvedExpressionsCheck(config.getStepSpecType().skipUnresolvedExpressionsCheck())
              .expressionMode(config.getStepSpecType().getExpressionMode());

      boolean storeTemplateReferenceEnabled =
          ctx.getFeatureFlagValue(FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.toString());
      if (storeTemplateReferenceEnabled) {
        TemplateReferenceSummary templateReferenceSummary =
            PlanCreatorUtils.extractTemplateInfoFromYaml(ctx.getCurrentField());
        if (templateReferenceSummary != null) {
          stepPlanNodeBuilder.templateReferenceSummary(templateReferenceSummary);
        }
      }

      PlanNode finalStepPlanNode = stepPlanNodeBuilder.build();
      return PlanCreationResponse.builder()
          .planNode(finalStepPlanNode)
          .dependencies(
              DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                  .toBuilder()
                  .putDependencyMetadata(config.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                  .build())
          .build();
    } else {
      // Standalone - use ChildrenPlanCreator pattern (init + step + wrapper)
      return super.createPlanForField(ctx, config);
    }
  }

  /**
   * Helper method to get timeout string (similar to PMSStepPlanCreatorV2).
   */
  private ParameterField<String> getTimeoutString(OPAEvaluationStepNode stepElement) {
    ParameterField<Timeout> timeout = TimeoutUtils.getTimeout(stepElement.getTimeout());
    if (timeout.isExpression()) {
      return ParameterField.createExpressionField(
          true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
    } else {
      return ParameterField.createValueField(timeout.getValue().getTimeoutString());
    }
  }

  @Override
  public PlanNode createPlanForStep(
      String stepNodeId, StepParameters stepParameters, List<AdviserObtainment> adviserObtainments) {
    return PlanNode.builder()
        .uuid(stepNodeId)
        .name("OPA Evaluation")
        .identifier("OPAEvaluation")
        .stepType(StepType.newBuilder()
                      .setType(StepSpecTypeConstants.OPA_EVALUATION)
                      .setStepCategory(StepCategory.STEP)
                      .build())
        .adviserObtainments(adviserObtainments)
        .group(StepOutcomeGroup.STEP.name())
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(false)
        .skipGraphType(SkipType.NOOP)
        .build();
  }
}
