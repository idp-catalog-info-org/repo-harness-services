
/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.ARTIFACTS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.ARTIFACTS_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.CONFIG_FILES_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.CONFIG_FILES_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFESTS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFESTS_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFEST_SECTION_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.MANIFEST_SECTION_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.POST_FETCH_FILES_HOOKS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.POST_FETCH_FILES_HOOKS_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.PRE_FETCH_FILES_HOOKS_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.PRE_FETCH_FILES_HOOKS_NODE_NAME;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.SERVICE_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.SERVICE_NODE_NAME;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENVIRONMENT_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENV_OVERRIDES_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_ID;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SVC_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SVC_OVERRIDES_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.ENV_OVERRIDES_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.INFRA_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_ENVIRONMENT;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_SERVICE;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SERVICE_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SVC_OVERRIDES_INPUTS;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addExpressionParameter;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addMapParameter;
import static io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils.addStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.commonconstants.CdStepParametersInfoConstants;
import io.harness.ci.states.V1.cd.ArtifactsStep;
import io.harness.ci.states.V1.cd.ConfigFilesStep;
import io.harness.ci.states.V1.cd.ManifestsStep;
import io.harness.ci.states.V1.cd.ServiceHooksStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStepParameters;
import io.harness.ci.states.V1.cd.UnifiedServiceStepParameters.UnifiedServiceStepParametersBuilder;
import io.harness.data.structure.UUIDGenerator;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.section.chain.SectionChainStep;
import io.harness.steps.section.chain.SectionChainStepParameters;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class UnifiedStageServicePlanCreatorUtils {
  public static LinkedHashMap<String, PlanCreationResponse> addServiceNode(KryoSerializer kryoSerializer,
      String nextNodeID, String serviceNodeID, Map<String, Object> deployModuleNodeEntitiesIDs,
      boolean isStepInsideRollback, ParameterField<Map<String, ParameterField<JsonNode>>> envVars,
      boolean serviceHooksEnabled) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceHooksEnabled);
    UnifiedServiceStepParameters stepParameters =
        prepareServiceStepParameters(deployModuleNodeEntitiesIDs, childrenNodeIds, envVars);
    return createServicePlanNode(
        kryoSerializer, serviceNodeID, nextNodeID, planCreationResponseMap, stepParameters, isStepInsideRollback);
  }

  private static LinkedHashMap<String, PlanCreationResponse> createServicePlanNode(KryoSerializer kryoSerializer,
      String serviceNodeID, String nextNodeID, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      UnifiedServiceStepParameters stepParameters, boolean isStepInsideRollback) {
    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceNodeID)
            .stepType(UnifiedServiceStep.STEP_TYPE)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .name(SERVICE_NODE_NAME)
            .identifier(SERVICE_NODE_ID)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeID).build())))
                    .build())
            .skipExpressionChain(true)
            .whenCondition(RunInfoUtilsV1.getStepWhenCondition(null, isStepInsideRollback))
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return planCreationResponseMap;
  }

  private static UnifiedServiceStepParameters prepareServiceStepParameters(Map<String, Object> deployModuleNodesInfo,
      List<String> childrenNodeIds, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    final UnifiedServiceStepParametersBuilder stepParametersBuilder =
        UnifiedServiceStepParameters.builder().childrenNodeIds(childrenNodeIds);
    if (isNotEmpty(deployModuleNodesInfo)) {
      if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
        boolean isMultiService = deployModuleNodesInfo.containsKey(MULTI_SERVICE)
            && String.valueOf(true).equals(deployModuleNodesInfo.get(MULTI_SERVICE));
        boolean isMultiEnv = deployModuleNodesInfo.containsKey(MULTI_ENVIRONMENT)
            && String.valueOf(true).equals(deployModuleNodesInfo.get(MULTI_ENVIRONMENT));

        addServiceDetails(deployModuleNodesInfo, stepParametersBuilder, isMultiService);
        addEnvAndInfraDetails(deployModuleNodesInfo, stepParametersBuilder, isMultiEnv);

        if (deployModuleNodesInfo.get(CdStepParametersInfoConstants.SERVICE_TYPE) instanceof String serviceType) {
          stepParametersBuilder.serviceType(serviceType);
        }
      }

      stepParametersBuilder.envVars(envVars);
    }
    return stepParametersBuilder.build();
  }

  private static void addServiceDetails(Map<String, Object> deployModuleNodesInfo,
      UnifiedServiceStepParametersBuilder stepParametersBuilder, boolean isMultiService) {
    if (isMultiService) {
      addMultiServiceParameters(deployModuleNodesInfo, stepParametersBuilder);
    } else {
      addSingleServiceParameters(deployModuleNodesInfo, stepParametersBuilder);
    }
  }

  private static void addMultiServiceParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedServiceStepParametersBuilder stepParametersBuilder) {
    addExpressionParameter(deployModuleNodesInfo, YAMLFieldNameConstants.SERVICE, MATRIX_SERVICE_REF, true,
        stepParametersBuilder::serviceRef);
    addExpressionParameter(
        deployModuleNodesInfo, SERVICE_INPUTS, MATRIX_SERVICE_INPUTS, false, stepParametersBuilder::serviceInputs);
    addExpressionParameter(deployModuleNodesInfo, CdStepParametersInfoConstants.SVC_BRANCH_REF, MATRIX_SVC_BRANCH_REF,
        true, stepParametersBuilder::branch);
  }

  private static void addSingleServiceParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedServiceStepParametersBuilder stepParametersBuilder) {
    addStringParameter(deployModuleNodesInfo, YAMLFieldNameConstants.SERVICE, stepParametersBuilder::serviceRef);
    addStringParameter(
        deployModuleNodesInfo, CdStepParametersInfoConstants.SVC_BRANCH_REF, stepParametersBuilder::branch);
    addMapParameter(deployModuleNodesInfo, SERVICE_INPUTS, stepParametersBuilder::serviceInputs);
  }

  private static void addEnvAndInfraDetails(Map<String, Object> deployModuleNodesInfo,
      UnifiedServiceStepParametersBuilder stepParametersBuilder, boolean isMultiEnv) {
    if (isMultiEnv) {
      addMultiEnvParameters(deployModuleNodesInfo, stepParametersBuilder);
    } else {
      addSingleEnvParameters(deployModuleNodesInfo, stepParametersBuilder);
    }
  }

  private static void addMultiEnvParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedServiceStepParametersBuilder stepParametersBuilder) {
    addExpressionParameter(deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT, MATRIX_ENVIRONMENT_REF, true,
        stepParametersBuilder::environmentRef);
    addExpressionParameter(deployModuleNodesInfo, YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, MATRIX_INFRA_ID, true,
        stepParametersBuilder::infraId);
    addExpressionParameter(
        deployModuleNodesInfo, INFRA_INPUTS, MATRIX_INFRA_INPUTS, false, stepParametersBuilder::infraInputs);
    addExpressionParameter(deployModuleNodesInfo, ENV_OVERRIDES_INPUTS, MATRIX_ENV_OVERRIDES_INPUTS, false,
        stepParametersBuilder::envOverridesInputs);
    addExpressionParameter(deployModuleNodesInfo, SVC_OVERRIDES_INPUTS, MATRIX_SVC_OVERRIDES_INPUTS, false,
        stepParametersBuilder::svcOverridesInputs);
    addExpressionParameter(
        deployModuleNodesInfo, ENV_BRANCH_REF, MATRIX_ENV_BRANCH_REF, true, stepParametersBuilder::envBranchRef);
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT_GROUP, stepParametersBuilder::envGroupRef);
  }

  private static void addSingleEnvParameters(
      Map<String, Object> deployModuleNodesInfo, UnifiedServiceStepParametersBuilder stepParametersBuilder) {
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.ENVIRONMENT, stepParametersBuilder::environmentRef);
    addStringParameter(
        deployModuleNodesInfo, YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, stepParametersBuilder::infraId);
    addMapParameter(deployModuleNodesInfo, INFRA_INPUTS, stepParametersBuilder::infraInputs);
    addMapParameter(deployModuleNodesInfo, ENV_OVERRIDES_INPUTS, stepParametersBuilder::envOverridesInputs);
    addMapParameter(deployModuleNodesInfo, SVC_OVERRIDES_INPUTS, stepParametersBuilder::svcOverridesInputs);
    addStringParameter(deployModuleNodesInfo, ENV_BRANCH_REF, stepParametersBuilder::envBranchRef);
  }

  private static List<String> addChildrenNodes(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, boolean serviceHooksEnabled) {
    final List<String> nodeIds = new ArrayList<>();
    if (serviceHooksEnabled) {
      addManifestSectionNode(planCreationResponseMap, nodeIds);
    } else {
      PlanNode manifestsNode = getManifestsNode();
      planCreationResponseMap.put(
          manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());
      nodeIds.add(manifestsNode.getUuid());
    }
    addArtifactsNode(planCreationResponseMap, nodeIds);
    addConfigFilesNode(planCreationResponseMap, nodeIds);
    return nodeIds;
  }

  private static void addConfigFilesNode(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, List<String> nodeIds) {
    PlanNode configFilesNode = getConfigFilesNode();
    planCreationResponseMap.put(
        configFilesNode.getUuid(), PlanCreationResponse.builder().planNode(configFilesNode).build());
    nodeIds.add(configFilesNode.getUuid());
  }

  private static void addArtifactsNode(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, List<String> nodeIds) {
    PlanNode artifactNode = getArtifactsNode();
    planCreationResponseMap.put(artifactNode.getUuid(), PlanCreationResponse.builder().planNode(artifactNode).build());
    nodeIds.add(artifactNode.getUuid());
  }

  private static void addManifestSectionNode(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, List<String> nodeIds) {
    PlanNode preHooksNode = getPreFetchFilesHooksNode();
    PlanNode manifestsNode = getManifestsNode();
    PlanNode postHooksNode = getPostFetchFilesHooksNode();

    planCreationResponseMap.put(preHooksNode.getUuid(), PlanCreationResponse.builder().planNode(preHooksNode).build());
    planCreationResponseMap.put(
        manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());
    planCreationResponseMap.put(
        postHooksNode.getUuid(), PlanCreationResponse.builder().planNode(postHooksNode).build());

    List<String> sectionChildIds = List.of(preHooksNode.getUuid(), manifestsNode.getUuid(), postHooksNode.getUuid());
    PlanNode manifestSectionNode = getManifestSectionNode(sectionChildIds);
    planCreationResponseMap.put(
        manifestSectionNode.getUuid(), PlanCreationResponse.builder().planNode(manifestSectionNode).build());
    nodeIds.add(manifestSectionNode.getUuid());
  }

  private static PlanNode getArtifactsNode() {
    return PlanNode.builder()
        .uuid("artifacts-" + UUIDGenerator.generateUuid())
        .stepType(ArtifactsStep.STEP_TYPE)
        .name(ARTIFACTS_NODE_NAME)
        .identifier(ARTIFACTS_NODE_ID)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private static PlanNode getConfigFilesNode() {
    return PlanNode.builder()
        .uuid("configFiles-" + UUIDGenerator.generateUuid())
        .stepType(ConfigFilesStep.STEP_TYPE)
        .name(CONFIG_FILES_NODE_NAME)
        .identifier(CONFIG_FILES_NODE_ID)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private static PlanNode getManifestSectionNode(List<String> childNodeIds) {
    return PlanNode.builder()
        .uuid("manifestSection-" + UUIDGenerator.generateUuid())
        .stepType(SectionChainStep.STEP_TYPE)
        .name(MANIFEST_SECTION_NODE_NAME)
        .identifier(MANIFEST_SECTION_NODE_ID)
        .stepParameters(SectionChainStepParameters.builder().childNodeIds(childNodeIds).build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private static PlanNode getPreFetchFilesHooksNode() {
    return PlanNode.builder()
        .uuid("preFetchFilesHooks-" + UUIDGenerator.generateUuid())
        .stepType(ServiceHooksStep.STEP_TYPE)
        .name(PRE_FETCH_FILES_HOOKS_NODE_NAME)
        .identifier(PRE_FETCH_FILES_HOOKS_NODE_ID)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private static PlanNode getManifestsNode() {
    return PlanNode.builder()
        .uuid("manifests-" + UUIDGenerator.generateUuid())
        .stepType(ManifestsStep.STEP_TYPE)
        .name(MANIFESTS_NODE_NAME)
        .identifier(MANIFESTS_NODE_ID)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC_CHAIN).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private static PlanNode getPostFetchFilesHooksNode() {
    return PlanNode.builder()
        .uuid("postFetchFilesHooks-" + UUIDGenerator.generateUuid())
        .stepType(ServiceHooksStep.STEP_TYPE)
        .name(POST_FETCH_FILES_HOOKS_NODE_NAME)
        .identifier(POST_FETCH_FILES_HOOKS_NODE_ID)
        .stepParameters(new EmptyStepParameters())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(true)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }
}