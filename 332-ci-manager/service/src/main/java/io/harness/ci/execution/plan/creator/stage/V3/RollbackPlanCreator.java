/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.CombinedRollbackStep;
import io.harness.steps.rollback.InfraRollbackPMSPlanCreator;
import io.harness.steps.rollback.RollbackNode;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters.RollbackOptionalChildChainStepParametersBuilder;

import java.util.Collections;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class RollbackPlanCreator {
  public PlanCreationResponse createPlanForRollback(YamlField executionField, String stageNodeUuid, String stageName,
      Infrastructure infrastructure, KryoSerializer kryoSerializer, Map<String, Object> modulesImplicitNodesInfo,
      RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator, PlanCreationContext ctx) {
    YamlField executionStepsField = executionField.getNode().getField(YAMLFieldNameConstants.STEPS);

    if (executionStepsField == null || executionStepsField.getNode().asArray().size() == 0) {
      return PlanCreationResponse.builder().build();
    }
    RollbackOptionalChildChainStepParametersBuilder stepParametersBuilder =
        RollbackOptionalChildChainStepParameters.builder();

    // Infra rollback
    YamlField infraField = executionField.getNode().nextSiblingNodeFromParentObject("infrastructure");
    PlanCreationResponse infraRollbackPlan =
        InfraRollbackPMSPlanCreator.createInfraRollbackPlan(HarnessYamlVersion.V1, infraField);
    if (isNotEmpty(infraRollbackPlan.getNodes())) {
      String infraNodeFullIdentifier =
          YamlUtils.getQualifiedNameTillGivenField(infraField.getNode(), YAMLFieldNameConstants.STAGES);
      stepParametersBuilder.childNode(
          RollbackNode.builder()
              .nodeId(infraField.getNode().getUuid() + InfraRollbackPMSPlanCreator.INFRA_ROLLBACK_NODE_ID_SUFFIX)
              .dependentNodeIdentifier(infraNodeFullIdentifier)
              .build());
    } else {
      YamlField environmentField = executionField.getNode().nextSiblingNodeFromParentObject("environment");
      infraRollbackPlan =
          InfraRollbackPMSPlanCreator.createProvisionerRollbackPlan(HarnessYamlVersion.V1, environmentField);
      if (isNotEmpty(infraRollbackPlan.getNodes())) {
        String infraNodeFullIdentifier =
            YamlUtils.getQualifiedNameTillGivenField(environmentField.getNode(), YAMLFieldNameConstants.STAGES);
        stepParametersBuilder.childNode(RollbackNode.builder()
                                            .nodeId(environmentField.getNode().getUuid()
                                                + InfraRollbackPMSPlanCreator.INFRA_ROLLBACK_NODE_ID_SUFFIX)
                                            .dependentNodeIdentifier(infraNodeFullIdentifier)
                                            .build());
      }
    }

    // ExecutionRollback
    // Derive the stage's YAML identifier (the V1 "id" field) and thread it through so that
    // ExecutionRollbackUnifiedStagePlanCreator can produce a stageFqn that matches the original run's
    // NodeExecution.stageFqn for POST_EXECUTION_ROLLBACK only. Other rollback modes keep current behavior.
    String stageIdentifier = null;
    if (ctx.getCurrentField() != null && ctx.getCurrentField().getNode() != null
        && ctx.getCurrentField().getNode().getCurrJsonNode() != null
        && ctx.getCurrentField().getNode().getCurrJsonNode().get(YAMLFieldNameConstants.ID) != null) {
      stageIdentifier = ctx.getCurrentField().getNode().getCurrJsonNode().get(YAMLFieldNameConstants.ID).asText();
    }

    PlanCreationResponse executionRollbackPlanNode =
        ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(executionField.getNode(),
            stageNodeUuid, stageName, stageIdentifier, infrastructure, kryoSerializer, modulesImplicitNodesInfo,
            rollbackStepsPMSPlanCreator, ctx);
    if (EmptyPredicate.isNotEmpty(executionRollbackPlanNode.getNodes())) {
      String executionRollbackUuid =
          executionStepsField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_EXECUTION_NODE_ID_SUFFIX;
      String executionNodeFullIdentifier =
          YamlUtils.getQualifiedNameTillGivenField(executionField.getNode(), YAMLFieldNameConstants.STAGES);
      stepParametersBuilder.childNode(RollbackNode.builder()
                                          .nodeId(executionRollbackUuid)
                                          .dependentNodeIdentifier(executionNodeFullIdentifier)
                                          .build());
    }

    String combinedRollbackNodeUuid = stageNodeUuid + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    PlanNode deploymentStageRollbackNode =
        PlanNode.builder()
            .uuid(combinedRollbackNodeUuid)
            .name(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)
            .identifier(YAMLFieldNameConstants.ROLLBACK_STEPS)
            .stepType(CombinedRollbackStep.STEP_TYPE)
            .stepParameters(stepParametersBuilder.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_NODE)
            .build();

    PlanCreationResponse finalResponse =
        PlanCreationResponse.builder()
            .node(deploymentStageRollbackNode.getUuid(), deploymentStageRollbackNode)
            .preservedNodesInRollbackMode(Collections.singletonList(combinedRollbackNodeUuid))
            .build();
    finalResponse.merge(executionRollbackPlanNode);
    finalResponse.merge(infraRollbackPlan);

    return finalResponse;
  }
}
