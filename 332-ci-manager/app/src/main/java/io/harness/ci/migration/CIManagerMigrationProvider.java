/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.migration;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.ng.MigrationProvider;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
@Singleton
public class CIManagerMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return ModuleType.CI.getDisplayName();
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return CIManagerSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      {
        add(CIManagerTimeScaleMigrationDetails.class);
        add(CIManagerMongoMigrationDetails.class);
        add(CIManagerTimeScaleBGMigrationDetails.class);
      }
    };
  }
}
