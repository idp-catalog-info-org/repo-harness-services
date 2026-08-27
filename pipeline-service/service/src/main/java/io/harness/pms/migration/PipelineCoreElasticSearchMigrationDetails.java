/*
 * Copyright 2024 Harness Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCoreElasticSearchMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.ElasticSearchMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, PmsElasticSearchCreateRunningExecutionsIndex.class))
        .add(Pair.of(2, PmsElasticSearchCreateExecutionAlias6Month.class))
        .add(Pair.of(3, PmsElasticSearchCreateIlmPolicies.class))
        .add(Pair.of(4, PipelineExecutionRetentionCreateReconciliationIteratorRecord.class))
        .add(Pair.of(5, PmsElasticSearchUpdateParentUniqueIdExecutionAlias6Month.class))
        .add(Pair.of(6, PmsElasticSearchUpdateParentUniqueIdExecutionForAccountSpecificRetentions.class))
        .add(Pair.of(7, PmsElasticSearchUpdateParentUniqueIdRuntimeExecutionIndexes.class))
        .add(Pair.of(8, PmsElasticSearchUpdateInputSetIdentifiersExecutionAlias6Month.class))
        .add(Pair.of(9, PmsElasticSearchUpdateInputSetIdentifiersExecutionForAccountSpecificRetentions.class))
        .add(Pair.of(10, PmsElasticSearchUpdateInputSetIdentifiersRuntimeExecutionIndexes.class))
        .add(Pair.of(11, PmsElasticSearchUpdateNotesExecutionAlias6Month.class))
        .add(Pair.of(12, PmsElasticSearchUpdateNotesExecutionForAccountSpecificRetentions.class))
        .add(Pair.of(13, PmsElasticSearchUpdateNotesRuntimeExecutionIndexes.class))
        .add(Pair.of(14, PmsElasticSearchUpdatePipelineTimeoutTsExecutionAlias6Month.class))
        .add(Pair.of(15, PmsElasticSearchUpdatePipelineTimeoutTsExecutionForAccountSpecificRetentions.class))
        .add(Pair.of(16, PmsElasticSearchUpdatePipelineTimeoutTsRuntimeExecutionIndexes.class))
        .build();
  }
}
