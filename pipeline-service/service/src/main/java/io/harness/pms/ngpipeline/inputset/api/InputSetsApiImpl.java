/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMSImpl;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.rbac.InputSetRbacPermissions;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.spec.server.pipeline.v1.InputSetsApi;
import io.harness.spec.server.pipeline.v1.model.GitImportInfo;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetUpdateRequestBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class InputSetsApiImpl implements InputSetsApi {
  private final PMSInputSetService pmsInputSetService;
  private final InputSetsApiUtils inputSetsApiUtils;
  private final ValidateAndMergeHelper validateAndMergeHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final InputSetResourcePMSImpl inputSetResourcePMSImpl;
  private final AccessControlClient accessControlClient;
  private static final String INPUT_SET = "INPUT_SET";

  @Override
  @Timed
  @ResponseMetered
  public Response createInputSet(InputSetCreateRequestBody requestBody, @ResourceIdentifier String pipeline,
      @OrgIdentifier String org, @ProjectIdentifier String project, @AccountIdentifier String account) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(account, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(account, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    if (requestBody == null) {
      throw new InvalidRequestException("Input Set create request body must not be null.");
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitCreateDetails(requestBody.getGitDetails()));
    InputSetEntity entity =
        PMSInputSetElementMapper.toInputSetEntityFromVersion(InputSetsApiUtils.mapCreateToRequestInfoDTO(requestBody),
            account, org, project, pipeline, requestBody.getVersion());
    log.info(String.format("Create input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        entity.getIdentifier(), pipeline, project, org, account));
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    InputSetResponseBody inputSetResponse = inputSetsApiUtils.getInputSetResponse(
        pmsInputSetService.create(entity, true, scopeInfo), false, scopeInfo, true);
    return Response.status(201).entity(inputSetResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  @Timed
  @ResponseMetered
  public Response deleteInputSet(@OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account) {
    log.info(String.format("Deleting input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, project, org, account));
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);

    pmsInputSetService.delete(scopeInfo, pipeline, inputSet, null, true);
    return Response.status(204).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  public Response getInputSet(@OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, String branchGitX,
      String parentEntityConnectorRef, String parentEntityRepoName, Boolean loadFromFallbackBranch,
      Boolean isHarnessCodeRepo, String loadFromCache) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    if (null == loadFromFallbackBranch) {
      loadFromFallbackBranch = false;
    }
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branchGitX)
                                                 .parentEntityConnectorRef(parentEntityConnectorRef)
                                                 .parentEntityRepoName(parentEntityRepoName)
                                                 .isHarnessCodeRepo(isHarnessCodeRepo)
                                                 .build());
    log.info(String.format("Retrieving input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier(),
        scopeInfo.getAccountIdentifier()));
    Optional<InputSetEntity> optionalInputSetEntity = Optional.empty();
    try {
      optionalInputSetEntity = pmsInputSetService.get(scopeInfo, pipeline, inputSet, false, null, null, true,
          loadFromFallbackBranch, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), true);
    } catch (InvalidInputSetException e) {
      return Response.ok()
          .entity(inputSetsApiUtils.getInputSetResponseWithError(
              e.getInputSetEntity(), (InputSetErrorWrapperDTOPMS) e.getMetadata(), scopeInfo, true))
          .build();
    }
    if (!optionalInputSetEntity.isPresent()) {
      throw new EntityNotFoundException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSet));
    }
    InputSetResponseBody inputSetResponse =
        inputSetsApiUtils.getInputSetResponse(optionalInputSetEntity.get(), false, scopeInfo, true);
    return Response.ok().entity(inputSetResponse).build();
  }

  @Override
  public Response importInputSetFromGit(@NotNull @ResourceIdentifier String pipeline, @OrgIdentifier String org,
      @ProjectIdentifier String project, String inputSet, @Valid InputSetImportRequestBody body,
      @AccountIdentifier String harnessAccount) {
    if (pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)) {
      String inputSetWithPipelineIdentifier = pipeline + "-" + inputSet;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
          Resource.of(INPUT_SET, inputSetWithPipelineIdentifier), InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    } else {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(harnessAccount, org, project, pipeline,
          pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }
    GitImportInfo gitImportInfo = body.getGitImportInfo();
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(gitImportInfo.getBranchName())
                                                 .connectorRef(gitImportInfo.getConnectorRef())
                                                 .filePath(gitImportInfo.getFilePath())
                                                 .repoName(gitImportInfo.getRepoName())
                                                 .isHarnessCodeRepo(gitImportInfo.isIsHarnessCodeRepo())
                                                 .build());
    InputSetImportRequestDTO inputSetImportRequestDTO =
        InputSetImportRequestDTO.builder()
            .inputSetName(body.getInputSetImportRequest().getInputSetName())
            .inputSetDescription(body.getInputSetImportRequest().getInputSetDescription())
            .version(body.getInputSetImportRequest().getInputSetYamlVersion())
            .build();
    InputSetEntity inputSetEntity = pmsInputSetService.importInputSetFromRemote(harnessAccount, org, project, pipeline,
        inputSet, inputSetImportRequestDTO, Boolean.TRUE.equals(body.getGitImportInfo().isIsForceImport()),
        scopeResolutionHelper.getScopeInfo(harnessAccount, org, project));
    InputSetMoveConfigResponseBody inputSetMoveConfigResponseBody = new InputSetMoveConfigResponseBody();
    inputSetMoveConfigResponseBody.setInputSetIdentifier(inputSetEntity.getIdentifier());
    return Response.ok().entity(inputSetMoveConfigResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  public Response listInputSets(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, Integer page, Integer limit,
      String searchTerm, String sort, String order) {
    log.info(String.format(
        "Get List of input sets for pipeline %s in project %s, org %s, account %s", pipeline, project, org, account));
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(scopeInfo.getAccountIdentifier(),
        scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), pipeline, InputSetListTypePMS.INPUT_SET,
        searchTerm, false, scopeInfo.getUniqueId(), true);
    Pageable pageRequest = PageUtils.getPageRequest(page, limit, PipelinesApiUtils.getSorting(sort, order),
        Sort.by(Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
    Page<InputSetEntity> inputSetEntities = pmsInputSetService.list(criteria, pageRequest, scopeInfo);

    Page<InputSetResponseBody> inputSetList = inputSetEntities.map(
        inputSetEntity -> inputSetsApiUtils.getInputSetResponse(inputSetEntity, true, scopeInfo, true));

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, inputSetList.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(inputSetList.getContent()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response mergedInputSets(@NotNull @ResourceIdentifier String pipeline, @OrgIdentifier String org,
      @ProjectIdentifier String project, @Valid MergeInputSetRequestBody body, @AccountIdentifier String harnessAccount,
      String loadFromCache, String pipelineRepoId, String pipelineBranch, String branchName,
      String parentEntityConnectorRef, String parentEntityRepoName, String inputSetBranchName) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branchName)
                                                 .parentEntityConnectorRef(parentEntityConnectorRef)
                                                 .parentEntityRepoName(parentEntityRepoName)
                                                 .build());
    List<String> inputSetReferences = body.getInputSetReferences();
    String mergedYaml = null;
    MergeInputSetResponseBody mergeInputSetResponseBody = new MergeInputSetResponseBody();
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    try {
      mergedYaml = validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(
          scopeInfo, pipeline, inputSetReferences, pipelineBranch, pipelineRepoId, body.getStageIdentifiers(),
          body.getLastYamlToMerge(), GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), true,
          inputSetBranchName);
    } catch (InvalidInputSetException e) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = (InputSetErrorWrapperDTOPMS) e.getMetadata();
      mergeInputSetResponseBody.setIsErrorResponse(true);
      mergeInputSetResponseBody.setInputsetErrorWrapper(inputSetsApiUtils.getInputSetErrorWrapper(errorWrapperDTO));
      Response.ok().entity(mergeInputSetResponseBody).build();
    }
    String fullYaml = "";
    if (body.isWithMergedPipelineYaml()) {
      fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(harnessAccount, org, project, pipeline, mergedYaml,
          pipelineBranch, pipelineRepoId, body.getStageIdentifiers(),
          GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo);
    }
    mergeInputSetResponseBody.setIsErrorResponse(false);
    mergeInputSetResponseBody.setInputsYamlMerged(mergedYaml);
    mergeInputSetResponseBody.setMergedPipelineYaml(fullYaml);
    return Response.ok().entity(mergeInputSetResponseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response updateInputSet(InputSetUpdateRequestBody requestBody, @ResourceIdentifier String pipeline,
      @OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @AccountIdentifier String account) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(account, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(account, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    if (requestBody == null) {
      throw new InvalidRequestException("Input Set update request body must not be null.");
    }
    if (!Objects.equals(inputSet, requestBody.getIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Input Set identifier in Request Body to be [%s], but was [%s]", inputSet,
              requestBody.getIdentifier()));
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, project, org, account));
    InputSetEntity entity =
        PMSInputSetElementMapper.toInputSetEntityFromVersion(InputSetsApiUtils.mapUpdateToRequestInfoDTO(requestBody),
            account, org, project, pipeline, requestBody.getVersion());
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    InputSetEntity updatedEntity = pmsInputSetService.update(ChangeType.MODIFY, entity, true, scopeInfo);
    InputSetResponseBody inputSetResponse =
        inputSetsApiUtils.getInputSetResponse(updatedEntity, false, scopeInfo, true);
    return Response.ok().entity(inputSetResponse).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response createOverlayInputSet(OverlayInputSetCreateRequestBody requestBody,
      @ResourceIdentifier String pipeline, @OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String harnessAccount) {
    if (requestBody == null) {
      throw new InvalidRequestException("Overlay Input Set create request body must not be null.");
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitCreateDetails(requestBody.getGitDetails()));
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    String inputSetVersion = isEmpty(requestBody.getVersion()) ? HarnessYamlVersion.V0 : requestBody.getVersion();
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.createOverlayInputSet(harnessAccount, org, project, pipeline,
            GitEntityCreateInfoDTO.builder().build(), inputSetVersion, requestBody.getOverlayInputSetYaml(), scopeInfo);
    return Response.status(201).entity(inputSetsApiUtils.toOverlayInputSetResponseBody(responseDTO.getData())).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getOverlayInputSet(@OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @ResourceIdentifier String pipeline, @AccountIdentifier String harnessAccount, String branchName,
      String pipelineBranch, String pipelineRepoId, String parentEntityConnectorRef, String parentEntityRepoName,
      Boolean isHarnessCodeRepo, Boolean loadFromFallbackBranch, String loadFromCache) {
    if (loadFromFallbackBranch == null) {
      loadFromFallbackBranch = false;
    }
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branchName)
                                                 .parentEntityConnectorRef(parentEntityConnectorRef)
                                                 .parentEntityRepoName(parentEntityRepoName)
                                                 .isHarnessCodeRepo(isHarnessCodeRepo)
                                                 .build());
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    GitEntityFindInfoDTO gitEntityFindInfoDTO = GitEntityFindInfoDTO.builder()
                                                    .branch(branchName)
                                                    .parentEntityConnectorRef(parentEntityConnectorRef)
                                                    .parentEntityRepoName(parentEntityRepoName)
                                                    .build();
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.getOverlayInputSet(inputSet, harnessAccount, org, project, pipeline, pipelineBranch,
            pipelineRepoId, loadFromFallbackBranch, gitEntityFindInfoDTO, loadFromCache, scopeInfo);
    return Response.ok().entity(inputSetsApiUtils.toOverlayInputSetResponseBody(responseDTO.getData())).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response updateOverlayInputSet(OverlayInputSetUpdateRequestBody requestBody,
      @ResourceIdentifier String pipeline, @OrgIdentifier String org, @ProjectIdentifier String project,
      String inputSet, @AccountIdentifier String harnessAccount, String ifMatch) {
    if (requestBody == null) {
      throw new InvalidRequestException("Overlay Input Set update request body must not be null.");
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    String inputSetVersion = isEmpty(requestBody.getVersion()) ? HarnessYamlVersion.V0 : requestBody.getVersion();
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.updateOverlayInputSet(ifMatch, inputSet, harnessAccount, org, project, pipeline,
            GitEntityUpdateInfoDTO.builder().build(), inputSetVersion, requestBody.getOverlayInputSetYaml());
    return Response.ok().entity(inputSetsApiUtils.toOverlayInputSetResponseBody(responseDTO.getData())).build();
  }

  @Override
  public Response inputSetsMoveConfig(
      String org, String project, String inputSet, @Valid InputSetMoveConfigRequestBody requestBody, String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("InputSet MoveConfig request body must not be null.");
    }
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(account, org, project, null,
        pmsFeatureFlagService.isEnabled(account, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    if (!Objects.equals(inputSet, requestBody.getInputSetIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected InputSet identifier in Request Body to be [%s], but was [%s]", inputSet,
              requestBody.getInputSetIdentifier()));
    }
    log.info(
        String.format("Move Config for InputSet of move type %s with identifier %s in project %s, org %s, account %s",
            requestBody.getMoveConfigOperationType().toString(), inputSet, project, org, account));
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(account, org, project);
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitMoveDetails(requestBody.getGitDetails()));
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperation = InputSetsApiUtils.buildMoveConfigOperationDTO(
        requestBody.getGitDetails(), requestBody.getMoveConfigOperationType(), requestBody.getPipelineIdentifier());
    InputSetEntity movedInputSetEntity =
        pmsInputSetService.moveConfig(account, org, project, inputSet, inputSetMoveConfigOperation, scopeInfo);
    InputSetMoveConfigResponseBody responseBody = new InputSetMoveConfigResponseBody();
    responseBody.setInputSetIdentifier(movedInputSetEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  public Response updateInputSetGitMetadata(@ResourceIdentifier @NotNull String pipeline, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String inputSet, @Valid GitMetadataUpdateRequestBody body,
      @AccountIdentifier String harnessAccount) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(harnessAccount, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    String inputSetAfterUpdate = pmsInputSetService.updateGitMetadata(harnessAccount, org, project, pipeline, inputSet,
        PMSUpdateGitDetailsParams.builder()
            .connectorRef(body.getConnectorRef())
            .repoName(body.getRepoName())
            .filePath(body.getFilePath())
            .build(),
        scopeResolutionHelper.getScopeInfo(harnessAccount, org, project), true);

    GitMetadataUpdateResponseBody gitMetadataUpdateResponseBody = new GitMetadataUpdateResponseBody();
    gitMetadataUpdateResponseBody.setEntityIdentifier(inputSetAfterUpdate);
    return Response.ok().entity(gitMetadataUpdateResponseBody).build();
  }
}
