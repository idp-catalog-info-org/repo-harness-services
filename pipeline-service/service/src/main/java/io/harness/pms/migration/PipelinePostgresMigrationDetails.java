/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelinePostgresMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.PostgresMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, SetupPostgresDatabase.class))
        .add(Pair.of(2, CreatePostgresIndexFunction.class))
        .add(Pair.of(3, CreateOrchestrationGraphCacheTable.class))
        .add(Pair.of(4, AddIndexOnValidUntilToOrchestrationGraphCacheTable.class))
        .add(Pair.of(5, AddPatternOpsIndexOrchestrationGraphCache.class))
        .add(Pair.of(6, CreateGraphVertexTable.class))
        .add(Pair.of(7, RecreateGraphVertexUpdateInfoIdsIndex.class))
        .add(Pair.of(8, WidenGraphVertexStatusColumn.class))
        .add(Pair.of(9, AddBarrierAndAdviserColumns.class))
        .add(Pair.of(10, CreateStepConcurrencyQueueTable.class))
        .add(Pair.of(11, CreatePlanCreationQueueTable.class))
        .add(Pair.of(12, WidenPlanCreationQueueIdentifierColumns.class))
        .build();
  }
}
