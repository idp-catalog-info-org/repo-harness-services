/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MULTI_ENV_DEPLOYMENT;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MULTI_SERVICE_DEPLOYMENT;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.UnifiedMultiDeploymentMetadata;
import io.harness.beans.stages.UnifiedMultiDeploymentStepParameters;
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
@Singleton
public class UnifiedMultiDeploymentPlanCreatorHelper {
  @Inject private KryoSerializer kryoSerializer;

  public GraphLayoutResponse getMultiDeploymentGraphLayoutResponse(PlanCreationContext context,
      UnifiedStageNodeV1 stageNode, Map<String, GraphLayoutNode> stageYamlFieldMap, YamlField stageYamlField) {
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, context.getDependency());
    EdgeLayoutList edgeLayoutList;
    String planNodeId = UnifiedMultiDeploymentUtils.getStageNodeUuid(context, stageNode);
    String pipelineRollbackStageId = PlanCreatorUtilsV1.getPipelineRollbackStageId(context.getDependency());

    if (isEmpty(nextNodeUuid) || nextNodeUuid.equals(pipelineRollbackStageId)) {
      edgeLayoutList = EdgeLayoutList.newBuilder().addCurrentNodeChildren(planNodeId).build();
    } else {
      edgeLayoutList = EdgeLayoutList.newBuilder().addNextIds(nextNodeUuid).addCurrentNodeChildren(planNodeId).build();
    }

    stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(stageYamlField.getNode().getUuid())
            .setNodeType(StrategyType.MATRIX.name())
            .setName(stageYamlField.getNode().getName())
            .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
            .setNodeIdentifier(stageYamlField.getNode().getId())
            .setEdgeLayoutList(edgeLayoutList)
            .build());
    stageYamlFieldMap.put(planNodeId,
        GraphLayoutNode.newBuilder()
            .setNodeUUID(planNodeId)
            .setNodeType(stageYamlField.getNode().getType())
            .setName(stageNode.getName())
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(stageNode.getId())
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
            .build());
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  public void addMultiDeploymentDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      UnifiedStageNodeV1 stageNode, PlanCreationContext ctx) {
    String subType = getMultiDeploymentSubType(stageNode);

    UnifiedMultiDeploymentStepParameters stepParameters = getMultiDeploymentStepParameters(stageNode, ctx, subType);
    UnifiedMultiDeploymentMetadata metadata = getMultiDeploymentMetadata(stageNode, ctx, stepParameters);

    PlanNode planNode = getMultiDeploymentPlanNode(stepParameters, metadata);
    planCreationResponseMap.put(
        UUIDGenerator.generateUuid(), PlanCreationResponse.builder().planNode(planNode).build());
  }

  private static PlanNode getMultiDeploymentPlanNode(
      UnifiedMultiDeploymentStepParameters stepParameters, UnifiedMultiDeploymentMetadata metadata) {
    String childNodeId = stepParameters.getChildNodeId();
    String multiDeploymentNodeId = metadata.getMultiDeploymentNodeId();

    if (isEmpty(childNodeId) || isEmpty(multiDeploymentNodeId)) {
      log.error("Not found childNodeId and multiDeploymentNodeId while creating muiti deployment plan node.");
      throw new InvalidRequestException("Invalid use of strategy field. Please check");
    }

    return PlanNode.builder()
        .uuid(multiDeploymentNodeId)
        .identifier(metadata.getStrategyNodeIdentifier())
        .stepType(UnifiedMultiDeploymentSpawnerStep.STEP_TYPE)
        .group(StepOutcomeGroup.STRATEGY.name())
        .name(metadata.getStrategyNodeName())
        .stepParameters(stepParameters)
        .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(true)
        .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, metadata.getAdviserObtainments())
        .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, metadata.getAdviserObtainments())
        .adviserObtainments(metadata.getAdviserObtainments())
        .build();
  }

  private UnifiedMultiDeploymentMetadata getMultiDeploymentMetadata(
      UnifiedStageNodeV1 stageNode, PlanCreationContext ctx, UnifiedMultiDeploymentStepParameters stepParameters) {
    return UnifiedMultiDeploymentMetadata.builder()
        .multiDeploymentNodeId(ctx.getCurrentField().getNode().getUuid())
        .multiDeploymentStepParameters(stepParameters)
        .strategyNodeIdentifier(stageNode.getId())
        .strategyNodeName(stageNode.getName())
        .adviserObtainments(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency()))
        .build();
  }

  private static UnifiedMultiDeploymentStepParameters getMultiDeploymentStepParameters(
      UnifiedStageNodeV1 stageNode, PlanCreationContext ctx, String subType) {
    return UnifiedMultiDeploymentStepParameters.builder()
        .strategyType(StrategyType.MATRIX)
        .subType(subType)
        .services(stageNode.getService())
        .environments(stageNode.getEnvironment())
        .childNodeId(UnifiedMultiDeploymentUtils.getStageNodeUuid(ctx, stageNode))
        .build();
  }

  private String getMultiDeploymentSubType(UnifiedStageNodeV1 stageNode) {
    String subType;
    boolean isMultiEnvironment = UnifiedMultiDeploymentUtils.isMultiEnvironment(stageNode.getEnvironment());
    boolean isMultiService = UnifiedMultiDeploymentUtils.isMultiService(stageNode.getService());
    if (!isMultiEnvironment) {
      subType = MULTI_SERVICE_DEPLOYMENT;
    } else if (!isMultiService) {
      subType = MULTI_ENV_DEPLOYMENT;
    } else {
      subType = MULTI_SERVICE_ENV_DEPLOYMENT;
    }
    return subType;
  }
}
