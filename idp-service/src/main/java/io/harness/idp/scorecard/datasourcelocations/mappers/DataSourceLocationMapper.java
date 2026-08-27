/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.mappers;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.spec.server.idp.v1.model.DataSourceLocationDetails;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class DataSourceLocationMapper {
  public DataSourceLocationEntity fromDto(
      DataSourceLocationDetails dataSourceLocationDetails, String dataSourceIdentifier) {
    DataSourceLocationType type = toEntityType(dataSourceLocationDetails.getType());
    DataSourceLocationEntity dataSourceLocationEntity = createEntity(type);
    dataSourceLocationEntity.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    dataSourceLocationEntity.setIdentifier(dataSourceLocationDetails.getIdentifier());
    dataSourceLocationEntity.setDataSourceIdentifier(dataSourceIdentifier);
    dataSourceLocationEntity.setType(type);
    updateTypeSpecificFields(dataSourceLocationEntity, dataSourceLocationDetails);
    return dataSourceLocationEntity;
  }

  public void updateEntity(
      DataSourceLocationEntity dataSourceLocationEntity, DataSourceLocationDetails dataSourceLocationDetails) {
    updateTypeSpecificFields(dataSourceLocationEntity, dataSourceLocationDetails);
  }

  private DataSourceLocationEntity createEntity(DataSourceLocationType type) {
    return switch (type) {
      case HQL -> new HQLDataSourceLocationEntity();
      case CATALOG -> new CatalogDataSourceLocationEntity();
      default -> throw new InvalidRequestException(String.format("Unsupported data source location type %s", type));
    };
  }

  private void updateTypeSpecificFields(
      DataSourceLocationEntity dataSourceLocationEntity, DataSourceLocationDetails dataSourceLocationDetails) {
    switch (dataSourceLocationEntity.getType()) {
      case HQL -> ((HQLDataSourceLocationEntity) dataSourceLocationEntity)
          .setHqlTemplate(dataSourceLocationDetails.getHqlTemplate());
      case CATALOG -> ((CatalogDataSourceLocationEntity) dataSourceLocationEntity)
          .setJexl(dataSourceLocationDetails.getJexl());
      default -> throw new InvalidRequestException(
          String.format("Unsupported data source location type %s", dataSourceLocationEntity.getType()));
    }
  }

  private DataSourceLocationType toEntityType(DataSourceLocationDetails.TypeEnum type) {
    return switch (type) {
      case HQL -> DataSourceLocationType.HQL;
      case CATALOG -> DataSourceLocationType.CATALOG;
    };
  }
}
