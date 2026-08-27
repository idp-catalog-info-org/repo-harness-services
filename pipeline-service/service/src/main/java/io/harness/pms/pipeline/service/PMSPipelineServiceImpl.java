/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.expression.common.ExpressionConstants.EXPR_END_ESC;
import static io.harness.expression.common.ExpressionConstants.EXPR_START_ESC;
import static io.harness.gitx.GitXAutoSyncLogContext.FORCE_IMPORT;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.pms.pipeline.service.PMSPipelineServiceStepHelper.LIBRARY;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.allowedvalues.AllowedValuesUsagesDTO;
import io.harness.allowedvalues.AllowedValuesUsagesInternalDTO;
import io.harness.allowedvalues.AllowedValuesUsagesRequestDTO;
import io.harness.allowedvalues.EntityListWithAllowedValuesResponse;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.autosync.ForceImportGovernanceResponse;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.PipelineEntityMetadata;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.PipelineException;
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
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmClearCacheResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitx.CrudAction;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.gitx.GitXAutoSyncLogContext;
import io.harness.gitx.GitXFileValidationLogContext;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.gitx.InlineHCHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.ForceImportPipelineResponse;
import io.harness.pms.pipeline.ForceImportPipelineYamlOperationDTO;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoListResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.pipeline.StepPalleteModuleInfo;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelineTemplateReferenceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.intfc.PipelineGetResult;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRemoteRepoPage;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PipelineYamlUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;
import io.harness.yaml.validator.beans.YamlValidationErrorMetadata;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceImpl implements PMSPipelineService {
  public static final String ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM = "Error connecting to systems upstream";
  public static final String EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE =
      "Events framework is down for Pipeline Service.";
  public static final String TEMPLATE_REF_PIPELINE = "template_ref_by_pipeline";
  public static final String INVALID_YAML_IN_NODE = "Invalid yaml in node [%s]";
  public static final String FILE_NOT_FOUND_ERROR = "%s If the entity yaml is stored in Harness, please restore the "
      + "file on git or delete the entity and create again.";
  @Inject private final PMSPipelineRepository pmsPipelineRepository;
  @Inject private final PmsSdkInstanceService pmsSdkInstanceService;
  @Inject private final PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject private final PipelineTemplateReferenceHelper pipelineTemplateReferenceHelper;

  @Inject private final PipelineGovernanceService pipelineGovernanceService;
  @Inject private final PMSPipelineServiceStepHelper pmsPipelineServiceStepHelper;
  @Inject private final GitSyncSdkService gitSyncSdkService;
  @Inject private final PipelineCloneHelper pipelineCloneHelper;
  @Inject private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private final PipelineSettingsService pipelineSettingsService;
  @Inject private final EntitySetupUsageClient entitySetupUsageClient;
  @Inject private final PipelineAsyncValidationService pipelineAsyncValidationService;
  @Inject private final PipelineValidationService pipelineValidationService;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject GitXSettingsHelper gitXSettingsHelper;
  @Inject private final AccountClient accountClient;
  @Inject NGSettingsClient settingsClient;
  @Inject private final GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;
  @Inject private final PipelineRetentionService pipelineRetentionService;
  @Inject private final TemplateResourceClient templateResourceClient;
  @Inject private NgServiceResourceClient ngServiceResourceClient;
  @Inject private EnvironmentResourceClient environmentResourceClient;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PipelineEnforcementService pipelineEnforcementService;
  @Inject private PipelineOpaStatusHandler pipelineOpaStatusHandler;

  public static final String CREATING_PIPELINE = "creating new pipeline";
  public static final String UPDATING_PIPELINE = "updating existing pipeline";

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Pipeline [%s] under Project[%s], Organization [%s] already exists or has been deleted.";

  private static final int MAX_LIST_SIZE = 1000;
  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";

  public static final String DEFAULT = "__default__";
  private final String PIPELINE_NOT_FOUND_HINT =
      "Please check if there exist any pipeline with the file path [%s] in repo name [%s] and branch [%s] in Harness.";
  private final int PIPELINE_LIMIT_FOR_YAML_VALIDATION = 100;
  private static final String GOVERNANCE_DENY_EXPLANATION =
      "The pipeline was updated in Git, but it does not comply with the OPA governance policies configured in Harness.";
  private static final String GOVERNANCE_DENY_HINT =
      "Update the pipeline YAML to comply with the denying policy sets, or contact your account administrator to "
      + "review the governance rules.";

  @Override
  public PipelineCRUDResult validateAndCreatePipeline(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceFails) {
    return validateAndCreatePipeline(pipelineEntity, throwExceptionIfGovernanceFails, null, false);
  }

  @Override
  public PipelineCRUDResult validateAndCreatePipeline(PipelineEntity pipelineEntity,
      boolean throwExceptionIfGovernanceFails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    validatePipelineCreationLimitBreach(pipelineEntity.getAccountIdentifier());
    pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules("PIPELINE_CREATE", scopeInfo);
    try {
      if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
        log.info("Creating Draft Pipeline with identifier: {}", pipelineEntity.getIdentifier());
        return createPipeline(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
      }
      PMSPipelineServiceHelper.validatePresenceOfRequiredFields(pipelineEntity, isParentIdQueryingEnabled);

      String accountId = scopeInfo.getAccountIdentifier();
      String orgId = scopeInfo.getOrgIdentifier();
      String projectId = scopeInfo.getProjectIdentifier();
      if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DISABLE_PIPELINE_CHAINING_FOR_FREE_TIER)) {
        pipelineEnforcementService.validatePipelineChainingInYaml(
            accountId, pipelineEntity.getYaml(), orgId, projectId);
      }
      applyGitXSettingsIfApplicable(accountId, orgId, projectId);
      checkProjectExists(accountId, orgId, projectId);

      GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
          pipelineEntity, throwExceptionIfGovernanceFails, false, scopeInfo, isParentIdQueryingEnabled, false);
      try {
        if (governanceMetadata.getDeny()) {
          return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).build();
        }
        // TODO: As part of this ticket https://harness.atlassian.net/browse/CDS-70970, we should publish the setup
        // usages after the entity has been created
        PipelineEntity entityWithUpdatedInfo = pipelineEntity;
        // If PIE_ASYNC_FILTER_CREATION is ON, then we do filter creation async
        if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_ASYNC_FILTER_CREATION)) {
          entityWithUpdatedInfo = pmsPipelineServiceHelper.updatePipelineInfo(
              pipelineEntity, pipelineEntity.getHarnessVersion(), scopeInfo, isParentIdQueryingEnabled);
        }

        PipelineEntity createdEntity;
        PipelineCRUDResult pipelineCRUDResult =
            createPipeline(entityWithUpdatedInfo, scopeInfo, isParentIdQueryingEnabled);
        createdEntity = pipelineCRUDResult.getPipelineEntity();
        pipelineOpaStatusHandler.handleUiApiSave(createdEntity, accountId, governanceMetadata, null);
        computeReferencesIfRemotePipeline(createdEntity, scopeInfo);
        if (HarnessYamlVersion.isV1(createdEntity.getHarnessVersion())) {
          pipelineTemplateReferenceHelper.upsertTemplateReferencesForV1Pipeline(
              createdEntity, scopeInfo, isParentIdQueryingEnabled);
        }
        try {
          String branchInRequest = GitAwareContextHelper.getBranchInRequest();
          pipelineAsyncValidationService.createRecordForSuccessfulSyncValidation(createdEntity,
              GitAwareContextHelper.DEFAULT.equals(branchInRequest) ? "" : branchInRequest, governanceMetadata,
              Action.CRUD, isParentIdQueryingEnabled);
        } catch (Exception e) {
          log.error("Unable to save validation event for Pipeline: " + e.getMessage(), e);
        }
        return PipelineCRUDResult.builder()
            .governanceMetadata(governanceMetadata)
            .pipelineEntity(createdEntity)
            .build();
      } catch (IOException ex) {
        log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
        throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      }
    } catch (NGTemplateException ex) {
      throw new PipelineException(
          PipelineException.PIPELINE_CREATE_MESSAGE, ex, ErrorCode.NG_PIPELINE_CREATE_EXCEPTION);
    }
  }

  private PipelineCRUDResult createPipeline(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntity createdEntity;
    validatePipelineCreationLimitBreach(pipelineEntity.getAccountIdentifier());
    if (scopeInfo == null) {
      Optional<ScopeInfo> scopeInfoOptional = scopeResolutionHelper.getScopeInfoOptional(
          pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier());
      scopeInfoOptional.ifPresent(info -> pipelineEntity.setParentUniqueId(info.getUniqueId()));
    } else {
      pipelineEntity.setParentUniqueId(scopeInfo.getUniqueId());
    }

    try {
      if (isGitSyncEnabled(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
              pipelineEntity.getProjectIdentifier(), scopeInfo, isParentIdQueryingEnabled)) {
        createdEntity = pmsPipelineRepository.saveForOldGitSync(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
      } else {
        createdEntity = pmsPipelineRepository.save(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
      }
      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(
          createdEntity, CREATING_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(
          createdEntity, TEMPLATE_REF_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
      return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(createdEntity).build();
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(),
                                            scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);

    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating pipeline " + pipelineEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving pipeline [%s]", pipelineEntity.getIdentifier()), e);
      throw new InvalidRequestException(String.format(
          "Error while saving pipeline [%s]: %s", pipelineEntity.getIdentifier(), ExceptionUtils.getMessage(e)));
    }
  }
  @Override
  public PipelineSaveResponse validateAndClonePipeline(
      ClonePipelineDTO clonePipelineDTO, String accountId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    validatePipelineCreationLimitBreach(accountId);
    PipelineEntity sourcePipelineEntity =
        getSourcePipelineEntity(clonePipelineDTO, accountId, scopeInfo, isParentIdQueryingEnabled);

    String sourcePipelineEntityYaml = sourcePipelineEntity.getYaml();

    String destYaml;
    String pipelineName = null;
    String pipelineId = null;
    String sourcePipelineVersion = sourcePipelineEntity.getHarnessVersion();
    switch (sourcePipelineVersion) {
      case HarnessYamlVersion.V1:
        destYaml = pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(clonePipelineDTO, sourcePipelineEntityYaml);
        pipelineName = clonePipelineDTO.getDestinationConfig().getPipelineName();
        pipelineId = clonePipelineDTO.getDestinationConfig().getPipelineIdentifier();
        break;
      default:
        destYaml = pipelineCloneHelper.updatePipelineMetadataInSourceYaml(
            clonePipelineDTO, sourcePipelineEntityYaml, accountId);
    }

    Boolean requestEnableDag =
        clonePipelineDTO.getEnableDAG() != null ? clonePipelineDTO.getEnableDAG() : Boolean.FALSE;
    if (Boolean.TRUE.equals(requestEnableDag)) {
      if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)) {
        throw new InvalidRequestException(String.format(
            "DAG execution feature flag is not enabled for account: %s. Please contact Harness support to enable it.",
            accountId));
      }
      if (Boolean.TRUE.equals(sourcePipelineEntity.getEnableDAG())) {
        throw new InvalidRequestException("Source pipeline is already a DAG pipeline. Clone with enableDAG is only for "
            + "converting non-DAG pipelines to DAG format.");
      }
      destYaml = PipelineYamlUtils.convertSequentialPipelineToDAG(destYaml);
    }

    ScopeInfo destScopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, clonePipelineDTO.getDestinationConfig().getOrgIdentifier(),
            clonePipelineDTO.getDestinationConfig().getProjectIdentifier());

    PipelineEntity destPipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, clonePipelineDTO.getDestinationConfig().getOrgIdentifier(),
            clonePipelineDTO.getDestinationConfig().getProjectIdentifier(), pipelineId, pipelineName, destYaml, false,
            sourcePipelineVersion, destScopeInfo, isParentIdQueryingEnabled,
            sourcePipelineEntity.getAllowDynamicExecutions());

    final boolean destinationEnableDag;
    if (Boolean.TRUE.equals(requestEnableDag)) {
      destinationEnableDag = true;
    } else {
      destinationEnableDag = Boolean.TRUE.equals(sourcePipelineEntity.getEnableDAG());
    }
    destPipelineEntity = destPipelineEntity.withEnableDAG(destinationEnableDag);

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    // if the source pipeline is INLINE_HC, but the feature flag is disabled, create inline pipeline only
    if (gitEntityInfo.isNull()) {
      InlineHCHelper.checkAndUpdateContextForInlineHC(destPipelineEntity, CrudAction.CREATE,
          InlineHCUpdateContextRequest.builder()
              .scope(Scope.of(destScopeInfo))
              .entityIdentifier(destPipelineEntity.getIdentifier())
              .build(),
          pmsFeatureFlagHelper::isEnabled);
    }

    PipelineCRUDResult pipelineCRUDResult = validateAndCreatePipeline(destPipelineEntity,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE),
        destScopeInfo, isParentIdQueryingEnabled);
    GovernanceMetadata destGovernanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (destGovernanceMetadata.getDeny()) {
      return PipelineSaveResponse.builder().governanceMetadata(destGovernanceMetadata).build();
    }
    PipelineEntity clonedPipelineEntity = pipelineCRUDResult.getPipelineEntity();

    return PipelineSaveResponse.builder()
        .governanceMetadata(destGovernanceMetadata)
        .identifier(clonedPipelineEntity.getIdentifier())
        .build();
  }

  @NotNull
  private PipelineEntity getSourcePipelineEntity(
      ClonePipelineDTO clonePipelineDTO, String accountId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try (EntityGitDetailsGuard entityGitDetailsGuard =
             new EntityGitDetailsGuard(buildGitEntityInfo(accountId, clonePipelineDTO.getSourceConfig().getBranch()))) {
      Optional<PipelineEntity> sourcePipelineEntity =
          getPipeline(accountId, clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
              clonePipelineDTO.getSourceConfig().getProjectIdentifier(),
              clonePipelineDTO.getSourceConfig().getPipelineIdentifier(), false, false, false, false, scopeInfo,
              isParentIdQueryingEnabled);

      if (sourcePipelineEntity.isEmpty()) {
        log.error(String.format("Pipeline with id [%s] in org [%s] in project [%s] is not present or deleted",
            clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
            clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
            clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
        throw new InvalidRequestException(
            String.format("Pipeline with id [%s] in org [%s] in project [%s] is not present or deleted",
                clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
                clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
                clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
      }
      return sourcePipelineEntity.get();
    }
  }

  @Override
  public PipelineGetResult getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineId, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, boolean validateAsync, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled,
      boolean shouldIgnoreOpaOnSaveCheck) {
    Optional<PipelineEntity> pipelineEntity;
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID = null;
    if (validateAsync) {
      PipelineGetResult pipelineEventPair = getPipelineAndAsyncValidationId(accountId, orgIdentifier, projectIdentifier,
          pipelineId, loadFromFallbackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      pipelineEntity = pipelineEventPair.getPipelineEntity();
      validationUUID = pipelineEventPair.getAsyncValidationUUID();
    } else {
      pipelineEntity = getAndValidatePipeline(accountId, orgIdentifier, projectIdentifier, pipelineId, false,
          loadFromFallbackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled, shouldIgnoreOpaOnSaveCheck);
    }
    if (pipelineEntity.isPresent()
        && PipelineGitXHelper.shouldPublishSetupUsages(pipelineEntity.get().getStoreType(), loadFromCache)) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity.get(), scopeInfo);
    }
    return PipelineGetResult.builder().pipelineEntity(pipelineEntity).asyncValidationUUID(validationUUID).build();
  }

  @Override
  public AllowedValuesUsagesDTO checkForAllowedValues(String accountId, AllowedValuesUsagesRequestDTO request) {
    Pattern allowedValuesPattern = Pattern.compile(EXPR_START_ESC + "input" + EXPR_END_ESC + ".*"
        + ".allowedValues\\(.*\\)");

    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    Criteria criteria = pmsPipelineServiceHelper.formCriteria(accountId, null, null, null,
        PipelineFilterPropertiesDto.builder().build(), false, null, null, scopeInfo, isParentIdQueryingEnabled);
    List<EntityListWithAllowedValuesResponse> pipelineListWithAllowedValuesList = new ArrayList<>();
    boolean allEntitiesChecked = false;
    // Reading by 100 pipelines at once so that a large number of pipelines do not get loaded in the memory.
    Page<PipelineEntity> pipelineEntities;
    for (int pageNumber = 0; pageNumber < request.getMaxPages(); pageNumber++) {
      pipelineEntities = list(criteria, PageRequest.of(pageNumber, request.getPageSize(), Sort.by("uuid")), accountId,
          null, null, false, scopeInfo, isParentIdQueryingEnabled);
      for (PipelineEntity pipeline : pipelineEntities) {
        String yaml = pipeline.getYaml();
        if (EmptyPredicate.isNotEmpty(yaml) && allowedValuesPattern.matcher(yaml).find()) {
          pipelineListWithAllowedValuesList.add(EntityListWithAllowedValuesResponse.builder()
                                                    .accountId(pipeline.getAccountId())
                                                    .orgIdentifier(pipeline.getOrgIdentifier())
                                                    .projectIdentifier(pipeline.getProjectIdentifier())
                                                    .identifier(pipeline.getIdentifier())
                                                    .entityType(YAMLFieldNameConstants.PIPELINE)
                                                    .gitDetails(EntityListWithAllowedValuesResponse.GitDetails.builder()
                                                                    .branch(pipeline.getBranch())
                                                                    .repoUrl(pipeline.getRepoURL())
                                                                    .filePath(pipeline.getFilePath())
                                                                    .build())
                                                    .build());
        }
      }
      if (pageNumber >= pipelineEntities.getTotalPages() - 1) {
        allEntitiesChecked = true;
        break;
      }
    }

    // fetching template and other entities data
    return AllowedValuesUsagesDTO.builder()
        .pipelines(AllowedValuesUsagesInternalDTO.builder()
                       .usedIn(pipelineListWithAllowedValuesList)
                       .allEntriesChecked(allEntitiesChecked)
                       .build())
        .templates(NGRestUtils.getResponse(templateResourceClient.checkForAllowedValue(accountId, request)))
        .services(NGRestUtils.getResponse(ngServiceResourceClient.checkAllowedValues(accountId, request)))
        .environments(NGRestUtils.getResponse(environmentResourceClient.checkAllowedValues(accountId, request)))
        .build();
  }

  @Override
  public String validatePipeline(String accountId, String orgIdentifier, String projectIdentifier, String pipelineId,
      boolean loadFromFallbackBranch, boolean loadFromCache, boolean validateAsync, PipelineEntity pipelineEntity,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID = null;
    if (validateAsync) {
      validationUUID = getAsyncValidationIdAndValidatePipeline(accountId, orgIdentifier, projectIdentifier,
          loadFromCache, pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
    } else {
      validatePipelineSync(orgIdentifier, projectIdentifier, pipelineId, loadFromCache, pipelineEntity, false,
          scopeInfo, isParentIdQueryingEnabled);
    }
    if (PipelineGitXHelper.shouldPublishSetupUsages(pipelineEntity.getStoreType(), loadFromCache)) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, scopeInfo);
    }
    return validationUUID;
  }

  @Override
  public Optional<PipelineEntity> getAndValidatePipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String identifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled,
      boolean shouldIgnoreOpaOnSaveCheck) {
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(accountId, orgIdentifier, projectIdentifier,
        identifier, deleted, false, loadFromFallbackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    validatePipelineSync(orgIdentifier, projectIdentifier, identifier, loadFromCache, pipelineEntity,
        shouldIgnoreOpaOnSaveCheck, scopeInfo, isParentIdQueryingEnabled);
    return optionalPipelineEntity;
  }

  void validatePipelineSync(String orgIdentifier, String projectIdentifier, String identifier, boolean loadFromCache,
      PipelineEntity pipelineEntity, boolean shouldIgnoreOpaOnSaveCheck, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    if (pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE) {
      // This is added to add validation for stored invalid yaml (duplicate yaml fields)
      validateStoredYaml(pipelineEntity);
    } else {
      if (EmptyPredicate.isEmpty(pipelineEntity.getData())) {
        String errorMessage = PipelineCRUDErrorResponse.errorMessageForEmptyYamlOnGit(
            orgIdentifier, projectIdentifier, identifier, GitAwareContextHelper.getBranchInRequest());
        YamlSchemaErrorWrapperDTO errorWrapperDTO =
            YamlSchemaErrorWrapperDTO.builder()
                .schemaErrors(Collections.singletonList(
                    YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.pipeline").build()))
                .build();
        throw new io.harness.yaml.validator.InvalidYamlException(
            errorMessage, errorWrapperDTO, pipelineEntity.getData());
      }
      pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineEntity(
          pipelineEntity, loadFromCache, shouldIgnoreOpaOnSaveCheck, scopeInfo, isParentIdQueryingEnabled);
    }
  }

  // This function validate the duplicate fields in yaml and throws error if any. This method will be called during get
  // call of inline Pipeline
  public void validateStoredYaml(PipelineEntity pipelineEntity) {
    try {
      YamlUtils.readTree(pipelineEntity.getYaml());
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder().message(ex.getMessage()).build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  @Override
  public Optional<PipelineEntity> getPipelineByUUID(String uuid) {
    return pmsPipelineRepository.find(uuid);
  }

  @Override
  public Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity;
    long start = System.currentTimeMillis();
    try {
      if (isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier, scopeInfo, isParentIdQueryingEnabled)) {
        optionalPipelineEntity = pmsPipelineRepository.findForOldGitSync(accountId, scopeInfo, identifier, !deleted);
      } else {
        // TODO: currently we are setting up the same in PipelineStageFilterCreator. Check if can call this only once
        PipelineGitXHelper.setupGitParentEntityDetails(accountId, orgIdentifier, projectIdentifier, null, null);
        optionalPipelineEntity = pmsPipelineRepository.find(accountId, orgIdentifier, projectIdentifier, identifier,
            !deleted, getMetadataOnly, loadFromFallbackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      }
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw new InvalidRequestException(
          String.format("Error while retrieving pipeline [%s]: %s", identifier, ExceptionUtils.getMessage(e)));
    } finally {
      log.info("[PMS_PipelineService] get Pipeline took {}ms for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectIdentifier, orgIdentifier, accountId);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    return optionalPipelineEntity;
  }

  @Override
  public void refreshGitFileCache(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String branch, ScopeInfo scopeInfo) {
    if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_GITX_FORCE_REFRESH)) {
      throw new UnavailableFeatureException(
          String.format("Cache refresh for pipeline [%s] is not enabled for this account. Contact Harness support to "
                  + "enable feature flag %s.",
              pipelineIdentifier, FeatureName.PIPE_GITX_FORCE_REFRESH.name()));
    }
    if (EmptyPredicate.isEmpty(branch) || GitAwareContextHelper.DEFAULT.equals(branch)) {
      throw new InvalidRequestException(
          String.format("A valid git branch is required to refresh cache for pipeline [%s].", pipelineIdentifier));
    }
    log.info(String.format("Refresh cache for pipeline with identifier %s in project %s, org %s, account %s, branch %s",
        pipelineIdentifier, projectIdentifier, orgIdentifier, accountId, branch));

    ScopeInfo effectiveScopeInfo =
        scopeInfo != null ? scopeInfo : scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    GitAwareContextHelper.updateGitEntityContextWithBranch(branch);

    PipelineEntity pipelineEntity =
        getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, true, false, true,
            effectiveScopeInfo, true)
            .orElseThrow(
                ()
                    -> new EntityNotFoundException(String.format(
                        "Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier)));

    if (!StoreType.REMOTE.equals(pipelineEntity.getStoreType())) {
      throw new InvalidRequestException(String.format(
          "Cache refresh for pipeline [%s] applies only to remote Git-backed pipelines.", pipelineIdentifier));
    }

    Scope scope = Scope.of(effectiveScopeInfo);
    ScmClearCacheResponse clearCacheResponse =
        gitAwareEntityHelper.clearCache(pipelineEntity, scope, branch, EntityType.PIPELINES);
    if (clearCacheResponse == null || !clearCacheResponse.isStatus()) {
      List<String> failedFilePaths =
          clearCacheResponse == null ? Collections.emptyList() : clearCacheResponse.getFailedFilePaths();
      String scmError = clearCacheResponse == null ? null : clearCacheResponse.getErrorMessage();
      log.error(String.format("Git cache clear failed for pipeline [%s] in account %s, org %s, project %s. Failed "
              + "paths: %s. SCM error: %s.",
          pipelineIdentifier, accountId, orgIdentifier, projectIdentifier, failedFilePaths, scmError));
      throw new InternalServerErrorException(String.format(
          "Failed to refresh git file cache for pipeline [%s] on branch [%s]. Failed paths: %s.%s", pipelineIdentifier,
          branch, failedFilePaths, EmptyPredicate.isEmpty(scmError) ? "" : " SCM error: " + scmError));
    }
  }

  @Override
  public PipelineEntity getPipelineMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> pipelineEntityOnlyMetadata = getPipeline(accountId, orgIdentifier, projectIdentifier,
        identifier, deleted, getMetadataOnly, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntityOnlyMetadata.isEmpty()) {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    return pipelineEntityOnlyMetadata.get();
  }

  PipelineGetResult getPipelineAndAsyncValidationId(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean loadFromFallbackBranch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(accountId, orgIdentifier, projectIdentifier,
        identifier, false, false, loadFromFallbackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted.", identifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String validationUUID = getAsyncValidationIdAndValidatePipeline(accountId, orgIdentifier, projectIdentifier,
        loadFromCache, pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
    return PipelineGetResult.builder()
        .pipelineEntity(optionalPipelineEntity)
        .asyncValidationUUID(validationUUID)
        .build();
  }

  String getAsyncValidationIdAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      boolean loadFromCache, PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // org, project ids are only used for logging, validation happens with the yaml
    pipelineValidationService.validateYamlWithUnresolvedTemplates(
        accountId, orgIdentifier, projectIdentifier, pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    // if the branch in the request is null, then the branch from where the remote pipeline is taken from is set
    // inside the scm git metadata. Hence, the branch from there is the actual branch we need
    String branchFromScm = GitAwareContextHelper.getBranchInSCMGitMetadata();
    return getValidationUuid(pipelineEntity, loadFromCache, branchFromScm, scopeInfo, isParentIdQueryingEnabled);
  }

  String getValidationUuid(PipelineEntity pipelineEntity, boolean loadFromCache, String branchFromScm,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String validationUUID;
    if (!loadFromCache && pipelineEntity.getStoreType() == StoreType.REMOTE) {
      // loadFromCache = false means user is reloading from Git. In this case, the validation data being shown can't be
      // for an older yaml as user expects everything to be refreshed. That's why it makes sense to have a fresh
      // validation process in this case
      PipelineValidationEvent newEvent = pipelineAsyncValidationService.startEvent(
          pipelineEntity, branchFromScm, Action.CRUD, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      validationUUID = newEvent.getUuid();
    } else {
      String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, branchFromScm, isParentIdQueryingEnabled);
      Optional<PipelineValidationEvent> optionalEvent =
          pipelineAsyncValidationService.getLatestEventByFQNAndAction(fqn, Action.CRUD);
      if (optionalEvent.isPresent()) {
        validationUUID = optionalEvent.get().getUuid();
      } else {
        PipelineValidationEvent newEvent = pipelineAsyncValidationService.startEvent(
            pipelineEntity, branchFromScm, Action.CRUD, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
        validationUUID = newEvent.getUuid();
      }
    }
    return validationUUID;
  }

  public PipelineCRUDResult validateAndUpdatePipeline(PipelineEntity pipelineEntity, ChangeType changeType,
      boolean throwExceptionIfGovernanceFails, boolean isPatch, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    try {
      if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
        log.info("Updating Draft Pipeline with identifier: {}", pipelineEntity.getIdentifier());
        PipelineEntity updatedEntity =
            updatePipelineWithoutValidation(pipelineEntity, changeType, isPatch, scopeInfo, isParentIdQueryingEnabled);
        GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
        return PipelineCRUDResult.builder()
            .governanceMetadata(governanceMetadata)
            .pipelineEntity(updatedEntity)
            .build();
      }
      PMSPipelineServiceHelper.validatePresenceOfRequiredFields(pipelineEntity, isParentIdQueryingEnabled);

      // V1 pipeline metadata (name/description/tags) lives outside the YAML, so V1 PATCH flows
      // can legitimately omit `pipeline_yaml` from the request body. In that case there is no
      // chaining to (re)validate; skip the chaining check. V0 always carries metadata in the
      // YAML, so V0 behavior is unchanged.
      boolean isV1MetadataOnlyUpdate =
          HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion()) && isEmpty(pipelineEntity.getYaml());
      if (!isV1MetadataOnlyUpdate
          && pmsFeatureFlagHelper.isEnabled(
              pipelineEntity.getAccountIdentifier(), FeatureName.PIPE_DISABLE_PIPELINE_CHAINING_FOR_FREE_TIER)) {
        String orgId = scopeInfo.getOrgIdentifier();
        String projectId = scopeInfo.getProjectIdentifier();
        pipelineEnforcementService.validatePipelineChainingInYaml(
            pipelineEntity.getAccountIdentifier(), pipelineEntity.getYaml(), orgId, projectId);
      }

      GovernanceMetadata governanceMetadata;
      if (isPatch && isEmpty(pipelineEntity.getYaml())) {
        governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
      } else {
        governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
            pipelineEntity, throwExceptionIfGovernanceFails, false, scopeInfo, isParentIdQueryingEnabled, false);
      }
      if (governanceMetadata.getDeny()) {
        return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).build();
      }
      PipelineEntity updatedEntity =
          updatePipelineWithoutValidation(pipelineEntity, changeType, isPatch, scopeInfo, isParentIdQueryingEnabled);
      pipelineOpaStatusHandler.handleUiApiSave(
          updatedEntity, pipelineEntity.getAccountIdentifier(), governanceMetadata, null);
      computeReferencesIfRemotePipeline(updatedEntity, scopeInfo);
      if (HarnessYamlVersion.isV1(updatedEntity.getHarnessVersion())) {
        pipelineTemplateReferenceHelper.upsertTemplateReferencesForV1Pipeline(
            updatedEntity, scopeInfo, isParentIdQueryingEnabled);
      }
      try {
        String branchInRequest = GitAwareContextHelper.getBranchInRequest();
        pipelineAsyncValidationService.createRecordForSuccessfulSyncValidation(updatedEntity,
            GitAwareContextHelper.DEFAULT.equals(branchInRequest) ? "" : branchInRequest, governanceMetadata,
            Action.CRUD, isParentIdQueryingEnabled);
      } catch (Exception e) {
        log.error("Unable to save validation event for Pipeline: " + e.getMessage(), e);
      }
      return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(updatedEntity).build();
    } catch (NGTemplateException ex) {
      throw new PipelineException(PipelineException.PIPELINE_UPDATE_MESSAGE, ex, ErrorCode.PIPELINE_UPDATE_EXCEPTION);
    }
  }

  private PipelineEntity updatePipelineWithoutValidation(PipelineEntity pipelineEntity, ChangeType changeType,
      boolean isPatch, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntity updatedEntity;
    if (isGitSyncEnabled(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), scopeInfo, isParentIdQueryingEnabled)) {
      updatedEntity = updatePipelineForOldGitSync(pipelineEntity, changeType, scopeInfo, isParentIdQueryingEnabled);
    } else {
      updatedEntity = makePipelineUpdateCall(
          pipelineEntity, null, changeType, false, isPatch, scopeInfo, isParentIdQueryingEnabled);
    }
    return updatedEntity;
  }

  private PipelineEntity updatePipelineForOldGitSync(
      PipelineEntity pipelineEntity, ChangeType changeType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (GitContextHelper.getGitEntityInfo() != null && GitContextHelper.getGitEntityInfo().isNewBranch()) {
      // sending old entity as null here because a new mongo entity will be created. If audit trail needs to be added
      // to git synced projects, a get call needs to be added here to the base branch of this pipeline update
      return makePipelineUpdateCall(pipelineEntity, null, changeType, true, scopeInfo, isParentIdQueryingEnabled);
    }
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Optional<PipelineEntity> optionalOriginalEntity = pmsPipelineRepository.findForOldGitSync(
        pipelineEntity.getAccountId(), scopeInfo, pipelineEntity.getIdentifier(), true);
    if (optionalOriginalEntity.isEmpty()) {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgId, projectId, pipelineEntity.getIdentifier()));
    }
    PipelineEntity entityToUpdate = optionalOriginalEntity.get();
    PipelineEntity tempEntity = entityToUpdate.withYaml(pipelineEntity.getYaml())
                                    .withName(pipelineEntity.getName())
                                    .withDescription(pipelineEntity.getDescription())
                                    .withTags(pipelineEntity.getTags())
                                    .withIsEntityInvalid(false)
                                    .withAllowStageExecutions(pipelineEntity.getAllowStageExecutions());

    return makePipelineUpdateCall(tempEntity, entityToUpdate, changeType, true, scopeInfo, isParentIdQueryingEnabled);
  }

  @Override
  public PipelineEntity syncPipelineEntityWithGit(EntityDetailProtoDTO entityDetail) {
    IdentifierRefProtoDTO identifierRef = entityDetail.getIdentifierRef();
    String accountId = StringValueUtils.getStringFromStringValue(identifierRef.getAccountIdentifier());
    String orgId = StringValueUtils.getStringFromStringValue(identifierRef.getOrgIdentifier());
    String projectId = StringValueUtils.getStringFromStringValue(identifierRef.getProjectIdentifier());
    String pipelineId = StringValueUtils.getStringFromStringValue(identifierRef.getIdentifier());

    Optional<PipelineEntity> optionalPipelineEntity;
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(null, false)) {
      // Get and validate pipeline only for old git exp full sync
      optionalPipelineEntity =
          getAndValidatePipeline(accountId, orgId, projectId, pipelineId, false, false, false, null, false, false);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgId, projectId, pipelineId));
    }
    // Non Git synced Pipelines are being marked as INLINE. Marking storeType as null here so that pipelines in old git
    // sync don't have any value for storeType.
    return makePipelineUpdateCall(optionalPipelineEntity.get().withStoreType(null), optionalPipelineEntity.get(),
        ChangeType.ADD, true, null, false);
  }

  private PipelineEntity makePipelineUpdateCall(PipelineEntity pipelineEntity, PipelineEntity oldEntity,
      ChangeType changeType, boolean isOldFlow, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return makePipelineUpdateCall(
        pipelineEntity, oldEntity, changeType, isOldFlow, false, scopeInfo, isParentIdQueryingEnabled);
  }

  private PipelineEntity makePipelineUpdateCall(PipelineEntity pipelineEntity, PipelineEntity oldEntity,
      ChangeType changeType, boolean isOldFlow, boolean isPatch, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    try {
      PipelineEntity entityWithUpdatedInfo = pipelineEntity;
      // If PIE_ASYNC_FILTER_CREATION is ON, then we do filter creation async
      if (!pmsFeatureFlagHelper.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIE_ASYNC_FILTER_CREATION)) {
        entityWithUpdatedInfo = pmsPipelineServiceHelper.updatePipelineInfo(
            pipelineEntity, pipelineEntity.getHarnessVersion(), scopeInfo, isParentIdQueryingEnabled);
      }

      PipelineEntity updatedResult;
      if (isOldFlow) {
        updatedResult = pmsPipelineRepository.updatePipelineYamlForOldGitSync(
            entityWithUpdatedInfo, oldEntity, changeType, isParentIdQueryingEnabled);
      } else {
        updatedResult = pmsPipelineRepository.updatePipelineYaml(
            entityWithUpdatedInfo, isPatch, scopeInfo, isParentIdQueryingEnabled);
      }

      String orgId = scopeInfo.getOrgIdentifier();
      String projectId = scopeInfo.getProjectIdentifier();
      if (updatedResult == null) {
        throw new InvalidRequestException(
            format("Pipeline [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                pipelineEntity.getIdentifier(), projectId, orgId));
      }

      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(
          updatedResult, UPDATING_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(
          updatedResult, TEMPLATE_REF_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      return updatedResult;
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);
    } catch (IOException ex) {
      log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while updating pipeline " + pipelineEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating pipeline [%s]", pipelineEntity.getIdentifier()), e);
      throw new InvalidRequestException(String.format(
          "Error while updating pipeline [%s]: %s", pipelineEntity.getIdentifier(), ExceptionUtils.getMessage(e)));
    }
  }

  @Override
  public PipelineEntity updatePipelineMetadata(
      ScopeInfo scopeInfo, Criteria criteria, Update updateOperations, boolean isParentIdQueryingEnabled) {
    return pmsPipelineRepository.updatePipelineMetadata(scopeInfo, criteria, updateOperations);
  }

  @Override
  public void saveExecutionInfo(ScopeInfo scopeInfo, String pipelineId, ExecutionSummaryInfo executionSummaryInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), pipelineId, false, null);

    Update update = new Update();
    update.set(PipelineEntityKeys.executionSummaryInfo, executionSummaryInfo);
    updatePipelineMetadata(scopeInfo, criteria, update, isParentIdQueryingEnabled);
  }

  @Override
  public boolean markEntityInvalid(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String invalidYaml) {
    // Not passing the scopeInfo as this is old git sync flow
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, false, false, false, false, null, false);
    if (optionalPipelineEntity.isEmpty()) {
      log.warn(String.format(
          "Marking pipeline [%s] as invalid failed as it does not exist or has been deleted", identifier));
      return false;
    }
    PipelineEntity existingPipeline = optionalPipelineEntity.get();
    PipelineEntity pipelineEntityUpdated = existingPipeline.withYaml(invalidYaml)
                                               .withObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml))
                                               .withIsEntityInvalid(true);
    pmsPipelineRepository.updatePipelineYamlForOldGitSync(
        pipelineEntityUpdated, existingPipeline, ChangeType.NONE, false);
    return true;
  }

  private boolean isForceDeleteEnabled(String accountIdentifier) {
    try {
      return isForceDeleteFFEnabledViaSettings(accountIdentifier);
    } catch (Exception e) {
      log.error("Failed to fetch feature flag info for force delete ", e);
      return false;
    }
  }

  @VisibleForTesting
  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      Long version, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (!isForceDeleteEnabled(accountId)) {
      validateSetupUsage(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return deleteForOldGitSync(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);
    }
    try {
      PipelineEntity pipelineMetadata = getPipelineMetadata(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, false, true, scopeInfo, isParentIdQueryingEnabled);
      Scope pipelineScope = Scope.of(scopeInfo);
      if (StoreType.INLINE_HC.equals(pipelineMetadata.getStoreType())) {
        InlineHCHelper.updateGitContext(CrudAction.DELETE, io.harness.yaml.utils.EntityType.PIPELINE,
            InlineHCUpdateContextRequest.builder()
                .scope(pipelineScope)
                .entityIdentifier(pipelineMetadata.getIdentifier())
                .build());
        try {
          gitAwareEntityHelper.deleteEntityOnGit(pipelineMetadata, pipelineScope);
        } catch (HintException exception) {
          log.error("Error deleting yaml file for pipeline. Skipping delete operation.", exception);
          if (!checkIfFileNotPresentError(exception)) {
            throw exception;
          }
        }
      }

      pmsPipelineRepository.delete(scopeInfo, pipelineIdentifier);

      // Clean up template references (non-blocking)
      if (HarnessYamlVersion.isV1(pipelineMetadata.getHarnessVersion())) {
        pipelineTemplateReferenceHelper.deleteTemplateReferencesForPipeline(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineMetadata);
      }

      return true;
    } catch (Exception e) {
      log.error(format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted: %s",
                    pipelineIdentifier, projectIdentifier, orgIdentifier, ExceptionUtils.getMessage(e)),
          e);
      throw new InvalidRequestException(
          format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.", pipelineIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  public void validateSetupUsage(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(pipelineIdentifier)
                                      .build();
    Boolean isEntityReferenced;
    try {
      isEntityReferenced = NGRestUtils.getResponse(entitySetupUsageClient.isEntityReferenced(
          accountId, identifierRef.getFullyQualifiedName(), EntityType.PIPELINES));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          pipelineIdentifier, ex);
      throw new UnexpectedException("Error while deleting the Pipeline");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(
          String.format("Could not delete the pipeline %s as it is referenced by other entities", pipelineIdentifier));
    }
  }

  private boolean deleteForOldGitSync(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineRepository.findForOldGitSync(accountId, scopeInfo, pipelineIdentifier, true);
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }

    PipelineEntity existingEntity = optionalPipelineEntity.get();
    PipelineEntity withDeleted = existingEntity.withDeleted(true);
    try {
      pmsPipelineRepository.deleteForOldGitSync(withDeleted, scopeInfo, isParentIdQueryingEnabled);

      // Clean up template references (non-blocking)
      if (HarnessYamlVersion.isV1(existingEntity.getHarnessVersion())) {
        String accountIdForCleanup = scopeInfo.getAccountIdentifier();
        String orgIdForCleanup = scopeInfo.getOrgIdentifier();
        String projectIdForCleanup = scopeInfo.getProjectIdentifier();
        pipelineTemplateReferenceHelper.deleteTemplateReferencesForPipeline(
            accountIdForCleanup, orgIdForCleanup, projectIdForCleanup, pipelineIdentifier, existingEntity);
      }

      return true;
    } catch (Exception e) {
      log.error(String.format("Error while deleting pipeline [%s]", pipelineIdentifier), e);

      throw new InvalidRequestException(
          format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.", pipelineIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public Page<PipelineEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    checkProjectExists(accountId, orgIdentifier, projectIdentifier);
    if (Boolean.TRUE.equals(getDistinctFromBranches)
        && gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return fetchAllPipelines(
          criteria, pageable, accountId, orgIdentifier, projectIdentifier, true, scopeInfo, isParentIdQueryingEnabled);
    }
    return fetchAllPipelines(
        criteria, pageable, accountId, orgIdentifier, projectIdentifier, false, scopeInfo, isParentIdQueryingEnabled);
  }

  private Page<PipelineEntity> fetchAllPipelines(Criteria criteria, Pageable pageable, String accountId,
      String orgIdentifier, String projectIdentifier, Boolean getDistinctFromBranches, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    return pmsPipelineRepository.findAll(criteria, pageable, accountId, scopeInfo, getDistinctFromBranches);
  }

  @Override
  public PipelineEntity importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, Boolean isForceImport,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    validatePipelineCreationLimitBreach(accountId);
    GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
    applyGitXSettingsIfApplicable(accountId, orgIdentifier, projectIdentifier);
    String repoUrl = pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, isForceImport, scopeInfo, isParentIdQueryingEnabled);
    String importedPipelineYAML =
        pmsPipelineServiceHelper.importPipelineFromRemote(accountId, orgIdentifier, projectIdentifier, true, scopeInfo);
    if (EmptyPredicate.isEmpty(importedPipelineYAML)) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForEmptyYamlOnGit(
          orgIdentifier, projectIdentifier, pipelineIdentifier, GitAwareContextHelper.getBranchInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipelineYAML);
    }
    String pipelineVersion = pipelineImportRequest.getVersion();
    if (!HarnessYamlVersion.isV1(pipelineVersion)) {
      YamlField pipelineRootYamlField = YamlUtils.readYamlTree(importedPipelineYAML).getNode().getField("pipeline");
      if (pipelineRootYamlField == null) {
        String errorMessage = PipelineCRUDErrorResponse.errorMessageForInvalidYamlOnGit(
            orgIdentifier, projectIdentifier, pipelineIdentifier, GitAwareContextHelper.getBranchInRequest());
        throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipelineYAML);
      }
      if (EmptyPredicate.isEmpty(pipelineIdentifier)
          || (!EmptyPredicate.isEmpty(pipelineIdentifier)
              && pipelineIdentifier.equals(pipelineRootYamlField.getNode().getIdentifier()))) {
        pipelineIdentifier = pipelineRootYamlField.getNode().getIdentifier();
      }
      if (EmptyPredicate.isEmpty(pipelineImportRequest.getPipelineName())) {
        pipelineImportRequest = PipelineImportRequestDTO.builder()
                                    .pipelineName(pipelineRootYamlField.getNode().getName())
                                    .pipelineDescription(pipelineImportRequest.getPipelineDescription())
                                    .build();
      }
    }
    PMSPipelineServiceHelper.checkAndThrowMismatchInImportedPipelineMetadata(orgIdentifier, projectIdentifier,
        pipelineIdentifier, pipelineImportRequest, importedPipelineYAML, pipelineVersion);
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, pipelineImportRequest.getPipelineName(), importedPipelineYAML, false, pipelineVersion,
        scopeInfo, isParentIdQueryingEnabled, null);
    pipelineEntity.setRepoURL(repoUrl);
    pipelineEntity.setStoreType(StoreType.REMOTE);
    try {
      PipelineEntity entityWithUpdatedInfo =
          pmsPipelineServiceHelper.updatePipelineInfo(pipelineEntity, pipelineVersion);
      if (isEmpty(pipelineEntity.getParentUniqueId())) {
        Optional<ScopeInfo> scopeInfoOptional =
            scopeResolutionHelper.getScopeInfoOptional(accountId, orgIdentifier, projectIdentifier);
        scopeInfoOptional.ifPresent(info -> entityWithUpdatedInfo.setParentUniqueId(info.getUniqueId()));
      }
      PipelineEntity savedPipelineEntity = pmsPipelineRepository.savePipelineEntityForImportedYAML(
          entityWithUpdatedInfo, scopeInfo, isParentIdQueryingEnabled);
      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(
          savedPipelineEntity, CREATING_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(
          savedPipelineEntity, TEMPLATE_REF_PIPELINE, scopeInfo, isParentIdQueryingEnabled);
      return savedPipelineEntity;
    } catch (DuplicateKeyException ex) {
      log.error(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(),
                    pipelineEntity.getOrgIdentifier()),
          ex);
      throw new DuplicateFieldException(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(),
                                            pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);
    } catch (IOException ex) {
      log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public Long countAllPipelines(Criteria criteria) {
    return pmsPipelineRepository.countAllPipelines(criteria);
  }

  @Override
  public StepCategory getStepsV2(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper) {
    return getStepsWithVersion(accountId, stepPalleteFilterWrapper, HarnessYamlVersion.V0);
  }

  @Override
  public StepCategory getStepsWithVersion(
      String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper, String version) {
    Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps =
        pmsSdkInstanceService.getModuleNameToStepPalleteInfo();
    if (stepPalleteFilterWrapper.getStepPalleteModuleInfos().isEmpty()) {
      // Return all the steps.
      return pmsPipelineServiceStepHelper.getAllSteps(accountId, serviceInstanceNameToSupportedSteps, version);
    }
    StepCategory stepCategory = StepCategory.builder().name(LIBRARY).build();
    for (StepPalleteModuleInfo request : stepPalleteFilterWrapper.getStepPalleteModuleInfos()) {
      String module = request.getModule();
      String category = request.getCategory();

      StepPalleteInfo stepPalleteInfo = serviceInstanceNameToSupportedSteps.get(module);
      if (stepPalleteInfo == null) {
        continue;
      }
      List<StepInfo> stepInfoList = stepPalleteInfo.getStepTypes();
      String displayModuleName = stepPalleteInfo.getModuleName();
      if (EmptyPredicate.isEmpty(stepInfoList)) {
        continue;
      }
      StepCategory moduleCategory;
      if (EmptyPredicate.isNotEmpty(category)) {
        moduleCategory = pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategoryV2(
            displayModuleName, category, stepInfoList, accountId, version);
      } else {
        moduleCategory =
            pmsPipelineServiceStepHelper.calculateStepsForCategory(displayModuleName, stepInfoList, accountId, version);
      }
      stepCategory.addStepCategory(moduleCategory);
      if (request.isShouldShowCommonSteps()) {
        pmsPipelineServiceStepHelper.addStepsToStepCategory(
            moduleCategory, CommonStepInfo.getCommonSteps(request.getCommonStepCategory()), accountId, version);
      }
    }

    return stepCategory;
  }

  // Todo: Remove only if there are no references to the pipeline
  @Override
  public boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId, ScopeInfo scopeInfo) {
    boolean isOldGitSyncEnabled = gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId);
    boolean isParentIdQueryingEnabled = true;
    if (isOldGitSyncEnabled) {
      Criteria criteria = PMSPipelineFilterHelper.getCriteriaForAllPipelinesInProject(accountId, orgId, projectId);
      Pageable pageRequest = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));

      Page<PipelineEntity> pipelineEntities =
          pmsPipelineRepository.findAll(criteria, pageRequest, accountId, orgId, projectId, false);
      for (PipelineEntity pipelineEntity : pipelineEntities) {
        pmsPipelineRepository.deleteForOldGitSync(pipelineEntity.withDeleted(true));
      }
      return true;
    }
    return pmsPipelineRepository.deleteAllPipelinesInAProject(
        accountId, orgId, projectId, scopeInfo, isParentIdQueryingEnabled);
  }

  @Override
  public String fetchExpandedPipelineJSON(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> pipelineEntityOptional = getPipeline(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntityOptional.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }

    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
      return null;
    }

    String branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();
    return pipelineGovernanceService.getExpandedPipelineJSONFromYaml(accountId, orgIdentifier, projectIdentifier,
        pipelineEntityOptional.get().getYaml(), branch, pipelineEntityOptional.get(), scopeInfo,
        isParentIdQueryingEnabled);
  }

  @Override
  public PipelineEntity updateGitFilePath(PipelineEntity pipelineEntity, String newFilePath) {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(pipelineEntity.getAccountId())
                              .orgIdentifier(pipelineEntity.getOrgIdentifier())
                              .projectIdentifier(pipelineEntity.getProjectIdentifier())
                              .uniqueId(pipelineEntity.getParentUniqueId())
                              .build();
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(pipelineEntity.getAccountId(),
        pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), false,
        null);

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(PipelineEntityKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(PipelineEntityKeys.rootFolder, gitEntityFilePath.getRootFolder())
                        .set(PipelineEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return updatePipelineMetadata(scopeInfo, criteria, update, false);
  }

  @Override
  public String pipelineVersion(String accountId, String yaml) {
    boolean isYamlSimplificationEnabled = pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CI_YAML_VERSIONING);
    return NGYamlHelper.getVersion(yaml, isYamlSimplificationEnabled);
  }

  @Override
  public PMSPipelineListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = PMSPipelineServiceHelper.buildCriteriaForRepoListing(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, isParentIdQueryingEnabled);
    List<String> uniqueRepos = pmsPipelineRepository.findAllUniqueRepos(criteria);
    CollectionUtils.filter(uniqueRepos, PredicateUtils.notNullPredicate());
    if (uniqueRepos.size() > MAX_LIST_SIZE) {
      log.error(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return PMSPipelineListRepoResponse.builder().repositories(uniqueRepos).build();
  }

  @Override
  public PMSPipelineRemoteRepoListResponse getRemoteRepoListForAGivenScope(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("accountIdentifier is required");
    }
    PMSPipelineRemoteRepoPage paged = pmsPipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, limit);
    return PMSPipelineRemoteRepoListResponse.builder()
        .repositories(paged.getRepositories())
        .totalRepos(paged.getTotalRepos())
        .build();
  }

  @Override
  public PipelineCRUDResult moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    PipelineEntity pipeline;
    // set empty git entity info for INLINE_HC pipelines to be fetched correctly
    try (EntityGitDetailsGuard ignore = new EntityGitDetailsGuard(GitEntityInfo.builder().build())) {
      pipeline = getPipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false,
          false, false, scopeInfo, isParentIdQueryingEnabled)
                     .get();
    }

    PipelineEntity movedPipelineEntity = movePipelineEntity(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, moveConfigDTO, pipeline, scopeInfo, isParentIdQueryingEnabled);

    return PipelineCRUDResult.builder().pipelineEntity(movedPipelineEntity).build();
  }

  @Override
  public String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    PipelineEntity pipelineMetadata = getPipelineMetadata(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, false, true, scopeInfo, isParentIdQueryingEnabled);
    if (StoreType.INLINE_HC.equals(pipelineMetadata.getStoreType())
        || StoreType.INLINE.equals(pipelineMetadata.getStoreType())) {
      log.error("Cannot update git metadata for pipeline with store type INLINE_HC or INLINE");
      return pipelineIdentifier;
    }

    validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, updateGitDetailsParams,
        scopeInfo, isParentIdQueryingEnabled);

    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), pipelineIdentifier, true);
    Update update = PMSPipelineFilterHelper.getUpdateWithGitMetadata(updateGitDetailsParams);

    PipelineEntity pipelineAfterUpdate = pmsPipelineRepository.updateEntity(criteria, update);
    if (pipelineAfterUpdate == null) {
      throw new EntityNotFoundException(
          format("Pipeline with id [%s] is not present or has been deleted", pipelineIdentifier));
    }

    return pipelineAfterUpdate.getIdentifier();
  }

  /*
  given a list of pipelineIds, sends back the ids with view access
   */
  @Override
  public List<String> getPermittedToViewPipelineIdentifiers(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifierList) {
    AccessCheckResponseDTO accessCheckResponseDTO =
        getAccessCheckResponseDTOForGivenPipelines(accountId, orgId, projectId, pipelineIdentifierList);
    return getPermittedPipelines(accessCheckResponseDTO);
  }

  private List<String> getPermittedPipelines(AccessCheckResponseDTO accessCheckResponseDTO) {
    List<String> permittedPipelineIdentifiers = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponseDTO.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedPipelineIdentifiers.add(accessControlDTO.getResourceIdentifier());
      }
    }
    return permittedPipelineIdentifiers;
  }

  @Override
  public List<String> getPermittedPipelineIdentifier(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifierList) {
    AccessCheckResponseDTO accessCheckResponseDTO =
        getAccessCheckResponseDTO(accountId, orgId, projectId, pipelineIdentifierList);
    return getPermittedPipelines(accessCheckResponseDTO);
  }

  private List<PermissionCheckDTO> getPermissionChecksForPipelineView(
      String accountId, String orgId, String projectId, List<String> entityIdentifierList) {
    return entityIdentifierList.stream()
        .map(identifier
            -> PermissionCheckDTO.builder()
                   .permission(PipelineRbacPermissions.PIPELINE_VIEW)
                   .resourceIdentifier(identifier)
                   .resourceScope(ResourceScope.of(accountId, orgId, projectId))
                   .resourceType("PIPELINE")
                   .build())
        .collect(Collectors.toList());
  }

  private AccessCheckResponseDTO getAccessCheckResponseDTOForGivenPipelines(
      String accountId, String orgId, String projectId, List<String> entityIdentifierList) {
    return accessControlClient.checkForAccess(
        getPermissionChecksForPipelineView(accountId, orgId, projectId, entityIdentifierList));
  }

  /*
 getAccessCheckResponseDTO return the access response for pipeline view permission on the pipeline identifier list
  */
  private AccessCheckResponseDTO getAccessCheckResponseDTO(
      String accountId, String orgId, String projectId, List<String> entityIdentifierList) {
    return accessControlClient.checkForAccessOrThrow(
        getPermissionChecksForPipelineView(accountId, orgId, projectId, entityIdentifierList));
  }

  @Override
  public List<String> listAllIdentifiers(Criteria criteria) {
    return pmsPipelineRepository.findAllPipelineIdentifiers(criteria);
  }

  @Override
  public boolean validateViewPermission(String accountId, String orgId, String projectId) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgId, projectId), Resource.of("PIPELINE", null),
        PipelineRbacPermissions.PIPELINE_VIEW);
  }

  @Override
  public List<YamlValidationResponseDTO> validatePipelineYaml(
      String accountIdentifier, YamlValidationRequestDTO entityYamlValidationRequestDTO) {
    try (GitXFileValidationLogContext context = new GitXFileValidationLogContext(entityYamlValidationRequestDTO)) {
      boolean isParentIdQueryingEnabled = true;
      List<YamlValidationResponseDTO> yamlValidationResponseDTOS = new ArrayList<>();
      int pipelineCount = 0;
      try (Stream<PipelineEntity> pipelineEntitiesStream =
               pmsPipelineServiceHelper.fetchAllPipelinesByFilePathAndRepo(accountIdentifier,
                   entityYamlValidationRequestDTO.getFilePath(), entityYamlValidationRequestDTO.getRepoName())) {
        Iterator<PipelineEntity> pipelineEntities = pipelineEntitiesStream.iterator();
        if (!pipelineEntities.hasNext()) {
          log.error("No pipeline exists with file path: {}, repo: {}, branch: {}",
              entityYamlValidationRequestDTO.getFilePath(), entityYamlValidationRequestDTO.getRepoName(),
              entityYamlValidationRequestDTO.getBranch());
          return List.of(
              YamlValidationResponseDTO.builder()
                  .validationErrorMetadata(
                      YamlValidationErrorMetadata.builder()
                          .hint(String.format(PIPELINE_NOT_FOUND_HINT, entityYamlValidationRequestDTO.getFilePath(),
                              entityYamlValidationRequestDTO.getRepoName(), entityYamlValidationRequestDTO.getBranch(),
                              accountIdentifier))
                          .build())
                  .isValid(false)
                  .build());
        }

        while (pipelineEntities.hasNext()) {
          if (pipelineCount > PIPELINE_LIMIT_FOR_YAML_VALIDATION) {
            log.warn("Limit for pipeline count for yaml validation has reached.");
            break;
          }
          PipelineEntity pipelineEntity = pipelineEntities.next();
          pipelineEntity.setYaml(entityYamlValidationRequestDTO.getYaml());
          GitEntityInfo gitEntityInfo =
              getGitEntityInfoForPipeline(entityYamlValidationRequestDTO, pipelineEntity.getConnectorRef());
          Pair<YamlValidationResponseDTO, GovernanceMetadata> validationResult = performValidation(
              entityYamlValidationRequestDTO.getFilePath(), entityYamlValidationRequestDTO.getRepoName(),
              entityYamlValidationRequestDTO.getBranch(), pipelineEntity, gitEntityInfo, isParentIdQueryingEnabled);
          yamlValidationResponseDTOS.add(validationResult.getLeft());
          if (validationResult.getRight() != null) {
            persistOpaStatusFromWebhookValidation(pipelineEntity, accountIdentifier, validationResult.getRight(),
                entityYamlValidationRequestDTO.getCommitId(), entityYamlValidationRequestDTO.getBranch());
          }
          ScopeInfo scopeInfo = ScopeInfo.builder()
                                    .accountIdentifier(pipelineEntity.getAccountIdentifier())
                                    .orgIdentifier(pipelineEntity.getOrgIdentifier())
                                    .projectIdentifier(pipelineEntity.getProjectIdentifier())
                                    .uniqueId(pipelineEntity.getParentUniqueId())
                                    .scopeType(ScopeLevel.PROJECT)
                                    .build();
          if (entityYamlValidationRequestDTO.getIsDefaultBranch()) {
            pmsPipelineServiceHelper.computePipelineReferences(
                pipelineEntity, entityYamlValidationRequestDTO.getBranch(), scopeInfo);
          }
          pipelineCount++;
        }
      } catch (Exception ex) {
        log.error("Unexpected error while validating pipeline YAML", ex);
        throw new InternalServerErrorException(
            format("Unexpected error while validating pipeline YAML. %s", ex.getMessage()));
      }
      return yamlValidationResponseDTOS;
    }
  }

  private void persistOpaStatusFromWebhookValidation(
      PipelineEntity pipelineEntity, String accountIdentifier, GovernanceMetadata gm, String commitId, String branch) {
    try {
      PipelineEntity entityWithBranch = pipelineEntity.toBuilder().branch(branch).build();
      pipelineOpaStatusHandler.handleWebhookSave(entityWithBranch, accountIdentifier, gm, commitId);
    } catch (Exception e) {
      log.warn("[OpaWebhookPersist] Failed to persist OPA status from webhook validation for pipeline {} in account {},"
              + " proceeding",
          pipelineEntity.getIdentifier(), accountIdentifier, e);
    }
  }

  @Override
  public ForceImportPipelineResponse forceImportPipeline(
      String accountIdentifier, ForceImportPipelineYamlOperationDTO request, boolean isParentIdQueryingEnabled) {
    try (GitXAutoSyncLogContext context = new GitXAutoSyncLogContext(accountIdentifier, request, FORCE_IMPORT)) {
      // validate basic details
      validateForceImportRequest(accountIdentifier, request);
      // set git context
      setupGitContext(request);
      String importedPipelineYAML = null;
      try {
        importedPipelineYAML = pmsPipelineServiceHelper.fetchYAMLFromRemote(accountIdentifier,
            request.getOrgIdentifier(), request.getProjectIdentifier(), true, request.getScopeInfo());
      } catch (HintException | ExplanationException | ScmException e) {
        log.error("Failed to fetch YAML when auto-creating pipeline from remote.", e);
        throw e;
      } catch (Exception e) {
        log.error("Unexpected error while fetching yaml.", e);
        throw new InternalServerErrorException("Unexpected error while fetching yaml.");
      }

      ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();

      String repoUrl = pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(accountIdentifier,
          request.getOrgIdentifier(), request.getProjectIdentifier(), request.getIdentifier(), true,
          request.getScopeInfo(), isParentIdQueryingEnabled);

      // get pipeline version from yaml. consider empty yaml as v0
      String pipelineVersion = pipelineVersion(accountIdentifier, importedPipelineYAML);

      // set pipeline name if possible otherwise use identifier
      String pipelineName = request.getIdentifier();
      String pipelineIdentifier = request.getIdentifier();
      if (isNotEmpty(importedPipelineYAML)) {
        Pair<String, String> pipelineNameAndIdentifierFromYaml =
            PMSPipelineServiceHelper.getPipelineNameAndIdentifierFromYaml(importedPipelineYAML, pipelineVersion);
        if (pipelineNameAndIdentifierFromYaml != null) {
          if (isNotEmpty(pipelineNameAndIdentifierFromYaml.getRight())) {
            pipelineName = pipelineNameAndIdentifierFromYaml.getRight();
          }
          if (isNotEmpty(pipelineNameAndIdentifierFromYaml.getLeft())) {
            pipelineIdentifier = pipelineNameAndIdentifierFromYaml.getLeft();
          }
        }
      }
      validatePipelineIdentifier(pipelineIdentifier);

      PipelineEntity pipelineEntity;
      try {
        pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountIdentifier, request.getOrgIdentifier(),
            request.getProjectIdentifier(), pipelineIdentifier, pipelineName, importedPipelineYAML, false,
            pipelineVersion, request.getScopeInfo(), isParentIdQueryingEnabled, null);
      } catch (Exception ex) {
        log.error(format("Error while mapping to pipeline entity when importing: %s", ex.getMessage()), ex);
        // falling back to simplistic approach
        pipelineEntity = PMSPipelineDtoMapper.toMinimalPipelineEntity(accountIdentifier, request.getOrgIdentifier(),
            request.getProjectIdentifier(), pipelineIdentifier, pipelineName, importedPipelineYAML, false,
            pipelineVersion, request.getScopeInfo(), isParentIdQueryingEnabled);
      }
      pipelineEntity.setRepoURL(repoUrl);
      pipelineEntity.setStoreType(StoreType.REMOTE);
      pipelineEntity.setConnectorRef(request.getConnectorRef());
      pipelineEntity.setRepo(request.getRepoName());
      pipelineEntity.setFilePath(request.getFilePath());

      if (isEmpty(pipelineEntity.getParentUniqueId())) {
        Optional<ScopeInfo> scopeInfoOptional = scopeResolutionHelper.getScopeInfoOptional(
            accountIdentifier, request.getOrgIdentifier(), request.getProjectIdentifier());
        if (scopeInfoOptional.isPresent()) {
          pipelineEntity.setParentUniqueId(scopeInfoOptional.get().getUniqueId());
        }
      }

      GovernanceMetadata governanceMetadata = null;
      if (pmsFeatureFlagService.isEnabled(
              accountIdentifier, FeatureName.PIPE_ENABLE_OPA_GOVERNANCE_FOR_AUTO_CREATION)) {
        try {
          governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
              pipelineEntity, false, false, request.getScopeInfo(), isParentIdQueryingEnabled, false);
          if (governanceMetadata.getDeny()) {
            List<String> denyingRuleSetIds = governanceMetadata.getDetailsList()
                                                 .stream()
                                                 .filter(PolicySetMetadata::getDeny)
                                                 .map(PolicySetMetadata::getIdentifier)
                                                 .collect(Collectors.toList());
            String policyIdentifier = String.join(", ", denyingRuleSetIds);
            log.error(
                "Pipeline governance check failed for pipeline {} in the auto creation flow. Denying policies: {}",
                pipelineEntity.getIdentifier(), policyIdentifier);
            return ForceImportPipelineResponse.builder()
                .identifier(pipelineEntity.getIdentifier())
                .governanceResponse(ForceImportGovernanceResponse.builder()
                                        .governanceCheckFailed(true)
                                        .policyIdentifier(policyIdentifier)
                                        .build())
                .build();
          }
        } catch (Exception ex) {
          log.error("Error during governance check for pipeline {} in the auto creation flow",
              pipelineEntity.getIdentifier(), ex);
          return ForceImportPipelineResponse.builder()
              .identifier(pipelineEntity.getIdentifier())
              .governanceResponse(ForceImportGovernanceResponse.builder()
                                      .governanceCheckFailed(true)
                                      .policyIdentifier("UNKNOWN")
                                      .build())
              .build();
        }
      }

      PipelineEntity entityWithUpdatedInfo = null;
      PipelineEntity savedPipelineEntity = null;

      try {
        entityWithUpdatedInfo = pmsPipelineServiceHelper.updatePipelineInfo(pipelineEntity, pipelineVersion);
      } catch (Exception ex) {
        log.warn("Error when updating pipeline info.", ex);
        // continue with pipeline entity in case of exception
        entityWithUpdatedInfo = pipelineEntity;
      }

      try {
        savedPipelineEntity = pmsPipelineRepository.savePipelineEntityForImportedYAML(
            entityWithUpdatedInfo, request.getScopeInfo(), isParentIdQueryingEnabled);
      } catch (DuplicateKeyException ex) {
        String error = format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier());
        log.error(error, ex);
        throw new DuplicateFieldException(error, USER_SRE, ex);
      }

      // entity successfully saved at this point, follow-up tasks to be done
      try {
        GitAwareContextHelper.updateScmGitMetaData(scmGitMetaData);
        pipelineOpaStatusHandler.handleWebhookSave(savedPipelineEntity, accountIdentifier, governanceMetadata,
            scmGitMetaData != null ? scmGitMetaData.getCommitId() : null);
        computeReferencesIfRemotePipeline(savedPipelineEntity, request.getScopeInfo());
        pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(
            savedPipelineEntity, CREATING_PIPELINE, request.getScopeInfo(), isParentIdQueryingEnabled);
        pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(
            savedPipelineEntity, TEMPLATE_REF_PIPELINE, request.getScopeInfo(), isParentIdQueryingEnabled);
      } catch (Exception ex) {
        log.warn("Exception while performing follow-up tasks.", ex);
      }

      return ForceImportPipelineResponse.builder().identifier(savedPipelineEntity.getIdentifier()).build();
    }
  }

  private void validatePipelineIdentifier(String pipelineIdentifier) {
    if (isEmpty(pipelineIdentifier)
        || !EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(pipelineIdentifier).matches()) {
      throw new InvalidRequestException(
          "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
          + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");
    }
  }

  private void setupGitContext(ForceImportPipelineYamlOperationDTO forceImportPipelineRequestDTO) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(forceImportPipelineRequestDTO.getBranch())
                                                 .filePath(forceImportPipelineRequestDTO.getFilePath())
                                                 .connectorRef(forceImportPipelineRequestDTO.getConnectorRef())
                                                 .storeType(StoreType.REMOTE)
                                                 .repoName(forceImportPipelineRequestDTO.getRepoName())
                                                 .build());
  }

  private void validateForceImportRequest(
      @NonNull String accountIdentifier, @NonNull ForceImportPipelineYamlOperationDTO forceImportRequest) {
    validateField(forceImportRequest.getOrgIdentifier(), "org identifier");
    validateField(forceImportRequest.getProjectIdentifier(), "project identifier");

    checkOrganizationExists(accountIdentifier, forceImportRequest.getOrgIdentifier());
    checkProjectExists(
        accountIdentifier, forceImportRequest.getOrgIdentifier(), forceImportRequest.getProjectIdentifier());

    validateField(forceImportRequest.getIdentifier(), "pipeline identifier");
    validateField(forceImportRequest.getFilePath(), "file path");
    validateField(forceImportRequest.getRepoName(), "repo name");

    if (BooleanUtils.isNotTrue(forceImportRequest.getIsHarnessCodeRepo())
        && isEmpty(forceImportRequest.getConnectorRef())) {
      throw new InvalidRequestException("connector ref not present in force import request for pipeline.");
    }
  }

  private void checkOrganizationExists(String accountIdentifier, String orgIdentifier) {
    getResponse(organizationClient.getOrganization(orgIdentifier, accountIdentifier),
        String.format("Organization with orgIdentifier %s not found", orgIdentifier));
  }

  private void validateField(String field, String fieldName) {
    if (isEmpty(field)) {
      throw new InvalidRequestException(format("%s not present in force import request for pipeline.", fieldName));
    }
  }

  private Pair<YamlValidationResponseDTO, GovernanceMetadata> performValidation(String filePath, String repoName,
      String branch, PipelineEntity pipelineEntity, GitEntityInfo gitEntityInfo, boolean isParentIdQueryingEnabled) {
    try (EntityGitDetailsGuard entityGitDetailsGuard = new EntityGitDetailsGuard(gitEntityInfo)) {
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(pipelineEntity.getAccountId(), pipelineEntity.getParentUniqueId());
      GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
          pipelineEntity, false, false, scopeInfo, isParentIdQueryingEnabled, false);
      if (governanceMetadata != null && governanceMetadata.getDeny()
          && !pmsFeatureFlagService.isEnabled(
              pipelineEntity.getAccountId(), FeatureName.PIPE_DISABLE_OPA_GOVERNANCE_IN_WEBHOOK_VALIDATION)) {
        List<String> denyingRuleSetIds = governanceMetadata.getDetailsList()
                                             .stream()
                                             .filter(PolicySetMetadata::getDeny)
                                             .map(PolicySetMetadata::getIdentifier)
                                             .collect(Collectors.toList());
        String denyingPolicies = String.join(", ", denyingRuleSetIds);
        log.error("Pipeline governance check failed for pipeline {} during validation. Denying policies: {}",
            pipelineEntity.getIdentifier(), denyingPolicies);
        return Pair.of(
            YamlValidationResponseDTO.builder()
                .entityMetadata(PipelineEntityMetadata.builder()
                                    .scope(Scope.of(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                                        pipelineEntity.getProjectIdentifier(), pipelineEntity.getParentUniqueId()))
                                    .identifier(pipelineEntity.getIdentifier())
                                    .build())
                .validationErrorMetadata(
                    YamlValidationErrorMetadata.builder()
                        .errorMessage("Pipeline does not follow the Policies in these Policy Sets: " + denyingPolicies)
                        .explanation(GOVERNANCE_DENY_EXPLANATION)
                        .hint(GOVERNANCE_DENY_HINT)
                        .build())
                .isValid(false)
                .build(),
            governanceMetadata);
      }
      return Pair.of(
          YamlValidationResponseDTO.builder()
              .entityMetadata(PipelineEntityMetadata.builder()
                                  .scope(Scope.of(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                                      pipelineEntity.getProjectIdentifier(), pipelineEntity.getParentUniqueId()))
                                  .identifier(pipelineEntity.getIdentifier())
                                  .build())
              .isValid(true)
              .build(),
          governanceMetadata);
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.error("Given pipeline yaml with file path: {}, repo: {}, branch: {} is not valid yaml: ", filePath, repoName,
          branch, e);
      return Pair.of(
          YamlValidationResponseDTO.builder()
              .entityMetadata(PipelineEntityMetadata.builder()
                                  .scope(Scope.of(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                                      pipelineEntity.getProjectIdentifier(), pipelineEntity.getParentUniqueId()))
                                  .identifier(pipelineEntity.getIdentifier())
                                  .build())
              .validationErrorMetadata(YamlValidationErrorMetadata.builder().errorMessage(e.getMessage()).build())
              .isValid(false)
              .build(),
          null);
    } catch (Exception e) {
      log.error("Pipeline Yaml validation failed with unexpected error: ", e);
      return Pair.of(
          YamlValidationResponseDTO.builder()
              .entityMetadata(PipelineEntityMetadata.builder()
                                  .scope(Scope.of(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                                      pipelineEntity.getProjectIdentifier(), pipelineEntity.getParentUniqueId()))
                                  .identifier(pipelineEntity.getIdentifier())
                                  .build())
              .validationErrorMetadata(YamlValidationErrorMetadata.builder().errorMessage(e.getMessage()).build())
              .isValid(false)
              .build(),
          null);
    }
  }

  public List<YamlInputDetails> getInputSchemaDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
    return pmsYamlSchemaService.getInputSchemaDetails(optionalPipelineEntity.get().getYaml());
  }

  private void validateRepo(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    if (isEmpty(updateGitDetailsParams.getRepoName())) {
      return;
    }

    String connectorRef = updateGitDetailsParams.getConnectorRef();
    if (connectorRef == null) {
      Optional<PipelineEntity> optionalPipelineEntity = getPipeline(accountIdentifier, orgIdentifier, projectIdentifier,
          pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
      checkIfPipelineIsPresent(orgIdentifier, projectIdentifier, pipelineIdentifier, optionalPipelineEntity);

      connectorRef = optionalPipelineEntity.get().getConnectorRef();
    }

    gitAwareEntityHelper.validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef,
        updateGitDetailsParams.getRepoName(), scopeInfo);
  }

  private void checkIfPipelineIsPresent(String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      Optional<PipelineEntity> optionalPipelineEntity) {
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  @VisibleForTesting
  protected PipelineEntity movePipelineEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO, PipelineEntity pipeline, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria pipelineCriteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        accountIdentifier, scopeInfo.getUniqueId(), pipelineIdentifier, false, null);
    Criteria metadataCriteria = pmsPipelineServiceHelper.getPipelineMetadataV2Criteria(
        accountIdentifier, scopeInfo.getUniqueId(), pipelineIdentifier);

    Update pipelineUpdate;
    Update metadataUpdate = new Update();

    if (INLINE_TO_REMOTE.equals(moveConfigDTO.getMoveConfigOperationType())) {
      setupGitContext(moveConfigDTO);
      if (Boolean.TRUE.equals(moveConfigDTO.getIsHarnessCodeRepo())) {
        applyGitXSettingsIfApplicable(accountIdentifier, orgIdentifier, projectIdentifier);
      }

      pipelineUpdate = pmsPipelineServiceHelper.getPipelineUpdateForInlineToRemote(
          accountIdentifier, orgIdentifier, projectIdentifier, moveConfigDTO, scopeInfo, isParentIdQueryingEnabled);
      metadataUpdate = metadataUpdate.set(PipelineMetadataV2Keys.branch, moveConfigDTO.getBranch());

    } else if (REMOTE_TO_INLINE.equals(moveConfigDTO.getMoveConfigOperationType())) {
      pipelineUpdate = pmsPipelineServiceHelper.getPipelineUpdateForRemoteToInline();
      metadataUpdate = metadataUpdate.unset(PipelineMetadataV2Keys.entityGitDetails);
    } else {
      log.error("Invalid move config operation provided: {}", moveConfigDTO.getMoveConfigOperationType().name());
      throw new InvalidRequestException(String.format(
          "Invalid move config operation specified [%s].", moveConfigDTO.getMoveConfigOperationType().name()));
    }
    PipelineEntity pipelineEntity =
        pmsPipelineRepository.updatePipelineEntity(pipeline, pipelineUpdate, pipelineCriteria, metadataUpdate,
            metadataCriteria, moveConfigDTO.getMoveConfigOperationType(), scopeInfo, isParentIdQueryingEnabled);
    computeSetupReferences(pipelineEntity, moveConfigDTO, scopeInfo, isParentIdQueryingEnabled);
    return pipelineEntity;
  }

  private void computeSetupReferences(PipelineEntity pipelineEntity, MoveConfigOperationDTO moveConfigDTO,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      if (INLINE_TO_REMOTE.equals(moveConfigDTO.getMoveConfigOperationType())) {
        Optional<PipelineEntity> optionalPipelineEntity = getPipeline(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
            false, false, true, false, scopeInfo, isParentIdQueryingEnabled);
        if (optionalPipelineEntity.isPresent()) {
          pmsPipelineServiceHelper.deletePipelineReferences(optionalPipelineEntity.get(), scopeInfo);
          if (PipelineGitXHelper.shouldPublishSetupUsages(optionalPipelineEntity.get().getStoreType(), false)) {
            pmsPipelineServiceHelper.computePipelineReferences(optionalPipelineEntity.get(), scopeInfo);
          }
        }
      }
    } catch (Exception exception) {
      log.error(String.format("Error occurred while trying to update references for pipeline %s: %s",
          pipelineEntity.getIdentifier(), exception));
    }
  }

  private void setupGitContext(MoveConfigOperationDTO moveConfigDTO) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(moveConfigDTO.getBranch())
                                                 .filePath(moveConfigDTO.getFilePath())
                                                 .commitMsg(moveConfigDTO.getCommitMessage())
                                                 .isNewBranch(moveConfigDTO.isNewBranch())
                                                 .baseBranch(moveConfigDTO.getBaseBranch())
                                                 .connectorRef(nullToEmpty(moveConfigDTO.getConnectorRef()))
                                                 .storeType(StoreType.REMOTE)
                                                 .repoName(moveConfigDTO.getRepoName())
                                                 .isHarnessCodeRepo(moveConfigDTO.getIsHarnessCodeRepo())
                                                 .build());
  }

  private void checkProjectExists(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier),
          format("Project with orgIdentifier %s and identifier %s not found", orgIdentifier, projectIdentifier));
    }
  }
  public void checkThatTheModuleExists(String module) {
    if (isNotEmpty(module)
        && isEmpty(ModuleType.getModules()
                       .stream()
                       .filter(moduleType -> moduleType.name().equalsIgnoreCase(module))
                       .collect(Collectors.toList()))) {
      throw new HintException(format(
          "Invalid module type [%s]. Please select the correct module type %s", module, ModuleType.getModules()));
    }
  }

  @VisibleForTesting
  void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.PIPELINES);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
  }

  private void computeReferencesIfRemotePipeline(PipelineEntity pipelineEntity, ScopeInfo scopeInfo) {
    if (PipelineGitXHelper.shouldPublishSetupUsages(pipelineEntity.getStoreType(), false)) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, scopeInfo);
    }
  }

  private boolean isGitSyncEnabled(String accountId, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    accountId = scopeInfo.getAccountIdentifier();
    orgIdentifier = scopeInfo.getOrgIdentifier();
    projectIdentifier = scopeInfo.getProjectIdentifier();
    return gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
  }

  private GitEntityInfo getGitEntityInfoForPipeline(
      YamlValidationRequestDTO entityYamlValidationRequestDTO, String connectorRef) {
    return GitEntityInfo.builder()
        .branch(entityYamlValidationRequestDTO.getBranch())
        .connectorRef(connectorRef)
        .parentEntityRepoName(entityYamlValidationRequestDTO.getRepoName())
        .build();
  }

  private GitEntityInfo buildGitEntityInfo(String accountId, String branch) {
    if (branch == null
        && !pmsFeatureFlagService.isEnabled(
            accountId, FeatureName.PIPE_CLONE_PIPELINE_USE_DEFAULT_IF_SOURCE_BRANCH_NOT_PASSED)) {
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      branch = gitEntityInfo.getBranch();
    }
    return GitEntityInfo.builder().branch(branch).build();
  }

  private void validatePipelineCreationLimitBreach(String accountIdentifier) {
    long currentPipelineCount = pmsPipelineRepository.countAllPipelinesInAccount(accountIdentifier);
    if (!pipelineSettingsService.isPipelineCreationWithinLimit(accountIdentifier, currentPipelineCount)) {
      log.warn("[PIPELINE_CREATION_LIMIT_EXCEEDED]: The pipeline creation limit is exceeded for the account {}.",
          accountIdentifier);
      try {
        pipelineRetentionService.updateMaxPipelineCreationLimit(accountIdentifier, (int) currentPipelineCount);
      } catch (Exception ex) {
        log.warn(String.format("Can be ignored - Error in overriding the pipeline creation limit for account id: "
                         + "{%s}, to size: {%d}:",
                     accountIdentifier, currentPipelineCount),
            ex);
      }
    }
  }

  private boolean checkIfFileNotPresentError(HintException e) {
    ScmException scmException = ScmExceptionUtils.getScmException(e);
    return scmException != null && scmException.getMessage().equals(SCMExceptionErrorMessages.FILE_NOT_FOUND_ERROR);
  }

  @Override
  public PMSPipelineResponseDTO convertPipelineToDAG(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (!pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)) {
      throw new InvalidRequestException(String.format(
          "FF for enabling DAG is not enabled for this account: {%s}. Please contact Harness support to enable it",
          accountIdentifier));
    }
    Optional<PipelineEntity> pipelineEntityOptional = getPipeline(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (!pipelineEntityOptional.isPresent()) {
      throw new EntityNotFoundException(format("Pipeline with identifier [%s] not found", pipelineIdentifier));
    }

    PipelineEntity pipelineEntity = pipelineEntityOptional.get();
    // Check if pipeline is already in DAG format
    if (Boolean.TRUE.equals(pipelineEntity.getEnableDAG())) {
      throw new InvalidRequestException("Pipeline is already in DAG format");
    }

    try {
      String convertedYaml = PipelineYamlUtils.convertSequentialPipelineToDAG(pipelineEntity.getYaml());
      PipelineEntity updatedEntity = pipelineEntity.withYaml(convertedYaml).withEnableDAG(true);
      PipelineEntity savedEntity = pmsPipelineRepository.save(updatedEntity, scopeInfo, isParentIdQueryingEnabled);

      log.info("Successfully converted pipeline {} to DAG format", pipelineIdentifier);
      return PMSPipelineDtoMapper.writePipelineDto(savedEntity);

    } catch (Exception e) {
      log.error("Error converting pipeline {} to DAG format", pipelineIdentifier, e);
      throw new InvalidRequestException(format("Failed to convert pipeline to DAG format: %s", e.getMessage()));
    }
  }
}
