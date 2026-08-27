/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;

import com.github.luben.zstd.Zstd;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

@OwnedBy(PIPELINE)
public class ExecutionRetentionUtilsTest extends CategoryTest {
  private static final String accountIdentifier = "abcde";
  private static final String uuid = "uuid";
  private static final String planExecutionId = "planExecutionId";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testConvertDBRecordToBytes() {
    PipelineExecutionSummaryEntity executionSummary = PipelineExecutionSummaryEntity.builder()
                                                          .planExecutionId(planExecutionId)
                                                          .validUntil(Date.from(Instant.ofEpochMilli(100L)))
                                                          .build();
    String bytesObj = new String(ExecutionRetentionUtils.convertDBRecordToBytes(
                                     executionSummary, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY),
        StandardCharsets.UTF_8);

    assertThat(ExecutionRetentionUtils.convertBytesToObject(ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
                   bytesObj.getBytes(StandardCharsets.UTF_8), new HashMap<>(), PipelineExecutionSummaryEntity.class))
        .isEqualTo(executionSummary);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCompressBytesIfRequired() {
    String cacheBytes =
        "{\"cacheContextOrder\":102,\"cacheKey\":\"cacheKey\",\"cacheParams\":null,\"lastUpdatedAt\":101,\"planExecutionId\":\"planExecutionId\",\"startTs\":100,\"endTs\":101,\"status\":null,\"rootNodeIds\":null,\"adjacencyList\":null}";

    MockedStatic<Zstd> zstdMockedStatic = mockStatic(Zstd.class);
    zstdMockedStatic.when(() -> Zstd.compress(eq(cacheBytes.getBytes(StandardCharsets.UTF_8))))
        .thenReturn("testing".getBytes(StandardCharsets.UTF_8));
    byte[] gotBytes = ExecutionRetentionUtils.compressBytesIfRequired(
        cacheBytes.getBytes(StandardCharsets.UTF_8), ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH);
    assertThat(gotBytes).isEqualTo("testing".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testBuildFilePathForObjectStore() {
    assertThat(ExecutionRetentionUtils.buildFilePathForObjectStore(
                   accountIdentifier, 100L, uuid, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY))
        .isEqualTo("abcde/19700101/pms-harness/pipeline_execution_summary/uuid.json");
  }
}
