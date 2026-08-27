/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.IDP)
public class IdpTimescaleBGMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, CreateBackstageCatalog.class))
        .add(Pair.of(2, CreateBackstageScaffolderTasks.class))
        .add(Pair.of(3, CreateScorecards.class))
        .add(Pair.of(4, CreatePlugins.class))
        .add(Pair.of(5, CreateChecks.class))
        .add(Pair.of(6, CreateScorecardsChecks.class))
        .add(Pair.of(7, CreateScorecardStats.class))
        .add(Pair.of(8, CreateCheckStats.class))
        .add(Pair.of(9, CreateActiveDevelopers.class))
        .add(Pair.of(10, AlterScorecardsChecks.class))
        .add(Pair.of(11, AlterCheckStats.class))
        .add(Pair.of(12, AlterScorecardStats.class))
        .add(Pair.of(13, AlterScorecardsChecksWithCreatedAt.class))
        .add(Pair.of(14, CreateCatalog.class))
        .add(Pair.of(15, AlterBackstageScaffolderTasks.class))
        .add(Pair.of(16, AlterBackstageScaffolderTasksAddCreatedBy.class))
        .build();
  }
}
