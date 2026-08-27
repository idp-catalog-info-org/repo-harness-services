/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.EXTRACT_STRING_FROM_A_FILE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_BRANCH_PROTECTED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_FILE_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MATCH_STRING_IN_A_FILE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_CODE_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_DEPENDABOT_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_PULL_REQUESTS_BY_ACCOUNT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_SECRET_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.WORKFLOWS_COUNT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.WORKFLOW_SUCCESS_RATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubAlertsCountParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubFileContainsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubFileContentsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubFileExistsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubIsBranchProtectedParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubMeanTimeToCompleteWorkflowRunsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubMeanTimeToMergeParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubPullRequestsCountParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubWorkflowSuccessRateParser;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubWorkflowsCountParser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class GithubDataPointParserFactory extends IntegrationDataPointParserFactory {
  private final GithubMeanTimeToMergeParser githubMeanTimeToMergeParser;
  private final GithubIsBranchProtectedParser githubIsBranchProtectedParser;
  private final GithubFileExistsParser githubFileExistsParser;
  private final GithubWorkflowsCountParser githubWorkflowsCountParser;
  private final GithubWorkflowSuccessRateParser githubWorkflowSuccessRateParser;
  private final GithubMeanTimeToCompleteWorkflowRunsParser githubMeanTimeToCompleteWorkflowRunsParser;
  private final GithubAlertsCountParser githubAlertsCountParser;
  private final GithubPullRequestsCountParser githubPullRequestsCountParser;
  private final GithubFileContentsParser githubFileContentsParser;
  private final GithubFileContainsParser githubFileContainsParser;

  @Inject
  public GithubDataPointParserFactory(GithubMeanTimeToMergeParser githubMeanTimeToMergeParser,
      GithubIsBranchProtectedParser githubIsBranchProtectedParser, GithubFileExistsParser githubFileExistsParser,
      GithubWorkflowsCountParser githubWorkflowsCountParser,
      GithubWorkflowSuccessRateParser githubWorkflowSuccessRateParser,
      GithubMeanTimeToCompleteWorkflowRunsParser githubMeanTimeToCompleteWorkflowRunsParser,
      GithubAlertsCountParser githubAlertsCountParser, GithubPullRequestsCountParser githubPullRequestsCountParser,
      GithubFileContentsParser githubFileContentsParser, GithubFileContainsParser githubFileContainsParser,
      DefaultHQLParser defaultHQLParser, DefaultCatalogDSLParser defaultCatalogDSLParser) {
    super(defaultHQLParser, defaultCatalogDSLParser);
    this.githubMeanTimeToMergeParser = githubMeanTimeToMergeParser;
    this.githubIsBranchProtectedParser = githubIsBranchProtectedParser;
    this.githubFileExistsParser = githubFileExistsParser;
    this.githubWorkflowsCountParser = githubWorkflowsCountParser;
    this.githubWorkflowSuccessRateParser = githubWorkflowSuccessRateParser;
    this.githubMeanTimeToCompleteWorkflowRunsParser = githubMeanTimeToCompleteWorkflowRunsParser;
    this.githubAlertsCountParser = githubAlertsCountParser;
    this.githubPullRequestsCountParser = githubPullRequestsCountParser;
    this.githubFileContentsParser = githubFileContentsParser;
    this.githubFileContainsParser = githubFileContainsParser;
  }

  public DataPointParser getParser(String identifier, DataSourceLocationType dataSourceLocationType) {
    switch (identifier) {
      case PULL_REQUEST_MEAN_TIME_TO_MERGE:
        return githubMeanTimeToMergeParser;
      case IS_BRANCH_PROTECTED:
        return githubIsBranchProtectedParser;
      case IS_FILE_EXISTS:
        return githubFileExistsParser;
      case EXTRACT_STRING_FROM_A_FILE:
        return githubFileContentsParser;
      case MATCH_STRING_IN_A_FILE:
        return githubFileContainsParser;
      case WORKFLOWS_COUNT:
        return githubWorkflowsCountParser;
      case WORKFLOW_SUCCESS_RATE:
        return githubWorkflowSuccessRateParser;
      case MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS:
      case MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS:
        return githubMeanTimeToCompleteWorkflowRunsParser;
      case OPEN_DEPENDABOT_ALERTS:
      case OPEN_CODE_SCANNING_ALERTS:
      case OPEN_SECRET_SCANNING_ALERTS:
        return githubAlertsCountParser;
      case OPEN_PULL_REQUESTS_BY_ACCOUNT:
        return githubPullRequestsCountParser;
      default:
        return super.getParser(identifier, dataSourceLocationType);
    }
  }
}
