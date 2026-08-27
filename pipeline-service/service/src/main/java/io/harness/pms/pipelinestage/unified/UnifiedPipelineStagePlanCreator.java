/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.unified;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.steps.v1.FailureStrategiesUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.PipelineStageStepParameters.PipelineStageStepParametersBuilder;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.utils.GitxBranchContextUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.pipelinestage.PipelineStageOutputs;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.when.utils.v1.RunInfoUtilsV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.action.FailureStrategyActionConfigV1;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class UnifiedPipelineStagePlanCreator implements PartialPlanCreator<UnifiedPipelineStageNode> {
  @Inject private PipelineStageHelper pipelineStageHelper;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject KryoSerializer kryoSerializer;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineEnforcementService pipelineEnforcementService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PipelineStageHelperV1 pipelineStageHelperV1;

  @Override
  public Class<UnifiedPipelineStageNode> getFieldClass() {
    return UnifiedPipelineStageNode.class;
  }

  @Override
  public UnifiedPipelineStageNode getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), UnifiedPipelineStageNode.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse unified pipeline stage yaml.", e);
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  public void setSourcePrincipal(PlanCreationContext ctx) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipal(
        ctx.getAccountIdentifier(), ctx.getPrincipalInfo(), ctx.getTriggerInfo().getTriggeredBy());
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  private PipelineStageStepParameters getStepParameters(
      UnifiedPipelineStageNode stageNode, YamlField pipelineInputs, String stageNodeId, String childPipelineVersion) {
    UnifiedPipelineStageInfo stageInfo = stageNode.getUnifiedPipelineStageInfo();
    if (stageInfo == null) {
      throw new InvalidRequestException("Pipeline Stage yaml does not contain pipeline info");
    }
    return getStageStepParameters(stageInfo, pipelineInputs, stageNodeId, childPipelineVersion);
  }

  public PipelineStageStepParameters getStageStepParameters(
      UnifiedPipelineStageInfo stageInfo, YamlField pipelineInputs, String stageNodeId, String childPipelineVersion) {
    IdentifierRef identifierRef = pipelineStageHelperV1.getIdentifierRef(stageInfo.getUses(), "");
    PipelineStageStepParametersBuilder builder = PipelineStageStepParameters.builder()
                                                     .pipeline(identifierRef.getIdentifier())
                                                     .org(identifierRef.getOrgIdentifier())
                                                     .project(identifierRef.getProjectIdentifier())
                                                     .stageNodeId(stageNodeId)
                                                     .gitBranch(stageInfo.getRef());

    UnifiedPipelineStageWithInfo withInfo = stageInfo.getWith();
    ParameterField<Map<String, ParameterField<String>>> outputsField =
        ParameterField.createValueField(Collections.emptyMap());
    if (withInfo != null) {
      if (isNotEmpty(withInfo.getInputSets())) {
        builder.inputSetReferences(withInfo.getInputSets());
      } else {
        builder.pipelineInputsJsonNode(pipelineStageHelper.getInputSetJsonNode(pipelineInputs, childPipelineVersion));
      }
      if (isNotEmpty(withInfo.getOutputs())) {
        outputsField = ParameterField.createValueField(PipelineStageOutputs.getMapOfString(withInfo.getOutputs()));
      }
    }
    builder.outputs(outputsField);
    return builder.build();
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, UnifiedPipelineStageNode stageNode) {
    UnifiedPipelineStageInfo stageInfo = stageNode.getUnifiedPipelineStageInfo();
    if (stageInfo == null) {
      throw new InvalidRequestException("Pipeline Stage yaml does not contain pipeline info");
    }

    pipelineEnforcementService.validatePipelineChainingEnforcement(ctx.getAccountIdentifier());

    // Set principal for fetching child pipeline
    setSourcePrincipal(ctx);

    IdentifierRef identifierRef =
        pipelineStageHelperV1.getIdentifierRef(stageInfo.getUses(), ctx.getAccountIdentifier());
    String orgIdentifier = identifierRef.getOrgIdentifier();
    String projectIdentifier = identifierRef.getProjectIdentifier();
    String pipelineIdentifier = identifierRef.getIdentifier();

    String childBranch = stageInfo.getRef();

    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(ctx.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    GitEntityInfo requestInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Optional<PipelineEntity> childPipelineEntity = GitxBranchContextUtils.withBranch(requestInfo, childBranch,
        ()
            -> pmsPipelineService.getPipeline(ctx.getAccountIdentifier(), orgIdentifier, projectIdentifier,
                pipelineIdentifier, false, false, false, true, scopeInfo, isParentIdQueryingEnabled));

    if (childPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("Child pipeline does not exist: %s", pipelineIdentifier));
    }

    String parentPipelineIdentifier = ctx.getPipelineIdentifier();
    GitxBranchContextUtils.withBranch(requestInfo, childBranch, () -> {
      pipelineStageHelper.validateNestedChainedPipeline(
          childPipelineEntity.get(), stageNode.getName(), parentPipelineIdentifier, scopeInfo);
      return null;
    });
    pipelineStageHelperV1.validateFailureStrategy(stageNode.getOnFailure());

    if (ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY) != null) {
      throw new InvalidRequestException(
          String.format("Strategy is not supported for Pipeline stage %s", stageNode.getId()));
    }

    String planNodeId = stageNode.getUuid();
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(planNodeId)
            .name(stageNode.getName())
            .identifier(stageNode.getId())
            .group(StepCategory.STAGE.name())
            .stepType(PipelineStageStep.STEP_TYPE)
            .stepParameters(
                getStepParameters(stageNode, pipelineStageHelperV1.getChainedPipelineInputField(ctx.getCurrentField()),
                    planNodeId, childPipelineEntity.get().getHarnessVersion()))
            .whenCondition(RunInfoUtilsV1.getStageWhenCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build());

    if (EmptyPredicate.isNotEmpty(ctx.getExecutionInputTemplate())) {
      builder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    List<AdviserObtainment> adviserObtainments = getFailureStrategiesAdvisers(ctx.getCurrentField(), ctx);
    builder.adviserObtainments(adviserObtainments)
        .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, adviserObtainments)
        .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, adviserObtainments);
    return PlanCreationResponse.builder().graphLayoutResponse(getLayoutNodeInfo(ctx)).planNode(builder.build()).build();
  }

  @VisibleForTesting
  protected List<AdviserObtainment> getFailureStrategiesAdvisers(YamlField field, PlanCreationContext ctx) {
    YamlNode yamlNode = field.getNode();
    List<FailureConfigV1> stageFailureStrategies = PlanCreatorUtilsV1.getFailureStrategies(yamlNode);
    List<AdviserObtainment> nextStageAdvisor =
        PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, ctx.getDependency());
    Map<FailureStrategyActionConfigV1, Collection<FailureType>> actionMap =
        FailureStrategiesUtilsV1.priorityMergeFailureStrategies(null, null, stageFailureStrategies);
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, ctx.getDependency());
    List<AdviserObtainment> adviserObtainments = new ArrayList<>(PlanCreatorUtilsV1.getFailureStrategiesAdvisers(
        ctx.getDependency(), kryoSerializer, actionMap, PlanCreatorUtilsV1.isStepInsideRollback(ctx.getDependency()),
        nextNodeUuid, PlanCreatorUtilsV1::getAdviserObtainmentForPipelineStage, false));
    if (EmptyPredicate.isNotEmpty(nextStageAdvisor)) {
      adviserObtainments.addAll(nextStageAdvisor);
    }
    return adviserObtainments;
  }

  private GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    String nextNodeUuid = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, context.getDependency());

    EdgeLayoutList edgeLayoutList = EdgeLayoutList.newBuilder().build();
    String pipelineRollbackStageUuid = PlanCreatorUtilsV1.getPipelineRollbackStageId(context.getDependency());
    if (EmptyPredicate.isNotEmpty(nextNodeUuid) && !nextNodeUuid.equals(pipelineRollbackStageUuid)) {
      edgeLayoutList = EdgeLayoutList.newBuilder().addNextIds(nextNodeUuid).build();
    }
    stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(stageYamlField.getNode().getUuid())
            .setNodeType(stageYamlField.getNode().getType())
            .setName(stageYamlField.getId())
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(stageYamlField.getId())
            .setEdgeLayoutList(edgeLayoutList)
            .build());

    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }
}
