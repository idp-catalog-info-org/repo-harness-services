/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.parseObjectToString;
import static io.harness.idp.common.Constants.PAGERDUTY_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_SERVICE_ID;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_TARGET_URL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class PagerDutyProvider extends HttpDataSourceProvider {
  private static final String PAGERDUTY_ANNOTATION = "pagerduty.com/service-id";
  private static final String TARGET_URL_EXPRESSION_KEY = "appConfig.proxy.endpoints.\"/pagerduty\".target";

  private static final String AUTH_TOKEN_EXPRESSION_KEY =
      "appConfig.proxy.endpoints.\"/pagerduty\".headers.Authorization";
  protected PagerDutyProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(PAGERDUTY_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }
  final ConfigReader configReader;

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs, null);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);

    Map<String, Object> annotations;
    if (entity instanceof CatalogEntity) {
      Map<String, Object> metadata =
          (Map<String, Object>) ((CatalogEntity) entity).getDecoratedEntityMap().get("metadata");
      annotations = (Map<String, Object>) metadata.get("annotations");
    } else {
      annotations = BackstageCatalogEntity.getValue(
          ((BackstageCatalogEntity) entity).getMetadata(), MetadataFieldConstants.ANNOTATIONS, Map.class);
    }
    String pagerDutyServiceId = null;
    if (annotations != null) {
      pagerDutyServiceId = parseObjectToString(annotations.get(PAGERDUTY_ANNOTATION));
    }
    String targetUrl =
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, TARGET_URL_EXPRESSION_KEY));

    return processOut(accountIdentifier, PAGERDUTY_IDENTIFIER, entity, replaceableHeaders,
        prepareUrlReplaceablePairs(PAGERDUTY_SERVICE_ID, pagerDutyServiceId, PAGERDUTY_TARGET_URL, targetUrl,
            "{CURRENT_TIME_IN_UTC}",
            LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))),
        prepareUrlReplaceablePairs(PAGERDUTY_SERVICE_ID, pagerDutyServiceId, PAGERDUTY_TARGET_URL, targetUrl,
            "{CURRENT_TIME_IN_UTC}",
            LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))),
        dataPointsAndInputValues, false, Set.of());
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host) {
    String authToken =
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, AUTH_TOKEN_EXPRESSION_KEY));
    return Map.of(AUTHORIZATION_HEADER, authToken);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return Map.of();
  }
}
