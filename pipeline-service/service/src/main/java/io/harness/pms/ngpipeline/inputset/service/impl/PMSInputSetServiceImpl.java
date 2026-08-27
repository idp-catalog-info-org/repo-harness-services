/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service.impl;

import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.NAME_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_DISABLE_DEFAULT_STORE_TYPE_TO_INLINE_FOR_INPUT_SET_CREATE_CHECK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.HintException.HINT_INPUT_SET_ACCOUNT_SETTING;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.gitx.GitXAutoSyncLogContext.FORCE_IMPORT;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.INPUT_SET_NAME;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.INPUT_SET_SAVE;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.INPUT_SET_SAVE_ACTION;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_ID;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.InputSetEntityMetadata;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.common.EntityYamlRootNames;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.helper.ScmExceptionUtils;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.entityInfo.EntityObjectIdUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmClearCacheResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitx.CrudAction;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.gitx.GitXAutoSyncLogContext;
import io.harness.gitx.GitXFileValidationLogContext;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.gitx.InlineHCHelper;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.ForceImportInputSetResponse;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.InputSetRemoteRepoListResponse;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTOMapper;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.ForceImportInputSetYamlOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.validate.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.inputset.setupusage.InputSetSetupUsageHelper;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.inputset.InputSetRemoteRepoPage;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.utils.GitXUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeInfoHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.Utils;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.beans.YamlValidationErrorMetadata;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_GITX})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputSetServiceImpl implements PMSInputSetService {
  @Inject private PMSInputSetRepository inputSetRepository;
  @Inject private GitSyncSdkService gitSyncSdkService;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private PMSPipelineService pipelineService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;
  @Inject private InputSetsApiUtils inputSetsApiUtils;
  @Inject GitXSettingsHelper gitXSettingsHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private PipelineTelemetryHelper pipelineTelemetryHelper;
  @Inject private PMSInputSetServiceHelper pmsInputSetServiceHelper;
  @Inject private ScopeInfoHelper scopeInfoHelper;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private InputSetSetupUsageHelper inputSetSetupUsageHelper;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Input set [%s] under Project[%s], Organization [%s] for Pipeline [%s] already exists";

  private static final int MAX_LIST_SIZE = 1000;
  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";
  private static final String EXPLANATION_INPUT_SET_ACCOUNT_SETTING = "As the account level setting: ["
      + GitSyncConstants.ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUTSETS_SETTING
      + "] is disabled, the input set repository and the linked pipeline repository cannot be different";
  private final String INPUT_SET_NOT_FOUND_HINT =
      "Please check if there exist any input set with the file path: [%s] and Repo name: [%s]";

  @Override
  public InputSetEntity create(InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo) {
    String accountId = scopeInfoHelper.getAccountIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getAccountId);
    return create(inputSetEntity, hasNewYamlStructure, scopeInfo, true, true);
  }

  @Override
  public InputSetEntity create(InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabledForPipeline, boolean isParentIdQueryingEnabled) {
    String accountId = scopeInfoHelper.getAccountIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getAccountId);
    String orgId = scopeInfoHelper.getOrgIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getOrgIdentifier);
    String projectId =
        scopeInfoHelper.getProjectIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getProjectIdentifier);
    setParentUniqueIdAndUniqueIdIfNotPresent(inputSetEntity);
    boolean isOldGitSync = gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId);
    InputSetValidationHelper.validateInputSet(
        this, inputSetEntity, hasNewYamlStructure, true, isParentIdQueryingEnabled, scopeInfo);
    if (!isOldGitSync) {
      applyGitXSettingsIfApplicable(accountId, orgId, projectId);

      PipelineEntity pipelineEntityMetadata = pipelineService.getPipelineMetadata(inputSetEntity.getAccountIdentifier(),
          inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(),
          inputSetEntity.getPipelineIdentifier(), false, true, scopeInfo, isParentIdQueryingEnabledForPipeline);
      if (StoreType.INLINE_HC.equals(pipelineEntityMetadata.getStoreType())) {
        InlineHCHelper.checkAndUpdateContextForInlineHC(inputSetEntity, CrudAction.CREATE,
            InlineHCUpdateContextRequest.builder()
                .entityIdentifier(inputSetEntity.getIdentifier())
                .parentIdentifier(inputSetEntity.getPipelineIdentifier())
                .scope(isParentIdQueryingEnabled
                        ? Scope.of(scopeInfo)
                        : Scope.of(accountId, inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier()))
                .build(),
            pmsFeatureFlagHelper::isEnabled);
      }
      boolean isDefaultToInlineStoreTypeDisabled = pmsFeatureFlagHelper.isEnabled(
          accountId, PIPE_DISABLE_DEFAULT_STORE_TYPE_TO_INLINE_FOR_INPUT_SET_CREATE_CHECK);
      InputSetValidationHelper.checkForPipelineStoreType(pipelineEntityMetadata, isDefaultToInlineStoreTypeDisabled);
      validateInputSetSetting(inputSetEntity, pipelineEntityMetadata);
    }

    try {
      InputSetEntity savedInputSetEntity;
      if (isOldGitSync) {
        savedInputSetEntity = inputSetRepository.saveForOldGitSync(
            inputSetEntity, InputSetYamlDTOMapper.toDTO(inputSetEntity), scopeInfo);
      } else {
        savedInputSetEntity = inputSetRepository.save(inputSetEntity, scopeInfo, isParentIdQueryingEnabled);
      }
      sendInputSetSaveTelemetryEvent(inputSetEntity, scopeInfo, "create");
      publishInputSetSetupUsage(savedInputSetEntity, scopeInfo, isParentIdQueryingEnabled);
      return savedInputSetEntity;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format(DUP_KEY_EXP_FORMAT_STRING, inputSetEntity.getIdentifier(), projectId,
                                            orgId, inputSetEntity.getPipelineIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating Input Set " + inputSetEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving input set [%s]", inputSetEntity.getIdentifier()), e);
      throw new InvalidRequestException(
          String.format("Error while saving input set [%s]: %s", inputSetEntity.getIdentifier(), e.getMessage()));
    }
  }

  @Override
  public Optional<InputSetEntity> get(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier,
      boolean deleted, String pipelineBranch, String pipelineRepoID, boolean hasNewYamlStructure,
      boolean loadFromFallbackBranch, boolean loadFromCache, boolean isParentIdQueryingEnabled) {
    Optional<InputSetEntity> optionalInputSetEntity = getWithoutValidations(scopeInfo, pipelineIdentifier, identifier,
        deleted, loadFromFallbackBranch, loadFromCache, isParentIdQueryingEnabled);
    checkIfInputSetIsPresent(identifier, optionalInputSetEntity);

    if (optionalInputSetEntity.isEmpty()) {
      return optionalInputSetEntity;
    }
    // Remember inputSetEntity organization can be different from scopeInfo organization(Outdated information)
    InputSetEntity inputSetEntity = optionalInputSetEntity.get();
    if (io.harness.gitx.GitXUtils.isYamlStoreBackedByGit(inputSetEntity.getStoreType())) {
      ScmGitMetaData inputSetScmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
      if (pmsFeatureFlagHelper.isEnabled(scopeInfo.getAccountIdentifier(),
              FeatureName.PIPE_RESTRICT_INVALID_TEMPLATE_AND_INPUT_SET_YAML_THROW_EXCEPTION.name())) {
        try {
          InputSetValidationHelper.validateInputSet(
              this, inputSetEntity, hasNewYamlStructure, false, isParentIdQueryingEnabled, scopeInfo);
        } catch (InvalidRequestException e) {
          log.error(String.format("Error while validating input set yaml [%s]", identifier), e);
          inputSetEntity.setEntityInvalid(true);
        } finally {
          // input set validation involves fetching the pipeline, which can change the global scm metadata to that of
          // the pipeline. Hence, it needs to be changed back to that of the input set once validation is complete,
          // irrespective of whether the validation throws an exception or not
          GitAwareContextHelper.updateScmGitMetaData(inputSetScmGitMetaData);
        }
      } else {
        try {
          InputSetValidationHelper.validateInputSet(
              this, inputSetEntity, hasNewYamlStructure, false, isParentIdQueryingEnabled, scopeInfo);
        } finally {
          // input set validation involves fetching the pipeline, which can change the global scm metadata to that of
          // the pipeline. Hence, it needs to be changed back to that of the input set once validation is complete,
          // irrespective of whether the validation throws an exception or not
          GitAwareContextHelper.updateScmGitMetaData(inputSetScmGitMetaData);
        }
      }
    }
    return Optional.of(inputSetEntity);
  }

  @Override
  public Optional<InputSetEntity> getWithoutValidations(ScopeInfo scopeInfo, String pipelineIdentifier,
      String identifier, boolean deleted, boolean loadFromFallbackBranch, boolean loadFromCache,
      boolean isParentIdQueryingEnabled) {
    try {
      if (gitSyncSdkService.isGitSyncEnabled(
              scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier())) {
        return inputSetRepository.findForOldGitSync(
            scopeInfo, pipelineIdentifier, identifier, !deleted, isParentIdQueryingEnabled);
      }
      return inputSetRepository.find(scopeInfo, pipelineIdentifier, identifier, !deleted, false, loadFromFallbackBranch,
          loadFromCache, isParentIdQueryingEnabled);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving input set [%s]", identifier), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while retrieving input set [%s]", identifier), e);
      throw new InvalidRequestException(
          String.format("Error while retrieving input set [%s]: %s", identifier, e.getMessage()));
    }
  }

  @Override
  public Optional<InputSetEntity> getMetadataWithoutValidations(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String identifier, boolean deleted,
      boolean loadFromFallbackBranch, boolean getMetadata, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<InputSetEntity> optionalInputSetEntity;
    try {
      optionalInputSetEntity = inputSetRepository.find(isParentIdQueryingEnabled
              ? scopeInfo
              : scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier),
          pipelineIdentifier, identifier, !deleted, getMetadata, loadFromFallbackBranch, false,
          isParentIdQueryingEnabled);

    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while retrieving input set [%s]", identifier), e);
      throw new InvalidRequestException(
          String.format("Error while retrieving input set [%s]: %s", identifier, e.getMessage()));
    }
    return optionalInputSetEntity;
  }

  @Override
  public InputSetEntity getMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean getMetadata, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<InputSetEntity> optionalInputSetMetadataEntity =
        getMetadataWithoutValidations(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            inputSetIdentifier, false, false, true, scopeInfo, isParentIdQueryingEnabled);
    if (optionalInputSetMetadataEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    return optionalInputSetMetadataEntity.get();
  }

  @Override
  public void refreshGitFileCache(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, String branch, ScopeInfo scopeInfo) {
    if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_GITX_FORCE_REFRESH)) {
      throw new UnavailableFeatureException(
          String.format("Cache refresh for input set [%s] is not enabled for this account. Contact Harness support to "
                  + "enable feature flag %s.",
              inputSetIdentifier, FeatureName.PIPE_GITX_FORCE_REFRESH.name()));
    }
    if (EmptyPredicate.isEmpty(branch) || GitAwareContextHelper.DEFAULT.equals(branch)) {
      throw new InvalidRequestException(
          String.format("A valid git branch is required to refresh cache for input set [%s].", inputSetIdentifier));
    }
    log.info(String.format(
        "Refresh cache for input set with identifier %s for pipeline %s in project %s, org %s, account %s, branch %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId, branch));

    ScopeInfo effectiveScopeInfo =
        scopeInfo != null ? scopeInfo : scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    GitAwareContextHelper.updateGitEntityContextWithBranch(branch);

    InputSetEntity inputSetEntity =
        getMetadataWithoutValidations(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            inputSetIdentifier, false, false, true, effectiveScopeInfo, true)
            .orElseThrow(
                ()
                    -> new EntityNotFoundException(String.format(
                        "InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier)));

    if (!StoreType.REMOTE.equals(inputSetEntity.getStoreType())) {
      throw new InvalidRequestException(String.format(
          "Cache refresh for input set [%s] applies only to remote Git-backed input sets.", inputSetIdentifier));
    }

    Scope scope = Scope.of(effectiveScopeInfo);
    ScmClearCacheResponse clearCacheResponse =
        gitAwareEntityHelper.clearCache(inputSetEntity, scope, branch, EntityType.INPUT_SETS);
    if (clearCacheResponse == null || !clearCacheResponse.isStatus()) {
      List<String> failedFilePaths =
          clearCacheResponse == null ? Collections.emptyList() : clearCacheResponse.getFailedFilePaths();
      String scmError = clearCacheResponse == null ? null : clearCacheResponse.getErrorMessage();
      log.error(String.format("Git cache clear failed for input set [%s] in account %s, org %s, project %s. Failed "
              + "paths: %s. SCM error: %s.",
          inputSetIdentifier, accountId, orgIdentifier, projectIdentifier, failedFilePaths, scmError));
      throw new InternalServerErrorException(String.format(
          "Failed to refresh git file cache for input set [%s] on branch [%s]. Failed paths: %s.%s", inputSetIdentifier,
          branch, failedFilePaths, EmptyPredicate.isEmpty(scmError) ? "" : " SCM error: " + scmError));
    }
  }

  @Override
  public InputSetEntity update(ChangeType changeType, InputSetEntity inputSetEntity, boolean hasNewYamlStructure) {
    return update(changeType, inputSetEntity, hasNewYamlStructure,
        scopeResolutionHelper.getScopeInfo(
            inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier()));
  }

  @Override
  public InputSetEntity update(
      ChangeType changeType, InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo) {
    String accountId = scopeInfo.getAccountIdentifier();
    boolean isOldGitSync =
        gitSyncSdkService.isGitSyncEnabled(accountId, scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
    InputSetValidationHelper.validateInputSet(this, inputSetEntity, hasNewYamlStructure, true, true, scopeInfo);
    if (isOldGitSync) {
      return updateForOldGitSync(inputSetEntity, changeType, scopeInfo, true);
    }
    return makeInputSetUpdateCall(inputSetEntity, changeType, false, scopeInfo, true);
  }

  private InputSetEntity updateForOldGitSync(
      InputSetEntity inputSetEntity, ChangeType changeType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (GitContextHelper.getGitEntityInfo() != null && GitContextHelper.getGitEntityInfo().isNewBranch()) {
      return makeInputSetUpdateCall(inputSetEntity, changeType, true, scopeInfo, isParentIdQueryingEnabled);
    }
    Optional<InputSetEntity> optionalOriginalEntity =
        getWithoutValidations(scopeInfo, inputSetEntity.getPipelineIdentifier(), inputSetEntity.getIdentifier(), false,
            false, false, isParentIdQueryingEnabled);
    if (!optionalOriginalEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.",
              inputSetEntity.getIdentifier(), inputSetEntity.getPipelineIdentifier(), scopeInfo.getProjectIdentifier(),
              scopeInfo.getOrgIdentifier()));
    }

    InputSetEntity originalEntity = optionalOriginalEntity.get();
    if (inputSetEntity.getVersion() != null && !inputSetEntity.getVersion().equals(originalEntity.getVersion())) {
      throw new InvalidRequestException(format(
          "Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] is not on the correct version.",
          inputSetEntity.getIdentifier(), inputSetEntity.getPipelineIdentifier(), scopeInfo.getProjectIdentifier(),
          scopeInfo.getOrgIdentifier()));
    }
    InputSetEntity entityToUpdate = originalEntity.withYaml(inputSetEntity.getYaml())
                                        .withName(inputSetEntity.getName())
                                        .withDescription(inputSetEntity.getDescription())
                                        .withTags(inputSetEntity.getTags())
                                        .withInputSetReferences(inputSetEntity.getInputSetReferences())
                                        .withIsInvalid(false)
                                        .withIsEntityInvalid(false);

    return makeInputSetUpdateCall(entityToUpdate, changeType, true, scopeInfo, isParentIdQueryingEnabled);
  }
  @Override
  public InputSetEntity syncInputSetWithGit(EntityDetailProtoDTO entityDetail) {
    InputSetReferenceProtoDTO inputSetRef = entityDetail.getInputSetRef();
    //
    String accountId = StringValueUtils.getStringFromStringValue(inputSetRef.getAccountIdentifier());
    String orgId = StringValueUtils.getStringFromStringValue(inputSetRef.getOrgIdentifier());
    String projectId = StringValueUtils.getStringFromStringValue(inputSetRef.getProjectIdentifier());
    String pipelineId = StringValueUtils.getStringFromStringValue(inputSetRef.getPipelineIdentifier());
    String inputSetId = StringValueUtils.getStringFromStringValue(inputSetRef.getIdentifier());
    Optional<InputSetEntity> optionalInputSetEntity;
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(null, false)) {
      optionalInputSetEntity = getWithoutValidations(scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId),
          pipelineId, inputSetId, false, false, false, false);
    }
    if (!optionalInputSetEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.", inputSetId,
              pipelineId, projectId, orgId));
    }
    return makeInputSetUpdateCall(optionalInputSetEntity.get().withStoreType(null), ChangeType.ADD, true,
        scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId), false);
  }

  @Override
  public boolean switchValidationFlag(InputSetEntity entity, boolean isInvalid, boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(InputSetEntityKeys.parentUniqueId).is(entity.getParentUniqueId());
    } else {
      criteria.and(InputSetEntityKeys.accountId)
          .is(entity.getAccountId())
          .and(InputSetEntityKeys.orgIdentifier)
          .is(entity.getOrgIdentifier())
          .and(InputSetEntityKeys.projectIdentifier)
          .is(entity.getProjectIdentifier());
    }

    criteria.and(InputSetEntityKeys.pipelineIdentifier)
        .is(entity.getPipelineIdentifier())
        .and(InputSetEntityKeys.identifier)
        .is(entity.getIdentifier());
    if (entity.getYamlGitConfigRef() != null) {
      criteria.and(InputSetEntityKeys.yamlGitConfigRef)
          .is(entity.getYamlGitConfigRef())
          .and(InputSetEntityKeys.branch)
          .is(entity.getBranch());
    }

    Update update = new Update();
    update.set(InputSetEntityKeys.isInvalid, isInvalid);
    InputSetEntity inputSetEntity = inputSetRepository.update(criteria, update);
    return inputSetEntity != null;
  }

  @Override
  public boolean markGitSyncedInputSetInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, String invalidYaml) {
    Optional<InputSetEntity> optionalInputSetEntity =
        getWithoutValidations(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier),
            pipelineIdentifier, identifier, false, false, false, false);
    if (!optionalInputSetEntity.isPresent()) {
      log.warn(String.format(
          "Marking input set [%s] as invalid failed as it does not exist or has been deleted", identifier));
      return false;
    }
    InputSetEntity existingInputSet = optionalInputSetEntity.get();
    InputSetEntity updatedInputSet = existingInputSet.withYaml(invalidYaml)
                                         .withObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml))
                                         .withIsEntityInvalid(true);
    makeInputSetUpdateCall(updatedInputSet, ChangeType.NONE, true,
        scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier), true);
    return true;
  }

  private InputSetEntity makeInputSetUpdateCall(InputSetEntity entity, ChangeType changeType, boolean isOldFlow,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      InputSetEntity updatedEntity;
      if (isOldFlow) {
        updatedEntity = inputSetRepository.updateForOldGitSync(
            entity, InputSetYamlDTOMapper.toDTO(entity), changeType, scopeInfo, isParentIdQueryingEnabled);
      } else {
        updatedEntity = inputSetRepository.update(entity, scopeInfo, isParentIdQueryingEnabled);
      }
      if (updatedEntity == null) {
        throw new InvalidRequestException(
            format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] could not be updated.",
                entity.getIdentifier(), entity.getPipelineIdentifier(), scopeInfo.getProjectIdentifier(),
                scopeInfo.getOrgIdentifier()));
      }
      publishInputSetSetupUsage(updatedEntity, scopeInfo, isParentIdQueryingEnabled);
      return updatedEntity;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while updating Input Set " + entity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating input set [%s]", entity.getIdentifier()), e);
      throw new InvalidRequestException(
          String.format("Error while updating input set [%s]: %s", entity.getIdentifier(), e.getMessage()));
    }
  }

  @Override
  public boolean delete(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier, Long version,
      boolean isParentIdQueryingEnabled) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
      return deleteForOldGitSync(scopeInfo, pipelineIdentifier, identifier, version);
    }
    try {
      InputSetEntity inputSetMetadata = getMetadata(accountId, orgId, projectId, pipelineIdentifier, identifier, false,
          false, true, scopeInfo, isParentIdQueryingEnabled);
      Scope pipelineScope = isParentIdQueryingEnabled ? Scope.of(scopeInfo) : Scope.of(accountId, orgId, projectId);
      if (StoreType.INLINE_HC.equals(inputSetMetadata.getStoreType())) {
        InlineHCHelper.updateGitContext(CrudAction.DELETE, io.harness.yaml.utils.EntityType.INPUT_SET,
            InlineHCUpdateContextRequest.builder()
                .scope(pipelineScope)
                .entityIdentifier(inputSetMetadata.getIdentifier())
                .parentIdentifier(pipelineIdentifier)
                .build());
        try {
          gitAwareEntityHelper.deleteEntityOnGit(inputSetMetadata, pipelineScope);
        } catch (HintException exception) {
          log.error("Error deleting yaml file for inputset. Skipping delete operation.", exception);
          if (!checkIfFileNotPresentError(exception)) {
            throw exception;
          }
        }
      }
      inputSetRepository.delete(scopeInfo, pipelineIdentifier, identifier);
      deleteInputSetSetupUsage(inputSetMetadata, scopeInfo, isParentIdQueryingEnabled);
      return true;
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("InputSet [%s] for Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.",
              identifier, pipelineIdentifier, projectId, orgId));
    }
  }

  private boolean deleteForOldGitSync(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier, Long version) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Optional<InputSetEntity> optionalOriginalEntity =
        getWithoutValidations(scopeInfo, pipelineIdentifier, identifier, false, false, false, false);
    if (optionalOriginalEntity.isEmpty()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s], Account [%s] doesn't exist.",
              identifier, pipelineIdentifier, projectId, orgId, accountId));
    }
    InputSetEntity existingEntity = optionalOriginalEntity.get();
    if (version != null && !version.equals(existingEntity.getVersion())) {
      throw new InvalidRequestException(format("Input Set [%s], for pipeline [%s], under Project[%s], Organization "
              + "[%s], Account [%s] is not on the correct version.",
          identifier, pipelineIdentifier, projectId, orgId, accountId));
    }
    InputSetEntity entityWithDelete = existingEntity.withDeleted(true);
    try {
      inputSetRepository.deleteForOldGitSync(
          entityWithDelete, InputSetYamlDTOMapper.toDTO(entityWithDelete), scopeInfo);
      return true;
    } catch (Exception e) {
      log.error(String.format("Error while deleting input set [%s]", identifier), e);
      throw new InvalidRequestException(format(
          "Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s], Account [%s] couldn't be deleted.",
          identifier, pipelineIdentifier, projectId, orgId, accountId));
    }
  }

  @Override
  public Page<InputSetEntity> list(Criteria criteria, Pageable pageable, ScopeInfo scopeInfo) {
    return inputSetRepository.findAll(criteria, pageable, scopeInfo);
  }

  @Override
  public List<InputSetEntity> list(Criteria criteria) {
    return inputSetRepository.findAll(criteria);
  }

  @Override
  public void deleteInputSetsOnPipelineDeletion(PipelineEntity pipelineEntity) {
    Criteria criteria = new Criteria();

    criteria.and(InputSetEntityKeys.parentUniqueId)
        .is(pipelineEntity.getParentUniqueId())
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(pipelineEntity.getIdentifier());
    Query query = new Query(criteria);
    try {
      inputSetRepository.deleteAllInputSetsWhenPipelineDeleted(query);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("InputSets for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
              pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()),
          e);
    }
  }

  @Override
  public InputSetEntity updateGitFilePath(InputSetEntity inputSetEntity, String newFilePath) {
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(inputSetEntity.getAccountId())
                            .and(InputSetEntityKeys.orgIdentifier)
                            .is(inputSetEntity.getOrgIdentifier())
                            .and(InputSetEntityKeys.projectIdentifier)
                            .is(inputSetEntity.getProjectIdentifier())
                            .and(InputSetEntityKeys.pipelineIdentifier)
                            .is(inputSetEntity.getPipelineIdentifier())
                            .and(InputSetEntityKeys.identifier)
                            .is(inputSetEntity.getIdentifier());

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(InputSetEntityKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(InputSetEntityKeys.rootFolder, gitEntityFilePath.getRootFolder());
    return inputSetRepository.update(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(),
        inputSetEntity.getProjectIdentifier(), criteria, update);
  }

  @Override
  public boolean checkForInputSetsForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return inputSetRepository.existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true, scopeInfo, isParentIdQueryingEnabled);
  }

  @Override
  public InputSetEntity importInputSetFromRemote(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String inputSetIdentifier,
      InputSetImportRequestDTO inputSetImportRequestDTO, boolean isForceImport, ScopeInfo scopeInfo) {
    GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
    applyGitXSettingsIfApplicable(accountIdentifier, orgIdentifier, projectIdentifier);
    String repoUrl = getRepoUrlAndCheckForFileUniqueness(
        accountIdentifier, orgIdentifier, projectIdentifier, inputSetIdentifier, isForceImport, scopeInfo, true);
    String importedInputSetYAML = gitAwareEntityHelper.importFile(Scope.of(scopeInfo), true);
    InputSetEntity inputSetEntity;
    switch (inputSetImportRequestDTO.getVersion()) {
      case HarnessYamlVersion.V1:
        inputSetEntity = PMSInputSetElementMapper.toInputSetEntityV1(accountIdentifier, orgIdentifier,
            projectIdentifier, pipelineIdentifier, importedInputSetYAML, InputSetEntityType.INPUT_SET);
        break;
      case HarnessYamlVersion.V0:
        checkAndThrowMismatchInImportedInputSetMetadata(orgIdentifier, projectIdentifier, pipelineIdentifier,
            inputSetIdentifier, inputSetImportRequestDTO, importedInputSetYAML);
        inputSetEntity = PMSInputSetElementMapper.toInputSetEntity(accountIdentifier, importedInputSetYAML);
        break;
      default:
        throw new IllegalStateException("version not supported");
    }
    inputSetEntity.setRepoURL(repoUrl);
    setParentUniqueIdAndUniqueIdIfNotPresent(inputSetEntity);
    try {
      return inputSetRepository.saveForImportedYAML(inputSetEntity, scopeInfo, true);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, inputSetEntity.getIdentifier(), scopeInfo.getProjectIdentifier(),
              scopeInfo.getOrgIdentifier(), inputSetEntity.getPipelineIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating Input Set " + inputSetEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving input set [%s]", inputSetEntity.getIdentifier()), e);
      throw new InvalidRequestException(
          String.format("Error while saving input set [%s]: %s", inputSetEntity.getIdentifier(), e.getMessage()));
    }
  }

  @Override
  public InputSetEntity moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String inputSetIdentifier, InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO, ScopeInfo scopeInfo) {
    Optional<InputSetEntity> optionalInputSetEntity;
    try (EntityGitDetailsGuard ignore = new EntityGitDetailsGuard(GitEntityInfo.builder().build())) {
      optionalInputSetEntity = getWithoutValidations(scopeInfo, inputSetMoveConfigOperationDTO.getPipelineIdentifier(),
          inputSetIdentifier, false, false, false, true);
    }
    if (optionalInputSetEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    return moveInputSetEntity(accountIdentifier, orgIdentifier, projectIdentifier, inputSetMoveConfigOperationDTO,
        optionalInputSetEntity.get(), scopeInfo, true);
  }

  @Override
  public PMSInputSetListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = isParentIdQueryingEnabled
        ? PMSInputSetFilterHelper.buildCriteriaForRepoListing(scopeInfo, pipelineIdentifier)
        : PMSInputSetFilterHelper.buildCriteriaForRepoListing(
              accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    List<String> inputSetRepoList = inputSetRepository.findAllUniqueInputSetRepos(criteria);
    CollectionUtils.filter(inputSetRepoList, PredicateUtils.notNullPredicate());
    if (inputSetRepoList.size() > MAX_LIST_SIZE) {
      log.error(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return PMSInputSetListRepoResponse.builder().repositories(inputSetRepoList).build();
  }

  @Override
  public InputSetRemoteRepoListResponse getRemoteRepoListForAGivenScope(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("accountIdentifier is required");
    }
    InputSetRemoteRepoPage paged = inputSetRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, limit);
    return InputSetRemoteRepoListResponse.builder()
        .repositories(paged.getRepositories())
        .totalRepos(paged.getTotalRepos())
        .build();
  }

  @Override
  public String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    InputSetEntity inputSetMetadata = getMetadata(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, inputSetIdentifier, false, false, true, scopeInfo, isParentIdQueryingEnabled);
    if (StoreType.INLINE_HC.equals(inputSetMetadata.getStoreType())
        || StoreType.INLINE.equals(inputSetMetadata.getStoreType())) {
      log.error("Cannot update git metadata for input set with store type INLINE_HC or INLINE");
      return inputSetIdentifier;
    }

    validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier,
        updateGitDetailsParams, scopeInfo, isParentIdQueryingEnabled);
    Criteria criteria = isParentIdQueryingEnabled
        ? PMSInputSetFilterHelper.getCriteriaForFind(scopeInfo, pipelineIdentifier, inputSetIdentifier, true)
        : PMSInputSetFilterHelper.getCriteriaForFind(
              accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, true);
    Update update = PMSInputSetFilterHelper.getUpdateWithGitMetadata(updateGitDetailsParams);

    InputSetEntity inputSetAfterUpdate = inputSetRepository.updateEntity(criteria, update);
    if (inputSetAfterUpdate == null) {
      throw new EntityNotFoundException(
          format("InputSet with id [%s] is not present or has been deleted", inputSetIdentifier));
    }

    return inputSetAfterUpdate.getIdentifier();
  }

  @Override
  public List<JsonNode> getSanitizedInputsFromInputSetV1(List<JsonNode> inputSetJsonNodeList) {
    List<JsonNode> sanitizedInputSet = new ArrayList<>();
    for (JsonNode inputSet : inputSetJsonNodeList) {
      if (inputSet.has(YAMLFieldNameConstants.SPEC)) {
        sanitizedInputSet.add(inputSet.get(YAMLFieldNameConstants.SPEC));
      } else {
        sanitizedInputSet.add(inputSet);
      }
    }
    return sanitizedInputSet;
  }

  @Override
  public List<YamlValidationResponseDTO> validateInputSetYaml(String accountIdentifier,
      YamlValidationRequestDTO entityYamlValidationRequestDTO, boolean isParentIdQueryingEnabled) {
    try (GitXFileValidationLogContext context = new GitXFileValidationLogContext(entityYamlValidationRequestDTO)) {
      List<InputSetEntity> inputSetEntityList;
      Map<String, Optional<ScopeInfo>> scopeInfos = new HashMap<>();
      try {
        inputSetEntityList = pmsInputSetServiceHelper.fetchAllInputSetByFilePathAndRepo(accountIdentifier,
            entityYamlValidationRequestDTO.getFilePath(), entityYamlValidationRequestDTO.getRepoName());
        // InputSetEntity organization information is stale.
        // Fetching scopeInfo information from ScopeResolutionHelper
        if (isParentIdQueryingEnabled) {
          List<String> parentUniqueIdList = new ArrayList<>();
          inputSetEntityList.forEach(inputSetEntity -> { parentUniqueIdList.add(inputSetEntity.getParentUniqueId()); });
          scopeInfos = scopeResolutionHelper.getScopeInfos(accountIdentifier, parentUniqueIdList);
        }
      } catch (EntityNotFoundException e) {
        log.error("No Input Set exist with file path: {}, repo: {}", entityYamlValidationRequestDTO.getFilePath(),
            entityYamlValidationRequestDTO.getRepoName(), e);
        return List.of(YamlValidationResponseDTO.builder()
                           .validationErrorMetadata(YamlValidationErrorMetadata.builder()
                                                        .hint(String.format(INPUT_SET_NOT_FOUND_HINT,
                                                            entityYamlValidationRequestDTO.getFilePath(),
                                                            entityYamlValidationRequestDTO.getRepoName()))
                                                        .errorMessage(e.getMessage())
                                                        .build())
                           .isValid(false)
                           .build());
      }
      return getYamlValidationResponseDTOList(
          entityYamlValidationRequestDTO, inputSetEntityList, isParentIdQueryingEnabled, scopeInfos);
    }
  }

  @Override
  public ForceImportInputSetResponse forceImportInputSet(
      String accountIdentifier, ForceImportInputSetYamlOperationDTO request, ScopeInfo scopeInfo) {
    try (GitXAutoSyncLogContext context = new GitXAutoSyncLogContext(accountIdentifier, request, FORCE_IMPORT)) {
      // validate basic details
      validateForceImportRequest(accountIdentifier, request);
      String orgIdentifier = request.getOrgIdentifier();
      String projectIdentifier = request.getProjectIdentifier();
      String identifier = request.getIdentifier();
      String pipelineIdentifier = request.getPipelineIdentifier();
      // set git context
      setupGitContext(request);
      String repoUrl = getRepoUrlAndCheckForFileUniqueness(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, true, scopeInfo, true);
      String importedInputSetYAML = null;
      try {
        importedInputSetYAML = gitAwareEntityHelper.fetchYAMLFromRemote(scopeInfo, true);
      } catch (HintException | ExplanationException | ScmException ex) {
        log.error("Failed to fetch YAML during force-importing inputSet.", ex);
        throw ex;
      } catch (Exception ex) {
        log.error("Unexpected error while fetching inputSet yaml.", ex);
        throw new InternalServerErrorException("Unexpected error while fetching inputSet yaml.");
      }

      String inputSetVersion = request.getVersion();
      Map<String, String> inputSetMetadata = getInputSetMetadata(request, importedInputSetYAML, inputSetVersion);

      InputSetEntity inputSetEntity = pmsInputSetServiceHelper.buildInputSetEntityForForceImport(accountIdentifier,
          orgIdentifier, projectIdentifier, pipelineIdentifier, importedInputSetYAML, inputSetVersion,
          inputSetMetadata.get(NAME_KEY), inputSetMetadata.get(IDENTIFIER_KEY));
      // the identifier can come from the YAML on git, hence validating it before persisting the entity
      validateInputSetIdentifier(inputSetEntity.getIdentifier());

      inputSetEntity.setRepoURL(repoUrl);
      InputSetEntity savedInputSetEntity = null;
      setParentUniqueIdAndUniqueIdIfNotPresent(inputSetEntity);
      try {
        savedInputSetEntity = inputSetRepository.saveForImportedYAML(inputSetEntity, scopeInfo, true);
      } catch (DuplicateKeyException ex) {
        log.error(format(DUP_KEY_EXP_FORMAT_STRING, inputSetEntity.getIdentifier(), scopeInfo.getProjectIdentifier(),
            scopeInfo.getOrgIdentifier(), inputSetEntity.getPipelineIdentifier(), ex));
        throw new DuplicateFieldException(
            format(DUP_KEY_EXP_FORMAT_STRING, inputSetEntity.getIdentifier(), scopeInfo.getProjectIdentifier(),
                scopeInfo.getOrgIdentifier(), inputSetEntity.getPipelineIdentifier()),
            USER_SRE, ex);
      } catch (Exception ex) {
        log.error(String.format("Unexpected error while saving input set [%s]", inputSetEntity.getIdentifier()), ex);
        throw new InternalServerErrorException(String.format(
            "Unexpected error while saving input set [%s]: %s", inputSetEntity.getIdentifier(), ex.getMessage()));
      }

      return ForceImportInputSetResponse.builder().identifier(savedInputSetEntity.getIdentifier()).build();
    }
  }

  private Map<String, String> getInputSetMetadata(
      ForceImportInputSetYamlOperationDTO request, String importedInputSetYAML, String inputSetVersion) {
    Map<String, String> inputSetMetadata = new HashMap<>();
    inputSetMetadata.put(NAME_KEY, request.getIdentifier());
    inputSetMetadata.put(IDENTIFIER_KEY, request.getIdentifier());

    if (isNotEmpty(importedInputSetYAML)) {
      Map<String, String> inputSetMetadataFromYaml =
          pmsInputSetServiceHelper.getNameAndIdentifierFromYaml(importedInputSetYAML, inputSetVersion);
      Utils.replaceFromInputIfEmpty(inputSetMetadataFromYaml, inputSetMetadata, NAME_KEY);
      Utils.replaceFromInputIfEmpty(inputSetMetadataFromYaml, inputSetMetadata, IDENTIFIER_KEY);
    }
    return inputSetMetadata;
  }

  private void validateInputSetIdentifier(String inputSetIdentifier) {
    if (isEmpty(inputSetIdentifier)
        || !EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(inputSetIdentifier).matches()) {
      throw new InvalidRequestException(
          "Input Set Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
          + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");
    }
  }

  private void setupGitContext(ForceImportInputSetYamlOperationDTO request) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(request.getBranch())
                                                 .filePath(request.getFilePath())
                                                 .connectorRef(request.getConnectorRef())
                                                 .storeType(StoreType.REMOTE)
                                                 .repoName(request.getRepoName())
                                                 .build());
  }

  @VisibleForTesting
  void validateForceImportRequest(
      @NonNull String accountIdentifier, @NonNull ForceImportInputSetYamlOperationDTO forceImportRequest) {
    Utils.validateField(forceImportRequest.getOrgIdentifier(), "org identifier");
    Utils.validateField(forceImportRequest.getProjectIdentifier(), "project identifier");

    checkOrganizationExists(accountIdentifier, forceImportRequest.getOrgIdentifier());
    checkProjectExists(
        accountIdentifier, forceImportRequest.getOrgIdentifier(), forceImportRequest.getProjectIdentifier());

    Utils.validateField(forceImportRequest.getPipelineIdentifier(), "pipeline identifier");
    Utils.validateField(forceImportRequest.getIdentifier(), "inputSet identifier");
    Utils.validateField(forceImportRequest.getFilePath(), "file path");
    Utils.validateField(forceImportRequest.getRepoName(), "repo name");

    GitXUtils.validateConnectorRefRequirement(
        forceImportRequest.getConnectorRef(), forceImportRequest.getIsHarnessCodeRepo());
  }

  private void checkOrganizationExists(String accountIdentifier, String orgIdentifier) {
    getResponse(organizationClient.getOrganization(orgIdentifier, accountIdentifier),
        String.format("Organization with orgIdentifier %s not found", orgIdentifier));
  }

  private void checkProjectExists(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier),
          format("Project with orgIdentifier %s and identifier %s not found", orgIdentifier, projectIdentifier));
    }
  }

  private List<YamlValidationResponseDTO> getYamlValidationResponseDTOList(
      YamlValidationRequestDTO entityYamlValidationRequestDTO, List<InputSetEntity> inputSetEntityList,
      boolean isParentIdQueryingEnabled, Map<String, Optional<ScopeInfo>> scopeInfos) {
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS = new ArrayList<>();
    inputSetEntityList.forEach(inputSetEntity -> {
      inputSetEntity.setYaml(entityYamlValidationRequestDTO.getYaml());
      GitEntityInfo gitEntityInfo = getGitEntityInfoForInputSet(entityYamlValidationRequestDTO);
      yamlValidationResponseDTOS.add(
          performValidation(entityYamlValidationRequestDTO.getFilePath(), entityYamlValidationRequestDTO.getRepoName(),
              entityYamlValidationRequestDTO.getBranch(), inputSetEntity, gitEntityInfo, isParentIdQueryingEnabled,
              scopeInfos.getOrDefault(inputSetEntity.getParentUniqueId(), Optional.empty()).orElse(null)));
    });
    return yamlValidationResponseDTOS;
  }

  private YamlValidationResponseDTO performValidation(String filePath, String repoName, String branch,
      InputSetEntity inputSetEntity, GitEntityInfo gitEntityInfo, boolean isParentIdQueryingEnabled,
      ScopeInfo scopeInfo) {
    try (EntityGitDetailsGuard entityGitDetailsGuard = new EntityGitDetailsGuard(gitEntityInfo)) {
      if (isParentIdQueryingEnabled) {
        PMSInputSetElementMapper.toInputSetEntityFromVersion(scopeInfo, inputSetEntity.getPipelineIdentifier(),
            inputSetEntity.getYaml(), inputSetEntity.getHarnessVersion(), inputSetEntity.getInputSetEntityType());
      } else {
        PMSInputSetElementMapper.toInputSetEntityFromVersion(inputSetEntity.getAccountId(),
            inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(),
            inputSetEntity.getPipelineIdentifier(), inputSetEntity.getYaml(), inputSetEntity.getHarnessVersion(),
            inputSetEntity.getInputSetEntityType());
      }
      if (isParentIdQueryingEnabled && scopeInfo == null) {
        throw new InvalidRequestException(
            String.format("Failed to retrieve scope information for input set [%s] with parentUniqueId [%s]",
                inputSetEntity.getIdentifier(), inputSetEntity.getParentUniqueId()));
      }
      InputSetValidationHelper.validateInputSet(
          this, inputSetEntity, false, true, isParentIdQueryingEnabled, scopeInfo);
      return YamlValidationResponseDTO.builder()
          .entityMetadata(InputSetEntityMetadata.builder()
                              .scope(isParentIdQueryingEnabled
                                      ? Scope.of(scopeInfo)
                                      : Scope.of(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(),
                                            inputSetEntity.getProjectIdentifier()))
                              .identifier(inputSetEntity.getIdentifier())
                              .pipelineIdentifier(inputSetEntity.getPipelineIdentifier())
                              .build())
          .isValid(true)
          .build();
    } catch (io.harness.yaml.validator.InvalidYamlException | InvalidRequestException e) {
      log.error("Given Input Set yaml with file path: {}, repo: {}, branch: {} is not valid yaml: ", filePath, repoName,
          branch, e);
      return YamlValidationResponseDTO.builder()
          .entityMetadata(InputSetEntityMetadata.builder()
                              .scope(isParentIdQueryingEnabled
                                      ? Scope.of(scopeInfo)
                                      : Scope.of(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(),
                                            inputSetEntity.getProjectIdentifier()))
                              .identifier(inputSetEntity.getIdentifier())
                              .pipelineIdentifier(inputSetEntity.getPipelineIdentifier())
                              .build())
          .validationErrorMetadata(YamlValidationErrorMetadata.builder().errorMessage(e.getMessage()).build())
          .isValid(false)
          .build();
    } catch (Exception e) {
      log.error("Input Set validation failed with unexpected error: ", e);
      return YamlValidationResponseDTO.builder()
          .entityMetadata(InputSetEntityMetadata.builder()
                              .scope(isParentIdQueryingEnabled
                                      ? Scope.of(scopeInfo)
                                      : Scope.of(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(),
                                            inputSetEntity.getProjectIdentifier()))
                              .identifier(inputSetEntity.getIdentifier())
                              .pipelineIdentifier(inputSetEntity.getPipelineIdentifier())
                              .build())
          .validationErrorMetadata(YamlValidationErrorMetadata.builder().errorMessage(e.getMessage()).build())
          .build();
    }
  }

  private void validateRepo(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isEmpty(updateGitDetailsParams.getRepoName())) {
      return;
    }

    String connectorRef = updateGitDetailsParams.getConnectorRef();
    if (connectorRef == null) {
      Optional<InputSetEntity> optionalInputSetEntity = getWithoutValidations(
          scopeInfo, pipelineIdentifier, inputSetIdentifier, false, false, false, isParentIdQueryingEnabled);
      checkIfInputSetIsPresent(inputSetIdentifier, optionalInputSetEntity);

      connectorRef = optionalInputSetEntity.get().getConnectorRef();
    }

    gitAwareEntityHelper.validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef,
        updateGitDetailsParams.getRepoName(), scopeInfo);
  }

  private void checkIfInputSetIsPresent(String inputSetIdentifier, Optional<InputSetEntity> optionalInputSetEntity) {
    if (optionalInputSetEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
  }

  @VisibleForTesting
  protected InputSetEntity moveInputSetEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO, InputSetEntity inputSetToMove, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria = isParentIdQueryingEnabled
        ? PMSInputSetFilterHelper.getCriteriaForFind(
              scopeInfo, inputSetMoveConfigOperationDTO.getPipelineIdentifier(), inputSetToMove.getIdentifier(), true)
        : PMSInputSetFilterHelper.getCriteriaForFind(accountIdentifier, orgIdentifier, projectIdentifier,
              inputSetMoveConfigOperationDTO.getPipelineIdentifier(), inputSetToMove.getIdentifier(), true);
    Update update;

    if (INLINE_TO_REMOTE.equals(inputSetMoveConfigOperationDTO.getMoveConfigOperationType())) {
      setupGitContext(inputSetMoveConfigOperationDTO);

      update = getUpdateForInputSetInlineToRemote(accountIdentifier, orgIdentifier, projectIdentifier,
          inputSetMoveConfigOperationDTO, scopeInfo, isParentIdQueryingEnabled);
    } else if (REMOTE_TO_INLINE.equals(inputSetMoveConfigOperationDTO.getMoveConfigOperationType())) {
      update = getUpdateForInputSetRemoteToInline();
    } else {
      log.error("Invalid move config operation provided: {}",
          inputSetMoveConfigOperationDTO.getMoveConfigOperationType().name());
      throw new InvalidRequestException(String.format("Invalid move config operation specified [%s].",
          inputSetMoveConfigOperationDTO.getMoveConfigOperationType().name()));
    }
    return inputSetRepository.updateInputSetEntity(inputSetToMove, criteria, update,
        inputSetMoveConfigOperationDTO.getMoveConfigOperationType(), scopeInfo, isParentIdQueryingEnabled);
  }

  private void setupGitContext(InputSetMoveConfigOperationDTO inputSetMoveConfig) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder()
            .branch(inputSetMoveConfig.getBranch())
            .filePath(inputSetMoveConfig.getFilePath())
            .commitMsg(inputSetMoveConfig.getCommitMessage())
            .isNewBranch(isNotEmpty(inputSetMoveConfig.getBranch()) && isNotEmpty(inputSetMoveConfig.getBaseBranch()))
            .baseBranch(inputSetMoveConfig.getBaseBranch())
            .connectorRef(inputSetMoveConfig.getConnectorRef())
            .storeType(StoreType.REMOTE)
            .repoName(inputSetMoveConfig.getRepoName())
            .build());
  }

  private Update getUpdateForInputSetInlineToRemote(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Update update = new Update();
    update.set(InputSetEntityKeys.storeType, StoreType.REMOTE);
    update.set(InputSetEntityKeys.repo, inputSetMoveConfigOperationDTO.getRepoName());
    update.set(InputSetEntityKeys.filePath, inputSetMoveConfigOperationDTO.getFilePath());
    update.set(InputSetEntityKeys.connectorRef, inputSetMoveConfigOperationDTO.getConnectorRef());
    update.set(InputSetEntityKeys.repoURL,
        isParentIdQueryingEnabled
            ? gitAwareEntityHelper.getRepoUrl(scopeInfo)
            : gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier));
    return update;
  }

  private Update getUpdateForInputSetRemoteToInline() {
    Update update = new Update();
    update.set(InputSetEntityKeys.storeType, StoreType.INLINE);
    update.unset(InputSetEntityKeys.repo);
    update.unset(InputSetEntityKeys.filePath);
    update.unset(InputSetEntityKeys.connectorRef);
    update.unset(InputSetEntityKeys.repoURL);
    return update;
  }

  // todo: move to helper class when created during refactoring
  @VisibleForTesting
  void checkAndThrowMismatchInImportedInputSetMetadata(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, InputSetImportRequestDTO inputSetImportRequest,
      String importedInputSet) {
    if (EmptyPredicate.isEmpty(importedInputSet)) {
      String errorMessage = format("Empty YAML found on Git in branch [%s] for Input Set [%s] of Pipeline [%s] under "
              + "Project[%s], Organization [%s].",
          GitAwareContextHelper.getBranchInRequest(), inputSetIdentifier, pipelineIdentifier, projectIdentifier,
          orgIdentifier);
      throw buildInvalidYamlException(errorMessage, importedInputSet);
    }
    YamlField inputSetYAMLField;
    try {
      inputSetYAMLField = YamlUtils.readTree(importedInputSet);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw buildInvalidYamlException(errorMessage, importedInputSet);
    }
    YamlField inputSetInnerField = inputSetYAMLField.getNode().getField(EntityYamlRootNames.INPUT_SET);
    boolean isOverlay = false;
    if (inputSetInnerField == null) {
      inputSetInnerField = inputSetYAMLField.getNode().getField(EntityYamlRootNames.OVERLAY_INPUT_SET);
      isOverlay = true;
      if (inputSetInnerField == null) {
        String errorMessage = format("File found on Git in branch [%s] for filepath [%s] is not an Input Set YAML.",
            GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
        throw buildInvalidYamlException(errorMessage, importedInputSet);
      }
    }
    checkAndThrowMismatchInImportedInputSetMetadataHelper(orgIdentifier, projectIdentifier, pipelineIdentifier,
        inputSetIdentifier, inputSetImportRequest, importedInputSet, inputSetYAMLField, isOverlay);
  }

  void checkAndThrowMismatchInImportedInputSetMetadataHelper(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, InputSetImportRequestDTO inputSetImportRequest,
      String importedInputSet, YamlField inputSetField, boolean isOverlay) {
    YamlField inputSetInnerField;
    if (isOverlay) {
      inputSetInnerField = inputSetField.getNode().getField(EntityYamlRootNames.OVERLAY_INPUT_SET);
    } else {
      inputSetInnerField = inputSetField.getNode().getField(EntityYamlRootNames.INPUT_SET);
    }
    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = inputSetInnerField.getNode().getIdentifier();
    if (!inputSetIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = inputSetInnerField.getNode().getName();
    if (!inputSetImportRequest.getInputSetName().equals(nameFromGit)) {
      changedFields.put(YAMLFieldNameConstants.NAME, nameFromGit);
    }

    String orgIdentifierFromGit = inputSetInnerField.getNode().getStringValue(YAMLFieldNameConstants.ORG_IDENTIFIER);
    if (!orgIdentifier.equals(orgIdentifierFromGit)) {
      changedFields.put(YAMLFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    }

    String projectIdentifierFromGit =
        inputSetInnerField.getNode().getStringValue(YAMLFieldNameConstants.PROJECT_IDENTIFIER);
    if (!projectIdentifier.equals(projectIdentifierFromGit)) {
      changedFields.put(YAMLFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    }

    if (isOverlay) {
      String pipelineIdentifierFromGit =
          inputSetInnerField.getNode().getStringValue(YAMLFieldNameConstants.PIPELINE_IDENTIFIER);
      if (!pipelineIdentifier.equals(pipelineIdentifierFromGit)) {
        changedFields.put(YAMLFieldNameConstants.PIPELINE_IDENTIFIER, pipelineIdentifierFromGit);
      }
    } else {
      String pipelineIdentifierFromGit = inputSetInnerField.getNode()
                                             .getFieldOrThrow(YAMLFieldNameConstants.PIPELINE)
                                             .getNode()
                                             .getStringValue(YAMLFieldNameConstants.IDENTIFIER);
      if (!pipelineIdentifier.equals(pipelineIdentifierFromGit)) {
        changedFields.put(YAMLFieldNameConstants.PIPELINE_IDENTIFIER, pipelineIdentifierFromGit);
      }
    }

    if (!changedFields.isEmpty()) {
      InvalidFieldsDTO invalidFields = InvalidFieldsDTO.builder().expectedValues(changedFields).build();
      throw new InvalidRequestException(
          "Requested metadata params do not match the values found in the YAML on Git for these fields: "
              + changedFields.keySet(),
          invalidFields);
    }
  }

  // todo: move to helper class when created during refactoring
  InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.inputSet").build()))
            .build();
    return new InvalidYamlException(errorMessage, errorWrapperDTO, pipelineYaml);
  }

  String getRepoUrlAndCheckForFileUniqueness(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String inputSetIdentifier, boolean isForceImport, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = isParentIdQueryingEnabled
        ? gitAwareEntityHelper.getRepoUrl(scopeInfo)
        : gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);

    if (Boolean.TRUE.equals(isForceImport)) {
      log.info("Importing YAML forcefully with InputSet Id: {}, RepoURl: {}, FilePath: {}", inputSetIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (inputSetRepository.checkIfInputSetWithGivenFilePathExists(
                   accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with InputSet Id: " + inputSetIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " already exists.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  @VisibleForTesting
  void validateInputSetSetting(InputSetEntity inputSetEntity, PipelineEntity pipelineEntity) {
    if (!inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(inputSetEntity.getAccountId())) {
      GitAwareContextHelper.initDefaultScmGitMetaData();
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null && StoreType.REMOTE.equals(gitEntityInfo.getStoreType())) {
        String inputSetRepo = gitEntityInfo.getRepoName();
        validatePipelineAndInputSetRepos(pipelineEntity.getRepo(), inputSetRepo);
      }
    }
  }

  private void validatePipelineAndInputSetRepos(String pipelineRepo, String inputSetRepo) {
    if (EmptyPredicate.isNotEmpty(pipelineRepo) && EmptyPredicate.isNotEmpty(inputSetRepo)
        && pipelineRepo.equals(inputSetRepo)) {
      log.info("The InputSet and the Pipeline are created in the same repo.");
    } else {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_INPUT_SET_ACCOUNT_SETTING,
          EXPLANATION_INPUT_SET_ACCOUNT_SETTING,
          new InvalidRequestException(String.format(
              "Input-set repository [%s] doesn't match linked pipeline repository [%s]", inputSetRepo, pipelineRepo)));
    }
  }

  @VisibleForTesting
  void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.INPUT_SETS);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
  }

  private void sendInputSetSaveTelemetryEvent(InputSetEntity entity, ScopeInfo scopeInfo, String actionType) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(INPUT_SET_NAME, entity.getName());
    properties.put(ORG_ID, scopeInfoHelper.getOrgIdentifier(scopeInfo, entity, InputSetEntity::getOrgIdentifier));
    properties.put(
        PROJECT_ID, scopeInfoHelper.getProjectIdentifier(scopeInfo, entity, InputSetEntity::getProjectIdentifier));
    properties.put(INPUT_SET_SAVE_ACTION, actionType);
    pipelineTelemetryHelper.sendTelemetryEventWithAccountName(INPUT_SET_SAVE,
        scopeInfoHelper.getAccountIdentifier(scopeInfo, entity, InputSetEntity::getAccountIdentifier), properties);
  }

  private GitEntityInfo getGitEntityInfoForInputSet(YamlValidationRequestDTO entityYamlValidationRequestDTO) {
    return GitEntityInfo.builder()
        .branch(entityYamlValidationRequestDTO.getBranch())
        .parentEntityRepoName(entityYamlValidationRequestDTO.getRepoName())
        .build();
  }

  private boolean checkIfFileNotPresentError(HintException e) {
    ScmException scmException = ScmExceptionUtils.getScmException(e);
    return scmException != null && scmException.getMessage().equals(SCMExceptionErrorMessages.FILE_NOT_FOUND_ERROR);
  }

  private void setParentUniqueIdAndUniqueIdIfNotPresent(InputSetEntity inputSetEntity) {
    if (isEmpty(inputSetEntity.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfo = scopeResolutionHelper.getScopeInfoOptional(inputSetEntity.getAccountIdentifier(),
          inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier());
      String parentUniqueId = null;
      if (scopeInfo.isPresent()) {
        parentUniqueId = scopeInfo.get().getUniqueId();
      }
      inputSetEntity.setParentUniqueId(parentUniqueId);
    }
    if (isEmpty(inputSetEntity.getUniqueId())) {
      inputSetEntity.setUniqueId(generateUuid());
    }
  }

  @Override
  public Page<InputSetEntity> getBatchInputSetsMetadata(
      ScopeInfo scopeInfo, BatchInputSetsRequestDTO pipelineIdentifiersRequest) {
    List<String> pipelineIdentifiers = pipelineIdentifiersRequest.getPipelineIdentifiers();

    String accountIdentifier = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();

    int page = pipelineIdentifiersRequest.getPage() != null ? pipelineIdentifiersRequest.getPage() : 0;
    int size = pipelineIdentifiersRequest.getSize() != null ? pipelineIdentifiersRequest.getSize() : 10;
    String searchTerm = pipelineIdentifiersRequest.getSearchTerm();

    pipelineIdentifiers = pipelineIdentifiers.stream().filter(id -> !isEmpty(id)).collect(Collectors.toList());

    if (isEmpty(pipelineIdentifiers)) {
      throw new InvalidRequestException("Pipeline identifiers list cannot be empty.");
    }
    Criteria criteria =
        PMSInputSetFilterHelper.getCriteriaForFindByPipelineIdentifiers(accountIdentifier, orgIdentifier,
            projectIdentifier, scopeInfo, InputSetEntityType.INPUT_SET, false, pipelineIdentifiers, true, searchTerm);

    Pageable pageable = PageRequest.of(page, size);
    return inputSetRepository.findAllFromSecondaryDb(criteria, List.of(InputSetEntityKeys.yaml), pageable, scopeInfo);
  }

  @Override
  public Page<InputSetEntity> getAllInputSetsMetadataForProject(
      ScopeInfo scopeInfo, int page, int size, String searchTerm) {
    String accountIdentifier = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();

    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForAllInputSetsInProject(accountIdentifier, orgIdentifier,
        projectIdentifier, scopeInfo, InputSetEntityType.INPUT_SET, false, true, searchTerm);

    Pageable pageable = PageRequest.of(page, size);
    return inputSetRepository.findAllFromSecondaryDb(criteria, List.of(InputSetEntityKeys.yaml), pageable, scopeInfo);
  }

  @Override
  public BulkInputSetsResponseDTO getBulkInputSets(
      ScopeInfo scopeInfo, String pipelineIdentifier, BulkInputSetsRequestDTO inputSetIdentifiersRequest) {
    if (inputSetIdentifiersRequest == null || isEmpty(inputSetIdentifiersRequest.getInputSetIdentifiers())) {
      throw new InvalidRequestException("Input set identifiers request cannot be null or empty.");
    }

    List<String> identifiers = inputSetIdentifiersRequest.getInputSetIdentifiers()
                                   .stream()
                                   .filter(id -> !isEmpty(id))
                                   .collect(Collectors.toList());

    if (isEmpty(identifiers)) {
      throw new InvalidRequestException("Input set identifiers list cannot be empty.");
    }

    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForFindByInputSetIdentifiers(
        scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), scopeInfo,
        false, pipelineIdentifier, identifiers, true);

    List<InputSetEntity> inputSetEntities = inputSetRepository.findAllFromSecondaryDb(criteria);

    List<InputSetSummaryResponseDTOPMS> result = inputSetEntities.stream()
                                                     .map(PMSInputSetElementMapper::toInputSetSummaryResponseDTOPMS)
                                                     .collect(Collectors.toList());

    return BulkInputSetsResponseDTO.builder().inputSets(result).build();
  }

  private void publishInputSetSetupUsage(
      InputSetEntity inputSetEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (inputSetEntity == null || StoreType.INLINE.equals(inputSetEntity.getStoreType())) {
      return;
    }
    try {
      ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
      String branch = scmGitMetaData != null ? scmGitMetaData.getBranchName() : null;
      String repo = scmGitMetaData != null ? scmGitMetaData.getRepoName() : null;
      inputSetSetupUsageHelper.publishSetupUsageEvent(
          inputSetEntity, scopeInfo, isParentIdQueryingEnabled, branch, repo);
    } catch (Exception ex) {
      log.error(
          "Error publishing setup usage for input set [{}]: {}", inputSetEntity.getIdentifier(), ex.getMessage(), ex);
    }
  }

  private void deleteInputSetSetupUsage(
      InputSetEntity inputSetEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (inputSetEntity == null || !StoreType.REMOTE.equals(inputSetEntity.getStoreType())) {
      return;
    }
    try {
      inputSetSetupUsageHelper.deleteExistingSetupUsages(inputSetEntity, scopeInfo, isParentIdQueryingEnabled);
    } catch (Exception ex) {
      log.error(
          "Error deleting setup usage for input set [{}]: {}", inputSetEntity.getIdentifier(), ex.getMessage(), ex);
    }
  }
}
