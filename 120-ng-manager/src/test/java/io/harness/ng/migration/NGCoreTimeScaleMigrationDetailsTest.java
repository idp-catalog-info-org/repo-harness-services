/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.rule.OwnerRule.PARTH_SHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.utils.NoopMigration;
import io.harness.ng.core.migration.timescale.AddIndexOnAccIdLastSyncStartedAtTsToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexOnLastSyncStartedAtTsToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.AddServiceIdToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.ChangeGitopsAppInfoUniqueIndexIncludeServiceId;
import io.harness.ng.core.migration.timescale.CreateGitopsAppInfoTable;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGCoreTimeScaleMigrationDetailsTest extends CategoryTest {
  private NGCoreTimeScaleMigrationDetails ngCoreTimeScaleMigrationDetails;

  @Before
  public void setUp() {
    ngCoreTimeScaleMigrationDetails = new NGCoreTimeScaleMigrationDetails();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void getMigrationTypeNameTest() {
    assertThat(ngCoreTimeScaleMigrationDetails.getMigrationTypeName()).isEqualTo(MigrationType.TimeScaleMigration);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void isBackgroundTest() {
    assertThat(ngCoreTimeScaleMigrationDetails.isBackground()).isFalse();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void getMigrationsTest() {
    List<Pair<Integer, Class<? extends NGMigration>>> migrations = ngCoreTimeScaleMigrationDetails.getMigrations();

    assertThat(migrations).hasSize(81);

    Set<Integer> migrationVersions = migrations.stream().map(Pair::getLeft).collect(Collectors.toSet());
    assertThat(migrationVersions).hasSize(migrations.size());

    assertThat(migrations).contains(Pair.of(35, CreateGitopsAppInfoTable.class));
    assertThat(migrations).contains(Pair.of(122, AddServiceIdToGitopsAppInfoTable.class));
    assertThat(migrations).contains(Pair.of(123, NoopMigration.class));
    assertThat(migrations).contains(Pair.of(124, AddIndexOnAccIdLastSyncStartedAtTsToGitopsAppInfoTable.class));
    assertThat(migrations).contains(Pair.of(125, ChangeGitopsAppInfoUniqueIndexIncludeServiceId.class));
    assertThat(migrations).contains(Pair.of(126, AddIndexOnLastSyncStartedAtTsToGitopsAppInfoTable.class));
  }
}
