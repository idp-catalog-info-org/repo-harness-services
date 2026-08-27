/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitsync.beans.StoreType.INLINE;
import static io.harness.utils.PipelineExceptionsHelper.ERROR_PIPELINE_BRANCH_NOT_PROVIDED;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.service.OverlayInputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetValidationHelper {
  public void checkForPipelineStoreType(PipelineEntity pipelineEntity, boolean isDefaultToInlineStoreTypeDisabled) {
    StoreType storeTypeInContext = GitAwareContextHelper.getGitRequestParamsInfo().getStoreType();
    StoreType pipelineStoreType = pipelineEntity.getStoreType();
    if (!isDefaultToInlineStoreTypeDisabled) {
      if (storeTypeInContext != StoreType.REMOTE && storeTypeInContext != INLINE) {
        log.error("Invalid store type {}, making it inline", storeTypeInContext);
        storeTypeInContext = INLINE;
      }
    }
    if (pipelineStoreType != storeTypeInContext) {
      throw new InvalidRequestException(String.format("Input Set storeType: %s does not match with Pipeline storeType: "
              + "%s. Input Set and Pipeline both must have same storeType",
          storeTypeInContext, pipelineStoreType));
    }
  }

  // this method is not for old git sync
  public void validateInputSet(PMSInputSetService inputSetService, InputSetEntity inputSetEntity,
      boolean hasNewYamlStructure, boolean validateInputSetIdentifier, boolean isParentIdQueryingEnabled,
      ScopeInfo scopeInfo) {
    switch (inputSetEntity.getHarnessVersion()) {
      case HarnessYamlVersion.V1:
        return;
      case HarnessYamlVersion.V0:
        break;
      default:
        throw new IllegalStateException("version not supported");
    }
    // These deprecated fields can be removed because when isParentIdQueryingEnabled is true,
    // you do not validate yaml with organization and project identifiers.
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();
    InputSetEntityType type = inputSetEntity.getInputSetEntityType();
    if (type.equals(InputSetEntityType.INPUT_SET)) {
      if (!hasNewYamlStructure) {
        validateIdentifyingFieldsInYAML(
            orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, isParentIdQueryingEnabled);
      }
      validateNoRuntimeInputsWrappedInList(yaml);
    } else {
      OverlayInputSetValidationHelper.validateOverlayInputSet(
          inputSetService, inputSetEntity, isParentIdQueryingEnabled, scopeInfo);
    }
    String inputSetIdentifier = inputSetEntity.getIdentifier();
    if (EmptyPredicate.isEmpty(inputSetIdentifier)) {
      if (validateInputSetIdentifier) {
        throw new InvalidRequestException("InputSet Identifier cannot be empty.");
      } else {
        log.warn("InputSet Identifier cannot be empty.");
      }
    } else if (!inputSetIdentifier.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN)) {
      if (validateInputSetIdentifier) {
        throw new InvalidRequestException(
            format("InputSet Identifier cannot contain special characters or spaces: [%s]", inputSetIdentifier));
      } else {
        log.warn(format("InputSet Identifier cannot contain special characters or spaces: [%s]", inputSetIdentifier));
      }
    }
  }

  /*
  If the Input Set create/update is to a new branch, in that case, the pipeline needs to be fetched from the base branch
  (the branch from which the new branch will be checked out). This method facilitates that by first whether the
  operation is to a new branch or not. If it is to a new branch, then it creates a guard to fetch the pipeline from the
  base branch. If not, no guard is needed.
  */
  public PipelineEntity getPipelineEntity(PMSPipelineService pmsPipelineService, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).build()).build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
      }
    } else {
      optionalPipelineEntity = pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  public String getPipelineYamlForOldGitSyncFlow(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String pipelineBranch,
      String pipelineRepoID) {
    if (EmptyPredicate.isEmpty(pipelineBranch) || EmptyPredicate.isEmpty(pipelineRepoID)) {
      return getPipelineYamlForOldGitSyncFlowInternal(
          pmsPipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();

    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
      return getPipelineYamlForOldGitSyncFlowInternal(
          pmsPipelineService, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
  }

  String getPipelineYamlForOldGitSyncFlowInternal(PMSPipelineService pmsPipelineService, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, false, null, false);
    if (pipelineEntity.isPresent()) {
      return pipelineEntity.get().getYaml();
    } else {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  /**
   * Rejects input sets where a runtime-input marker ({@code <+input>}) has been wrapped inside a
   * YAML list instead of being provided as a scalar field value.
   **/
  static void validateNoRuntimeInputsWrappedInList(String yaml) {
    try {
      JsonNode rootNode = YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
      JsonNode pipelineNode = rootNode.path("inputSet").path("pipeline");
      if (pipelineNode.isMissingNode() || pipelineNode.isNull()) {
        return;
      }
      List<String> violations = RuntimeInputValuesValidator.findRuntimeInputsWrappedInList(pipelineNode, "");
      if (!violations.isEmpty()) {
        throw new InvalidRequestException(
            String.format("Input set contains runtime input marker '<+input>' wrapped inside a list at field(s): %s. "
                    + "Provide '<+input>' as a direct scalar value, not as a list element.",
                violations));
      }
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Could not validate input set YAML for wrapped runtime inputs", e);
    }
  }

  void validateIdentifyingFieldsInYAML(String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String yaml, boolean isParentIdQueryingEnabled) {
    String identifier = InputSetYamlHelper.getStringField(yaml, "identifier", "inputSet");
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("Identifier cannot be empty");
    }
    if (identifier.length() > 127) {
      throw new InvalidRequestException("Input Set identifier length cannot be more that 127 characters.");
    }
    InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml, pipelineIdentifier);
    if (!isParentIdQueryingEnabled) {
      InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml, "inputSet", orgIdentifier, projectIdentifier);
    }
  }

  public InputSetYamlDiffDTO getYAMLDiff(GitSyncSdkService gitSyncSdkService, PMSInputSetService inputSetService,
      PMSPipelineService pipelineService, ValidateAndMergeHelper validateAndMergeHelper, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String inputSetIdentifier,
      String pipelineBranch, String pipelineRepoID, InputSetsApiUtils inputSetsApiUtils, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabledForPipelines, boolean isParentIdQueryingEnabledForInputSet) {
    //    get input set and pipeline metadata for checking the if same repos or different repos to set the branch for
    //    input set
    InputSetEntity inputSetMetadata = inputSetService.getMetadata(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, inputSetIdentifier, false, false, true, scopeInfo, isParentIdQueryingEnabledForInputSet);

    if (EmptyPredicate.isEmpty(pipelineBranch) && StoreType.REMOTE.equals(inputSetMetadata.getStoreType())) {
      throw new InvalidRequestException(ERROR_PIPELINE_BRANCH_NOT_PROVIDED);
    }

    PipelineEntity pipelineMetadata = pipelineService.getPipelineMetadata(inputSetMetadata.getAccountIdentifier(),
        inputSetMetadata.getOrgIdentifier(), inputSetMetadata.getProjectIdentifier(),
        inputSetMetadata.getPipelineIdentifier(), false, true, scopeInfo, isParentIdQueryingEnabledForPipelines);
    // fetch complete input set yaml
    InputSetEntity inputSetEntity = getInputSetEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineMetadata, inputSetMetadata, inputSetIdentifier, inputSetService,
        inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(accountId),
        gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier), scopeInfo,
        isParentIdQueryingEnabledForInputSet);

    EntityGitDetails entityGitDetails = PMSInputSetElementMapper.getEntityGitDetails(inputSetEntity);
    // fetch complete pipeline yaml
    String pipelineYaml = getPipelineYaml(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        pipelineBranch, pipelineMetadata, pipelineRepoID, pipelineService, gitSyncSdkService, scopeInfo,
        isParentIdQueryingEnabledForPipelines);

    InputSetYamlDiffDTO yamlDiffDTO;
    if (inputSetEntity.getInputSetEntityType() == InputSetEntityType.INPUT_SET) {
      yamlDiffDTO = getYAMLDiffForInputSet(validateAndMergeHelper, inputSetEntity, pipelineYaml);
    } else {
      yamlDiffDTO = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(gitSyncSdkService, inputSetService,
          inputSetEntity, pipelineYaml, scopeInfo, isParentIdQueryingEnabledForInputSet);
    }

    yamlDiffDTO.setGitDetails(entityGitDetails);
    yamlDiffDTO.setYamlDiffPresent(!Objects.equals(yamlDiffDTO.getOldYAML(), yamlDiffDTO.getNewYAML()));
    return yamlDiffDTO;
  }

  @VisibleForTesting
  InputSetEntity getInputSetEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata,
      InputSetEntity inputSetMetadata, String inputSetIdentifier, PMSInputSetService inputSetService,
      boolean isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled, boolean isOldGitSyncEnabled,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<InputSetEntity> optionalInputSetEntity;
    if (EmptyPredicate.isNotEmpty(pipelineMetadata.getRepo()) && EmptyPredicate.isNotEmpty(inputSetMetadata.getRepo())
        && pipelineMetadata.getRepo().equals(inputSetMetadata.getRepo())) {
      String inputSetBranch = GitAwareContextHelper.getBranchFromGitContext();
      if (!GitAwareContextHelper.DEFAULT.equals(inputSetBranch)) {
        throwExceptionIfInputSetBranchNotEqualToPipelineBranch(pipelineBranch, inputSetBranch);
      }
      GitSyncBranchContext branchContext =
          buildGitSyncBranchContext(inputSetMetadata.getRepo(), inputSetBranch, inputSetMetadata.getConnectorRef());
      //      Fetch input set when pipeline and input set are in same repos
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        optionalInputSetEntity = inputSetService.getWithoutValidations(
            scopeInfo, pipelineIdentifier, inputSetIdentifier, false, false, false, isParentIdQueryingEnabled);
        inputSetBranch = GitAwareContextHelper.getBranchInSCMGitMetadata();
      }
      throwExceptionIfInputSetBranchNotEqualToPipelineBranch(pipelineBranch, inputSetBranch);
    } else if (EmptyPredicate.isNotEmpty(pipelineMetadata.getRepo())
        && EmptyPredicate.isNotEmpty(inputSetMetadata.getRepo())
        && !isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled) {
      throw new InvalidRequestException(
          "Reconciliation is not allowed for the given input set. Pipeline and input set must be in same repository. "
          + "Please enable account level default setting : 'Allow different repo for Pipeline and InputSets' if its "
          + "intended to keep pipeline and input set in different repository.");
    } else {
      //      Fetch input set when pipeline and input set are in different repos
      if (!isOldGitSyncEnabled) {
        GitAwareContextHelper.updateGitEntityContextWithBranch("");
      }
      optionalInputSetEntity = inputSetService.getWithoutValidations(
          scopeInfo, pipelineIdentifier, inputSetIdentifier, false, false, false, isParentIdQueryingEnabled);
    }
    if (optionalInputSetEntity.isEmpty()) {
      throw new InvalidRequestException(
          format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    return optionalInputSetEntity.get();
  }

  private void throwExceptionIfInputSetBranchNotEqualToPipelineBranch(String pipelineBranch, String inputSetBranch) {
    if (!inputSetBranch.equals(pipelineBranch)) {
      throw new InvalidRequestException("Reconciliation is not allowed for the given input set. Pipeline and InputSet "
          + "must be present on the same branch when they are in the same repository");
    }
  }

  private String getPipelineYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata, String pipelineRepoID,
      PMSPipelineService pipelineService, GitSyncSdkService gitSyncSdkService, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    boolean isOldGitSyncFlow = gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
    String pipelineYaml;
    if (isOldGitSyncFlow) {
      //      Old git experience flow for fetching the pipeline
      pipelineYaml = getPipelineYamlForOldGitSyncFlow(pipelineService, accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, pipelineBranch, pipelineRepoID);
    } else {
      //      New git experience flow for fetching the pipeline
      pipelineYaml = getPipelineYamlForGitX(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineBranch, pipelineMetadata, pipelineService, scopeInfo, isParentIdQueryingEnabled);
    }
    return pipelineYaml;
  }

  private String getPipelineYamlForGitX(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineBranch, PipelineEntity pipelineMetadata,
      PMSPipelineService pipelineService, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntity pipelineEntity;
    if (EmptyPredicate.isNotEmpty(pipelineBranch)) {
      GitSyncBranchContext branchContext =
          buildGitSyncBranchContext(pipelineMetadata.getRepo(), pipelineBranch, pipelineMetadata.getConnectorRef());

      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        pipelineEntity = getPipelineEntity(pipelineService, accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
      }
    } else {
      pipelineEntity = getPipelineEntity(pipelineService, accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
    }
    return pipelineEntity.getYaml();
  }

  InputSetYamlDiffDTO getYAMLDiffForInputSet(
      ValidateAndMergeHelper validateAndMergeHelper, InputSetEntity inputSetEntity, String pipelineYaml) {
    String inputSetYaml = inputSetEntity.getYaml();
    String newInputSetYaml = InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML(pipelineYaml, inputSetYaml);
    if (EmptyPredicate.isEmpty(newInputSetYaml)) {
      String pipelineTemplate = validateAndMergeHelper.getPipelineTemplate(pipelineYaml, null);
      if (EmptyPredicate.isEmpty(pipelineTemplate)) {
        return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(true).build();
      } else {
        return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(false).build();
      }
    }
    return InputSetYamlDiffDTO.builder()
        .oldYAML(inputSetYaml)
        .newYAML(newInputSetYaml)
        .isInputSetEmpty(false)
        .noUpdatePossible(false)
        .build();
  }

  public GitSyncBranchContext buildGitSyncBranchContext(String repo, String branch, String connectorRef) {
    return GitSyncBranchContext.builder()
        .gitBranchInfo(GitEntityInfo.builder().repoName(repo).branch(branch).connectorRef(connectorRef).build())
        .build();
  }
}
