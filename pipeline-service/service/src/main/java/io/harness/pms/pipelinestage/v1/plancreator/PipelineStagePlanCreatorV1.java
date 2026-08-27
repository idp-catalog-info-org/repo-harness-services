/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.plancreator;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.PmsStepPlanCreatorUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.steps.pipelinestage.PipelineStageOutputs;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineStagePlanCreatorV1 implements PartialPlanCreator<PipelineStageNode> {
  @Inject private PipelineStageHelper pipelineStageHelper;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject KryoSerializer kryoSerializer;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineEnforcementService pipelineEnforcementService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public PipelineStageNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), PipelineStageNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException(
          "Unable to parse pipeline stage yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  public PipelineStageStepParameters getStepParameter(PipelineStageConfig config, YamlField pipelineInputs,
      String stageNodeId, String childPipelineVersion, String accountIdentifier) {
    return PipelineStageStepParameters.builder()
        .pipeline(config.getPipeline())
        .org(config.getOrg())
        .project(config.getProject())
        .stageNodeId(stageNodeId)
        .inputSetReferences(config.getInputSetReferences())
        .outputs(ParameterField.createValueField(PipelineStageOutputs.getMapOfString(config.getOutputs())))
        .pipelineInputsJsonNode(pipelineStageHelper.getInputSetJsonNode(pipelineInputs, childPipelineVersion))
        .build();
  }

  public void setSourcePrincipal(PlanCreationContext ctx) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipal(
        ctx.getAccountIdentifier(), ctx.getPrincipalInfo(), ctx.getTriggerInfo().getTriggeredBy());
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineStageNode stageNode) {
    PipelineStageConfig configNode = stageNode.getPipelineStageConfig();
    if (configNode == null) {
      throw new InvalidRequestException("Pipeline Stage Yaml does not contain spec");
    }

    pipelineEnforcementService.validatePipelineChainingEnforcement(ctx.getAccountIdentifier());

    // Principal is added to fetch Git Entity
    setSourcePrincipal(ctx);
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(ctx.getAccountIdentifier(), configNode.getOrg(), configNode.getProject());
    Optional<PipelineEntity> childPipelineEntity =
        pmsPipelineService.getPipeline(ctx.getAccountIdentifier(), configNode.getOrg(), configNode.getProject(),
            configNode.getPipeline(), false, false, false, true, scopeInfo, isParentIdQueryingEnabled);

    if (childPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("Child pipeline does not exists %s ", configNode.getPipeline()));
    }

    String parentPipelineIdentifier = ctx.getPipelineIdentifier();

    pipelineStageHelper.validateNestedChainedPipeline(
        childPipelineEntity.get(), stageNode.getName(), parentPipelineIdentifier, scopeInfo);
    // Validate the failure-strategies once failure-strategies are supported with simplified YAML.
    //    pipelineStageHelper.validateFailureStrategy(stageNode.getFailureStrategies());

    // TODO: remove this to enable Strategy support for Pipeline Stage
    if (ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY) != null) {
      throw new InvalidRequestException(
          String.format("Strategy is not supported for Pipeline stage %s", stageNode.getIdentifier()));
    }

    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();

    // Here planNodeId is used to support strategy. Same node id will be passed to child execution for navigation to
    // parent execution
    String planNodeId = StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid());

    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(planNodeId)
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepCategory.STAGE.name())
            .stepType(PipelineStageStep.STEP_TYPE)
            .stepParameters(getStepParameter(configNode,
                ctx.getCurrentField()
                    .getNode()
                    .getField(YAMLFieldNameConstants.SPEC)
                    .getNode()
                    .getField(YAMLFieldNameConstants.INPUTS),
                planNodeId, childPipelineEntity.get().getHarnessVersion(), ctx.getAccountIdentifier()))
            // TODO: Add when condition once v1 stage node is created
            //            .whenCondition(RunInfoUtilsV1.getStageWhenCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .adviserObtainments(PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(
                kryoSerializer, ctx.getCurrentField(), true, false));
    if (EmptyPredicate.isNotEmpty(ctx.getExecutionInputTemplate())) {
      builder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    // If strategy present then don't add advisers. Strategy node will take care of running the stage nodes.
    if (stageNode.getStrategy() == null) {
      builder.adviserObtainments(getAdviserObtainments(ctx.getDependency()));
    }

    // This will be empty till we enable strategy support for Pipeline Stage
    Map<String, HarnessValue> dependencyMetadata =
        getDependencyMetadataForStrategy(ctx, stageNode, dependenciesNodeMap);

    return PlanCreationResponse.builder()
        .graphLayoutResponse(getLayoutNodeInfo(ctx))
        .planNode(builder.build())
        .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                          .toBuilder()
                          .putDependencyMetadata(stageNode.getUuid(),
                              Dependency.newBuilder()
                                  .setNodeMetadata(HarnessStruct.newBuilder().putAllData(dependencyMetadata).build())
                                  .build())
                          .build())
        .build();
  }

  private Map<String, HarnessValue> getDependencyMetadataForStrategy(
      PlanCreationContext ctx, PipelineStageNode stageNode, Map<String, YamlField> dependenciesNodeMap) {
    return StrategyUtilsV1.getStrategyFieldDependencyMetadataIfPresent(
        kryoSerializer, ctx, stageNode.getUuid(), dependenciesNodeMap, getAdviserObtainments(ctx.getDependency()));
  }

  private List<AdviserObtainment> getAdviserObtainments(Dependency dependency) {
    return PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, dependency);
  }

  // This is for graph view of strategy execution
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, context.getDependency());
    if (StrategyUtilsV1.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtilsV1.modifyStageLayoutNodeGraph(stageYamlField, nextNodeUuid);
    } else {
      EdgeLayoutList edgeLayoutList = EdgeLayoutList.newBuilder().build();
      if (EmptyPredicate.isNotEmpty(nextNodeUuid)) {
        // Add nextChildId in edgeLayoutList if present.
        edgeLayoutList = EdgeLayoutList.newBuilder().addNextIds(nextNodeUuid).build();
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType(stageYamlField.getNode().getType())
              .setName(stageYamlField.getId())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(stageYamlField.getId())
              .setEdgeLayoutList(edgeLayoutList)
              .build());
    }

    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}
