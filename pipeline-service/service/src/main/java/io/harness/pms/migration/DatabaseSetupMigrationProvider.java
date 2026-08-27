/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.ng.MigrationProvider;

import java.util.ArrayList;
import java.util.List;

public class DatabaseSetupMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "DatabaseSetup";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return DatabaseSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(DatabaseSetupMigrationDetails.class); }
    };
  }
}
