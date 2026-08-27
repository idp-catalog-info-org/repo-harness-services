/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_FETCH_SERVICE_ENV_RUNTIME_INPUTS_METADATA;
import static io.harness.beans.FeatureName.PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY;
import static io.harness.beans.FeatureName.PIPE_SUPPORT_CHILD_PIPELINE_STAGE_EXPRESSION_VALIDATION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetsForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipelineForGivenStages;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.dto.InputSetMetadataDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.plan.execution.StagesExecutionHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.tools.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ValidateAndMergeHelper {
  private final PMSPipelineService pmsPipelineService;
  private final PMSInputSetService pmsInputSetService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final GitSyncSdkService gitSyncSdkService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  private final InputsMetadataHelper inputsMetadataHelper;
  private final StagesExpressionExtractor stagesExpressionExtractor;
  @Deprecated
  public PipelineEntity getPipelineEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, String pipelineRepoID, boolean checkForStoreType,
      boolean loadFromCache) {
    return getPipelineEntity(scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier),
        pipelineIdentifier, pipelineBranch, pipelineRepoID, checkForStoreType, loadFromCache);
  }

  public PipelineEntity getPipelineEntity(ScopeInfo scopeInfo, String pipelineIdentifier, String pipelineBranch,
      String pipelineRepoID, boolean checkForStoreType, boolean loadFromCache) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    // todo: move this to PMSPipelineService
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
      return getPipelineEntityForOldGitSyncFlow(
          accountId, orgId, projectId, pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      return getPipelineEntity(scopeInfo, pipelineIdentifier, checkForStoreType, loadFromCache);
    }
  }

  private PipelineEntity getPipelineEntityForOldGitSyncFlow(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String pipelineBranch, String pipelineRepoID) {
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();

    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
      Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, false, null, false);
      if (pipelineEntity.isPresent()) {
        return pipelineEntity.get();
      } else {
        throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
            orgIdentifier, projectIdentifier, pipelineIdentifier));
      }
    }
  }

  private PipelineEntity getPipelineEntity(
      ScopeInfo scopeInfo, String pipelineIdentifier, boolean checkForStoreType, boolean loadFromCache) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    boolean isParentIdQueryingEnabled = true;
    Optional<PipelineEntity> optionalPipelineEntity;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).build()).build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier, false,
            false, false, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      }
    } else {
      long start = System.currentTimeMillis();
      optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier, false,
          false, false, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      log.info("[PMS_ValidateMerger] fetching and validating pipeline when update to new branch is false, took {}ms "
              + "for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectId, orgId, accountId);
    }
    if (optionalPipelineEntity.isPresent()) {
      StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
      PipelineEntity pipelineEntity = optionalPipelineEntity.get();
      if (checkForStoreType && storeTypeInContext != null && pipelineEntity.getStoreType() != storeTypeInContext) {
        throw new InvalidRequestException("Input Set should have the same Store Type as the Pipeline it is for");
      }
      return pipelineEntity;
    } else {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgId, projectId, pipelineIdentifier));
    }
  }

  public InputSetTemplateResponseDTOPMS getInputSetTemplateResponseDTO(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> stageIdentifiers, boolean loadFromCache,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabledForPipeline, boolean isParentIdQueryingEnabledForInputSet,
      Optional<PipelineEntity> optionalPipelineEntity, String processedPipelineYaml) {
    Set<FeatureName> enabledFeatures = new HashSet<>();
    if (pmsFeatureFlagService.isEnabled(accountId, PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY)) {
      enabledFeatures.add(FeatureName.PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY);
    }

    if (optionalPipelineEntity.isEmpty()) {
      optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, false, false, false, loadFromCache, scopeInfo, isParentIdQueryingEnabledForPipeline);
    }

    if (optionalPipelineEntity.isPresent()) {
      String template;
      List<String> replacedExpressions = null;
      Map<String, List<String>> stageToReplacedExpressionMap = null;

      PipelineEntity pipelineEntity = optionalPipelineEntity.get();
      String pipelineYaml = isNotEmpty(processedPipelineYaml) ? processedPipelineYaml : pipelineEntity.getYaml();
      String harnessVersion = pipelineEntity.getHarnessVersion();
      if (HarnessYamlVersion.isV1(harnessVersion)) {
        pipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYaml, false);
      }
      if (EmptyPredicate.isEmpty(stageIdentifiers)) {
        template = InputSetTemplateHelper.createTemplateWithDefaultValuesFromPipeline(
            pipelineYaml, enabledFeatures, harnessVersion);
      } else {
        // TODO: This requires changes with Move Project when templates are handled correctly.
        String yaml = stagesExpressionExtractor.getYaml(accountId, orgIdentifier, projectIdentifier, pipelineYaml,
            optionalPipelineEntity, loadFromCache, scopeInfo);
        StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(yaml, stageIdentifiers, harnessVersion);
        boolean shouldResolveChildPipeline =
            pmsFeatureFlagService.isEnabled(accountId, PIPE_SUPPORT_CHILD_PIPELINE_STAGE_EXPRESSION_VALIDATION);
        replacedExpressions =
            new ArrayList<>(stagesExpressionExtractor.getNonLocalExpressions(yaml, stageIdentifiers, harnessVersion,
                accountId, loadFromCache, isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline));
        stageToReplacedExpressionMap = stagesExpressionExtractor.getNonLocalExpressionsPerStage(yaml, stageIdentifiers,
            harnessVersion, accountId, loadFromCache, isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline);
        template = createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages(
            yaml, pipelineYaml, stageIdentifiers, enabledFeatures);
      }
      boolean hasInputSets = pmsInputSetService.checkForInputSetsForPipeline(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabledForInputSet);
      if (pmsFeatureFlagService.isEnabled(accountId, PIPE_FETCH_SERVICE_ENV_RUNTIME_INPUTS_METADATA)) {
        template = inputsMetadataHelper.mergeRuntimeInputsMetadataIntoTemplate(IdentifierRef.builder()
                                                                                   .accountIdentifier(accountId)
                                                                                   .orgIdentifier(orgIdentifier)
                                                                                   .projectIdentifier(projectIdentifier)
                                                                                   .identifier(pipelineIdentifier)
                                                                                   .build(),
            pipelineYaml, template);
      }

      return InputSetTemplateResponseDTOPMS.builder()
          .inputSetTemplateYaml(template)
          .replacedExpressions(replacedExpressions)
          .replacedExpressionsPerStage(stageToReplacedExpressionMap)
          .modules(pipelineEntity.getFilters().keySet())
          .hasInputSets(hasInputSets)
          .build();
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  public InputSetTemplateResponseDTOPMS getInputSetTemplateResponseDTO(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, List<String> stageIdentifiers, boolean loadFromCache,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabledForPipeline, boolean isParentIdQueryingEnabledForInputSet) {
    return getInputSetTemplateResponseDTO(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        stageIdentifiers, loadFromCache, scopeInfo, isParentIdQueryingEnabledForPipeline,
        isParentIdQueryingEnabledForInputSet, Optional.empty(), StringUtils.EMPTY);
  }

  //  use this method when the pipelineYaml is not available
  public String getPipelineTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> stageIdentifiers) {
    Optional<PipelineEntity> optionalPipelineEntity = pmsPipelineService.getPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, false, null, false);
    if (optionalPipelineEntity.isPresent()) {
      String pipelineYaml = optionalPipelineEntity.get().getYaml();
      if (EmptyPredicate.isEmpty(stageIdentifiers)) {
        return createTemplateFromPipeline(pipelineYaml);
      } else {
        return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
      }

    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  //  use this method when the pipelineYaml is not available
  public String getPipelineTemplate(ScopeInfo scopeInfo, String pipelineIdentifier, String pipelineBranch,
      String pipelineRepoID, List<String> stageIdentifiers) {
    String pipelineYaml =
        getPipelineEntity(scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, false).getYaml();
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return createTemplateFromPipeline(pipelineYaml);
    }
    return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
  }

  //  use this method when the pipelineYaml is available
  public String getPipelineTemplate(String pipelineYaml, List<String> stageIdentifiers) {
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return createTemplateFromPipeline(pipelineYaml);
    }
    return createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
  }
  public JsonNode getMergeInputSetFromPipelineTemplateWithJsonNode(String pipelineIdentifier,
      List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID, List<String> stageIdentifiers,
      boolean processAdditionalBaseKeys, ScopeInfo scopeInfo) {
    return getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo, pipelineIdentifier,
        inputSetReferences, pipelineBranch, pipelineRepoID, stageIdentifiers, null, false, false,
        processAdditionalBaseKeys, true, null);
  }

  public String getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(ScopeInfo scopeInfo,
      String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, String lastYamlToMerge, boolean loadFromCache, boolean isParentIdQueryingEnabled,
      String inputSetBranch) {
    JsonNode lastJsonNodeToMerge = null;
    if (isNotEmpty(lastYamlToMerge)) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(lastYamlToMerge);
    }
    boolean processAdditionalBaseKeys =
        pmsFeatureFlagService.isEnabled(scopeInfo.getAccountIdentifier(), FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
    return YamlUtils.writeYamlString(getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo,
        pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID, stageIdentifiers, lastJsonNodeToMerge,
        true, loadFromCache, processAdditionalBaseKeys, isParentIdQueryingEnabled, inputSetBranch));
  }

  public JsonNode getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(ScopeInfo scopeInfo,
      String pipelineIdentifier, List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, JsonNode lastJsonNodeToMerge, boolean keepDefaultValues, boolean loadFromCache,
      boolean processAdditionalBaseKeys, boolean isParentIdQueryingEnabled, String inputSetBranch) {
    InputSetMetadataDTO inputSetMetadataDTO =
        getInputSetMetadataDTO(scopeInfo, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID,
            stageIdentifiers, keepDefaultValues, loadFromCache, isParentIdQueryingEnabled, inputSetBranch);

    Set<String> inputSetVersions = inputSetMetadataDTO.getInputSetVersions();
    List<JsonNode> inputSetJsonNodeList = inputSetMetadataDTO.getInputSetJsonNodeList();
    JsonNode pipelineTemplate = inputSetMetadataDTO.getPipelineTemplate();
    String pipelineVersion = inputSetMetadataDTO.getPipelineVersion();
    if (inputSetVersions.contains(HarnessYamlVersion.V0) && inputSetVersions.contains(HarnessYamlVersion.V1)) {
      throw new InvalidRequestException("Input set versions 0 and 1 are not compatible");
    } else if (!inputSetVersions.isEmpty()) {
      String inputSetVersion = inputSetVersions.iterator().next();
      if (!inputSetVersion.equals(pipelineVersion)) {
        throw new InvalidRequestException("Different versions of Pipeline and InputSet are not supported");
      }
    }
    if (HarnessYamlVersion.V1.equals(pipelineVersion)) {
      List<JsonNode> sanitizedInputSetJsonNodeList =
          pmsInputSetService.getSanitizedInputsFromInputSetV1(inputSetJsonNodeList);
      if (!EmptyPredicate.isEmpty(lastJsonNodeToMerge)) {
        sanitizedInputSetJsonNodeList.add(
            pmsInputSetService.getSanitizedInputsFromInputSetV1(List.of(lastJsonNodeToMerge)).get(0));
      }
      JsonNode mergedV1Inputs = InputSetMergeHelper.mergeInputSetsV1(sanitizedInputSetJsonNodeList, pipelineVersion);
      // For selective stage execution, restrict the merged runtime inputs to only the selected stages.
      // For V1 input sets that are pipeline-shaped (have pipeline.stages), this filters in-place.
      // For flat V1 input sets (pipeline-level inputs only), this is a safe no-op.
      return InputSetMergeHelper.removeNonRequiredStagesV1IfStagesPresent(mergedV1Inputs, stageIdentifiers);
    }

    if (!EmptyPredicate.isEmpty(lastJsonNodeToMerge)) {
      inputSetJsonNodeList.add(lastJsonNodeToMerge);
    }

    boolean trimServiceInputsFromMergeResponse = pmsFeatureFlagService.isEnabled(
        scopeInfo.getAccountIdentifier(), FeatureName.PIPE_TRIM_SERVICE_INPUTS_FROM_MERGE_RESPONSE);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSets(
          pipelineTemplate, inputSetJsonNodeList, false, processAdditionalBaseKeys, trimServiceInputsFromMergeResponse);
    }
    return mergeInputSetsForGivenStages(pipelineTemplate, inputSetJsonNodeList, false, stageIdentifiers,
        processAdditionalBaseKeys, trimServiceInputsFromMergeResponse);
  }

  public InputSetMetadataDTO getInputSetMetadataDTO(ScopeInfo scopeInfo, String pipelineIdentifier,
      List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID, List<String> stageIdentifiers,
      boolean keepDefaultValues, boolean loadFromCache, boolean isParentIdQueryingEnabled, String inputSetBranch) {
    PipelineEntity pipelineEntity;

    String accountId = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_FETCH_CHILD_PIPELINE_WITHOUT_CONTEXT_GUARD)) {
      GitSyncBranchContext branchContext =
          setupGitContextForPipeline(accountId, orgIdentifier, projectIdentifier, pipelineBranch);
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        pipelineEntity =
            getPipelineEntity(scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, loadFromCache);
      }
    } else {
      GitSyncBranchContext branchContext = setupGitContext(accountId, orgIdentifier, projectIdentifier, pipelineBranch);
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        pipelineEntity =
            getPipelineEntity(scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, loadFromCache);
      }
    }
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    JsonNode pipelineTemplate = null;
    String pipelineVersion = HarnessYamlVersion.V0;
    if (HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
      if (keepDefaultValues) {
        Set<FeatureName> enabledFlags = new HashSet<>();
        if (pmsFeatureFlagService.isEnabled(
                accountId, PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY)) {
          enabledFlags.add(FeatureName.PIPE_POPULATE_REQUIRED_AND_DESC_METADATA_INTO_REFERENCING_ENTITY);
        }
        pipelineTemplate = EmptyPredicate.isEmpty(stageIdentifiers)
            ? createTemplateWithDefaultValuesFromPipeline(pipelineJsonNode, enabledFlags)
            : createTemplateWithDefaultValuesFromPipelineForGivenStages(
                  pipelineJsonNode, stageIdentifiers, enabledFlags);
      } else {
        pipelineTemplate = EmptyPredicate.isEmpty(stageIdentifiers)
            ? createTemplateFromPipeline(pipelineJsonNode)
            : createTemplateFromPipelineForGivenStages(pipelineJsonNode, stageIdentifiers);
      }
      if (EmptyPredicate.isEmpty(pipelineTemplate) && EmptyPredicate.isNotEmpty(inputSetReferences)) {
        throw new InvalidRequestException(
            "Pipeline " + pipelineIdentifier + " does not have any runtime input. All existing input sets are invalid");
      }
    } else {
      pipelineVersion = HarnessYamlVersion.V1;
    }
    List<JsonNode> inputSetJsonNodeList = new ArrayList<>();
    Set<String> inputSetVersions = new HashSet<>();

    if (inputSetReferences != null) {
      executeWithOptionalBranchContext(inputSetBranch, () -> inputSetReferences.forEach(identifier -> {
        Optional<InputSetEntity> entity = pmsInputSetService.getWithoutValidations(
            scopeInfo, pipelineIdentifier, identifier, false, false, loadFromCache, isParentIdQueryingEnabled);
        if (entity.isEmpty()) {
          return;
        }
        InputSetEntity inputSet = entity.get();
        inputSetVersions.add(inputSet.getHarnessVersion());
        checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(pipelineEntity, inputSet);
        if (HarnessYamlVersion.V0.equals(inputSet.getHarnessVersion())) {
          if (inputSet.getInputSetEntityType() == InputSetEntityType.INPUT_SET) {
            inputSetJsonNodeList.add(YamlUtils.readAsJsonNode(inputSet.getYaml()));
          } else {
            // for remote overlay inputsets, the inputSetReferences field can be outdated as it is not updated when
            // updates are done via git so compute references from yaml in real time
            List<String> overlayReferences =
                InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(inputSet.getYaml());
            overlayReferences.forEach(id -> {
              Optional<InputSetEntity> entity2 = pmsInputSetService.getWithoutValidations(
                  scopeInfo, pipelineIdentifier, id, false, false, loadFromCache, isParentIdQueryingEnabled);
              entity2.ifPresent(inputSetEntity -> {
                checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(pipelineEntity, entity2.get());
                inputSetJsonNodeList.add(YamlUtils.readAsJsonNode(inputSetEntity.getYaml()));
              });
            });
          }
        } else if (HarnessYamlVersion.V1.equals(inputSet.getHarnessVersion())) {
          JsonNode inputSetNode = YamlUtils.readAsJsonNode(inputSet.getYaml());
          if (isOverlayInputSetV1(inputSetNode)) {
            Pair<List<JsonNode>, Set<String>> overlayReferencesAndVersions = getOverlayInputSetReferences(
                inputSetNode, pipelineIdentifier, loadFromCache, pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
            List<JsonNode> overlayReferences = overlayReferencesAndVersions.getLeft();
            Set<String> overlayReferenceVersions = overlayReferencesAndVersions.getRight();
            inputSetVersions.addAll(overlayReferenceVersions);
            inputSetJsonNodeList.addAll(overlayReferences);
            JsonNode overlayInputSetInputs = getOverlayInputSetInputs(inputSetNode);
            if (null != overlayInputSetInputs) {
              inputSetJsonNodeList.add(overlayInputSetInputs);
            }
          } else {
            inputSetJsonNodeList.add(inputSetNode);
          }
        }
      }));
    }
    return InputSetMetadataDTO.builder()
        .inputSetVersions(inputSetVersions)
        .inputSetJsonNodeList(inputSetJsonNodeList)
        .pipelineTemplate(pipelineTemplate)
        .pipelineVersion(pipelineVersion)
        .build();
  }

  private void executeWithOptionalBranchContext(String inputSetBranch, Runnable inputSetProcessor) {
    if (EmptyPredicate.isNotEmpty(inputSetBranch)) {
      GitEntityInfo gitXInputSetBranchInfo = createInputSetBranchInfo(inputSetBranch);
      try (EntityGitDetailsGuard ignored = new EntityGitDetailsGuard(gitXInputSetBranchInfo)) {
        inputSetProcessor.run();
      }
    } else {
      inputSetProcessor.run();
    }
  }

  private JsonNode getOverlayInputSetInputs(JsonNode inputSetNode) {
    if (inputSetNode.has(YAMLFieldNameConstants.SPEC)) {
      JsonNode spec = inputSetNode.get(YAMLFieldNameConstants.SPEC);
      ObjectNode specNode = (ObjectNode) spec;
      specNode.remove(YAMLFieldNameConstants.INPUT_SETS);
      if (!specNode.isEmpty()) {
        return inputSetNode;
      }
    }
    return null;
  }

  Pair<List<JsonNode>, Set<String>> getOverlayInputSetReferences(JsonNode inputSetNode, String pipelineIdentifier,
      boolean loadFromCache, PipelineEntity pipelineEntity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabledForInputSet) {
    List<JsonNode> overlayReferences = new LinkedList<>();
    Set<String> inputSetVersions = new HashSet<>();
    if (inputSetNode.has(YAMLFieldNameConstants.SPEC)) {
      JsonNode spec = inputSetNode.get(YAMLFieldNameConstants.SPEC);
      if (spec.has(YAMLFieldNameConstants.INPUT_SETS)) {
        // Overlay InputSets contains input_sets key
        JsonNode inputSetsList = spec.get(YAMLFieldNameConstants.INPUT_SETS);
        if (inputSetsList instanceof ArrayNode) {
          ArrayNode overlayInputSetReferences = (ArrayNode) inputSetsList;
          for (JsonNode inputSetItem : overlayInputSetReferences) {
            Optional<InputSetEntity> inputSetEntity =
                pmsInputSetService.getWithoutValidations(scopeInfo, pipelineIdentifier, inputSetItem.asText(), false,
                    false, loadFromCache, isParentIdQueryingEnabledForInputSet);
            inputSetEntity.ifPresent(inputSetElement -> {
              checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(pipelineEntity, inputSetEntity.get());
              inputSetVersions.add(inputSetElement.getHarnessVersion());
              overlayReferences.add(YamlUtils.readAsJsonNode(inputSetElement.getYaml()));
            });
          }
        }
      }
    }
    return Pair.of(overlayReferences, inputSetVersions);
  }

  private boolean isOverlayInputSetV1(JsonNode inputSetNode) {
    if (inputSetNode.has(YAMLFieldNameConstants.SPEC)) {
      JsonNode spec = inputSetNode.get(YAMLFieldNameConstants.SPEC);
      if (spec.has(YAMLFieldNameConstants.INPUT_SETS)) {
        return true;
      }
    }
    return false;
  }

  public String mergeInputSetIntoPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String mergedRuntimeInputYaml, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, boolean loadFromCache, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PipelineEntity pipelineEntity =
        getPipelineEntity(scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, loadFromCache);
    String pipelineYaml = pipelineEntity.getYaml();
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineYaml, mergedRuntimeInputYaml, false);
    }
    return mergeInputSetIntoPipelineForGivenStages(
        pipelineYaml, mergedRuntimeInputYaml, false, stageIdentifiers, pipelineEntity.getHarnessVersion());
  }

  @VisibleForTesting
  void checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(
      PipelineEntity pipelineEntity, InputSetEntity inputSetEntity) {
    if (pipelineEntity.getStoreType() == null || inputSetEntity.getStoreType() == null) {
      return;
    }
    if (!pipelineEntity.getStoreType().equals(inputSetEntity.getStoreType())) {
      throw NestedExceptionUtils.hintWithExplanationException("Please move the input-set from inline to remote.",
          "The pipeline is remote and input-set is inline",
          new InvalidRequestException(String.format("Remote Pipeline %s cannot be used with inline input-set %s, "
                  + "please move input-set to from inline to remote to use them",
              pipelineEntity.getIdentifier(), inputSetEntity.getIdentifier())));
    }
  }

  private GitSyncBranchContext setupGitContext(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineBranch) {
    PipelineGitXHelper.setupGitParentEntityDetails(accountIdentifier, orgIdentifier, projectIdentifier, null, null);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return InputSetValidationHelper.buildGitSyncBranchContext(
        gitEntityInfo.getParentEntityRepoName(), pipelineBranch, gitEntityInfo.getParentEntityConnectorRef());
  }

  private GitSyncBranchContext setupGitContextForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineBranch) {
    PipelineGitXHelper.setupGitParentEntityDetails(accountIdentifier, orgIdentifier, projectIdentifier, null, null);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    return GitSyncBranchContext.builder()
        .gitBranchInfo(gitEntityInfo.toBuilder()
                           .repoName(gitEntityInfo.getParentEntityRepoName())
                           .branch(pipelineBranch)
                           .connectorRef(gitEntityInfo.getParentEntityConnectorRef())
                           .build())
        .build();
  }

  private GitEntityInfo createInputSetBranchInfo(String inputSetBranch) {
    return GitEntityInfo.builder().branch(inputSetBranch).build();
  }
}
