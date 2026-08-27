/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;
import static io.harness.idp.common.Constants.HARNESS_ACCOUNT;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.COMPLETE_REPO_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.intfc.DelegateSelectable;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.BitbucketCloudIntegrationOpsImpl;
import io.harness.idp.integrations.service.git.GithubIntegrationOpsImpl;
import io.harness.idp.integrations.service.git.GitlabIntegrationOpsImpl;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.providers.HttpDataSourceProvider;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public abstract class ScmBaseProvider extends HttpDataSourceProvider {
  public static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";

  @Inject ConfigReader configReader;
  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationOps;
  @Inject GitlabIntegrationOpsImpl gitlabIntegrationOps;
  @Inject GithubIntegrationOpsImpl githubIntegrationOps;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject AccountClient accountClient;
  boolean isUseLocalGitConnectorForScoreComputationEnabled;

  protected ScmBaseProvider(String identifier, DataPointService dataPointService,
      DataSourceLocationFactory dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository) {
    super(identifier, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository, dataPointParserFactory,
        dataSourceRepository);
  }

  protected Map<String, Map<String, Object>> scmProcessOut(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();

    String catalogLocation = null;
    if (entity instanceof CatalogEntity) {
      String sourceCodeUrl = CatalogUtils.extractSourceLocationUrlFromSpec((CatalogEntity) entity);
      if (!isEmpty(sourceCodeUrl)) {
        catalogLocation = sourceCodeUrl;
      } else {
        if (((CatalogEntity) entity).getSourceLocation() != null) {
          catalogLocation = ((CatalogEntity) entity).getSourceLocation();
        }
      }
    } else {
      Map<String, Object> annotations = BackstageCatalogEntity.getValue(
          ((BackstageCatalogEntity) entity).getMetadata(), MetadataFieldConstants.ANNOTATIONS, Map.class);
      if (annotations != null) {
        catalogLocation = (String) annotations.get(SOURCE_LOCATION_ANNOTATION);
      }
    }
    if (catalogLocation != null) {
      possibleReplaceableRequestBodyPairs = this.prepareRequestBodyReplaceablePairs(catalogLocation);
    }

    log.info("USE_LOCAL_GIT_CONNECTOR_FOR_SCORE_COMPUTATION scm - {} for account - {}",
        isUseLocalGitConnectorForScoreComputationEnabled, accountIdentifier);

    Map<String, String> possibleReplaceableUrlBodyPairs = new HashMap<>(possibleReplaceableRequestBodyPairs);
    Map<String, String> apiBaseUrlReplaceablePair = this.fetchApiBaseUrl(accountIdentifier, configs,
        possibleReplaceableUrlBodyPairs.get(REPO_SCM), entity, isUseLocalGitConnectorForScoreComputationEnabled);
    if (!isEmpty(apiBaseUrlReplaceablePair)) {
      possibleReplaceableUrlBodyPairs.putAll(apiBaseUrlReplaceablePair);
    }

    Map<String, String> authHeaders = new HashMap<>();
    if (!isEmpty(possibleReplaceableRequestBodyPairs.get(REPO_SCM))) {
      if (possibleReplaceableRequestBodyPairs.get(REPO_SCM).contains(HARNESS_IDENTIFIER)) {
        authHeaders = this.getAuthHeaders(accountIdentifier, possibleReplaceableUrlBodyPairs.get(COMPLETE_REPO_NAME),
            possibleReplaceableUrlBodyPairs.get(REPO_SCM), entity, isUseLocalGitConnectorForScoreComputationEnabled);
      } else {
        authHeaders = this.getAuthHeaders(accountIdentifier, configs, possibleReplaceableUrlBodyPairs.get(REPO_SCM),
            entity, isUseLocalGitConnectorForScoreComputationEnabled);
      }
    }
    Map<String, String> replaceableHeaders = new HashMap<>(authHeaders);
    replaceableHeaders.put(HARNESS_ACCOUNT, accountIdentifier);

    boolean throughDelegate = false;
    Set<String> delegateSelectors = new HashSet<>();

    String connectorRef = null;
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (isUseLocalGitConnectorForScoreComputationEnabled && entity instanceof InlineCatalogEntity inlineEntity
        && CatalogUtils.extractConnectorRefFromSpec(inlineEntity) != null) {
      connectorRef = CatalogUtils.extractConnectorRefFromSpec(inlineEntity);

      String[] connectorRefSplit = connectorRef.split("[.]");
      if (connectorRefSplit.length == 2 && "org".equals(connectorRefSplit[0])) {
        orgIdentifier = inlineEntity.getOrgIdentifier();
      } else if (connectorRefSplit.length == 1) {
        orgIdentifier = inlineEntity.getOrgIdentifier();
        projectIdentifier = inlineEntity.getProjectIdentifier();
      }

    } else if (entity instanceof GitReferencedCatalogEntity gitReferencedCatalogEntity) {
      if (isUseLocalGitConnectorForScoreComputationEnabled
          && CatalogUtils.extractConnectorRefFromSpec(gitReferencedCatalogEntity) != null) {
        connectorRef = CatalogUtils.extractConnectorRefFromSpec(gitReferencedCatalogEntity);
      }

      if (connectorRef == null) {
        connectorRef = gitReferencedCatalogEntity.getConnectorRef();
      }

      String[] connectorRefSplit = connectorRef.split("[.]");
      if (connectorRefSplit.length == 2 && "org".equals(connectorRefSplit[0])) {
        orgIdentifier = gitReferencedCatalogEntity.getOrgIdentifier();
      } else if (connectorRefSplit.length == 1) {
        orgIdentifier = gitReferencedCatalogEntity.getOrgIdentifier();
        projectIdentifier = gitReferencedCatalogEntity.getProjectIdentifier();
      }
    }

    if (connectorRef != null) {
      Optional<ConnectorDTO> optionalConnectorDTO = Optional.empty();
      try {
        optionalConnectorDTO = NGRestUtils.getResponse(connectorResourceClient.get(
            removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier));
      } catch (Exception ex) {
        log.warn("Error in connector resource get for connector = {} account = {} org = {} project = {} error = {}",
            removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier,
            ex.getMessage(), ex);
      }
      if (optionalConnectorDTO.isPresent()) {
        throughDelegate = optionalConnectorDTO.get().getConnectorInfo().getConnectorConfig().shouldExecuteOnDelegate();

        ConnectorConfigDTO connectorConfigDTO = optionalConnectorDTO.get().getConnectorInfo().getConnectorConfig();
        if (connectorConfigDTO instanceof DelegateSelectable) {
          delegateSelectors = ((DelegateSelectable) connectorConfigDTO).getDelegateSelectors();
        }
      }
    }

    return processOut(accountIdentifier, this.getIdentifier(), entity, replaceableHeaders,
        possibleReplaceableRequestBodyPairs, possibleReplaceableUrlBodyPairs, dataPointsAndInputValues, throughDelegate,
        delegateSelectors);
  }

  protected Map<String, String> prepareRequestBodyReplaceablePairs(String catalogLocation) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();

    List<String> catalogLocationParts = new ArrayList<>(Arrays.asList(catalogLocation.split("/")));

    if (catalogLocationParts.size() >= 5) {
      possibleReplaceableRequestBodyPairs.put(REPO_SCM, catalogLocationParts.get(2));
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_OWNER, catalogLocationParts.get(3));
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_NAME, catalogLocationParts.get(4));

      if (catalogLocationParts.size() > 6) {
        possibleReplaceableRequestBodyPairs.put(REPOSITORY_BRANCH, catalogLocationParts.get(6));
      }

      StringBuilder subFolder = new StringBuilder();
      if (catalogLocationParts.size() > 7) {
        for (int i = 7; i < catalogLocationParts.size(); i++) {
          subFolder.append(catalogLocationParts.get(i)).append("/");
        }
      }
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_SUB_FOLDER, subFolder.toString());
    }

    return possibleReplaceableRequestBodyPairs;
  }

  abstract Map<String, String> fetchApiBaseUrl(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled);

  protected String findMatchingHostIndex(String accountIdentifier, String configs, String host, String expressionKey) {
    int index = 0;
    int i = 0;
    List<Map<String, Object>> gitIntegrationConfigs =
        (List<Map<String, Object>>) configReader.getConfigValues(accountIdentifier, configs, expressionKey);
    if (!isEmpty(host) && !isEmpty(gitIntegrationConfigs)) {
      for (Map<String, Object> gitIntegrationConfig : gitIntegrationConfigs) {
        if (host.equals(gitIntegrationConfig.get("host"))) {
          index = i;
          break;
        }
        i++;
      }
    }
    return String.valueOf(index);
  }
}
