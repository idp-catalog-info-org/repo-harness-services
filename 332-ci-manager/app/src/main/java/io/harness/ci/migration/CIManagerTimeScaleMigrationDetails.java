/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
public class CIManagerTimeScaleMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.TimeScaleMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, CreateServiceAndCIExecutionIndex.class))
        .add(Pair.of(2, CreateTimescaleCIStageTableWhereNotExist.class))
        .add(Pair.of(3, AlterTimescalePipelineSummaryExecutionCILicense.class))
        .add(Pair.of(4, AlterTimescalePipelineSummaryExecutionUserSource.class))
        .add(Pair.of(5, UpdateBuildCreditsTimescaleStageExecutionSummary.class))
        .add(Pair.of(6, CreateTimeBucketListBigIntFunction.class))
        .add(Pair.of(7, AddModuleColumnToStageExecutionSummaryCITable.class))
        .add(Pair.of(8, AlterTimescalePipelineSummaryExecutionCISavings.class))
        .add(Pair.of(9, CreateTimescaleCICommittersTableWhereNotExist.class))
        .add(Pair.of(10, CreateCICommittersTableIndex.class))
        .add(Pair.of(11, AlterTimescaleStageSummaryExecutionCISavings.class))
        .add(Pair.of(12, CreateStageExecutionSummaryCITableIndex.class))
        .add(Pair.of(13, SetupDatabase.class))
        .add(Pair.of(14, AlterExecutionSummaryCITable.class))
        .add(Pair.of(15, AddUniqueIdAndParentUniqueIdToPipelineExecutionSummaryCITable.class))
        .add(Pair.of(16, AddUniqueIdAndParentUniqueIdToStageExecutionSummaryCITable.class))
        .add(Pair.of(17, AddUniqueIdAndParentUniqueIdToPipelineExecutionSummaryCICommittersTable.class))
        .add(Pair.of(18, AlterExecutionSummaryCITable.class))
        .build();
  }
}
