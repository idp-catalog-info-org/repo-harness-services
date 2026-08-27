/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.entity.accountoverrides.beans.AccountOverridesConstants.DEFAULT_RETENTION_PERIOD_IN_MONTHS;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.DataRetentionEntity.DataRetentionEntityKeys;
import io.harness.entity.accountoverrides.DataRetentionSettings;
import io.harness.entity.accountoverrides.SearchSettings;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.repositories.dataretention.DataRetentionRepository;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineRetentionServiceImplTest extends OrchestrationTestBase {
  @Mock DataRetentionRepository dataRetentionRepository;
  @InjectMocks PipelineRetentionServiceImpl pipelineRetentionService;

  String accountIdentifier = "abcde";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRetentionPeriodEmptyAccountId() {
    String accountId = "";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountId);
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountId);
    assertEquals(retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRetentionPeriodInvalidAccountId() {
    String accountId = "invalid";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountId);
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountId);
    assertEquals(retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRetentionPeriodValidPeriod() {
    doReturn(Optional.of(DataRetentionEntity.builder().retentionPeriodInMonths(12).build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier);
    assertEquals(retentionPeriodInMonths, 12);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRetentionPeriodInvalidPeriod() {
    doReturn(Optional.of(DataRetentionEntity.builder().retentionPeriodInMonths(36).build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier);
    assertEquals(retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRetentionPeriodLessThanDefault() {
    doReturn(Optional.of(DataRetentionEntity.builder().retentionPeriodInMonths(1).build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier);
    assertEquals(retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetMaxConcurrentExecutions() throws ExecutionException {
    doReturn(Optional.of(DataRetentionEntity.builder().retentionPeriodInMonths(1).maxConcurrentExecutions(5L).build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<Long> retentionPeriodInMonths =
        pipelineRetentionService.getMaxConcurrentPipelineExecution(accountIdentifier);
    assertEquals(5L, retentionPeriodInMonths.get().longValue());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyInvalidAccountId() {
    String accountId = "invalid";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountId);
    Optional<Long> maxConcurrentPipelineExecution =
        pipelineRetentionService.getMaxConcurrentPipelineExecution(accountId);
    assertThat(maxConcurrentPipelineExecution.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetMaxInputParameterSize() {
    doReturn(Optional.of(DataRetentionEntity.builder()
                             .retentionPeriodInMonths(1)
                             .maxConcurrentExecutions(5L)
                             .maxInputParameterSize(6L)
                             .build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<Long> maxInputParameterSize = pipelineRetentionService.getMaxInputParameterSize(accountIdentifier);
    assertTrue(maxInputParameterSize.isPresent());
    assertEquals(6L, maxInputParameterSize.get().longValue());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetDataRetentionSettings() {
    DataRetentionSettings dataRetentionSettingsExpected =
        DataRetentionSettings.builder().dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS).build();
    doReturn(Optional.of(DataRetentionEntity.builder()
                             .maxConcurrentExecutions(5L)
                             .maxInputParameterSize(6L)
                             .dataRetentionSettings(dataRetentionSettingsExpected)
                             .build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<DataRetentionSettings> dataRetentionSettings =
        pipelineRetentionService.getDataRetentionSettings(accountIdentifier);
    assertTrue(dataRetentionSettings.isPresent());
    assertEquals(dataRetentionSettingsExpected, dataRetentionSettings.get());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetSearchSettings() {
    SearchSettings searchSettingsExpected = SearchSettings.builder()
                                                .indexMigrationStatus(PipelineSearchMigrationStatus.IN_PROGRESS)
                                                .oldIndexName("old")
                                                .newIndexName("new")
                                                .build();
    doReturn(Optional.of(DataRetentionEntity.builder()
                             .maxConcurrentExecutions(5L)
                             .maxInputParameterSize(6L)
                             .searchSettings(searchSettingsExpected)
                             .build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<SearchSettings> dataRetentionSettings = pipelineRetentionService.getSearchSettings(accountIdentifier);
    assertTrue(dataRetentionSettings.isPresent());
    assertEquals(searchSettingsExpected, dataRetentionSettings.get());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateSearchIndexMigrationDetails() {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.searchIndexMigrationStatus, PipelineSearchMigrationStatus.IN_PROGRESS);
    updateOps.set(DataRetentionEntityKeys.searchIndexMigrationOldIndexName, "old");
    updateOps.set(DataRetentionEntityKeys.searchIndexMigrationNewIndexName, "new");
    pipelineRetentionService.updateSearchIndexMigrationDetails(
        accountIdentifier, PipelineSearchMigrationStatus.IN_PROGRESS, "old", "new");
    verify(dataRetentionRepository, times(1)).update(accountIdentifier, updateOps);

    Update updateOps2 = new Update();
    updateOps2.set(DataRetentionEntityKeys.searchIndexMigrationStatus, PipelineSearchMigrationStatus.COMPLETE);
    pipelineRetentionService.updateSearchIndexMigrationDetails(
        accountIdentifier, PipelineSearchMigrationStatus.COMPLETE, null, null);
    verify(dataRetentionRepository, times(1)).update(accountIdentifier, updateOps2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetRetentionConfigByAccountId_ValidAccount() {
    doReturn(Optional.of(DataRetentionEntity.builder()
                             .accountIdentifier(accountIdentifier)
                             .retentionPeriodInMonths(1)
                             .maxConcurrentExecutions(5L)
                             .build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<DataRetentionEntity> result = pipelineRetentionService.getRetentionConfigByAccountId(accountIdentifier);
    assertThat(result.isPresent()).isTrue();
    assertEquals(accountIdentifier, result.get().getAccountIdentifier());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetRetentionConfigByAccountId_InvalidAccount() {
    String accountIdentifier = "invalid";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountIdentifier);

    Optional<DataRetentionEntity> result = pipelineRetentionService.getRetentionConfigByAccountId(accountIdentifier);
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetMaxInputParameterSize_InvalidAccountId() {
    String accountIdentifier = "invalid";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountIdentifier);
    Optional<Long> maxInputParameterSize = pipelineRetentionService.getMaxInputParameterSize(accountIdentifier);
    assertThat(maxInputParameterSize.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetMaxLeafStepConcurrency_valuePresent_returnsValue() {
    String accountIdentifier = "acc-with-override";
    doReturn(Optional.of(
                 DataRetentionEntity.builder().accountIdentifier(accountIdentifier).maxLeafStepConcurrency(42).build()))
        .when(dataRetentionRepository)
        .findByAccountIdentifier(accountIdentifier);
    Optional<Integer> result = pipelineRetentionService.getMaxLeafStepConcurrency(accountIdentifier);
    assertThat(result.isPresent()).isTrue();
    assertEquals(Integer.valueOf(42), result.get());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetMaxLeafStepConcurrency_noRetentionEntity_returnsEmpty() {
    String accountIdentifier = "acc-no-entity";
    doReturn(Optional.empty()).when(dataRetentionRepository).findByAccountIdentifier(accountIdentifier);
    Optional<Integer> result = pipelineRetentionService.getMaxLeafStepConcurrency(accountIdentifier);
    assertThat(result.isEmpty()).isTrue();
  }
}
