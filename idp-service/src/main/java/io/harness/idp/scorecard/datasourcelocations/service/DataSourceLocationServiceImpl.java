/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.mappers.DataSourceLocationMapper;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.spec.server.idp.v1.model.DataSourceLocationDetails;

import java.util.Collections;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class DataSourceLocationServiceImpl implements DataSourceLocationService {
  DataSourceLocationRepository dataSourceLocationRepository;
  DataSourceRepository dataSourceRepository;

  @Override
  public void createGlobalDataSourceLocation(
      String dataSourceIdentifier, DataSourceLocationDetails dataSourceLocationDetails) {
    validateRequest(dataSourceIdentifier, dataSourceLocationDetails);
    if (findGlobalDataSourceLocation(dataSourceIdentifier, dataSourceLocationDetails.getIdentifier()).isPresent()) {
      throw new InvalidRequestException(String.format("Data source location %s already exists for data source %s",
          dataSourceLocationDetails.getIdentifier(), dataSourceIdentifier));
    }
    dataSourceLocationRepository.save(
        DataSourceLocationMapper.fromDto(dataSourceLocationDetails, dataSourceIdentifier));
  }

  @Override
  public void updateGlobalDataSourceLocation(String dataSourceIdentifier, String dataSourceLocationIdentifier,
      DataSourceLocationDetails dataSourceLocationDetails) {
    if (!dataSourceLocationIdentifier.equals(dataSourceLocationDetails.getIdentifier())) {
      throw new InvalidRequestException("Data source location identifier cannot be changed");
    }
    validateRequest(dataSourceIdentifier, dataSourceLocationDetails);
    DataSourceLocationEntity dataSourceLocationEntity =
        findGlobalDataSourceLocation(dataSourceIdentifier, dataSourceLocationIdentifier)
            .orElseThrow(
                ()
                    -> new InvalidRequestException(String.format("Global data source location %s not found for data "
                            + "source %s",
                        dataSourceLocationIdentifier, dataSourceIdentifier)));
    if (dataSourceLocationEntity.getType() != toEntityType(dataSourceLocationDetails.getType())) {
      throw new InvalidRequestException("Data source location type cannot be changed");
    }
    DataSourceLocationMapper.updateEntity(dataSourceLocationEntity, dataSourceLocationDetails);
    dataSourceLocationRepository.save(dataSourceLocationEntity);
  }

  private Optional<DataSourceLocationEntity> findGlobalDataSourceLocation(
      String dataSourceIdentifier, String dataSourceLocationIdentifier) {
    DataSourceLocationEntity dataSourceLocationEntity =
        dataSourceLocationRepository.findByIdentifier(dataSourceLocationIdentifier);
    if (dataSourceLocationEntity == null || !GLOBAL_ACCOUNT_ID.equals(dataSourceLocationEntity.getAccountIdentifier())
        || !dataSourceIdentifier.equals(dataSourceLocationEntity.getDataSourceIdentifier())) {
      return Optional.empty();
    }
    return Optional.of(dataSourceLocationEntity);
  }

  private void validateRequest(String dataSourceIdentifier, DataSourceLocationDetails dataSourceLocationDetails) {
    if (dataSourceRepository
            .findByAccountIdentifierInAndIdentifier(Collections.singleton(GLOBAL_ACCOUNT_ID), dataSourceIdentifier)
            .isEmpty()) {
      throw new InvalidRequestException(String.format("Global data source %s not found", dataSourceIdentifier));
    }

    DataSourceLocationType type = toEntityType(dataSourceLocationDetails.getType());
    if (type.isLegacy()) {
      throw new InvalidRequestException(String.format("Data source location type %s is not supported", type.getType()));
    }

    switch (type) {
      case HQL -> validateHqlPayload(dataSourceLocationDetails);
      case CATALOG -> validateCatalogPayload(dataSourceLocationDetails);
      default -> throw new InvalidRequestException(String.format("Data source location type %s is not supported",
          dataSourceLocationDetails.getType()));
    }
  }

  private void validateHqlPayload(DataSourceLocationDetails dataSourceLocationDetails) {
    if (StringUtils.isBlank(dataSourceLocationDetails.getHqlTemplate())) {
      throw new InvalidRequestException("HQL template is required for HQL data source locations");
    }
    if (StringUtils.isNotBlank(dataSourceLocationDetails.getJexl())) {
      throw new InvalidRequestException("JEXL is not supported for HQL data source locations");
    }
  }

  private void validateCatalogPayload(DataSourceLocationDetails dataSourceLocationDetails) {
    if (StringUtils.isBlank(dataSourceLocationDetails.getJexl())) {
      throw new InvalidRequestException("JEXL is required for Catalog data source locations");
    }
    if (StringUtils.isNotBlank(dataSourceLocationDetails.getHqlTemplate())) {
      throw new InvalidRequestException("HQL template is not supported for Catalog data source locations");
    }
  }

  private DataSourceLocationType toEntityType(DataSourceLocationDetails.TypeEnum type) {
    return switch (type) {
      case HQL -> DataSourceLocationType.HQL;
      case CATALOG -> DataSourceLocationType.CATALOG;
    };
  }
}
