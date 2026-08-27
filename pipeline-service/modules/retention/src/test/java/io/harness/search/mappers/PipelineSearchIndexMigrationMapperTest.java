/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineSearchIndexMigrationMapperTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToDTO() {
    assertThat(PipelineSearchIndexMigrationMapper.toDTO(null)).isNull();
    assertThat(PipelineSearchIndexMigrationMapper.toDTO(
                   PipelineSearchIndexMigrationEntity.builder()
                       .accountIdentifier("ACCOUNT_ID")
                       .createdAt(1L)
                       .uuid("UUID")
                       .lastUpdatedAt(1L)
                       .elasticTaskID("TASK1")
                       .elasticBufferSyncTaskID("TASK2")
                       .status(PipelineSearchMigrationStatus.COMPLETE)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS)
                       .migrationStartTime(2L)
                       .migrationEndTime(3L)
                       .build()))
        .isEqualTo(PipelineSearchIndexMigration.builder()
                       .accountIdentifier("ACCOUNT_ID")
                       .createdAt(1L)
                       .uuid("UUID")
                       .lastUpdatedAt(1L)
                       .elasticTaskID("TASK1")
                       .elasticBufferSyncTaskID("TASK2")
                       .status(PipelineSearchMigrationStatus.COMPLETE)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS)
                       .migrationStartTime(2L)
                       .migrationEndTime(3L)
                       .build());

    assertThat(PipelineSearchIndexMigrationMapper.toDTO(
                   PipelineSearchIndexMigrationEntity.builder()
                       .accountIdentifier("ACCOUNT_ID")
                       .createdAt(1L)
                       .uuid("UUID")
                       .lastUpdatedAt(1L)
                       .status(PipelineSearchMigrationStatus.NOT_STARTED)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS)
                       .build()))
        .isEqualTo(PipelineSearchIndexMigration.builder()
                       .accountIdentifier("ACCOUNT_ID")
                       .createdAt(1L)
                       .uuid("UUID")
                       .lastUpdatedAt(1L)
                       .status(PipelineSearchMigrationStatus.NOT_STARTED)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS)
                       .build());
  }
}
