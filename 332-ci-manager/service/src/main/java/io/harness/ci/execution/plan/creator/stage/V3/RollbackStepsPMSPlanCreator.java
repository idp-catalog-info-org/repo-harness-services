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
import io.harness.cd.beans.DeployPlanCreationResult;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.InitializeStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.UnifiedStageCDInfraPlanCreatorUtils;
import io.harness.ci.plan.creator.step.v1.PlanCreatorEnvVarHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.RollbackNode;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters.RollbackOptionalChildChainStepParametersBuilder;
import io.harness.steps.rollback.StepGroupRollbackChainStep;
import io.harness.utils.execution.ExecutionModeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is used to create rollback plan for steps inside rollback section of execution.
 * Example :
 * execution:
 *    steps:
 *    rollbackSteps: // This section
 */
@OwnedBy(HarnessTeam.CDC)
public class RollbackStepsPMSPlanCreator implements PartialPlanCreator<YamlField> {
  @Inject InitializeStepPlanCreatorV1 initializeStepPlanCreatorV1;
  @Inject KryoSerializer kryoSerializer;
  @Inject CIPlanCreatorUtils ciPlanCreatorUtils;
  @Inject PlanCreatorEnvVarHelper planCreatorEnvVarHelper;

  @Override
  public YamlField getFieldObject(YamlField field) {
    return field;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.ROLLBACK_STEPS_V1, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, YamlField rollbackStepsField) {
    if (rollbackStepsField == null || rollbackStepsField.getNode().asArray().size() == 0) {
      return PlanCreationResponse.builder().build();
    }
    List<YamlField> stepsArrayFields = getStepYamlFields(rollbackStepsField);
    if (stepsArrayFields.isEmpty()) {
      return PlanCreationResponse.builder().build();
    }
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        stepsArrayFields.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    PlanCreationResponseBuilder planCreationResponseBuilder = PlanCreationResponse.builder();
    Optional<Object> optionalInfrastructure =
        ciPlanCreatorUtils.getDeserializedObjectFromDependency(ctx.getDependency(), "infrastructure");
    Infrastructure infrastructure = null;
    if (!optionalInfrastructure.isEmpty()) {
      infrastructure = (Infrastructure) optionalInfrastructure.get();
    }
    HarnessValue value = PlanCreatorUtilsV1.getNodeMetadataValueFromDependency(ctx.getDependency(), "stageIdentifier");
    String stageId = null;
    if (value != null) {
      stageId = value.getStringValue();
    }
    String stageName = null;
    value = PlanCreatorUtilsV1.getNodeMetadataValueFromDependency(ctx.getDependency(), "stageName");
    if (value != null) {
      stageName = value.getStringValue();
    }

    Map<String, Object> modulesImplicitNodesInfo = ciPlanCreatorUtils.getModulesImplicitNodesInfo(ctx);
    Map<String, Object> deployModuleNodesInfo = getDeployModuleNodesInfo(modulesImplicitNodesInfo);

    // ──── Step Group Rollback Chain ────
    // Inserting Step Group Rollback Chain to its sibling steps as step group rollback is child of stage rollback too.
    // Init → (Infra) → SG Rollback Chain → first user-defined rollback step
    // List<List<RollbackNode>>: inner list size == 1 → sequential, size > 1 → parallel
    List<List<RollbackNode>> sgRollbackGroups = collectSgRollbackGroupsFromYaml(rollbackStepsField);
    String sgRollbackChainNodeId = null;
    PlanCreationResponse sgRollbackChainPlan = null;
    if (isNotEmpty(sgRollbackGroups)) {
      sgRollbackChainNodeId = UUIDGenerator.generateUuid();
      sgRollbackChainPlan =
          createSgRollbackChainPlan(sgRollbackChainNodeId, sgRollbackGroups, stepsArrayFields.get(0).getUuid());
    }

    // Create infra plan if needed
    // Infra's next step should point to the SG rollback chain (if present), otherwise to the first rollback step
    String infraNextNodeId;
    if (sgRollbackChainNodeId != null) {
      infraNextNodeId = sgRollbackChainNodeId;
    } else {
      infraNextNodeId = stepsArrayFields.get(0).getUuid();
    }
    LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses =
        createInfraPlan(ctx, stepsArrayFields, deployModuleNodesInfo, infraNextNodeId);
    String infraNodeId = getInfraNodeId(infraPlanCreationResponses);

    String childIdForInitPlan;
    if (infraNodeId != null) {
      childIdForInitPlan = infraNodeId;
    } else if (sgRollbackChainNodeId != null) {
      childIdForInitPlan = sgRollbackChainNodeId;
    } else {
      childIdForInitPlan = stepsArrayFields.get(0).getUuid();
    }

    DeployPlanCreationResult deployResult = getDeployEntityResults(deployModuleNodesInfo);

    // Collect forward execution step group rollback for container provisioning in K8.
    List<ExecutionWrapperConfig> forwardStepConfigs = new ArrayList<>();
    if (infrastructure != null && Infrastructure.Type.KUBERNETES_DIRECT.equals(infrastructure.getType())) {
      forwardStepConfigs = collectStepGroupRollbackExecutionWrapperConfigs(rollbackStepsField);
    }

    PlanCreationResponse initStepPlanCreationResponse = initializeStepPlanCreatorV1.createPlan(ctx, stageId, stageName,
        null, infrastructure, executionWrapperConfigs, forwardStepConfigs, childIdForInitPlan, deployResult,
        modulesImplicitNodesInfo, ParameterField.createValueField(Collections.emptyList()));
    Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
    Map<String, Dependency> metadataMap = new HashMap<>();
    for (int i = 0; i < stepsArrayFields.size(); i++) {
      YamlField stepYamlField = stepsArrayFields.get(i);
      stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      if (i + 1 < stepsArrayFields.size()) {
        metadataMap.put(stepYamlField.getNode().getUuid(),
            Dependency.newBuilder()
                .setNodeMetadata(HarnessStruct.newBuilder()
                                     .putData(PlanCreatorConstants.NEXT_ID,
                                         HarnessValue.newBuilder()
                                             .setStringValue(stepsArrayFields.get(i + 1).getNode().getUuid())
                                             .build())
                                     .build())
                .build());
      }
    }
    planCreationResponseBuilder.dependencies(
        DependenciesUtils.toDependenciesProtoWithMetadataMap(stepYamlFieldMap, metadataMap));

    PlanNode executionRollbackNode = StepGroupRollbackPlanCreatorUtils.createRollbackStepsWrapperNode(
        rollbackStepsField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX,
        initStepPlanCreationResponse.getPlanNode().getUuid());
    PlanCreationResponse rollbackStepsPlanCreationResponse =
        planCreationResponseBuilder.planNode(executionRollbackNode).build();
    rollbackStepsPlanCreationResponse.merge(initStepPlanCreationResponse);

    // For stage rollback, hide Init in the graph (already ran during forward execution).
    // For pipeline rollback, Init stays visible (runs in a separate stage).
    StepGroupRollbackPlanCreatorUtils.hideInitForStageRollback(
        ctx.getExecutionMode(), rollbackStepsPlanCreationResponse, initStepPlanCreationResponse);

    // Add the SG rollback chain (and any parallel wrapper nodes) to the plan
    if (sgRollbackChainPlan != null) {
      rollbackStepsPlanCreationResponse.merge(sgRollbackChainPlan);
    }

    if (isNotEmpty(infraPlanCreationResponses)) {
      for (PlanCreationResponse infraResponse : infraPlanCreationResponses.values()) {
        rollbackStepsPlanCreationResponse.merge(infraResponse);
      }
    }

    return rollbackStepsPlanCreationResponse;
  }

  private List<List<RollbackNode>> collectSgRollbackGroupsFromYaml(YamlField rollbackStepsField) {
    if (rollbackStepsField == null || rollbackStepsField.getNode() == null
        || rollbackStepsField.getNode().getParentNode() == null) {
      return Collections.emptyList();
    }

    YamlNode stageNode = rollbackStepsField.getNode().getParentNode();
    YamlField executionStepsField = stageNode.getField(YAMLFieldNameConstants.STEPS);
    return StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(executionStepsField);
  }

  private PlanCreationResponse createSgRollbackChainPlan(
      String nodeId, List<List<RollbackNode>> sgRollbackGroups, String nextNodeId) {
    RollbackOptionalChildChainStepParametersBuilder paramBuilder = RollbackOptionalChildChainStepParameters.builder();
    List<PlanNode> parallelWrapperNodes = new ArrayList<>();
    List<String> preservedNodeIds = new ArrayList<>();

    // Each inner list represents a rollback group:
    //   size == 1 → sequential (single step group rollback)
    //   size  > 1 → parallel  (multiple step groups from same parallel: section)
    for (List<RollbackNode> group : sgRollbackGroups) {
      if (group.size() == 1) {
        // Sequential — add single entry directly to the chain
        paramBuilder.childNode(group.get(0));
      } else {
        // Parallel — use NGForkStep (type "NG_FORK")
        // Architecture:
        //   NGForkStep (visible, transparent parallel container)
        //     ├── per-group wrapper 1 (StepGroupRollbackChainStep, SKIP_NODE, CHILD_CHAIN)
        //     │   └── (Rollback) - group_2 PlanNode
        //     └── per-group wrapper 2 (StepGroupRollbackChainStep, SKIP_NODE, CHILD_CHAIN)
        //         └── (Rollback) - group_3 PlanNode
        String forkUuid = UUIDGenerator.generateUuid();
        List<String> perGroupWrapperIds = new ArrayList<>();

        for (RollbackNode entry : group) {
          String perGroupWrapperUuid = UUIDGenerator.generateUuid();
          perGroupWrapperIds.add(perGroupWrapperUuid);
          PlanNode perGroupWrapper =
              StepGroupRollbackPlanCreatorUtils.createOuterPerGroupWrapperPlanNode(perGroupWrapperUuid, entry);

          parallelWrapperNodes.add(perGroupWrapper);
          preservedNodeIds.add(perGroupWrapperUuid);
        }

        PlanNode forkNode =
            StepGroupRollbackPlanCreatorUtils.createParallelNodeForOuterStepGroupRollback(forkUuid, perGroupWrapperIds);

        parallelWrapperNodes.add(forkNode);
        preservedNodeIds.add(forkUuid);

        paramBuilder.childNode(RollbackNode.builder().nodeId(forkUuid).dependentNodeIdentifier(null).build());
      }
    }

    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(nodeId)
            .name(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_NAME)
            .identifier(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_IDENTIFIER)
            .stepType(StepGroupRollbackChainStep.STEP_TYPE)
            .stepParameters(paramBuilder.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .skipGraphType(SkipType.SKIP_NODE)
            .skipExpressionChain(true);

    // ON_SUCCESS adviser to chain to the first user-defined rollback step after SG rollback completes.
    // When nextNodeId is null (SG-only, no rollback nodes at stage), no adviser is needed.
    if (isNotEmpty(nextNodeId)) {
      AdviserObtainment onSuccessAdviser =
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
              .setParameters(ByteString.copyFrom(
                  kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeId).build())))
              .build();
      builder.adviserObtainment(onSuccessAdviser);
    }

    PlanNode sgChainNode = builder.build();

    // Build response with chain node + parallel wrapper nodes
    PlanCreationResponseBuilder responseBuilder = PlanCreationResponse.builder().planNode(sgChainNode);
    for (PlanNode wrapperNode : parallelWrapperNodes) {
      responseBuilder.node(wrapperNode.getUuid(), wrapperNode);
    }

    if (!preservedNodeIds.isEmpty()) {
      responseBuilder.preservedNodesInRollbackMode(preservedNodeIds);
    }

    return responseBuilder.build();
  }

  /**
   * Creates a complete rollback plan for the SG-only case (no stage-level rollback nodes present).
   * Follows the same flow as — Init → (Infra) → SG Rollback Chain —
   * but without stage-level rollback step dependencies.
   */
  public PlanCreationResponse createSgOnlyRollbackPlan(PlanCreationContext ctx, String stageId, String stageName,
      Infrastructure infrastructure, Map<String, Object> modulesImplicitNodesInfo,
      List<List<RollbackNode>> sgRollbackGroups, YamlField executionStepsField) {
    // Rollback-aware context (IS_STEP_INSIDE_ROLLBACK = true)
    Dependency rollbackDependency = Dependency.newBuilder()
                                        .setParentInfo(HarnessStruct.newBuilder()
                                                           .putData(PlanCreatorConstants.IS_STEP_INSIDE_ROLLBACK,
                                                               HarnessValue.newBuilder().setBoolValue(true).build())
                                                           .build())
                                        .build();
    PlanCreationContext rollbackCtx = PlanCreationContext.cloneWithCurrentField(
        ctx, ctx.getCurrentField(), ctx.getYaml(), rollbackDependency, ctx.getExecutionInputTemplate());

    Map<String, Object> deployModuleNodesInfo = getDeployModuleNodesInfo(modulesImplicitNodesInfo);

    // ──── SG Rollback Chain (no ON_SUCCESS — no stage rollback steps to chain to) ────
    String sgChainNodeId = UUIDGenerator.generateUuid();
    PlanCreationResponse sgRollbackChainPlan = createSgRollbackChainPlan(sgChainNodeId, sgRollbackGroups, null);

    // ──── Infra plan (same as createPlanForField) ────
    LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses =
        createInfraPlan(rollbackCtx, Collections.emptyList(), deployModuleNodesInfo, sgChainNodeId);
    String infraNodeId = getInfraNodeId(infraPlanCreationResponses);

    // ──── childIdForInitPlan: Infra → SG Chain (same priority as createPlanForField) ────
    String childIdForInitPlan = infraNodeId != null ? infraNodeId : sgChainNodeId;

    DeployPlanCreationResult deployResult = getDeployEntityResults(deployModuleNodesInfo);

    // ──── K8s container provisioning (same as createPlanForField) ────
    List<ExecutionWrapperConfig> forwardStepConfigs = new ArrayList<>();
    if (infrastructure != null && Infrastructure.Type.KUBERNETES_DIRECT.equals(infrastructure.getType())) {
      forwardStepConfigs = collectSgRollbackConfigsFromStepsField(executionStepsField);
    }

    // ──── Init step ────
    PlanCreationResponse initResponse = initializeStepPlanCreatorV1.createPlan(rollbackCtx, stageId, stageName, null,
        infrastructure, Collections.emptyList(), forwardStepConfigs, childIdForInitPlan, deployResult,
        modulesImplicitNodesInfo, ParameterField.createValueField(Collections.emptyList()));

    // ──── RollbackStepsStep wrapper ────
    String wrapperUuid = UUIDGenerator.generateUuid();
    PlanNode wrapperNode = StepGroupRollbackPlanCreatorUtils.createRollbackStepsWrapperNode(
        wrapperUuid, initResponse.getPlanNode().getUuid());

    // ──── Preserved nodes for pipeline rollback ────
    // In pipeline rollback, RollbackModeExecutionHelper.transformPlanForRollbackMode only keeps
    // nodes that are either in preservedNodesInRollbackMode or are Stage/ancestor-of-stage nodes.
    // ALL other nodes are dropped from the final plan. So every node created here must be preserved.
    List<String> preservedNodes = new ArrayList<>();
    preservedNodes.add(wrapperUuid);
    preservedNodes.add(sgChainNodeId);
    for (List<RollbackNode> group : sgRollbackGroups) {
      for (RollbackNode entry : group) {
        preservedNodes.add(entry.getNodeId());
      }
    }

    // Preserve Init node and any nodes it created (without this, Init is missing from the
    // final rollback plan and the wrapper hangs trying to start a non-existent child)
    if (initResponse.getPlanNode() != null) {
      preservedNodes.add(initResponse.getPlanNode().getUuid());
    }
    if (isNotEmpty(initResponse.getNodes())) {
      preservedNodes.addAll(initResponse.getNodes().keySet());
    }

    // Preserve Infra nodes (CD deploy infrastructure)
    if (isNotEmpty(infraPlanCreationResponses)) {
      for (PlanCreationResponse infraResponse : infraPlanCreationResponses.values()) {
        if (infraResponse.getPlanNode() != null) {
          preservedNodes.add(infraResponse.getPlanNode().getUuid());
        }
        if (isNotEmpty(infraResponse.getNodes())) {
          preservedNodes.addAll(infraResponse.getNodes().keySet());
        }
      }
    }

    PlanCreationResponse response =
        PlanCreationResponse.builder().planNode(wrapperNode).preservedNodesInRollbackMode(preservedNodes).build();
    response.merge(initResponse);

    // For stage rollback, hide Init in the graph (already ran during forward execution).
    // For pipeline rollback, Init stays visible (runs in a separate stage).
    StepGroupRollbackPlanCreatorUtils.hideInitForStageRollback(ctx.getExecutionMode(), response, initResponse);

    response.merge(sgRollbackChainPlan);

    if (isNotEmpty(infraPlanCreationResponses)) {
      for (PlanCreationResponse infraResponse : infraPlanCreationResponses.values()) {
        response.merge(infraResponse);
      }
    }
    return response;
  }

  private List<ExecutionWrapperConfig> collectStepGroupRollbackExecutionWrapperConfigs(YamlField rollbackStepsField) {
    if (rollbackStepsField == null || rollbackStepsField.getNode() == null
        || rollbackStepsField.getNode().getParentNode() == null) {
      return Collections.emptyList();
    }

    YamlNode stageNode = rollbackStepsField.getNode().getParentNode();
    YamlField executionStepsField = stageNode.getField(YAMLFieldNameConstants.STEPS);
    if (executionStepsField == null || executionStepsField.getNode() == null) {
      return Collections.emptyList();
    }

    List<YamlField> stepsFields = getStepYamlFields(executionStepsField);
    if (stepsFields.isEmpty()) {
      return Collections.emptyList();
    }

    // getExecutionConfig already populates .rollback() on StepGroupElementConfig,
    // so we just trim each config to keep only rollback content.
    List<ExecutionWrapperConfig> allConfigs =
        stepsFields.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    return CIPlanCreatorUtils.extractStepGroupRollbackExecutionWrapperConfigs(allConfigs);
  }

  /**
   * Collects SG rollback ExecutionWrapperConfig from the forward steps field directly.
   * Used in the SG-only case where rollbackStepsField is not available.
   */
  private List<ExecutionWrapperConfig> collectSgRollbackConfigsFromStepsField(YamlField executionStepsField) {
    if (executionStepsField == null || executionStepsField.getNode() == null) {
      return Collections.emptyList();
    }
    List<YamlField> stepsFields = getStepYamlFields(executionStepsField);
    if (stepsFields.isEmpty()) {
      return Collections.emptyList();
    }
    List<ExecutionWrapperConfig> allConfigs =
        stepsFields.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    return CIPlanCreatorUtils.extractStepGroupRollbackExecutionWrapperConfigs(allConfigs);
  }

  private static Map<String, Object> getDeployModuleNodesInfo(Map<String, Object> modulesImplicitNodesInfo) {
    Map<String, Object> deployModuleNodesInfo = new HashMap<>();
    if (isNotEmpty(modulesImplicitNodesInfo) && modulesImplicitNodesInfo.containsKey(TemplateType.DEPLOY.getName())) {
      deployModuleNodesInfo = (Map<String, Object>) modulesImplicitNodesInfo.get(TemplateType.DEPLOY.getName());
    }
    return deployModuleNodesInfo;
  }

  private static List<YamlField> getStepYamlFields(YamlField rollbackStepsNode) {
    List<YamlNode> yamlNodes =
        Optional.of(Preconditions.checkNotNull(rollbackStepsNode).getNode().asArray()).orElse(Collections.emptyList());
    return PlanCreatorUtils.getStepYamlFieldsV1(yamlNodes);
  }

  private static DeployPlanCreationResult getDeployEntityResults(Map<String, Object> deployModuleNodesInfo) {
    DeployPlanCreationResult deployResult = null;
    if (isNotEmpty(deployModuleNodesInfo)) {
      deployResult = DeployPlanCreationResult.builder()
                         .envRef((String) deployModuleNodesInfo.get(YAMLFieldNameConstants.ENVIRONMENT))
                         .serviceRef((String) deployModuleNodesInfo.get(YAMLFieldNameConstants.SERVICE))
                         .infraId((String) deployModuleNodesInfo.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE))
                         .build();
    }
    return deployResult;
  }

  private static String getInfraNodeId(LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses) {
    String infraNodeId = null;
    if (isNotEmpty(infraPlanCreationResponses) && infraPlanCreationResponses.size() == 1) {
      infraNodeId = infraPlanCreationResponses.keySet().iterator().next();
    }
    return infraNodeId;
  }

  private LinkedHashMap<String, PlanCreationResponse> createInfraPlan(PlanCreationContext ctx,
      List<YamlField> stepsArrayFields, Map<String, Object> deployModuleNodesInfo, String infraNextNodeId) {
    LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses = new LinkedHashMap<>();
    if (ExecutionModeUtils.isRollbackMode(ctx.getExecutionMode())) {
      infraPlanCreationResponses =
          createInfrastructurePlanForDeploy(deployModuleNodesInfo, ctx, stepsArrayFields, infraNextNodeId);
    }
    return infraPlanCreationResponses;
  }

  private LinkedHashMap<String, PlanCreationResponse> createInfrastructurePlanForDeploy(
      Map<String, Object> deployModuleNodesInfo, PlanCreationContext ctx, List<YamlField> stepsArrayFields,
      String infraNextNodeId) {
    LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses = new LinkedHashMap<>();

    if (isNotEmpty(deployModuleNodesInfo)
        && deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars = getEnvVars(ctx);
      boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
      String infraNodeId = UUIDGenerator.generateUuid();
      infraPlanCreationResponses = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, infraNextNodeId, infraNodeId, deployModuleNodesInfo, isStepInsideRollback, envVars);
    }
    return infraPlanCreationResponses;
  }

  private ParameterField<Map<String, ParameterField<JsonNode>>> getEnvVars(PlanCreationContext ctx) {
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars =
        planCreatorEnvVarHelper.retrieveEnvVars(ctx, PlanCreatorConstants.PIPELINE_ENV);
    ParameterField<Map<String, ParameterField<JsonNode>>> stageEnvVars =
        planCreatorEnvVarHelper.retrieveEnvVars(ctx, PlanCreatorConstants.STAGE_ENV);
    envVars.obtainValue().putAll(stageEnvVars.obtainValue());
    return envVars;
  }
}
