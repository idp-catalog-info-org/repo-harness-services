/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.CommonUtils.getDomainFromUrl;
import static io.harness.idp.common.CommonUtils.parseObjectToString;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
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
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.service.git.GitlabIntegrationOpsImpl;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitlabProvider extends ScmBaseProvider {
  static final String GITLAB_EXPRESSION_KEY = "appConfig.integrations.gitlab";
  static final String INDEX = "[index]";
  static final String HOST_EXPRESSION_KEY = "appConfig.integrations.gitlab.[index].host";
  static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.gitlab.[index].token";
  static final String GITLAB_REF = "?ref_type=heads";

  public GitlabProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository,
      ConnectorResourceClient connectorResourceClient, GitlabIntegrationOpsImpl gitlabIntegrationOps,
      BackstageEnvVariableService backstageEnvVariableService, AccountClient accountClient,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    super(GITLAB_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
    this.connectorResourceClient = connectorResourceClient;
    this.gitlabIntegrationOps = gitlabIntegrationOps;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.accountClient = accountClient;
    this.isUseLocalGitConnectorForScoreComputationEnabled = isUseLocalGitConnectorForScoreComputationEnabled;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    return scmProcessOut(accountIdentifier, entity, dataPointsAndInputValues, configs);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host) {
    String index = findMatchingHostIndex(accountIdentifier, configs, host, GITLAB_EXPRESSION_KEY);
    String token = parseObjectToString(
        configReader.getConfigValues(accountIdentifier, configs, TOKEN_EXPRESSION_KEY.replace(INDEX, index)));
    return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(token) ? "Bearer " + token : StringUtils.EMPTY);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    String connectorRef = null;
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (isUseLocalGitConnectorForScoreComputationEnabled && entity instanceof InlineCatalogEntity inlineEntity
        && CatalogUtils.extractConnectorRefFromSpec(inlineEntity) != null) {
      connectorRef = CatalogUtils.extractConnectorRefFromSpec(inlineEntity);
      if (connectorRef == null) {
        return getAuthHeaders(accountIdentifier, configs, host); // fallback if missing
      }

      String[] connectorRefSplit = connectorRef.split("[.]");
      if (connectorRefSplit.length == 2 && "org".equals(connectorRefSplit[0])) {
        orgIdentifier = inlineEntity.getOrgIdentifier();
      } else if (connectorRefSplit.length == 1) {
        orgIdentifier = inlineEntity.getOrgIdentifier();
        projectIdentifier = inlineEntity.getProjectIdentifier();
      }

    } else if (entity instanceof GitReferencedCatalogEntity gitEntity) {
      if (isUseLocalGitConnectorForScoreComputationEnabled
          && CatalogUtils.extractConnectorRefFromSpec(gitEntity) != null) {
        connectorRef = CatalogUtils.extractConnectorRefFromSpec(gitEntity);
      }

      if (connectorRef == null) {
        connectorRef = gitEntity.getConnectorRef();
      }
      if (connectorRef == null) {
        return getAuthHeaders(accountIdentifier, configs, host); // fallback if missing
      }

      String[] connectorRefSplit = connectorRef.split("[.]");
      if (connectorRefSplit.length == 2 && "org".equals(connectorRefSplit[0])) {
        orgIdentifier = gitEntity.getOrgIdentifier();
      } else if (connectorRefSplit.length == 1) {
        orgIdentifier = gitEntity.getOrgIdentifier();
        projectIdentifier = gitEntity.getProjectIdentifier();
      }

    } else {
      return getAuthHeaders(accountIdentifier, configs, host); // fallback for other types
    }

    Optional<ConnectorDTO> optionalConnectorDTO = Optional.empty();
    try {
      optionalConnectorDTO = NGRestUtils.getResponse(connectorResourceClient.get(
          removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception ex) {
      log.warn("Error in connector resource get for connector = {} account = {} org = {} project = {} error = {}",
          removeScopeFromIdentifier(connectorRef), accountIdentifier, orgIdentifier, projectIdentifier, ex.getMessage(),
          ex);
    }

    if (optionalConnectorDTO.isEmpty()) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    GitlabConnectorDTO gitlabConnectorDTO =
        gitlabIntegrationOps.getConnectorConfigDTO(optionalConnectorDTO.get().getConnectorInfo());

    if (gitlabConnectorDTO.getApiAccess() == null) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    GitIntegrationTokenAuth auth =
        (GitIntegrationTokenAuth) gitlabIntegrationOps.getApiAuthForScorecards(gitlabConnectorDTO);
    if (auth == null) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    String[] secretRefSplit = auth.getTokenSecretIdentifier().split("[.]");
    String secretOrgIdentifier = null;
    String secretProjectIdentifier = null;

    if (secretRefSplit.length == 2 && "org".equals(secretRefSplit[0])) {
      secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
    } else if (secretRefSplit.length == 1) {
      secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
      secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
    }

    Pair<String, Long> decryptedValueAndLastModifiedTime =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("GITLAB_API_TOKEN",
            auth.getTokenSecretIdentifier(), accountIdentifier, secretOrgIdentifier, secretProjectIdentifier);

    String token = decryptedValueAndLastModifiedTime.getFirst();
    return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(token) ? "Bearer " + token : StringUtils.EMPTY);
  }

  @Override
  Map<String, String> fetchApiBaseUrl(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    if (isUseLocalGitConnectorForScoreComputationEnabled && entity instanceof InlineCatalogEntity inlineEntity) {
      String sourceLocationUrl = CatalogUtils.extractSourceLocationUrlFromSpec(inlineEntity);
      if (sourceLocationUrl != null) {
        return Map.of(API_BASE_URL, getDomainFromUrl(sourceLocationUrl));
      }
    }
    if (entity instanceof GitReferencedCatalogEntity gitReferencedCatalogEntity) {
      if (isUseLocalGitConnectorForScoreComputationEnabled) {
        String sourceLocationUrl = CatalogUtils.extractSourceLocationUrlFromSpec(gitReferencedCatalogEntity);
        if (sourceLocationUrl != null) {
          return Map.of(API_BASE_URL, getDomainFromUrl(sourceLocationUrl));
        }
      }
      return Map.of(API_BASE_URL, getDomainFromUrl(gitReferencedCatalogEntity.getRepoURL()));
    }
    String index = findMatchingHostIndex(accountIdentifier, configs, host, GITLAB_EXPRESSION_KEY);
    return Map.of(API_BASE_URL,
        parseObjectToString(
            configReader.getConfigValues(accountIdentifier, configs, HOST_EXPRESSION_KEY.replace(INDEX, index))));
  }

  @Override
  protected Map<String, String> prepareRequestBodyReplaceablePairs(String catalogLocation) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();

    if (catalogLocation.endsWith(GITLAB_REF)) {
      catalogLocation = catalogLocation.substring(0, catalogLocation.length() - GITLAB_REF.length());
    }

    List<String> catalogLocationParts = new ArrayList<>(Arrays.asList(catalogLocation.split("/")));

    if (catalogLocationParts.size() >= 5) {
      possibleReplaceableRequestBodyPairs.put(REPO_SCM, catalogLocationParts.get(2));
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_OWNER, catalogLocationParts.get(3));
      StringBuilder subGroup = new StringBuilder();
      int index = 4;
      while (index < catalogLocationParts.size() && !catalogLocationParts.get(index).equals("-")
          && !catalogLocationParts.get(index).equals("tree") && !catalogLocationParts.get(index).equals("blob")) {
        subGroup.append(catalogLocationParts.get(index)).append("/");
        index++;
      }
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_NAME, subGroup.substring(0, subGroup.length() - 1));

      if (catalogLocationParts.size() > index && catalogLocationParts.get(index).equals("-")) {
        catalogLocationParts.remove(index);
      }

      index++;
      if (catalogLocationParts.size() > index) {
        possibleReplaceableRequestBodyPairs.put(REPOSITORY_BRANCH, catalogLocationParts.get(index));
      }

      index++;
      StringBuilder subFolder = new StringBuilder();
      if (catalogLocationParts.size() > index) {
        for (int i = index; i < catalogLocationParts.size(); i++) {
          subFolder.append(catalogLocationParts.get(i)).append("/");
        }
      }
      possibleReplaceableRequestBodyPairs.put(REPOSITORY_SUB_FOLDER, subFolder.toString());
    }

    return possibleReplaceableRequestBodyPairs;
  }
}
