/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.entity;

import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CatalogDataSourceLocationEntity extends DataSourceLocationEntity {
  private String jexl;
  public CatalogDataSourceLocationEntity() {
    super.setType(DataSourceLocationType.CATALOG);
  }
}
