/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MODULE_IMPLICIT_NODES_INFO;
import static io.harness.ci.execution.integrationstage.V1.ModuleSpecificPlanHandlers.getStageChildrenEntitiesInfo;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.plan.creation.PlanCreatorConstants.IS_STEP_INSIDE_ROLLBACK;
import static io.harness.pms.plan.creation.PlanCreatorConstants.STAGE_ID;
import static io.harness.pms.plan.creation.PlanCreatorConstants.STAGE_PREFIX_FQN;
import static io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.contracts.plan.RollbackModeBehaviour;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.RollbackNode;
import io.harness.steps.rollback.RollbackOptionalChildChainStep;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters.RollbackOptionalChildChainStepParametersBuilder;

import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ExecutionRollbackUnifiedStagePlanCreator {
  public static final String STAGE_IDENTIFIER = "stageIdentifier";
  public static final String STAGE_NAME = "stageName";
  public static final String INFRASTRUCTURE = "infrastructure";

  public static PlanCreationResponse createExecutionRollbackPlanNode(YamlNode executionField, String stageNodeUuid,
      String stageName, String stageIdentifier, Infrastructure infrastructure, KryoSerializer kryoSerializer,
      Map<String, Object> moduleImplicitNodesInfo, RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator,
      PlanCreationContext ctx) {
    if (executionField == null) {
      return PlanCreationResponse.builder().build();
    }
    Map<String, YamlField> dependencies = new HashMap<>();
    YamlField executionStepsField = executionField.getField(YAMLFieldNameConstants.STEPS);

    if (executionStepsField == null || executionStepsField.getNode().asArray().size() == 0) {
      return PlanCreationResponse.builder().build();
    }
    RollbackOptionalChildChainStepParametersBuilder stepParametersBuilder =
        RollbackOptionalChildChainStepParameters.builder();

    String executionNodeFullIdentifier =
        YamlUtils.getQualifiedNameTillGivenField(executionField, YAMLFieldNameConstants.STAGES);
    YamlField executionRollbackSteps = executionField.getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);

    boolean hasStageRollback = executionRollbackSteps != null && executionRollbackSteps.getNode() != null
        && executionRollbackSteps.getNode().asArray().size() > 0;

    PlanCreationResponse sgOnlyRollbackResponse = null;
    if (hasStageRollback) {
      // Adding dependencies
      dependencies.put(
          executionRollbackSteps.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX,
          executionRollbackSteps);
      stepParametersBuilder.childNode(RollbackNode.builder()
                                          .nodeId(executionRollbackSteps.getNode().getUuid()
                                              + NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX)
                                          .dependentNodeIdentifier(executionNodeFullIdentifier)
                                          .build());
    } else {
      // Step Group Rollback where only rollback at step group level is present. No rollback node at stage level.
      // List<List<RollbackNode>>: inner list size == 1 → sequential, size > 1 → parallel
      List<List<RollbackNode>> sgRollbackGroups =
          StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(executionStepsField);
      if (isNotEmpty(sgRollbackGroups)) {
        sgOnlyRollbackResponse = rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(ctx, stageNodeUuid, stageName,
            infrastructure, moduleImplicitNodesInfo, sgRollbackGroups, executionStepsField);
        if (sgOnlyRollbackResponse != null && sgOnlyRollbackResponse.getPlanNode() != null) {
          stepParametersBuilder.childNode(RollbackNode.builder()
                                              .nodeId(sgOnlyRollbackResponse.getPlanNode().getUuid())
                                              .dependentNodeIdentifier(executionNodeFullIdentifier)
                                              .build());
        }
      }
    }

    if (EmptyPredicate.isEmpty(stepParametersBuilder.build().getChildNodes())) {
      return PlanCreationResponse.builder().build();
    }

    String executionRollbackNodeUuid =
        executionStepsField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_EXECUTION_NODE_ID_SUFFIX;
    PlanNode unifiedStageRollbackNode =
        PlanNode.builder()
            .uuid(executionRollbackNodeUuid)
            .name(NGCommonUtilPlanCreationConstants.EXECUTION_NODE_NAME + " "
                + NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)
            .identifier(YAMLFieldNameConstants.ROLLBACK_STEPS)
            .stepType(RollbackOptionalChildChainStep.STEP_TYPE)
            .stepParameters(stepParametersBuilder.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .skipExpressionChain(true)
            .build();
    ListValue stageChildren = getStageChildrenEntitiesInfo(moduleImplicitNodesInfo);

    PlanCreationResponseBuilder responseBuilder =
        PlanCreationResponse.builder()
            .node(unifiedStageRollbackNode.getUuid(), unifiedStageRollbackNode)
            .preservedNodesInRollbackMode(Collections.singletonList(executionRollbackNodeUuid));

    if (hasStageRollback) {
      responseBuilder.dependencies(toDependenciesProtoWithRollbackModeV1(stageNodeUuid, stageName, stageIdentifier,
          infrastructure, kryoSerializer, executionField.getUuid(), dependencies, RollbackModeBehaviour.PRESERVE,
          stageChildren, moduleImplicitNodesInfo, ctx.getExecutionMode()));
    }
    PlanCreationResponse response = responseBuilder.build();
    if (sgOnlyRollbackResponse != null) {
      response.merge(sgOnlyRollbackResponse);
    }
    return response;
  }

  private static Dependencies toDependenciesProtoWithRollbackModeV1(String stageNodeUuid, String stageName,
      String stageIdentifier, Infrastructure infrastructure, KryoSerializer kryoSerializer, String stageUuid,
      Map<String, YamlField> fields, RollbackModeBehaviour behaviour, ListValue stageChildren,
      Map<String, Object> moduleImplicitNodesInfo, ExecutionMode executionMode) {
    if (EmptyPredicate.isEmpty(fields)) {
      return Dependencies.newBuilder().build();
    }
    Map<String, HarnessValue> nodeMetadataMap = new HashMap<>();
    nodeMetadataMap.put(STAGE_IDENTIFIER, HarnessValue.newBuilder().setStringValue(stageNodeUuid).build());
    nodeMetadataMap.put(STAGE_NAME, HarnessValue.newBuilder().setStringValue(stageName).build());
    nodeMetadataMap.put(INFRASTRUCTURE,
        HarnessValue.newBuilder().setBytesValue(ByteString.copyFrom(kryoSerializer.asBytes(infrastructure))).build());
    if (isNotEmpty(moduleImplicitNodesInfo)) {
      ByteString moduleImplicitNodesInfoBytes = ByteString.copyFrom(kryoSerializer.asBytes(moduleImplicitNodesInfo));
      nodeMetadataMap.put(
          MODULE_IMPLICIT_NODES_INFO, HarnessValue.newBuilder().setBytesValue(moduleImplicitNodesInfoBytes).build());
    }

    // PPR-only: use the YAML stage identifier so freshly-created rollback nodes (init/infra/SG-chain)
    // get a stageFqn matching the previous run's NodeExecution stageFqn. Required because
    // RollbackModeExecutionHelper.shouldPreserveNode for POST_EXECUTION_ROLLBACK requires both
    // (a) UUID in preserve list, AND (b) node.stageFqn ∈ rollbackStageIds.
    // For every other mode (NORMAL stage rollback, PIPELINE_ROLLBACK, ...) keep the existing
    // UUID-based value to guarantee no behavioral change.
    String stageFqnPart =
        (ExecutionMode.POST_EXECUTION_ROLLBACK.equals(executionMode) && EmptyPredicate.isNotEmpty(stageIdentifier))
        ? stageIdentifier
        : stageNodeUuid;

    Dependencies.Builder builder = Dependencies.newBuilder();
    fields.forEach((k, v) -> {
      builder.putDependencies(k, v.getYamlPath());
      builder.putDependencyMetadata(k,
          Dependency.newBuilder()
              .setRollbackModeBehaviour(behaviour)
              .setParentInfo(HarnessStruct.newBuilder()
                                 .putData(IS_STEP_INSIDE_ROLLBACK, HarnessValue.newBuilder().setBoolValue(true).build())
                                 .putData(STAGE_ID, HarnessValue.newBuilder().setStringValue(stageUuid).build())
                                 .putData(PlanCreatorConstants.STAGE_CHILDREN,
                                     HarnessValue.newBuilder().setListValue(stageChildren).build())
                                 .putData(PlanCreatorConstants.STAGE_FQN,
                                     HarnessValue.newBuilder().setStringValue(STAGE_PREFIX_FQN + stageFqnPart).build())
                                 .build())
              .setNodeMetadata(HarnessStruct.newBuilder().putAllData(nodeMetadataMap).build())
              .build());
    });
    return builder.build();
  }
}
