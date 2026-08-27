/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;
import io.harness.pms.migration.background.AddIndexOnParentUniqueIdAccountIdToStepExecutionMigration;
import io.harness.pms.migration.background.AddIndexOnParentUniqueIdIdentifierToPipelinesMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToPipelineExecutionSummaryCdMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToPipelineExecutionSummaryMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToPipelinesMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToServiceInfraInfoMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToStageExecutionMigration;
import io.harness.pms.migration.background.AddIndexOnUniqueIdToStepExecutionMigration;
import io.harness.pms.migration.background.DropAndRecreateParentUniqueIdOnlyIndexOnPipelineExecutionSummary;
import io.harness.pms.migration.background.DropAndRecreateParentUniqueIdPipelineIdIndexOnPipelineExecutionSummaryCd;
import io.harness.pms.migration.background.DropAndRecreateParentUniqueIdStarttsIndexOnPipelineExecutionSummary;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCoreTimeScaleBgMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.TimeScaleBGMigration;
  }

  @Override
  public boolean isBackground() {
    return true;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, SyncPipelineExecutionsWithWrongStatusInTimescale.class))
        .add(Pair.of(2, RenameEnvGroupRefInServiceInfraInfoTable.class))
        .add(Pair.of(3, NoopPipelineCoreMigration.class))
        .add(Pair.of(4, NoopPipelineCoreMigration.class))
        .add(Pair.of(5, NoopPipelineCoreMigration.class))
        .add(Pair.of(6, AddIndexOnUniqueIdToPipelineExecutionSummaryMigration.class))
        .add(Pair.of(7, AddIndexOnUniqueIdToPipelineExecutionSummaryCdMigration.class))
        .add(Pair.of(8, AddIndexOnUniqueIdToPipelinesMigration.class))
        .add(Pair.of(9, AddIndexOnUniqueIdToServiceInfraInfoMigration.class))
        .add(Pair.of(10, AddIndexOnUniqueIdToStageExecutionMigration.class))
        .add(Pair.of(11, AddIndexOnUniqueIdToStepExecutionMigration.class))
        .add(Pair.of(12, DropAndRecreateParentUniqueIdStarttsIndexOnPipelineExecutionSummary.class))
        .add(Pair.of(13, DropAndRecreateParentUniqueIdPipelineIdIndexOnPipelineExecutionSummaryCd.class))
        .add(Pair.of(14, DropAndRecreateParentUniqueIdOnlyIndexOnPipelineExecutionSummary.class))
        .add(Pair.of(15, AddIndexOnParentUniqueIdAccountIdToStepExecutionMigration.class))
        .add(Pair.of(16, AddIndexOnParentUniqueIdIdentifierToPipelinesMigration.class))
        .build();
  }
}
