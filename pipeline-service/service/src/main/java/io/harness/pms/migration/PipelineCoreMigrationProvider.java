/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.ng.MigrationProvider;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineCoreMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return ModuleType.PMS.getDisplayName();
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return PipelineCoreSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(PipelineCoreMigrationDetails.class); }
      { add(PipelineCoreTimeScaleMigrationDetails.class); }
      { add(PipelineCoreTimeScaleBgMigrationDetails.class); }
      { add(PipelineCoreBackgroundMigrationDetails.class); }
      { add(PipelineCoreElasticSearchMigrationDetails.class); }
      { add(PipelineCoreDataRetentionMigrationDetails.class); }
      { add(PipelinePostgresMigrationDetails.class); }
    };
  }
}
