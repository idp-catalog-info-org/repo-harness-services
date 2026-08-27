/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service.impl;

import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.repositories.search.PipelineSearchIndexMigrationEntityRepository;
import io.harness.rule.Owner;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity.PipelineSearchIndexMigrationEntityKeys;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineSearchIndexMigrationServiceImplTest extends CategoryTest {
  @Mock PipelineSearchIndexMigrationEntityRepository searchIndexMigrationEntityRepository;
  @InjectMocks PipelineSearchIndexMigrationServiceImpl indexMigrationService;

  private static final String accountIdentifier = "abcde";
  private static final String uuid = "uuid";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSave() {
    PipelineSearchIndexMigrationEntity indexMigrationEntity = PipelineSearchIndexMigrationEntity.builder()
                                                                  .accountIdentifier(accountIdentifier)
                                                                  .status(PipelineSearchMigrationStatus.NOT_STARTED)
                                                                  .build();
    when(searchIndexMigrationEntityRepository.save(eq(indexMigrationEntity))).thenReturn(indexMigrationEntity);
    PipelineSearchIndexMigrationEntity gotIndexMigrationEntity = indexMigrationService.save(indexMigrationEntity);
    assertEquals(gotIndexMigrationEntity, indexMigrationEntity);
    verify(searchIndexMigrationEntityRepository, times(1)).save(indexMigrationEntity);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdate() {
    String taskID = "task";
    PipelineSearchIndexMigrationEntity indexMigrationEntity = PipelineSearchIndexMigrationEntity.builder()
                                                                  .accountIdentifier(accountIdentifier)
                                                                  .status(PipelineSearchMigrationStatus.IN_PROGRESS)
                                                                  .elasticTaskID(taskID)
                                                                  .build();
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.status, PipelineSearchMigrationStatus.IN_PROGRESS);
    update.set(PipelineSearchIndexMigrationEntityKeys.elasticTaskID, taskID);
    when(searchIndexMigrationEntityRepository.update(eq(uuid), eq(update))).thenReturn(indexMigrationEntity);

    PipelineSearchIndexMigrationEntity gotIndexMigrationEntity = indexMigrationService.update(uuid, update);
    assertEquals(gotIndexMigrationEntity, indexMigrationEntity);
    verify(searchIndexMigrationEntityRepository, times(1)).update(uuid, update);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateRetentionPeriod() {
    PipelineSearchIndexMigration indexMigration = PipelineSearchIndexMigration.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .status(PipelineSearchMigrationStatus.NOT_STARTED)
                                                      .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
                                                      .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
                                                      .build();
    PipelineSearchIndexMigrationEntity indexMigrationEntity = PipelineSearchIndexMigrationEntity.builder()
                                                                  .accountIdentifier(accountIdentifier)
                                                                  .status(PipelineSearchMigrationStatus.NOT_STARTED)
                                                                  .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
                                                                  .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
                                                                  .build();
    ArgumentCaptor<PipelineSearchIndexMigrationEntity> migrationEntityArgumentCaptor =
        ArgumentCaptor.forClass(PipelineSearchIndexMigrationEntity.class);
    when(searchIndexMigrationEntityRepository.save(migrationEntityArgumentCaptor.capture()))
        .thenReturn(indexMigrationEntity);
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier))).thenReturn(null);
    PipelineSearchIndexMigration gotIndexMigration = indexMigrationService.updateRetentionPeriod(
        accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS);
    assertEquals(gotIndexMigration, indexMigration);
    PipelineSearchIndexMigrationEntity gotMigrationEntity = migrationEntityArgumentCaptor.getValue();
    assertEquals(gotMigrationEntity.getAccountIdentifier(), indexMigrationEntity.getAccountIdentifier());
    assertEquals(gotMigrationEntity.getStatus(), indexMigrationEntity.getStatus());
    assertEquals(gotMigrationEntity.getOldIndexRetentionPeriod(), indexMigrationEntity.getOldIndexRetentionPeriod());
    assertEquals(gotMigrationEntity.getNewIndexRetentionPeriod(), indexMigrationEntity.getNewIndexRetentionPeriod());
    assertTrue(gotMigrationEntity.getNextIteration() > 0L);
    verify(searchIndexMigrationEntityRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateRetentionPeriodGotOldNotStarted() {
    PipelineSearchIndexMigrationEntity oldIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.NOT_STARTED)
            .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .nextIteration(0L)
            .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);

    assertThatThrownBy(()
                           -> indexMigrationService.updateRetentionPeriod(
                               accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS))
        .hasMessage("Index migration is already under progress for this account: abcde, with status: NOT_STARTED");
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());

    oldIndexMigrationEntity = PipelineSearchIndexMigrationEntity.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .status(PipelineSearchMigrationStatus.IN_PROGRESS)
                                  .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
                                  .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
                                  .nextIteration(0L)
                                  .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);

    assertThatThrownBy(()
                           -> indexMigrationService.updateRetentionPeriod(
                               accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS))
        .hasMessage("Index migration is already under progress for this account: abcde, with status: IN_PROGRESS");
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateRetentionPeriodGotOldCompleted() {
    PipelineSearchIndexMigrationEntity oldIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .uuid(uuid)
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.COMPLETE)
            .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .elasticTaskID("task1")
            .elasticBufferSyncTaskID("task2")
            .migrationEndTime(2L)
            .migrationStartTime(1L)
            .nextIteration(100L)
            .build();
    PipelineSearchIndexMigrationEntity newIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.NOT_STARTED)
            .oldIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_24_MONTHS)
            .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);

    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    when(searchIndexMigrationEntityRepository.update(eq(uuid), updateArgumentCaptor.capture()))
        .thenReturn(newIndexMigrationEntity);

    PipelineSearchIndexMigration gotIndexMigration = indexMigrationService.updateRetentionPeriod(
        accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_24_MONTHS);

    Update capturedUpdate = updateArgumentCaptor.getValue();
    Document setDocument = (Document) capturedUpdate.getUpdateObject().get("$set");
    Document unsetDocument = (Document) capturedUpdate.getUpdateObject().get("$unset");
    assertEquals(
        PipelineSearchMigrationStatus.NOT_STARTED, setDocument.get(PipelineSearchIndexMigrationEntityKeys.status));
    assertEquals(
        ACCOUNT_RETENTION_12_MONTHS, setDocument.get(PipelineSearchIndexMigrationEntityKeys.oldIndexRetentionPeriod));
    assertEquals(
        ACCOUNT_RETENTION_24_MONTHS, setDocument.get(PipelineSearchIndexMigrationEntityKeys.newIndexRetentionPeriod));
    assertNotNull(setDocument.get(PipelineSearchIndexMigrationEntityKeys.nextIteration));
    assertEquals(setDocument.size(), 4);

    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.elasticTaskID));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.elasticBufferSyncTaskID));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.migrationStartTime));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.migrationEndTime));
    assertEquals(unsetDocument.size(), 4);

    assertEquals(gotIndexMigration,
        PipelineSearchIndexMigration.builder()
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.NOT_STARTED)
            .oldIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_24_MONTHS)
            .build());
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());
    verify(searchIndexMigrationEntityRepository, times(1)).update(eq(uuid), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateRetentionPeriodGotOldFailed() {
    PipelineSearchIndexMigrationEntity oldIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .uuid(uuid)
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.FAILED)
            .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .elasticTaskID("task1")
            .elasticBufferSyncTaskID("task2")
            .migrationEndTime(2L)
            .migrationStartTime(1L)
            .nextIteration(100L)
            .build();
    PipelineSearchIndexMigrationEntity newIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.NOT_STARTED)
            .oldIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_24_MONTHS)
            .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);

    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    when(searchIndexMigrationEntityRepository.update(eq(uuid), updateArgumentCaptor.capture()))
        .thenReturn(newIndexMigrationEntity);

    PipelineSearchIndexMigration gotIndexMigration = indexMigrationService.updateRetentionPeriod(
        accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_24_MONTHS);

    Update capturedUpdate = updateArgumentCaptor.getValue();
    Document setDocument = (Document) capturedUpdate.getUpdateObject().get("$set");
    Document unsetDocument = (Document) capturedUpdate.getUpdateObject().get("$unset");
    assertEquals(
        PipelineSearchMigrationStatus.NOT_STARTED, setDocument.get(PipelineSearchIndexMigrationEntityKeys.status));
    assertEquals(
        DEFAULT_RETENTION_6_MONTHS, setDocument.get(PipelineSearchIndexMigrationEntityKeys.oldIndexRetentionPeriod));
    assertEquals(
        ACCOUNT_RETENTION_24_MONTHS, setDocument.get(PipelineSearchIndexMigrationEntityKeys.newIndexRetentionPeriod));
    assertNotNull(setDocument.get(PipelineSearchIndexMigrationEntityKeys.nextIteration));
    assertEquals(setDocument.size(), 4);

    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.elasticTaskID));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.elasticBufferSyncTaskID));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.migrationStartTime));
    assertTrue(unsetDocument.containsKey(PipelineSearchIndexMigrationEntityKeys.migrationEndTime));
    assertEquals(unsetDocument.size(), 4);

    assertEquals(gotIndexMigration,
        PipelineSearchIndexMigration.builder()
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.NOT_STARTED)
            .oldIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_24_MONTHS)
            .build());
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());
    verify(searchIndexMigrationEntityRepository, times(1)).update(eq(uuid), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateRetentionPeriodGotOldSameRetentionPeriod() {
    PipelineSearchIndexMigrationEntity oldIndexMigrationEntity =
        PipelineSearchIndexMigrationEntity.builder()
            .uuid(uuid)
            .accountIdentifier(accountIdentifier)
            .status(PipelineSearchMigrationStatus.COMPLETE)
            .oldIndexRetentionPeriod(DEFAULT_RETENTION_6_MONTHS)
            .newIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
            .elasticTaskID("task1")
            .elasticBufferSyncTaskID("task2")
            .migrationEndTime(2L)
            .migrationStartTime(1L)
            .nextIteration(100L)
            .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);
    assertThatThrownBy(()
                           -> indexMigrationService.updateRetentionPeriod(
                               accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS))
        .hasMessage(
            "Currently the account: abcde, is already on the requested retention period: ACCOUNT_RETENTION_12_MONTHS");
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());
    verify(searchIndexMigrationEntityRepository, times(0)).update(any(), any());

    oldIndexMigrationEntity = PipelineSearchIndexMigrationEntity.builder()
                                  .uuid(uuid)
                                  .accountIdentifier(accountIdentifier)
                                  .status(PipelineSearchMigrationStatus.FAILED)
                                  .oldIndexRetentionPeriod(ACCOUNT_RETENTION_12_MONTHS)
                                  .newIndexRetentionPeriod(ACCOUNT_RETENTION_24_MONTHS)
                                  .elasticTaskID("task1")
                                  .elasticBufferSyncTaskID("task2")
                                  .migrationEndTime(2L)
                                  .migrationStartTime(1L)
                                  .nextIteration(100L)
                                  .build();
    when(searchIndexMigrationEntityRepository.findByAccountIdentifier(eq(accountIdentifier)))
        .thenReturn(oldIndexMigrationEntity);
    assertThatThrownBy(()
                           -> indexMigrationService.updateRetentionPeriod(
                               accountIdentifier, DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS))
        .hasMessage(
            "Currently the account: abcde, is already on the requested retention period: ACCOUNT_RETENTION_12_MONTHS");
    verify(searchIndexMigrationEntityRepository, times(0)).save(any());
    verify(searchIndexMigrationEntityRepository, times(0)).update(any(), any());
  }
}
