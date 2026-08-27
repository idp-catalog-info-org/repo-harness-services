/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.HARNESS_ACCOUNT;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BODY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;
import io.harness.spec.server.idp.v1.model.KubernetesRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class KubernetesProvider extends HttpDataSourceProvider {
  private static final String KUBERNETES_LABEL_SELECTOR_ANNOTATION = "backstage.io/kubernetes-label-selector";
  private static final String KUBERNETES_NAMESPACE_ANNOTATION = "backstage.io/kubernetes-namespace";
  final ConfigReader configReader;
  final IdpAuthInterceptor idpAuthInterceptor;

  final String base;

  protected KubernetesProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, IdpAuthInterceptor idpAuthInterceptor, String base,
      DataSourceRepository dataSourceRepository) {
    super(KUBERNETES_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
    this.idpAuthInterceptor = idpAuthInterceptor;
    this.base = base;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataFetchDTOS, String configs) {
    Map<String, String> replaceableHeaders = new HashMap<>(this.getAuthHeaders(accountIdentifier, null, null));
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);
    List<ClusterConfig> clustersConfigList = getClustersConfig(accountIdentifier, configs);
    Map<String, Object> annotations;
    if (entity instanceof CatalogEntity) {
      Map<String, Object> metadata =
          (Map<String, Object>) ((CatalogEntity) entity).getDecoratedEntityMap().get("metadata");
      annotations = (Map<String, Object>) metadata.get("annotations");
    } else {
      annotations = BackstageCatalogEntity.getValue(
          ((BackstageCatalogEntity) entity).getMetadata(), MetadataFieldConstants.ANNOTATIONS, Map.class);
    }
    String labelSelector = null;
    String namespace = null;
    if (annotations != null) {
      labelSelector = (String) annotations.get(KUBERNETES_LABEL_SELECTOR_ANNOTATION);
      namespace = (String) annotations.get(KUBERNETES_NAMESPACE_ANNOTATION);
    }
    Map<String, String> possibleReplaceableRequestBodyPairs =
        prepareRequestBodyReplaceablePairs(clustersConfigList, labelSelector, namespace, dataFetchDTOS);
    return processOut(accountIdentifier, KUBERNETES_IDENTIFIER, entity, replaceableHeaders,
        possibleReplaceableRequestBodyPairs, prepareUrlReplaceablePairs(), dataFetchDTOS, false, Set.of());
  }

  @Override
  protected Map<String, String> prepareUrlReplaceablePairs(String... keysValues) {
    return Map.of(HOST, base);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host) {
    return idpAuthInterceptor.getAuthHeaders();
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return Map.of();
  }

  private Map<String, String> prepareRequestBodyReplaceablePairs(
      List<ClusterConfig> clustersConfig, String labelSelector, String namespace, List<DataFetchDTO> dataFetchDTOS) {
    List<DataPointInputValues> dataPoints = new ArrayList<>();
    for (DataFetchDTO dataFetchDTO : dataFetchDTOS) {
      DataPointInputValues dataPointInputValues = new DataPointInputValues();
      dataPointInputValues.setDataPointIdentifier(dataFetchDTO.getDataPoint().getIdentifier());
      dataPoints.add(dataPointInputValues);
    }
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();
    dataSourceLocationInfo.setDataPoints(dataPoints);
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.clusters(clustersConfig);
    kubernetesConfig.dataSourceLocation(dataSourceLocationInfo);
    if (!isEmpty(labelSelector)) {
      kubernetesConfig.labelSelector(URLEncoder.encode(labelSelector, StandardCharsets.UTF_8));
    }
    kubernetesConfig.namespace(namespace);
    KubernetesRequest kubernetesRequest = new KubernetesRequest();
    kubernetesRequest.setRequest(kubernetesConfig);

    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();
    possibleReplaceableRequestBodyPairs.put(BODY, JacksonUtils.write(kubernetesRequest));
    possibleReplaceableRequestBodyPairs.putAll(prepareUrlReplaceablePairs());
    return possibleReplaceableRequestBodyPairs;
  }

  private List<ClusterConfig> getClustersConfig(String accountIdentifier, String configs) {
    String clustersExpression = "appConfig.kubernetes.clusterLocatorMethods[0].clusters";
    List<Map<String, Object>> clusters =
        (List<Map<String, Object>>) configReader.getConfigValues(accountIdentifier, configs, clustersExpression);
    List<ClusterConfig> clustersConfig = new ArrayList<>();
    for (int i = 0; i < clusters.size(); ++i) {
      ClusterConfig clusterConfig = new ClusterConfig();
      String urlExpression = String.format("%s[%s].url", clustersExpression, i);
      String tokenExpression = String.format("%s[%s].serviceAccountToken", clustersExpression, i);
      String nameExpression = String.format("%s[%s].name", clustersExpression, i);
      String url = (String) configReader.getConfigValues(accountIdentifier, configs, urlExpression);
      String token = (String) configReader.getConfigValues(accountIdentifier, configs, tokenExpression);
      String name = (String) configReader.getConfigValues(accountIdentifier, configs, nameExpression);
      clusterConfig.setUrl(url);
      clusterConfig.setName(name);
      clusterConfig.setToken(token);
      clustersConfig.add(clusterConfig);
    }
    return clustersConfig;
  }
}
