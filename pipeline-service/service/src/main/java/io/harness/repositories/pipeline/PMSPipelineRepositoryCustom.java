/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.git.model.ChangeType;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PipelineEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PMSPipelineRepositoryCustom {
  Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, boolean getDistinctFromBranches);

  Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier, ScopeInfo scopeInfo,
      boolean getDistinctFromBranches);

  Long countAllPipelines(Criteria criteria);

  Long countAllPipelinesInAccount(String accountId);

  PipelineEntity saveForOldGitSync(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  /**
   * this method is to be used for new git experience, and for all pipelines that are not git synced in both old and new
   * flows
   */
  PipelineEntity save(PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Optional<PipelineEntity> findForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted);

  Optional<PipelineEntity> findForOldGitSync(
      String accountId, ScopeInfo scopeInfo, String pipelineIdentifier, boolean notDeleted);

  /**
   * this method is to be used for new git experience, and for all pipelines that are not git synced in both old and new
   * flows
   */
  Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache);

  Optional<PipelineEntity> find(String uuid);

  PipelineEntity updatePipelineYamlForOldGitSync(PipelineEntity pipelineToUpdate, PipelineEntity oldPipelineEntity,
      ChangeType changeType, boolean isParentIdQueryingEnabled);

  /**
   * this method is to be used for new git experience, and for all pipelines that are not git synced in both old and new
   * flows
   */
  PipelineEntity updatePipelineYaml(
      PipelineEntity pipelineToUpdate, boolean isPatch, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity updatePipelineFilters(PipelineEntity pipelineToUpdate, String uuid, Integer yamlHash);

  PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);

  void deleteForOldGitSync(PipelineEntity pipelineToDelete, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity updatePipelineMetadata(ScopeInfo scopeInfo, Criteria criteria, Update update);

  /**
   * Updates pipeline metadata using the provided criteria and update operations.
   *
   * @param criteria The criteria to identify which pipelines to update
   * @param update   The update operations to apply
   * @return The number of documents that were updated
   */
  Long updatePipelineMetadataBulk(Criteria criteria, Update update);

  void deleteForOldGitSync(PipelineEntity pipelineToDelete);

  /**
   * this method is to be used for new git experience, and for all pipelines that are not git synced in both old and new
   * flows
   */
  void delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  void delete(ScopeInfo scopeInfo, String pipelineIdentifier);

  boolean deleteAllPipelinesInAProject(String accountId, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity savePipelineEntityForImportedYAML(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Long countFileInstances(String accountId, String repoURL, String filePath);

  List<String> findAllUniqueRepos(Criteria criteria);

  PMSPipelineRemoteRepoPage findRemoteRepoInfosForGivenScope(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit);

  PipelineEntity updatePipelineEntity(PipelineEntity pipelineEntity, Update pipelineUpdate, Criteria pipelineCriteria,
      Update metadataUpdate, Criteria metadataCriteria, MoveConfigOperationType moveConfigOperationType,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity updateEntity(Criteria criteria, Update update);

  List<String> findAllPipelineIdentifiers(Criteria criteria);

  Stream<PipelineEntity> findAllFromSecondaryDb(Criteria criteria, List<String> fields);

  // This method will find the pipeline entity (V1 pipeline) from DB which is stored remotely in some DB.
  // Criteria passed here has the filter of repo url to find the pipeline entity from that particular repo.
  List<PipelineEntity> find(Criteria criteria);

  Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
}
