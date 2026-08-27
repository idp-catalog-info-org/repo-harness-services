/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationV2;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.DataSourceProvider;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class IntegrationDataSourceProvider implements DataSourceProvider {
  private String identifier;
  protected DataPointService dataPointService;
  protected DataSourceLocationFactoryV2 dataSourceLocationFactory;
  protected DataSourceLocationRepository dataSourceLocationRepository;
  protected DataPointParserFactory dataPointParserFactory;
  protected DataSourceRepository dataSourceRepository;

  protected IntegrationDataSourceProvider(String identifier, DataPointService dataPointService,
      DataSourceLocationFactoryV2 dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository) {
    this.identifier = identifier;
    this.dataPointService = dataPointService;
    this.dataSourceLocationFactory = dataSourceLocationFactory;
    this.dataSourceLocationRepository = dataSourceLocationRepository;
    this.dataPointParserFactory = dataPointParserFactory;
    this.dataSourceRepository = dataSourceRepository;
  }

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataToFetch, String configs) {
    Map<String, List<DataFetchDTO>> dataToFetchByDsl =
        dataPointService.getDslDataPointsInfo(accountIdentifier, getIdentifier(), dataToFetch);
    return fetchV2Data(accountIdentifier, entity, dataToFetchByDsl);
  }

  /**
   * Splits the requested data points into legacy and V2 buckets based on their DSL type, fetches legacy data points
   * through the supplied legacy provider and V2 data points through the V2 datasource locations, then merges the
   * results under this provider's identifier.
   */
  protected Map<String, Map<String, Object>> fetchDataWithLegacySplit(String accountIdentifier, Object entity,
      List<DataFetchDTO> dataToFetch, String configs, DataSourceProvider legacyProvider) {
    String identifier = getIdentifier();
    Map<String, List<DataFetchDTO>> dataToFetchByDsl =
        dataPointService.getDslDataPointsInfo(accountIdentifier, identifier, dataToFetch);

    List<DataFetchDTO> legacyDataPoints = new ArrayList<>();
    Map<String, List<DataFetchDTO>> v2DataToFetchByDsl = new HashMap<>();
    Map<String, DataSourceLocationEntity> v2LocationsByDsl = new HashMap<>();
    dataToFetchByDsl.forEach((dslIdentifier, value) -> {
      DataSourceLocationEntity dslEntity = dataSourceLocationRepository.findByIdentifier(dslIdentifier);
      if (dslEntity == null) {
        log.warn("Skipping {} DSL: no DataSourceLocation found for identifier {}", identifier, dslIdentifier);
        return;
      }
      if (dslEntity.getType().isLegacy()) {
        legacyDataPoints.addAll(value);
      } else {
        v2DataToFetchByDsl.put(dslIdentifier, value);
        v2LocationsByDsl.put(dslIdentifier, dslEntity);
      }
    });

    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
    if (!legacyDataPoints.isEmpty()) {
      mergeProviderData(aggregatedData, legacyProvider.fetchData(accountIdentifier, entity, legacyDataPoints, configs));
    }
    if (!v2DataToFetchByDsl.isEmpty()) {
      mergeProviderData(aggregatedData, fetchV2Data(accountIdentifier, entity, v2DataToFetchByDsl, v2LocationsByDsl));
    }
    return aggregatedData;
  }

  private Map<String, Map<String, Object>> fetchV2Data(
      String accountIdentifier, Object entity, Map<String, List<DataFetchDTO>> dataToFetchByDsl) {
    Map<String, DataSourceLocationEntity> locationsByDsl = new HashMap<>();
    dataToFetchByDsl.keySet().forEach(dslIdentifier -> {
      DataSourceLocationEntity location = dataSourceLocationRepository.findByIdentifier(dslIdentifier);
      if (location != null) {
        locationsByDsl.put(dslIdentifier, location);
      }
    });
    return fetchV2Data(accountIdentifier, entity, dataToFetchByDsl, locationsByDsl);
  }

  private Map<String, Map<String, Object>> fetchV2Data(String accountIdentifier, Object entity,
      Map<String, List<DataFetchDTO>> dataToFetchByDsl, Map<String, DataSourceLocationEntity> locationsByDsl) {
    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
    for (Map.Entry<String, List<DataFetchDTO>> entry : dataToFetchByDsl.entrySet()) {
      String dslIdentifier = entry.getKey();
      List<DataFetchDTO> dataToFetchForDsl = entry.getValue();
      DataSourceLocationEntity dataSourceLocationEntity = locationsByDsl.get(dslIdentifier);
      if (dataSourceLocationEntity == null) {
        log.warn("Skipping {} DSL: no DataSourceLocation found for identifier {}", getIdentifier(), dslIdentifier);
        continue;
      }
      DataSourceLocationV2 dataSourceLocation =
          dataSourceLocationFactory.getDataSourceLocation(dataSourceLocationEntity.getType());
      for (DataFetchDTO dataFetchDTO : dataToFetchForDsl) {
        try {
          Map<String, Object> response = dataSourceLocation.fetchData(
              accountIdentifier, entity, dataSourceLocationEntity, Collections.singletonList(dataFetchDTO));
          parseResponseAgainstDataPoint(dataFetchDTO, response, aggregatedData, dataSourceLocationEntity.getType());
        } catch (Exception e) {
          log.warn("Could not fetch data for entity - accountId={}, dsl={}, datapoint={}, rule={}", accountIdentifier,
              dslIdentifier, dataFetchDTO.getDataPoint().getIdentifier(), dataFetchDTO.getRuleIdentifier(), e);
        }
      }
    }
    return aggregatedData;
  }

  private void mergeProviderData(
      Map<String, Map<String, Object>> aggregatedData, Map<String, Map<String, Object>> source) {
    Map<String, Object> providerData = source.get(getIdentifier());
    if (providerData != null) {
      aggregatedData.computeIfAbsent(getIdentifier(), k -> new HashMap<>()).putAll(providerData);
    }
  }

  private void parseResponseAgainstDataPoint(DataFetchDTO dataFetchDTO, Map<String, Object> response,
      Map<String, Map<String, Object>> aggregatedData, DataSourceLocationType dataSourceLocationType) {
    Map<String, Object> providerData = aggregatedData.getOrDefault(getIdentifier(), new HashMap<>());

    DataPointEntity dataPointEntity = dataFetchDTO.getDataPoint();
    DataPointParser dataPointParser =
        dataPointParserFactory.getParser(dataPointEntity.getIdentifier(), dataSourceLocationType);

    // Wrap the response with rule identifier as the parser expects it
    Map<String, Object> wrappedResponse = Map.of(dataFetchDTO.getRuleIdentifier(), response);
    Object values = dataPointParser.parseDataPoint(wrappedResponse, dataFetchDTO);

    if (values != null) {
      providerData.putAll((Map<String, Object>) values);
    }

    aggregatedData.put(getIdentifier(), providerData);
  }
}
