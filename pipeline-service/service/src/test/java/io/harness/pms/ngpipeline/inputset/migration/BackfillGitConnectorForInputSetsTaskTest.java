/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.migration;

import static io.harness.gitsync.beans.StoreType.REMOTE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.setupusage.InputSetSetupUsageHelper;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class BackfillGitConnectorForInputSetsTaskTest extends PipelineServiceTestBase {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private PersistentLocker persistentLocker;
  @Mock private InputSetSetupUsageHelper inputSetSetupUsageHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @Mock private FeatureFlagService featureFlagService;

  private BackfillGitConnectorForInputSetsTask task;
  private final List<InputSetConnectorBackfillMigrationStatus> statusSaveSnapshots = new ArrayList<>();

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PARENT_UNIQUE_ID = "accountId/orgId/projectId";
  private static final String CONNECTOR_REF = "account.myConnector";
  private static final String REPO_NAME = "test-repo";
  private static final String BRANCH_NAME = "main";
  private static final String FILE_PATH = ".harness/inputset.yaml";

  @Before
  public void setup() {
    statusSaveSnapshots.clear();
    task = new BackfillGitConnectorForInputSetsTask(mongoTemplate, persistentLocker, inputSetSetupUsageHelper,
        scopeResolutionHelper, pmsFeatureFlagHelper, gitAwareEntityHelper, featureFlagService);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_marksCompleteWhenNoBatchReturned() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class))).thenReturn(Collections.emptyList());

    task.run();

    ArgumentCaptor<InputSetConnectorBackfillMigrationStatus> statusCaptor =
        ArgumentCaptor.forClass(InputSetConnectorBackfillMigrationStatus.class);
    verify(mongoTemplate, times(2)).save(statusCaptor.capture());

    InputSetConnectorBackfillMigrationStatus savedStatus = statusCaptor.getAllValues().get(1);
    assertThat(savedStatus.getMigrationCompleted()).isTrue();
    assertThat(savedStatus.getLastProcessedTimestamp()).isNull();
    assertThat(savedStatus.getLastProcessedUuid()).isNull();
    assertThat(savedStatus.getTotalProcessed()).isEqualTo(0L);
    assertThat(savedStatus.getTotalGitCalls()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_skipsWhenAlreadyCompleted() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(true)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);

    task.run();

    verify(mongoTemplate, never()).find(any(Query.class), eq(InputSetEntity.class));
    verify(inputSetSetupUsageHelper, never()).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_skipsWhenLockNotAcquired() {
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(null);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    task.run();

    verify(mongoTemplate, never()).findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class));
    verify(mongoTemplate, never()).find(any(Query.class), eq(InputSetEntity.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_skipsWhenFeatureFlagEnabled() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(true);

    task.run();

    verify(persistentLocker, never()).tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class));
    verify(mongoTemplate, never()).findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class));
    verify(mongoTemplate, never()).find(any(Query.class), eq(InputSetEntity.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testFetchBatch_withNullTimestamp_returnsCorrectInputSets() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity remoteInputSet = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier("inputSet1")
                                        .storeType(REMOTE)
                                        .connectorRef(CONNECTOR_REF)
                                        .repo(REPO_NAME)
                                        .filePath(FILE_PATH)
                                        .createdAt(1000L)
                                        .deleted(false)
                                        .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Arrays.asList(remoteInputSet))
        .thenReturn(Collections.emptyList());

    doNothing().when(inputSetSetupUsageHelper).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());

    try (MockedStatic<GitAwareContextHelper> mockedStatic = Mockito.mockStatic(GitAwareContextHelper.class)) {
      ScmGitMetaData gitMetaData =
          ScmGitMetaData.builder()
              .branchName(BRANCH_NAME)
              .repoName(REPO_NAME)
              .cacheResponse(CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build())
              .build();

      mockedStatic.when(GitAwareContextHelper::getScmGitMetaData).thenReturn(gitMetaData);
      mockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(anyString())).thenReturn(false);

      task.run();

      ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
      verify(mongoTemplate, times(2)).find(queryCaptor.capture(), eq(InputSetEntity.class));

      Query firstQuery = queryCaptor.getAllValues().get(0);
      assertThat(firstQuery.toString()).contains(InputSetEntityKeys.storeType);
      assertThat(firstQuery.toString()).contains(InputSetEntityKeys.connectorRef);
      assertThat(firstQuery.toString()).contains(InputSetEntityKeys.deleted);
      assertThat(firstQuery.toString()).contains("$nin");
      assertThat(firstQuery.toString()).contains(InputSetEntityKeys.uuid);
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testFetchBatch_withTimestampAndUuid_usesCompoundCursor() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(1500L)
                                                          .lastProcessedUuid("uuid-1")
                                                          .build();
    stubStatusPersistence(status);

    InputSetEntity inputSet = createRemoteInputSet("inputSet1", 1500L, "uuid-2");
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Collections.singletonList(inputSet))
        .thenReturn(Collections.emptyList());

    runWithSuccessfulGitFetch();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate, times(2)).find(queryCaptor.capture(), eq(InputSetEntity.class));

    Query queryWithCompoundCursor = queryCaptor.getAllValues().get(0);
    assertThat(queryWithCompoundCursor.toString()).contains(InputSetEntityKeys.createdAt);
    assertThat(queryWithCompoundCursor.toString()).contains(InputSetEntityKeys.uuid);
    assertThat(queryWithCompoundCursor.toString()).contains("$or");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_savesLastProcessedUuidAfterBatch() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    stubStatusPersistence(status);

    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Collections.singletonList(createRemoteInputSet("inputSet1", 1000L, "inputSet1-uuid")))
        .thenReturn(Collections.emptyList());

    runWithSuccessfulGitFetch();

    InputSetConnectorBackfillMigrationStatus progressSave =
        statusSaveSnapshots.stream()
            .filter(saved
                -> saved.getLastProcessedTimestamp() != null && !Boolean.TRUE.equals(saved.getMigrationCompleted()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected in-progress save with cursor"));
    assertThat(progressSave.getLastProcessedTimestamp()).isEqualTo(1000L);
    assertThat(progressSave.getLastProcessedUuid()).isEqualTo("inputSet1-uuid");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishConnectorReference_skipsNullConnectorRef() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity inputSetWithoutConnector = InputSetEntity.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .identifier("inputSet1")
                                                  .storeType(REMOTE)
                                                  .connectorRef(null)
                                                  .createdAt(1000L)
                                                  .deleted(false)
                                                  .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Arrays.asList(inputSetWithoutConnector))
        .thenReturn(Collections.emptyList());

    task.run();

    verify(inputSetSetupUsageHelper, never()).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishConnectorReference_skipsEmptyStringConnectorRef() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity inputSetWithEmptyConnector = InputSetEntity.builder()
                                                    .uuid("uuid-empty")
                                                    .accountId(ACCOUNT_ID)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .identifier("inputSet1")
                                                    .storeType(REMOTE)
                                                    .connectorRef("")
                                                    .createdAt(1000L)
                                                    .deleted(false)
                                                    .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    stubStatusPersistence(status);
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Collections.singletonList(inputSetWithEmptyConnector))
        .thenReturn(Collections.emptyList());

    task.run();

    verify(inputSetSetupUsageHelper, never()).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishConnectorReference_withFeatureFlagEnabled_usesScopeInfo() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity remoteInputSet = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier("inputSet1")
                                        .parentUniqueId(PARENT_UNIQUE_ID)
                                        .storeType(REMOTE)
                                        .connectorRef(CONNECTOR_REF)
                                        .repo(REPO_NAME)
                                        .filePath(FILE_PATH)
                                        .createdAt(1000L)
                                        .deleted(false)
                                        .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Arrays.asList(remoteInputSet))
        .thenReturn(Collections.emptyList());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PARENT_UNIQUE_ID)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(scopeInfo);
    doNothing().when(inputSetSetupUsageHelper).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());

    try (MockedStatic<GitAwareContextHelper> mockedStatic = Mockito.mockStatic(GitAwareContextHelper.class)) {
      ScmGitMetaData gitMetaData =
          ScmGitMetaData.builder()
              .branchName(BRANCH_NAME)
              .repoName(REPO_NAME)
              .cacheResponse(CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build())
              .build();

      mockedStatic.when(GitAwareContextHelper::getScmGitMetaData).thenReturn(gitMetaData);
      mockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(anyString())).thenReturn(false);

      task.run();

      verify(scopeResolutionHelper, times(1)).getScopeInfo(ACCOUNT_ID, PARENT_UNIQUE_ID);
      verify(inputSetSetupUsageHelper, times(1))
          .publishSetupUsageEvent(eq(remoteInputSet), eq(scopeInfo), eq(true), eq(BRANCH_NAME), eq(REPO_NAME));
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishConnectorReference_skipsOnGitFetchFailure() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity remoteInputSet = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier("inputSet1")
                                        .storeType(REMOTE)
                                        .connectorRef(CONNECTOR_REF)
                                        .repo(REPO_NAME)
                                        .filePath(FILE_PATH)
                                        .createdAt(1000L)
                                        .deleted(false)
                                        .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Arrays.asList(remoteInputSet))
        .thenReturn(Collections.emptyList());

    try (MockedStatic<GitAwareContextHelper> mockedStatic = Mockito.mockStatic(GitAwareContextHelper.class)) {
      mockedStatic.when(GitAwareContextHelper::getScmGitMetaData).thenThrow(new RuntimeException("Git fetch failed"));
      mockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(anyString())).thenReturn(false);

      task.run();

      ArgumentCaptor<InputSetConnectorBackfillMigrationStatus> statusCaptor =
          ArgumentCaptor.forClass(InputSetConnectorBackfillMigrationStatus.class);
      verify(mongoTemplate, times(3)).save(statusCaptor.capture());

      List<InputSetConnectorBackfillMigrationStatus> savedStatuses = statusCaptor.getAllValues();
      InputSetConnectorBackfillMigrationStatus finalStatus = savedStatuses.get(savedStatuses.size() - 1);
      assertThat(finalStatus.getTotalProcessed()).isEqualTo(1L);
      assertThat(finalStatus.getTotalGitCalls()).isEqualTo(1L); // Failed fetch counts as 1 git call
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishConnectorReference_publishesGitMetadata() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    InputSetEntity remoteInputSet = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier("inputSet1")
                                        .storeType(REMOTE)
                                        .connectorRef(CONNECTOR_REF)
                                        .repo(REPO_NAME)
                                        .filePath(FILE_PATH)
                                        .createdAt(1000L)
                                        .deleted(false)
                                        .build();

    InputSetConnectorBackfillMigrationStatus status = InputSetConnectorBackfillMigrationStatus.builder()
                                                          .id("InputSetConnectorBackfill")
                                                          .migrationCompleted(false)
                                                          .lastProcessedTimestamp(null)
                                                          .build();
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class)))
        .thenReturn(Arrays.asList(remoteInputSet))
        .thenReturn(Collections.emptyList());

    doNothing().when(inputSetSetupUsageHelper).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());

    try (MockedStatic<GitAwareContextHelper> mockedStatic = Mockito.mockStatic(GitAwareContextHelper.class)) {
      ScmGitMetaData gitMetaData =
          ScmGitMetaData.builder()
              .branchName(BRANCH_NAME)
              .repoName(REPO_NAME)
              .cacheResponse(CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build())
              .build();

      mockedStatic.when(GitAwareContextHelper::getScmGitMetaData).thenReturn(gitMetaData);
      mockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(anyString())).thenReturn(false);

      task.run();

      verify(inputSetSetupUsageHelper, times(1))
          .publishSetupUsageEvent(eq(remoteInputSet), any(), eq(true), eq(BRANCH_NAME), eq(REPO_NAME));

      ArgumentCaptor<InputSetConnectorBackfillMigrationStatus> statusCaptor =
          ArgumentCaptor.forClass(InputSetConnectorBackfillMigrationStatus.class);
      verify(mongoTemplate, times(3)).save(statusCaptor.capture());

      List<InputSetConnectorBackfillMigrationStatus> savedStatuses = statusCaptor.getAllValues();
      InputSetConnectorBackfillMigrationStatus finalStatus = savedStatuses.get(savedStatuses.size() - 1);
      assertThat(finalStatus.getTotalProcessed()).isEqualTo(1L);
      assertThat(finalStatus.getTotalGitCalls()).isEqualTo(0L); // Cache hit, no git call
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRunMigration_createsNewStatusIfNotExists() {
    AcquiredLock<?> lock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(lock);
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION))
        .thenReturn(false);

    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class))).thenReturn(null);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(mongoTemplate.find(any(Query.class), eq(InputSetEntity.class))).thenReturn(Collections.emptyList());

    task.run();

    ArgumentCaptor<InputSetConnectorBackfillMigrationStatus> statusCaptor =
        ArgumentCaptor.forClass(InputSetConnectorBackfillMigrationStatus.class);
    verify(mongoTemplate, times(2)).save(statusCaptor.capture());

    InputSetConnectorBackfillMigrationStatus savedStatus = statusCaptor.getAllValues().get(1);
    assertThat(savedStatus.getId()).isEqualTo("InputSetConnectorBackfill");
    assertThat(savedStatus.getMigrationCompleted()).isTrue();
  }

  private void stubStatusPersistence(InputSetConnectorBackfillMigrationStatus status) {
    when(mongoTemplate.findOne(any(Query.class), eq(InputSetConnectorBackfillMigrationStatus.class)))
        .thenReturn(status);
    when(mongoTemplate.save(any(InputSetConnectorBackfillMigrationStatus.class))).thenAnswer(invocation -> {
      statusSaveSnapshots.add(cloneStatus(invocation.getArgument(0)));
      return invocation.getArgument(0);
    });
  }

  private InputSetConnectorBackfillMigrationStatus cloneStatus(InputSetConnectorBackfillMigrationStatus status) {
    return InputSetConnectorBackfillMigrationStatus.builder()
        .id(status.getId())
        .migrationCompleted(status.getMigrationCompleted())
        .lastProcessedTimestamp(status.getLastProcessedTimestamp())
        .lastProcessedUuid(status.getLastProcessedUuid())
        .totalProcessed(status.getTotalProcessed())
        .totalGitCalls(status.getTotalGitCalls())
        .build();
  }

  private void runWithSuccessfulGitFetch() {
    doNothing().when(inputSetSetupUsageHelper).publishSetupUsageEvent(any(), any(), anyBoolean(), any(), any());

    try (MockedStatic<GitAwareContextHelper> mockedStatic = Mockito.mockStatic(GitAwareContextHelper.class)) {
      ScmGitMetaData gitMetaData =
          ScmGitMetaData.builder()
              .branchName(BRANCH_NAME)
              .repoName(REPO_NAME)
              .cacheResponse(CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build())
              .build();

      mockedStatic.when(GitAwareContextHelper::getScmGitMetaData).thenReturn(gitMetaData);
      mockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(anyString())).thenReturn(false);

      task.run();
    }
  }

  private InputSetEntity createRemoteInputSet(String identifier, Long createdAt, String uuid) {
    return InputSetEntity.builder()
        .uuid(uuid)
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .identifier(identifier)
        .storeType(REMOTE)
        .connectorRef(CONNECTOR_REF)
        .repo(REPO_NAME)
        .filePath(FILE_PATH)
        .createdAt(createdAt)
        .deleted(false)
        .build();
  }
}
