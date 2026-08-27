/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.annotations.StoreIn;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.entities.NGSchema;
import io.harness.ng.DbAliases;

import java.util.Map;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@StoreIn(DbAliases.NG_MANAGER)
@Document("schema_apiKeyAndTokenScope")
@Persistent
public class ApiKeyAndTokenScopeSchema extends NGSchema {
  public ApiKeyAndTokenScopeSchema(String id, @NotEmpty Long createdAt, Long lastUpdatedAt, @NotEmpty String name,
      Map<MigrationType, Integer> migrationDetails) {
    super(id, createdAt, lastUpdatedAt, name, migrationDetails);
  }
}
