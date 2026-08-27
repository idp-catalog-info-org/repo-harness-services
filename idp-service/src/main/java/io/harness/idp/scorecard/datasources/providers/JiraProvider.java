/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.CommonUtils.parseObjectToString;
import static io.harness.idp.common.Constants.JIRA_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_COMPONENT_REPLACER;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class JiraProvider extends HttpDataSourceProvider {
  static final String JIRA_PROJECT_ANNOTATION = "jira/project-key";
  static final String JIRA_COMPONENT_ANNOTATION = "jira/component";
  static final String JIRA_TARGET_URL_EXPRESSION_KEY = "appConfig.proxy.endpoints.\"/jira/api\".target";
  static final String AUTH_TOKEN_EXPRESSION_KEY = "appConfig.proxy.endpoints.\"/jira/api\".headers.Authorization";
  final ConfigReader configReader;

  protected JiraProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository) {
    super(JIRA_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> authHeaders = this.getAuthHeaders(accountIdentifier, configs, null);
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);
    Map<String, String> requestBodyPairs = prepareRequestBodyReplaceablePairs(entity);
    Map<String, String> requestUrlPairs = prepareUrlReplaceablePairs(API_BASE_URL,
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, JIRA_TARGET_URL_EXPRESSION_KEY)));
    requestBodyPairs.putAll(requestUrlPairs);
    return processOut(accountIdentifier, JIRA_IDENTIFIER, entity, replaceableHeaders, requestBodyPairs, requestUrlPairs,
        dataPointsAndInputValues, false, Set.of());
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host) {
    String authToken =
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, AUTH_TOKEN_EXPRESSION_KEY));
    return Map.of(AUTHORIZATION_HEADER, authToken);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return Map.of();
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(Object entity) {
    Map<String, String> requestBodyPairs = new HashMap<>();
    Map<String, Object> annotations;
    if (entity instanceof CatalogEntity) {
      Map<String, Object> metadata =
          (Map<String, Object>) ((CatalogEntity) entity).getDecoratedEntityMap().get("metadata");
      annotations = (Map<String, Object>) metadata.get("annotations");
      ;
    } else {
      annotations = BackstageCatalogEntity.getValue(
          ((BackstageCatalogEntity) entity).getMetadata(), MetadataFieldConstants.ANNOTATIONS, Map.class);
    }
    if (annotations != null) {
      String projectKey = (String) annotations.get(JIRA_PROJECT_ANNOTATION);
      String component = (String) annotations.get(JIRA_COMPONENT_ANNOTATION);
      StringBuilder builder = new StringBuilder();
      if (projectKey != null) {
        builder.append("'").append(projectKey).append("'");
        if (component != null) {
          builder.append(" AND ").append("component = ").append("'").append(component).append("'");
        }
      }
      requestBodyPairs.put(PROJECT_COMPONENT_REPLACER, builder.toString());
    }
    return requestBodyPairs;
  }
}
