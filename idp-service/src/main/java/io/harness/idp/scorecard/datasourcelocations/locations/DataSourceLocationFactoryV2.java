/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataSourceLocationFactoryV2 {
  private final HQLDataSourceLocation hqlDataSourceLocation;
  private final CatalogDataSourceLocation catalogDataSourceLocation;

  public DataSourceLocationV2 getDataSourceLocation(DataSourceLocationType type) {
    if (type == DataSourceLocationType.HQL)
      return hqlDataSourceLocation;
    else if (type == DataSourceLocationType.CATALOG)
      return catalogDataSourceLocation;

    throw new IllegalStateException("No valid DSL found!");
  }
}
