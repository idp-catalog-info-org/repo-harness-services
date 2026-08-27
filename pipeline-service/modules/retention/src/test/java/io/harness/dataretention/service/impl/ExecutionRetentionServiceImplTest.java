/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ENABLE_DATA_RETENTION;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.RetentionTestHelper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.config.MongoTTLConfig;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.joda.time.DateTime;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ExecutionRetentionServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountID";
  private static final String UUID = "UUID";
  private static final String bucketName = "bucketName";

  @Mock ExecutionRetentionMetadataService retentionMetadataService;
  @Mock ObjectStoreClient objectStoreClient;
  private static final DataRetentionConfig dataRetentionConfig =
      DataRetentionConfig.builder().enabled(true).mongoTTLDays(MongoTTLConfig.builder().defaultTTL(5).build()).build();
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks ExecutionRetentionServiceImpl retentionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(retentionService).set("retentionMetadataService", retentionMetadataService);
    Reflect.on(retentionService).set("objectStoreClient", objectStoreClient);
    Reflect.on(retentionService).set("dataRetentionConfig", dataRetentionConfig);
    Reflect.on(retentionService).set("pmsFeatureFlagService", pmsFeatureFlagService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testReadExpiredRecordFromObjectStore() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, PIPE_ENABLE_DATA_RETENTION)).thenReturn(false);
    assertThat(retentionService.readExpiredRecordFromObjectStore(
                   ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .isNull();
    verify(retentionMetadataService, times(0)).get(any(), any());
    verify(retentionMetadataService, times(0)).filterRetentionFileData(any(), any(), any());

    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, PIPE_ENABLE_DATA_RETENTION)).thenReturn(true);
    when(retentionMetadataService.get(UUID, EXECUTION_SUMMARY)).thenReturn(null);
    assertThat(retentionService.readExpiredRecordFromObjectStore(
                   ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .isNull();
    verify(retentionMetadataService, times(1)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(0)).filterRetentionFileData(any(), any(), any());

    when(retentionMetadataService.get(UUID, EXECUTION_SUMMARY))
        .thenReturn(ExecutionRetentionMetadata.builder().endTs(DateTime.now().minusDays(4).getMillis()).build());
    assertThat(retentionService.readExpiredRecordFromObjectStore(
                   ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .isNull();
    verify(retentionMetadataService, times(2)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(0)).filterRetentionFileData(any(), any(), any());

    ExecutionRetentionMetadata executionRetentionMetadata =
        ExecutionRetentionMetadata.builder().endTs(DateTime.now().minusDays(6).getMillis()).build();
    when(retentionMetadataService.get(UUID, EXECUTION_SUMMARY)).thenReturn(executionRetentionMetadata);
    when(retentionMetadataService.filterRetentionFileData(executionRetentionMetadata, UUID, EXECUTION_SUMMARY))
        .thenThrow(new InvalidRequestException(
            "[DATA_RETENTION]: The requested retention metadata for uuid: UUID, doesn't contain any file"));
    assertThatThrownBy(()
                           -> retentionService.readExpiredRecordFromObjectStore(
                               ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .hasMessage("[DATA_RETENTION]: The requested retention metadata for uuid: UUID, doesn't contain any file");
    verify(retentionMetadataService, times(3)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(1)).filterRetentionFileData(any(), any(), any());
    verify(objectStoreClient, times(0)).getObject(any());

    doReturn(RetentionFileData.builder().filePath("abc").build())
        .when(retentionMetadataService)
        .filterRetentionFileData(executionRetentionMetadata, UUID, EXECUTION_SUMMARY);
    when(objectStoreClient.getObject("abc")).thenReturn(null);
    assertThat(retentionService.readExpiredRecordFromObjectStore(
                   ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .isNull();
    verify(retentionMetadataService, times(4)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(2)).filterRetentionFileData(any(), any(), any());
    verify(objectStoreClient, times(1)).getObject(any());

    when(objectStoreClient.getObject("abc")).thenThrow(new InvalidRequestException("Could not fetch file from GCS"));
    assertThatThrownBy(()
                           -> retentionService.readExpiredRecordFromObjectStore(
                               ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class))
        .hasMessage("Could not fetch file from GCS");
    verify(retentionMetadataService, times(5)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(3)).filterRetentionFileData(any(), any(), any());
    verify(objectStoreClient, times(2)).getObject(any());

    StorageObject mockObject = Mockito.mock(StorageObject.class);
    Mockito.when(mockObject.getContent())
        .thenReturn("{\"name\":\"rishabh\", \"runSequence\": 1, \"pipelineDeleted\": false}".getBytes(
            StandardCharsets.UTF_8));
    Mockito.when(mockObject.getName()).thenReturn("abc");
    Mockito.when(mockObject.getSize()).thenReturn(100L);
    doReturn(mockObject).when(objectStoreClient).getObject("abc");
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) retentionService.readExpiredRecordFromObjectStore(
            ACCOUNT_ID, UUID, EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class);
    assertThat(executionSummary.getName()).isEqualTo("rishabh");
    assertThat(executionSummary.getRunSequence()).isEqualTo(1);
    assertThat(executionSummary.getPipelineDeleted()).isEqualTo(false);
    verify(retentionMetadataService, times(6)).get(UUID, EXECUTION_SUMMARY);
    verify(retentionMetadataService, times(4)).filterRetentionFileData(any(), any(), any());
    verify(objectStoreClient, times(3)).getObject(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDeleteAllPlanExecutionsData() {
    retentionService.deleteAllPlanExecutionsData(Sets.newHashSet(), false);
    verify(retentionMetadataService, times(0)).getExecutionRetentionMetadataForExecutions(any());

    Set<String> executionIDs = Sets.newHashSet("pipeline1", "pipeline2");
    retentionService.deleteAllPlanExecutionsData(executionIDs, true);
    verify(retentionMetadataService, times(0)).getExecutionRetentionMetadataForExecutions(any());

    List<String> objectPaths = Arrays.asList(
        "accountID/b/c/test1.json", "accountID/b/c/test2.json", "accountID/b/c/test3.json", "accountID/b/c/test4.json");
    Map<String, Boolean> deleteResponse = Map.of("accountID/b/c/test1.json", true, "accountID/b/c/test2.json", false,
        "accountID/b/c/test3.json", true, "accountID/b/c/test4.json", true);
    List<ExecutionRetentionMetadata> executionRetentionMetadataList = new ArrayList<>();
    executionRetentionMetadataList.add(ExecutionRetentionMetadata.builder()
                                           .accountId(ACCOUNT_ID)
                                           .bucketName(bucketName)
                                           .planExecutionId("pipeline1")
                                           .uuid("pipeline1")
                                           .endTs(100L)
                                           .retentionFileData(Arrays.asList(RetentionFileData.builder()
                                                                                .uuid("pipeline1")
                                                                                .collectionName("c1")
                                                                                .fileName("test1")
                                                                                .filePath("accountID/b/c/test1.json")
                                                                                .build(),
                                               RetentionFileData.builder()
                                                   .uuid("pipeline123")
                                                   .collectionName("collection")
                                                   .fileName("test2")
                                                   .filePath("accountID/b/c/test2.json")
                                                   .build()))
                                           .build());
    executionRetentionMetadataList.add(ExecutionRetentionMetadata.builder()
                                           .accountId(ACCOUNT_ID)
                                           .bucketName(bucketName)
                                           .planExecutionId("pipeline2")
                                           .uuid("pipeline2")
                                           .endTs(100L)
                                           .retentionFileData(Arrays.asList(RetentionFileData.builder()
                                                                                .uuid("pipeline2")
                                                                                .collectionName("c1")
                                                                                .fileName("test3")
                                                                                .filePath("accountID/b/c/test3.json")
                                                                                .build(),
                                               RetentionFileData.builder()
                                                   .uuid("pipeline223")
                                                   .collectionName("collection")
                                                   .fileName("test4")
                                                   .filePath("accountID/b/c/test4.json")
                                                   .build()))
                                           .build());
    Stream<ExecutionRetentionMetadata> stream =
        RetentionTestHelper.createCloseableIterator(executionRetentionMetadataList.iterator()).stream();
    when(retentionMetadataService.getExecutionRetentionMetadataForExecutions(eq(executionIDs))).thenReturn(stream);
    when(objectStoreClient.deleteObjectsByPaths(eq(objectPaths))).thenReturn(deleteResponse);

    retentionService.deleteAllPlanExecutionsData(executionIDs, false);
    verify(retentionMetadataService, times(1)).getExecutionRetentionMetadataForExecutions(eq(executionIDs));
    verify(objectStoreClient, times(1)).deleteObjectsByPaths(eq(objectPaths));
    verify(retentionMetadataService, times(1))
        .updateRetentionFileDataList(eq("pipeline1"), eq(List.of("accountID/b/c/test2.json")));
    verify(retentionMetadataService, times(1))
        .deleteAllExecutionRetentionMetadataByUuids(eq(Sets.newHashSet("pipeline2")));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetMongoValidUntilTTL() {
    // Inject the mock config using reflection
    Reflect.on(retentionService)
        .set("dataRetentionConfig",
            DataRetentionConfig.builder()
                .mongoTTLDays(MongoTTLConfig.builder().defaultTTL(180).executionGraph(30).executionMetadata(45).build())
                .build());

    // Test Case 1: EXECUTION_GRAPH should return executionGraph TTL
    int executionGraphTTL =
        retentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH);
    assertThat(executionGraphTTL).isEqualTo(30);

    // Test Case 3: EXECUTION_METADATA should return executionMetadata TTL
    int executionMetadataTTL =
        retentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA);
    assertThat(executionMetadataTTL).isEqualTo(45);

    // Test Case 4: EXECUTION_SUMMARY should throw InvalidRequestException
    assertThatThrownBy(
        () -> retentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("[DATA_RETENTION]: Overriding ttl for collection: EXECUTION_SUMMARY is not supported");

    // Test Case 5: APPROVAL_INSTANCES should throw InvalidRequestException
    assertThatThrownBy(
        () -> retentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.APPROVAL_INSTANCES))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("[DATA_RETENTION]: Overriding ttl for collection: APPROVAL_INSTANCES is not supported");
  }
}
