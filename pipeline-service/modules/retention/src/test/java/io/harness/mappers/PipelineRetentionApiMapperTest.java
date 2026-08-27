/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.retention.PipelineRetentionPeriod;
import io.harness.retention.PipelineRetentionPeriodResponseDTO;
import io.harness.retention.PipelineUpdateRetentionPeriodResponseDTO;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineRetentionApiMapperTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToResponseDTO() {
    assertThat(PipelineRetentionApiMapper.toResponseDTO(null)).isNull();
    assertThat(PipelineRetentionApiMapper.toResponseDTO(
                   PipelineRetentionPeriod.builder()
                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build()))
        .isEqualTo(PipelineRetentionPeriodResponseDTO.builder()
                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build());
    assertThat(PipelineRetentionApiMapper.toResponseDTO(
                   PipelineRetentionPeriod.builder()
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build()))
        .isEqualTo(PipelineRetentionPeriodResponseDTO.builder()
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToUpdateResponseDTO() {
    assertThat(PipelineRetentionApiMapper.toUpdateResponseDTO(null)).isNull();
    assertThat(PipelineRetentionApiMapper.toUpdateResponseDTO(
                   PipelineRetentionPeriod.builder()
                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build()))
        .isEqualTo(PipelineUpdateRetentionPeriodResponseDTO.builder()
                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS)
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .indexMigrationStatus(PipelineSearchMigrationStatus.NOT_STARTED)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build());
    assertThat(PipelineRetentionApiMapper.toUpdateResponseDTO(
                   PipelineRetentionPeriod.builder()
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build()))
        .isEqualTo(PipelineUpdateRetentionPeriodResponseDTO.builder()
                       .newIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS)
                       .oldIndexRetentionPeriod(PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS)
                       .build());
  }
}
