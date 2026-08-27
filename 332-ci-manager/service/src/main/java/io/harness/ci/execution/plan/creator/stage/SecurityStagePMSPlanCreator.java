/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage;

import static io.harness.beans.steps.StepSpecTypeConstants.INTEGRATIONSTAGESTEPPMS_FACILITATOR;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI_CODE_BASE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.EXECUTION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROPERTIES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.SecurityStageNode;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.execution.states.STOSpecStep;
import io.harness.ci.execution.states.SecurityStageStepPMS;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.stages.AbstractStagePlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.STO)
public class SecurityStagePMSPlanCreator extends AbstractStagePlanCreator<SecurityStageNode> {
  @Inject private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIStagePlanCreationUtils ciStagePlanCreationUtils;
  @Inject private CIFeatureFlagService featureFlagService;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, SecurityStageNode stageNode) {
    log.info("Received plan creation request for security stage {}", stageNode.getIdentifier());
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();

    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField executionField = specField.getNode().getField(EXECUTION);
    YamlNode parentNode = executionField.getNode().getParentNode();
    String childNodeId = executionField.getNode().getUuid();

    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();
    boolean cloneCodebase =
        RunTimeInputHandler.resolveBooleanParameter(integrationStageConfig.getCloneCodebase(), false);

    if (cloneCodebase) {
      String codeBaseNodeUUID = fetchCodeBaseNodeUUID(ctx, executionField.getNode().getUuid(), planCreationResponseMap);
      if (isNotEmpty(codeBaseNodeUUID)) {
        childNodeId = codeBaseNodeUUID; // Change the child of security stage to codebase node
      }
    }

    CodeBase codeBase = IntegrationStageUtils.getCICodebase(ctx);
    if (featureFlagService.isEnabled(FeatureName.CI_PIPELINE_VARIABLES_IN_STEPS, ctx.getAccountIdentifier())) {
      addPipelineVariablesToStageNode(ctx, stageNode);
    }

    ExecutionElementConfig modifiedExecutionPlan =
        modifyYAMLWithImplicitSteps(ctx, executionField, stageNode, codeBase);

    addStrategyFieldDependencyIfPresent(ctx, stageNode, planCreationResponseMap, metadataMap);

    putNewExecutionYAMLInResponseMap(executionField, planCreationResponseMap, modifiedExecutionPlan, parentNode);

    PlanNode specPlanNode = getSpecPlanNode(specField,
        IntegrationStageStepParametersPMS.getStepParameters(
            ctx, getIntegrationStageNode(stageNode), codeBase, childNodeId));
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    log.info("Successfully created plan for security stage {}", stageNode.getIdentifier());
    return planCreationResponseMap;
  }

  public void addPipelineVariablesToStageNode(PlanCreationContext ctx, SecurityStageNode stageNode) {
    List<NGVariable> pipelineVariables = fetchPipelineVariables(ctx);
    stageNode.setPipelineVariables(pipelineVariables);
  }

  private List<NGVariable> fetchPipelineVariables(PlanCreationContext ctx) {
    List<NGVariable> pipelineVariables = new ArrayList<>();

    try {
      YamlField fullField = YamlUtils.readTree(ctx.getYaml());
      YamlField variablesField = fullField.fromYamlPath("pipeline/variables");
      if (Objects.isNull(variablesField) || Objects.isNull(variablesField.getNode())) {
        return pipelineVariables;
      }
      for (YamlNode variable : variablesField.getNode().asArray()) {
        NGVariable pipelineVariable = YamlUtils.read(variable.toString(), NGVariable.class);
        pipelineVariables.add(pipelineVariable);
      }
    } catch (Exception e) {
      log.warn("Exception while reading pipeline variables : ", e);
    }

    return pipelineVariables;
  }

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(StepSpecTypeConstants.SECURITY_STAGE);
  }

  @Override
  public StepType getStepType(SecurityStageNode stageNode) {
    return SecurityStageStepPMS.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, SecurityStageNode stageNode) {
    CodeBase codeBase = IntegrationStageUtils.getCICodebase(ctx);
    return IntegrationStageStepParametersPMS.getStepParameters(
        ctx, getIntegrationStageNode(stageNode), codeBase, childNodeId);
  }

  @Override
  public Class<SecurityStageNode> getFieldClass() {
    return SecurityStageNode.class;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, SecurityStageNode stageNode, List<String> childrenNodeIds) {
    stageNode.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getIdentifier()));
    stageNode.setName(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getName()));

    StageElementParametersBuilder stageParameters =
        ciStagePlanCreationUtils.getStageParameters(getIntegrationStageNode(stageNode));
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    stageParameters.specConfig(getSpecParameters(specField.getNode().getUuid(), ctx, stageNode));
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(stageParameters.build())
            .stepType(getStepType(stageNode))
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(INTEGRATIONSTAGESTEPPMS_FACILITATOR).build())
                    .build())
            .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, true, ctx));

    return planNodeBuilder.build();
  }

  private void putNewExecutionYAMLInResponseMap(YamlField executionField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, ExecutionElementConfig modifiedExecutionPlan,
      YamlNode parentYamlNode) {
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(modifiedExecutionPlan);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      YamlNode modifiedExecutionNode = new YamlNode(EXECUTION, jsonNode, parentYamlNode);

      YamlField yamlField = new YamlField(EXECUTION, modifiedExecutionNode);
      planCreationResponseMap.put(executionField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  DependenciesUtils.toDependenciesProto(ImmutableMap.of(yamlField.getNode().getUuid(), yamlField)))
              .yamlUpdates(YamlUpdates.newBuilder().putFqnToYaml(yamlField.getYamlPath(), jsonString).build())
              .build());

    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
  }

  private ExecutionElementConfig modifyYAMLWithImplicitSteps(
      PlanCreationContext ctx, YamlField executionYAMLField, SecurityStageNode stageNode, CodeBase codeBase) {
    ExecutionElementConfig executionElementConfig;
    try {
      executionElementConfig = YamlUtils.read(executionYAMLField.getNode().toString(), ExecutionElementConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
    IntegrationStageNode integrationStageNode = getIntegrationStageNode(stageNode);
    return ciIntegrationStageModifier.modifyExecutionPlan(executionElementConfig, integrationStageNode, ctx, codeBase,
        IntegrationStageStepParametersPMS.getInfrastructure(integrationStageNode, ctx), null);
  }

  private String fetchCodeBaseNodeUUID(PlanCreationContext ctx, String executionNodeUUid,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField ciCodeBaseField = getCodebaseYamlField(ctx);
    if (ciCodeBaseField != null) {
      String codeBaseNodeUUID = generateUuid();
      List<PlanNode> codeBasePlanNodeList = CodebasePlanCreator.createPlanForCodeBase(
          ciCodeBaseField, executionNodeUUid, kryoSerializer, codeBaseNodeUUID, null);
      if (isNotEmpty(codeBasePlanNodeList)) {
        for (PlanNode planNode : codeBasePlanNodeList) {
          planCreationResponseMap.put(
              planNode.getUuid(), PlanCreationResponse.builder().node(planNode.getUuid(), planNode).build());
        }
        return codeBaseNodeUUID;
      }
    }
    return null;
  }

  private PlanNode getSpecPlanNode(YamlField specField, IntegrationStageStepParametersPMS stepParameters) {
    return PlanNode.builder()
        .uuid(specField.getNode().getUuid())
        .identifier(YAMLFieldNameConstants.SPEC)
        .stepType(STOSpecStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.SPEC)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  private YamlField getCodebaseYamlField(PlanCreationContext ctx) {
    YamlField ciCodeBaseYamlField = null;
    try {
      YamlNode properties = YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), PROPERTIES);
      ciCodeBaseYamlField = properties.getField(CI).getNode().getField(CI_CODE_BASE);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }
    return ciCodeBaseYamlField;
  }
  private IntegrationStageNode getIntegrationStageNode(SecurityStageNode stageNode) {
    IntegrationStageConfig currentStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();
    IntegrationStageConfigImpl integrationConfig = IntegrationStageConfigImpl.builder()
                                                       .sharedPaths(currentStageConfig.getSharedPaths())
                                                       .execution(currentStageConfig.getExecution())
                                                       .runtime(currentStageConfig.getRuntime())
                                                       .serviceDependencies(currentStageConfig.getServiceDependencies())
                                                       .platform(currentStageConfig.getPlatform())
                                                       .cloneCodebase(currentStageConfig.getCloneCodebase())
                                                       .infrastructure(currentStageConfig.getInfrastructure())
                                                       .build();

    return IntegrationStageNode.builder()
        .uuid(stageNode.getUuid())
        .name(stageNode.getName())
        .failureStrategies(stageNode.getFailureStrategies())
        .type(IntegrationStageNode.StepType.CI)
        .identifier(stageNode.getIdentifier())
        .variables(stageNode.getVariables())
        .pipelineVariables(stageNode.getPipelineVariables())
        .integrationStageConfig(integrationConfig)
        .build();
  }
}
