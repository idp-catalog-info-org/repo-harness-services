/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo.InitializeStepInfoBuilder;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.cd.beans.DeployPlanCreationResult;
import io.harness.ci.execution.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1.StepElementParametersV1Builder;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.v1.RunInfoUtilsV1;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CI)
public class InitializeStepPlanCreatorV1 {
  private final String InitializeDisplayName = "Initialize";
  private final StepType STEP_TYPE = StepType.newBuilder()
                                         .setType(CIStepInfoType.INITIALIZE_TASK.getDisplayName())
                                         .setStepCategory(StepCategory.STEP)
                                         .build();

  @Inject private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Inject private CIFeatureFlagService ffService;
  @Inject KryoSerializer kryoSerializer;

  public PlanCreationResponse createPlan(PlanCreationContext ctx, String stageId, String stageName, CodeBase codebase,
      Infrastructure infrastructure, List<ExecutionWrapperConfig> executionWrapperConfigs,
      List<ExecutionWrapperConfig> rollbackExecutionConfigs, String childID,
      DeployPlanCreationResult deployPlanCreationResult, Map<String, Object> moduleImplicitNodesInfo,
      ParameterField<List<String>> sharedPaths) {
    return createPlan(ctx, stageId, stageName, codebase, infrastructure, executionWrapperConfigs,
        rollbackExecutionConfigs, childID, deployPlanCreationResult, moduleImplicitNodesInfo, sharedPaths, null);
  }

  public PlanCreationResponse createPlan(PlanCreationContext ctx, String stageId, String stageName, CodeBase codebase,
      Infrastructure infrastructure, List<ExecutionWrapperConfig> executionWrapperConfigs,
      List<ExecutionWrapperConfig> rollbackExecutionConfigs, String childID,
      DeployPlanCreationResult deployPlanCreationResult, Map<String, Object> moduleImplicitNodesInfo,
      ParameterField<List<String>> sharedPaths, Map<String, String> stagePermissions) {
    // create PluginStepNode
    InitializeStepNode initializeStepNode =
        getStepNode(ctx, codebase, infrastructure, stageId, stageName, executionWrapperConfigs,
            rollbackExecutionConfigs, deployPlanCreationResult, moduleImplicitNodesInfo, sharedPaths, stagePermissions);
    // create Plan node
    return createPlanForField(ctx, initializeStepNode, childID);
  }

  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, InitializeStepNode stepNode, String childId) {
    final boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stepNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, stepNode.getName()))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stepNode.getIdentifier()))
            .stepType(STEP_TYPE)
            .group(StepOutcomeGroup.STEP.name())
            // TODO: send rollback parameters to this method which can be extracted from dependency
            .stepParameters(getStepParameters(ctx, stepNode))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .whenCondition(RunInfoUtilsV1.getStepWhenConditionForInit(null, isStepInsideRollback))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                    .timeout(TimeoutUtils.getTimeoutParameterFieldString(stepNode.getTimeout()))
                                    .build())
                    .build())
            .skipUnresolvedExpressionsCheck(true)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    List<AdviserObtainment> adviserObtainmentList = getAdviserObtainments(ctx, childId);
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

  protected StepParameters getStepParameters(PlanCreationContext ctx, InitializeStepNode stepElement) {
    stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
    return getStepParameters(stepElement, ctx);
  }

  StepParameters getStepParameters(InitializeStepNode stepElementConfig, PlanCreationContext ctx) {
    StepElementParametersV1Builder stepParametersBuilder = StepElementParametersV1.builder();
    stepParametersBuilder.name(stepElementConfig.getName());
    stepParametersBuilder.id(stepElementConfig.getIdentifier());
    stepParametersBuilder.description(stepElementConfig.getDescription());
    stepParametersBuilder.timeout(
        ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepElementConfig.getTimeout())));
    stepParametersBuilder.type(stepElementConfig.getType());
    stepParametersBuilder.uuid(stepElementConfig.getUuid());
    stepParametersBuilder.enforce(stepElementConfig.getEnforce());
    StepUtils.appendDelegateSelectorsV1(stepElementConfig.getInitializeStepInfo(), ctx, kryoSerializer);
    stepParametersBuilder.spec(stepElementConfig.getInitializeStepInfo());
    return stepParametersBuilder.build();
  }

  protected List<AdviserObtainment> getAdviserObtainments(PlanCreationContext ctx, String childId) {
    boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
    boolean isFlexibleTemplatesEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ctx);
    List<AdviserObtainment> adviserObtainmentList =
        PlanCreatorUtilsV1.getAdviserObtainmentsForStep(kryoSerializer, ctx.getDependency(), new ArrayList<>(),
            isStepInsideRollback, isFlexibleTemplatesEnabled, ctx.getCurrentField(), childId);

    if (isNotEmpty(childId) && !isStepInsideRollback) {
      adviserObtainmentList.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
              .setParameters(ByteString.copyFrom(
                  kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(childId).build())))
              .build());
    }
    return adviserObtainmentList;
  }

  private InitializeStepNode getStepNode(PlanCreationContext ctx, CodeBase codeBase, Infrastructure infrastructure,
      String stageId, String stageName, List<ExecutionWrapperConfig> executionWrapperConfigs,
      List<ExecutionWrapperConfig> rollbackExecutionConfigs, DeployPlanCreationResult deployPlanCreationResult,
      Map<String, Object> moduleImplicitNodesInfo, ParameterField<List<String>> sharedPaths,
      Map<String, String> stagePermissions) {
    InitializeStepInfoBuilder initializeStepInfoBuilder =
        InitializeStepInfo.builder()
            .identifier(InitializeStepInfo.STEP_TYPE.getType())
            .name(InitializeStepInfo.STEP_TYPE.getType())
            .infrastructure(infrastructure)
            .stageIdentifier(stageId)
            // TODO: set variables once InitializeStepInfoV1 is created
            //            .variables(abstractStageNode.getVariables())
            .stageElementConfig(IntegrationStageConfigImpl.builder()
                                    .uuid(IdentifierGeneratorUtils.getId(stageName))
                                    .execution(ExecutionElementConfig.builder()
                                                   .steps(executionWrapperConfigs)
                                                   .rollbackSteps(rollbackExecutionConfigs)
                                                   .version(HarnessYamlVersion.V1)
                                                   .build())
                                    .infrastructure(infrastructure)
                                    .cloneCodebase(ParameterField.createValueField(codeBase != null))
                                    .sharedPaths(sharedPaths)
                                    .serviceDependencies(ParameterField.createValueField(Collections.emptyList()))
                                    .permissions(stagePermissions)
                                    .build())
            .ciCodebase(codeBase)
            .skipGitClone(codeBase == null)
            .executionElementConfig(ExecutionElementConfig.builder()
                                        .version(HarnessYamlVersion.V1)
                                        .steps(executionWrapperConfigs)
                                        .rollbackSteps(rollbackExecutionConfigs)
                                        .build())
            .timeout(buildJobEnvInfoBuilder.getTimeout(infrastructure, ctx.getAccountIdentifier()))
            .modulesMetadata(moduleImplicitNodesInfo);

    setCDEntitiesIdsToInfo(deployPlanCreationResult, initializeStepInfoBuilder);

    return InitializeStepNode.builder()
        .identifier(InitializeStepInfo.STEP_TYPE.getType())
        .name(InitializeDisplayName)
        .uuid(generateUuid())
        .type(InitializeStepNode.StepType.liteEngineTask)
        .timeout(getTimeout(infrastructure, ctx.getAccountIdentifier()))
        .initializeStepInfo(initializeStepInfoBuilder.build())
        .build();
  }

  private static void setCDEntitiesIdsToInfo(
      DeployPlanCreationResult deployPlanCreationResult, InitializeStepInfoBuilder initializeStepInfoBuilder) {
    if (deployPlanCreationResult != null) {
      if (isNotEmpty(deployPlanCreationResult.getServiceRef())) {
        initializeStepInfoBuilder.serviceRef(ParameterField.createValueField(deployPlanCreationResult.getServiceRef()));
      }
      if (isNotEmpty(deployPlanCreationResult.getEnvRef())) {
        initializeStepInfoBuilder.envRef(ParameterField.createValueField(deployPlanCreationResult.getEnvRef()));
      }
      if (isNotEmpty(deployPlanCreationResult.getInfraId())) {
        initializeStepInfoBuilder.infraId(ParameterField.createValueField(deployPlanCreationResult.getInfraId()));
      }
    }
  }

  private ParameterField<Timeout> getTimeout(Infrastructure infrastructure, String accountId) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() == Infrastructure.Type.VM) {
      VmInitializeTaskParamsBuilder.validateInfrastructure(infrastructure);
      VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
      return parseTimeout(vmPoolYaml.getSpec().getInitTimeout(), "15m");
    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
        throw new CIStageExecutionException("Input infrastructure can not be empty");
      }
      ParameterField<String> timeout = ((K8sDirectInfraYaml) infrastructure).getSpec().getInitTimeout();
      return parseTimeout(timeout, "10m");
    } else if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      return ParameterField.createValueField(Timeout.fromString("10h"));
    }
    return ParameterField.createValueField(Timeout.fromString("10m"));
  }

  private ParameterField<Timeout> parseTimeout(ParameterField<String> timeout, String defaultTimeout) {
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      return ParameterField.createValueField(Timeout.fromString((String) timeout.fetchFinalValue()));
    } else {
      return ParameterField.createValueField(Timeout.fromString(defaultTimeout));
    }
  }
}