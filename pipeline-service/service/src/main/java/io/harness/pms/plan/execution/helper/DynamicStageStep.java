/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_TRUE_VALUE;

import static io.grpc.netty.shaded.io.netty.util.internal.StringUtil.EMPTY_STRING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceRequestDTO;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.Plan;
import io.harness.plancreator.stages.dynamic.DynamicStageStepParameters;
import io.harness.plancreator.stages.dynamic.GitConfig;
import io.harness.plancreator.stages.dynamic.GitSourceConfig;
import io.harness.plancreator.stages.dynamic.SourceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
public class DynamicStageStep implements ChildExecutable<DynamicStageStepParameters> {
  @Inject private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Inject private DynamicExecutionService dynamicExecutionService;
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;

  @Override
  public Class<DynamicStageStepParameters> getStepParametersClass() {
    return DynamicStageStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, DynamicStageStepParameters stepParameters, StepInputPackage inputPackage) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (EmptyPredicate.isNotEmpty(stepParameters.getChildNodeId())) {
      handleCopyForRetryOrRollback(ambiance);
      return ChildExecutableResponse.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build();
    }
    YamlField yamlField;
    String sourceYaml;
    try {
      // Check if Git store is provided
      if (isGitStoreProvided(stepParameters)) {
        sourceYaml = fetchYamlFromGit(ambiance, stepParameters, accountId);
      } else {
        // Use inline source (backward compatibility)
        if (isEmpty(stepParameters.getSource())) {
          throw new InvalidYamlException(
              "Either 'source' (inline YAML) or 'sourceConfig' (Git store configuration) must be provided.");
        }
        String base64Source = stepParameters.getSource().replaceAll("\\s+", "");
        sourceYaml = new String(Base64.getDecoder().decode(base64Source), StandardCharsets.UTF_8);
      }

      try (PmsSecurityContextNoSideEffectsGuard securityContextEventGuard =
               new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
        ScopeInfo scopeInfo =
            scopeResolutionHelper.getScopeInfo(accountId, AmbianceUtils.getParentUniqueIdentifier(ambiance));
        TemplateMergeResponseDTO templateMergeResponseDTO = scopeInfo != null
            ? pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(accountId, scopeInfo.getOrgIdentifier(),
                  scopeInfo.getProjectIdentifier(), sourceYaml, BOOLEAN_TRUE_VALUE, HarnessYamlVersion.V0)
            : pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(accountId,
                  AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), sourceYaml,
                  BOOLEAN_TRUE_VALUE, HarnessYamlVersion.V0);
        sourceYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      }
      yamlField = YamlUtils.injectUuidInYamlField(sourceYaml);
    } catch (Exception e) {
      throw new InvalidYamlException("Kindly provide valid YAML for dynamic execution.", e);
    }
    dynamicExecutionService.create(DynamicExecutionInstanceRequestDTO.builder()
                                       .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                       .planExecutionId(ambiance.getPlanExecutionId())
                                       .yaml(sourceYaml)
                                       .identifier(AmbianceUtils.obtainStepIdentifier(ambiance))
                                       .processedYaml(getYaml(yamlField))
                                       .build());
    Plan plan = planCreationQueueRequestHelper.createAndAppendToExistingPlan(
        ambiance, yamlField.getNode().toString(), getRootFieldValueForPlanCreation(yamlField));
    if (plan.getPlanNodes().stream().anyMatch(o -> EmptyPredicate.isNotEmpty(o.getExecutionInputTemplate()))) {
      throw new InvalidYamlException("Execution Time Input is not supported with the dynamic-stage execution.");
    }
    return ChildExecutableResponse.newBuilder().setChildNodeId(getStartingNodeId(yamlField)).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, DynamicStageStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
  }

  private YamlField getRootFieldValueForPlanCreation(YamlField yamlField) {
    // Getting the Pipeline field value as default implementation.
    return YamlUtils.getPipelineField(yamlField.getNode());
  }

  private String getStartingNodeId(YamlField yamlField) {
    YamlField pipelineField = YamlUtils.getPipelineField(yamlField.getNode());
    if (pipelineField == null) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
    }
    YamlField stagesField = pipelineField.getNode().getField(YAMLFieldNameConstants.STAGES);
    if (stagesField == null) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
    }
    List<YamlNode> stagesList = stagesField.getNode().asArray();
    if (stagesList == null || stagesList.isEmpty()) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
    }
    YamlNode firstElement = stagesList.get(0);
    YamlField stageField = firstElement.getField(YAMLFieldNameConstants.STAGE);
    if (stageField != null) {
      return stageField.getNode().getUuid();
    }
    YamlField parallelField = firstElement.getField(YAMLFieldNameConstants.PARALLEL);
    if (parallelField != null) {
      return parallelField.getNode().getUuid();
    }
    throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
  }

  private String getYaml(YamlField yamlField) {
    try {
      return YamlUtils.writeYamlString(yamlField);
    } catch (IOException e) {
      throw new InvalidYamlException("Error while converting to YAML");
    }
  }

  private boolean isGitStoreProvided(DynamicStageStepParameters stepParameters) {
    SourceConfig sourceConfig = stepParameters.getSourceConfig();
    if (!(sourceConfig instanceof GitSourceConfig)) {
      return false;
    }
    GitSourceConfig gitSourceConfig = (GitSourceConfig) sourceConfig;
    GitConfig gitConfig = gitSourceConfig.getSpec();
    if (gitConfig == null) {
      return false;
    }
    return gitConfig.getFilePath() != null && gitConfig.getFilePath().fetchFinalValue() != null
        && gitConfig.getRepoName() != null && gitConfig.getRepoName().fetchFinalValue() != null;
  }

  private String fetchYamlFromGit(Ambiance ambiance, DynamicStageStepParameters stepParameters, String accountId) {
    try {
      // Extract Git config from sourceConfig
      SourceConfig sourceConfig = stepParameters.getSourceConfig();
      if (!(sourceConfig instanceof GitSourceConfig)) {
        throw new InvalidRequestException("sourceConfig must be of type Git when using Git store.");
      }
      GitSourceConfig gitSourceConfig = (GitSourceConfig) sourceConfig;
      GitConfig gitConfig = gitSourceConfig.getSpec();
      if (gitConfig == null) {
        throw new InvalidRequestException("Git sourceConfig must have a spec.");
      }

      // Resolve all expressions in GitConfig object at once for better performance
      GitConfig resolvedGitConfig = (GitConfig) pmsEngineExpressionService.resolve(
          ambiance, gitConfig, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

      // Extract resolved values from ParameterFields
      String connectorRef = getResolvedValue(resolvedGitConfig.getConnectorRef(), "connectorRef");
      String filePath = getResolvedValue(resolvedGitConfig.getFilePath(), "filePath");
      String branch = getResolvedValue(resolvedGitConfig.getBranchName(), "branchName");
      String commitId = getResolvedValue(resolvedGitConfig.getCommitId(), "commitId");
      String repoName = getResolvedValue(resolvedGitConfig.getRepoName(), "repoName");

      // Validate required fields
      if (isEmpty(filePath)) {
        throw new InvalidRequestException("filePath is required when using Git store for dynamic stage.");
      }
      if (isEmpty(repoName)) {
        throw new InvalidRequestException("repoName is required when using Git store for dynamic stage.");
      }

      // If branch is empty, pass empty string to let Git SDK figure out the default branch for the repo
      // This follows the same pattern as GitAwareEntityHelper.fetchYAMLFromRemote
      if (isEmpty(branch)) {
        branch = EMPTY_STRING;
        log.info("Branch not specified, Git SDK will determine the default branch for the repository");
      }

      // Create scope
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(accountId, AmbianceUtils.getParentUniqueIdentifier(ambiance));
      Scope scope = scopeInfo != null
          ? Scope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier())
          : Scope.of(accountId, AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

      // Extract Principal from Ambiance and set it in context for Git operations
      Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
      SourcePrincipalContextBuilder.setSourcePrincipal(principal);

      // Build Git context request params
      GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                            .connectorRef(connectorRef)
                                                            .branchName(branch)
                                                            .commitId(commitId)
                                                            .filePath(filePath)
                                                            .repoName(repoName)
                                                            .loadFromCache(false)
                                                            .applyRepoAllowListFilter(false)
                                                            .build();

      // Fetch YAML from Git
      log.info("Fetching pipeline YAML from Git - connectorRef: {}, branch: {}, commitId: {}, filePath: {}",
          connectorRef, branch, commitId, filePath);
      String yamlContent =
          gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());

      if (isEmpty(yamlContent)) {
        throw new InvalidYamlException(
            String.format("Failed to fetch YAML from Git. File path: %s, Branch: %s", filePath, branch));
      }

      return yamlContent;
    } catch (InvalidRequestException | InvalidYamlException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error fetching YAML from Git for dynamic stage", e);
      throw new InvalidYamlException(String.format("Error fetching YAML from Git: %s", e.getMessage()), e);
    }
  }

  private String getResolvedValue(ParameterField<String> field, String fieldName) {
    if (field == null) {
      return null;
    }
    try {
      // After resolve() call, the ParameterField should have the resolved value
      return (String) field.fetchFinalValue();
    } catch (Exception e) {
      log.warn("Failed to get resolved value for field: {}", fieldName, e);
      throw new InvalidRequestException(
          String.format("Failed to get resolved value for %s: %s", fieldName, e.getMessage()), e);
    }
  }

  private void handleCopyForRetryOrRollback(Ambiance ambiance) {
    String originalPlanExecutionId = "";
    if (ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      originalPlanExecutionId = ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();
    } else {
      Optional<PlanExecutionMetadata> metadata = planExecutionMetadataService.findByPlanExecutionId(
          AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
      if (metadata.isPresent() && metadata.get().getRetryExecutionInfo() != null
          && EmptyPredicate.isNotEmpty(metadata.get().getRetryExecutionInfo().getParentRetryId())) {
        originalPlanExecutionId = metadata.get().getRetryExecutionInfo().getParentRetryId();
      }
    }
    Optional<DynamicExecutionInstanceResponseDTO> optional = dynamicExecutionService.getByPlanExecutionIdAndIdentifier(
        originalPlanExecutionId, AmbianceUtils.getStepIdentifierFromAmbiance(ambiance));
    optional.ifPresent(dynamicExecutionInstanceResponseDTO
        -> dynamicExecutionService.create(DynamicExecutionInstanceRequestDTO.builder()
                                              .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                              .planExecutionId(ambiance.getPlanExecutionId())
                                              .yaml(dynamicExecutionInstanceResponseDTO.getYaml())
                                              .identifier(AmbianceUtils.obtainStepIdentifier(ambiance))
                                              .processedYaml(dynamicExecutionInstanceResponseDTO.getProcessedYaml())
                                              .build()));
  }
}
