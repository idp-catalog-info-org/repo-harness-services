/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.git.model.ChangeType;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.pipeline.MoveConfigOperationType;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PMSInputSetRepositoryCustom {
  List<InputSetEntity> findAll(Criteria criteria);

  Page<InputSetEntity> findAll(Criteria criteria, Pageable pageable, ScopeInfo scopeInfo);

  InputSetEntity saveForOldGitSync(InputSetEntity entityToSave, InputSetYamlDTO yamlDTO, ScopeInfo scopeInfo);

  InputSetEntity save(InputSetEntity entityToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetEntity saveForImportedYAML(
      InputSetEntity entityToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Optional<InputSetEntity> findForOldGitSync(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier,
      boolean notDeleted, boolean isParentIdQueryingEnabled);

  Optional<InputSetEntity> find(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier, boolean notDeleted,
      boolean getMetadataOnly, boolean loadFromFallbackBranch, boolean loadFromCache,
      boolean isParentIdQueryingEnabled);

  InputSetEntity updateForOldGitSync(InputSetEntity entityToUpdate, InputSetYamlDTO yamlDTO, ChangeType changeType,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetEntity update(InputSetEntity entityToUpdate, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  InputSetEntity update(Criteria criteria, Update update);

  InputSetEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);

  /**
   * Updates input set metadata using the provided criteria and update operations.
   *
   * @param criteria The criteria to identify which input sets to update
   * @param update   The update operations to apply
   * @return The number of documents that were updated
   */
  Long updateInputSetMetadataBulk(Criteria criteria, Update update);

  void deleteForOldGitSync(InputSetEntity entityToDelete, InputSetYamlDTO yamlDTO, ScopeInfo scopeInfo);

  void delete(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier);

  void deleteAllInputSetsWhenPipelineDeleted(Query query);
  // nit rename this method after ff is removed.
  boolean existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  boolean checkIfInputSetWithGivenFilePathExists(String accountId, String repoURL, String filePath);

  InputSetEntity updateInputSetEntity(InputSetEntity inputSetToMove, Criteria criteria, Update update,
      MoveConfigOperationType moveConfigOperationType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  List<String> findAllUniqueInputSetRepos(@NotNull Criteria criteria);

  InputSetRemoteRepoPage findRemoteRepoInfosForGivenScope(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit);

  InputSetEntity updateEntity(Criteria criteria, Update update);

  List<InputSetEntity> findAllFromSecondaryDb(Criteria criteria);

  List<InputSetEntity> findAllFromSecondaryDb(Criteria criteria, List<String> fieldsToBeExcluded, Pageable pageable);

  Page<InputSetEntity> findAllFromSecondaryDb(
      Criteria criteria, List<String> fieldsToBeExcluded, Pageable pageable, ScopeInfo scopeInfo);
}
