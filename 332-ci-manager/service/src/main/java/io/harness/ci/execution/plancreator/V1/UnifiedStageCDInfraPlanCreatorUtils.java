/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.INFRA_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.INFRA_NODE_NAME;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENVIRONMENT_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_ID;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_REF;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.INFRA_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_ENVIRONMENT;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_SERVICE;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addExpressionParameter;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addMapParameter;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.v1.InfrastructureConstants.INFRASTRUCTURE_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStep;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStepParameters;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStepParameters.UnifiedCDInfraStepParametersBuilder;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class UnifiedStageCDInfraPlanCreatorUtils {
  public static LinkedHashMap<String, PlanCreationResponse> addCDInfrastructureNode(KryoSerializer kryoSerializer,
      String nextNodeID, String infraNodeId, Map<String, Object> deployModuleNodesInfo, boolean isStepInsideRollback,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    UnifiedCDInfraStepParameters parameters = getInfraStepParameters(deployModuleNodesInfo, envVars);
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    PlanNode node = prepareInfraPlanNode(kryoSerializer, nextNodeID, infraNodeId, parameters, isStepInsideRollback);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return planCreationResponseMap;
  }

  private static PlanNode prepareInfraPlanNode(KryoSerializer kryoSerializer, String nextNodeID, String infraNodeId,
      UnifiedCDInfraStepParameters parameters, boolean isStepInsideRollback) {
    AdviserObtainment adviserObtainment = getAdviserObtainment(kryoSerializer, nextNodeID);
    return PlanNode.builder()
        .uuid(infraNodeId)
        .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
        .name(INFRA_NODE_NAME)
        .identifier(INFRA_NODE_ID)
        .stepType(UnifiedCDInfraStep.STEP_TYPE)
        .group(INFRASTRUCTURE_GROUP)
        .stepParameters(parameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .adviserObtainment(adviserObtainment)
        .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, List.of(adviserObtainment))
        .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, List.of(adviserObtainment))
        .whenCondition(RunInfoUtilsV1.getStepWhenCondition(null, isStepInsideRollback))
        .build();
  }

  private static AdviserObtainment getAdviserObtainment(KryoSerializer kryoSerializer, String nextNodeID) {
    return AdviserObtainment.newBuilder()
        .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
        .setParameters(ByteString.copyFrom(
            kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeID).build())))
        .build();
  }

  private static UnifiedCDInfraStepParameters getInfraStepParameters(
      Map<String, Object> deployModuleNodesInfo, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    UnifiedCDInfraStepParametersBuilder stepParametersBuilder = UnifiedCDInfraStepParameters.builder();

    boolean isMultiService = deployModuleNodesInfo.containsKey(MULTI_SERVICE)
        && String.valueOf(true).equals(deployModuleNodesInfo.get(MULTI_SERVICE));
    boolean isMultiEnv = deployModuleNodesInfo.containsKey(MULTI_ENVIRONMENT)
        && String.valueOf(true).equals(deployModuleNodesInfo.get(MULTI_ENVIRONMENT));

    if (isNotEmpty(deployModuleNodesInfo)) {
      addEnvAndInfraDetails(deployModuleNodesInfo, stepParametersBuilder, isMultiEnv);
      addServiceDetails(deployModuleNodesInfo, stepParametersBuilder, isMultiService);
      stepParametersBuilder.envVars(envVars);
    }
    return stepParametersBuilder.build();
  }

  private static void addEnvAndInfraDetails(Map<String, Object> deployModuleNodesInfo,
      UnifiedCDInfraStepParametersBuilder stepParametersBuilder, boolean isMultiEnv) {
    if (isMultiEnv) {
      addMultiEnvParameters(deployModuleNodesInfo, stepParametersBuilder);
    } else {
      addSingleEnvParameters(deployModuleNodesInfo, stepParametersBuilder);
    }
  }

  private static void addMultiEnvParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedCDInfraStepParametersBuilder stepParametersBuilder) {
    addExpressionParameter(deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT, MATRIX_ENVIRONMENT_REF, true,
        stepParametersBuilder::environmentRef);
    addExpressionParameter(deployModuleNodesInfo, YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, MATRIX_INFRA_ID, true,
        stepParametersBuilder::infraId);
    addExpressionParameter(
        deployModuleNodesInfo, INFRA_INPUTS, MATRIX_INFRA_INPUTS, false, stepParametersBuilder::infraInputs);
    addExpressionParameter(
        deployModuleNodesInfo, ENV_BRANCH_REF, MATRIX_ENV_BRANCH_REF, true, stepParametersBuilder::envBranchRef);
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT_GROUP, stepParametersBuilder::envGroupRef);
  }

  private static void addSingleEnvParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedCDInfraStepParametersBuilder stepParametersBuilder) {
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT, stepParametersBuilder::environmentRef);
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, stepParametersBuilder::infraId);
    addMapParameter(deployModuleNodesInfo, INFRA_INPUTS, stepParametersBuilder::infraInputs);
    addStringParameter(deployModuleNodesInfo, ENV_BRANCH_REF, stepParametersBuilder::envBranchRef);
  }

  private static void addServiceDetails(Map<String, Object> deployModuleNodesInfo,
      UnifiedCDInfraStepParametersBuilder stepParametersBuilder, boolean isMultiService) {
    if (isMultiService) {
      if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
        stepParametersBuilder.serviceRef(ParameterField.createExpressionField(true, MATRIX_SERVICE_REF, null, true));
      }
    } else {
      if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
        stepParametersBuilder.serviceRef(
            ParameterField.createValueField((String) deployModuleNodesInfo.get(YAMLFieldNameConstants.SERVICE)));
      }
    }
  }
}
