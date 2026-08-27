/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.CrudAction;
import io.harness.gitx.GitXUtils;
import io.harness.gitx.InlineHCHelper;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineMoveConfigEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.events.delete.PipelineDeleteEvent;
import io.harness.pms.mongo.PipelineBucket;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Builder;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.response.PipelineEntityReadHelper;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.springdata.BudgetedQuery;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PipelineExceptionsHelper;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.tuple.Pair;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@GitSyncableHarnessRepo
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineRepositoryCustomImpl implements PMSPipelineRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final GitAwarePersistence gitAwarePersistence;
  private final TransactionHelper transactionHelper;
  private final PipelineMetadataService pipelineMetadataService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final OutboxService outboxService;
  private final GitSyncSdkService gitSyncSdkService;
  private final PipelineEntityReadHelper pipelineEntityReadHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, boolean getDistinctFromBranches) {
    boolean shouldUseCollation = shouldUseCollation(pageable);
    if (getDistinctFromBranches) {
      return getDistinctPipelinesFromBranches(mongoTemplate, criteria, pageable, shouldUseCollation, false);
    }
    List<PipelineEntity> pipelineEntities = gitAwarePersistence.find(criteria, pageable, projectIdentifier,
        orgIdentifier, accountIdentifier, PipelineEntity.class, shouldUseCollation);

    return PageableExecutionUtils.getPage(pipelineEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, PipelineEntity.class));
  }

  private boolean shouldUseCollation(Pageable pageable) {
    boolean shouldUseCollation = true;
    if (pageable != null && pageable.getSort() != null) {
      // If the sort is not on the name or identifier fields then do not use the collation.
      if (pageable.getSort().getOrderFor(PipelineEntityKeys.name) == null
          && pageable.getSort().getOrderFor(PipelineEntityKeys.identifier) == null) {
        shouldUseCollation = false;
      }
    }
    return shouldUseCollation;
  }

  @Override
  public Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier,
      ScopeInfo scopeInfo, boolean getDistinctFromBranches) {
    boolean shouldUseCollation = shouldUseCollation(pageable);
    if (getDistinctFromBranches) {
      return getDistinctPipelinesFromBranches(mongoTemplate, criteria, pageable, shouldUseCollation(pageable), true);
    }
    List<PipelineEntity> pipelineEntities = gitAwarePersistence.find(
        criteria, pageable, accountIdentifier, scopeInfo, PipelineEntity.class, shouldUseCollation);

    return PageableExecutionUtils.getPage(pipelineEntities, pageable,
        () -> gitAwarePersistence.count(criteria, scopeInfo, accountIdentifier, PipelineEntity.class));
  }

  private Page<PipelineEntity> getDistinctPipelinesFromBranches(MongoTemplate mongoTemplate, Criteria criteria,
      Pageable pageable, boolean shouldUseCollation, boolean isParentIdQueryingEnabled) {
    return EntityDistinctElementHelper.getDistinctElementPage(mongoTemplate, criteria, pageable, PipelineEntity.class,
        shouldUseCollation, PipelineEntityKeys.accountId, PipelineEntityKeys.parentUniqueId,
        PipelineEntityKeys.identifier);
  }

  @Override
  public Long countAllPipelines(Criteria criteria) {
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public Long countAllPipelinesInAccount(String accountId) {
    Criteria criteria =
        Criteria.where(PipelineEntityKeys.accountId).is(accountId).and(PipelineEntityKeys.deleted).is(false);
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public PipelineEntity saveForOldGitSync(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return transactionHelper.performTransaction(() -> {
      PipelineEntity savedEntity = gitAwarePersistence.save(
          pipelineToSave, pipelineToSave.getYaml(), scopeInfo, ChangeType.ADD, PipelineEntity.class, null);
      PipelineCreateEvent pipelineCreateEvent = getPipelineSaveEvent(savedEntity, true, isParentIdQueryingEnabled);
      outboxService.save(pipelineCreateEvent);
      checkForMetadataAndSaveIfAbsent(savedEntity, isParentIdQueryingEnabled);
      return savedEntity;
    });
  }

  @Override
  public PipelineEntity save(PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return transactionHelper.performTransaction(
        () -> savePipelineOperations(pipelineToSave, scopeInfo, isParentIdQueryingEnabled));
  }

  @VisibleForTesting
  PipelineEntity savePipelineOperations(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntity savedEntity = savePipelineEntity(pipelineToSave, scopeInfo, isParentIdQueryingEnabled);
    checkForMetadataAndSaveIfAbsent(savedEntity, isParentIdQueryingEnabled);
    return savedEntity;
  }

  @VisibleForTesting
  PipelineEntity savePipelineEntity(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo == null || gitEntityInfo.getStoreType() == null
        || gitEntityInfo.getStoreType().equals(StoreType.INLINE)) {
      pipelineToSave.setStoreType(StoreType.INLINE);
      PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
      outboxService.save(getPipelineSaveEvent(savedPipelineEntity, false, isParentIdQueryingEnabled));
      return savedPipelineEntity;
    }

    String accountId = scopeInfo.getAccountIdentifier();
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    if (gitSyncSdkService.isGitSimplificationEnabled(accountId, orgId, projectId)) {
      createRemoteEntity(pipelineToSave, scopeInfo, isParentIdQueryingEnabled);
    } else {
      log.info(String.format("Marking storeType as INLINE for Pipeline with ID [%s] because Git simplification was not "
              + "enabled for Project [%s] in Account [%s]",
          pipelineToSave.getIdentifier(), projectId, accountId));
      pipelineToSave.setStoreType(StoreType.INLINE);
    }
    PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
    sendCreateAuditEvents(isParentIdQueryingEnabled, savedPipelineEntity, accountId);
    return savedPipelineEntity;
  }

  private void sendCreateAuditEvents(
      boolean isParentIdQueryingEnabled, PipelineEntity savedPipelineEntity, String accountId) {
    try {
      outboxService.save(getPipelineSaveEvent(savedPipelineEntity, false, isParentIdQueryingEnabled));
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, savedPipelineEntity.getParentUniqueId());
      Scope scope = Scope.of(scopeInfo);
      outboxService.save(new PipelineMoveConfigEvent(scope, savedPipelineEntity.getIdentifier(),
          savedPipelineEntity.getName(), "", savedPipelineEntity.getGitAttributesYaml(), pmsFeatureFlagHelper));

    } catch (Exception e) {
      log.warn("Audit trails when creating pipeline failed with exception: ", e);
    }
  }

  PipelineCreateEvent getPipelineSaveEvent(
      PipelineEntity savedPipelineEntity, boolean isOldGitSync, boolean isParentIdQueryingEnabled) {
    return PipelineCreateEvent.builder()
        .accountIdentifier(savedPipelineEntity.getAccountId())
        .orgIdentifier(savedPipelineEntity.getOrgIdentifier())
        .projectIdentifier(savedPipelineEntity.getProjectIdentifier())
        .pipeline(savedPipelineEntity)
        .isForOldGitSync(isOldGitSync)
        .isParentIdQueryingEnabled(isParentIdQueryingEnabled)
        .pmsFeatureFlagHelper(pmsFeatureFlagHelper)
        .build();
  }

  void checkForMetadataAndSaveIfAbsent(PipelineEntity savedEntity) {
    checkForMetadataAndSaveIfAbsent(savedEntity, false);
  }

  void checkForMetadataAndSaveIfAbsent(PipelineEntity savedEntity, boolean isParentIdQueryingEnabled) {
    // checking if PipelineMetadata exists or not, if exists don't re-save the entity, as only one entry across git
    // repos should be there.
    Optional<PipelineMetadataV2> metadataOptional = pipelineMetadataService.getMetadata(
        savedEntity.getAccountIdentifier(), savedEntity.getParentUniqueId(), savedEntity.getIdentifier());
    if (metadataOptional.isEmpty()) {
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(savedEntity.getAccountIdentifier(), savedEntity.getParentUniqueId());
      PipelineMetadataV2Builder metadataV2Builder =
          PipelineMetadataV2.builder()
              .accountIdentifier(savedEntity.getAccountIdentifier())
              .runSequence(0)
              .identifier(savedEntity.getIdentifier())
              .entityGitDetails(EntityGitDetails.builder().branch(GitContextHelper.getBranch()).build())
              .orgIdentifier(isNotEmpty(scopeInfo.getOrgIdentifier()) ? scopeInfo.getOrgIdentifier()
                                                                      : savedEntity.getOrgIdentifier())
              .projectIdentifier(isNotEmpty(scopeInfo.getProjectIdentifier()) ? scopeInfo.getProjectIdentifier()
                                                                              : savedEntity.getProjectIdentifier())
              .pipelineUniqueId(savedEntity.getUniqueId())
              .parentUniqueId(savedEntity.getParentUniqueId());
      if (isEmpty(savedEntity.getParentUniqueId())) {
        metadataV2Builder.parentUniqueId(scopeResolutionHelper
                                             .getScopeInfo(savedEntity.getAccountIdentifier(),
                                                 savedEntity.getOrgIdentifier(), savedEntity.getProjectIdentifier())
                                             .getUniqueId());
      }
      pipelineMetadataService.save(metadataV2Builder.build());
    }
  }

  @Override
  public Optional<PipelineEntity> findForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, notDeleted);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
  }

  @Override
  public Optional<PipelineEntity> findForOldGitSync(
      String accountId, ScopeInfo scopeInfo, String pipelineIdentifier, boolean notDeleted) {
    Criteria criteria =
        PMSPipelineFilterHelper.getCriteriaForFind(accountId, scopeInfo.getUniqueId(), pipelineIdentifier, notDeleted);
    return gitAwarePersistence.findOne(
        criteria, scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier(), accountId, PipelineEntity.class);
  }

  @Override
  public Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache) {
    return find(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, notDeleted, getMetadataOnly,
        loadFromFallbackBranch, loadFromCache, null, false);
  }

  @Override
  public Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PipelineEntity savedEntity = getPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        notDeleted, getMetadataOnly, scopeInfo, isParentIdQueryingEnabled);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (GitXUtils.isYamlStoreBackedByGit(savedEntity.getStoreType())) {
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      log.info("Fetching pipeline from working branchName - " + branchName);
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(accountId, orgIdentifier, projectIdentifier, savedEntity,
            branchName, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      } else {
        savedEntity = fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branchName,
            loadFromCache, scopeInfo, isParentIdQueryingEnabled);
      }
    }
    return Optional.of(savedEntity);
  }

  @Override
  public Optional<PipelineEntity> find(String uuid) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(uuid);
    Query query = new Query(criteria);
    PipelineEntity pipelineEntity = mongoTemplate.findOne(query, PipelineEntity.class);
    if (pipelineEntity == null) {
      return Optional.empty();
    }
    return Optional.of(pipelineEntity);
  }

  private PipelineEntity getPipelineEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean metadataOnly, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria =
        PMSPipelineFilterHelper.getCriteriaForFind(accountId, scopeInfo.getUniqueId(), pipelineIdentifier, notDeleted);
    Query query = new Query(criteria);
    if (metadataOnly) {
      for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
        query.fields().exclude(nonMetadataField);
      }
    }
    return mongoTemplate.findOne(query, PipelineEntity.class);
  }

  PipelineEntity fetchRemoteEntityWithFallBackBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, PipelineEntity savedEntity, String branch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    try {
      savedEntity = fetchRemoteEntity(accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branch,
          loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    } catch (WingsException ex) {
      String fallBackBranch = getFallBackBranch(savedEntity, scopeInfo, isParentIdQueryingEnabled);
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

  private String getFallBackBranch(PipelineEntity savedEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Optional<PipelineMetadataV2> metadataOptional = pipelineMetadataService.getMetadata(
        savedEntity.getAccountIdentifier(), scopeInfo.getUniqueId(), savedEntity.getIdentifier());
    if (metadataOptional.isPresent() && metadataOptional.get().getEntityGitDetails() != null) {
      return metadataOptional.get().getEntityGitDetails().getBranch();
    }
    return null;
  }

  @VisibleForTesting
  PipelineEntity fetchRemoteEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineEntity savedEntity, String branch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Scope scope = Scope.of(scopeInfo);
    return (PipelineEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity, scope,
        GitContextRequestParams.builder()
            .branchName(branch)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .loadFromCache(loadFromCache)
            .entityType(EntityType.PIPELINES)
            .build(),
        Collections.emptyMap());
  }

  @Override
  public PipelineEntity updatePipelineYamlForOldGitSync(PipelineEntity pipelineToUpdate,
      PipelineEntity oldPipelineEntity, ChangeType changeType, boolean isParentIdQueryingEnabled) {
    String accountIdentifier = pipelineToUpdate.getAccountIdentifier();
    String orgIdentifier = pipelineToUpdate.getOrgIdentifier();
    String projectIdentifier = pipelineToUpdate.getProjectIdentifier();
    PipelineEntity updatedEntity =
        gitAwarePersistence.save(pipelineToUpdate, pipelineToUpdate.getYaml(), changeType, PipelineEntity.class, null);
    if (updatedEntity != null) {
      outboxService.save(new PipelineUpdateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipelineToUpdate,
          oldPipelineEntity, true, isParentIdQueryingEnabled, pmsFeatureFlagHelper));
    }
    return updatedEntity;
  }

  public PipelineEntity updatePipelineYaml(
      PipelineEntity pipelineToUpdate, boolean isPatch, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        pipelineToUpdate.getAccountId(), pipelineToUpdate.getParentUniqueId(), pipelineToUpdate.getIdentifier(), true);
    Query query = new Query(criteria);
    long timeOfUpdate = System.currentTimeMillis();
    Update updateOperations;
    if (isPatch) {
      updateOperations = PMSPipelineFilterHelper.getUpdateOperationsForPatch(pipelineToUpdate, timeOfUpdate);
    } else {
      updateOperations = PMSPipelineFilterHelper.getUpdateOperations(pipelineToUpdate, timeOfUpdate);
    }
    PipelineEntity updatedPipelineEntity =
        transactionHelper.performTransaction(()
                                                 -> updatePipelineEntityInDB(query, updateOperations, pipelineToUpdate,
                                                     timeOfUpdate, isPatch, scopeInfo, isParentIdQueryingEnabled));

    if (updatedPipelineEntity == null) {
      return null;
    }

    updatedPipelineEntity = onboardToInlineIfNullStoreType(updatedPipelineEntity, query);
    if (updatedPipelineEntity == null) {
      return null;
    }

    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    if (GitXUtils.isYamlStoreBackedByGit(updatedPipelineEntity.getStoreType())
        && gitSyncSdkService.isGitSimplificationEnabled(pipelineToUpdate.getAccountIdentifier(), orgId, projectId)) {
      Scope scope = Scope.of(scopeInfo);
      InlineHCHelper.checkAndUpdateContextForInlineHC(updatedPipelineEntity, CrudAction.UPDATE,
          InlineHCUpdateContextRequest.builder()
              .scope(scope)
              .entityIdentifier(updatedPipelineEntity.getIdentifier())
              .build(),
          pmsFeatureFlagService::isEnabled);
      boolean isV1MetadataOnlyPatch = isPatch && HarnessYamlVersion.isV1(updatedPipelineEntity.getHarnessVersion())
          && isEmpty(pipelineToUpdate.getYaml());
      if (!isV1MetadataOnlyPatch) {
        gitAwareEntityHelper.updateEntityOnGit(updatedPipelineEntity, pipelineToUpdate.getYaml(), scope);
      }
    }
    return updatedPipelineEntity;
  }

  @Override
  public PipelineEntity updatePipelineFilters(PipelineEntity pipelineToUpdate, String uuid, Integer yamlHash) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(uuid, yamlHash);
    Query query = new Query(criteria);
    Update updateOperations = PMSPipelineFilterHelper.getPipelineFilterUpdateOperations(pipelineToUpdate);

    Pair<PipelineEntity, PipelineEntity> updatePipelineInDb =
        transactionHelper.performTransaction(()
                                                 -> updatePipelineEntityWithoutOutboxEvent(query, updateOperations,
                                                     pipelineToUpdate, pipelineToUpdate.getLastUpdatedAt()));
    PipelineEntity updatedPipelineEntity = updatePipelineInDb.getRight();
    if (updatedPipelineEntity == null) {
      return null;
    }
    return updatedPipelineEntity;
  }

  PipelineEntity updatePipelineEntityInDB(Query query, Update updateOperations, PipelineEntity pipelineToUpdate,
      long timeOfUpdate, boolean isPatch, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Pair<PipelineEntity, PipelineEntity> updatePipelineInDb = updatePipelineEntityWithoutOutboxEvent(
        query, updateOperations, pipelineToUpdate, timeOfUpdate, isPatch, scopeInfo, isParentIdQueryingEnabled);
    PipelineEntity oldEntityFromDB = updatePipelineInDb.getLeft();
    if (oldEntityFromDB == null) {
      return null;
    }
    PipelineEntity pipelineEntityAfterUpdate = updatePipelineInDb.getRight();
    outboxService.save(new PipelineUpdateEvent(pipelineToUpdate.getAccountIdentifier(),
        pipelineToUpdate.getOrgIdentifier(), pipelineToUpdate.getProjectIdentifier(), pipelineEntityAfterUpdate,
        oldEntityFromDB, isParentIdQueryingEnabled, pmsFeatureFlagHelper));
    return pipelineEntityAfterUpdate;
  }

  Pair<PipelineEntity, PipelineEntity> updatePipelineEntityWithoutOutboxEvent(Query query, Update updateOperations,
      PipelineEntity pipelineToUpdate, long timeOfUpdate, boolean isPatch, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    /*
     Return Pair of oldEntityFromDB and pipelineEntityAfterUpdate
     First value is Old, second value is New
    */
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    PipelineEntity oldEntityFromDB = mongoTemplate.findAndModify(
        query, updateOperations, new FindAndModifyOptions().returnNew(false), PipelineEntity.class);
    if (oldEntityFromDB == null) {
      throw new EntityNotFoundException(
          String.format("Pipeline with identifier %s does not exist in account: %s org: %s, project: %s",
              pipelineToUpdate.getIdentifier(), pipelineToUpdate.getAccountIdentifier(), orgId, projectId));
    }
    PipelineEntity pipelineEntityAfterUpdate;
    if (isPatch) {
      pipelineEntityAfterUpdate =
          PMSPipelineFilterHelper.updateFieldsInDBEntryForPatch(oldEntityFromDB, pipelineToUpdate, timeOfUpdate);
    } else {
      pipelineEntityAfterUpdate =
          PMSPipelineFilterHelper.updateFieldsInDBEntry(oldEntityFromDB, pipelineToUpdate, timeOfUpdate);
    }
    return Pair.of(oldEntityFromDB, pipelineEntityAfterUpdate);
  }

  Pair<PipelineEntity, PipelineEntity> updatePipelineEntityWithoutOutboxEvent(
      Query query, Update updateOperations, PipelineEntity pipelineToUpdate, long timeOfUpdate) {
    return updatePipelineEntityWithoutOutboxEvent(
        query, updateOperations, pipelineToUpdate, timeOfUpdate, false, null, false);
  }

  PipelineEntity onboardToInlineIfNullStoreType(PipelineEntity updatedPipelineEntity, Query query) {
    if (updatedPipelineEntity.getStoreType() == null) {
      // onboarding old entities as INLINE
      Update updateOperationsForOnboardingToInline = PMSPipelineFilterHelper.getUpdateOperationsForOnboardingToInline();
      updatedPipelineEntity = mongoTemplate.findAndModify(query, updateOperationsForOnboardingToInline,
          new FindAndModifyOptions().returnNew(true), PipelineEntity.class);
    }
    return updatedPipelineEntity;
  }

  @Override
  public PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    return updatePipelineMetadata(
        scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier), criteria, update);
  }

  @Override
  public PipelineEntity updatePipelineMetadata(ScopeInfo scopeInfo, Criteria criteria, Update update) {
    String accountId = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();

    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      criteria = gitAwarePersistence.makeCriteriaGitAware(
          accountId, orgIdentifier, projectIdentifier, PipelineEntity.class, criteria);
    }
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicyForPipelineUpdate();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineEntity.class));
  }

  @Override
  public Long updatePipelineMetadataBulk(Criteria criteria, Update update) {
    // Multi-document write: tag SLOW for the bulk budget.
    Query query = BudgetedQuery.withBudget(new Query(criteria), PipelineBucket.SLOW);
    RetryPolicy<Object> retryPolicy = getRetryPolicyForPipelineUpdate();
    return Failsafe.with(retryPolicy)
        .get(() -> mongoTemplate.updateMulti(query, update, PipelineEntity.class).getModifiedCount());
  }

  @Override
  public void deleteForOldGitSync(PipelineEntity pipelineToDelete) {
    deleteForOldGitSync(pipelineToDelete, null, false);
  }

  @Override
  public void deleteForOldGitSync(
      PipelineEntity pipelineToDelete, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String accountId = pipelineToDelete.getAccountId();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();
    gitAwarePersistence.delete(pipelineToDelete, ChangeType.DELETE, PipelineEntity.class);
    outboxService.save(new PipelineDeleteEvent(
        accountId, orgIdentifier, projectIdentifier, pipelineToDelete, pmsFeatureFlagHelper, null, scopeInfo));
  }

  @Override
  public void delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true);
    Query query = new Query(criteria);
    transactionHelper.performTransaction(() -> {
      PipelineEntity deletedPipelineEntity = mongoTemplate.findAndRemove(query, PipelineEntity.class);
      outboxService.save(new PipelineDeleteEvent(accountId, orgIdentifier, projectIdentifier, deletedPipelineEntity,
          pmsFeatureFlagHelper, deletedPipelineEntity != null ? deletedPipelineEntity.getParentUniqueId() : null,
          null));
      return deletedPipelineEntity;
    });
  }

  @Override
  public void delete(ScopeInfo scopeInfo, String pipelineIdentifier) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), pipelineIdentifier, true);
    Query query = new Query(criteria);
    transactionHelper.performTransaction(() -> {
      PipelineEntity deletedPipelineEntity = mongoTemplate.findAndRemove(query, PipelineEntity.class);
      outboxService.save(new PipelineDeleteEvent(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), deletedPipelineEntity, pmsFeatureFlagHelper, scopeInfo.getUniqueId(),
          scopeInfo));
      return deletedPipelineEntity;
    });
  }

  @Override
  public boolean deleteAllPipelinesInAProject(
      String accountId, String orgId, String projectId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForAllPipelinesInProject(scopeInfo);
    // Project-wide bulk removal: tag SLOW for the bulk budget.
    Query query = BudgetedQuery.withBudget(new Query(criteria), PipelineBucket.SLOW);
    try {
      List<PipelineEntity> entities = mongoTemplate.findAllAndRemove(query, PipelineEntity.class);
      entities.stream().forEach(deletedPipelineEntity -> {
        outboxService.save(new PipelineDeleteEvent(accountId, orgId, projectId, deletedPipelineEntity,
            pmsFeatureFlagHelper, deletedPipelineEntity.getParentUniqueId(), scopeInfo));
      });
      return true;
    } catch (Exception e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForPipelinesNotDeleted(
          accountId, orgId, projectId, ExceptionUtils.getMessage(e));
      log.error(errorMessage, e);
      return false;
    }
  }

  private RetryPolicy<Object> getRetryPolicyForPipelineUpdate() {
    return PersistenceUtils.getRetryPolicy(
        "[Retrying]: Failed updating Pipeline; attempt: {}", "[Failed]: Failed updating Pipeline; attempt: {}");
  }

  @Override
  public PipelineEntity savePipelineEntityForImportedYAML(
      PipelineEntity pipelineToSave, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String accountIdentifier = pipelineToSave.getAccountIdentifier();
    String orgIdentifier = pipelineToSave.getOrgIdentifier();
    String projectIdentifier = pipelineToSave.getProjectIdentifier();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    addGitParamsToPipelineEntity(pipelineToSave, gitEntityInfo, scopeInfo, isParentIdQueryingEnabled);
    return transactionHelper.performTransaction(() -> {
      PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
      checkForMetadataAndSaveIfAbsent(savedPipelineEntity);
      outboxService.save(new PipelineCreateEvent(
          accountIdentifier, orgIdentifier, projectIdentifier, savedPipelineEntity, pmsFeatureFlagHelper));
      return savedPipelineEntity;
    });
  }

  void addGitParamsToPipelineEntity(PipelineEntity pipelineToSave, GitEntityInfo gitEntityInfo, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    if (!StoreType.INLINE_HC.equals(pipelineToSave.getStoreType())) {
      pipelineToSave.setStoreType(StoreType.REMOTE);
    }
    if (EmptyPredicate.isEmpty(pipelineToSave.getRepoURL())) {
      String repoUrl = gitAwareEntityHelper.getRepoUrl(scopeInfo);
      pipelineToSave.setRepoURL(repoUrl);
    }
    pipelineToSave.setConnectorRef(gitEntityInfo.getConnectorRef());
    pipelineToSave.setRepo(gitEntityInfo.getRepoName());
    pipelineToSave.setFilePath(gitEntityInfo.getFilePath());
  }

  @Override
  public Long countFileInstances(String accountId, String repoURL, String filePath) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFileUniquenessCheck(accountId, repoURL, filePath);
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public List<String> findAllUniqueRepos(Criteria criteria) {
    // Account-wide distinct scan: tag SLOW so it isn't held to the FAST budget.
    Query query = BudgetedQuery.withBudget(new Query(criteria), PipelineBucket.SLOW);
    return mongoTemplate.findDistinct(query, PipelineEntityKeys.repo, PipelineEntity.class, String.class);
  }

  @Override
  public PMSPipelineRemoteRepoPage findRemoteRepoInfosForGivenScope(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit) {
    long methodStartMs = System.currentTimeMillis();
    Criteria criteria = buildRemoteRepoBaseCriteria(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    long totalRepos;
    boolean repoNameFiltered = isNotEmpty(repoName);
    if (repoNameFiltered) {
      criteria.and(PipelineEntityKeys.repo).regex("^" + Pattern.quote(repoName) + "$", "i");
      totalRepos = 0L;
    } else {
      long phaseAStartMs = System.currentTimeMillis();
      // Account-wide distinct scan: tag SLOW so it isn't held to the FAST budget.
      List<String> distinctRepos = mongoTemplate.findDistinct(
          BudgetedQuery.withBudget(
              new Query(buildRemoteRepoBaseCriteria(accountId, orgIdentifier, projectIdentifier, scopeInfo)),
              PipelineBucket.SLOW),
          PipelineEntityKeys.repo, PipelineEntity.class, String.class);
      distinctRepos = isEmpty(distinctRepos) ? new ArrayList<>() : new ArrayList<>(distinctRepos);
      distinctRepos.sort(String.CASE_INSENSITIVE_ORDER);
      totalRepos = distinctRepos.size();
      log.info("[REMOTE_PIPELINE_METADATA] phaseA findDistinct account={} totalRepos={} page={} limit={} latencyMs={}",
          accountId, totalRepos, page, limit, System.currentTimeMillis() - phaseAStartMs);
      if (page < 0 || limit <= 0 || page * (long) limit >= totalRepos) {
        log.info("[REMOTE_PIPELINE_METADATA] short-circuit empty page account={} page={} limit={} totalRepos={} "
                + "latencyMs={}",
            accountId, page, limit, totalRepos, System.currentTimeMillis() - methodStartMs);
        return PMSPipelineRemoteRepoPage.builder().repositories(new ArrayList<>()).totalRepos(totalRepos).build();
      }
      int from = page * limit;
      int to = (int) Math.min((long) from + limit, totalRepos);
      criteria.and(PipelineEntityKeys.repo).in(new ArrayList<>(distinctRepos.subList(from, to)));
    }
    long phaseBStartMs = System.currentTimeMillis();
    Aggregation aggregation =
        Aggregation
            .newAggregation(Aggregation.match(criteria),
                Aggregation.project(PipelineEntityKeys.repo, PipelineEntityKeys.repoURL, PipelineEntityKeys.filePath,
                    PipelineEntityKeys.connectorRef, PipelineEntityKeys.accountId, PipelineEntityKeys.orgIdentifier,
                    PipelineEntityKeys.projectIdentifier, PipelineEntityKeys.parentUniqueId),
                Aggregation.group(PipelineEntityKeys.repo, PipelineEntityKeys.repoURL)
                    .count()
                    .as(REMOTE_REPO_COUNT_KEY)
                    // Capture per-entity scope alongside each filePath so consumers (e.g. the GitX webhook
                    // health endpoint) can decide which per-scope webhook governs each file. Two projects
                    // sharing a repo would otherwise be indistinguishable in a flat list of paths.
                    //
                    // $push (not $addToSet): PipelineEntity has a unique constraint on
                    // (accountId, parentUniqueId, identifier, repo, branch) and each entity has exactly
                    // one filePath, so two docs in the $match set can never share an identical filePath
                    // tuple within a single (repo, repoURL) group. $addToSet would pay O(N²) set-membership
                    // comparisons for zero dedup benefit; downstream consumers fold by filePath via
                    // putIfAbsent so an accidental duplicate would be tolerated.
                    .push(new BasicDBObject(PipelineEntityKeys.filePath, "$" + PipelineEntityKeys.filePath)
                              .append(PipelineEntityKeys.accountId, "$" + PipelineEntityKeys.accountId)
                              .append(PipelineEntityKeys.orgIdentifier, "$" + PipelineEntityKeys.orgIdentifier)
                              .append(PipelineEntityKeys.projectIdentifier, "$" + PipelineEntityKeys.projectIdentifier)
                              .append(PipelineEntityKeys.parentUniqueId, "$" + PipelineEntityKeys.parentUniqueId))
                    .as(REMOTE_REPO_FILE_PATH_TUPLES_KEY)
                    .addToSet(
                        new BasicDBObject(PipelineEntityKeys.connectorRef, "$" + PipelineEntityKeys.connectorRef)
                            .append(PipelineEntityKeys.accountId, "$" + PipelineEntityKeys.accountId)
                            .append(PipelineEntityKeys.orgIdentifier, "$" + PipelineEntityKeys.orgIdentifier)
                            .append(PipelineEntityKeys.projectIdentifier, "$" + PipelineEntityKeys.projectIdentifier))
                    .as(REMOTE_REPO_CONNECTOR_TUPLES_KEY),
                Aggregation
                    .project(REMOTE_REPO_COUNT_KEY, REMOTE_REPO_FILE_PATH_TUPLES_KEY, REMOTE_REPO_CONNECTOR_TUPLES_KEY)
                    .and("_id." + PipelineEntityKeys.repo)
                    .as(PipelineEntityKeys.repo)
                    .and("_id." + PipelineEntityKeys.repoURL)
                    .as(PipelineEntityKeys.repoURL))
            .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
    AggregationResults<RemoteRepoAggregationResult> results =
        pipelineEntityReadHelper.aggregate(aggregation, RemoteRepoAggregationResult.class);
    List<PMSPipelineRemoteRepoInfo> remoteRepoInfos = new ArrayList<>();
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
      remoteRepoInfos.add(PMSPipelineRemoteRepoInfo.builder()
                              .repoName(result.getRepo())
                              .repoURL(result.getRepoURL())
                              .count(result.getCount())
                              .filePathsByOwningScope(filePathsByOwningScope)
                              .connectorRefs(connectorRefs)
                              .build());
    }
    remoteRepoInfos.sort(java.util.Comparator.comparing(
        PMSPipelineRemoteRepoInfo::getRepoName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    if (repoNameFiltered) {
      totalRepos = remoteRepoInfos.size();
    }
    log.info("[REMOTE_PIPELINE_METADATA] phaseB aggregate account={} repoNameFiltered={} pageRepos={} totalRepos={} "
            + "phaseBLatencyMs={} totalLatencyMs={}",
        accountId, repoNameFiltered, remoteRepoInfos.size(), totalRepos, System.currentTimeMillis() - phaseBStartMs,
        System.currentTimeMillis() - methodStartMs);
    return PMSPipelineRemoteRepoPage.builder().repositories(remoteRepoInfos).totalRepos(totalRepos).build();
  }

  /**
   * Narrows the aggregation to the requested scope using {@code parentUniqueId}, which is stable across
   * project movement.
   *
   * <ul>
   *   <li>Account scope (orgIdentifier empty): no narrowing.</li>
   *   <li>Project scope: parentUniqueId == project's uniqueId.</li>
   *   <li>Org scope: parentUniqueId IN [orgUniqueId, ...all projectUniqueIds under the org]. The orgUniqueId
   *       is included so org-scoped pipelines aren't excluded; the project list is fetched via
   *       ScopeInfoClient.</li>
   * </ul>
   */
  private Criteria buildRemoteRepoBaseCriteria(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria()
                            .and(PipelineEntityKeys.accountId)
                            .is(accountId)
                            .and(PipelineEntityKeys.storeType)
                            .is(StoreType.REMOTE)
                            .and(PipelineEntityKeys.deleted)
                            .is(false);
    applyScopeFilter(criteria, accountId, orgIdentifier, projectIdentifier, scopeInfo);
    return criteria;
  }

  private void applyScopeFilter(
      Criteria criteria, String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    if (isEmpty(orgIdentifier)) {
      return;
    }
    if (isNotEmpty(projectIdentifier)) {
      String projectUniqueId = resolveProjectUniqueId(accountId, orgIdentifier, projectIdentifier, scopeInfo);
      if (isNotEmpty(projectUniqueId)) {
        criteria.and(PipelineEntityKeys.parentUniqueId).is(projectUniqueId);
      } else {
        log.warn("[REMOTE_PIPELINE_METADATA] could not resolve projectUniqueId for account={} org={} project={}",
            accountId, orgIdentifier, projectIdentifier);
      }
      return;
    }
    String orgUniqueId = resolveOrgUniqueId(accountId, orgIdentifier, scopeInfo);
    if (isEmpty(orgUniqueId)) {
      log.warn(
          "[REMOTE_PIPELINE_METADATA] could not resolve orgUniqueId for account={} org={}", accountId, orgIdentifier);
      return;
    }
    List<String> projectUniqueIds = scopeResolutionHelper.getProjectUniqueIds(accountId, orgUniqueId);
    List<String> allowedParentIds = new ArrayList<>(projectUniqueIds == null ? 1 : projectUniqueIds.size() + 1);
    allowedParentIds.add(orgUniqueId);
    if (projectUniqueIds != null) {
      allowedParentIds.addAll(projectUniqueIds);
    }
    criteria.and(PipelineEntityKeys.parentUniqueId).in(allowedParentIds);
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
  public PipelineEntity updatePipelineEntity(PipelineEntity pipelineToSave, Update pipelineUpdate,
      Criteria pipelineCriteria, Update metadataUpdate, Criteria metadataCriteria,
      MoveConfigOperationType moveConfigOperationType, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return transactionHelper.performTransaction(
        ()
            -> moveConfigOperations(pipelineToSave, pipelineUpdate, pipelineCriteria, metadataUpdate, metadataCriteria,
                moveConfigOperationType, scopeInfo, isParentIdQueryingEnabled));
  }

  @Override
  public PipelineEntity updateEntity(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return Failsafe.with(DEFAULT_RETRY_POLICY)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineEntity.class));
  }

  @Override
  public List<String> findAllPipelineIdentifiers(Criteria criteria) {
    return pipelineEntityReadHelper.findAllIdentifiers(criteria);
  }

  @Override
  public Stream<PipelineEntity> findAllFromSecondaryDb(Criteria criteria, List<String> fields) {
    // Performing query through secondary Db
    return pipelineEntityReadHelper.findAllPipelines(criteria, fields);
  }

  @Override
  public List<PipelineEntity> find(Criteria criteria) {
    return pipelineEntityReadHelper.find(criteria);
  }

  @VisibleForTesting
  PipelineEntity moveConfigOperations(PipelineEntity pipelineToMove, Update pipelineUpdate, Criteria pipelineCriteria,
      Update metadataUpdate, Criteria metadataCriteria, MoveConfigOperationType moveConfigOperationType,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String oldAttributesYaml = pipelineToMove.getGitAttributesYaml();
    //   create file if inline to remote
    if (INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      createRemoteEntity(pipelineToMove, scopeInfo, isParentIdQueryingEnabled);
    }
    //    update the mongo db
    PipelineEntity movedPipelineEntity = updatePipelineMetadata(scopeInfo, pipelineCriteria, pipelineUpdate);
    // send audit event
    try {
      if (movedPipelineEntity != null) {
        Scope scope = Scope.of(scopeInfo);
        outboxService.save(
            new PipelineMoveConfigEvent(scope, movedPipelineEntity.getIdentifier(), movedPipelineEntity.getName(),
                oldAttributesYaml, movedPipelineEntity.getGitAttributesYaml(), pmsFeatureFlagHelper));
      }
    } catch (Exception e) {
      log.warn("Audit trails for PipelineMoveConfigEvent event failed with exception: ", e);
    }
    // update the metadataV2 db
    updatePipelineMetadataV2(metadataUpdate, metadataCriteria);
    return movedPipelineEntity;
  }

  private PipelineMetadataV2 updatePipelineMetadataV2(Update update, Criteria criteria) {
    return pipelineMetadataService.update(criteria, update);
  }

  private ScmCreateFileGitResponse createRemoteEntity(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    Scope scope = Scope.of(scopeInfo);
    String yamlToPush = pipelineEntity.getYaml();
    addGitParamsToPipelineEntity(pipelineEntity, gitEntityInfo, scopeInfo, isParentIdQueryingEnabled);

    return gitAwareEntityHelper.createEntityOnGit(pipelineEntity, yamlToPush, scope);
  }
}
