/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.bulkReconciliation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.bulkReconciliation.BulkReconciliationErrorMessage;
import io.harness.beans.bulkReconciliation.BulkReconciliationRequestBody;
import io.harness.bulkReconciliation.ReferenceEntityType;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmCreatePRResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.refresh.ReferenceEntityDetails;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PipelineResource;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import java.util.Arrays;
import java.util.Collections;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineReconciliationHandler implements Runnable {
  String loadFromCache;
  private final String accountIdentifier;
  private final String orgIdentifier;
  private final String projectIdentifier;
  private final String parentUniqueId;
  private final String pipelineIdentifier;
  private final GitEntityUpdateInfoDTO gitEntityBasicInfo;
  private final PipelineRefreshService pipelineRefreshService;
  private final String bulkReconciliationUUID;
  private final TemplateResourceClient templateResourceClient;
  private final Principal principal;
  private final PipelineResource pipelineResource;
  private final ReferenceEntityDetails referenceEntityDetails;
  private final SCMGitSyncHelper scmGitSyncHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final String ERROR_MESSAGE = "Pipeline Reconciliation Failed for [%s] bulkReconciliationUUID [%s]";
  private final String ACCESS_ERROR_MESSAGE =
      "Pipeline reconciliation failed because the user does not have edit permissions for the template";
  private final String ACCESS_HINT_MESSAGE = "Please ensure user have edit permissions";
  private final String HINT = "Please ensure template details provided are correct";
  private final String PR_TITLE = "Reconciliation for the specified template identifier %s";
  private final String PIPELINE_UPDATE_FAILED = "Pipeline update failed";
  private final String PR_CREATION_FAILED = "Failed to create the pull request.";

  @Builder
  public PipelineReconciliationHandler(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, GitEntityUpdateInfoDTO gitEntityBasicInfo,
      PipelineRefreshService pipelineRefreshService, String bulkReconciliationUUID,
      TemplateResourceClient templateResourceClient, Principal principal, PipelineResource pipelineResource,
      ReferenceEntityDetails referenceEntityDetails, SCMGitSyncHelper scmGitSyncHelper,
      PmsFeatureFlagService pmsFeatureFlagService, PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper,
      String parentUniqueId, ScopeResolutionHelper scopeResolutionHelper) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.parentUniqueId = parentUniqueId;
    this.pipelineIdentifier = pipelineIdentifier;
    this.gitEntityBasicInfo = gitEntityBasicInfo;
    this.pipelineRefreshService = pipelineRefreshService;
    this.bulkReconciliationUUID = bulkReconciliationUUID;
    this.templateResourceClient = templateResourceClient;
    this.principal = principal;
    this.pipelineResource = pipelineResource;
    this.referenceEntityDetails = referenceEntityDetails;
    this.scmGitSyncHelper = scmGitSyncHelper;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.pipelineSplitPermissionsHelper = pipelineSplitPermissionsHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public void run() {
    try {
      // Set context for principal and source
      setSecurityContext();

      checkAccessPermissions(
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT));

      if (shouldPerformReconciliationCheck()) {
        return;
      }

      if (isRemoteReferenceEntity()) {
        handleRemoteReferenceEntity();
      } else {
        refreshPipelineTemplatesAndUpdateStatus();
      }
    } catch (NGAccessDeniedException ngAccessDeniedException) {
      handleAccessDeniedException(ngAccessDeniedException);
    } catch (Exception exception) {
      handleGeneralException(exception);
    }
  }

  private void setSecurityContext() {
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }

  private void checkAccessPermissions(boolean isPipelinePermissionSplitEnabled) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier, isPipelinePermissionSplitEnabled, PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
  }

  private boolean isRemoteReferenceEntity() {
    return referenceEntityDetails != null && StoreType.REMOTE.equals(referenceEntityDetails.getType());
  }

  private void handleRemoteReferenceEntity() {
    // Prepare the git entity details and scope information
    GitEntityFindInfoDTO gitEntityFindInfoDTO = createGitEntityFindInfo();
    updateReconciliationStatus(ValidationStatus.IN_PROGRESS.name(),
        BulkReconciliationRequestBody.builder()
            .repo(gitEntityFindInfoDTO.getRepoName())
            .branch(gitEntityFindInfoDTO.getBranch())
            .build());

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .branch(gitEntityFindInfoDTO.getBranch())
                                      .storeType(StoreType.REMOTE)
                                      .repoName(gitEntityFindInfoDTO.getRepoName())
                                      .build();
    ResponseDTO<PMSPipelineResponseDTO> pipelineResponse;
    YamlDiffResponseDTO yamlDiffResponseDTO;
    try (EntityGitDetailsGuard ignored = new EntityGitDetailsGuard(gitEntityInfo)) {
      // Fetch pipeline response and YAML diff
      log.info("Fetch Pipeline from git branch {} and repo {}", gitEntityInfo.getBranch(), gitEntityInfo.getRepoName());
      pipelineResponse = fetchPipelineResponse(gitEntityFindInfoDTO);
      yamlDiffResponseDTO = fetchYamlDiffResponse();
    }

    if (null == pipelineResponse.getData() || null == yamlDiffResponseDTO) {
      updateReconciliationStatus(ValidationStatus.FAILURE.name(), HINT, HINT, HINT);
      return;
    }

    // Generate source branch and update git entity context
    String sourceBranch = generateSourceBranchName(referenceEntityDetails.getIdentifier());
    updateGitEntityContext(
        pipelineResponse.getData().getGitDetails(), pipelineResponse.getData().getConnectorRef(), sourceBranch);

    // Build git entity update info and update the pipeline
    GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO = buildGitEntityUpdateInfo(
        pipelineResponse.getData().getGitDetails(), pipelineResponse.getData().getConnectorRef(), sourceBranch);
    ResponseDTO<PipelineSaveResponse> responseResponseDTO = updatePipeline(gitEntityUpdateInfoDTO, yamlDiffResponseDTO);
    if (null == responseResponseDTO.getData()) {
      updateReconciliationStatus(ValidationStatus.FAILURE.name(), PIPELINE_UPDATE_FAILED, HINT, PIPELINE_UPDATE_FAILED);
      return;
    }

    // Create a pull request
    ScmCreatePRResponse scmCreatePRResponse =
        createPullRequest(gitEntityUpdateInfoDTO, pipelineResponse.getData().getConnectorRef());
    if (scmCreatePRResponse == null || scmCreatePRResponse.getPrNumber() == 0) {
      updateReconciliationStatus(ValidationStatus.FAILURE.name(), PR_CREATION_FAILED, HINT, PR_CREATION_FAILED);
      return;
    }
    updateReconciliationStatus(ValidationStatus.PR_PENDING.name(),
        BulkReconciliationRequestBody.builder()
            .branch(referenceEntityDetails.getBranch())
            .repo(referenceEntityDetails.getRepo())
            .prLink(scmCreatePRResponse.getPrUrl())
            .build());
  }

  private GitEntityFindInfoDTO createGitEntityFindInfo() {
    return GitEntityFindInfoDTO.builder()
        .branch(referenceEntityDetails.getBranch())
        .repoName(referenceEntityDetails.getRepo())
        .build();
  }

  private ResponseDTO<PMSPipelineResponseDTO> fetchPipelineResponse(GitEntityFindInfoDTO gitEntityFindInfoDTO) {
    ScopeInfo scopeInfo = null;
    if (isNotEmpty(parentUniqueId)) {
      scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId);
    }
    return pipelineResource.getPipelineByIdentifier(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, gitEntityFindInfoDTO, false, false, false, "false", scopeInfo);
  }

  private YamlDiffResponseDTO fetchYamlDiffResponse() {
    ScopeInfo scopeInfo = null;
    if (isNotEmpty(parentUniqueId)) {
      scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId);
    }
    return pipelineRefreshService.getYamlDiff(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, "false", scopeInfo);
  }

  private ResponseDTO<PipelineSaveResponse> updatePipeline(
      GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO, YamlDiffResponseDTO yamlDiffResponseDTO) {
    ScopeInfo scopeInfo = null;
    if (isNotEmpty(parentUniqueId)) {
      scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId);
    }
    return pipelineResource.updatePipelineV2("", accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, referenceEntityDetails.getName(), "", false, gitEntityUpdateInfoDTO, null,
        yamlDiffResponseDTO.getRefreshedYaml(), false, scopeInfo);
  }

  private void refreshPipelineTemplatesAndUpdateStatus() {
    updateReconciliationStatus(ValidationStatus.IN_PROGRESS.name(), BulkReconciliationRequestBody.builder().build());
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId);
    pipelineRefreshService.recursivelyRefreshAllTemplateInputsInPipelineAndUpdateReconcileEvent(accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineIdentifier, bulkReconciliationUUID, gitEntityBasicInfo, loadFromCache,
        scopeInfo);
    updateReconciliationStatus(ValidationStatus.SUCCESS.name(), BulkReconciliationRequestBody.builder().build());
  }

  private void handleAccessDeniedException(NGAccessDeniedException ngAccessDeniedException) {
    try {
      String errorMessage = String.format(ACCESS_ERROR_MESSAGE, ngAccessDeniedException.getMessage());
      updateReconciliationStatus(
          ValidationStatus.FAILURE.name(), ngAccessDeniedException.getMessage(), ACCESS_HINT_MESSAGE, errorMessage);
      log.error(errorMessage);
    } catch (Exception e) {
      throw new InternalServerErrorException(createErrorMessage(e), e);
    }
  }

  private void handleGeneralException(Exception exception) {
    try {
      String errorMessage = createErrorMessage(exception);
      log.error(errorMessage);
      updateReconciliationStatus(ValidationStatus.FAILURE.name(), errorMessage, HINT, exception.getMessage());
    } catch (Exception e) {
      throw new InternalServerErrorException(createErrorMessage(exception), exception);
    }
  }

  private String createErrorMessage(Exception exception) {
    return String.format(ERROR_MESSAGE, pipelineIdentifier, bulkReconciliationUUID, exception.getMessage());
  }

  private void updateReconciliationStatus(String status, String errorMessage, String hint, String explanation) {
    BulkReconciliationErrorMessage bulkReconciliationErrorMessage =
        BulkReconciliationErrorMessage.builder().message(errorMessage).hint(hint).explanation(explanation).build();

    NGRestUtils.getResponse(templateResourceClient.updateBulkReconcileStatus(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier, ReferenceEntityType.PIPELINE.name(), bulkReconciliationUUID, status,
        BulkReconciliationRequestBody.builder()
            .bulkReconciliationErrorMessage(bulkReconciliationErrorMessage)
            .build()));
  }

  private void updateReconciliationStatus(String status, BulkReconciliationRequestBody body) {
    NGRestUtils.getResponse(
        templateResourceClient.updateBulkReconcileStatus(accountIdentifier, orgIdentifier, projectIdentifier,
            pipelineIdentifier, ReferenceEntityType.PIPELINE.name(), bulkReconciliationUUID, status, body));
  }

  ScmCreatePRResponse createPullRequest(GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO, String connectorRef) {
    String prTitle = String.format(PR_TITLE, referenceEntityDetails.getIdentifier());
    return scmGitSyncHelper.createPullRequest(
        Scope.of(referenceEntityDetails.getAccountIdentifier(), referenceEntityDetails.getOrgIdentifier(),
            referenceEntityDetails.getProjectIdentifier()),
        referenceEntityDetails.getRepo(), connectorRef, gitEntityUpdateInfoDTO.getBranch(),
        gitEntityUpdateInfoDTO.getBaseBranch(), prTitle, Collections.emptyMap());
  }

  private String generateSourceBranchName(String pipelineIdentifier) {
    return pipelineIdentifier + "_" + System.currentTimeMillis();
  }

  GitEntityUpdateInfoDTO buildGitEntityUpdateInfo(
      EntityGitDetails gitDetails, String connectorRef, String sourceBranch) {
    return GitEntityUpdateInfoDTO.builder()
        .baseBranch(referenceEntityDetails.getBranch())
        .branch(sourceBranch)
        .isNewBranch(true)
        .filePath(gitDetails.getFilePath())
        .connectorRef(connectorRef)
        .commitMsg(referenceEntityDetails.getCommitMessage())
        .lastCommitId(gitDetails.getCommitId())
        .lastObjectId(gitDetails.getObjectId())
        .build();
  }

  void updateGitEntityContext(EntityGitDetails gitDetails, String connectorRef, String sourceBranch) {
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .baseBranch(referenceEntityDetails.getBranch())
                                      .branch(sourceBranch)
                                      .isNewBranch(true)
                                      .connectorRef(connectorRef)
                                      .filePath(gitDetails.getFilePath())
                                      .commitMsg(referenceEntityDetails.getCommitMessage())
                                      .lastCommitId(gitDetails.getCommitId())
                                      .lastObjectId(gitDetails.getObjectId())
                                      .build();

    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
  }

  boolean shouldPerformReconciliationCheck() {
    if (referenceEntityDetails.isCheckForReconciliation()) {
      try {
        ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId);
        ValidateTemplateInputsResponseDTO validateTemplateInputsInPipeline =
            pipelineRefreshService.validateTemplateInputsInPipeline(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, "false", scopeInfo);
        if (!validateTemplateInputsInPipeline.isValidYaml()) {
          updateReconciliationStatus(
              ValidationStatus.OUT_OF_SYNC.name(), BulkReconciliationRequestBody.builder().build());
        }
      } catch (Exception e) {
        log.info("Error while validating template inputs in pipeline", e);
        return true;
      }
      return true;
    }
    return false;
  }
}