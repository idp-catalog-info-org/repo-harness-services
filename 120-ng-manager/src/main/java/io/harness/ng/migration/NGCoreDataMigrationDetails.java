/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.utils.NoopMigration;
import io.harness.ng.core.migration.background.AddIndexOnParentIdAccountIdIdentifierToConnectorsMigration;
import io.harness.ng.core.migration.background.AddIndexOnParentUniqueIdToProjectsMigration;
import io.harness.ng.core.migration.background.AddIndexOnParentUniqueIdToServicesMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToConnectorsMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToEnvironmentsMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToExecutionTagsInfoNgMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToInfrastructuresMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToNgUsersMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToOrganizationsMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToProjectsMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToRuntimeInputsInfoMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToServicesMigration;
import io.harness.ng.core.migration.background.AddIndexOnUniqueIdToTagsInfoNgMigration;
import io.harness.ng.core.migration.background.AddReplicaIdentityToLicenseUsageHourly;
import io.harness.ng.core.migration.background.DropAndRecreateParentUniqueIdFirstIndexOnStageExecution;
import io.harness.ng.core.migration.background.DropAndRecreateParentUniqueIdServiceIdIndexOnServiceInfraInfo;
import io.harness.ng.core.migration.background.DropAndRecreateParentUniqueIdTimesServiceIdArtifactIndexOnServiceInfraInfo;
import io.harness.ng.core.migration.background.PopulateUniqueIdForProjectsTable;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class NGCoreDataMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, NoopMigration.class))
        .add(Pair.of(2, NoopMigration.class))
        .add(Pair.of(3, NoopMigration.class))
        .add(Pair.of(4, NoopMigration.class))
        .add(Pair.of(5, AddIndexOnUniqueIdToInfrastructuresMigration.class))
        .add(Pair.of(6, AddIndexOnUniqueIdToNgUsersMigration.class))
        .add(Pair.of(7, AddIndexOnUniqueIdToOrganizationsMigration.class))
        .add(Pair.of(8, AddIndexOnUniqueIdToProjectsMigration.class))
        .add(Pair.of(9, AddIndexOnUniqueIdToServicesMigration.class))
        .add(Pair.of(10, AddIndexOnUniqueIdToTagsInfoNgMigration.class))
        .add(Pair.of(11, AddIndexOnUniqueIdToConnectorsMigration.class))
        .add(Pair.of(12, NoopMigration.class))
        .add(Pair.of(13, AddIndexOnUniqueIdToEnvironmentsMigration.class))
        .add(Pair.of(14, AddIndexOnUniqueIdToExecutionTagsInfoNgMigration.class))
        .add(Pair.of(15, AddIndexOnUniqueIdToRuntimeInputsInfoMigration.class))
        .add(Pair.of(16, DropAndRecreateParentUniqueIdFirstIndexOnStageExecution.class))
        .add(Pair.of(17, DropAndRecreateParentUniqueIdServiceIdIndexOnServiceInfraInfo.class))
        .add(Pair.of(18, DropAndRecreateParentUniqueIdTimesServiceIdArtifactIndexOnServiceInfraInfo.class))
        .add(Pair.of(19, AddIndexOnParentIdAccountIdIdentifierToConnectorsMigration.class))
        .add(Pair.of(20, AddIndexOnParentUniqueIdToProjectsMigration.class))
        .add(Pair.of(21, PopulateUniqueIdForProjectsTable.class))
        .add(Pair.of(22, AddIndexOnParentUniqueIdToServicesMigration.class))
        .add(Pair.of(23, AddReplicaIdentityToLicenseUsageHourly.class))
        .build();
  }
}
