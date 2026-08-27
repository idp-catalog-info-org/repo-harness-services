/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_RETURN_NULL_ON_EXPRESSION_FAIL_PIPELINE_STAGE;
import static io.harness.beans.FeatureName.PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.plan.execution.helper.PipelineStageStep.NESTED_CHAINING_ERROR;
import static io.harness.pms.plan.execution.helper.PipelineStageStep.NESTED_CHAINING_HINT;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.InputSetValidatorType;
import io.harness.beans.ScopeInfo;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.PipelineStageStepParameters.PipelineStageStepParametersKeys;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.execution.PmsExecutionSummaryDtoUpdateHelper;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO.ChildExecutionDetailDTOBuilder;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.action.NGFailureActionTypeConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageHelper {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private final PipelineStageHelperV1 pipelineStageHelperV1;
  @Inject GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  @Inject private final PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;
  @Inject private final ScopeResolutionHelper scopeResolutionHelper;

  private static String NESTED_ERROR_EXCEPTION_HINT = "Pipeline setup configuration issue for pipeline stage";
  private static String NESTED_ERROR_EXCEPTION =
      "The referred pipeline invokes a child pipeline on stage [%s], so it cannot be included within another pipeline. "
      + "Nested Pipeline Chaining is not supported";
  private final List<String> actionTypeNotSupported = Arrays.asList(NGFailureActionTypeConstants.RETRY,
      NGFailureActionTypeConstants.PIPELINE_ROLLBACK, NGFailureActionTypeConstants.MANUAL_INTERVENTION);

  public void validateNestedChainedPipeline(PipelineEntity pipelineEntity, String stageName, ScopeInfo scopeInfo) {
    try {
      validateNestedChainedPipeline(pipelineEntity, scopeInfo);
    } catch (Exception e) {
      log.error("Error during nested chaining validation ", e);
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_ERROR_EXCEPTION_HINT, stageName), e.getMessage(), null);
    }
  }

  public void validateNestedChainedPipeline(
      PipelineEntity pipelineEntity, String stageName, String parentPipelineIdentifier, ScopeInfo scopeInfo) {
    try {
      validateNestedChainedPipeline(pipelineEntity, scopeInfo);
    } catch (Exception e) {
      log.error("Error during nested chaining validation ", e);
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_CHAINING_HINT, parentPipelineIdentifier),
          String.format(NESTED_CHAINING_ERROR, pipelineEntity.getIdentifier()));
    }
  }

  public void validateNestedChainedPipeline(PipelineEntity entity, ScopeInfo scopeInfo) {
    GitEntityInfo gitEntityInfo = null;
    if (pmsFeatureFlagService.isEnabled(entity.getAccountId(), PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX)) {
      gitEntityInfo =
          GitEntityInfo.builder()
              .branch(
                  // TODO fetch repo name for the parent pipeline from db since repo might not be always set in the
                  // context
                  gitAwareEntityHelper.getWorkingBranch(
                      GitAwareContextHelper.getRepoFromGitContext(), entity.getRepo()))
              // storeType should be set here, because it is checked for null in GitContextHelper.getGitEntityInfo()
              // which is called from resolveTemplateRefsInPipeline() in PMSPipelineTemplateHelper.java
              .storeType(StoreType.REMOTE)
              .repoName(entity.getRepo())
              .build();
    } else {
      // default scenario
      gitEntityInfo = GitEntityInfo.builder()
                          .branch(gitAwareEntityHelper.getWorkingBranch(
                              GitAwareContextHelper.getRepoFromGitContext(), entity.getRepo()))
                          .storeType(entity.getStoreType())
                          .repoName(entity.getRepo())
                          .build();
    }
    try (EntityGitDetailsGuard ignored = new EntityGitDetailsGuard(gitEntityInfo)) {
      String yaml = entity.getYaml();
      boolean isTemplateResolutionFFEnabled = pmsFeatureFlagService.isEnabled(
          entity.getAccountId(), FeatureName.CDS_PIPELINE_STAGE_TEMPLATE_VALIDATION_OPTIMISATIONS);
      if (isTemplateResolutionFFEnabled) {
        /* * Instead of recursively resolving all potential template occurrences for a pipeline stage, since we only
        want to check if this pipeline stage contains another pipeline stage, we can just check if current stage points
        to a pipeline that is a pipeline template, and if so resolve. Otherwise, if it is not a template, we have
        relevant information. In case it is a stage template, we don't allow pipeline stage currently. Either way, the
        linked/resolved pipeline yaml will have all the stage types that we need to make this check.
         * */
        yaml = scopeInfo != null
            ? pmsPipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(yaml, "true", entity.getAccountId(),
                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), entity.getHarnessVersion())
            : pmsPipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(yaml, "true", entity.getAccountId(),
                  entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getHarnessVersion());
      } else {
        TemplateMergeResponseDTO templateMergeResponseDTO = scopeInfo != null
            ? pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(entity, scopeInfo, "true")
            : pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(entity, "true");
        yaml = templateMergeResponseDTO.getMergedPipelineYaml();
      }
      switch (entity.getHarnessVersion()) {
        case HarnessYamlVersion.V0:
          containsPipelineStage(yaml);
          break;
        case HarnessYamlVersion.V1:
          pipelineStageHelperV1.containsPipelineStage(yaml);
          break;
        default:
          throw new InvalidRequestException(
              String.format("Child pipeline version: %s not supported", entity.getHarnessVersion()));
      }
    }
  }

  private void containsPipelineStage(String yaml) {
    try {
      YamlField pipelineYamlField = YamlUtils.readTree(yaml);
      List<YamlNode> stages = pipelineYamlField.getNode()
                                  .getField(YAMLFieldNameConstants.PIPELINE)
                                  .getNode()
                                  .getField(YAMLFieldNameConstants.STAGES)
                                  .getNode()
                                  .asArray();
      for (YamlNode yamlNode : stages) {
        if (yamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
          containsPipelineStageInStageNode(yamlNode);
        } else if (yamlNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
          containsPipelineStageInParallelNode(yamlNode);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void containsPipelineStageInParallelNode(YamlNode yamlNode) {
    List<YamlNode> stageInParallel = yamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    for (YamlNode stage : stageInParallel) {
      if (stage.getField(YAMLFieldNameConstants.STAGE) != null) {
        containsPipelineStageInStageNode(stage);
      } else {
        throw new InvalidRequestException("Parallel stages contains entity other than stage");
      }
    }
  }

  private void containsPipelineStageInStageNode(YamlNode yamlNode) {
    if (yamlNode.getField(YAMLFieldNameConstants.STAGE) != null
        && yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode() != null
        && yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getType().equals("Pipeline")) {
      throw new InvalidRequestException(
          String.format(NESTED_ERROR_EXCEPTION, yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getName()));
    }
  }

  public void validateResource(
      AccessControlClient accessControlClient, Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(ambiance.getSetupAbstractions().get("accountId"),
                                                  stepParameters.getOrg(), stepParameters.getProject()),
        Resource.of("PIPELINE", stepParameters.getPipeline()), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  public JsonNode getInputSetJsonNode(YamlField pipelineInputs, String pipelineVersion) {
    switch (pipelineVersion) {
      case HarnessYamlVersion.V0:
        return getInputSetJsonNode(pipelineInputs);
      case HarnessYamlVersion.V1:
        return pipelineStageHelperV1.getInputSetJsonNode(pipelineInputs);
      default:
        throw new InvalidRequestException(String.format("Child pipeline version: %s not supported", pipelineVersion));
    }
  }

  private JsonNode getInputSetJsonNode(YamlField pipelineInputs) {
    JsonNode inputJsonNode = null;
    if (pipelineInputs != null) {
      Map<String, JsonNode> map = getInputSetMapInternal(pipelineInputs);
      inputJsonNode = JsonPipelineUtils.asTree(map);
    }
    return inputJsonNode;
  }

  private Map<String, JsonNode> getInputSetMapInternal(YamlField pipelineInputs) {
    // Deep copy is required to prevent any concurrentException as we are reading yaml in other places. This is caught
    // via PIE-8733
    JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode().deepCopy();
    YamlUtils.removeUuid(inputJsonNode);
    // In a chained pipeline the parent bakes the child's input-set validator onto expression inputs (e.g.
    // <+pipeline.variables.x>.selectOneFrom(0,1)). After the expression resolves at runtime the baked suffix survives
    // (2.selectOneFrom(0,1)) and fails the child's number schema validation. The child re-applies its own validator at
    // its own merge, so the parent's copy is redundant and safe to drop here, while the expression is still unresolved
    // and carries a clean closing '>' delimiter.
    stripInputSetValidatorFromExpressionLeaves(inputJsonNode);
    Map<String, JsonNode> map = new HashMap<>();
    map.put(YAMLFieldNameConstants.PIPELINE, inputJsonNode);
    return map;
  }

  private void stripInputSetValidatorFromExpressionLeaves(JsonNode node) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      List<String> fieldNames = new ArrayList<>();
      objectNode.fieldNames().forEachRemaining(fieldNames::add);
      for (String fieldName : fieldNames) {
        JsonNode child = objectNode.get(fieldName);
        if (child != null && child.isTextual()) {
          String stripped = stripInputSetValidatorFromExpression(child.asText());
          if (stripped != null) {
            objectNode.put(fieldName, stripped);
          }
        } else {
          stripInputSetValidatorFromExpressionLeaves(child);
        }
      }
    } else if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        JsonNode element = arrayNode.get(i);
        if (element != null && element.isTextual()) {
          String stripped = stripInputSetValidatorFromExpression(element.asText());
          if (stripped != null) {
            arrayNode.set(i, TextNode.valueOf(stripped));
          }
        } else {
          stripInputSetValidatorFromExpressionLeaves(element);
        }
      }
    }
  }

  // Returns the value with a baked input-set validator suffix removed, or null if nothing was stripped. Only touches
  // expression values (<+...>) so that concrete inputs like "2.allowedValues(1,2)" - which have no expression - are
  // never mistaken for a validator. Only strips when the validator sits right after the expression's closing '>', so
  // a validator name appearing inside the expression body is left untouched.
  private String stripInputSetValidatorFromExpression(String value) {
    if (EmptyPredicate.isEmpty(value) || !NGExpressionUtils.isExpressionField(value)) {
      return null;
    }
    for (InputSetValidatorType validatorType : InputSetValidatorType.values()) {
      Pattern validatorPattern =
          Pattern.compile(NGExpressionUtils.getInputSetValidatorPattern(validatorType.getYamlName()));
      if (NGExpressionUtils.containsPattern(validatorPattern, value)) {
        String leftSide = validatorPattern.split(value)[0];
        if (leftSide.endsWith(">")) {
          return leftSide;
        }
      }
    }
    return null;
  }

  public ChildExecutionDetailDTO getChildGraph(String accountId, String childStageNodeId,
      EntityGitDetails entityGitDetails, NodeExecution nodeExecution, String stageNodeExecutionId) {
    String childExecutionId = nodeExecution.getExecutableResponses().get(0).getAsync().getCallbackIds(0);
    PmsStepParameters parameters = nodeExecution.getResolvedParams();

    String orgId = parameters.get(PipelineStageStepParametersKeys.org).toString();
    String projectId = parameters.get(PipelineStageStepParametersKeys.project).toString();
    return getChildGraph(
        accountId, childStageNodeId, entityGitDetails, childExecutionId, orgId, projectId, stageNodeExecutionId);
  }

  public ChildExecutionDetailDTO getChildGraph(String accountId, String childStageNodeId,
      EntityGitDetails entityGitDetails, String childExecutionId, String orgId, String projectId,
      String stageNodeExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntityForChild =
        pmsExecutionService.fetchExecutionSummary(accountId, childExecutionId, false);

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, executionSummaryEntityForChild.getParentUniqueId());

    // access control on child pipeline
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntityForChild.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetailsForChild;
    if (entityGitDetails == null) {
      entityGitDetailsForChild =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntityForChild.getGitSyncBranchContext());
    } else {
      entityGitDetailsForChild = executionSummaryEntityForChild.getEntityGitDetails();
    }

    return getChildGraph(accountId, childStageNodeId, childExecutionId, executionSummaryEntityForChild,
        entityGitDetailsForChild, stageNodeExecutionId, scopeInfo);
  }

  private ChildExecutionDetailDTO getChildGraph(String accountId, String childStageNodeId, String childExecutionId,
      PipelineExecutionSummaryEntity executionSummaryEntityForChild, EntityGitDetails entityGitDetailsForChild,
      String stageNodeExecutionId, ScopeInfo scopeInfo) {
    // Top graph for child execution
    ChildExecutionDetailDTOBuilder childGraphBuilder = ChildExecutionDetailDTO.builder().pipelineExecutionSummary(
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityForChild, entityGitDetailsForChild,
            retryExecutionHelper.shouldShowRetryHistory(executionSummaryEntityForChild),
            retryExecutionHelper.isLatestExecution(executionSummaryEntityForChild),
            pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntityForChild), scopeInfo));

    // if child stage node id is not null, add bottom graph for child execution
    if (childStageNodeId != null) {
      childGraphBuilder.executionGraph(
          ExecutionGraphMapper.toExecutionGraph(pmsExecutionService.getOrchestrationGraph(accountId, childStageNodeId,
                                                    childExecutionId, stageNodeExecutionId),
              executionSummaryEntityForChild, scopeInfo));
    }
    return childGraphBuilder.build();
  }

  public boolean validateChildGraphToGenerate(
      Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO, String stageNodeId, String stageNodeExecutionId) {
    // Validates nodeType which should be Pipeline
    if (graphLayoutNodeDTO.containsKey(stageNodeId) && graphLayoutNodeDTO.get(stageNodeId).getNodeType() != null
        && StepSpecTypeConstants.PIPELINE_STAGE.equals(graphLayoutNodeDTO.get(stageNodeId).getNodeType())) {
      return true;
    }

    // NEW: Fallback for IdentityNode retry scenarios
    // If stageNodeId doesn't exist in layoutNodeMap (common for IdentityNodes during retry),
    // check if stageNodeExecutionId exists and is a PIPELINE_STAGE with strategy metadata
    if (stageNodeExecutionId != null && graphLayoutNodeDTO.containsKey(stageNodeExecutionId)) {
      GraphLayoutNodeDTO node = graphLayoutNodeDTO.get(stageNodeExecutionId);
      return node.getNodeType() != null && StepSpecTypeConstants.PIPELINE_STAGE.equals(node.getNodeType())
          && node.getStrategyMetadata() != null;
    }

    return false;
  }

  public PipelineStageOutcome resolveOutputVariables(Map<String, ParameterField<String>> map, Ambiance ambiance) {
    Map<String, Object> resolvedMap = resolveOutputVariables(map);
    var returnNullOnExpressionUnresolved = pmsFeatureFlagService.isEnabled(
        AmbianceUtils.getAccountId(ambiance), PIPE_RETURN_NULL_ON_EXPRESSION_FAIL_PIPELINE_STAGE);

    return new PipelineStageOutcome((Map<String, Object>) pmsEngineExpressionService.resolve(ambiance, resolvedMap,
        returnNullOnExpressionUnresolved ? ExpressionMode.RETURN_NULL_IF_UNRESOLVED
                                         : ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }

  public Map<String, Object> resolveOutputVariables(Map<String, ParameterField<String>> map) {
    Map<String, Object> resolvedMap = new HashMap<>();

    for (Map.Entry<String, ParameterField<String>> entry : map.entrySet()) {
      String expression;
      ParameterField<String> valueField = entry.getValue();
      if (valueField.getExpressionValue() != null) {
        expression = valueField.getExpressionValue();
      } else {
        expression = valueField.getValue();
      }

      resolvedMap.put(entry.getKey(), expression);
    }
    return resolvedMap;
  }

  public void validateFailureStrategy(ParameterField<List<FailureStrategyConfig>> failureStrategies) {
    if (ParameterField.isNotNull(failureStrategies) && isNotEmpty(failureStrategies.getValue())) {
      for (FailureStrategyConfig failureStrategyConfig : failureStrategies.getValue()) {
        if (actionTypeNotSupported.contains(failureStrategyConfig.getOnFailure().getAction().getType().getYamlName())) {
          throw new InvalidRequestException(String.format("Action %s is not supported in pipeline stage",
              failureStrategyConfig.getOnFailure().getAction().getType()));
        }
      }
    }
  }
}
