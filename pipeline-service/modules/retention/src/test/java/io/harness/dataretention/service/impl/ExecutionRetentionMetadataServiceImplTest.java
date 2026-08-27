/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.service.impl;

import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.ExecutionRetentionMetadata.ExecutionRetentionMetadataKeys;
import io.harness.dataretention.entity.beans.ExecutionRetentionMetadataUpdateDTO;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.repositories.dataretention.ExecutionRetentionMetadataRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionRetentionMetadataServiceImplTest extends CategoryTest {
  @Mock private ExecutionRetentionMetadataRepository retentionMetadataRepository;
  @InjectMocks ExecutionRetentionMetadataServiceImpl retentionMetadataService;

  private static final String accountIdentifier = "abcde";
  private static final String bucketName = "bucketName";
  private static final String planExecutionId = "planExecutionId";
  private static final String uuid = "uuid";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(retentionMetadataService).set("retentionMetadataRepository", retentionMetadataRepository);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpsert() {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionMetadataKeys.accountId, accountIdentifier);
    updateOps.set(ExecutionRetentionMetadataKeys.bucketName, bucketName);
    updateOps.set(ExecutionRetentionMetadataKeys.planExecutionId, planExecutionId);

    ExecutionRetentionMetadataUpdateDTO updateDTO = ExecutionRetentionMetadataUpdateDTO.builder()
                                                        .accountId(accountIdentifier)
                                                        .bucketName(bucketName)
                                                        .planExecutionId(planExecutionId)
                                                        .build();

    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    retentionMetadataService.upsert(accountIdentifier, updateDTO);

    verify(retentionMetadataRepository, times(1)).upsert(eq(accountIdentifier), updateArgumentCaptor.capture());
    assertThat(updateOps).isEqualTo(updateArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpsertAllFields() {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionMetadataKeys.accountId, accountIdentifier);
    updateOps.set(ExecutionRetentionMetadataKeys.bucketName, bucketName);
    updateOps.set(ExecutionRetentionMetadataKeys.planExecutionId, planExecutionId);
    updateOps.set(ExecutionRetentionMetadataKeys.endTs, 100L);
    updateOps.set(ExecutionRetentionMetadataKeys.retentionFileData,
        Arrays.asList(RetentionFileData.builder().fileName("test").build()));

    ExecutionRetentionMetadataUpdateDTO updateDTO =
        ExecutionRetentionMetadataUpdateDTO.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(RetentionFileData.builder().fileName("test").build()))
            .build();

    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    retentionMetadataService.upsert(accountIdentifier, updateDTO);

    verify(retentionMetadataRepository, times(1)).upsert(eq(accountIdentifier), updateArgumentCaptor.capture());
    assertThat(updateOps).isEqualTo(updateArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFilterRetentionFileData() {
    ExecutionRetentionMetadata retentionMetadata =
        ExecutionRetentionMetadata.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(RetentionFileData.builder().fileName("test").build()))
            .build();

    assertThatThrownBy(
        () -> retentionMetadataService.filterRetentionFileData(retentionMetadata, uuid, EXECUTION_METADATA))
        .hasMessage("[DATA_RETENTION]: The requested retention metadata for uuid: uuid, doesn't exist for collection: "
            + "planExecutionsMetadata");

    ExecutionRetentionMetadata retentionMetadata1 = ExecutionRetentionMetadata.builder()
                                                        .accountId(accountIdentifier)
                                                        .bucketName(bucketName)
                                                        .planExecutionId(planExecutionId)
                                                        .endTs(100L)
                                                        .build();

    assertThatThrownBy(
        () -> retentionMetadataService.filterRetentionFileData(retentionMetadata1, uuid, EXECUTION_METADATA))
        .hasMessage("[DATA_RETENTION]: The requested retention metadata for uuid: uuid, doesn't contain any file");

    ExecutionRetentionMetadata retentionMetadata2 = ExecutionRetentionMetadata.builder()
                                                        .accountId(accountIdentifier)
                                                        .bucketName(bucketName)
                                                        .planExecutionId(planExecutionId)
                                                        .endTs(100L)
                                                        .retentionFileData(Collections.emptyList())
                                                        .build();

    assertThatThrownBy(
        () -> retentionMetadataService.filterRetentionFileData(retentionMetadata2, uuid, EXECUTION_METADATA))
        .hasMessage("[DATA_RETENTION]: The requested retention metadata for uuid: uuid, doesn't exist for collection: "
            + "planExecutionsMetadata");

    ExecutionRetentionMetadata retentionMetadata3 =
        ExecutionRetentionMetadata.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(RetentionFileData.builder().uuid(uuid).fileName("test").build()))
            .build();

    assertThatThrownBy(
        () -> retentionMetadataService.filterRetentionFileData(retentionMetadata3, uuid, EXECUTION_METADATA))
        .hasMessage("[DATA_RETENTION]: The requested retention metadata for uuid: uuid, doesn't exist for collection: "
            + "planExecutionsMetadata");

    ExecutionRetentionMetadata retentionMetadata4 =
        ExecutionRetentionMetadata.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_GRAPH).fileName("test1").build(),
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build()))
            .build();

    assertThat(retentionMetadataService.filterRetentionFileData(retentionMetadata4, uuid, EXECUTION_METADATA))
        .isEqualTo(RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build());

    ExecutionRetentionMetadata retentionMetadata5 =
        ExecutionRetentionMetadata.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_GRAPH).fileName("test1").build(),
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build()))
            .build();

    assertThat(retentionMetadataService.filterRetentionFileData(retentionMetadata5, uuid, EXECUTION_METADATA))
        .isEqualTo(RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build());

    ExecutionRetentionMetadata retentionMetadata6 =
        ExecutionRetentionMetadata.builder()
            .accountId(accountIdentifier)
            .bucketName(bucketName)
            .planExecutionId(planExecutionId)
            .endTs(100L)
            .retentionFileData(Arrays.asList(
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_GRAPH).fileName("test1").build(),
                RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build()))
            .build();

    assertThat(retentionMetadataService.filterRetentionFileData(retentionMetadata6, uuid, EXECUTION_METADATA))
        .isEqualTo(RetentionFileData.builder().uuid(uuid).collection(EXECUTION_METADATA).fileName("test").build());
  }
}
