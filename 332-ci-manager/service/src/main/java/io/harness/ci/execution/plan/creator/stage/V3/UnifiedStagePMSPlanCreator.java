/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.beans.steps.StepSpecTypeConstants.INTEGRATIONSTAGESTEPPMS_FACILITATOR;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MODULE_IMPLICIT_NODES_INFO;
import static io.harness.ci.execution.integrationstage.V1.ModuleSpecificPlanHandlers.getStageChildrenEntitiesInfo;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.plan.creation.PlanCreatorConstants.STAGE_PREFIX_FQN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.v1.BuildIntelligenceV1;
import io.harness.beans.steps.v1.CachePolicyV1;
import io.harness.beans.steps.v1.CachingV1;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.integrationstage.V1.ModuleSpecificPlanHandlers;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.states.V1.cd.UnifiedMultiDeploymentPlanCreatorHelper;
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.utils.PipelineV1InputVarsUtils;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.v1.AbstractStagePlanCreator;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.utils.CiCodebaseUtils;
import io.harness.when.utils.v1.RunInfoUtilsV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.CommitShaBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.TagBuildSpec;
import io.harness.yaml.options.Options;
import io.harness.yaml.registry.Registry;
import io.harness.yaml.utils.v1.NGVariablesUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnifiedStagePMSPlanCreator extends AbstractStagePlanCreator<UnifiedStageNodeV1> {
  public static final String TYPE = "type";
  private static final String CODEBASE_STEP_TYPE = "codebase";
  private static final String CODEBASE_IDENTIFIER = "codebase";
  @Inject private KryoSerializer kryoSerializer;
  @Inject private CIPlanCreatorUtils ciPlanCreatorUtils;
  @Inject private BaseConnectorUtils baseConnectorUtils;
  @Inject private CiCodebaseUtils ciCodebaseUtils;
  @Inject private UnifiedMultiDeploymentPlanCreatorHelper unifiedMultiDeploymentPlanCreatorHelper;
  @Inject private SerializerUtils serializerUtils;
  @Inject private RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator;

  public static final String STAGE_NODE = "stageNode";
  public static final String INFRASTRUCTURE = "infrastructure";
  public static final String CODEBASE = "codebase";
  public static final String PERMISSIONS = YAMLFieldNameConstants.PERMISSIONS;
  private static final String DEFAULT_CACHE_SERVER_PORT = "8082";
  private static final String MAVEN_URL = "MAVEN_URL";

  @Override
  public UnifiedStageNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedStageNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException(
          "Unable to parse integration stage yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.UNIFIED_STAGE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, UnifiedStageNodeV1 stageNode) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    YamlField field = ctx.getCurrentField();
    YamlField stepsField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.STEPS));
    Infrastructure infrastructure =
        ciPlanCreatorUtils.getInfrastructure(stageNode.getRuntime(), stageNode.getPlatform(), stageNode.getVolumes());
    GitCloneStepInfoV1 stageClone = getGitClone(ctx, stageNode);
    GitCloneStepInfoV1 pipelineClone = ciPlanCreatorUtils.getDeserializedClone(ctx.getDependency()).orElse(null);
    boolean hasStageOverride = (stageNode.getClone() != null);
    CodeBase codeBase = createPlanForCodebase(
        ctx, stageClone, pipelineClone, hasStageOverride, planCreationResponseMap, stepsField.getUuid());

    Map<String, Object> modulesImplicitNodesInfo =
        ModuleSpecificPlanHandlers.getModulesImplicitNodesInfo(field, stageNode);
    YamlUpdates cdRelatedYamlUpdates = null;

    PlanCreationResponse dependencies = preparePlanCreationResponse(ctx, stageNode, dependenciesNodeMap, field,
        stepsField, infrastructure, codeBase, cdRelatedYamlUpdates, modulesImplicitNodesInfo);
    planCreationResponseMap.put(stepsField.getUuid(), dependencies);
    PlanCreationResponse planForRollback = RollbackPlanCreator.createPlanForRollback(ctx.getCurrentField(),
        UnifiedMultiDeploymentUtils.getStageNodeUuid(ctx, stageNode), stageNode.getName(), infrastructure,
        kryoSerializer, modulesImplicitNodesInfo, rollbackStepsPMSPlanCreator, ctx);

    if (isNotEmpty(planForRollback.getNodes())) {
      planCreationResponseMap.put(
          Objects.requireNonNull(stepsField).getNode().getUuid() + "_combinedRollback", planForRollback);
    }

    if (UnifiedMultiDeploymentUtils.isMultiDeployment(stageNode.getService(), stageNode.getEnvironment())) {
      if (stageNode.getStrategy() != null) {
        throw new InvalidYamlException("Looping Strategy and Multi Service/Environment configurations are not "
            + "supported together in a single stage. Please use any one of these");
      }
      unifiedMultiDeploymentPlanCreatorHelper.addMultiDeploymentDependency(planCreationResponseMap, stageNode, ctx);
    }

    log.info("Successfully created plan for integration stage {}", stageNode.getName());

    return planCreationResponseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1, List<String> childrenNodeIds) {
    StepParameters stageParameters = getStageParameters(ctx, stageNodeV1, childrenNodeIds);
    SdkTimeoutObtainment timeoutObtainment = PlanCreatorUtilsV1.getTimeoutObtainmentForStage(stageNodeV1);
    List<AdviserObtainment> adviserObtainments = getAdviserObtainmentsForStage(ctx, stageNodeV1);
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(UnifiedMultiDeploymentUtils.getStageNodeUuid(ctx, stageNodeV1))
            .identifier(getIdentifierWithExpression(ctx, stageNodeV1, stageNodeV1.getId()))
            .name(getIdentifierWithExpression(ctx, stageNodeV1, stageNodeV1.getName()))
            .stepType(getStepType())
            .group(StepOutcomeGroup.STAGE.name())
            .skipUnresolvedExpressionsCheck(true)
            .whenCondition(RunInfoUtilsV1.getStageWhenCondition(stageNodeV1.getWhen(), ctx.getExecutionMode()))
            .stepParameters(stageParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(INTEGRATIONSTAGESTEPPMS_FACILITATOR).build())
                    .build())
            .exports(stageNodeV1.getExports())
            .adviserObtainments(adviserObtainments)
            .skipExpressionChain(false);

    if (timeoutObtainment != null) {
      builder.timeoutObtainment(timeoutObtainment);
    }
    // For rollback modes, always provide NextStage advisers based on dependency NEXT_ID,
    // even when the stage is wrapped under strategy (normal advisers may be intentionally empty).
    List<AdviserObtainment> rollbackAdvisers =
        PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency());
    builder.advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, rollbackAdvisers)
        .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, rollbackAdvisers);

    if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
      builder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    return builder.build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, UnifiedStageNodeV1 stageNode) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();

    if (UnifiedMultiDeploymentUtils.isMultiDeployment(stageNode.getService(), stageNode.getEnvironment())) {
      return unifiedMultiDeploymentPlanCreatorHelper.getMultiDeploymentGraphLayoutResponse(
          context, stageNode, stageYamlFieldMap, stageYamlField);
    }
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, context.getDependency());
    if (StrategyUtilsV1.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtilsV1.modifyStageLayoutNodeGraph(stageYamlField, nextNodeUuid);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  private PlanCreationResponse preparePlanCreationResponse(PlanCreationContext ctx, UnifiedStageNodeV1 stageNode,
      Map<String, YamlField> dependenciesNodeMap, YamlField field, YamlField stepsField, Infrastructure infrastructure,
      CodeBase codeBase, YamlUpdates yamlUpdates, Map<String, Object> modulesImplicitNodeInfo) {
    dependenciesNodeMap.put(stepsField.getUuid(), stepsField);
    Dependency strategyDependency = getDependencyForStrategy(dependenciesNodeMap, stageNode, ctx);
    PlanCreationResponseBuilder dependenciesBuilder = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
            .toBuilder()
            .putDependencyMetadata(field.getUuid(), strategyDependency)
            .putDependencyMetadata(stepsField.getUuid(),
                getDependencyMetadataForStepsField(ctx, infrastructure, codeBase, stageNode, modulesImplicitNodeInfo))
            .build());

    if (yamlUpdates != null) {
      dependenciesBuilder.yamlUpdates(yamlUpdates);
    }
    return dependenciesBuilder.build();
  }

  private CodeBase createPlanForCodebase(PlanCreationContext ctx, GitCloneStepInfoV1 stageClone,
      GitCloneStepInfoV1 pipelineClone, boolean hasStageOverride,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String childNodeID) {
    Optional<CodeBase> optionalCodeBase = ciPlanCreatorUtils.getCodebase(ctx, stageClone);
    if (!optionalCodeBase.isPresent()) {
      return null;
    }
    CodeBase codeBase = optionalCodeBase.get();

    if (hasStageOverride && pipelineClone != null) {
      Optional<CodeBase> pipelineCodeBase = ciPlanCreatorUtils.getCodebase(ctx, pipelineClone);
      if (pipelineCodeBase.isPresent()) {
        ExecutionSource pipelineExecutionSource = buildExecutionSourceFromCodeBase(pipelineCodeBase.get());
        String stageCodebaseNodeUuid = generateUuid();
        addCodebasePlanNodes(planCreationResponseMap, pipelineCodeBase.get(), generateUuid(), stageCodebaseNodeUuid,
            true, pipelineExecutionSource);
        addCodebasePlanNodes(planCreationResponseMap, codeBase, stageCodebaseNodeUuid, childNodeID, false, null);
        return codeBase;
      }
    }

    boolean writeToPipelineScope = (pipelineClone != null);
    addCodebasePlanNodes(planCreationResponseMap, codeBase, generateUuid(), childNodeID, writeToPipelineScope, null);
    return codeBase;
  }

  // V1-only helper: boolean is safe here since callers always pass literal true/false
  private void addCodebasePlanNodes(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      CodeBase codeBase, String codebaseNodeUuid, String childNodeID, boolean writeToPipelineScope,
      ExecutionSource executionSource) {
    List<PlanNode> codebasePlanNodes = CodebasePlanCreator.buildCodebasePlanNodes(
        codebaseNodeUuid, childNodeID, kryoSerializer, codeBase, executionSource, writeToPipelineScope);
    if (isNotEmpty(codebasePlanNodes)) {
      Collections.reverse(codebasePlanNodes);
      for (PlanNode planNode : codebasePlanNodes) {
        planCreationResponseMap.put(planNode.getUuid(), PlanCreationResponse.builder().planNode(planNode).build());
      }
    }
  }

  private ExecutionSource buildExecutionSourceFromCodeBase(CodeBase codeBase) {
    Build build = codeBase.getBuild().getValue();
    if (build == null || build.getType() == null) {
      return null;
    }
    // Reuse V0's RunTimeInputHandler.resolveStringParameterV2 so unresolved expressions like
    // <+pipeline.variables.x> are preserved as their raw expression string (via fetchFinalValue()),
    // while unresolved <+input> placeholders are collapsed to null. ManualExecutionSource fields are
    // plain Strings inside CodeBaseTaskStepParameters, so PMS resolves preserved expressions at
    // execution time (V0 parity).
    switch (build.getType()) {
      case BRANCH:
        BranchBuildSpec branchSpec = (BranchBuildSpec) build.getSpec();
        return ManualExecutionSource.builder().branch(resolveCodebaseField("branch", branchSpec.getBranch())).build();
      case TAG:
        TagBuildSpec tagSpec = (TagBuildSpec) build.getSpec();
        return ManualExecutionSource.builder().tag(resolveCodebaseField("tag", tagSpec.getTag())).build();
      case PR:
        PRBuildSpec prSpec = (PRBuildSpec) build.getSpec();
        return ManualExecutionSource.builder().prNumber(resolveCodebaseField("number", prSpec.getNumber())).build();
      case COMMIT_SHA:
        CommitShaBuildSpec commitSpec = (CommitShaBuildSpec) build.getSpec();
        return ManualExecutionSource.builder()
            .commitSha(resolveCodebaseField("commitSha", commitSpec.getCommitSha()))
            .build();
      default:
        return null;
    }
  }

  private static String resolveCodebaseField(String fieldName, ParameterField<String> field) {
    return RunTimeInputHandler.resolveStringParameterV2(
        fieldName, CODEBASE_STEP_TYPE, CODEBASE_IDENTIFIER, field, false);
  }

  Dependency getDependencyMetadataForStepsField(PlanCreationContext ctx, Infrastructure infrastructure,
      CodeBase codeBase, UnifiedStageNodeV1 stageNode, Map<String, Object> moduleImplicitNodesInfo) {
    Map<String, HarnessValue> nodeMetadataMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    ByteString stageNodeBytes = ByteString.copyFrom(kryoSerializer.asBytes(stageNode));
    ByteString infrastructureBytes = ByteString.copyFrom(kryoSerializer.asBytes(infrastructure));
    metadataMap.put(STAGE_NODE, stageNodeBytes);
    metadataMap.put(INFRASTRUCTURE, infrastructureBytes);

    nodeMetadataMap.put(STAGE_NODE, HarnessValue.newBuilder().setBytesValue(stageNodeBytes).build());
    nodeMetadataMap.put(INFRASTRUCTURE, HarnessValue.newBuilder().setBytesValue(infrastructureBytes).build());
    if (codeBase != null) {
      ByteString codebaseBytes = ByteString.copyFrom(kryoSerializer.asBytes(codeBase));
      metadataMap.put(CODEBASE, codebaseBytes);
      nodeMetadataMap.put(CODEBASE, HarnessValue.newBuilder().setBytesValue(codebaseBytes).build());
    }

    Map<String, String> pipelinePermissions =
        ciPlanCreatorUtils.getDeserializedPermissions(ctx.getDependency()).orElse(null);
    if (pipelinePermissions != null) {
      ByteString permissionsBytes = ByteString.copyFrom(kryoSerializer.asBytes(pipelinePermissions));
      metadataMap.put(PERMISSIONS, permissionsBytes);
      nodeMetadataMap.put(PERMISSIONS, HarnessValue.newBuilder().setBytesValue(permissionsBytes).build());
    }

    HarnessStruct.Builder parentInfo = HarnessStruct.newBuilder();
    if (isNotEmpty(moduleImplicitNodesInfo)) {
      ByteString moduleImplicitNodesInfoBytes = ByteString.copyFrom(kryoSerializer.asBytes(moduleImplicitNodesInfo));
      metadataMap.put(MODULE_IMPLICIT_NODES_INFO, moduleImplicitNodesInfoBytes);
      nodeMetadataMap.put(
          MODULE_IMPLICIT_NODES_INFO, HarnessValue.newBuilder().setBytesValue(moduleImplicitNodesInfoBytes).build());

      ListValue stageChildren = getStageChildrenEntitiesInfo(moduleImplicitNodesInfo);
      parentInfo.putData(
          PlanCreatorConstants.STAGE_CHILDREN, HarnessValue.newBuilder().setListValue(stageChildren).build());
    }

    List<FailureConfigV1> stageFailureStrategies =
        stageNode.getOnFailure() != null ? stageNode.getOnFailure().getValue() : null;
    if (isNotEmpty(stageFailureStrategies)) {
      parentInfo.putData(PlanCreatorConstants.STAGE_FAILURE_STRATEGIES,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stageFailureStrategies)))
              .build());
    }
    ParameterField<List<TaskSelectorYaml>> delegates = stageNode.getDelegate();
    if (ParameterField.isNotNull(delegates)) {
      parentInfo.putData(PlanCreatorConstants.STAGE_DELEGATES,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(delegates)))
              .build());
    }
    parentInfo.putData(PlanCreatorConstants.STAGE_ID,
        HarnessValue.newBuilder().setStringValue(UnifiedMultiDeploymentUtils.getStageNodeUuid(ctx, stageNode)).build());
    parentInfo.putData(PlanCreatorConstants.STAGE_FQN,
        HarnessValue.newBuilder()
            .setStringValue(STAGE_PREFIX_FQN + ctx.getCurrentField().getNode().getCurrJsonNode().get("id").asText())
            .build());
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = stageNode.getEnv();
    if (ParameterField.isNull(envVars) || envVars.getValue() == null) {
      envVars = ParameterField.createValueField(new HashMap<>());
    }
    addBuildIntelligenceEnvVars(ctx, stageNode, envVars);
    if (ParameterField.isNotNull(envVars)) {
      parentInfo.putData(PlanCreatorConstants.STAGE_ENV,
          HarnessValue.newBuilder()
              .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(envVars)))
              .build());
    }
    return Dependency.newBuilder()
        .setNodeMetadata(HarnessStruct.newBuilder().putAllData(nodeMetadataMap).build())
        .setParentInfo(parentInfo)
        .build();
  }

  @Override
  public StepType getStepType() {
    return IntegrationStageStepPMS.STEP_TYPE;
  }

  @Override
  public StepParameters getStageParameters(
      PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1, List<String> childrenNodeIds) {
    YamlField field = ctx.getCurrentField();
    YamlField stepsField = Preconditions.checkNotNull(field.getNode().getField(YAMLFieldNameConstants.STEPS));
    List<YamlField> steps = CIPlanCreatorUtils.getStepYamlFields(stepsField);
    Optional<Options> optionalOptions =
        ciPlanCreatorUtils.getDeserializedOptions(ctx.getMetadata().getGlobalDependency());
    Options options = optionalOptions.orElse(Options.builder().build());
    Infrastructure infrastructure = ciPlanCreatorUtils.getInfrastructure(
        stageNodeV1.getRuntime(), stageNodeV1.getPlatform(), stageNodeV1.getVolumes());
    GitCloneStepInfoV1 stageClone = getGitClone(ctx, stageNodeV1);
    CodeBase codeBase = ciPlanCreatorUtils.getCodebase(ctx, stageClone).orElse(null);
    Registry registry = options.getRegistry() == null ? Registry.builder().build() : options.getRegistry();
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        steps.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    String cachePort = resolveCacheProxyPort(ctx, stageNodeV1);
    Map<String, Object> modulesImplicitNodesInfo =
        ModuleSpecificPlanHandlers.getModulesImplicitNodesInfo(field, stageNodeV1);
    IntegrationStageStepParametersPMS params =
        IntegrationStageStepParametersPMS.builder()
            .stepIdentifiers(IntegrationStageUtils.getStepIdentifiers(executionWrapperConfigs))
            .infrastructure(infrastructure)
            .childNodeID(isNotEmpty(childrenNodeIds) ? childrenNodeIds.get(0) : null)
            .codeBase(codeBase)
            .triggerPayload(ctx.getTriggerPayload())
            .registry(registry)
            .cloneManually(ciPlanCreatorUtils.shouldCloneManually(ctx, codeBase))
            .buildIntelligence(toBuildIntelligence(stageNodeV1.getBuildIntelligence()))
            .cacheProxyPort(cachePort)
            .caching(toCaching(stageNodeV1.getCacheIntelligence()))
            .build();
    params.setModulesMetadataInfo(modulesImplicitNodesInfo);

    Map<String, Object> variables = getCummulativeStageVariables(ctx, stageNodeV1);

    return StageElementParametersV1.builder()
        .uuid(UnifiedMultiDeploymentUtils.getStageNodeUuid(stageNodeV1))
        .id(StrategyUtilsV1.appendIdentifierPostfix(stageNodeV1.getId(), stageNodeV1.getStrategy()))
        .name(StrategyUtilsV1.appendIdentifierPostfix(stageNodeV1.getName(), stageNodeV1.getStrategy()))
        .spec(params)
        .description(stageNodeV1.getDescription())
        .cache(getCacheIntelV1(stageNodeV1))
        .runtime(stageNodeV1.getRuntime())
        .buildIntelligence(stageNodeV1.getBuildIntelligence())
        .variables(ParameterField.createValueField(variables))
        .inputs(ParameterField.createValueField(variables))
        .onFailure(
            ParameterField.isNotNull(stageNodeV1.getOnFailure()) ? stageNodeV1.getOnFailure().obtainValue() : null)
        .delegateSelectors(PlanCreatorUtilsV1.getDelegates(field.getNode()))
        .tags(stageNodeV1.getTags())
        .timeout(stageNodeV1.getTimeout())
        .type(YAMLFieldNameConstants.UNIFIED)
        .build();
  }

  private Map<String, Object> getCummulativeStageVariables(PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1) {
    Map<String, Object> variables = new HashMap<>();

    YamlNode node = ctx.getCurrentField().getNode().getParentNode();
    YamlNode pipelineNode = getParentPipelineNode(node);
    Map<String, Object> pipelineInputsAsVariables = PipelineV1InputVarsUtils.getInputsNodeAsVariables(pipelineNode);

    // Add pipeline inputs first (lower priority)
    if (isNotEmpty(pipelineInputsAsVariables)) {
      variables.putAll(pipelineInputsAsVariables);
    }

    // Add stage inputs last (higher priority - will override pipeline inputs)
    if (stageNodeV1.getInputs() != null) {
      variables.putAll(NGVariablesUtilsV1.getMapOfVariables(stageNodeV1.getInputs().getMap()));
    }

    return variables;
  }

  private YamlNode getParentPipelineNode(YamlNode node) {
    YamlNode pipelineNode = null;
    while (node != null) {
      if (YAMLFieldNameConstants.PIPELINE.equals(node.getFieldName())) {
        pipelineNode = node;
        break;
      }
      node = node.getParentNode();
    }
    return pipelineNode;
  }

  private boolean isEnabledTrueUnlessLiteralFalse(ParameterField<Boolean> enabled) {
    if (!ParameterField.isNotNull(enabled)) {
      return false; // Missing enabled means disabled
    }
    if (enabled.isExpression()) {
      return true; // Expressions treated as enabled, evaluated at runtime
    }
    return !Boolean.FALSE.equals(enabled.obtainValue());
  }

  private boolean isBuildIntelligenceEnabled(BuildIntelligenceV1 buildIntelligence) {
    if (buildIntelligence == null) {
      return false;
    }
    return isEnabledTrueUnlessLiteralFalse(buildIntelligence.getEnabled());
  }

  private BuildIntelligence toBuildIntelligence(BuildIntelligenceV1 buildIntelligence) {
    if (buildIntelligence == null) {
      return BuildIntelligence.builder().enabled(ParameterField.createValueField(false)).build();
    }
    ParameterField<Boolean> enabled = buildIntelligence.getEnabled();
    if (!ParameterField.isNotNull(enabled)) {
      enabled = ParameterField.createValueField(false); // Missing enabled means disabled
    } else if (!enabled.isExpression()) {
      // Resolve literals at plan time
      enabled = Boolean.FALSE.equals(enabled.obtainValue()) ? ParameterField.createValueField(false)
                                                            : ParameterField.createValueField(true);
    }
    // else: preserve expression for runtime evaluation
    return BuildIntelligence.builder().enabled(enabled).build();
  }

  private Caching toCaching(CachingV1 cachingV1) {
    if (cachingV1 == null) {
      return Caching.builder().enabled(ParameterField.createValueField(false)).build();
    }
    ParameterField<Boolean> enabled = cachingV1.getEnabled();
    if (!ParameterField.isNotNull(enabled)) {
      enabled = ParameterField.createValueField(false); // Missing enabled means disabled
    } else if (!enabled.isExpression()) {
      enabled = Boolean.FALSE.equals(enabled.obtainValue()) ? ParameterField.createValueField(false)
                                                            : ParameterField.createValueField(true);
    }
    return Caching.builder().enabled(enabled).build();
  }

  private Map<String, String> getCombinedSettingsMap(PlanCreationContext ctx) {
    String accountId = ctx.getAccountIdentifier();
    String orgId = ctx.getOrgIdentifier();
    String projectId = ctx.getProjectIdentifier();
    Map<String, String> settingsMap = serializerUtils.getCommonCacheIntelSettingsMap(accountId, orgId, projectId);
    Map<String, String> selfHostedSettingsMap = serializerUtils.getSelfHostedSettingsMap(accountId, orgId, projectId);
    settingsMap.putAll(selfHostedSettingsMap);
    return settingsMap;
  }

  private void addBuildIntelligenceEnvVars(PlanCreationContext ctx, UnifiedStageNodeV1 stageNode,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    BuildIntelligenceV1 buildIntelligence = stageNode.getBuildIntelligence();
    if (!isBuildIntelligenceEnabled(buildIntelligence)) {
      return;
    }

    String mavenUrl = null;
    if (ParameterField.isNotNull(buildIntelligence.getMavenUrl())) {
      mavenUrl = buildIntelligence.getMavenUrl().obtainValue();
    }

    if (EmptyPredicate.isEmpty(mavenUrl)) {
      Map<String, String> settingsMap = getCombinedSettingsMap(ctx);
      mavenUrl = settingsMap.get(SettingIdentifiers.CI_BUILD_INTEL_MAVEN_REPO_URL);
    }

    if (EmptyPredicate.isNotEmpty(mavenUrl)) {
      envVars.getValue().put(MAVEN_URL, ParameterField.createValueField(new TextNode(mavenUrl)));
    }
  }

  private String resolveCacheProxyPort(PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1) {
    BuildIntelligenceV1 buildIntelligence = stageNodeV1.getBuildIntelligence();
    if (!isBuildIntelligenceEnabled(buildIntelligence)) {
      return null;
    }

    String cachePort = null;
    if (ParameterField.isNotNull(buildIntelligence.getPort())) {
      cachePort = buildIntelligence.getPort().obtainValue();
    }

    if (EmptyPredicate.isEmpty(cachePort)) {
      Map<String, String> settingsMap = getCombinedSettingsMap(ctx);
      String fallbackPort = settingsMap.get(SettingIdentifiers.CI_BUILD_INTEL_CACHE_SERVER_PORT);
      cachePort = EmptyPredicate.isNotEmpty(fallbackPort) ? fallbackPort : DEFAULT_CACHE_SERVER_PORT;
    }

    return cachePort;
  }

  private GitCloneStepInfoV1 getGitClone(PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1) {
    GitCloneStepInfoV1 stageClone = stageNodeV1.getClone();
    GitCloneStepInfoV1 pipelineClone;
    Optional<GitCloneStepInfoV1> optionalGitCloneStepInfoV1 =
        ciPlanCreatorUtils.getDeserializedClone(ctx.getDependency());
    pipelineClone = optionalGitCloneStepInfoV1.orElse(null);
    if (stageClone == null) {
      return getCloneIfEnabled(pipelineClone);
    }
    if (pipelineClone == null) {
      return getCloneIfEnabled(stageClone);
    }
    if (stageClone.getStrategy() == null) {
      stageClone.setStrategy(pipelineClone.getStrategy());
    }
    if (ParameterField.isBlank(stageClone.getDepth())) {
      stageClone.setDepth(pipelineClone.getDepth());
    }
    if (ParameterField.isBlank(stageClone.getInsecure())) {
      stageClone.setInsecure(pipelineClone.getInsecure());
    }
    if (ParameterField.isBlank(stageClone.getRef())) {
      stageClone.setRef(pipelineClone.getRef());
    }
    if (ParameterField.isBlank(stageClone.getClonedir())) {
      stageClone.setClonedir(pipelineClone.getClonedir());
    }
    if (ParameterField.isBlank(stageClone.getLfs())) {
      stageClone.setLfs(pipelineClone.getLfs());
    }
    if (ParameterField.isBlank(stageClone.getTags())) {
      stageClone.setTags(pipelineClone.getTags());
    }
    if (ParameterField.isBlank(stageClone.getSubmodules())) {
      stageClone.setSubmodules(pipelineClone.getSubmodules());
    }
    if (ParameterField.isBlank(stageClone.getSparseCheckout())) {
      stageClone.setSparseCheckout(pipelineClone.getSparseCheckout());
    }
    if (ParameterField.isBlank(stageClone.getPreFetchCommand())) {
      stageClone.setPreFetchCommand(pipelineClone.getPreFetchCommand());
    }
    if (ParameterField.isBlank(stageClone.getPersistCredentials())) {
      stageClone.setPersistCredentials(pipelineClone.getPersistCredentials());
    }
    if (ParameterField.isBlank(stageClone.getTrace())) {
      stageClone.setTrace(pipelineClone.getTrace());
    }
    if (stageClone.getResources() == null) {
      stageClone.setResources(pipelineClone.getResources());
    }
    if (ParameterField.isBlank(stageClone.getRepo())) {
      stageClone.setRepo(pipelineClone.getRepo());
    }
    if (ParameterField.isBlank(stageClone.getConnector())) {
      stageClone.setConnector(pipelineClone.getConnector());
    }
    if (ParameterField.isBlank(stageClone.getUser())) {
      stageClone.setUser(pipelineClone.getUser());
    }
    return getCloneIfEnabled(stageClone);
  }

  // This method will return "null" only if we specify "enabled" as false explicitly OR clone object is "null", else it
  // will return "clone" object.
  private GitCloneStepInfoV1 getCloneIfEnabled(GitCloneStepInfoV1 cloneStepInfoV1) {
    if (cloneStepInfoV1 == null) {
      return null;
    }
    if (ParameterField.isNotNull(cloneStepInfoV1.getEnabled())) {
      Boolean enabled = cloneStepInfoV1.getEnabled().getValue();
      if (Boolean.FALSE.equals(enabled)) {
        return null;
      }
    }
    return cloneStepInfoV1;
  }

  private CachingV1 getCacheIntelV1(UnifiedStageNodeV1 stageNode) {
    CachingV1 cache = stageNode.getCacheIntelligence();
    if (cache == null) {
      return null;
    }
    if (cache.getPolicy() == null) {
      cache.setPolicy(CachePolicyV1.PULL_PUSH);
    }
    return cache;
  }

  private List<AdviserObtainment> getAdviserObtainmentsForStage(
      PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    boolean isMultiDeployment =
        UnifiedMultiDeploymentUtils.isMultiDeployment(stageNodeV1.getService(), stageNodeV1.getEnvironment());
    if (!(isMultiDeployment || stageNodeV1.getStrategy() != null)) {
      adviserObtainments = PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency());
    }
    return adviserObtainments;
  }

  private String getIdentifierWithExpression(
      PlanCreationContext ctx, UnifiedStageNodeV1 stageNodeV1, String identifier) {
    if (UnifiedMultiDeploymentUtils.isMultiDeployment(stageNodeV1.getService(), stageNodeV1.getEnvironment())) {
      return identifier + StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
    }
    return StrategyUtils.getIdentifierWithExpression(ctx, identifier);
  }
}