/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ACCOUNT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.COMPLETE_REPO_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ORG_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.security.dto.PrincipalType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProvider extends ScmBaseProvider {
  static final Pattern BASE_URL_PATTERN = Pattern.compile("https://([^/]+)");
  static final Pattern ACCOUNT_IDENTIFIER_PATTERN = Pattern.compile("account/([^/]+)");
  static final Pattern ORG_IDENTIFIER_PATTERN = Pattern.compile("orgs/([^/]+)");
  static final Pattern PROJECT_IDENTIFIER_PATTERN = Pattern.compile("projects/([^/]+)");
  static final Pattern REPO_IDENTIFIER_PATTERN = Pattern.compile("repos/([^/]+)");
  static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("files/([^/]+)");
  static final Pattern SUB_FOLDER_PATTERN = Pattern.compile("~/(.*)");
  final String base;
  final HarnessCodeRepoConfig harnessCodeRepoConfig;
  final HarnessCodeConnectorUtils harnessCodeConnectorUtils;

  public HarnessProvider(DataPointService dataPointService, DataSourceLocationFactory dataSourceLocationFactory,
      DataSourceLocationRepository dataSourceLocationRepository, DataPointParserFactory dataPointParserFactory,
      HarnessCodeRepoConfig harnessCodeRepoConfig, HarnessCodeConnectorUtils harnessCodeConnectorUtils, String base,
      DataSourceRepository dataSourceRepository, AccountClient accountClient,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    super(HARNESS_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.harnessCodeRepoConfig = harnessCodeRepoConfig;
    this.harnessCodeConnectorUtils = harnessCodeConnectorUtils;
    this.base = base;
    this.accountClient = accountClient;
    this.isUseLocalGitConnectorForScoreComputationEnabled = isUseLocalGitConnectorForScoreComputationEnabled;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataPointsAndInputValues, String configs) {
    return scmProcessOut(accountIdentifier, entity, dataPointsAndInputValues, configs);
  }

  @Override
  public Map<String, String> getAuthHeaders(String accountIdentifier, String completeRepoName, String host) {
    String token = harnessCodeConnectorUtils.getTokenWithClaims(accountIdentifier,
        harnessCodeRepoConfig.getServiceClientSharedSecret(), completeRepoName, IDP_SERVICE.getServiceId(),
        PrincipalType.SERVICE.name(), null, 1);
    return Map.of(AUTHORIZATION_HEADER, IDP_SERVICE.getServiceId() + " " + token);
  }

  @Override
  protected Map<String, String> getAuthHeaders(String accountIdentifier, String completeRepoName, String host,
      Object entity, boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return getAuthHeaders(accountIdentifier, completeRepoName, host);
  }

  @Override
  Map<String, String> fetchApiBaseUrl(String accountIdentifier, String configs, String host, Object entity,
      boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    return Map.of(HOST, base);
  }

  @Override
  protected Map<String, String> prepareRequestBodyReplaceablePairs(String catalogLocation) {
    Map<String, String> possibleReplaceableRequestBodyPairs = new HashMap<>();
    String accountIdentifier = getValue(ACCOUNT_IDENTIFIER_PATTERN, catalogLocation);
    String orgIdentifier = getValue(ORG_IDENTIFIER_PATTERN, catalogLocation);
    String projectIdentifier = getValue(PROJECT_IDENTIFIER_PATTERN, catalogLocation);
    String repoIdentifier = getValue(REPO_IDENTIFIER_PATTERN, catalogLocation);
    possibleReplaceableRequestBodyPairs.put(REPO_SCM, getValue(BASE_URL_PATTERN, catalogLocation));
    possibleReplaceableRequestBodyPairs.put(ACCOUNT_IDENTIFIER, accountIdentifier);
    possibleReplaceableRequestBodyPairs.put(ORG_IDENTIFIER, orgIdentifier);
    possibleReplaceableRequestBodyPairs.put(PROJECT_IDENTIFIER, projectIdentifier);
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_NAME, repoIdentifier);
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_BRANCH, getValue(BRANCH_NAME_PATTERN, catalogLocation));
    String subFolder = getValue(SUB_FOLDER_PATTERN, catalogLocation);
    if (!StringUtils.isEmpty(subFolder)) {
      subFolder = subFolder + "/";
    }
    possibleReplaceableRequestBodyPairs.put(REPOSITORY_SUB_FOLDER, subFolder);

    possibleReplaceableRequestBodyPairs.put(COMPLETE_REPO_NAME,
        HarnessStringUtils.joinNullableString(
            "/", accountIdentifier, orgIdentifier, projectIdentifier, repoIdentifier));
    return possibleReplaceableRequestBodyPairs;
  }

  private String getValue(Pattern pattern, String sourceLocation) {
    Matcher matcher = pattern.matcher(sourceLocation);
    return matcher.find() ? matcher.group(1) : StringUtils.EMPTY;
  }
}
