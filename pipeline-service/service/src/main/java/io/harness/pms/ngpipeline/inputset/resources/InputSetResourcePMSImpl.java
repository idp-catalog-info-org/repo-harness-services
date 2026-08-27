/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ENABLE_SETTING_PIPELINE_CONTEXT_IN_INPUT_SET_MERGE_REQUEST;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.removeRuntimeInputFromYaml;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.GitxRefreshMetrics;
import io.harness.gitaware.helper.MoveConfigOperationType;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitx.CrudAction;
import io.harness.gitx.InlineHCHelper;
import io.harness.grpc.utils.FlowName;
import io.harness.grpc.utils.GrpcContextMetadataDto;
import io.harness.grpc.utils.GrpcContextMetadataHelper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.ForceImportInputSetRequestDTO;
import io.harness.pms.inputset.ForceImportInputSetResponse;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetFilterPropertiesDto;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.InputSetRemoteRepoInfo;
import io.harness.pms.inputset.InputSetRemoteRepoListResponse;
import io.harness.pms.inputset.MergeInputSetForRerunRequestDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.inputset.RemoteInputSetsDTO;
import io.harness.pms.inputset.RemoteInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIResponse;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.ForceImportInputSetYamlOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetGitUpdateResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSanitiseResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.helpers.validate.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.InputSetRbacPermissions;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.beans.YamlValidationAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationListAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO.YamlValidationRequestDTOBuilder;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY,
        HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class InputSetResourcePMSImpl implements InputSetResourcePMS {
  private final PMSInputSetService pmsInputSetService;
  private final PMSPipelineService pipelineService;
  private final GitSyncSdkService gitSyncSdkService;
  private final ValidateAndMergeHelper validateAndMergeHelper;
  private final InputSetsApiUtils inputSetsApiUtils;
  private final PMSExecutionService executionService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final AccessControlClient accessControlClient;
  private final PMSPipelineServiceHelper pmsPipelineServiceHelper;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final GitxRefreshMetrics gitxRefreshMetrics;

  private static final String INPUT_SET = "INPUT_SET";
  private static final String PIPELINE_SERVICE = "pipeline-service";

  public ResponseDTO<InputSetResponseDTOPMS> getInputSet(String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      String pipelineBranch, String pipelineRepoId, boolean loadFromFallbackBranch,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_GET).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_VIEW);
    } else {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);
    }
    log.info(String.format("Retrieving input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    Optional<InputSetEntity> optionalInputSetEntity =
        pmsInputSetService.get(scopeInfo, pipelineIdentifier, inputSetIdentifier, false, pipelineBranch, pipelineRepoId,
            false, loadFromFallbackBranch, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), true);

    if (optionalInputSetEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    InputSetEntity inputSetEntity = optionalInputSetEntity.get();
    InputSetResponseDTOPMS inputSet = PMSInputSetElementMapper.toInputSetResponseDTOPMS(inputSetEntity, scopeInfo);

    return ResponseDTO.newResponse(inputSetEntity.getVersion().toString(), inputSet);
  }

  @Override
  public ResponseDTO<InputSetResponseDTOPMS> refreshAndGetInputSet(String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      @NotBlank String branch, ScopeInfo scopeInfo) {
    return gitxRefreshMetrics.executeWithMetrics(accountId, () -> {
      if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
        String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
            Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_VIEW);
      }
      GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                                .flowName(FlowName.INPUTSET_REFRESH_AND_GET)
                                                .callerName(PIPELINE_SERVICE)
                                                .build());
      pmsInputSetService.refreshGitFileCache(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetIdentifier, branch, scopeInfo);
      return getInputSet(inputSetIdentifier, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null,
          null, false, null, BOOLEAN_FALSE_VALUE, scopeInfo);
    });
  }

  public ResponseDTO<OverlayInputSetResponseDTOPMS> getOverlayInputSet(String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      String pipelineBranch, String pipelineRepoId, boolean loadFromFallbackBranch,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_GET).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(scopeInfo.getAccountIdentifier(), FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                    scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_VIEW);
    } else {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                    scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);
    }
    log.info(String.format(
        "Retrieving overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, scopeInfo.getProjectIdentifier(), scopeInfo.getProjectIdentifier(),
        scopeInfo.getProjectIdentifier()));
    Optional<InputSetEntity> optionalInputSetEntity =
        pmsInputSetService.get(scopeInfo, pipelineIdentifier, inputSetIdentifier, false, pipelineBranch, pipelineRepoId,
            false, loadFromFallbackBranch, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), true);

    if (optionalInputSetEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSetIdentifier));
    }
    InputSetEntity inputSetEntity = optionalInputSetEntity.get();
    OverlayInputSetResponseDTOPMS overlayInputSet =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(inputSetEntity, scopeInfo, true);

    return ResponseDTO.newResponse(inputSetEntity.getVersion().toString(), overlayInputSet);
  }

  public ResponseDTO<InputSetResponseDTOPMS> createInputSet(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, String pipelineBranch, String pipelineRepoID,
      GitEntityCreateInfoDTO gitEntityCreateInfo, String inputSetVersion, @NotNull String yaml, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_CREATE).callerName(PIPELINE_SERVICE).build());
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityFromVersion(
        scopeInfo, pipelineIdentifier, yaml, inputSetVersion, InputSetEntityType.INPUT_SET);

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + entity.getIdentifier();
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    log.info(String.format("Create input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        entity.getIdentifier(), pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));

    InputSetEntity createdEntity = pmsInputSetService.create(entity, false, scopeInfo);
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(),
        PMSInputSetElementMapper.toInputSetResponseDTOPMS(createdEntity, scopeInfo));
  }

  @Override
  public ResponseDTO<OverlayInputSetResponseDTOPMS> createOverlayInputSet(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, GitEntityCreateInfoDTO gitEntityCreateInfo,
      String inputSetVersion, @NotNull String yaml, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_CREATE).callerName(PIPELINE_SERVICE).build());
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, inputSetVersion);

    InlineHCHelper.checkAndUpdateContextForInlineHC(entity, CrudAction.CREATE,
        InlineHCUpdateContextRequest.builder()
            .entityIdentifier(entity.getIdentifier())
            .parentIdentifier(pipelineIdentifier)
            .scope(Scope.of(accountId, orgIdentifier, projectIdentifier))
            .build(),
        pmsFeatureFlagService::isEnabled);

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + entity.getIdentifier();
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    log.info(
        String.format("Create overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
            entity.getIdentifier(), pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));

    // overlay input set validation does not require pipeline branch and repo, hence sending null here
    InputSetEntity createdEntity = pmsInputSetService.create(entity, false, scopeInfo);
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(),
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(createdEntity, scopeInfo, true));
  }

  public ResponseDTO<OverlayInputSetResponseDTOPMS> createOverlayInputSet(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, GitEntityCreateInfoDTO gitEntityCreateInfo,
      @NotNull String yaml, ScopeInfo scopeInfo) {
    return createOverlayInputSet(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, gitEntityCreateInfo,
        HarnessYamlVersion.V0, yaml, scopeInfo);
  }

  public ResponseDTO<InputSetResponseDTOPMS> updateInputSet(String ifMatch, String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      String pipelineBranch, String pipelineRepoID, GitEntityUpdateInfoDTO gitEntityInfo, String inputSetVersion,
      @NotNull String yaml, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_UPDATE).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityFromVersion(
        scopeInfo, pipelineIdentifier, yaml, inputSetVersion, InputSetEntityType.INPUT_SET);
    if (!isEmpty(entity.getIdentifier()) && !Objects.equals(inputSetIdentifier, entity.getIdentifier())) {
      throw new InvalidRequestException(String.format(
          "Input Set Identifier : %s in input set request doesn't match with identifier : %s given in yaml",
          inputSetIdentifier, entity.getIdentifier()));
    }
    InputSetEntity entityWithVersion = entity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    InputSetEntity updatedEntity = pmsInputSetService.update(ChangeType.MODIFY, entityWithVersion, false);
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(),
        PMSInputSetElementMapper.toInputSetResponseDTOPMS(updatedEntity, scopeInfo));
  }

  @Override
  public ResponseDTO<OverlayInputSetResponseDTOPMS> updateOverlayInputSet(String ifMatch, String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      GitEntityUpdateInfoDTO gitEntityInfo, String inputSetVersion, @NotNull String yaml) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_UPDATE).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    log.info(
        String.format("Updating overlay input set with identifier %s for pipeline %s in project %s, org %s, account %s",
            inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, inputSetVersion);
    InputSetEntity entityWithVersion = entity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ScopeInfo scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, null);
    // overlay input set validation does not require pipeline branch and repo, hence sending null here
    InputSetEntity updatedEntity = pmsInputSetService.update(ChangeType.MODIFY, entityWithVersion, false, scopeInfo);
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(),
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(updatedEntity, scopeInfo, true));
  }

  public ResponseDTO<OverlayInputSetResponseDTOPMS> updateOverlayInputSet(String ifMatch, String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      GitEntityUpdateInfoDTO gitEntityInfo, @NotNull String yaml) {
    return updateOverlayInputSet(ifMatch, inputSetIdentifier, accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gitEntityInfo, HarnessYamlVersion.V0, yaml);
  }

  public ResponseDTO<Boolean> delete(String ifMatch, String inputSetIdentifier,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      GitEntityDeleteInfoDTO entityDeleteInfo, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_DELETE).callerName(PIPELINE_SERVICE).build());
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_DELETE);
    } else {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_DELETE);
    }

    log.info(String.format("Deleting input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    return ResponseDTO.newResponse(pmsInputSetService.delete(
        scopeInfo, pipelineIdentifier, inputSetIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, true));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>> listInputSetsForPipeline(int page, int size,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      InputSetListTypePMS inputSetListType, String searchTerm, List<String> sort,
      GitEntityFindInfoDTO gitEntityBasicInfo, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_LIST).callerName(PIPELINE_SERVICE).build());
    log.info(String.format("Get List of input sets for pipeline %s in project %s, org %s, account %s",
        pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(scopeInfo.getAccountIdentifier(),
        scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), pipelineIdentifier, inputSetListType,
        searchTerm, false, scopeInfo.getUniqueId(), true);
    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
    Page<InputSetEntity> inputSetEntities = pmsInputSetService.list(criteria, pageRequest, scopeInfo);

    Page<InputSetSummaryResponseDTOPMS> inputSetList =
        inputSetEntities.map(PMSInputSetElementMapper::toInputSetSummaryResponseDTOPMS);
    return ResponseDTO.newResponse(getNGPageResponse(inputSetList));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<InputSetTemplateResponseDTOPMS> getTemplateFromPipeline(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      GitEntityFindInfoDTO gitEntityBasicInfo, InputSetTemplateRequestDTO inputSetTemplateRequestDTO,
      String loadFromCache, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_GET_TEMPLATE).callerName(PIPELINE_SERVICE).build());
    log.info(String.format("Get template for pipeline %s in project %s, org %s, account %s", pipelineIdentifier,
        projectIdentifier, orgIdentifier, accountId));
    List<String> stageIdentifiers =
        inputSetTemplateRequestDTO == null ? Collections.emptyList() : inputSetTemplateRequestDTO.getStageIdentifiers();
    InputSetTemplateResponseDTOPMS response = validateAndMergeHelper.getInputSetTemplateResponseDTO(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, stageIdentifiers,
        GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo, true, true);
    return ResponseDTO.newResponse(response);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<MergeInputSetResponseDTOPMS> getMergeInputSetFromPipelineTemplate(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      String pipelineBranch, String pipelineRepoID, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO, String loadFromCache, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_MERGE).callerName(PIPELINE_SERVICE).build());
    if (pipelineBranch == null && gitEntityBasicInfo != null) {
      pipelineBranch = gitEntityBasicInfo.getBranch();
    }

    // To keep it backward compatible, need to set pipeline repo in context only if repo isn't set in input context
    if (!GitAwareContextHelper.isParentEntityRepoSetInGitContext()) {
      if (pmsFeatureFlagService.isEnabled(accountId, PIPE_ENABLE_SETTING_PIPELINE_CONTEXT_IN_INPUT_SET_MERGE_REQUEST)) {
        PipelineEntity pipelineEntity = pipelineService.getPipelineMetadata(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, true, scopeInfo, true);
        GitAwareContextHelper.updateParentEntityRepoInGitContext(pipelineEntity.getRepo());
        log.warn("Setting pipeline parent repo name in context as a fix, it may be backward incompatible. If something "
            + "breaks, consider switching off FF: PIPE_ENABLE_SETTING_PIPELINE_CONTEXT_IN_INPUT_SET_MERGE_REQUEST");
      }
    }

    List<String> inputSetReferences = mergeInputSetRequestDTO.getInputSetReferences();
    String inputSetBranch = mergeInputSetRequestDTO.getInputSetBranchName();
    String mergedYaml;
    try {
      mergedYaml = validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(
          scopeInfo, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID,
          mergeInputSetRequestDTO.getStageIdentifiers(), mergeInputSetRequestDTO.getLastYamlToMerge(),
          GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), true, inputSetBranch);
    } catch (InvalidInputSetException e) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = (InputSetErrorWrapperDTOPMS) e.getMetadata();
      return ResponseDTO.newResponse(
          MergeInputSetResponseDTOPMS.builder().isErrorResponse(true).inputSetErrorWrapper(errorWrapperDTO).build());
    }
    String fullYaml = "";
    if (mergeInputSetRequestDTO.isWithMergedPipelineYaml()) {
      fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, mergedYaml, pipelineBranch, pipelineRepoID, mergeInputSetRequestDTO.getStageIdentifiers(),
          GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo);
    }
    return ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                       .isErrorResponse(false)
                                       .pipelineYaml(mergedYaml)
                                       .completePipelineYaml(fullYaml)
                                       .build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<MergeInputSetResponseDTOPMS> getMergeInputSetForRerun(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, String pipelineBranch, String pipelineRepoID,
      GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull @Valid MergeInputSetForRerunRequestDTO mergeInputSetRequestDTO,
      ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_MERGE).callerName(PIPELINE_SERVICE).build());
    String planExecutionId = mergeInputSetRequestDTO.getPlanExecutionId();
    String mergedYaml;
    try {
      mergedYaml = executionService.mergeRuntimeInputIntoPipelineForRerun(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, planExecutionId, pipelineBranch, pipelineRepoID,
          mergeInputSetRequestDTO.getStageIdentifiers(), scopeInfo);
    } catch (InvalidInputSetException e) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = (InputSetErrorWrapperDTOPMS) e.getMetadata();
      return ResponseDTO.newResponse(
          MergeInputSetResponseDTOPMS.builder().isErrorResponse(true).inputSetErrorWrapper(errorWrapperDTO).build());
    }
    String fullYaml = "";
    if (mergeInputSetRequestDTO.isGetResponseWithMergedPipelineYaml()) {
      fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, mergedYaml, pipelineBranch, pipelineRepoID, mergeInputSetRequestDTO.getStageIdentifiers(),
          false, scopeInfo);
    }
    return ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                       .isErrorResponse(false)
                                       .pipelineYaml(mergedYaml)
                                       .completePipelineYaml(fullYaml)
                                       .build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<MergeInputSetResponseDTOPMS> getMergeInputForExecution(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, boolean resolveExpressions,
      ResolveInputYamlType resolveExpressionsType, @NotNull String planExecutionId) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_MERGE).callerName(PIPELINE_SERVICE).build());
    String mergedYaml;
    try {
      mergedYaml = executionService.mergeRuntimeInputIntoPipeline(
          accountId, orgIdentifier, projectIdentifier, planExecutionId, resolveExpressions, resolveExpressionsType);
    } catch (InvalidInputSetException e) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = (InputSetErrorWrapperDTOPMS) e.getMetadata();
      return ResponseDTO.newResponse(
          MergeInputSetResponseDTOPMS.builder().isErrorResponse(true).inputSetErrorWrapper(errorWrapperDTO).build());
    }
    return ResponseDTO.newResponse(
        MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).pipelineYaml(mergedYaml).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  // TODO(Naman): Correct PipelineServiceClient when modifying this api
  public ResponseDTO<MergeInputSetResponseDTOPMS> getMergeInputSetFromPipelineTemplate(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      String pipelineBranch, String pipelineRepoID, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetTemplateRequestDTO mergeInputSetTemplateRequestDTO, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_MERGE).callerName(PIPELINE_SERVICE).build());
    String fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, mergeInputSetTemplateRequestDTO.getRuntimeInputYaml(), pipelineBranch, pipelineRepoID, null,
        false, scopeInfo);
    return ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                       .isErrorResponse(false)
                                       .pipelineYaml(mergeInputSetTemplateRequestDTO.getRuntimeInputYaml())
                                       .completePipelineYaml(fullYaml)
                                       .build());
  }

  public ResponseDTO<InputSetSanitiseResponseDTO> sanitiseInputSet(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, String inputSetIdentifier, String pipelineBranch,
      String pipelineRepoID, GitEntityUpdateInfoDTO gitEntityInfo, @NotNull String invalidInputSetYaml,
      ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_SANITIZE).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    String pipelineYaml =
        validateAndMergeHelper
            .getPipelineEntity(scopeInfo, pipelineIdentifier, pipelineBranch, pipelineRepoID, false, false)
            .getYaml();
    String newInputSetYaml = InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML(pipelineYaml, invalidInputSetYaml);
    if (isEmpty(newInputSetYaml)) {
      return ResponseDTO.newResponse(InputSetSanitiseResponseDTO.builder().shouldDeleteInputSet(true).build());
    }

    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSetIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
    newInputSetYaml = removeRuntimeInputFromYaml(pipelineYaml, newInputSetYaml);

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, newInputSetYaml);
    InputSetEntity updatedEntity = pmsInputSetService.update(ChangeType.MODIFY, entity, false, scopeInfo);
    return ResponseDTO.newResponse(
        InputSetSanitiseResponseDTO.builder()
            .shouldDeleteInputSet(false)
            .inputSetUpdateResponse(PMSInputSetElementMapper.toInputSetResponseDTOPMS(updatedEntity, scopeInfo, true))
            .build());
  }

  // Pipeline Branch is mandatory for this Api
  public ResponseDTO<InputSetYamlDiffDTO> getInputSetYAMLDiff(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, String inputSetIdentifier, String pipelineBranch,
      String pipelineRepoID, GitEntityUpdateInfoDTO gitEntityInfo, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_GET_DIFF).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_VIEW);
    } else {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);
    }
    return ResponseDTO.newResponse(InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, pmsInputSetService,
        pipelineService, validateAndMergeHelper, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        inputSetIdentifier, pipelineBranch, pipelineRepoID, inputSetsApiUtils, scopeInfo, true, true));
  }

  public ResponseDTO<InputSetImportResponseDTO> importInputSetFromGit(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, String inputSetIdentifier,
      @Valid GitImportInfoDTO gitImportInfoDTO, InputSetImportRequestDTO inputSetImportRequestDTO,
      ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_IMPORT).callerName(PIPELINE_SERVICE).build());
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipelineIdentifier + "-" + inputSetIdentifier;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier,
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }

    InputSetEntity inputSetEntity =
        pmsInputSetService.importInputSetFromRemote(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            inputSetIdentifier, inputSetImportRequestDTO, gitImportInfoDTO.getIsForceImport(), scopeInfo);
    return ResponseDTO.newResponse(
        InputSetImportResponseDTO.builder().identifier(inputSetEntity.getIdentifier()).build());
  }

  @Override
  public ResponseDTO<InputSetMoveConfigResponseDTO> moveConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String inputSetIdentifier, InputSetMoveConfigRequestDTO inputSetMoveConfigRequestDTO,
      ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_MOVE).callerName(PIPELINE_SERVICE).build());
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
        projectIdentifier, inputSetMoveConfigRequestDTO.getPipelineIdentifier(),
        pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    if (!inputSetIdentifier.equals(inputSetMoveConfigRequestDTO.getInputSetIdentifier())) {
      throw new InvalidRequestException("Identifiers given in path param and request body don't match.");
    }
    InputSetEntity movedInputSet = pmsInputSetService.moveConfig(accountIdentifier, orgIdentifier, projectIdentifier,
        inputSetIdentifier,
        InputSetMoveConfigOperationDTO.builder()
            .connectorRef(inputSetMoveConfigRequestDTO.getConnectorRef())
            .repoName(inputSetMoveConfigRequestDTO.getRepoName())
            .branch(inputSetMoveConfigRequestDTO.getBranch())
            .filePath(inputSetMoveConfigRequestDTO.getFilePath())
            .baseBranch(inputSetMoveConfigRequestDTO.getBaseBranch())
            .commitMessage(inputSetMoveConfigRequestDTO.getCommitMsg())
            .isNewBranch(inputSetMoveConfigRequestDTO.getIsNewBranch())
            .pipelineIdentifier(inputSetMoveConfigRequestDTO.getPipelineIdentifier())
            .moveConfigOperationType(
                MoveConfigOperationType.getMoveConfigType(inputSetMoveConfigRequestDTO.getMoveConfigOperationType()))
            .build(),
        scopeInfo);
    return ResponseDTO.newResponse(
        InputSetMoveConfigResponseDTO.builder().identifier(movedInputSet.getIdentifier()).build());
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSInputSetListRepoResponse> getListRepos(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String pipelineIdentifier, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_LIST).callerName(PIPELINE_SERVICE).build());
    return ResponseDTO.newResponse(pmsInputSetService.getListOfRepos(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, true));
  }

  @Override
  public ResponseDTO<RemoteInputSetsResponseDTO> getRemoteInputSetMetadata(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String repoName, int page, int size, ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_INPUT_SET_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      InputSetRemoteRepoListResponse serviceResponse = pmsInputSetService.getRemoteRepoListForAGivenScope(
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<InputSetRemoteRepoInfo> serviceRepos =
          serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();
      List<RemoteInputSetsDTO> resourceRepos = serviceRepos.stream()
                                                   .map(info
                                                       -> RemoteInputSetsDTO.builder()
                                                              .repoName(info.getRepoName())
                                                              .repoURL(info.getRepoURL())
                                                              .count(info.getCount())
                                                              .filePathsByOwningScope(info.getFilePathsByOwningScope())
                                                              .connectorRefs(info.getConnectorRefs())
                                                              .build())
                                                   .collect(Collectors.toList());
      long totalInputSets = serviceRepos.stream().mapToLong(InputSetRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_INPUT_SET_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalInputSets={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceResponse.getTotalRepos(),
          resourceRepos.size(), totalInputSets, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemoteInputSetsResponseDTO.builder()
                                         .totalInputSets(totalInputSets)
                                         .totalRepos(serviceResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error("[REMOTE_INPUT_SET_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  @Override
  public ResponseDTO<InputSetGitUpdateResponseDTO> updateGitMetadataForInputSet(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String inputSetIdentifier,
      GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_UPDATE).callerName(PIPELINE_SERVICE).build());
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier,
        pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    String inputSetAfterUpdate = pmsInputSetService.updateGitMetadata(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier, inputSetIdentifier,
        PMSUpdateGitDetailsParams.builder()
            .connectorRef(gitMetadataUpdateRequestInfo.getConnectorRef())
            .repoName(gitMetadataUpdateRequestInfo.getRepoName())
            .filePath(gitMetadataUpdateRequestInfo.getFilePath())
            .build(),
        scopeInfo, true);
    return ResponseDTO.newResponse(InputSetGitUpdateResponseDTO.builder().identifier(inputSetAfterUpdate).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PageResponse<InputSetListResponseDTO>> listInputSetsForProject(int page, int size,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, InputSetListTypePMS inputSetListType, String searchTerm,
      List<String> sort, GitEntityFindInfoDTO gitEntityBasicInfo,
      InputSetFilterPropertiesDto inputSetFilterPropertiesDto, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_LIST).callerName(PIPELINE_SERVICE).build());
    log.info(String.format("Get List of input sets for project %s, org %s, account %s",
        scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getAccountIdentifier()));
    Criteria criteria = PMSInputSetFilterHelper.listInputSetsForProjectCriteria(accountId, orgIdentifier,
        projectIdentifier, inputSetListType, searchTerm, false, inputSetFilterPropertiesDto, scopeInfo, true);
    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
    Page<InputSetEntity> inputSetEntities = pmsInputSetService.list(criteria, pageRequest, scopeInfo);

    Page<InputSetListResponseDTO> inputSetList =
        inputSetEntities.map(PMSInputSetElementMapper::toInputSetListResponseDTO);
    return ResponseDTO.newResponse(getNGPageResponse(inputSetList));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<YamlValidationListAPIResponse> validateInputSetYaml(
      @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_VALIDATE).callerName(PIPELINE_SERVICE).build());
    YamlValidationRequestDTOBuilder yamlValidationRequestDTOBuilder =
        YamlValidationRequestDTO.builder().yaml(yamlValidationRequestBody.getYaml());
    if (yamlValidationRequestBody.getGitYamlValidationRequestParams() != null) {
      yamlValidationRequestDTOBuilder.branch(yamlValidationRequestBody.getGitYamlValidationRequestParams().getBranch());
      yamlValidationRequestDTOBuilder.filePath(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getFilePath());
      yamlValidationRequestDTOBuilder.repoName(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getRepoName());
    }
    YamlValidationRequestDTO yamlValidationRequestDTO = yamlValidationRequestDTOBuilder.build();
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        pmsInputSetService.validateInputSetYaml(accountIdentifier, yamlValidationRequestDTO, true);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
  }

  @Override
  public ResponseDTO<ForceImportInputSetResponse> forceImportInputSet(
      String accountIdentifier, ForceImportInputSetRequestDTO requestDTO) {
    if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = requestDTO.getPipelineIdentifier() + "-" + requestDTO.getIdentifier();
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier,
          requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getPipelineIdentifier(),
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_FORCE_IMPORT).callerName(PIPELINE_SERVICE).build());
    ScopeInfo scopeInfo = pmsPipelineServiceHelper.getScopeInfo(
        accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), null);
    ForceImportInputSetYamlOperationDTO operationDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                           .branch(requestDTO.getBranch())
                                                           .repoName(requestDTO.getRepoName())
                                                           .connectorRef(requestDTO.getConnectorRef())
                                                           .filePath(requestDTO.getFilePath())
                                                           .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
                                                           .identifier(requestDTO.getIdentifier())
                                                           .pipelineIdentifier(requestDTO.getPipelineIdentifier())
                                                           .orgIdentifier(requestDTO.getOrgIdentifier())
                                                           .version(requestDTO.getVersion())
                                                           .projectIdentifier(requestDTO.getProjectIdentifier())
                                                           .scopeInfo(scopeInfo)
                                                           .build();

    ForceImportInputSetResponse response =
        pmsInputSetService.forceImportInputSet(accountIdentifier, operationDTO, scopeInfo);
    return ResponseDTO.newResponse(response);
  }

  @Override
  public ResponseDTO<PageResponse<InputSetListResponseDTO>> getBatchInputSetsMetadata(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, int page, int size, String searchTerm, ScopeInfo scopeInfo,
      @Valid BatchInputSetsAPIRequest pipelineIdentifiersRequest) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_LIST).callerName(PIPELINE_SERVICE).build());
    List<String> pipelineIdentifiers =
        pipelineIdentifiersRequest != null ? pipelineIdentifiersRequest.getPipelineIdentifiers() : null;
    boolean hasPipelineIdentifiers = !isEmpty(pipelineIdentifiers);

    ResourceScope resourceScope = ResourceScope.of(
        scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());

    // When pipeline identifiers are not provided, fetch input sets based on RBAC permissions
    if (!hasPipelineIdentifiers) {
      return getBatchInputSetsMetadataWithoutPipelineIds(
          accountId, orgIdentifier, projectIdentifier, page, size, searchTerm, scopeInfo, resourceScope);
    }

    // When pipeline identifiers are provided, check access for each pipeline
    pipelineIdentifiers.stream()
        .filter(EmptyPredicate::isNotEmpty)
        .forEach(pipelineIdentifier
            -> accessControlClient.checkForAccessOrThrow(
                resourceScope, Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW));

    BatchInputSetsRequestDTO requestDTO = BatchInputSetsRequestDTO.builder()
                                              .pipelineIdentifiers(pipelineIdentifiers)
                                              .page(page)
                                              .size(size)
                                              .searchTerm(searchTerm)
                                              .build();

    Page<InputSetEntity> inputSetEntities = pmsInputSetService.getBatchInputSetsMetadata(scopeInfo, requestDTO);
    Page<InputSetListResponseDTO> inputSetList =
        inputSetEntities.map(PMSInputSetElementMapper::toInputSetListResponseDTO);
    return ResponseDTO.newResponse(getNGPageResponse(inputSetList));
  }

  /**
   * Fetches input sets when no pipeline identifiers are provided.
   * If INPUT_SET RBAC FF is enabled, fetches all input sets and filters by input set permissions.
   * If FF is disabled, fetches all pipelines user has VIEW access to and gets their input sets.
   */
  private ResponseDTO<PageResponse<InputSetListResponseDTO>> getBatchInputSetsMetadataWithoutPipelineIds(
      String accountId, String orgIdentifier, String projectIdentifier, int page, int size, String searchTerm,
      ScopeInfo scopeInfo, ResourceScope resourceScope) {
    Page<InputSetEntity> inputSetEntities;

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      int maxFetchSize = 1000;
      Page<InputSetEntity> allInputSets =
          pmsInputSetService.getAllInputSetsMetadataForProject(scopeInfo, 0, maxFetchSize, searchTerm);

      List<InputSetEntity> permittedInputSets = filterInputSetsByPermission(allInputSets.getContent(), resourceScope);

      int start = page * size;
      int end = Math.min(start + size, permittedInputSets.size());

      List<InputSetEntity> pagedResults =
          start < permittedInputSets.size() ? permittedInputSets.subList(start, end) : Collections.emptyList();

      inputSetEntities = new PageImpl<>(pagedResults, PageRequest.of(page, size), permittedInputSets.size());
    } else {
      Criteria pipelineCriteria = pmsPipelineServiceHelper.formCriteria(
          accountId, orgIdentifier, projectIdentifier, null, null, false, null, null, scopeInfo, true);

      List<String> allPipelineIds = pipelineService.listAllIdentifiers(pipelineCriteria);

      if (isEmpty(allPipelineIds)) {
        return ResponseDTO.newResponse(getNGPageResponse(Page.empty()));
      }

      List<String> permittedPipelineIds =
          pipelineService.getPermittedPipelineIdentifier(accountId, orgIdentifier, projectIdentifier, allPipelineIds);

      if (isEmpty(permittedPipelineIds)) {
        return ResponseDTO.newResponse(getNGPageResponse(Page.empty()));
      }

      BatchInputSetsRequestDTO requestDTO = BatchInputSetsRequestDTO.builder()
                                                .pipelineIdentifiers(permittedPipelineIds)
                                                .page(page)
                                                .size(size)
                                                .searchTerm(searchTerm)
                                                .build();

      inputSetEntities = pmsInputSetService.getBatchInputSetsMetadata(scopeInfo, requestDTO);
    }

    Page<InputSetListResponseDTO> inputSetList =
        inputSetEntities.map(PMSInputSetElementMapper::toInputSetListResponseDTO);
    return ResponseDTO.newResponse(getNGPageResponse(inputSetList));
  }

  private List<InputSetEntity> filterInputSetsByPermission(
      List<InputSetEntity> inputSetEntities, ResourceScope resourceScope) {
    if (isEmpty(inputSetEntities)) {
      return Collections.emptyList();
    }

    List<PermissionCheckDTO> permissionChecks =
        inputSetEntities.stream()
            .map(inputSet
                -> PermissionCheckDTO.builder()
                       .permission(InputSetRbacPermissions.INPUTSET_VIEW)
                       .resourceIdentifier(inputSet.getPipelineIdentifier() + "-" + inputSet.getIdentifier())
                       .resourceScope(resourceScope)
                       .resourceType(INPUT_SET)
                       .build())
            .collect(Collectors.toList());

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionChecks);
    if (accessCheckResponse == null || isEmpty(accessCheckResponse.getAccessControlList())) {
      return Collections.emptyList();
    }

    java.util.Set<String> permittedIdentifiers = accessCheckResponse.getAccessControlList()
                                                     .stream()
                                                     .filter(AccessControlDTO::isPermitted)
                                                     .map(AccessControlDTO::getResourceIdentifier)
                                                     .collect(java.util.stream.Collectors.toSet());

    return inputSetEntities.stream()
        .filter(inputSet
            -> permittedIdentifiers.contains(inputSet.getPipelineIdentifier() + "-" + inputSet.getIdentifier()))
        .collect(Collectors.toList());
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<BulkInputSetsAPIResponse> getBulkInputSets(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, ScopeInfo scopeInfo,
      @NotNull @Valid BulkInputSetsAPIRequest bulkInputSetsAPIRequest) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.INPUTSET_LIST).callerName(PIPELINE_SERVICE).build());
    BulkInputSetsRequestDTO serviceRequest =
        BulkInputSetsRequestDTO.builder().inputSetIdentifiers(bulkInputSetsAPIRequest.getInputSetIdentifiers()).build();

    BulkInputSetsResponseDTO serviceResponse =
        pmsInputSetService.getBulkInputSets(scopeInfo, pipelineIdentifier, serviceRequest);

    BulkInputSetsAPIResponse apiResponse =
        BulkInputSetsAPIResponse.builder().inputSets(serviceResponse.getInputSets()).build();

    return ResponseDTO.newResponse(apiResponse);
  }
}
