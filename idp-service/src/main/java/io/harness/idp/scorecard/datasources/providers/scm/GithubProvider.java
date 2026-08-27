/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.getDomainFromUrl;
import static io.harness.idp.common.CommonUtils.parseObjectToString;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_INSTALLATION_ID;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.intfc.DelegateSelectable;
import io.harness.git.GitClientHelper;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationGithubAppAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.service.git.GithubIntegrationOpsImpl;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.json.JSONObject;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubProvider extends ScmBaseProvider {
  static final String GITHUB_EXPRESSION_KEY = "appConfig.integrations.github";
  static final String INDEX = "[index]";
  static final String TARGET_URL_EXPRESSION_KEY = "appConfig.integrations.github.[index].apiBaseUrl";
  static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.github.[index].token";
  static final String GITHUB_APP_ID_EXPRESSION = "appConfig.integrations.github.[index].apps.0.appId";
  static final String GITHUB_APP_PRIVATE_KEY_EXPRESSION = "appConfig.integrations.github.[index].apps.0.privateKey";
  final GithubService githubService;
  final ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;

  public GithubProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository, GithubService githubService,
      ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper, ConnectorResourceClient connectorResourceClient,
      GithubIntegrationOpsImpl githubIntegrationOps, BackstageEnvVariableService backstageEnvVariableService,
      AccountClient accountClient, boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    super(GITHUB_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
    this.githubService = githubService;
    this.proxyEnvVariableServiceWrapper = proxyEnvVariableServiceWrapper;
    this.connectorResourceClient = connectorResourceClient;
    this.githubIntegrationOps = githubIntegrationOps;
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
  public Map<String, String> getAuthHeaders(String accountIdentifier, String configs, String host) {
    String index = findMatchingHostIndex(accountIdentifier, configs, host, GITHUB_EXPRESSION_KEY);
    String token = parseObjectToString(
        configReader.getConfigValues(accountIdentifier, configs, TOKEN_EXPRESSION_KEY.replace(INDEX, index)));
    if (StringUtils.isEmpty(token)) {
      String appId = parseObjectToString(
          configReader.getConfigValues(accountIdentifier, configs, GITHUB_APP_ID_EXPRESSION.replace(INDEX, index)));
      String installationId = parseObjectToString(configReader.getDecryptedValue(
          accountIdentifier, INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_" + host.toUpperCase().replace(".", "_")));
      String privateKey = parseObjectToString(configReader.getConfigValues(
          accountIdentifier, configs, GITHUB_APP_PRIVATE_KEY_EXPRESSION.replace(INDEX, index)));
      String url = parseObjectToString(
          configReader.getConfigValues(accountIdentifier, configs, TARGET_URL_EXPRESSION_KEY.replace(INDEX, index)));
      if (!StringUtils.isEmpty(appId) && !StringUtils.isEmpty(installationId) && !StringUtils.isEmpty(privateKey)
          && !StringUtils.isEmpty(url)) {
        JSONObject jsonObject = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
        if (jsonObject.has(host) && jsonObject.get(host).equals(true)) {
          token = "dummy";
        } else {
          token = githubService.getToken(buildGithubAppConfig(appId, installationId, privateKey, url));
        }
      }
    }
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

    GithubConnectorDTO githubConnectorDTO =
        githubIntegrationOps.getConnectorConfigDTO(optionalConnectorDTO.get().getConnectorInfo());

    if (githubConnectorDTO.getApiAccess() == null) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    GitIntegrationAuth auth = githubIntegrationOps.getApiAuthForScorecards(githubConnectorDTO);
    if (auth == null) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    if (auth instanceof GitIntegrationTokenAuth gitIntegrationTokenAuth) {
      String[] secretRefSplit = gitIntegrationTokenAuth.getTokenSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && "org".equals(secretRefSplit[0])) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
      }
      Pair<String, Long> decryptedValueAndLastModifiedTime =
          backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("GITHUB_API_TOKEN",
              gitIntegrationTokenAuth.getTokenSecretIdentifier(), accountIdentifier, secretOrgIdentifier,
              secretProjectIdentifier);
      String token = decryptedValueAndLastModifiedTime.getFirst();
      return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(token) ? "Bearer " + token : StringUtils.EMPTY);
    }

    if (auth instanceof GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth) {
      String appId = gitIntegrationGithubAppAuth.getApplicationId();
      if (isEmpty(appId)) {
        String applicationIdSecretIdentifier = gitIntegrationGithubAppAuth.getApplicationIdSecretIdentifier();
        String[] secretRefSplit = applicationIdSecretIdentifier.split("[.]");
        String secretOrgIdentifier = null;
        String secretProjectIdentifier = null;
        if (secretRefSplit.length == 2 && "org".equals(secretRefSplit[0])) {
          secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        }
        if (secretRefSplit.length == 1) {
          secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
          secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
        }
        Pair<String, Long> decryptedValueAndLastModifiedTime =
            backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("GITHUB_API_APP_APPLICATION_ID",
                applicationIdSecretIdentifier, accountIdentifier, secretOrgIdentifier, secretProjectIdentifier);
        appId = decryptedValueAndLastModifiedTime.getFirst();
      }

      String installationId = gitIntegrationGithubAppAuth.getInstallationId();
      if (isEmpty(installationId)) {
        String installationIdSecretIdentifier = gitIntegrationGithubAppAuth.getInstallationIdSecretIdentifier();
        String[] secretRefSplit = installationIdSecretIdentifier.split("[.]");
        String secretOrgIdentifier = null;
        String secretProjectIdentifier = null;
        if (secretRefSplit.length == 2 && "org".equals(secretRefSplit[0])) {
          secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        }
        if (secretRefSplit.length == 1) {
          secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
          secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
        }
        Pair<String, Long> decryptedValueAndLastModifiedTime =
            backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("GITHUB_API_APP_INSTALLATION_ID",
                installationIdSecretIdentifier, accountIdentifier, secretOrgIdentifier, secretProjectIdentifier);
        installationId = decryptedValueAndLastModifiedTime.getFirst();
      }
      String privateKey = gitIntegrationGithubAppAuth.getPrivateKeySecretIdentifier();
      String[] secretRefSplit = privateKey.split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && "org".equals(secretRefSplit[0])) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
      }
      Pair<String, Long> decryptedValueAndLastModifiedTime =
          backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("GITHUB_API_APP_PRIVATE_KEY", privateKey,
              accountIdentifier, secretOrgIdentifier, secretProjectIdentifier);
      privateKey = decryptedValueAndLastModifiedTime.getFirst();
      String url = GitClientHelper.getGithubApiURL(githubConnectorDTO.getUrl());
      String token = null;
      if (!StringUtils.isEmpty(appId) && !StringUtils.isEmpty(installationId) && !StringUtils.isEmpty(privateKey)
          && !StringUtils.isEmpty(url)) {
        ConnectorConfigDTO connectorConfigDTO = optionalConnectorDTO.get().getConnectorInfo().getConnectorConfig();
        if (connectorConfigDTO instanceof DelegateSelectable) {
          token = "dummy";
        } else {
          token = githubService.getToken(buildGithubAppConfig(appId, installationId, privateKey, url));
        }
      }
      return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(token) ? "Bearer " + token : StringUtils.EMPTY);
    }

    return getAuthHeaders(accountIdentifier, configs, host);
  }

  @Override
  Map<String, String> fetchApiBaseUrl(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    if (isUseLocalGitConnectorForScoreComputationEnabled && entity instanceof InlineCatalogEntity inlineEntity) {
      String sourceLocationUrl = CatalogUtils.extractSourceLocationUrlFromSpec(inlineEntity);
      if (sourceLocationUrl != null) {
        return Map.of(
            API_BASE_URL, githubIntegrationOps.getGithubApiBaseUrlFromHost(getDomainFromUrl(sourceLocationUrl)));
      }
    }
    if (entity instanceof GitReferencedCatalogEntity gitReferencedCatalogEntity) {
      if (isUseLocalGitConnectorForScoreComputationEnabled) {
        String sourceLocationUrl = CatalogUtils.extractSourceLocationUrlFromSpec(gitReferencedCatalogEntity);
        if (sourceLocationUrl != null) {
          return Map.of(
              API_BASE_URL, githubIntegrationOps.getGithubApiBaseUrlFromHost(getDomainFromUrl(sourceLocationUrl)));
        }
      }
      return Map.of(API_BASE_URL,
          githubIntegrationOps.getGithubApiBaseUrlFromHost(getDomainFromUrl(gitReferencedCatalogEntity.getRepoURL())));
    }
    String index = findMatchingHostIndex(accountIdentifier, configs, host, GITHUB_EXPRESSION_KEY);
    return Map.of(API_BASE_URL,
        parseObjectToString(
            configReader.getConfigValues(accountIdentifier, configs, TARGET_URL_EXPRESSION_KEY.replace(INDEX, index))));
  }

  private GithubAppConfig buildGithubAppConfig(String appId, String installationId, String privateKey, String url) {
    return GithubAppConfig.builder()
        .appId(appId)
        .installationId(installationId)
        .privateKey(privateKey)
        .githubUrl(url)
        .build();
  }
}
