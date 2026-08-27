/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.ng.MigrationProvider;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class ApiKeyAndTokenScopeSchemaMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "apiKeyAndTokenScopeMigration";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return ApiKeyAndTokenScopeSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ImmutableList.Builder<Class<? extends MigrationDetails>>()
        .add(ApiKeyAndTokenScopeBackgroundMigrationDetails.class)
        .build();
  }
}
