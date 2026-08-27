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
import io.harness.plancreator.stages.dynamic.v1.DynamicGitSourceWithV1;
import io.harness.plancreator.stages.dynamic.v1.DynamicSourceConfigV1;
import io.harness.plancreator.stages.dynamic.v1.DynamicStageStepParametersV1;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.ScopeResolutionHelper;

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
public class DynamicStageStepV1 implements ChildExecutable<StageElementParametersV1> {
  @Inject private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Inject private DynamicExecutionService dynamicExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @Override
  public Class<StageElementParametersV1> getStepParametersClass() {
    return StageElementParametersV1.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParametersV1 stageParameters, StepInputPackage inputPackage) {
    DynamicStageStepParametersV1 stepParameters = (DynamicStageStepParametersV1) stageParameters.getSpec();

    if (EmptyPredicate.isNotEmpty(stepParameters.getChildNodeId())) {
      copyDynamicExecutionInstanceForRollbackOrRetry(ambiance);
      return ChildExecutableResponse.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build();
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    YamlField yamlField;
    String sourceYaml;
    try {
      if (isGitStoreProvided(stepParameters)) {
        sourceYaml = fetchYamlFromGit(ambiance, stepParameters, accountId);
      } else {
        if (isEmpty(stepParameters.getSource())) {
          throw new InvalidYamlException(
              "Either 'source' (inline YAML) or 'source-config' (Git store configuration) must be provided.");
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
                  scopeInfo.getProjectIdentifier(), sourceYaml, BOOLEAN_TRUE_VALUE, HarnessYamlVersion.V1)
            : pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(accountId,
                  AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), sourceYaml,
                  BOOLEAN_TRUE_VALUE, HarnessYamlVersion.V1);
        sourceYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      }
      sourceYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(sourceYaml, false);
      sourceYaml = pmsPipelineServiceHelper.injectTypeField(sourceYaml);
      yamlField = YamlUtils.injectUuidInYamlField(sourceYaml);
    } catch (InvalidRequestException | InvalidYamlException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidYamlException("Kindly provide valid YAML for dynamic execution.", e);
    }

    validateChildPipelineIsV1(yamlField);

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
      Ambiance ambiance, StageElementParametersV1 stageParameters, Map<String, ResponseData> responseDataMap) {
    return SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
  }

  private YamlField getRootFieldValueForPlanCreation(YamlField yamlField) {
    return YamlUtils.getPipelineField(yamlField.getNode());
  }

  private void validateChildPipelineIsV1(YamlField yamlField) {
    YamlField pipelineField = YamlUtils.getPipelineField(yamlField.getNode());
    if (pipelineField == null) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has a 'pipeline' root.");
    }
    YamlField stagesField = pipelineField.getNode().getField(YAMLFieldNameConstants.STAGES);
    if (stagesField == null) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
    }
    List<YamlNode> stagesList = stagesField.getNode().asArray();
    if (stagesList == null || stagesList.isEmpty()) {
      throw new InvalidYamlException("Kindly make sure that YAML is correct and it has at least one stage.");
    }
    for (YamlNode stageElement : stagesList) {
      if (stageElement.getField(YAMLFieldNameConstants.STAGE) != null) {
        throw new InvalidYamlException("V1 dynamic stage requires a V1 child pipeline YAML. The provided YAML is in V0 "
            + "format (contains 'stage:' wrapper).");
      }
    }
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
    return stagesList.get(0).getUuid();
  }

  private String getYaml(YamlField yamlField) {
    try {
      return YamlUtils.writeYamlString(yamlField);
    } catch (IOException e) {
      throw new InvalidYamlException("Error while converting to YAML", e);
    }
  }

  private boolean isGitStoreProvided(DynamicStageStepParametersV1 stepParameters) {
    DynamicSourceConfigV1 sourceConfig = stepParameters.getSourceConfig();
    if (sourceConfig == null || sourceConfig.getWith() == null) {
      return false;
    }
    DynamicGitSourceWithV1 gitWith = sourceConfig.getWith();
    return EmptyPredicate.isNotEmpty(gitWith.getPath()) && EmptyPredicate.isNotEmpty(gitWith.getRepo());
  }

  private String fetchYamlFromGit(Ambiance ambiance, DynamicStageStepParametersV1 stepParameters, String accountId) {
    try {
      DynamicSourceConfigV1 sourceConfig = stepParameters.getSourceConfig();
      DynamicGitSourceWithV1 gitWith = sourceConfig.getWith();

      DynamicGitSourceWithV1 resolvedGitWith = (DynamicGitSourceWithV1) pmsEngineExpressionService.resolve(
          ambiance, gitWith, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

      String connectorRef = resolvedGitWith.getConnector();
      String filePath = resolvedGitWith.getPath();
      String branch = resolvedGitWith.getBranch();
      String commitId = resolvedGitWith.getCommit();
      String repoName = resolvedGitWith.getRepo();

      if (isEmpty(filePath)) {
        throw new InvalidRequestException("path is required when using Git store for dynamic stage.");
      }
      if (isEmpty(repoName)) {
        throw new InvalidRequestException("repo is required when using Git store for dynamic stage.");
      }

      if (isEmpty(branch)) {
        branch = "";
        log.info("Branch not specified, Git SDK will determine the default branch for the repository");
      }

      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(accountId, AmbianceUtils.getParentUniqueIdentifier(ambiance));
      Scope scope = scopeInfo != null
          ? Scope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier())
          : Scope.of(accountId, AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

      Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
      SourcePrincipalContextBuilder.setSourcePrincipal(principal);

      GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                            .connectorRef(connectorRef)
                                                            .branchName(branch)
                                                            .commitId(commitId)
                                                            .filePath(filePath)
                                                            .repoName(repoName)
                                                            .loadFromCache(false)
                                                            .applyRepoAllowListFilter(false)
                                                            .build();

      log.info("Fetching pipeline YAML from Git for v1 dynamic stage - connectorRef: {}, branch: {}, filePath: {}",
          connectorRef, branch, filePath);
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
      log.error("Error fetching YAML from Git for v1 dynamic stage", e);
      throw new InvalidYamlException(String.format("Error fetching YAML from Git: %s", e.getMessage()), e);
    }
  }

  private void copyDynamicExecutionInstanceForRollbackOrRetry(Ambiance ambiance) {
    String originalPlanExecutionId = "";
    if (io.harness.utils.execution.ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      originalPlanExecutionId = ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();
    } else {
      Optional<PlanExecutionMetadata> metadata = planExecutionMetadataService.findByPlanExecutionId(
          AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
      if (metadata.isPresent() && metadata.get().getRetryExecutionInfo() != null
          && EmptyPredicate.isNotEmpty(metadata.get().getRetryExecutionInfo().getParentRetryId())) {
        originalPlanExecutionId = metadata.get().getRetryExecutionInfo().getParentRetryId();
      }
    }
    if (EmptyPredicate.isEmpty(originalPlanExecutionId)) {
      return;
    }
    String stageIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    Optional<DynamicExecutionInstanceResponseDTO> original =
        dynamicExecutionService.getByPlanExecutionIdAndIdentifier(originalPlanExecutionId, stageIdentifier);
    if (original.isEmpty()) {
      log.warn("Skipping DynamicExecutionInstance copy: no instance found for"
              + " originalPlanExecutionId={} stageIdentifier={}",
          originalPlanExecutionId, stageIdentifier);
      return;
    }
    DynamicExecutionInstanceResponseDTO dto = original.get();
    dynamicExecutionService.create(DynamicExecutionInstanceRequestDTO.builder()
                                       .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                       .planExecutionId(ambiance.getPlanExecutionId())
                                       .yaml(dto.getYaml())
                                       .identifier(stageIdentifier)
                                       .processedYaml(dto.getProcessedYaml())
                                       .build());
  }
}
