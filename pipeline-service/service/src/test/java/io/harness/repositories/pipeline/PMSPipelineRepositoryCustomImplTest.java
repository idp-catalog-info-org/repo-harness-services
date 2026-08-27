/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.DANIEL;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.ScmBadRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineMoveConfigEvent;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.service.response.PipelineEntityReadHelper;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PMSPipelineRepositoryCustomImplTest extends CategoryTest {
  PMSPipelineRepositoryCustomImpl pipelineRepository;
  @Mock MongoTemplate mongoTemplate;
  @Mock GitAwarePersistence gitAwarePersistence;
  @Mock TransactionHelper transactionHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock OutboxService outboxService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock PipelineEntityReadHelper pipelineEntityReadHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String uniqueIdentifier = "uniqueId";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";
  String repoURL = "repoURL";

  String scmBadRequest = "SCM bad request";

  Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                          .is(accountIdentifier)
                          .and(PipelineEntityKeys.parentUniqueId)
                          .is(uniqueIdentifier)
                          .and(PipelineEntityKeys.identifier)
                          .is(pipelineId)
                          .and(PipelineEntityKeys.deleted)
                          .is(false);
  Query query = new Query(criteria);

  Scope scope = Scope.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .parentUniqueId(uniqueIdentifier)
                    .build();

  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .scopeType(ScopeLevel.PROJECT)
                            .uniqueId(uniqueIdentifier)
                            .build();

  String repoName = "repoName";
  String branch = "isThisMaster";
  String connectorRef = "conn";
  String filePath = "./harness/filepath.yaml";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineRepository = new PMSPipelineRepositoryCustomImpl(mongoTemplate, gitAwarePersistence, transactionHelper,
        pipelineMetadataService, gitAwareEntityHelper, outboxService, gitSyncSdkService, pipelineEntityReadHelper,
        pmsFeatureFlagService, pmsFeatureFlagHelper, scopeResolutionHelper);
    doReturn(true)
        .when(gitSyncSdkService)
        .isGitSimplificationEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(uniqueIdentifier)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(uniqueIdentifier)
                        .build());
    when(mongoTemplate.findDistinct(any(), eq(PipelineEntityKeys.repo), eq(PipelineEntity.class), eq(String.class)))
        .thenReturn(java.util.Arrays.asList("harness-core", "test-repo"));
  }

  @After
  public void tearDown() {
    // Reset GlobalContext so GitEntityInfo written by setupGitContext / populateGitDetails
    // does not leak into subsequent tests.
    GlobalContextManager.set(new GlobalContext());
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSave() {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity somethingAddedToPipelineToSave = pipelineToSave.withVersion(0L);
    doReturn(somethingAddedToPipelineToSave).when(transactionHelper).performTransaction(any());
    PipelineEntity savedEntity = pipelineRepository.save(pipelineToSave, scopeInfo, true);
    assertThat(savedEntity).isEqualTo(somethingAddedToPipelineToSave);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveInlinePipelineEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.INLINE);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, scopeInfo, true);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveRemotePipelineEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, scopeInfo, true);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    // to check if the supplier is actually called
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testSaveInlineHCPipelineEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.INLINE_HC)
                                   .connectorRef(GitSyncConstants.EMPTY)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build()
                                   .withIsHarnessCodeRepo(true);
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE_HC)
                                        .build();
    PipelineEntity pipelineToSaveWithExtraFields = pipelineToSave.withVersion(0L);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(accountIdentifier, projectIdentifier, orgIdentifier);
    doReturn(pipelineToSaveWithExtraFields).when(mongoTemplate).save(pipelineToSave);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, scopeInfo, true);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithExtraFields);
    // to check if the supplier is actually called
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  // Test pipeline entity properties for inline pipeline (both with and without metadata flag)
  public void testFindInlinePipeline() {
    PipelineEntity inlinePipelineEntity = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .yaml(pipelineYaml)
                                              .storeType(StoreType.INLINE)
                                              .build();

    doReturn(inlinePipelineEntity).when(mongoTemplate).findOne(query, PipelineEntity.class);
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    PipelineEntity pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(inlinePipelineEntity);
    assertThat(pipelineEntityFound.getYaml()).isNotEmpty();
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());

    Query withMetadataQuery = query;
    for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
      withMetadataQuery.fields().exclude(nonMetadataField);
    }
    PipelineEntity withMetadataEntity = PipelineEntity.builder()
                                            .accountId(accountIdentifier)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .identifier(pipelineId)
                                            .storeType(StoreType.INLINE)
                                            .build();

    doReturn(withMetadataEntity).when(mongoTemplate).findOne(withMetadataQuery, PipelineEntity.class);
    optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false, scopeInfo, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(withMetadataEntity);
    assertThat(pipelineEntityFound.getYaml()).isNullOrEmpty();
    verify(gitAwareEntityHelper, times(0)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFindRemotePipeline() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(query, PipelineEntity.class);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    PipelineEntity pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(remotePipelineWithYAML);
    assertThat(pipelineEntityFound.getYaml()).isNotEmpty();
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());

    // With metadata flag true test
    Query withMetadataQuery = query;
    for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
      withMetadataQuery.fields().exclude(nonMetadataField);
    }

    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(withMetadataQuery, PipelineEntity.class);
    optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false, scopeInfo, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    pipelineEntityFound = optionalPipelineEntity.get();
    assertThat(pipelineEntityFound).isEqualTo(remotePipelineFromDB);
    assertThat(pipelineEntityFound.getYaml()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemotePipelineWithLoadFromCache() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineFromDB).when(mongoTemplate).findOne(query, PipelineEntity.class);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    Optional<PipelineEntity> optionalPipelineEntity = pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true, scopeInfo, true);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(remotePipelineWithYAML);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateInlinePipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.INLINE)
                                          .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name("new name")
                                        .description("new desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.INLINE)
                                        .version(1L)
                                        .build();
    doReturn(pipelineEntity).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate, false, scopeInfo, true);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(0)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateRemotePipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name("new name")
                                        .description("new desc")
                                        .yaml(newYaml)
                                        .storeType(StoreType.REMOTE)
                                        .version(1L)
                                        .build();
    doReturn(pipelineEntity).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate, false, scopeInfo, true);
    assertThat(updatedEntity.getYaml()).isEqualTo(newYaml);
    assertThat(updatedEntity.getName()).isEqualTo("new name");
    assertThat(updatedEntity.getDescription()).isEqualTo("new desc");
    verify(gitAwareEntityHelper, times(1)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPatchV1RemotePipelineMetadataOnlySkipsGitUpdate() {
    String yamlInStore = "pipeline:\n  identifier: pipeline\n  name: Pipeline";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(null)
                                          .harnessVersion(HarnessYamlVersion.V1)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name("new name")
                                        .description("new desc")
                                        .yaml(yamlInStore)
                                        .harnessVersion(HarnessYamlVersion.V1)
                                        .storeType(StoreType.REMOTE)
                                        .version(1L)
                                        .build();
    doReturn(pipelineEntity).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate, true, scopeInfo, true);
    assertThat(updatedEntity.getYaml()).isEqualTo(yamlInStore);
    verify(gitAwareEntityHelper, times(0)).updateEntityOnGit(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateNonExistentPipeline() {
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    doReturn(null).when(transactionHelper).performTransaction(any());
    PipelineEntity updatedEntity = pipelineRepository.updatePipelineYaml(pipelineToUpdate, false, scopeInfo, true);
    assertThat(updatedEntity).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineEntityInDB() {
    Query query = new Query();
    Update update = new Update();
    String newYaml = "pipeline: new yaml";
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name("new name")
                                          .description("new desc")
                                          .yaml(newYaml)
                                          .storeType(StoreType.REMOTE)
                                          .build();
    PipelineEntity oldEntityFromDB = PipelineEntity.builder()
                                         .accountId(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier(pipelineId)
                                         .name("name")
                                         .description("desc")
                                         .yaml(newYaml)
                                         .storeType(StoreType.REMOTE)
                                         .createdAt(0L)
                                         .build();
    doReturn(oldEntityFromDB).when(mongoTemplate).findAndModify(any(), any(), any(), any(Class.class));
    PipelineEntity pipelineEntity =
        pipelineRepository.updatePipelineEntityInDB(query, update, pipelineToUpdate, 1L, false, scopeInfo, true);
    assertThat(pipelineEntity.getCreatedAt()).isEqualTo(0L);
    assertThat(pipelineEntity.getLastUpdatedAt()).isEqualTo(1L);
    assertThat(pipelineEntity.getYaml()).isEqualTo(newYaml);
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    Update update = new Update();
    update.set(PipelineEntityKeys.deleted, true);
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .deleted(true)
                                        .build();
    doReturn(pipelineEntity).when(mongoTemplate).findAndRemove(any(), any());
    pipelineRepository.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId);

    ArgumentCaptor<TransactionHelper.TransactionFunction> captor =
        ArgumentCaptor.forClass(TransactionHelper.TransactionFunction.class);
    verify(transactionHelper, times(1)).performTransaction(captor.capture());
    captor.getValue().execute();
    verify(mongoTemplate, times(1)).findAndRemove(any(), any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteAllPipelineInProject() {
    Update update = new Update();
    update.set(PipelineEntityKeys.deleted, true);
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .deleted(true)
                                        .build();
    List<PipelineEntity> entityList = Arrays.asList(pipelineEntity);
    doReturn(entityList).when(mongoTemplate).findAllAndRemove(any(), (Class<PipelineEntity>) any());
    pipelineRepository.deleteAllPipelinesInAProject(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, true);
    verify(mongoTemplate, times(1)).findAllAndRemove(any(), (Class<PipelineEntity>) any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCountFileInstances() {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFileUniquenessCheck(accountIdentifier, repoURL, filePath);
    Query query = new Query(criteria);
    doReturn(17L).when(pipelineEntityReadHelper).findCount(query);
    assertThat(pipelineRepository.countFileInstances(accountIdentifier, repoURL, filePath)).isEqualTo(17L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNotPresent() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false, scopeInfo, true));
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWhenLoadFromCache() {
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .branch(branch)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);
    doReturn(remotePipelineWithYAML).when(gitAwareEntityHelper).fetchEntityFromRemote(any(), any(), any(), any());
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, true, scopeInfo, true);
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsSameDefaultBranch() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false, scopeInfo, true));
    verify(gitAwareEntityHelper, times(1)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsDifferentDefaultBranch() {
    // when fetch from the default branch fails and branch present in metadata are different from fetched branch
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .branch(branch)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);

    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any());

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doReturn(remotePipelineWithYAML)
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false, scopeInfo, true);
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void
  testFetchRemoteEntityWithRetryWhenDefaultFailsAndCreatedBranchNameIsDifferentDefaultBrancAndGitContextIsSet() {
    // when fetch from the default branch fails and branch present in metadata are different from fetched branch
    PipelineEntity remotePipelineFromDB = PipelineEntity.builder()
                                              .accountId(accountIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(pipelineId)
                                              .storeType(StoreType.REMOTE)
                                              .connectorRef(connectorRef)
                                              .repo(repoName)
                                              .filePath(filePath)
                                              .build();
    PipelineEntity remotePipelineWithYAML = remotePipelineFromDB.withYaml(pipelineYaml);

    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any());

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doReturn(remotePipelineWithYAML)
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());

    pipelineRepository.fetchRemoteEntityWithFallBackBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false, scopeInfo, true);

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
    assertEquals(fallBackBranch, gitEntityInfo.getBranch());
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchRemoteEntityWithRetryWhenDefaultFailsAndNonDefaultFails() {
    // when fetch from default fails and branch present in metadata also fails
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    String fallBackBranch = "main-patch1";
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .entityGitDetails(EntityGitDetails.builder().branch(fallBackBranch).build())
            .build();
    Optional<PipelineMetadataV2> pipelineMetadataV2Mock = Optional.of(pipelineMetadataV2);
    doReturn(pipelineMetadataV2Mock).when(pipelineMetadataService).getMetadata(any(), any(), any());
    doThrow(new ScmBadRequestException(scmBadRequest))
        .doThrow(new ScmBadRequestException(scmBadRequest))
        .when(gitAwareEntityHelper)
        .fetchEntityFromRemote(any(), any(), any(), any());
    assertThrows(ScmBadRequestException.class,
        ()
            -> pipelineRepository.fetchRemoteEntityWithFallBackBranch(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineEntity, branch, false, scopeInfo, true));
    verify(gitAwareEntityHelper, times(2)).fetchEntityFromRemote(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdatePipelineOperationsInlineToRemote() {
    Criteria pipelineCriteria = new Criteria();
    Update pipelineUpdate = new Update();
    Criteria metadataCriteria = new Criteria();
    Update metadataUpdate = new Update();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields)
        .when(mongoTemplate)
        .findAndModify(any(), any(), any(), any(Class.class));

    PipelineEntity movedPipeline = pipelineRepository.moveConfigOperations(pipelineToSave, pipelineUpdate,
        pipelineCriteria, metadataUpdate, metadataCriteria, INLINE_TO_REMOTE, scopeInfo, true);
    verify(gitAwareEntityHelper, times(1)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);

    verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), any(Class.class));
    verify(pipelineMetadataService, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpdatePipelineOperationsRemoteToInline() {
    Criteria pipelineCriteria = new Criteria();
    Update pipelineUpdate = new Update();
    Criteria metadataCriteria = new Criteria();
    Update metadataUpdate = new Update();

    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();

    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields)
        .when(mongoTemplate)
        .findAndModify(any(), any(), any(), any(Class.class));

    PipelineEntity movedPipeline = pipelineRepository.moveConfigOperations(pipelineToSave, pipelineUpdate,
        pipelineCriteria, metadataUpdate, metadataCriteria, REMOTE_TO_INLINE, scopeInfo, true);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(pipelineToSave, pipelineYaml, scope);

    verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), any(Class.class));
    verify(pipelineMetadataService, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testMoveConfigOperationsWithFeatureFlagOnAndOff() {
    // Setup test data
    Criteria pipelineCriteria = new Criteria();
    Update pipelineUpdate = new Update();
    Criteria metadataCriteria = new Criteria();
    Update metadataUpdate = new Update();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);

    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();

    PipelineEntity pipelineAfterMove = pipelineToSave.withStoreType(StoreType.REMOTE)
                                           .withRepo(repoName)
                                           .withConnectorRef(connectorRef)
                                           .withFilePath(filePath)
                                           .withVersion(1L);

    // Mock responses
    when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(pipelineAfterMove);
    when(pipelineMetadataService.update(any(), any())).thenReturn(null);

    pipelineRepository.moveConfigOperations(pipelineToSave, pipelineUpdate, pipelineCriteria, metadataUpdate,
        metadataCriteria, INLINE_TO_REMOTE, scopeInfo, true);

    verify(outboxService, times(1)).save(any(PipelineMoveConfigEvent.class));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFind_whenIsParentIdQueryingEnabledIsTrue() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId("xyz")
                              .build();
    when(mongoTemplate.findOne(any(Query.class), eq(PipelineEntity.class))).thenReturn(pipelineEntity);

    pipelineRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false, scopeInfo, true);

    verify(mongoTemplate, times(1))
        .findOne(argThat(query -> {
          Document document = query.getQueryObject();
          return "xyz".equals(document.get("parentUniqueId"));
        }),
            eq(PipelineEntity.class));
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testSaveRemotePipelineEntitySendsOutboxEvents() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    pipelineRepository.savePipelineEntity(pipelineToSave, scopeInfo, true);
    // verify outbox events
    verify(outboxService, times(1)).save(any(PipelineCreateEvent.class));
    verify(outboxService, times(1)).save(any(PipelineMoveConfigEvent.class));
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testSavePipelineSendsOnlyCreateEvent() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(connectorRef)
                                   .repoName(repoName)
                                   .branch(branch)
                                   .filePath(filePath)
                                   .build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.REMOTE)
                                                     .withConnectorRef(connectorRef)
                                                     .withRepo(repoName)
                                                     .withFilePath(filePath);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveInlinePipelineDoesNotSendMoveOutboxEvent() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.INLINE).build();
    setupGitContext(branchInfo);
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    PipelineEntity pipelineToSaveWithStoreType = pipelineToSave.withStoreType(StoreType.INLINE);
    PipelineEntity pipelineToSaveWithStoreTypeWithExtraFields =
        pipelineToSave.withStoreType(StoreType.INLINE).withVersion(0L);
    doReturn(pipelineToSaveWithStoreTypeWithExtraFields).when(mongoTemplate).save(pipelineToSaveWithStoreType);

    PipelineEntity savedPipelineEntity = pipelineRepository.savePipelineEntity(pipelineToSave, scopeInfo, true);
    assertThat(savedPipelineEntity).isEqualTo(pipelineToSaveWithStoreTypeWithExtraFields);
    verify(gitAwareEntityHelper, times(0)).createEntityOnGit(any(), any(), any());
    // should not be called if the entity is inline
    verify(outboxService, never()).save(any(PipelineMoveConfigEvent.class));
  }

  private Document extractMatchCriteria(Aggregation aggregation) {
    AggregationOperation matchOp = aggregation.getPipeline().getOperations().get(0);
    assertThat(matchOp).isInstanceOf(MatchOperation.class);
    return matchOp.toDocument(Aggregation.DEFAULT_CONTEXT);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForAccountWithoutRepoNameDoesNotAddRepoCriteria() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));

    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    assertThat(match.get(PipelineEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(match.get(PipelineEntityKeys.storeType)).isEqualTo(StoreType.REMOTE);
    assertThat(match.get(PipelineEntityKeys.deleted)).isEqualTo(false);
    assertThat(((Document) match.get(PipelineEntityKeys.repo)).containsKey("$in")).isTrue();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForAccountWithRepoNameUsesCaseInsensitiveAnchoredRegex() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, "Harness-Core", null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));

    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    Object repoFilter = match.get(PipelineEntityKeys.repo);
    assertThat(repoFilter).isInstanceOf(java.util.regex.Pattern.class);
    java.util.regex.Pattern pattern = (java.util.regex.Pattern) repoFilter;
    assertThat(pattern.pattern()).isEqualTo("^\\QHarness-Core\\E$");
    assertThat(pattern.flags() & java.util.regex.Pattern.CASE_INSENSITIVE)
        .isEqualTo(java.util.regex.Pattern.CASE_INSENSITIVE);

    // The regex should match the same name in any casing.
    assertThat(pattern.matcher("Harness-Core").matches()).isTrue();
    assertThat(pattern.matcher("harness-core").matches()).isTrue();
    assertThat(pattern.matcher("HARNESS-CORE").matches()).isTrue();
    // ...and should NOT match a different repo or a substring.
    assertThat(pattern.matcher("harness-core-fork").matches()).isFalse();
    assertThat(pattern.matcher("not-harness-core").matches()).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForAccountEscapesRegexMetaCharsInRepoName() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String repoWithRegexChars = "team.repo+.*";
    pipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, null, null, repoWithRegexChars, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document matchDoc = extractMatchCriteria(aggregationCaptor.getValue());
    Document match = (Document) matchDoc.get("$match");
    java.util.regex.Pattern pattern = (java.util.regex.Pattern) match.get(PipelineEntityKeys.repo);

    // Exact matches with the literal characters.
    assertThat(pattern.matcher("team.repo+.*").matches()).isTrue();
    assertThat(pattern.matcher("TEAM.repo+.*").matches()).isTrue();
    // Must NOT treat the dot/plus/asterisk as regex meta-characters.
    assertThat(pattern.matcher("teamXrepo+.*").matches()).isFalse();
    assertThat(pattern.matcher("team.repoo.*").matches()).isFalse();
    assertThat(pattern.matcher("team.repo+abc").matches()).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeMapsAggregationResultToDtos() {
    PMSPipelineRepositoryCustomImpl.ConnectorTuple projectScopedTuple =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    projectScopedTuple.setConnectorRef("githubMain");
    projectScopedTuple.setAccountId(accountIdentifier);
    projectScopedTuple.setOrgIdentifier("default");
    projectScopedTuple.setProjectIdentifier("cdProject");

    PMSPipelineRepositoryCustomImpl.ConnectorTuple orgScopedTuple =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    orgScopedTuple.setConnectorRef("org.sharedGithub");
    orgScopedTuple.setAccountId(accountIdentifier);
    orgScopedTuple.setOrgIdentifier("default");
    orgScopedTuple.setProjectIdentifier(null);

    PMSPipelineRepositoryCustomImpl.ConnectorTuple accountScopedTuple =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    accountScopedTuple.setConnectorRef("account.accountGithub");
    accountScopedTuple.setAccountId(accountIdentifier);
    accountScopedTuple.setOrgIdentifier(null);
    accountScopedTuple.setProjectIdentifier(null);

    Set<PMSPipelineRepositoryCustomImpl.ConnectorTuple> tuples = new HashSet<>();
    tuples.add(projectScopedTuple);
    tuples.add(orgScopedTuple);
    tuples.add(accountScopedTuple);

    PMSPipelineRepositoryCustomImpl.FilePathTuple buildTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    buildTuple.setFilePath(".harness/build.yaml");
    buildTuple.setAccountId(accountIdentifier);
    buildTuple.setOrgIdentifier("default");
    buildTuple.setProjectIdentifier("cdProject");
    buildTuple.setParentUniqueId("uid-cdProject");

    PMSPipelineRepositoryCustomImpl.FilePathTuple deployTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    deployTuple.setFilePath(".harness/deploy.yaml");
    deployTuple.setAccountId(accountIdentifier);
    deployTuple.setOrgIdentifier("default");
    deployTuple.setProjectIdentifier("cdProject");
    deployTuple.setParentUniqueId("uid-cdProject");

    List<PMSPipelineRepositoryCustomImpl.FilePathTuple> filePathTuples = new ArrayList<>();
    filePathTuples.add(buildTuple);
    filePathTuples.add(deployTuple);

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("harness-core");
    result.setRepoURL("https://github.com/wings-software/harness-core");
    result.setCount(5L);
    result.setFilePathTuples(filePathTuples);
    result.setConnectorTuples(tuples);

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    PMSPipelineRemoteRepoInfo info = infos.get(0);
    assertThat(info.getRepoName()).isEqualTo("harness-core");
    assertThat(info.getRepoURL()).isEqualTo("https://github.com/wings-software/harness-core");
    assertThat(info.getCount()).isEqualTo(5L);
    assertThat(info.getFilePathsByOwningScope().keySet())
        .containsExactlyInAnyOrder(".harness/build.yaml", ".harness/deploy.yaml");
    // Per-file owning scope carries parentUniqueId so downstream consumers (the GitX webhook health
    // endpoint) can decide which per-scope webhook governs each file.
    assertThat(info.getFilePathsByOwningScope().get(".harness/build.yaml").getParentUniqueId())
        .isEqualTo("uid-cdProject");
    assertThat(info.getConnectorRefs())
        .containsExactlyInAnyOrder(accountIdentifier + "/default/cdProject/githubMain",
            accountIdentifier + "/default/sharedGithub", accountIdentifier + "/accountGithub");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeProjectScopeUsesScopeInfoUniqueIdDirectly() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String projectUniqueId = "projUid-123";
    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .uniqueId(projectUniqueId)
                                     .build();

    pipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, null, projectScopeInfo, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    assertThat(match.get(PipelineEntityKeys.parentUniqueId)).isEqualTo(projectUniqueId);
    // Did NOT need to call ScopeInfoClient since scopeInfo already had uniqueId.
    verify(scopeResolutionHelper, never()).getProjectUniqueIds(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeProjectScopeFallsBackToHelperWhenScopeInfoMissing() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String resolvedProjUid = "resolvedProjUid";
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(resolvedProjUid)
                        .build());

    pipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    assertThat(match.get(PipelineEntityKeys.parentUniqueId)).isEqualTo(resolvedProjUid);
    verify(scopeResolutionHelper, times(2)).getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeOrgScopeIncludesOrgAndAllProjectUniqueIds() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String orgUniqueId = "orgUid-1";
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(accountIdentifier)
                                 .orgIdentifier(orgIdentifier)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId(orgUniqueId)
                                 .build();
    when(scopeResolutionHelper.getProjectUniqueIds(accountIdentifier, orgUniqueId))
        .thenReturn(Arrays.asList("p1", "p2", "p3"));

    pipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, null, null, orgScopeInfo, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    Object parentUniqueIdFilter = match.get(PipelineEntityKeys.parentUniqueId);
    assertThat(parentUniqueIdFilter).isInstanceOf(Document.class);
    @SuppressWarnings("unchecked") List<String> inList = (List<String>) ((Document) parentUniqueIdFilter).get("$in");
    assertThat(inList).containsExactlyInAnyOrder(orgUniqueId, "p1", "p2", "p3");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeOrgScopeFallsBackToHelperWhenScopeInfoMissing() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    String resolvedOrgUid = "resolvedOrgUid";
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, null))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .scopeType(ScopeLevel.ORGANIZATION)
                        .uniqueId(resolvedOrgUid)
                        .build());
    when(scopeResolutionHelper.getProjectUniqueIds(accountIdentifier, resolvedOrgUid)).thenReturn(null);

    pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, orgIdentifier, null, null, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    Object parentUniqueIdFilter = match.get(PipelineEntityKeys.parentUniqueId);
    assertThat(parentUniqueIdFilter).isInstanceOf(Document.class);
    @SuppressWarnings("unchecked") List<String> inList = (List<String>) ((Document) parentUniqueIdFilter).get("$in");
    // Project list null → only orgUniqueId should be in the filter.
    assertThat(inList).containsExactly(resolvedOrgUid);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForAccountGivenScopeScopeAddsNoParentUniqueIdFilter() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
        .getRepositories();

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    assertThat(match.containsKey(PipelineEntityKeys.parentUniqueId)).isFalse();
    verify(scopeResolutionHelper, never()).getProjectUniqueIds(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeSkipsConnectorFqnWhenConnectorRefIsEmpty() {
    PMSPipelineRepositoryCustomImpl.ConnectorTuple validTuple = new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    validTuple.setConnectorRef("conn1");
    validTuple.setAccountId(accountIdentifier);
    validTuple.setOrgIdentifier("o");
    validTuple.setProjectIdentifier("p");

    PMSPipelineRepositoryCustomImpl.ConnectorTuple emptyRefTuple = new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    emptyRefTuple.setConnectorRef("");
    emptyRefTuple.setAccountId(accountIdentifier);

    PMSPipelineRepositoryCustomImpl.ConnectorTuple nullRefTuple = new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    nullRefTuple.setConnectorRef(null);
    nullRefTuple.setAccountId(accountIdentifier);

    Set<PMSPipelineRepositoryCustomImpl.ConnectorTuple> tuples = new HashSet<>();
    tuples.add(validTuple);
    tuples.add(emptyRefTuple);
    tuples.add(nullRefTuple);

    PMSPipelineRepositoryCustomImpl.FilePathTuple xTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    xTuple.setFilePath(".harness/x.yaml");
    xTuple.setAccountId(accountIdentifier);
    xTuple.setOrgIdentifier("o");
    xTuple.setProjectIdentifier("p");
    xTuple.setParentUniqueId("uid-p");

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(1L);
    result.setFilePathTuples(new ArrayList<>(Arrays.asList(xTuple)));
    result.setConnectorTuples(tuples);

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    // Empty/null connectorRef tuples should be filtered out, leaving only the project-scoped FQN.
    assertThat(infos.get(0).getConnectorRefs()).containsExactly(accountIdentifier + "/o/p/conn1");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeReturnsEmptyListWhenNoMatches() {
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).isNotNull();
    assertThat(infos).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeHandlesNullTupleListsFromAggregation() {
    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(0L);
    // Both null to exercise the defensive null guards in the mapping loop.
    result.setFilePathTuples(null);
    result.setConnectorTuples(null);

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    assertThat(infos.get(0).getFilePathsByOwningScope()).isEmpty();
    assertThat(infos.get(0).getConnectorRefs()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeSkipsFilePathTuplesWithNullOrEmptyPath() {
    PMSPipelineRepositoryCustomImpl.FilePathTuple validTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    validTuple.setFilePath(".harness/build.yaml");
    validTuple.setAccountId(accountIdentifier);
    validTuple.setOrgIdentifier("o");
    validTuple.setProjectIdentifier("p");
    validTuple.setParentUniqueId("uid-p");

    PMSPipelineRepositoryCustomImpl.FilePathTuple nullPathTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    nullPathTuple.setFilePath(null);

    PMSPipelineRepositoryCustomImpl.FilePathTuple emptyPathTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    emptyPathTuple.setFilePath("");

    List<PMSPipelineRepositoryCustomImpl.FilePathTuple> tuples = new ArrayList<>();
    tuples.add(validTuple);
    tuples.add(nullPathTuple);
    tuples.add(emptyPathTuple);
    // A null tuple in the list should also be silently skipped.
    tuples.add(null);

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(4L);
    result.setFilePathTuples(tuples);
    result.setConnectorTuples(new HashSet<>());

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    assertThat(infos.get(0).getFilePathsByOwningScope().keySet()).containsExactly(".harness/build.yaml");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeDedupesDuplicateFilePathsAndKeepsFirstScope() {
    PMSPipelineRepositoryCustomImpl.FilePathTuple firstTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    firstTuple.setFilePath(".harness/build.yaml");
    firstTuple.setAccountId(accountIdentifier);
    firstTuple.setOrgIdentifier("orgA");
    firstTuple.setProjectIdentifier("projA");
    firstTuple.setParentUniqueId("uid-projA");

    PMSPipelineRepositoryCustomImpl.FilePathTuple duplicateTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    duplicateTuple.setFilePath(".harness/build.yaml");
    duplicateTuple.setAccountId(accountIdentifier);
    duplicateTuple.setOrgIdentifier("orgB");
    duplicateTuple.setProjectIdentifier("projB");
    duplicateTuple.setParentUniqueId("uid-projB");

    List<PMSPipelineRepositoryCustomImpl.FilePathTuple> tuples = new ArrayList<>();
    tuples.add(firstTuple);
    tuples.add(duplicateTuple);

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(2L);
    result.setFilePathTuples(tuples);
    result.setConnectorTuples(new HashSet<>());

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    // putIfAbsent: first occurrence wins for the same filePath within a repo.
    assertThat(infos.get(0).getFilePathsByOwningScope()).hasSize(1);
    assertThat(infos.get(0).getFilePathsByOwningScope().get(".harness/build.yaml").getParentUniqueId())
        .isEqualTo("uid-projA");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeMapsMultipleRepoGroupsIndependently() {
    PMSPipelineRepositoryCustomImpl.FilePathTuple repoATuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    repoATuple.setFilePath(".harness/a.yaml");
    repoATuple.setAccountId(accountIdentifier);
    repoATuple.setOrgIdentifier("o");
    repoATuple.setProjectIdentifier("p");
    repoATuple.setParentUniqueId("uid-p");

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult repoA =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    repoA.setRepo("repoA");
    repoA.setRepoURL("https://repoA");
    repoA.setCount(1L);
    repoA.setFilePathTuples(new ArrayList<>(Arrays.asList(repoATuple)));
    repoA.setConnectorTuples(new HashSet<>());

    PMSPipelineRepositoryCustomImpl.FilePathTuple repoBTuple = new PMSPipelineRepositoryCustomImpl.FilePathTuple();
    repoBTuple.setFilePath(".harness/b.yaml");
    repoBTuple.setAccountId(accountIdentifier);
    repoBTuple.setOrgIdentifier("o");
    repoBTuple.setProjectIdentifier("p");
    repoBTuple.setParentUniqueId("uid-p");

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult repoB =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    repoB.setRepo("repoB");
    repoB.setRepoURL("https://repoB");
    repoB.setCount(7L);
    repoB.setFilePathTuples(new ArrayList<>(Arrays.asList(repoBTuple)));
    repoB.setConnectorTuples(new HashSet<>());

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(Arrays.asList(repoA, repoB), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(2);
    assertThat(infos).extracting(PMSPipelineRemoteRepoInfo::getRepoName).containsExactly("repoA", "repoB");
    assertThat(infos.get(0).getFilePathsByOwningScope().keySet()).containsExactly(".harness/a.yaml");
    assertThat(infos.get(0).getCount()).isEqualTo(1L);
    assertThat(infos.get(1).getFilePathsByOwningScope().keySet()).containsExactly(".harness/b.yaml");
    assertThat(infos.get(1).getCount()).isEqualTo(7L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeBuildConnectorFqnGuardsMalformedTuples() {
    // Unprefixed ref but missing org → must be dropped (NG convention: unprefixed = project-scoped).
    PMSPipelineRepositoryCustomImpl.ConnectorTuple unprefixedNoOrg =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    unprefixedNoOrg.setConnectorRef("conn");
    unprefixedNoOrg.setAccountId(accountIdentifier);
    unprefixedNoOrg.setOrgIdentifier(null);
    unprefixedNoOrg.setProjectIdentifier(null);

    // Unprefixed ref with org but no project → must also be dropped.
    PMSPipelineRepositoryCustomImpl.ConnectorTuple unprefixedNoProject =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    unprefixedNoProject.setConnectorRef("conn");
    unprefixedNoProject.setAccountId(accountIdentifier);
    unprefixedNoProject.setOrgIdentifier("o");
    unprefixedNoProject.setProjectIdentifier(null);

    // org.-prefixed ref but missing org → must be dropped.
    PMSPipelineRepositoryCustomImpl.ConnectorTuple orgPrefixedNoOrg =
        new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    orgPrefixedNoOrg.setConnectorRef("org.conn");
    orgPrefixedNoOrg.setAccountId(accountIdentifier);
    orgPrefixedNoOrg.setOrgIdentifier(null);

    // Missing accountId → must be dropped regardless of ref shape.
    PMSPipelineRepositoryCustomImpl.ConnectorTuple noAccount = new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    noAccount.setConnectorRef("account.conn");
    noAccount.setAccountId(null);

    // Sanity: a well-formed account-scoped tuple should survive.
    PMSPipelineRepositoryCustomImpl.ConnectorTuple validAccount = new PMSPipelineRepositoryCustomImpl.ConnectorTuple();
    validAccount.setConnectorRef("account.accConn");
    validAccount.setAccountId(accountIdentifier);

    Set<PMSPipelineRepositoryCustomImpl.ConnectorTuple> tuples = new HashSet<>();
    tuples.add(unprefixedNoOrg);
    tuples.add(unprefixedNoProject);
    tuples.add(orgPrefixedNoOrg);
    tuples.add(noAccount);
    tuples.add(validAccount);

    PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult result =
        new PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult();
    result.setRepo("r1");
    result.setRepoURL("https://r1");
    result.setCount(0L);
    result.setFilePathTuples(new ArrayList<>());
    result.setConnectorTuples(tuples);

    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.singletonList(result), new Document()));

    List<PMSPipelineRemoteRepoInfo> infos =
        pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, null, null, null, null, 0, 1000)
            .getRepositories();

    assertThat(infos).hasSize(1);
    // Only the well-formed account-scoped tuple should make it through.
    assertThat(infos.get(0).getConnectorRefs()).containsExactly(accountIdentifier + "/accConn");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeProjectScopeOmitsParentUniqueIdWhenUidUnresolvable() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    // No scopeInfo, and helper returns null → projectUniqueId is unresolvable.
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(null);

    pipelineRepository.findRemoteRepoInfosForGivenScope(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    // When projectUniqueId can't be resolved, no parentUniqueId narrowing is applied.
    assertThat(match.containsKey(PipelineEntityKeys.parentUniqueId)).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFindRemoteRepoInfosForGivenScopeOrgScopeOmitsParentUniqueIdWhenOrgUidUnresolvable() {
    ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(pipelineEntityReadHelper.aggregate(
             any(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class)))
        .thenReturn(new AggregationResults<>(java.util.Collections.emptyList(), new Document()));

    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(null);

    pipelineRepository.findRemoteRepoInfosForGivenScope(accountIdentifier, orgIdentifier, null, null, null, 0, 1000);

    verify(pipelineEntityReadHelper, times(1))
        .aggregate(aggregationCaptor.capture(), eq(PMSPipelineRepositoryCustomImpl.RemoteRepoAggregationResult.class));
    Document match = (Document) extractMatchCriteria(aggregationCaptor.getValue()).get("$match");
    // When orgUniqueId can't be resolved, no parentUniqueId narrowing is applied, and no project lookup is attempted.
    assertThat(match.containsKey(PipelineEntityKeys.parentUniqueId)).isFalse();
    verify(scopeResolutionHelper, never()).getProjectUniqueIds(any(), any());
  }
}
