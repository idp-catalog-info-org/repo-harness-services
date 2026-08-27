/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.parseObjectToString;
import static io.harness.idp.common.CommonUtils.removeScopeFromIdentifier;
import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.BitbucketCloudIntegrationOpsImpl;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BitbucketProvider extends ScmBaseProvider {
  static final String USERNAME_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.username";
  static final String PASSWORD_EXPRESSION_KEY = "appConfig.integrations.bitbucketCloud.0.appPassword";

  public BitbucketProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      ConfigReader configReader, DataSourceRepository dataSourceRepository,
      IntegrationEntityRepository integrationEntityRepository, ConnectorResourceClient connectorResourceClient,
      BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationOps,
      BackstageEnvVariableService backstageEnvVariableService, AccountClient accountClient,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    super(BITBUCKET_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.configReader = configReader;
    this.integrationEntityRepository = integrationEntityRepository;
    this.connectorResourceClient = connectorResourceClient;
    this.bitbucketCloudIntegrationOps = bitbucketCloudIntegrationOps;
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
    String username =
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, USERNAME_EXPRESSION_KEY));
    String password =
        parseObjectToString(configReader.getConfigValues(accountIdentifier, configs, PASSWORD_EXPRESSION_KEY));
    String authToken = null;
    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
      authToken = encodeBase64(username + ":" + password);
      authToken = !StringUtils.isEmpty(authToken) ? "Basic " + authToken : StringUtils.EMPTY;
    }
    authToken = handlingForApiTokenIfPresent(accountIdentifier, authToken);
    return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(authToken) ? authToken : StringUtils.EMPTY);
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
        return getAuthHeaders(accountIdentifier, configs, host); // fallback
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
        return getAuthHeaders(accountIdentifier, configs, host); // fallback
      }
      String[] connectorRefSplit = connectorRef.split("[.]");
      if (connectorRefSplit.length == 2 && "org".equals(connectorRefSplit[0])) {
        orgIdentifier = gitEntity.getOrgIdentifier();
      } else if (connectorRefSplit.length == 1) {
        orgIdentifier = gitEntity.getOrgIdentifier();
        projectIdentifier = gitEntity.getProjectIdentifier();
      }

    } else {
      return getAuthHeaders(accountIdentifier, configs, host); // fallback for other entity types
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

    BitbucketConnectorDTO bitbucketConnectorDTO =
        bitbucketCloudIntegrationOps.getConnectorConfigDTO(optionalConnectorDTO.get().getConnectorInfo());

    GitIntegrationAuth auth = bitbucketCloudIntegrationOps.getAuth(bitbucketConnectorDTO);

    if (auth == null) {
      return getAuthHeaders(accountIdentifier, configs, host);
    }

    if (auth instanceof GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth) {
      String username = gitIntegrationUsernamePasswordAuth.getUsername();
      if (!isEmpty(gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier())) {
        String[] usernameRefSplit = gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier().split("[.]");
        String usernameOrgIdentifier = null;
        String usernameProjectIdentifier = null;
        if (usernameRefSplit.length == 2 && usernameRefSplit[0].equals("org")) {
          usernameOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        }
        if (usernameRefSplit.length == 1) {
          usernameOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
          usernameProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
        }
        Pair<String, Long> decryptedValueAndLastModifiedTime =
            backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("BITBUCKET_API_USERNAME",
                gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier(), accountIdentifier,
                usernameOrgIdentifier, usernameProjectIdentifier);
        username = decryptedValueAndLastModifiedTime.getFirst();
      }
      String[] secretRefSplit = gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
      }
      Pair<String, Long> decryptedValueAndLastModifiedTime =
          backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("BITBUCKET_API_TOKEN",
              gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier(), accountIdentifier, secretOrgIdentifier,
              secretProjectIdentifier);
      String token = decryptedValueAndLastModifiedTime.getFirst();

      if (!isEmpty(username) && !isEmpty(token)) {
        String authToken = encodeBase64(username + ":" + token);
        authToken = !isEmpty(authToken) ? "Basic " + authToken : "";
        return Map.of(AUTHORIZATION_HEADER, authToken);
      }
    } else if (auth instanceof GitIntegrationTokenAuth gitIntegrationTokenAuth) {
      String[] secretRefSplit = gitIntegrationTokenAuth.getTokenSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = optionalConnectorDTO.get().getConnectorInfo().getOrgIdentifier();
        secretProjectIdentifier = optionalConnectorDTO.get().getConnectorInfo().getProjectIdentifier();
      }
      Pair<String, Long> decryptedValueAndLastModifiedTime =
          backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("BITBUCKET_API_TOKEN",
              gitIntegrationTokenAuth.getTokenSecretIdentifier(), accountIdentifier, secretOrgIdentifier,
              secretProjectIdentifier);
      String token = decryptedValueAndLastModifiedTime.getFirst();
      return Map.of(AUTHORIZATION_HEADER, !StringUtils.isEmpty(token) ? "Bearer " + token : StringUtils.EMPTY);
    }

    return getAuthHeaders(accountIdentifier, configs, host); // fallback if token or username is missing
  }

  @Override
  Map<String, String> fetchApiBaseUrl(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return Collections.emptyMap();
  }

  private String handlingForApiTokenIfPresent(String accountIdentifier, String authToken) {
    Optional<IntegrationEntity> optionalIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
            accountIdentifier, IntegrationEntity.ParentType.BITBUCKET_CLOUD, null, "bitbucket.org");
    if (optionalIntegrationEntity.isEmpty()) {
      return authToken;
    }
    GitIntegrationEntity gitIntegrationEntity = (GitIntegrationEntity) optionalIntegrationEntity.get();
    Optional<ConnectorDTO> optionalConnectorDTO = Optional.empty();
    try {
      optionalConnectorDTO = NGRestUtils.getResponse(
          connectorResourceClient.get(gitIntegrationEntity.getConnectorIdentifier(), accountIdentifier, null, null));
    } catch (Exception ex) {
      log.warn("Error in connector resource get for connector = {} account = {} error = {}",
          gitIntegrationEntity.getConnectorIdentifier(), accountIdentifier, ex.getMessage(), ex);
    }
    if (optionalConnectorDTO.isEmpty()) {
      return authToken;
    }
    BitbucketConnectorDTO bitbucketConnectorDTO =
        bitbucketCloudIntegrationOps.getConnectorConfigDTO(optionalConnectorDTO.get().getConnectorInfo());
    if (bitbucketConnectorDTO.getApiAccess() == null) {
      return authToken;
    }
    GitIntegrationAuth auth = bitbucketCloudIntegrationOps.getAuth(bitbucketConnectorDTO);
    if (auth == null) {
      return authToken;
    }
    if (auth instanceof GitIntegrationUsernamePasswordAuth) {
      return authToken;
    } else if (auth instanceof GitIntegrationTokenAuth gitIntegrationTokenAuth) {
      Pair<String, Long> decryptedValueAndLastModifiedTime =
          backstageEnvVariableService.getDecryptedValueAndLastModifiedTime("BITBUCKET_CLOUD_API_TOKEN",
              gitIntegrationTokenAuth.getTokenSecretIdentifier(), accountIdentifier, null, null);
      return "Bearer " + decryptedValueAndLastModifiedTime.getFirst();
    }
    return authToken;
  }
}
