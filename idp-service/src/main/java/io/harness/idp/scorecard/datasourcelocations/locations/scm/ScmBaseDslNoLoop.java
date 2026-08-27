/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONNECTOR_CONFIGURATION;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_DATA_SOURCE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SOURCE_LOCATION_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationNoLoop;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public abstract class ScmBaseDslNoLoop extends DataSourceLocationNoLoop {
  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) {
    String dataSourceIdentifier = dataFetchDTO.getDataPoint().getDataSourceIdentifier();
    if ((dataSourceIdentifier.equals(BITBUCKET_IDENTIFIER) || dataSourceIdentifier.equals(GITHUB_IDENTIFIER)
            || dataSourceIdentifier.equals(GITLAB_IDENTIFIER))
        && (isEmpty(possibleReplaceableRequestBodyPairs.get(REPO_SCM))
            || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_OWNER))
            || isEmpty(possibleReplaceableRequestBodyPairs.get(REPOSITORY_NAME)))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, SOURCE_LOCATION_ANNOTATION_ERROR));
      return false;
    }

    if (dataSourceIdentifier.equals(BITBUCKET_IDENTIFIER)
        && !possibleReplaceableRequestBodyPairs.get(REPO_SCM).contains(BITBUCKET_IDENTIFIER)) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, INVALID_DATA_SOURCE));
      return false;
    }

    if ((dataSourceIdentifier.equals(GITLAB_IDENTIFIER) || dataSourceIdentifier.equals(GITHUB_IDENTIFIER))
        && !possibleReplaceableUrlPairs.get(API_BASE_URL).contains(possibleReplaceableRequestBodyPairs.get(REPO_SCM))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, INVALID_DATA_SOURCE));
      return false;
    }

    if (dataSourceIdentifier.equals(HARNESS_IDENTIFIER)
        && !possibleReplaceableRequestBodyPairs.get(REPO_SCM).contains(HARNESS_IDENTIFIER)) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, INVALID_DATA_SOURCE));
      return false;
    }

    if (isEmpty(replaceableHeaders.get(AUTHORIZATION_HEADER))) {
      data.put(dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, INVALID_CONNECTOR_CONFIGURATION));
      return false;
    }
    return true;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return data.get(REPO_SCM);
  }
}
