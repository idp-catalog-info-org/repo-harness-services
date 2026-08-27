/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template.service;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.template.utils.PipelineTemplateUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PipelineRefreshServiceImpl implements PipelineRefreshService {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;

  @Override
  public boolean refreshTemplateInputsInPipeline(String accountId, String orgId, String projectId,
      String pipelineIdentifier, String loadFromCache, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId,
        pipelineIdentifier, pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE, scopeInfo, isParentIdQueryingEnabled);
    RefreshResponseDTO refreshResponseDTO = pmsPipelineTemplateHelper.getRefreshedYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    if (refreshResponseDTO != null) {
      pmsPipelineService.validateAndUpdatePipeline(pipelineEntity.withYaml(refreshResponseDTO.getRefreshedYaml()),
          ChangeType.MODIFY, true, false, scopeInfo, isParentIdQueryingEnabled);
    }
    return true;
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInPipeline(String accountId, String orgId,
      String projectId, String pipelineIdentifier, String loadFromCache, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    return validateTemplateInputsInPipeline(
        accountId, orgId, projectId, pipelineIdentifier, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInPipeline(String accountId, String orgId,
      String projectId, String pipelineIdentifier, String loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountId, orgId, projectId, pipelineIdentifier, loadFromCache, scopeInfo, isParentIdQueryingEnabled);

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(
            accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    if (!validateTemplateInputsResponse.isValidYaml()) {
      validateTemplateInputsResponse.getErrorNodeSummary().setNodeInfo(
          NodeInfo.builder().identifier(pipelineIdentifier).name(pipelineEntity.getName()).build());
      return validateTemplateInputsResponse;
    }
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  private PipelineEntity getPipelineEntity(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier, false, false, false,
            PipelineTemplateUtils.parseLoadFromCache(loadFromCache), scopeInfo, isParentIdQueryingEnabled);
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given id: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return optionalPipelineEntity.get();
  }

  @Override
  public YamlDiffResponseDTO getYamlDiff(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String loadFromCache, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE, scopeInfo, isParentIdQueryingEnabled);

    String pipelineYaml = pipelineEntity.getYaml();
    RefreshResponseDTO refreshResponseDTO = pmsPipelineTemplateHelper.getRefreshedYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);
    return YamlDiffResponseDTO.builder()
        .originalYaml(pipelineYaml)
        .refreshedYaml(refreshResponseDTO.getRefreshedYaml())
        .build();
  }

  @Override
  public boolean recursivelyRefreshAllTemplateInputsInPipeline(String accountId, String orgId, String projectId,
      String pipelineIdentifier, GitEntityUpdateInfoDTO gitEntityBasicInfo, String loadFromCache, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId,
        pipelineIdentifier, pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE, scopeInfo, isParentIdQueryingEnabled);
    YamlFullRefreshResponseDTO refreshResponse = pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, loadFromCache);

    if (refreshResponse != null && refreshResponse.isShouldRefreshYaml()) {
      // TODO: add schema validation support
      boolean throwExceptionIfGovernanceFails = !HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion());
      pmsPipelineService.validateAndUpdatePipeline(pipelineEntity.withYaml(refreshResponse.getRefreshedYaml()),
          ChangeType.MODIFY, throwExceptionIfGovernanceFails, false, scopeInfo, isParentIdQueryingEnabled);
    }
    return true;
  }

  @Override
  public boolean recursivelyRefreshAllTemplateInputsInPipelineAndUpdateReconcileEvent(String accountId, String orgId,
      String projectId, String pipelineIdentifier, String bulkReconcileUUID, GitEntityUpdateInfoDTO gitEntityBasicInfo,
      String loadFromCache, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity = getPipelineEntity(
        accountId, orgId, projectId, pipelineIdentifier, BOOLEAN_FALSE_VALUE, scopeInfo, isParentIdQueryingEnabled);
    YamlFullRefreshResponseDTO refreshResponse = pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(
        accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity, bulkReconcileUUID, loadFromCache);
    if (refreshResponse != null && refreshResponse.isShouldRefreshYaml()) {
      pmsPipelineService.validateAndUpdatePipeline(pipelineEntity.withYaml(refreshResponse.getRefreshedYaml()),
          ChangeType.MODIFY, true, false, scopeInfo, isParentIdQueryingEnabled);
    }
    return true;
  }
}
