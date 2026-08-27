/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitx.CrudAction;
import io.harness.gitx.GitXUtils;
import io.harness.gitx.InlineHCHelper;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.InputSetCreateEvent;
import io.harness.pms.events.InputSetDeleteEvent;
import io.harness.pms.events.InputSetUpdateEvent;
import io.harness.pms.inputset.InputSetRemoteRepoInfo;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PipelineExceptionsHelper;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeInfoHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputSetRepositoryCustomImpl implements PMSInputSetRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final MongoTemplate mongoTemplate;
  private final OutboxService outboxService;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final TransactionHelper transactionHelper;
  private final InputSetEntityReadHelper inputSetEntityReadHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ScopeInfoHelper scopeInfoHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public List<InputSetEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, InputSetEntity.class);
  }

  @Override
  public Page<InputSetEntity> findAll(Criteria criteria, Pageable pageable, ScopeInfo scopeInfo) {
    String accountId = scopeInfo.getAccountIdentifier();
    boolean shouldUseCollation = shouldUseCollation(pageable);
    List<InputSetEntity> inputSetEntities =
        gitAwarePersistence.find(criteria, pageable, accountId, scopeInfo, InputSetEntity.class, shouldUseCollation);

    return PageableExecutionUtils.getPage(inputSetEntities, pageable,
        () -> gitAwarePersistence.count(criteria, scopeInfo, accountId, InputSetEntity.class));
  }

  private boolean shouldUseCollation(Pageable pageable) {
    boolean shouldUseCollation = true;
    if (pageable != null && pageable.getSort() != null) {
      // If the sort is not on the name or identifier fields then do not use the collation.
      if (pageable.getSort().getOrderFor(InputSetEntityKeys.name) == null
          && pageable.getSort().getOrderFor(InputSetEntityKeys.identifier) == null) {
        shouldUseCollation = false;
      }
    }
    return shouldUseCollation;
  }
  @Override
  public InputSetEntity saveForOldGitSync(InputSetEntity entityToSave, InputSetYamlDTO yamlDTO, ScopeInfo scopeInfo) {
    InputSetEntity savedInputSetEntity = gitAwarePersistence.save(
        entityToSave, entityToSave.getYaml(), scopeInfo, ChangeType.ADD, InputSetEntity.class, null);
    outboxService.save(InputSetCreateEvent.builder()
                           .accountIdentifier(entityToSave.getAccountIdentifier())
                           .orgIdentifier(entityToSave.getOrgIdentifier())
                           .projectIdentifier(entityToSave.getProjectIdentifier())
                           .pipelineIdentifier(entityToSave.getPipelineIdentifier())
                           .inputSet(savedInputSetEntity)
                           .isForOldGitSync(true)
                           .scopeInfo(scopeInfo)
                           .build());
    return savedInputSetEntity;
  }

  @Override
  public InputSetEntity save(InputSetEntity entityToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    boolean isGitBackedFlow = gitEntityInfo != null && GitXUtils.isYamlStoreBackedByGit(gitEntityInfo.getStoreType());

    String accountId = scopeInfoHelper.getAccountIdentifier(scopeInfo, entityToSave, InputSetEntity::getAccountId);
    String orgId = scopeInfoHelper.getOrgIdentifier(scopeInfo, entityToSave, InputSetEntity::getOrgIdentifier);
    String projectId =
        scopeInfoHelper.getProjectIdentifier(scopeInfo, entityToSave, InputSetEntity::getProjectIdentifier);

    if (gitSyncSdkService.isGitSimplificationEnabled(accountId, orgId, projectId) && isGitBackedFlow) {
      createRemoteEntity(entityToSave, scopeInfo, isParentIdQueryingEnabled);
    } else {
      entityToSave.setStoreType(StoreType.INLINE);
    }
    return transactionHelper.performTransaction(() -> {
      InputSetEntity savedInputSetEntity = mongoTemplate.save(entityToSave);
      outboxService.save(InputSetCreateEvent.builder()
                             .accountIdentifier(accountId)
                             .orgIdentifier(orgId)
                             .projectIdentifier(projectId)
                             .pipelineIdentifier(entityToSave.getPipelineIdentifier())
                             .inputSet(savedInputSetEntity)
                             .isForOldGitSync(false)
                             .scopeInfo(scopeInfo)
                             .build());
      return savedInputSetEntity;
    });
  }

  @Override
  public InputSetEntity saveForImportedYAML(
      InputSetEntity entityToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String accountIdentifier = entityToSave.getAccountIdentifier();
    String orgIdentifier = isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : entityToSave.getOrgIdentifier();
    String projectIdentifier =
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : entityToSave.getProjectIdentifier();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String yamlToPush = entityToSave.getYaml();
    entityToSave.setStoreType(StoreType.REMOTE);
    setRepoUrlForSave(entityToSave, scopeInfo, isParentIdQueryingEnabled);
    entityToSave.setConnectorRef(gitEntityInfo.getConnectorRef());
    entityToSave.setRepo(gitEntityInfo.getRepoName());
    entityToSave.setFilePath(gitEntityInfo.getFilePath());
    return transactionHelper.performTransaction(() -> {
      InputSetEntity savedInputSetEntity = mongoTemplate.save(entityToSave);
      outboxService.save(InputSetCreateEvent.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .pipelineIdentifier(savedInputSetEntity.getPipelineIdentifier())
                             .inputSet(savedInputSetEntity)
                             .isForOldGitSync(false)
                             .scopeInfo(scopeInfo)
                             .build());
      return savedInputSetEntity;
    });
  }

  public Optional<InputSetEntity> findForOldGitSync(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier,
      boolean notDeleted, boolean isParentIdQueryingEnabled) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Criteria criteriaForFind;

    if (isParentIdQueryingEnabled) {
      criteriaForFind =
          PMSInputSetFilterHelper.getCriteriaForFind(scopeInfo, pipelineIdentifier, identifier, notDeleted);
      return gitAwarePersistence.findOne(criteriaForFind, projectId, orgId, accountId, InputSetEntity.class);
    }
    criteriaForFind = PMSInputSetFilterHelper.getCriteriaForFind(
        accountId, orgId, projectId, pipelineIdentifier, identifier, notDeleted);
    return gitAwarePersistence.findOne(criteriaForFind, projectId, orgId, accountId, InputSetEntity.class);
  }

  @Override
  public Optional<InputSetEntity> find(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier,
      boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch, boolean loadFromCache,
      boolean isParentIdQueryingEnabled) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Criteria criteria;
    if (isParentIdQueryingEnabled) {
      criteria = PMSInputSetFilterHelper.getCriteriaForFind(scopeInfo, pipelineIdentifier, identifier, notDeleted);
    } else {
      criteria = PMSInputSetFilterHelper.getCriteriaForFind(
          accountId, orgId, projectId, pipelineIdentifier, identifier, notDeleted);
    }

    Query query = new Query(criteria);
    InputSetEntity savedEntity = mongoTemplate.findOne(query, InputSetEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (GitXUtils.isYamlStoreBackedByGit(savedEntity.getStoreType())) {
      String branch = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(
            accountId, orgId, projectId, savedEntity, branch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      } else {
        savedEntity = fetchRemoteEntity(
            accountId, orgId, projectId, savedEntity, branch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      }
    }

    return Optional.of(savedEntity);
  }

  @Override
  public List<InputSetEntity> findAllFromSecondaryDb(
      Criteria criteria, List<String> fieldsToBeExcluded, Pageable pageable) {
    // Performing query through secondary Db
    return inputSetEntityReadHelper.findAllFromSecondaryDB(criteria, fieldsToBeExcluded, pageable);
  }

  @Override
  public Page<InputSetEntity> findAllFromSecondaryDb(
      Criteria criteria, List<String> fieldsToBeExcluded, Pageable pageable, ScopeInfo scopeInfo) {
    // Performing query through secondary Db
    List<InputSetEntity> inputSetEntities =
        inputSetEntityReadHelper.findAllFromSecondaryDB(criteria, fieldsToBeExcluded, pageable);
    return PageableExecutionUtils.getPage(inputSetEntities, pageable,
        () -> gitAwarePersistence.count(criteria, scopeInfo, scopeInfo.getAccountIdentifier(), InputSetEntity.class));
  }

  private InputSetEntity fetchRemoteEntityWithFallBackBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, InputSetEntity savedEntity, String branch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    try {
      savedEntity = fetchRemoteEntity(accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branch,
          loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    } catch (WingsException ex) {
      log.info(String.format("Failed to fetch input-set from default branch [%s]", branch));
      String fallBackBranch = savedEntity.getFallBackBranch();
      if (PipelineGitXHelper.shouldRetryWithFallBackBranch(
              PipelineExceptionsHelper.getScmException(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving pipeline [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        GitAwareContextHelper.updateGitEntityContextWithBranch(fallBackBranch);
        savedEntity = fetchRemoteEntity(accountIdentifier, orgIdentifier, projectIdentifier, savedEntity,
            fallBackBranch, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  InputSetEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      InputSetEntity savedEntity, String branch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    // fetch yaml from git
    return (InputSetEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        isParentIdQueryingEnabled ? Scope.of(scopeInfo) : Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branch)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .entityType(EntityType.INPUT_SETS)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }

  @Override
  public InputSetEntity updateForOldGitSync(InputSetEntity entityToUpdate, InputSetYamlDTO yamlDTO,
      ChangeType changeType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Supplier<OutboxEvent> functor = null;
    if (!gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
      Optional<InputSetEntity> inputSetEntityOptional = findForOldGitSync(scopeInfo,
          entityToUpdate.getPipelineIdentifier(), entityToUpdate.getIdentifier(), true, isParentIdQueryingEnabled);
      if (inputSetEntityOptional.isPresent()) {
        InputSetEntity oldInputSet = inputSetEntityOptional.get();
        functor = ()
            -> outboxService.save(InputSetUpdateEvent.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgId)
                                      .projectIdentifier(projectId)
                                      .pipelineIdentifier(entityToUpdate.getPipelineIdentifier())
                                      .newInputSet(entityToUpdate)
                                      .oldInputSet(oldInputSet)
                                      .isForOldGitSync(true)
                                      .scopeInfo(scopeInfo)
                                      .build());
      } else {
        throw new InvalidRequestException("No such input set exist");
      }
    }

    return gitAwarePersistence.save(
        entityToUpdate, entityToUpdate.getYaml(), scopeInfo, changeType, InputSetEntity.class, functor);
  }

  @Override
  public InputSetEntity update(InputSetEntity entityToUpdate, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria;
    if (isParentIdQueryingEnabled) {
      criteria = PMSInputSetFilterHelper.getCriteriaForFind(
          scopeInfo, entityToUpdate.getPipelineIdentifier(), entityToUpdate.getIdentifier(), true);
    } else {
      criteria = PMSInputSetFilterHelper.getCriteriaForFind(scopeInfo.getAccountIdentifier(),
          scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), entityToUpdate.getPipelineIdentifier(),
          entityToUpdate.getIdentifier(), true);
    }
    Query query = new Query(criteria);
    long timeOfUpdate = System.currentTimeMillis();
    Update updateOperations = PMSInputSetFilterHelper.getUpdateOperations(entityToUpdate, timeOfUpdate);
    InputSetEntity updatedEntity = transactionHelper.performTransaction(
        () -> updateInputSetInDB(query, updateOperations, entityToUpdate, timeOfUpdate, scopeInfo));

    updatedEntity = onboardToInlineIfNullStoreType(updatedEntity, query);
    if (updatedEntity == null) {
      return null;
    }

    if (GitXUtils.isYamlStoreBackedByGit(updatedEntity.getStoreType())
        && gitSyncSdkService.isGitSimplificationEnabled(
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier())) {
      Scope scope = isParentIdQueryingEnabled ? Scope.of(scopeInfo)
                                              : Scope.builder()
                                                    .accountIdentifier(scopeInfo.getAccountIdentifier())
                                                    .orgIdentifier(scopeInfo.getOrgIdentifier())
                                                    .projectIdentifier(scopeInfo.getProjectIdentifier())
                                                    .build();
      InlineHCHelper.checkAndUpdateContextForInlineHC(updatedEntity, CrudAction.UPDATE,
          InlineHCUpdateContextRequest.builder()
              .scope(scope)
              .entityIdentifier(updatedEntity.getIdentifier())
              .parentIdentifier(updatedEntity.getPipelineIdentifier())
              .build(),
          pmsFeatureFlagService::isEnabled);
      gitAwareEntityHelper.updateEntityOnGit(updatedEntity, entityToUpdate.getYaml(), scope);
    }
    return updatedEntity;
  }

  InputSetEntity updateInputSetInDB(
      Query query, Update updateOperations, InputSetEntity entityToUpdate, long timeOfUpdate, ScopeInfo scopeInfo) {
    InputSetEntity oldEntityFromDB = mongoTemplate.findAndModify(
        query, updateOperations, new FindAndModifyOptions().returnNew(false), InputSetEntity.class);
    if (oldEntityFromDB == null) {
      return null;
    }
    InputSetEntity updatedEntity =
        PMSInputSetFilterHelper.updateFieldsInDBEntry(oldEntityFromDB, entityToUpdate, timeOfUpdate);
    outboxService.save(InputSetUpdateEvent.builder()
                           .accountIdentifier(scopeInfo.getAccountIdentifier())
                           .orgIdentifier(scopeInfo.getOrgIdentifier())
                           .projectIdentifier(scopeInfo.getProjectIdentifier())
                           .pipelineIdentifier(entityToUpdate.getPipelineIdentifier())
                           .newInputSet(updatedEntity)
                           .oldInputSet(oldEntityFromDB)
                           .isForOldGitSync(false)
                           .scopeInfo(scopeInfo)
                           .build());
    return updatedEntity;
  }

  InputSetEntity onboardToInlineIfNullStoreType(InputSetEntity updatedEntity, Query query) {
    if (updatedEntity.getStoreType() == null) {
      Update updateOperationsForOnboardingToInline = PMSInputSetFilterHelper.getUpdateOperationsForOnboardingToInline();
      updatedEntity = mongoTemplate.findAndModify(query, updateOperationsForOnboardingToInline,
          new FindAndModifyOptions().returnNew(true), InputSetEntity.class);
    }
    return updatedEntity;
  }

  @Override
  public InputSetEntity update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Input Set; attempt: {}", "[Failed]: Failed updating Input Set; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }

  @Override
  public InputSetEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    criteria = gitAwarePersistence.makeCriteriaGitAware(
        accountIdentifier, orgIdentifier, projectIdentifier, InputSetEntity.class, criteria);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Input Set; attempt: {}", "[Failed]: Failed updating Input Set; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }
  @Override
  public void deleteForOldGitSync(InputSetEntity entityToDelete, InputSetYamlDTO yamlDTO, ScopeInfo scopeInfo) {
    gitAwarePersistence.delete(entityToDelete, ChangeType.DELETE, InputSetEntity.class);
    outboxService.save(InputSetDeleteEvent.builder()
                           .accountIdentifier(entityToDelete.getAccountIdentifier())
                           .orgIdentifier(entityToDelete.getOrgIdentifier())
                           .projectIdentifier(entityToDelete.getProjectIdentifier())
                           .pipelineIdentifier(entityToDelete.getPipelineIdentifier())
                           .inputSet(entityToDelete)
                           .isForOldGitSync(true)
                           .scopeInfo(scopeInfo)
                           .build());
  }

  @Override
  public void delete(ScopeInfo scopeInfo, String pipelineIdentifier, String identifier) {
    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForFind(scopeInfo, pipelineIdentifier, identifier, true);
    Query query = new Query(criteria);
    InputSetEntity removedEntity = mongoTemplate.findAndRemove(query, InputSetEntity.class);
    outboxService.save(InputSetDeleteEvent.builder()
                           .accountIdentifier(scopeInfo.getAccountIdentifier())
                           .orgIdentifier(scopeInfo.getOrgIdentifier())
                           .projectIdentifier(scopeInfo.getProjectIdentifier())
                           .pipelineIdentifier(pipelineIdentifier)
                           .inputSet(removedEntity)
                           .isForOldGitSync(false)
                           .scopeInfo(scopeInfo)
                           .build());
  }

  @Override
  public void deleteAllInputSetsWhenPipelineDeleted(Query query) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Input Set; attempt: {}", "[Failed]: Failed deleting Input Set; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, InputSetEntity.class));
  }

  @Override
  public boolean existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isParentIdQueryingEnabled) {
      return mongoTemplate.exists(query(Criteria.where(InputSetEntityKeys.deleted)
                                            .is(!notDeleted)
                                            .and(InputSetEntityKeys.parentUniqueId)
                                            .is(scopeInfo.getUniqueId())
                                            .and(InputSetEntityKeys.pipelineIdentifier)
                                            .is(pipelineIdentifier)),
          InputSetEntity.class);
    }
    return gitAwarePersistence.exists(Criteria.where(InputSetEntityKeys.deleted)
                                          .is(!notDeleted)
                                          .and(InputSetEntityKeys.accountId)
                                          .is(accountId)
                                          .and(InputSetEntityKeys.orgIdentifier)
                                          .is(orgIdentifier)
                                          .and(InputSetEntityKeys.projectIdentifier)
                                          .is(projectIdentifier)
                                          .and(InputSetEntityKeys.pipelineIdentifier)
                                          .is(pipelineIdentifier),
        projectIdentifier, orgIdentifier, accountId, InputSetEntity.class);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  @Override
  public boolean checkIfInputSetWithGivenFilePathExists(String accountIdentifier, String repoURL, String filePath) {
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(accountIdentifier)
                            .and(InputSetEntityKeys.repoURL)
                            .is(repoURL)
                            .and(InputSetEntityKeys.filePath)
                            .is(filePath);
    List<InputSetEntity> listOfInputSetEntities = findAll(criteria);
    return !listOfInputSetEntities.isEmpty();
  }

  @Override
  public InputSetEntity updateInputSetEntity(InputSetEntity inputSetToMove, Criteria criteria, Update update,
      MoveConfigOperationType moveConfigOperationType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return transactionHelper.performTransaction(()
                                                    -> moveConfigOperations(inputSetToMove, criteria, update,
                                                        moveConfigOperationType, scopeInfo, isParentIdQueryingEnabled));
  }

  @Override
  public List<String> findAllUniqueInputSetRepos(@NotNull Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, InputSetEntityKeys.repo, InputSetEntity.class, String.class);
  }

  @Override
  public InputSetRemoteRepoPage findRemoteRepoInfosForGivenScope(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit) {
    long methodStartMs = System.currentTimeMillis();
    Criteria criteria = buildRemoteRepoBaseCriteria(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    long totalRepos;
    boolean repoNameFiltered = isNotEmpty(repoName);
    if (repoNameFiltered) {
      criteria.and(InputSetEntityKeys.repo).regex("^" + Pattern.quote(repoName) + "$", "i");
      totalRepos = 0L;
    } else {
      long phaseAStartMs = System.currentTimeMillis();
      List<String> distinctRepos = mongoTemplate.findDistinct(
          new Query(buildRemoteRepoBaseCriteria(accountId, orgIdentifier, projectIdentifier, scopeInfo)),
          InputSetEntityKeys.repo, InputSetEntity.class, String.class);
      distinctRepos = isEmpty(distinctRepos) ? new ArrayList<>() : new ArrayList<>(distinctRepos);
      distinctRepos.sort(String.CASE_INSENSITIVE_ORDER);
      totalRepos = distinctRepos.size();
      log.info("[REMOTE_INPUT_SET_METADATA] phaseA findDistinct account={} totalRepos={} page={} limit={} latencyMs={}",
          accountId, totalRepos, page, limit, System.currentTimeMillis() - phaseAStartMs);
      if (page < 0 || limit <= 0 || page * (long) limit >= totalRepos) {
        log.info("[REMOTE_INPUT_SET_METADATA] short-circuit empty page account={} page={} limit={} totalRepos={} "
                + "latencyMs={}",
            accountId, page, limit, totalRepos, System.currentTimeMillis() - methodStartMs);
        return InputSetRemoteRepoPage.builder().repositories(new ArrayList<>()).totalRepos(totalRepos).build();
      }
      int from = page * limit;
      int to = (int) Math.min((long) from + limit, totalRepos);
      criteria.and(InputSetEntityKeys.repo).in(new ArrayList<>(distinctRepos.subList(from, to)));
    }
    long phaseBStartMs = System.currentTimeMillis();
    Aggregation aggregation =
        Aggregation
            .newAggregation(Aggregation.match(criteria),
                Aggregation.project(InputSetEntityKeys.repo, InputSetEntityKeys.repoURL, InputSetEntityKeys.filePath,
                    InputSetEntityKeys.connectorRef, InputSetEntityKeys.accountId, InputSetEntityKeys.orgIdentifier,
                    InputSetEntityKeys.projectIdentifier, InputSetEntityKeys.parentUniqueId),
                Aggregation.group(InputSetEntityKeys.repo, InputSetEntityKeys.repoURL)
                    .count()
                    .as(REMOTE_REPO_COUNT_KEY)
                    // Capture per-entity scope alongside each filePath so consumers (e.g. the GitX webhook
                    // health endpoint) can decide which per-scope webhook governs each file. Two projects
                    // sharing a repo would otherwise be indistinguishable in a flat list of paths.
                    //
                    // $push (not $addToSet): InputSetEntity has a unique constraint on
                    // (accountId, parentUniqueId, pipelineIdentifier, inputSetIdentifier, repo, branch) and
                    // each entity has exactly one filePath, so two docs in the $match set can never share
                    // an identical filePath tuple within a single (repo, repoURL) group. $addToSet would
                    // pay O(N²) set-membership comparisons for zero dedup benefit; downstream consumers
                    // fold by filePath via putIfAbsent so an accidental duplicate would be tolerated.
                    .push(new BasicDBObject(InputSetEntityKeys.filePath, "$" + InputSetEntityKeys.filePath)
                              .append(InputSetEntityKeys.accountId, "$" + InputSetEntityKeys.accountId)
                              .append(InputSetEntityKeys.orgIdentifier, "$" + InputSetEntityKeys.orgIdentifier)
                              .append(InputSetEntityKeys.projectIdentifier, "$" + InputSetEntityKeys.projectIdentifier)
                              .append(InputSetEntityKeys.parentUniqueId, "$" + InputSetEntityKeys.parentUniqueId))
                    .as(REMOTE_REPO_FILE_PATH_TUPLES_KEY)
                    .addToSet(
                        new BasicDBObject(InputSetEntityKeys.connectorRef, "$" + InputSetEntityKeys.connectorRef)
                            .append(InputSetEntityKeys.accountId, "$" + InputSetEntityKeys.accountId)
                            .append(InputSetEntityKeys.orgIdentifier, "$" + InputSetEntityKeys.orgIdentifier)
                            .append(InputSetEntityKeys.projectIdentifier, "$" + InputSetEntityKeys.projectIdentifier))
                    .as(REMOTE_REPO_CONNECTOR_TUPLES_KEY),
                Aggregation
                    .project(REMOTE_REPO_COUNT_KEY, REMOTE_REPO_FILE_PATH_TUPLES_KEY, REMOTE_REPO_CONNECTOR_TUPLES_KEY)
                    .and("_id." + InputSetEntityKeys.repo)
                    .as(InputSetEntityKeys.repo)
                    .and("_id." + InputSetEntityKeys.repoURL)
                    .as(InputSetEntityKeys.repoURL))
            .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
    AggregationResults<RemoteRepoAggregationResult> results =
        inputSetEntityReadHelper.aggregate(aggregation, RemoteRepoAggregationResult.class);
    List<InputSetRemoteRepoInfo> remoteRepoInfos = new ArrayList<>();
    for (RemoteRepoAggregationResult result : results.getMappedResults()) {
      Map<String, Scope> filePathsByOwningScope = new HashMap<>();
      if (result.getFilePathTuples() != null) {
        for (FilePathTuple tuple : result.getFilePathTuples()) {
          if (tuple == null || isEmpty(tuple.getFilePath())) {
            continue;
          }
          // putIfAbsent: a single file path within a repo has exactly one owning scope. If duplicates ever
          // appeared (shouldn't, but defensively), the first wins — deterministic by Mongo's result ordering.
          filePathsByOwningScope.putIfAbsent(tuple.getFilePath(),
              Scope.of(tuple.getAccountId(), tuple.getOrgIdentifier(), tuple.getProjectIdentifier(),
                  tuple.getParentUniqueId()));
        }
      }
      Set<String> connectorRefs = new HashSet<>();
      if (result.getConnectorTuples() != null) {
        for (ConnectorTuple tuple : result.getConnectorTuples()) {
          String fqn = buildConnectorFqn(tuple);
          if (fqn != null) {
            connectorRefs.add(fqn);
          }
        }
      }
      remoteRepoInfos.add(InputSetRemoteRepoInfo.builder()
                              .repoName(result.getRepo())
                              .repoURL(result.getRepoURL())
                              .count(result.getCount())
                              .filePathsByOwningScope(filePathsByOwningScope)
                              .connectorRefs(connectorRefs)
                              .build());
    }
    remoteRepoInfos.sort(java.util.Comparator.comparing(
        InputSetRemoteRepoInfo::getRepoName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    if (repoNameFiltered) {
      totalRepos = remoteRepoInfos.size();
    }
    log.info("[REMOTE_INPUT_SET_METADATA] phaseB aggregate account={} repoNameFiltered={} pageRepos={} totalRepos={} "
            + "phaseBLatencyMs={} totalLatencyMs={}",
        accountId, repoNameFiltered, remoteRepoInfos.size(), totalRepos, System.currentTimeMillis() - phaseBStartMs,
        System.currentTimeMillis() - methodStartMs);
    return InputSetRemoteRepoPage.builder().repositories(remoteRepoInfos).totalRepos(totalRepos).build();
  }

  private Criteria buildRemoteRepoBaseCriteria(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria()
                            .and(InputSetEntityKeys.accountId)
                            .is(accountId)
                            .and(InputSetEntityKeys.storeType)
                            .is(StoreType.REMOTE)
                            .and(InputSetEntityKeys.deleted)
                            .is(false);
    applyScopeFilter(criteria, accountId, orgIdentifier, projectIdentifier, scopeInfo);
    return criteria;
  }

  /**
   * Narrows the aggregation to the requested scope using {@code parentUniqueId}, which is stable across
   * project movement.
   *
   * <ul>
   *   <li>Account scope (orgIdentifier empty): no narrowing.</li>
   *   <li>Project scope: parentUniqueId == project's uniqueId.</li>
   *   <li>Org scope: parentUniqueId IN [orgUniqueId, ...all projectUniqueIds under the org]. The orgUniqueId
   *       is included so org-scoped input sets aren't excluded; the project list is fetched via
   *       ScopeInfoClient.</li>
   * </ul>
   */
  private void applyScopeFilter(
      Criteria criteria, String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    if (isEmpty(orgIdentifier)) {
      return;
    }
    if (isNotEmpty(projectIdentifier)) {
      String projectUniqueId = resolveProjectUniqueId(accountId, orgIdentifier, projectIdentifier, scopeInfo);
      if (isNotEmpty(projectUniqueId)) {
        criteria.and(InputSetEntityKeys.parentUniqueId).is(projectUniqueId);
      } else {
        log.warn("[REMOTE_INPUT_SET_METADATA] could not resolve projectUniqueId for account={} org={} project={}",
            accountId, orgIdentifier, projectIdentifier);
      }
      return;
    }
    String orgUniqueId = resolveOrgUniqueId(accountId, orgIdentifier, scopeInfo);
    if (isEmpty(orgUniqueId)) {
      log.warn(
          "[REMOTE_INPUT_SET_METADATA] could not resolve orgUniqueId for account={} org={}", accountId, orgIdentifier);
      return;
    }
    List<String> projectUniqueIds = scopeResolutionHelper.getProjectUniqueIds(accountId, orgUniqueId);
    List<String> allowedParentIds = new ArrayList<>(projectUniqueIds == null ? 1 : projectUniqueIds.size() + 1);
    allowedParentIds.add(orgUniqueId);
    if (projectUniqueIds != null) {
      allowedParentIds.addAll(projectUniqueIds);
    }
    criteria.and(InputSetEntityKeys.parentUniqueId).in(allowedParentIds);
  }

  private String resolveProjectUniqueId(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    if (scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      return scopeInfo.getUniqueId();
    }
    ScopeInfo resolved = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
    return resolved == null ? null : resolved.getUniqueId();
  }

  private String resolveOrgUniqueId(String accountId, String orgIdentifier, ScopeInfo scopeInfo) {
    if (scopeInfo != null && orgIdentifier.equals(scopeInfo.getOrgIdentifier())
        && isEmpty(scopeInfo.getProjectIdentifier()) && isNotEmpty(scopeInfo.getUniqueId())) {
      return scopeInfo.getUniqueId();
    }
    ScopeInfo resolved = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, null);
    return resolved == null ? null : resolved.getUniqueId();
  }

  private static String buildConnectorFqn(ConnectorTuple tuple) {
    String ref = tuple.getConnectorRef();
    String accountId = tuple.getAccountId();
    if (isEmpty(ref) || isEmpty(accountId)) {
      return null;
    }
    if (ref.startsWith("account.")) {
      return accountId + "/" + ref.substring("account.".length());
    }
    if (ref.startsWith("org.")) {
      if (isEmpty(tuple.getOrgIdentifier())) {
        return null;
      }
      return accountId + "/" + tuple.getOrgIdentifier() + "/" + ref.substring("org.".length());
    }
    // Unprefixed ref is project-scoped by NG convention — require org + project to build a valid FQN.
    if (isEmpty(tuple.getOrgIdentifier()) || isEmpty(tuple.getProjectIdentifier())) {
      return null;
    }
    return accountId + "/" + tuple.getOrgIdentifier() + "/" + tuple.getProjectIdentifier() + "/" + ref;
  }

  private static final String REMOTE_REPO_COUNT_KEY = "count";
  private static final String REMOTE_REPO_FILE_PATH_TUPLES_KEY = "filePathTuples";
  private static final String REMOTE_REPO_CONNECTOR_TUPLES_KEY = "connectorTuples";

  @lombok.Data
  @lombok.NoArgsConstructor
  static class RemoteRepoAggregationResult {
    private String repo;
    private String repoURL;
    private long count;
    // List (not Set): the aggregation uses $push for filePathTuples, which produces a BSON array.
    // Deserializing into a Set would force the driver to hash/equals every tuple again, paying the
    // dedup cost we deliberately skipped server-side.
    private List<FilePathTuple> filePathTuples;
    private Set<ConnectorTuple> connectorTuples;
  }

  @lombok.Data
  @lombok.NoArgsConstructor
  static class FilePathTuple {
    private String filePath;
    private String accountId;
    private String orgIdentifier;
    private String projectIdentifier;
    private String parentUniqueId;
  }

  @lombok.Data
  @lombok.NoArgsConstructor
  static class ConnectorTuple {
    private String connectorRef;
    private String accountId;
    private String orgIdentifier;
    private String projectIdentifier;
  }

  @Override
  public InputSetEntity updateEntity(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return Failsafe.with(DEFAULT_RETRY_POLICY)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }

  @VisibleForTesting
  InputSetEntity moveConfigOperations(InputSetEntity inputSetToMove, Criteria criteria, Update update,
      MoveConfigOperationType moveConfigOperationType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    //   create file if inline to remote
    if (INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      createRemoteEntity(inputSetToMove, scopeInfo, isParentIdQueryingEnabled);
    }
    //    update the mongo db
    return updateInputSetInDB(new Query(criteria), update, inputSetToMove, System.currentTimeMillis(), scopeInfo);
  }

  void setRepoUrlForSave(InputSetEntity entityToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isEmpty(entityToSave.getRepoURL())) {
      entityToSave.setRepoURL(isParentIdQueryingEnabled
              ? gitAwareEntityHelper.getRepoUrl(scopeInfo)
              : gitAwareEntityHelper.getRepoUrl(
                    entityToSave.getAccountId(), entityToSave.getOrgIdentifier(), entityToSave.getProjectIdentifier()));
    }
  }

  private ScmCreateFileGitResponse createRemoteEntity(
      InputSetEntity inputSetEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    Scope scope = isParentIdQueryingEnabled ? Scope.of(scopeInfo)
                                            : Scope.builder()
                                                  .accountIdentifier(inputSetEntity.getAccountIdentifier())
                                                  .orgIdentifier(inputSetEntity.getOrgIdentifier())
                                                  .projectIdentifier(inputSetEntity.getProjectIdentifier())
                                                  .build();

    String yamlToPush = inputSetEntity.getYaml();
    addGitParamsToInputSetEntity(inputSetEntity, gitEntityInfo, scopeInfo, isParentIdQueryingEnabled);
    return gitAwareEntityHelper.createEntityOnGit(inputSetEntity, yamlToPush, scope);
  }

  private void addGitParamsToInputSetEntity(InputSetEntity inputSetEntity, GitEntityInfo gitEntityInfo,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (!StoreType.INLINE_HC.equals(inputSetEntity.getStoreType())) {
      inputSetEntity.setStoreType(StoreType.REMOTE);
    }
    inputSetEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    inputSetEntity.setRepo(gitEntityInfo.getRepoName());
    inputSetEntity.setFilePath(gitEntityInfo.getFilePath());
    inputSetEntity.setFallBackBranch(gitEntityInfo.getBranch());
    setRepoUrlForSave(inputSetEntity, scopeInfo, isParentIdQueryingEnabled);
  }

  @Override
  public Long updateInputSetMetadataBulk(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Input Set; attempt: {}", "[Failed]: Failed updating Input Set; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(() -> mongoTemplate.updateMulti(query, update, InputSetEntity.class).getModifiedCount());
  }

  @Override
  public List<InputSetEntity> findAllFromSecondaryDb(Criteria criteria) {
    // Performing query through secondary Db
    return inputSetEntityReadHelper.findAllFromSecondaryDB(criteria);
  }
}
