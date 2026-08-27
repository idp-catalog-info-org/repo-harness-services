/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_RENDERING_STEP;
import static io.harness.pms.yaml.TemplateYamlV1Utility.TEMPLATE_PRESENT_VALIDATORS;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.Boolean.parseBoolean;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.rollback.CDStepsRollbackModeAdviser;
import io.harness.advisers.rollback.RollbackCustomAdviser;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.cd.beans.DeployPlanCreationResult;
import io.harness.cd.beans.DeployPlanCreationResult.DeployPlanCreationResultBuilder;
import io.harness.cd.beans.ModuleSpecificMetadata;
import io.harness.cd.beans.ModuleSpecificPlanCreationResult;
import io.harness.cd.beans.ModuleTemplatePlanCreationResults;
import io.harness.cd.beans.ModuleTemplatePlanCreationResults.ModuleTemplatePlanCreationResultsBuilder;
import io.harness.cd.beans.TemplateTypeBasedPlanCreatorData;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.integrationstage.V1.IACMPlanCreatorUtils;
import io.harness.ci.execution.plan.creator.stage.V3.UnifiedStagePMSPlanCreator;
import io.harness.ci.execution.plancreator.V1.GitClonePlanCreator;
import io.harness.ci.execution.plancreator.V1.InitializeStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.RenderingPlanCreator;
import io.harness.ci.execution.plancreator.V1.TemplatingPlanCreator;
import io.harness.ci.execution.plancreator.V1.UnifiedStageCDInfraPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.UnifiedStageResourceConstraintPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.UnifiedStageServicePlanCreatorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.plan.creator.step.v1.PlanCreatorEnvVarHelper;
import io.harness.ci.states.V1.cd.ServiceHookTaskHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.response.ShouldDisableDeploymentFreezeResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.iacm.beans.IACMPlanCreationResult;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.utilities.PrincipalUtility;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.rollback.NGSectionStepWithRollbackInfo;
import io.harness.unified.depoloymentfreeze.NgDeploymentFreezeResourceClient;
import io.harness.utils.execution.ExecutionModeUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIStepsPlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject private GitClonePlanCreator gitClonePlanCreator;
  @Inject private InitializeStepPlanCreatorV1 initializeStepPlanCreatorV1;
  @Inject private CIPlanCreatorUtils ciPlanCreatorUtils;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ServiceHookTaskHelper serviceHookTaskHelper;
  @Inject RenderingPlanCreator renderingPlanCreator;
  @Inject TemplatingPlanCreator templatingPlanCreator;
  @Inject private PlanCreatorEnvVarHelper planCreatorEnvVarHelper;
  @Inject private NGSettingsClient settingsClient;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject(optional = true) private NgDeploymentFreezeResourceClient ngDeploymentFreezeResourceClient;

  private static final String UNIFIED_PARENT_NODE = "unified";
  private static final String PROJECT_SCOPED_RESOURCE_CONSTRAINT_SETTING_ID =
      "project_scoped_resource_constraint_queue";
  private static final String RC_STEP_ADD_CONDITION = "<+infra.addRcStep> == \"true\"";

  // Default no-op plan creator for template types that don't require custom handling
  private static final BiFunction<TemplateTypeBasedPlanCreatorData, String,
      Optional<? extends ModuleSpecificPlanCreationResult>> DEFAULT_PLAN_CREATOR =
      (data, nextStepId) -> Optional.empty();

  // Default no-op response handler for template types that don't require custom handling
  private static final BiConsumer<LinkedHashMap<String, PlanCreationResponse>, ModuleSpecificPlanCreationResult>
      DEFAULT_RESPONSE_HANDLER = (responseMap, result) -> {};

  // Only register template types that require custom plan creation logic.
  // New template types added to TemplateType enum will automatically use the default no-op handler.
  private final Map<TemplateType,
      BiFunction<TemplateTypeBasedPlanCreatorData, String, Optional<? extends ModuleSpecificPlanCreationResult>>>
      MODULE_TYPE_BASED_PLAN_CREATORS = ImmutableMap
                                            .<TemplateType,
                                                BiFunction<TemplateTypeBasedPlanCreatorData, String,
                                                    Optional<? extends ModuleSpecificPlanCreationResult>>>builder()
                                            .put(TemplateType.DEPLOY, this::getPreDeployPlanCreationResult)
                                            .put(TemplateType.IACM, this::getIacmPlanCreationResult)
                                            .build();

  // Only register template types that require custom response handling logic.
  // New template types added to TemplateType enum will automatically use the default no-op handler.
  private final Map<String, BiConsumer<LinkedHashMap<String, PlanCreationResponse>, ModuleSpecificPlanCreationResult>>
      MODULE_TYPE_BASED_RESPONSE_HANDLERS =
          ImmutableMap
              .<String,
                  BiConsumer<LinkedHashMap<String, PlanCreationResponse>, ModuleSpecificPlanCreationResult>>builder()
              .put(TemplateType.DEPLOY.getName(),
                  (responseMap,
                      result) -> addDeployPlanCreationResponse(responseMap, (DeployPlanCreationResult) result))
              .put(TemplateType.IACM.getName(),
                  (responseMap, result) -> addIacmPlanCreationResponse(responseMap, (IACMPlanCreationResult) result))
              .build();

  @Override
  public YamlField getFieldObject(YamlField field) {
    return field;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STEPS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    boolean isStepsInsideGroup = PlanCreatorUtilsV1.isStepsInsideGroup(ctx.getDependency());
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    String facilitatorType = OrchestrationFacilitatorType.CHILD;
    StepType stepType = NGSectionStepWithRollbackInfo.STEP_TYPE;
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(config.getUuid())
            .identifier(YAMLFieldNameConstants.STEPS)
            .stepType(stepType)
            .name(YAMLFieldNameConstants.STEPS)
            .stepParameters(stepParameters)
            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                       .setType(FacilitatorType.newBuilder().setType(facilitatorType).build())
                                       .build())
            .skipGraphType(SkipType.SKIP_NODE);
    // We are adding rollback adviser obtainments only in the steps which is not inside step group, since we only want
    // to invoke rollback once by the global steps field under stage
    if (!isStepsInsideGroup) {
      String stageNodeId = PlanCreatorUtilsV1.getStageNodeId(ctx.getDependency());
      String combinedRollbackNodeUuid = stageNodeId + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
      NextStepAdviserParameters nextStepDuringRollbackModeAdviserParameters =
          NextStepAdviserParameters.builder().nextNodeId(combinedRollbackNodeUuid).build();
      ByteString adviserParamsBytes =
          ByteString.copyFrom(kryoSerializer.asBytes(nextStepDuringRollbackModeAdviserParameters));
      planNodeBuilder
          .adviserObtainment(AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build())
          .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK,
              Collections.singletonList(AdviserObtainment.newBuilder()
                                            .setType(CDStepsRollbackModeAdviser.ADVISER_TYPE)
                                            .setParameters(adviserParamsBytes)
                                            .build()))
          .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK,
              Collections.singletonList(AdviserObtainment.newBuilder()
                                            .setType(CDStepsRollbackModeAdviser.ADVISER_TYPE)
                                            .setParameters(adviserParamsBytes)
                                            .build()));
    }
    return planNodeBuilder.build();
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> steps = CIPlanCreatorUtils.getStepYamlFields(config);
    if (EmptyPredicate.isEmpty(steps)) {
      return responseMap;
    }

    boolean isStageChild = isChildOfStage(ctx);
    List<YamlField> rollbackSteps = getRollbackSteps(ctx, config, isStageChild);

    List<ExecutionWrapperConfig> executionConfigs =
        steps.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());
    List<ExecutionWrapperConfig> rollbackExecutionConfigs =
        rollbackSteps.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList());

    ModuleSpecificMetadata moduleSpecificMetadata = getModuleSpecificMetadata(config);
    Map<String, String> templateTypeToFirstStepIdMap = mapModuleTemplateTypeToStepPosition(steps);
    validateTemplateUses(moduleSpecificMetadata, templateTypeToFirstStepIdMap);

    Map<String, Object> modulesImplicitNodesInfo = new HashMap<>();
    ModuleTemplatePlanCreationResults moduleTemplatePlanCreationResults = null;

    if (isStageChild && isNotEmpty(moduleSpecificMetadata.getModules())) {
      modulesImplicitNodesInfo = ciPlanCreatorUtils.getModulesImplicitNodesInfo(ctx);
    }

    if (isNotEmpty(templateTypeToFirstStepIdMap)) {
      moduleTemplatePlanCreationResults = getModuleTemplateTypeBasedPlanCreation(
          moduleSpecificMetadata, templateTypeToFirstStepIdMap, ctx, modulesImplicitNodesInfo);
    }

    if (isStageChild) {
      createModulesImplicitPlanCreators(ctx, responseMap, executionConfigs, rollbackExecutionConfigs,
          steps.get(0).getUuid(), moduleSpecificMetadata, templateTypeToFirstStepIdMap, modulesImplicitNodesInfo,
          moduleTemplatePlanCreationResults);
    }

    addStepsDependencies(ctx, steps, responseMap, moduleTemplatePlanCreationResults, templateTypeToFirstStepIdMap);

    return responseMap;
  }

  private static List<YamlField> getRollbackSteps(PlanCreationContext ctx, YamlField config, boolean isStageChild) {
    List<YamlField> rollbackSteps = new ArrayList<>();
    if (!ExecutionModeUtils.isRollbackMode(ctx.getExecutionMode())) {
      if (config.getNode().getParentNode() != null && isStageChild) {
        YamlField rollbackStepsField =
            config.getNode().getParentNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);
        if (rollbackStepsField != null) {
          rollbackSteps = CIPlanCreatorUtils.getStepYamlFields(rollbackStepsField);
        }
      }
    }
    return rollbackSteps;
  }

  private void validateTemplateUses(ModuleSpecificMetadata metadata, Map<String, String> templateToFirstStepId) {
    if (isNotEmpty(templateToFirstStepId) && isNotEmpty(metadata.getModules())) {
      Set<String> allowedTypes = metadata.getModules().stream().map(TemplateType::getName).collect(Collectors.toSet());

      Set<String> invalidTypes =
          templateToFirstStepId.keySet()
              .stream()
              .filter(type -> TemplateType.getModuleNeededMandatoryImplicitSteps().contains(type))
              .filter(type -> !allowedTypes.contains(type))
              .collect(Collectors.toSet());

      if (!invalidTypes.isEmpty()) {
        throw new InvalidYamlException("Invalid template types used: " + String.join(", ", invalidTypes)
            + " Please provide mandatory fields for specific template type");
      }
    }
  }

  private ModuleTemplatePlanCreationResults getModuleTemplateTypeBasedPlanCreation(
      ModuleSpecificMetadata moduleSpecificMetadata, Map<String, String> templateTypeToFirstStepIdMap,
      PlanCreationContext ctx, Map<String, Object> modulesImplicitNodesInfo) {
    ModuleTemplatePlanCreationResultsBuilder builder = ModuleTemplatePlanCreationResults.builder();
    Map<String, ModuleSpecificPlanCreationResult> planCreationResults = new HashMap<>();

    if (isNotEmpty(templateTypeToFirstStepIdMap)) {
      moduleSpecificMetadata.getModules()
          .stream()
          .filter(type -> templateTypeToFirstStepIdMap.containsKey(type.getName()))
          .forEach(type -> {
            String nextStepId = templateTypeToFirstStepIdMap.get(type.getName());
            Map<String, Object> specificTemplateInfo = new HashMap<>();
            if (modulesImplicitNodesInfo.containsKey(type.getName())) {
              specificTemplateInfo = (Map<String, Object>) modulesImplicitNodesInfo.get(type.getName());
            }
            TemplateTypeBasedPlanCreatorData planCreatorData =
                TemplateTypeBasedPlanCreatorData.builder().ctx(ctx).templateBasedInfo(specificTemplateInfo).build();
            MODULE_TYPE_BASED_PLAN_CREATORS.getOrDefault(type, DEFAULT_PLAN_CREATOR)
                .apply(planCreatorData, nextStepId)
                .ifPresent(result -> planCreationResults.put(type.getName(), result));
          });
    }
    return builder.planCreationResults(planCreationResults).build();
  }

  private Optional<DeployPlanCreationResult> getPreDeployPlanCreationResult(
      TemplateTypeBasedPlanCreatorData planCreatorData, String nextStepId) {
    PlanCreationContext ctx = planCreatorData.getCtx();
    if (isNotEmpty(planCreatorData.getTemplateBasedInfo())) {
      return handlePreDeployPlanCreation(ctx, nextStepId, planCreatorData.getTemplateBasedInfo());
    }
    return Optional.empty();
  }

  private Optional<IACMPlanCreationResult> getIacmPlanCreationResult(
      TemplateTypeBasedPlanCreatorData planCreatorData, String nextStepId) {
    PlanCreationContext ctx = planCreatorData.getCtx();
    if (isNotEmpty(planCreatorData.getTemplateBasedInfo())) {
      return handleIACMPlanCreation(ctx, nextStepId, planCreatorData.getTemplateBasedInfo());
    }
    return Optional.empty();
  }

  private ModuleSpecificMetadata getModuleSpecificMetadata(YamlField config) {
    YamlNode node = config.getNode();
    Set<TemplateType> templates = Arrays.stream(TemplateType.getCustomTypes())
                                      .filter(type -> TEMPLATE_PRESENT_VALIDATORS.get(type).isTemplateTypePresent(node))
                                      .collect(Collectors.toSet());
    return ModuleSpecificMetadata.builder().modules(templates).build();
  }

  private boolean isChildOfStage(PlanCreationContext ctx) {
    return UNIFIED_PARENT_NODE.equals(ctx.getCurrentField().getNode().getParentNode().getType());
  }

  private void addStepsDependencies(PlanCreationContext ctx, List<YamlField> steps,
      LinkedHashMap<String, PlanCreationResponse> responseMap,
      ModuleTemplatePlanCreationResults moduleTemplatePlanCreationResults,
      Map<String, String> templateTypeToFirstStepIdMap) {
    for (int i = 0; i < steps.size(); i++) {
      YamlField curr = steps.get(i);
      String currId = curr.getUuid();
      String nextId = (i < steps.size() - 1) ? steps.get(i + 1).getUuid() : null;

      // If first step is from template, inject service/infra before it
      // Service/infra nodes will point to current step, so we inject them but don't change nextId
      if (templateTypeToFirstStepIdMap.containsValue(currId)) {
        updateResponseMapAndGetNextId(
            responseMap, moduleTemplatePlanCreationResults, templateTypeToFirstStepIdMap, currId);
      }

      // If next step is a template type, inject service/infra before it
      // Make current step point to first service/infra node (nextId is updated)
      if (isNotEmpty(nextId) && templateTypeToFirstStepIdMap.containsValue(nextId)) {
        nextId = updateResponseMapAndGetNextId(
            responseMap, moduleTemplatePlanCreationResults, templateTypeToFirstStepIdMap, nextId);
      }

      JsonNode stepRunNode = getStepRunNode(curr);
      JsonNode stepEnvNode = getStepEnvNode(stepRunNode);

      if (isRenderingStep(stepEnvNode)) {
        boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());
        // Propagate pipeline + stage env vars so the rendering plugin task inherits them (parity with run steps).
        renderingPlanCreator.addRenderingNode(
            responseMap, curr, nextId, stepEnvNode, isStepInsideRollback, getMergedPipelineAndStageEnvVars(ctx));
      } else {
        // Preserve existing parentInfo from context (STEP_GROUP_DELEGATES, STAGE_DELEGATES, etc.)
        HarnessStruct.Builder parentInfoBuilder = HarnessStruct.newBuilder();
        if (ctx.getDependency().getParentInfo() != null) {
          parentInfoBuilder.putAllData(ctx.getDependency().getParentInfo().getDataMap());
        }
        Dependency dependency = isNotEmpty(nextId) ? getDependencyMetadata(ctx, nextId)
                                                   : Dependency.newBuilder()
                                                         .setNodeMetadata(ctx.getDependency().getNodeMetadata())
                                                         .setParentInfo(parentInfoBuilder.build())
                                                         .build();
        responseMap.put(curr.getUuid(),
            PlanCreationResponse.builder()
                .dependencies(Dependencies.newBuilder()
                                  .putDependencies(curr.getUuid(), curr.getYamlPath())
                                  .putDependencyMetadata(curr.getUuid(), dependency)
                                  .build())
                .build());
      }
    }
  }

  private String updateResponseMapAndGetNextId(LinkedHashMap<String, PlanCreationResponse> responseMap,
      ModuleTemplatePlanCreationResults moduleTemplatePlanCreationResults,
      Map<String, String> templatesBasedStepsOverrides, String stepId) {
    String updatedNextId = stepId;
    if (isNotEmpty(moduleTemplatePlanCreationResults.getPlanCreationResults())
        && isNotEmpty(templatesBasedStepsOverrides)) {
      // Find the template type that corresponds to this step ID
      Optional<String> templateType = templatesBasedStepsOverrides.entrySet()
                                          .stream()
                                          .filter(entry -> entry.getValue().equals(stepId))
                                          .map(Map.Entry::getKey)
                                          .findFirst();

      if (templateType.isPresent()
          && moduleTemplatePlanCreationResults.getPlanCreationResults().containsKey(templateType.get())) {
        ModuleSpecificPlanCreationResult moduleSpecificPlanCreationResult =
            moduleTemplatePlanCreationResults.getPlanCreationResults().get(templateType.get());

        MODULE_TYPE_BASED_RESPONSE_HANDLERS.getOrDefault(templateType.get(), DEFAULT_RESPONSE_HANDLER)
            .accept(responseMap, moduleSpecificPlanCreationResult);
        String firstNodeIdFromTemplate = moduleSpecificPlanCreationResult.getFirstNodeId();
        updatedNextId = getNextNodeId(firstNodeIdFromTemplate, stepId);
      }
    }
    return updatedNextId;
  }

  private Dependency getDependencyMetadata(PlanCreationContext ctx, String nextId) {
    HarnessStruct.Builder parentInfoBuilder = HarnessStruct.newBuilder();
    // Preserve existing parentInfo from context (STEP_GROUP_DELEGATES, STAGE_DELEGATES, etc.)
    if (ctx.getDependency().getParentInfo() != null) {
      parentInfoBuilder.putAllData(ctx.getDependency().getParentInfo().getDataMap());
    }
    return Dependency.newBuilder()
        .setNodeMetadata(
            HarnessStruct.newBuilder()
                .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextId).build())
                .putAllData(ctx.getDependency().getNodeMetadata().getDataMap())
                .putData("parent", HarnessValue.newBuilder().setStringValue("steps").build())
                .build())
        .setParentInfo(parentInfoBuilder.build())
        .build();
  }

  private void createModulesImplicitPlanCreators(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      List<ExecutionWrapperConfig> executionWrapperConfigs, List<ExecutionWrapperConfig> rollbackExecutionConfigs,
      String firstStepNodeId, ModuleSpecificMetadata moduleSpecificMetadata,
      Map<String, String> templateTypeToFirstStepIdMap, Map<String, Object> moduleImplicitNodesInfo,
      ModuleTemplatePlanCreationResults moduleTemplatePlanCreationResults) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    Infrastructure infrastructure = getInfrastructure(ctx);
    CodeBase codeBase = getCodeBase(ctx);
    AbstractStageNodeV1 stageNode = getStageNode(ctx);

    // do in reverse order
    // inject codebase plugin plan creator
    Map<String, ModuleSpecificPlanCreationResult> templateResults = new LinkedHashMap<>();
    String nextStepId = handleModuleSpecificPlanCreations(ctx, moduleSpecificMetadata, templateTypeToFirstStepIdMap,
        moduleImplicitNodesInfo, responseMap, templateResults, firstStepNodeId);

    // If first step is a deploy template, use the first service/infra node ID instead
    nextStepId = updateNextIdIfFirstStepIsFromTemplate(
        firstStepNodeId, templateTypeToFirstStepIdMap, moduleTemplatePlanCreationResults, nextStepId);

    String gitCloneChildNodeID =
        createGitClonePlanCreator(ctx, responseMap, executionWrapperConfigs, codeBase, nextStepId);

    String initialiseStepNextNodeId = getNextNodeId(gitCloneChildNodeID, nextStepId);
    DeployPlanCreationResult deployPlanResult = getDeployPlanResult(templateResults);

    ParameterField<List<String>> sharedPaths = stageNode.getSharedPaths();
    Map<String, String> stagePermissions =
        (Map<String, String>) ciPlanCreatorUtils
            .getDeserializedObjectFromDependency(ctx.getDependency(), UnifiedStagePMSPlanCreator.PERMISSIONS)
            .orElse(null);
    PlanCreationResponse planCreationResponse = initializeStepPlanCreatorV1.createPlan(ctx, stageNode.getId(),
        stageNode.getName(), codeBase, infrastructure, executionWrapperConfigs, rollbackExecutionConfigs,
        initialiseStepNextNodeId, deployPlanResult, moduleImplicitNodesInfo, sharedPaths, stagePermissions);

    planCreationResponseMap.put(planCreationResponse.getPlanNode().getUuid(), planCreationResponse);
    planCreationResponseMap.putAll(responseMap);
  }

  private static String updateNextIdIfFirstStepIsFromTemplate(String firstStepNodeId,
      Map<String, String> templateTypeToFirstStepIdMap,
      ModuleTemplatePlanCreationResults moduleTemplatePlanCreationResults, String nextStepId) {
    if (templateTypeToFirstStepIdMap.containsValue(firstStepNodeId) && moduleTemplatePlanCreationResults != null
        && isNotEmpty(moduleTemplatePlanCreationResults.getPlanCreationResults())) {
      // Find the template type that corresponds to the first step
      Optional<String> templateType = templateTypeToFirstStepIdMap.entrySet()
                                          .stream()
                                          .filter(entry -> entry.getValue().equals(firstStepNodeId))
                                          .map(Map.Entry::getKey)
                                          .findFirst();
      if (templateType.isPresent()
          && moduleTemplatePlanCreationResults.getPlanCreationResults().containsKey(templateType.get())) {
        ModuleSpecificPlanCreationResult moduleSpecificPlanCreationResult =
            moduleTemplatePlanCreationResults.getPlanCreationResults().get(templateType.get());
        if (isNotEmpty(moduleSpecificPlanCreationResult.getFirstNodeId())) {
          nextStepId = moduleSpecificPlanCreationResult.getFirstNodeId();
        }
      }
    }
    return nextStepId;
  }

  private Infrastructure getInfrastructure(PlanCreationContext ctx) {
    return (
        Infrastructure) ciPlanCreatorUtils.getDeserializedObjectFromDependency(ctx.getDependency(), "infrastructure")
        .orElseThrow(() -> new InvalidRequestException("Infrastructure cannot be empty"));
  }

  private CodeBase getCodeBase(PlanCreationContext ctx) {
    return (CodeBase) ciPlanCreatorUtils.getDeserializedObjectFromDependency(ctx.getDependency(), "codebase")
        .orElse(null);
  }

  private AbstractStageNodeV1 getStageNode(PlanCreationContext ctx) {
    return (
        AbstractStageNodeV1) ciPlanCreatorUtils.getDeserializedObjectFromDependency(ctx.getDependency(), "stageNode")
        .orElseThrow(() -> new InvalidRequestException("IntegrationStageNode cannot be empty"));
  }

  private String handleModuleSpecificPlanCreations(PlanCreationContext ctx,
      ModuleSpecificMetadata moduleSpecificMetadata, Map<String, String> templateTypeToFirstStepIdMap,
      Map<String, Object> moduleImplicitNodesInfo, LinkedHashMap<String, PlanCreationResponse> responseMap,
      Map<String, ModuleSpecificPlanCreationResult> templateResults, String firstStepNodeId) {
    String nextStepId = firstStepNodeId;

    for (TemplateType templateType : TemplateType.getCustomTypesInPriorityOrder()) {
      if (!moduleSpecificMetadata.getModules().contains(templateType)) {
        continue;
      }

      if (isModuleTemplateImplicitStepPositionOverridden(
              moduleSpecificMetadata, templateTypeToFirstStepIdMap, templateType)) {
        continue;
      }

      Map<String, Object> specificTemplateInfo =
          (Map<String, Object>) moduleImplicitNodesInfo.getOrDefault(templateType.getName(), new HashMap<>());

      TemplateTypeBasedPlanCreatorData planCreatorData =
          TemplateTypeBasedPlanCreatorData.builder().ctx(ctx).templateBasedInfo(specificTemplateInfo).build();

      Optional<? extends ModuleSpecificPlanCreationResult> planResult =
          MODULE_TYPE_BASED_PLAN_CREATORS.getOrDefault(templateType, DEFAULT_PLAN_CREATOR)
              .apply(planCreatorData, nextStepId);

      if (planResult.isPresent()) {
        ModuleSpecificPlanCreationResult result = planResult.get();
        templateResults.put(templateType.getName(), result);
        MODULE_TYPE_BASED_RESPONSE_HANDLERS.getOrDefault(templateType.getName(), DEFAULT_RESPONSE_HANDLER)
            .accept(responseMap, result);

        if (isNotEmpty(result.getFirstNodeId())) {
          nextStepId = result.getFirstNodeId();
        }
      }
    }

    return nextStepId;
  }

  private DeployPlanCreationResult getDeployPlanResult(Map<String, ModuleSpecificPlanCreationResult> templateResults) {
    return Optional.ofNullable(templateResults.get(TemplateType.DEPLOY.getName()))
        .map(DeployPlanCreationResult.class ::cast)
        .orElse(null);
  }

  private boolean isModuleTemplateImplicitStepPositionOverridden(ModuleSpecificMetadata moduleSpecificMetadata,
      Map<String, String> templateTypeToFirstStepIdMap, TemplateType templateType) {
    return moduleSpecificMetadata.getModules().contains(templateType)
        && templateTypeToFirstStepIdMap.containsKey(templateType.getName());
  }

  private static void addDeployPlanCreationResponse(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeployPlanCreationResult deployPlanCreationResult) {
    if (isNotEmpty(deployPlanCreationResult.getSvcPlanCreationResponses())) {
      planCreationResponseMap.putAll(deployPlanCreationResult.getSvcPlanCreationResponses());
    }
    if (isNotEmpty(deployPlanCreationResult.getInfraPlanCreationResponses())) {
      planCreationResponseMap.putAll(deployPlanCreationResult.getInfraPlanCreationResponses());
    }
    if (isNotEmpty(deployPlanCreationResult.getRcPlanCreationResponse())) {
      planCreationResponseMap.putAll(deployPlanCreationResult.getRcPlanCreationResponse());
    }
    if (isNotEmpty(deployPlanCreationResult.getRenderingCreationResponse())) {
      planCreationResponseMap.putAll(deployPlanCreationResult.getRenderingCreationResponse());
    }
  }

  private Optional<DeployPlanCreationResult> handlePreDeployPlanCreation(
      PlanCreationContext ctx, String nextStepId, Map<String, Object> deployModuleNodesInfo) {
    // Fail first if project is frozen
    failIfProjectIsFrozen(ctx);

    if (isEmpty(deployModuleNodesInfo)) {
      return Optional.empty();
    }

    String serviceNodeID = null;
    String infraNodeId = null;
    LinkedHashMap<String, PlanCreationResponse> svcPlanCreationResponses = new LinkedHashMap<>();
    LinkedHashMap<String, PlanCreationResponse> infraPlanCreationResponses = new LinkedHashMap<>();
    LinkedHashMap<String, PlanCreationResponse> resourceConstraintPlanCreationResponse = new LinkedHashMap<>();
    LinkedHashMap<String, PlanCreationResponse> renderingPlanCreationResponse = new LinkedHashMap<>();

    DeployPlanCreationResultBuilder deployPlanCreationResultBuilder = DeployPlanCreationResult.builder();

    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = getMergedPipelineAndStageEnvVars(ctx);
    final boolean isStepInsideRollback = PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency());

    // Adding resource constraint dependency
    String resourceConstraintNodeId = null;
    if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)
        && deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      resourceConstraintPlanCreationResponse =
          UnifiedStageResourceConstraintPlanCreatorUtils.addResourceConstraintDependency(
              ctx, RC_STEP_ADD_CONDITION, isRCQueueProjectScoped(ctx), nextStepId);
      resourceConstraintNodeId = resourceConstraintPlanCreationResponse.keySet().iterator().next();
    }

    String renderingNodeId = null;
    String renderingNextNodeId = getNextNodeId(resourceConstraintNodeId, nextStepId);

    boolean serviceHooksEnabled = isServiceHooksEnabled(ctx);
    renderingNodeId =
        addRenderingNodes(nextStepId, deployModuleNodesInfo, renderingPlanCreationResponse, isStepInsideRollback,
            resourceConstraintNodeId, renderingNodeId, renderingNextNodeId, envVars, serviceHooksEnabled);

    if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      infraNodeId = UUIDGenerator.generateUuid();
      String infraNextNodeId = getNextNodeId(renderingNodeId, resourceConstraintNodeId, nextStepId);
      infraPlanCreationResponses = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, infraNextNodeId, infraNodeId, deployModuleNodesInfo, isStepInsideRollback, envVars);
    }

    if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
      String serviceNextNode = getNextNodeId(infraNodeId, renderingNodeId, resourceConstraintNodeId, nextStepId);
      serviceNodeID = UUIDGenerator.generateUuid();
      svcPlanCreationResponses = UnifiedStageServicePlanCreatorUtils.addServiceNode(kryoSerializer, serviceNextNode,
          serviceNodeID, deployModuleNodesInfo, isStepInsideRollback, envVars, serviceHooksEnabled);
    }

    setDeployModuleEntitiesIds(deployPlanCreationResultBuilder, deployModuleNodesInfo);

    DeployPlanCreationResult deployPlanCreationResult =
        deployPlanCreationResultBuilder.serviceNodeID(serviceNodeID)
            .infraNodeId(infraNodeId)
            .svcPlanCreationResponses(svcPlanCreationResponses)
            .infraPlanCreationResponses(infraPlanCreationResponses)
            .rcPlanCreationResponse(resourceConstraintPlanCreationResponse)
            .renderingCreationResponse(renderingPlanCreationResponse)
            .build();
    return Optional.of(deployPlanCreationResult);
  }

  // Merge pipeline + stage env vars (stage overrides pipeline) from parentInfo for propagation to implicit steps.
  private ParameterField<Map<String, ParameterField<JsonNode>>> getMergedPipelineAndStageEnvVars(
      PlanCreationContext ctx) {
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars =
        planCreatorEnvVarHelper.retrieveEnvVars(ctx, PlanCreatorConstants.PIPELINE_ENV);
    ParameterField<Map<String, ParameterField<JsonNode>>> stageEnvVars =
        planCreatorEnvVarHelper.retrieveEnvVars(ctx, PlanCreatorConstants.STAGE_ENV);
    envVars.obtainValue().putAll(stageEnvVars.obtainValue());
    return envVars;
  }

  private String addRenderingNodes(String nextStepId, Map<String, Object> deployModuleNodesInfo,
      LinkedHashMap<String, PlanCreationResponse> renderingPlanCreationResponse, boolean isStepInsideRollback,
      String resourceConstraintNodeId, String renderingNodeId, String templatingNextNodeId,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, boolean serviceHooksEnabled) {
    if (deployModuleNodesInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
      // Chain: RenderingStep → TemplatingStep → next
      // Template hooks (pre/post) run as internal chain links inside TemplatingStep
      String templatingNodeId = addManifestTemplatingNode(nextStepId, renderingPlanCreationResponse,
          isStepInsideRollback, resourceConstraintNodeId, templatingNextNodeId, envVars);
      String renderingNextNodeId = getNextNodeId(templatingNodeId, templatingNextNodeId);
      renderingNodeId = addExpressionRenderingNode(nextStepId, renderingPlanCreationResponse, isStepInsideRollback,
          templatingNodeId, renderingNextNodeId, envVars);
    }
    return renderingNodeId;
  }

  private String addExpressionRenderingNode(String nextStepId,
      LinkedHashMap<String, PlanCreationResponse> renderingPlanCreationResponse, boolean isStepInsideRollback,
      String templatingNodeId, String renderingNextNodeId,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    String renderingNodeId;
    try {
      String yamlField = "---\n"
          + "name: \"Harness Manifest Rendering\"\n"
          + "id: \"harnessRendering\"\n";

      YamlField curr = YamlUtils.injectUuidInYamlField(yamlField);

      renderingNodeId = renderingPlanCreator.addRenderingNode(
          renderingPlanCreationResponse, curr, renderingNextNodeId, null, isStepInsideRollback, envVars);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add implicit rendering step plan");
    }
    return renderingNodeId;
  }

  private String addManifestTemplatingNode(String nextStepId,
      LinkedHashMap<String, PlanCreationResponse> renderingPlanCreationResponse, boolean isStepInsideRollback,
      String resourceConstraintNodeId, String renderingNextNodeId,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    String templatingNodeId;
    try {
      String yamlField = "---\n"
          + "name: \"Harness Manifest Templating\"\n"
          + "id: \"harnessTemplating\"\n";

      YamlField curr = YamlUtils.injectUuidInYamlField(yamlField);

      templatingNodeId = templatingPlanCreator.addTemplatingNode(
          renderingPlanCreationResponse, curr, renderingNextNodeId, isStepInsideRollback, envVars);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add implicit rendering step plan");
    }
    return templatingNodeId;
  }

  private static void addIacmPlanCreationResponse(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      IACMPlanCreationResult iacmPlanCreationResult) {
    if (isNotEmpty(iacmPlanCreationResult.getIacmPlanCreationResponses())) {
      planCreationResponseMap.putAll(iacmPlanCreationResult.getIacmPlanCreationResponses());
    }
  }

  private Optional<IACMPlanCreationResult> handleIACMPlanCreation(
      PlanCreationContext ctx, String childNodeId, Map<String, Object> iacmModuleNodesInfo) {
    if (isNotEmpty(iacmModuleNodesInfo)) {
      String iacmNodeId = UUIDGenerator.generateUuid();
      LinkedHashMap<String, PlanCreationResponse> iacmPlanCreationResponses =
          IACMPlanCreatorUtils.addIACMNode(kryoSerializer, childNodeId, iacmNodeId, iacmModuleNodesInfo,
              PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency()), ctx);
      return Optional.of(IACMPlanCreationResult.builder()
                             .iacmPlanCreationResponses(iacmPlanCreationResponses)
                             .iacmNodeId(iacmNodeId)
                             .build());
    }
    return Optional.empty();
  }

  private static void setDeployModuleEntitiesIds(
      DeployPlanCreationResultBuilder deployPlanCreationResultBuilder, Map<String, Object> deployModuleEntitiesIDs) {
    if (deployModuleEntitiesIDs.containsKey(YAMLFieldNameConstants.SERVICE)) {
      deployPlanCreationResultBuilder.serviceRef((String) deployModuleEntitiesIDs.get(YAMLFieldNameConstants.SERVICE));
    }
    if (deployModuleEntitiesIDs.containsKey(YAMLFieldNameConstants.ENVIRONMENT)) {
      deployPlanCreationResultBuilder.envRef((String) deployModuleEntitiesIDs.get(YAMLFieldNameConstants.ENVIRONMENT));
    }
    if (deployModuleEntitiesIDs.containsKey(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      deployPlanCreationResultBuilder.infraId(
          (String) deployModuleEntitiesIDs.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE));
    }
  }

  /*
  Method return first non blank step id, Provide string parameters in specific order according to the use-case
   */
  private String getNextNodeId(String... values) {
    return Arrays.stream(values).filter(StringUtils::isNotBlank).findFirst().orElse(null);
  }

  private String createGitClonePlanCreator(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> responseMap, List<ExecutionWrapperConfig> executionWrapperConfigs,
      CodeBase codeBase, String childNodeID) {
    if (codeBase != null) {
      Pair<PlanCreationResponse, JsonNode> plan = gitClonePlanCreator.createPlan(ctx, codeBase, childNodeID);
      PlanNode planNode = plan.getKey().getPlanNode();
      responseMap.put(planNode.getUuid(), plan.getLeft());
      executionWrapperConfigs.add(0,
          ExecutionWrapperConfig.builder()
              .uuid(planNode.getUuid())
              .step(plan.getRight())
              .version(HarnessYamlVersion.V1)
              .build());
      return planNode.getUuid();
    }
    return null;
  }

  private JsonNode getStepRunNode(YamlField curr) {
    if (curr != null && curr.getNode().getCurrJsonNode().get("run") != null) {
      return curr.getNode().getCurrJsonNode().get("run");
    }
    return null;
  }

  JsonNode getStepEnvNode(JsonNode stepRunNode) {
    if (stepRunNode != null && stepRunNode.get("env") != null) {
      return stepRunNode.get("env");
    }
    return null;
  }

  private boolean isRenderingStep(JsonNode stepEnvNode) {
    return stepEnvNode != null && stepEnvNode.get(PLUGIN_RENDERING_STEP) != null
        && Boolean.TRUE.equals(stepEnvNode.get(PLUGIN_RENDERING_STEP).asBoolean());
  }

  private boolean isServiceHooksEnabled(PlanCreationContext ctx) {
    return serviceHookTaskHelper.isServiceHooksEnabled(ctx.getAccountIdentifier());
  }

  private boolean isRCQueueProjectScoped(PlanCreationContext ctx) {
    return featureFlagService.isEnabled(
               FeatureName.CDS_PROJECT_SCOPED_RESOURCE_CONSTRAINT_QUEUE, ctx.getAccountIdentifier())
        || parseBoolean(getResponse(settingsClient.getSetting(PROJECT_SCOPED_RESOURCE_CONSTRAINT_SETTING_ID,
                                        ctx.getAccountIdentifier(), null, null))
                            .getValue());
  }

  private void failIfProjectIsFrozen(PlanCreationContext ctx) {
    boolean shouldDisable = false;
    try {
      String accountIdentifier = ctx.getAccountIdentifier();
      String orgIdentifier = ctx.getOrgIdentifier();
      String projectIdentifier = ctx.getProjectIdentifier();
      String pipelineIdentifier = ctx.getPipelineIdentifier();
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(accountIdentifier, orgIdentifier, projectIdentifier,
              accessControlClient, PrincipalUtility.getExecutionPrincipalInfo(ctx))) {
        return;
      }
      ShouldDisableDeploymentFreezeResponseDTO shouldDisableDeploymentFreezeResponseDTO =
          getResponse(ngDeploymentFreezeResourceClient.shouldDisableDeployment(
              accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier));
      shouldDisable = shouldDisableDeploymentFreezeResponseDTO.isShouldDisable();
    } catch (Exception e) {
      log.error("Unified Freeze: Failure occurred when evaluating execution should fail due to freeze at the time of "
          + "plan creation");
    }
    if (shouldDisable) {
      throw new NGFreezeException("Execution can't be performed because project is frozen");
    }
  }

  /*
  This method returns map, where key is template type (deploy, iacm, test etc) where value is where the first step id of
  the steps which are part of resolved template
   */
  private Map<String, String> mapModuleTemplateTypeToStepPosition(List<YamlField> steps) {
    if (isEmpty(steps)) {
      return Collections.emptyMap();
    }

    Set<String> customTemplateTypes =
        Arrays.stream(TemplateType.getCustomTypes()).map(TemplateType::getName).collect(Collectors.toSet());

    Map<String, String> templateTypeToStepId = new HashMap<>();
    steps.stream()
        .map(YamlField::getNode)
        .filter(node -> !JsonNodeUtils.isNull(node.getCurrJsonNode().get(YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE)))
        .forEach(node -> {
          String templateType = node.getCurrJsonNode().get(YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE).asText();
          String stepId = node.getUuid();

          if (!customTemplateTypes.contains(templateType)) {
            return; // Skip non-custom template types
          }

          // if a specific module type template is added multiple times, only consider first occurrence
          if (templateTypeToStepId.containsKey(templateType)) {
            return;
          }
          // Include the first step if it's a template type, so service/infra can be injected before it
          templateTypeToStepId.put(templateType, stepId);
        });

    return templateTypeToStepId;
  }
}
