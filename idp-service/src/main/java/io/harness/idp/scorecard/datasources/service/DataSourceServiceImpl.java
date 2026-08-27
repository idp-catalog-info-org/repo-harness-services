/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.scorecard.datasources.cache.EnabledIntegrationsInMemoryCache.DATA_SOURCE_INTEGRATION_TYPES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.integrationmanager.TypesIntegrationConfig.EnumIntegrationType;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.mappers.DataPointMapper;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasources.cache.EnabledIntegrationsInMemoryCache;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.mappers.DataSourceDataPointsMapMapper;
import io.harness.idp.scorecard.datasources.mappers.DataSourceMapper;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class DataSourceServiceImpl implements DataSourceService {
  DataSourceRepository dataSourceRepository;
  DataPointService dataPointService;
  EnabledIntegrationsInMemoryCache enabledIntegrationsInMemoryCache;

  @Override
  public List<DataSource> getAllDataSourcesDetailsForAnAccount(String accountIdentifier) {
    List<DataSourceEntity> dataSourceEntities =
        dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(accountIdentifier));
    return filterUnavailableDataSources(accountIdentifier, dataSourceEntities)
        .stream()
        .map(DataSourceMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<DataPoint> getAllDataPointsDetailsForDataSource(String accountIdentifier, String dataSourceIdentifier) {
    EnumIntegrationType integrationType = DATA_SOURCE_INTEGRATION_TYPES.get(dataSourceIdentifier);
    if (integrationType != null) {
      Optional<Set<EnumIntegrationType>> enabledIntegrationTypes =
          enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(accountIdentifier);
      if (enabledIntegrationTypes.isPresent() && !enabledIntegrationTypes.get().contains(integrationType)) {
        return List.of();
      }
    }

    return dataPointService.getAllDataPointsDetailsForAccountAndDataSource(accountIdentifier, dataSourceIdentifier);
  }

  @Override
  public List<DataSourceDataPointsMap> getDataPointsForDataSources(String accountIdentifier) {
    List<DataPointEntity> dataPointsInAccount = dataPointService.getAllDataPointsForAccount(accountIdentifier);
    List<DataSourceEntity> dataSourcesInAccount =
        dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(accountIdentifier));
    return filterUnavailableDataSources(accountIdentifier, dataSourcesInAccount)
        .stream()
        .map(dataSourceEntity -> {
          return DataSourceDataPointsMapMapper.toDto(DataSourceMapper.toDTO(dataSourceEntity),
              dataPointsInAccount.stream()
                  .filter(dataPointEntity
                      -> dataPointEntity.getDataSourceIdentifier().equals(dataSourceEntity.getIdentifier()))
                  .map(DataPointMapper::toDto)
                  .collect(Collectors.toList()));
        })
        .collect(Collectors.toList());
  }

  private List<DataSourceEntity> filterUnavailableDataSources(
      String accountIdentifier, List<DataSourceEntity> dataSourceEntities) {
    Optional<Set<EnumIntegrationType>> enabledIntegrationTypes =
        enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(accountIdentifier);
    return enabledIntegrationTypes
        .map(enumIntegrationTypes
            -> dataSourceEntities.stream()
                   .filter(dataSourceEntity -> {
                     EnumIntegrationType integrationType =
                         DATA_SOURCE_INTEGRATION_TYPES.get(dataSourceEntity.getIdentifier());
                     return integrationType == null || enumIntegrationTypes.contains(integrationType);
                   })
                   .collect(Collectors.toList()))
        .orElse(dataSourceEntities);
  }
}
