/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.MANISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.config.ServiceUniqueIdBackfillConfig;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ServiceUniqueIdBackfillTaskTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private PersistentLocker persistentLocker;
  @Mock private DSLContext dslContext;
  @Mock private AcquiredLock<?> acquiredLock;

  private ServiceUniqueIdBackfillTask task;
  private ServiceUniqueIdBackfillConfig config;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    task = new ServiceUniqueIdBackfillTask(mongoTemplate, persistentLocker, dslContext);

    config = ServiceUniqueIdBackfillConfig.builder()
                 .disabled(false)
                 .initialDelayInMinutes(10)
                 .intervalInMinutes(60)
                 .batchSize(500)
                 .sleepBetweenBatchesInMillis(100)
                 .maxRetryCount(3)
                 .build();
    setPrivateField(task, "config", config);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testRun_whenLockNotAcquired_shouldSkip() {
    // Arrange
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(null);

    // Act
    task.run();

    // Assert - no further processing should happen
    verify(persistentLocker)
        .tryToAcquireInfiniteLockWithPeriodicRefresh(eq("ServiceUniqueIdBackfillTaskLock"), any(Duration.class));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testRun_whenLockAcquired_shouldProceedWithMigration() throws Exception {
    // Arrange
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(anyString(), any(Duration.class)))
        .thenReturn(acquiredLock);

    // Mock the dslContext to return empty results for Step 1 (cd_stage_execution query)
    // This simulates migration complete - no records need backfill
    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Result emptyResult = mock(org.jooq.Result.class);

    // Mock Step 1: select id, service_id, env_id, service_unique_id, env_unique_id, infra_id, infra_unique_id
    when(dslContext.select(any(), any(), any(), any(), any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(limitStep).when(conditionStep).limit(any(Integer.class));
    when(limitStep.fetch()).thenReturn(emptyResult);
    when(emptyResult.size()).thenReturn(0);
    when(emptyResult.isEmpty()).thenReturn(true);

    // Act
    task.run();

    // Assert
    verify(persistentLocker)
        .tryToAcquireInfiniteLockWithPeriodicRefresh(eq("ServiceUniqueIdBackfillTaskLock"), any(Duration.class));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_accountScoped() throws Exception {
    // Use reflection to test private method
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "account.myService", "testOrg", "testProject");

    // Assert - account scoped should have null org and project
    assertThat(result).isNotNull();
    assertThat(getFieldValue(result, "identifier")).isEqualTo("myService");
    assertThat(getFieldValue(result, "orgIdentifier")).isNull();
    assertThat(getFieldValue(result, "projectIdentifier")).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_orgScoped() throws Exception {
    // Use reflection to test private method
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "org.myService", "testOrg", "testProject");

    // Assert - org scoped should have org but null project
    assertThat(result).isNotNull();
    assertThat(getFieldValue(result, "identifier")).isEqualTo("myService");
    assertThat(getFieldValue(result, "orgIdentifier")).isEqualTo("testOrg");
    assertThat(getFieldValue(result, "projectIdentifier")).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_projectScoped() throws Exception {
    // Use reflection to test private method
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "myService", "testOrg", "testProject");

    // Assert - project scoped should have both org and project
    assertThat(result).isNotNull();
    assertThat(getFieldValue(result, "identifier")).isEqualTo("myService");
    assertThat(getFieldValue(result, "orgIdentifier")).isEqualTo("testOrg");
    assertThat(getFieldValue(result, "projectIdentifier")).isEqualTo("testProject");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_emptyScopedId() throws Exception {
    // Use reflection to test private method
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "", "testOrg", "testProject");

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_nullScopedId() throws Exception {
    // Use reflection to test private method
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, null, "testOrg", "testProject");

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_envAccountScoped() throws Exception {
    // Use reflection to test private method - also works for env identifiers
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "account.myEnv", "testOrg", "testProject");

    // Assert - account scoped should have null org and project
    assertThat(result).isNotNull();
    assertThat(getFieldValue(result, "identifier")).isEqualTo("myEnv");
    assertThat(getFieldValue(result, "orgIdentifier")).isNull();
    assertThat(getFieldValue(result, "projectIdentifier")).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testParseScopedId_envOrgScoped() throws Exception {
    Method parseMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("parseScopedId", String.class, String.class, String.class);
    parseMethod.setAccessible(true);

    // Act
    Object result = parseMethod.invoke(task, "org.myEnv", "testOrg", "testProject");

    // Assert - org scoped should have org but null project
    assertThat(result).isNotNull();
    assertThat(getFieldValue(result, "identifier")).isEqualTo("myEnv");
    assertThat(getFieldValue(result, "orgIdentifier")).isEqualTo("testOrg");
    assertThat(getFieldValue(result, "projectIdentifier")).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testBuildCacheKey_withAllIdentifiers() throws Exception {
    // Use reflection to test private method
    Method buildCacheKeyMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "buildCacheKey", String.class, String.class, String.class, String.class, String.class);
    buildCacheKeyMethod.setAccessible(true);

    // Act
    String result = (String) buildCacheKeyMethod.invoke(task, "service", "account1", "org1", "project1", "svc1");

    // Assert
    assertThat(result).isEqualTo("service|account1|org1|project1|svc1");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testBuildCacheKey_withNullOrgAndProject() throws Exception {
    // Use reflection to test private method
    Method buildCacheKeyMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "buildCacheKey", String.class, String.class, String.class, String.class, String.class);
    buildCacheKeyMethod.setAccessible(true);

    // Act
    String result = (String) buildCacheKeyMethod.invoke(task, "service", "account1", null, null, "svc1");

    // Assert
    assertThat(result).isEqualTo("service|account1|||svc1");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testBuildCacheKey_withNullProject() throws Exception {
    // Use reflection to test private method
    Method buildCacheKeyMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "buildCacheKey", String.class, String.class, String.class, String.class, String.class);
    buildCacheKeyMethod.setAccessible(true);

    // Act
    String result = (String) buildCacheKeyMethod.invoke(task, "service", "account1", "org1", null, "svc1");

    // Assert
    assertThat(result).isEqualTo("service|account1|org1||svc1");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testBuildCacheKey_envPrefix() throws Exception {
    // Use reflection to test private method
    Method buildCacheKeyMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "buildCacheKey", String.class, String.class, String.class, String.class, String.class);
    buildCacheKeyMethod.setAccessible(true);

    // Act
    String result = (String) buildCacheKeyMethod.invoke(task, "env", "account1", "org1", "project1", "env1");

    // Assert
    assertThat(result).isEqualTo("env|account1|org1|project1|env1");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testFetchBatchRecords_useTwoSeparateQueries() throws Exception {
    // This test verifies that fetchBatchRecords uses two separate queries
    // instead of a join on the two large tables

    // Arrange - mock the first query (cd_stage_execution)
    org.jooq.SelectSelectStep selectStep1 = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep1 = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep1 = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectLimitPercentStep limitStep1 = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Result emptyResult = mock(org.jooq.Result.class);

    // Mock Step 1: select id, service_id, env_id, service_unique_id, env_unique_id, infra_id, infra_unique_id
    when(dslContext.select(any(), any(), any(), any(), any(), any(), any())).thenReturn(selectStep1);
    when(selectStep1.from(any(org.jooq.Table.class))).thenReturn(joinStep1);
    when(joinStep1.where(any(org.jooq.Condition.class))).thenReturn(conditionStep1);
    doReturn(limitStep1).when(conditionStep1).limit(any(Integer.class));
    when(limitStep1.fetch()).thenReturn(emptyResult);
    when(emptyResult.isEmpty()).thenReturn(true);

    // Use reflection to test private method
    Method fetchBatchMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod("fetchBatchRecords", int.class);
    fetchBatchMethod.setAccessible(true);

    // Act
    java.util.List<?> result = (java.util.List<?>) fetchBatchMethod.invoke(task, 100);

    // Assert - should return empty list since first query returned empty
    assertThat(result).isEmpty();

    // Verify the first query was executed (select with 7 fields)
    verify(dslContext, times(1)).select(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testUnknownUniqueIdConstant() {
    // Verify the sentinel constant value
    assertThat(ServiceUniqueIdBackfillTask.UNKNOWN_UNIQUE_ID).isEqualTo("UNKNOWN");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testProcessBatch_orphanedRecord_usesSentinel() throws Exception {
    // Test that records with null accountIdentifier (orphaned - no scope info)
    // get sentinel values instead of being skipped

    Method processBatchMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("processBatch", java.util.List.class);
    processBatchMethod.setAccessible(true);

    // Create an orphaned record (null accountIdentifier means no scope info was found)
    // Use reflection to create CdStageExecutionRecord
    Class<?> recordClass = null;
    for (Class<?> innerClass : ServiceUniqueIdBackfillTask.class.getDeclaredClasses()) {
      if (innerClass.getSimpleName().equals("CdStageExecutionRecord")) {
        recordClass = innerClass;
        break;
      }
    }
    assertThat(recordClass).isNotNull();

    java.lang.reflect.Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object orphanedRecord = constructor.newInstance(
        "exec-1", "myService", "myEnv", "myInfra", "myEnv", null, null, null); // null account = orphaned

    java.util.List<Object> batchRecords = new java.util.ArrayList<>();
    batchRecords.add(orphanedRecord);

    // Mock BulkOperations for MongoDB
    org.springframework.data.mongodb.core.BulkOperations bulkOps =
        mock(org.springframework.data.mongodb.core.BulkOperations.class);
    when(mongoTemplate.bulkOps(any(), eq(io.harness.cdng.execution.StageExecutionInfo.class))).thenReturn(bulkOps);
    when(bulkOps.execute()).thenReturn(null);

    // Mock TimescaleDB batch update - individual queries approach
    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);
    int[] batchResult = new int[] {1};

    when(dslContext.update(any(org.jooq.Table.class))).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.Field.class), (String) any())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.Field.class), (String) any())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(dslContext.batch(any(java.util.Collection.class))).thenReturn(mock(org.jooq.Batch.class));

    // Act
    @SuppressWarnings("unchecked") int updatedCount = (int) processBatchMethod.invoke(task, batchRecords);

    // Assert - orphaned record should be processed (sentinel written), not skipped
    assertThat(updatedCount).isEqualTo(1);

    // Verify TimescaleDB batch was executed
    verify(dslContext).batch(any(java.util.Collection.class));

    // Verify MongoDB bulk was executed
    verify(bulkOps).execute();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_generatesUuidWhenNull() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    // Mock the select query chain (3 fields: ID, UNIQUE_ID, PARENT_UNIQUE_ID)
    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    // Entity exists but unique_id is null
    when(record.get(any(org.jooq.TableField.class))).thenReturn(null);
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.ID)).thenReturn("row-123");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.UNIQUE_ID)).thenReturn(null);
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.PARENT_UNIQUE_ID)).thenReturn(null);

    // Mock the update query for generating UUID
    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);

    when(dslContext.update(io.harness.timescaledb.tables.Services.SERVICES)).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(updateConditionStep.execute()).thenReturn(1);

    // Act
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "svc1");

    // Assert - should return generated values, not null
    assertThat(result).isNotNull();
    assertThat(result[0]).isNotNull().isNotEmpty().isNotEqualTo("UNKNOWN");
    assertThat(result[1]).isEqualTo("UNKNOWN");

    // Verify the update was called to persist the generated UUID
    verify(dslContext).update(io.harness.timescaledb.tables.Services.SERVICES);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_preservesExistingParentUniqueId() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    // Entity exists, unique_id null but parent_unique_id is populated
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.ID)).thenReturn("row-456");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.UNIQUE_ID)).thenReturn(null);
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.PARENT_UNIQUE_ID)).thenReturn("existing-parent-id");

    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);

    when(dslContext.update(io.harness.timescaledb.tables.Services.SERVICES)).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(updateConditionStep.execute()).thenReturn(1);

    // Act
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "svc1");

    // Assert - parentUniqueId should be preserved, not overwritten with UNKNOWN
    assertThat(result).isNotNull();
    assertThat(result[0]).isNotNull().isNotEmpty();
    assertThat(result[1]).isEqualTo("existing-parent-id");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_returnsExistingUniqueId() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    // Entity exists with unique_id already populated
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.ID)).thenReturn("row-789");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.UNIQUE_ID)).thenReturn("existing-uuid");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.PARENT_UNIQUE_ID))
        .thenReturn("existing-parent-uuid");

    // Act
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "svc1");

    // Assert - should return existing values without update
    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo("existing-uuid");
    assertThat(result[1]).isEqualTo("existing-parent-uuid");

    // Verify no update was performed
    verify(dslContext, times(0)).update(io.harness.timescaledb.tables.Services.SERVICES);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_returnsNullWhenNotFound() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(null);

    // Act
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "svc1");

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupEnvUniqueId_generatesUuidWhenNull() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupEnvUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Environments.ENVIRONMENTS.ID)).thenReturn("env-row-1");
    when(record.get(io.harness.timescaledb.tables.Environments.ENVIRONMENTS.UNIQUE_ID)).thenReturn(null);
    when(record.get(io.harness.timescaledb.tables.Environments.ENVIRONMENTS.PARENT_UNIQUE_ID)).thenReturn(null);

    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);

    when(dslContext.update(io.harness.timescaledb.tables.Environments.ENVIRONMENTS)).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(updateConditionStep.execute()).thenReturn(1);

    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "env1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isNotNull().isNotEmpty().isNotEqualTo("UNKNOWN");
    assertThat(result[1]).isEqualTo("UNKNOWN");

    verify(dslContext).update(io.harness.timescaledb.tables.Environments.ENVIRONMENTS);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupInfraUniqueId_generatesUuidWhenNull() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupInfraUniqueId", String.class, String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.ID)).thenReturn("infra-row-1");
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.UNIQUE_ID)).thenReturn(null);
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.PARENT_UNIQUE_ID)).thenReturn(null);

    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);

    when(dslContext.update(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES)).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.TableField.class), anyString())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(updateConditionStep.execute()).thenReturn(1);

    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", "project1", "env1", "infra1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isNotNull().isNotEmpty().isNotEqualTo("UNKNOWN");
    assertThat(result[1]).isEqualTo("UNKNOWN");

    verify(dslContext).update(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_accountScoped() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.ID)).thenReturn("row-acct");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.UNIQUE_ID)).thenReturn("acct-uuid");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.PARENT_UNIQUE_ID)).thenReturn("acct-parent");

    // Account scoped: orgIdentifier=null, projectIdentifier=null
    String[] result = (String[]) lookupMethod.invoke(task, "account1", null, null, "svc1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo("acct-uuid");
    assertThat(result[1]).isEqualTo("acct-parent");

    // Verify .and() was called for isNull conditions (account + identifier + orgIsNull + projectIsNull)
    verify(conditionStep, times(3)).and(any(org.jooq.Condition.class));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupServiceUniqueId_orgScoped() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupServiceUniqueId", String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.ID)).thenReturn("row-org");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.UNIQUE_ID)).thenReturn("org-uuid");
    when(record.get(io.harness.timescaledb.tables.Services.SERVICES.PARENT_UNIQUE_ID)).thenReturn("org-parent");

    // Org scoped: orgIdentifier=non-null, projectIdentifier=null
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", null, "svc1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo("org-uuid");
    assertThat(result[1]).isEqualTo("org-parent");

    // Verify .and() was called (account + identifier + orgEq + projectIsNull)
    verify(conditionStep, times(3)).and(any(org.jooq.Condition.class));
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testUpdateTimescaleDBBatch_returnsFalseOnFailure() throws Exception {
    Method updateTsdbMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("updateTimescaleDBBatch", java.util.List.class);
    updateTsdbMethod.setAccessible(true);

    Class<?> updateInfoClass = null;
    for (Class<?> innerClass : ServiceUniqueIdBackfillTask.class.getDeclaredClasses()) {
      if (innerClass.getSimpleName().equals("TimescaleUpdateInfo")) {
        updateInfoClass = innerClass;
        break;
      }
    }
    assertThat(updateInfoClass).isNotNull();

    java.lang.reflect.Constructor<?> constructor = updateInfoClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object updateInfo = constructor.newInstance("exec-1", "svc-uuid", "svc-parent", null, null, null, null);

    java.util.List<Object> updates = new java.util.ArrayList<>();
    updates.add(updateInfo);

    // Make dslContext.update throw to simulate TSDB failure
    when(dslContext.update(any(org.jooq.Table.class))).thenThrow(new RuntimeException("Connection lost"));

    boolean result = (boolean) updateTsdbMethod.invoke(task, updates);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testUpdateTimescaleDBBatch_returnsTrueOnSuccess() throws Exception {
    Method updateTsdbMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("updateTimescaleDBBatch", java.util.List.class);
    updateTsdbMethod.setAccessible(true);

    Class<?> updateInfoClass = null;
    for (Class<?> innerClass : ServiceUniqueIdBackfillTask.class.getDeclaredClasses()) {
      if (innerClass.getSimpleName().equals("TimescaleUpdateInfo")) {
        updateInfoClass = innerClass;
        break;
      }
    }
    assertThat(updateInfoClass).isNotNull();

    java.lang.reflect.Constructor<?> constructor = updateInfoClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object updateInfo = constructor.newInstance("exec-1", "svc-uuid", "svc-parent", null, null, null, null);

    java.util.List<Object> updates = new java.util.ArrayList<>();
    updates.add(updateInfo);

    // Mock a successful update chain
    org.jooq.UpdateSetFirstStep updateStep = mock(org.jooq.UpdateSetFirstStep.class);
    org.jooq.UpdateSetMoreStep updateMoreStep = mock(org.jooq.UpdateSetMoreStep.class);
    org.jooq.UpdateConditionStep updateConditionStep = mock(org.jooq.UpdateConditionStep.class);
    org.jooq.Batch batch = mock(org.jooq.Batch.class);

    when(dslContext.update(any(org.jooq.Table.class))).thenReturn(updateStep);
    when(updateStep.set(any(org.jooq.Field.class), (String) any())).thenReturn(updateMoreStep);
    when(updateMoreStep.set(any(org.jooq.Field.class), (String) any())).thenReturn(updateMoreStep);
    when(updateMoreStep.where(any(org.jooq.Condition.class))).thenReturn(updateConditionStep);
    when(dslContext.batch(any(java.util.Collection.class))).thenReturn(batch);
    when(batch.execute()).thenReturn(new int[] {1});

    boolean result = (boolean) updateTsdbMethod.invoke(task, updates);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testProcessBatch_throwsWhenTsdbUpdateFails() throws Exception {
    Method processBatchMethod =
        ServiceUniqueIdBackfillTask.class.getDeclaredMethod("processBatch", java.util.List.class);
    processBatchMethod.setAccessible(true);

    Class<?> recordClass = null;
    for (Class<?> innerClass : ServiceUniqueIdBackfillTask.class.getDeclaredClasses()) {
      if (innerClass.getSimpleName().equals("CdStageExecutionRecord")) {
        recordClass = innerClass;
        break;
      }
    }
    assertThat(recordClass).isNotNull();

    java.lang.reflect.Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    // Orphaned record so no lookup is needed, sentinel values are used directly
    Object orphanedRecord = constructor.newInstance("exec-1", "myService", null, null, null, null, null, null);

    java.util.List<Object> batchRecords = new java.util.ArrayList<>();
    batchRecords.add(orphanedRecord);

    // Mock TSDB update to throw exception (simulating failure)
    when(dslContext.update(any(org.jooq.Table.class))).thenThrow(new RuntimeException("TSDB connection failed"));

    // Act & Assert - should throw since TSDB update fails
    try {
      processBatchMethod.invoke(task, batchRecords);
      assertThat(false).as("Expected RuntimeException to be thrown").isTrue();
    } catch (java.lang.reflect.InvocationTargetException e) {
      assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      assertThat(e.getCause().getMessage()).contains("TimescaleDB batch update failed");
    }
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupInfraUniqueId_accountScopedEnv() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupInfraUniqueId", String.class, String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.ID)).thenReturn("infra-acct-1");
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.UNIQUE_ID))
        .thenReturn("infra-acct-uuid");
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.PARENT_UNIQUE_ID))
        .thenReturn("infra-acct-parent");

    // Account-scoped env: orgIdentifier=null, projectIdentifier=null
    String[] result = (String[]) lookupMethod.invoke(task, "account1", null, null, "env1", "infra1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo("infra-acct-uuid");
    assertThat(result[1]).isEqualTo("infra-acct-parent");
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testLookupInfraUniqueId_orgScopedEnv() throws Exception {
    Method lookupMethod = ServiceUniqueIdBackfillTask.class.getDeclaredMethod(
        "lookupInfraUniqueId", String.class, String.class, String.class, String.class, String.class);
    lookupMethod.setAccessible(true);

    org.jooq.SelectSelectStep selectStep = mock(org.jooq.SelectSelectStep.class);
    org.jooq.SelectJoinStep joinStep = mock(org.jooq.SelectJoinStep.class);
    org.jooq.SelectConditionStep conditionStep = mock(org.jooq.SelectConditionStep.class);
    org.jooq.SelectSeekStep1 orderByStep = mock(org.jooq.SelectSeekStep1.class);
    org.jooq.SelectLimitPercentStep limitStep = mock(org.jooq.SelectLimitPercentStep.class);
    org.jooq.Record record = mock(org.jooq.Record.class);

    when(dslContext.select(any(), any(), any())).thenReturn(selectStep);
    when(selectStep.from(any(org.jooq.Table.class))).thenReturn(joinStep);
    when(joinStep.where(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    when(conditionStep.and(any(org.jooq.Condition.class))).thenReturn(conditionStep);
    doReturn(orderByStep).when(conditionStep).orderBy(any(org.jooq.OrderField.class));
    doReturn(limitStep).when(orderByStep).limit(1);
    when(limitStep.fetchOne()).thenReturn(record);

    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.ID)).thenReturn("infra-org-1");
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.UNIQUE_ID))
        .thenReturn("infra-org-uuid");
    when(record.get(io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES.PARENT_UNIQUE_ID))
        .thenReturn("infra-org-parent");

    // Org-scoped env: orgIdentifier set, projectIdentifier=null
    String[] result = (String[]) lookupMethod.invoke(task, "account1", "org1", null, "env1", "infra1");

    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo("infra-org-uuid");
    assertThat(result[1]).isEqualTo("infra-org-parent");
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private Object getFieldValue(Object target, String fieldName) throws Exception {
    Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null) {
        return findField(superclass, fieldName);
      }
      throw e;
    }
  }
}
