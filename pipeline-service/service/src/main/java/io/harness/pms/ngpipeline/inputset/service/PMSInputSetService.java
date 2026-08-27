/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.pms.inputset.ForceImportInputSetResponse;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.InputSetRemoteRepoListResponse;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.ForceImportInputSetYamlOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface PMSInputSetService {
  InputSetEntity create(InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo);

  InputSetEntity create(InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabledForPipeline, boolean isParentIdQueryingEnabled);

  Optional<InputSetEntity> get(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier, boolean deleted,
      String pipelineBranch, String pipelineRepoID, boolean hasNewYamlStructure, boolean loadFromFallbackBranch,
      boolean loadFromCache, boolean isParentIdQueryingEnabled);

  Optional<InputSetEntity> getWithoutValidations(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier,
      boolean deleted, boolean loadFromFallbackBranch, boolean loadFromCache, boolean isParentIdQueryingEnabled);

  Optional<InputSetEntity> getMetadataWithoutValidations(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String identifier, boolean deleted,
      boolean loadFromFallbackBranch, boolean getMetadata, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetEntity getMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean getMetadata, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  // pipeline branch and repo ID are needed for old git sync
  @Deprecated InputSetEntity update(ChangeType changeType, InputSetEntity inputSetEntity, boolean hasNewYamlStructure);

  InputSetEntity update(
      ChangeType changeType, InputSetEntity inputSetEntity, boolean hasNewYamlStructure, ScopeInfo scopeInfo);

  InputSetEntity syncInputSetWithGit(EntityDetailProtoDTO entityDetail);

  boolean switchValidationFlag(InputSetEntity entity, boolean isInvalid, boolean isParentIdQueryingEnabled);

  boolean markGitSyncedInputSetInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, String invalidYaml);

  boolean delete(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier, Long version,
      boolean isParentIdQueryingEnabled);

  Page<InputSetEntity> list(Criteria criteria, Pageable pageable, ScopeInfo scopeInfo);

  List<InputSetEntity> list(Criteria criteria);

  void deleteInputSetsOnPipelineDeletion(PipelineEntity pipelineEntity);

  InputSetEntity updateGitFilePath(InputSetEntity inputSetEntity, String newFilePath);

  boolean checkForInputSetsForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetEntity importInputSetFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, InputSetImportRequestDTO inputSetImportRequestDTO,
      boolean isForceImport, ScopeInfo scopeInfo);

  InputSetEntity moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String inputSetIdentifier, InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO, ScopeInfo scopeInfo);

  PMSInputSetListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetRemoteRepoListResponse getRemoteRepoListForAGivenScope(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit);

  String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  // getSanitizedInputsFromInputSetV1 returns the list of the Spec of all the given V1 InputSets to be merged
  List<JsonNode> getSanitizedInputsFromInputSetV1(List<JsonNode> inputSetJsonNodeList);

  List<YamlValidationResponseDTO> validateInputSetYaml(String accountIdentifier,
      YamlValidationRequestDTO entityYamlValidationRequestDTO, boolean isParentIdQueryingEnabled);

  ForceImportInputSetResponse forceImportInputSet(
      String accountIdentifier, ForceImportInputSetYamlOperationDTO requestDTO, ScopeInfo scopeInfo);

  Page<InputSetEntity> getBatchInputSetsMetadata(
      ScopeInfo scopeInfo, BatchInputSetsRequestDTO pipelineIdentifiersRequest);

  Page<InputSetEntity> getAllInputSetsMetadataForProject(ScopeInfo scopeInfo, int page, int size, String searchTerm);

  BulkInputSetsResponseDTO getBulkInputSets(
      ScopeInfo scopeInfo, String pipelineIdentifier, BulkInputSetsRequestDTO request);

  void refreshGitFileCache(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String inputSetIdentifier, String branch, ScopeInfo scopeInfo);
}
