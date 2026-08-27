/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import static io.harness.pms.utils.NGPipelineSettingsConstant.MAX_STAGE_TIMEOUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.logging.AutoLogContext;
import io.harness.plancreator.PmsStepPlanCreatorUtils;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.utils.GitxBranchContextUtils;
import io.harness.pms.utils.SdkTimeoutObtainmentUtils;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.steps.pipelinestage.PipelineStageOutputs;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineStagePlanCreator implements PartialPlanCreator<PipelineStageNode> {
  @Inject private PipelineStageHelper pipelineStageHelper;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject KryoSerializer kryoSerializer;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineEnforcementService pipelineEnforcementService;
  @Inject private BarrierService barrierService;
  @Inject private PipelineBarrierExtractor pipelineBarrierExtractor;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  public PipelineStageStepParameters getStepParameter(PipelineStageConfig config, YamlField pipelineInputs,
      String stageNodeId, String childPipelineVersion, PipelineStageNode stageNode) {
    return PipelineStageStepParameters.builder()
        .identifier(stageNode.getIdentifier())
        .name(stageNode.getName())
        .description(ParameterField.isNull(stageNode.getDescription()) ? null : stageNode.getDescription().getValue())
        .tags(stageNode.getTags())
        .pipeline(config.getPipeline())
        .org(config.getOrg())
        .project(config.getProject())
        .gitBranch(config.getGitBranch() != null ? config.getGitBranch().getValue() : null)
        .stageNodeId(stageNodeId)
        .inputSetReferences(config.getInputSetReferences())
        .outputs(ParameterField.createValueField(PipelineStageOutputs.getMapOfString(config.getOutputs())))
        .pipelineInputsJsonNode(pipelineStageHelper.getInputSetJsonNode(pipelineInputs, childPipelineVersion))
        .build();
  }

  public void setSourcePrincipal(PlanCreationContext ctx) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipal(
        ctx.getAccountIdentifier(), ctx.getPrincipalInfo(), ctx.getTriggerInfo().getTriggeredBy());
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PipelineStageNode stageNode) {
    PipelineStageConfig config = stageNode.getPipelineStageConfig();
    if (config == null) {
      throw new InvalidRequestException("Pipeline Stage Yaml does not contain spec");
    }

    pipelineEnforcementService.validatePipelineChainingEnforcement(ctx.getAccountIdentifier());

    // Principal is added to fetch Git Entity. GitContext is to set GitContext for child pipeline. This was missed where
    // parent pipeline is in non-default branch. With this change, chained pipeline will be executed with same branch as
    // that of parent pipeline branch
    setSourcePrincipal(ctx);

    String childBranch = config.getGitBranch() != null ? config.getGitBranch().getValue() : null;

    try (AutoLogContext ignore = GitAwareContextHelper.autoLogContext()) {
      log.info("Retrieving nested pipeline for pipeline stage");
      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(ctx.getAccountIdentifier(), config.getOrg(), config.getProject());
      if (childBranch == null) {
        setGitContextForChildPipeline(ctx);
      }
      // Build a child-scoped GitSyncBranchContext to override only for this fetch
      GitEntityInfo requestInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      Optional<PipelineEntity> childPipelineEntity = GitxBranchContextUtils.withBranch(requestInfo, childBranch,
          ()
              -> pmsPipelineService.getPipeline(ctx.getAccountIdentifier(), config.getOrg(), config.getProject(),
                  config.getPipeline(), false, false, false, true, scopeInfo, isParentIdQueryingEnabled));

      if (!childPipelineEntity.isPresent()) {
        throw new InvalidRequestException(String.format("Child pipeline does not exists %s ", config.getPipeline()));
      }

      String parentPipelineIdentifier = ctx.getPipelineIdentifier();

      // Validate under the same child branch context so nested chaining checks resolve YAML from the correct branch
      GitxBranchContextUtils.withBranch(requestInfo, childBranch, () -> {
        pipelineStageHelper.validateNestedChainedPipeline(
            childPipelineEntity.get(), stageNode.getName(), parentPipelineIdentifier, scopeInfo);
        return null;
      });
      pipelineStageHelper.validateFailureStrategy(stageNode.getFailureStrategies());

      if (StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())
          && !ctx.getFeatureFlagValue(FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES.toString())) {
        throw new InvalidRequestException(
            String.format("Strategy is not supported for Pipeline stage %s", stageNode.getIdentifier()));
      }

      if (StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())) {
        stageNode.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getIdentifier()));
        stageNode.setName(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getName()));
      }

      Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
      Map<String, ByteString> metadataMap = new HashMap<>();

      // This will be empty till we enable strategy support for Pipeline Stage
      addDependencyForStrategy(ctx, stageNode, dependenciesNodeMap, metadataMap);

      // Here planNodeId is used to support strategy. Same node id will be passed to child execution for navigation to
      // parent execution
      String planNodeId = StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid());
      List<AdviserObtainment> adviserObtainmentFromMetaData = PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(
          ctx, null, kryoSerializer, ctx.getCurrentField(), true, InjectUtils.IsFlexibleTemplatesEnabled(ctx));

      YamlField childPipelineInputs = pipelineBarrierExtractor.getChildPipelineInputsField(ctx);
      List<String> barrierIdentifiers = pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(childPipelineInputs);

      if (!barrierIdentifiers.isEmpty()) {
        populateDummyEntriesForBarriersInChildPipeline(barrierIdentifiers, planNodeId, ctx.getExecutionUuid());
      }

      PlanNodeBuilder builder =
          PlanNode.builder()
              .uuid(planNodeId)
              .name(stageNode.getName())
              .identifier(stageNode.getIdentifier())
              .group(StepCategory.STAGE.name())
              .stepType(PipelineStageStep.STEP_TYPE)
              .expressionMode(
                  ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED) // Do not want null if expression is
              // unresolved. Used in envV2 implementation
              .stepParameters(getStepParameter(config,
                  ctx.getCurrentField()
                      .getNode()
                      .getField(YAMLFieldNameConstants.SPEC)
                      .getNode()
                      .getField(YAMLFieldNameConstants.INPUTS),
                  planNodeId, childPipelineEntity.get().getHarnessVersion(), stageNode))
              .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
              .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen()))
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                      .build())
              .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, adviserObtainmentFromMetaData)
              .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, adviserObtainmentFromMetaData)
              .adviserObtainments(adviserObtainmentFromMetaData);
      if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
        builder.executionInputTemplate(ctx.getExecutionInputTemplate());
      }

      // Add stage timeout obtainment to ensure Pipeline Stage respects timeout/expire configuration
      ParameterField<Timeout> timeout = SdkTimeoutObtainmentUtils.getTimeout(stageNode.getTimeout(),
          ctx.getTimeoutDuration(MAX_STAGE_TIMEOUT.getName()),
          ctx.getFeatureFlagValue(FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG.toString()));
      if (!ParameterField.isBlank(timeout)) {
        builder.timeoutObtainment(SdkTimeoutObtainment.builder()
                                      .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                                      .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                                      .timeout(TimeoutUtils.getParameterTimeoutString(timeout))
                                                      .build())
                                      .build());
      }

      // Dependencies is added for strategy node
      return PlanCreationResponse.builder()
          .graphLayoutResponse(getLayoutNodeInfo(ctx, stageNode))
          .planNode(builder.build())
          .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                            .toBuilder()
                            .putDependencyMetadata(
                                stageNode.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                            .build())
          .build();
    }
  }

  private void populateDummyEntriesForBarriersInChildPipeline(
      List<String> barrierIdentifiers, String planNodeId, String executionUuid) {
    for (String barrierRef : barrierIdentifiers) {
      if (barrierRef.startsWith(YAMLFieldNameConstants.PARENT_DOT)) {
        barrierRef = barrierRef.split("\\.")[1];
      }
      barrierService.upsertBarrierExecutionInstance(
          null, barrierRef, barrierRef, null, null, null, null, null, null, true, executionUuid, planNodeId);
    }
  }

  private void setGitContextForChildPipeline(PlanCreationContext ctx) {
    EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(ctx.getGitSyncBranchContext());
    PipelineGitXHelper.setupEntityDetails(entityGitDetails);
  }

  private void addDependencyForStrategy(PlanCreationContext ctx, PipelineStageNode stageNode,
      Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(),
        stageNode.getIdentifier(), stageNode.getName(), dependenciesNodeMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false, ctx), true);
  }

  // This is for graph view of strategy execution
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, PipelineStageNode config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(context, stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}
