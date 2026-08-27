/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BITBUCKET_FILE_CONTAINS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BITBUCKET_FILE_CONTENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BITBUCKET_FILE_EXISTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BITBUCKET_IS_BRANCH_PROTECTION_SET;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BITBUCKET_MEAN_TIME_TO_MERGE_PR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.CATALOG;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.CATALOG_SYSTEM;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_FILE_CONTAINS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_FILE_CONTENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_FILE_EXISTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_IS_BRANCH_PROTECTION_SET;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_MEAN_TIME_TO_MERGE_PR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_OPEN_CODE_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_OPEN_DEPENDABOT_ALERTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_OPEN_PULL_REQUESTS_BY_ACCOUNT;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_OPEN_SECRET_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_WORKFLOWS_COUNT;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITHUB_WORKFLOW_SUCCESS_RATE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITLAB_FILE_CONTAINS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITLAB_FILE_CONTENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITLAB_FILE_EXISTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITLAB_IS_BRANCH_PROTECTION_SET;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.GITLAB_MEAN_TIME_TO_MERGE_PR;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_CODE_FILE_CONTAINS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_CODE_FILE_CONTENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_CODE_FILE_EXISTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_POLICY_EVALUATION_DSL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_STO_ACTIVE_VULNERABILITIES;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_STO_SCAN_SETUP_DSL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.HARNESS_TEST_PASSING_ON_CI_IS_ZERO;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.JIRA_ISSUES_COUNT;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.JIRA_ISSUES_OPEN_CLOSE_RATIO;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.JIRA_MEAN_TIME_TO_RESOLVE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.KUBERNETES;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_INCIDENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_RESOLVED_INCIDENTS;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PAGERDUTY_SERVICE_DIRECTORY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datasourcelocations.locations.harness.HarnessProxyThroughDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.harness.HarnessSTOActiveVulnerabilitiesDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.jira.JiraIssuesCountDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.jira.JiraIssuesOpenCloseRatioDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.jira.JiraMeanTimeToResolveDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.kubernetes.KubernetesProxyThroughDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.pagerduty.PagerDutyIncidents;
import io.harness.idp.scorecard.datasourcelocations.locations.pagerduty.PagerDutyServiceDirectory;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmProxyThroughDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket.BitbucketIsBranchProtectionSetDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket.BitbucketMeanTimeToMergePRDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubFileExistsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubIsBranchProtectionSetDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubMeanTimeToCompleteSuccessWorkflowRunsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubMeanTimeToCompleteWorkflowRunsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubMeanTimeToMergePRDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubOpenCodeScanningAlertsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubOpenDependabotAlertsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubOpenPullRequestsByAccountDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubOpenSecretScanningAlertsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubWorkflowSuccessRateDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubWorkflowsCountDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.gitlab.GitlabFileExistsDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.gitlab.GitlabIsBranchProtectionSetDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.gitlab.GitlabMeanTimeToMergePRDsl;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.harnesscode.HarnessCodeFileExistsDsl;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class DataSourceLocationFactory {
  private GithubMeanTimeToMergePRDsl githubMeanTimeToMergePRDsl;
  private GithubIsBranchProtectionSetDsl githubIsBranchProtectionSetDsl;
  private GithubFileExistsDsl githubFileExistsDsl;
  private ScmProxyThroughDsl scmProxyThroughDsl;
  private GithubWorkflowsCountDsl githubWorkflowsCountDsl;
  private GithubWorkflowSuccessRateDsl githubWorkflowSuccessRateDsl;
  private GithubMeanTimeToCompleteWorkflowRunsDsl githubMeanTimeToCompleteWorkflowRunsDsl;
  private GithubMeanTimeToCompleteSuccessWorkflowRunsDsl githubMeanTimeToCompleteSuccessWorkflowRunsDsl;
  private GithubOpenDependabotAlertsDsl githubOpenDependabotAlertsDsl;
  private GithubOpenCodeScanningAlertsDsl githubOpenCodeScanningAlertsDsl;
  private GithubOpenSecretScanningAlertsDsl githubOpenSecretScanningAlertsDsl;
  private GithubOpenPullRequestsByAccountDsl githubOpenPullRequestsByAccountDsl;
  private BitbucketMeanTimeToMergePRDsl bitbucketMeanTimeToMergePRDsl;
  private BitbucketIsBranchProtectionSetDsl bitbucketIsBranchProtectionSetDsl;
  private GitlabMeanTimeToMergePRDsl gitlabMeanTimeToMergePRDsl;
  private GitlabFileExistsDsl gitlabFileExistsDsl;
  private GitlabIsBranchProtectionSetDsl gitlabIsBranchProtectionSetDsl;
  private HarnessProxyThroughDsl harnessProxyThroughDsl;
  private NoOpDsl noOpDsl;
  private CatalogSystemDsl catalogSystemDsl;
  private PagerDutyServiceDirectory pagerDutyServiceDirectory;
  private PagerDutyIncidents pagerDutyIncidents;
  private KubernetesProxyThroughDsl kubernetesProxyThroughDsl;
  private JiraMeanTimeToResolveDsl jiraMeanTimeToResolveDsl;
  private JiraIssuesCountDsl jiraIssuesCountDsl;
  private JiraIssuesOpenCloseRatioDsl jiraIssuesOpenCloseRatioDsl;
  private HarnessCodeFileExistsDsl harnessCodeFileExistsDsl;
  private HarnessSTOActiveVulnerabilitiesDsl harnessSTOActiveVulnerabilitiesDsl;

  public DataSourceLocation getDataSourceLocation(String identifier) {
    switch (identifier) {
      // Github
      case GITHUB_MEAN_TIME_TO_MERGE_PR:
        return githubMeanTimeToMergePRDsl;
      case GITHUB_IS_BRANCH_PROTECTION_SET:
        return githubIsBranchProtectionSetDsl;
      case GITHUB_FILE_EXISTS:
        return githubFileExistsDsl;
      case GITHUB_WORKFLOWS_COUNT:
        return githubWorkflowsCountDsl;
      case GITHUB_WORKFLOW_SUCCESS_RATE:
        return githubWorkflowSuccessRateDsl;
      case GITHUB_MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS:
        return githubMeanTimeToCompleteWorkflowRunsDsl;
      case GITHUB_MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS:
        return githubMeanTimeToCompleteSuccessWorkflowRunsDsl;
      case GITHUB_OPEN_DEPENDABOT_ALERTS:
        return githubOpenDependabotAlertsDsl;
      case GITHUB_OPEN_CODE_SCANNING_ALERTS:
        return githubOpenCodeScanningAlertsDsl;
      case GITHUB_OPEN_SECRET_SCANNING_ALERTS:
        return githubOpenSecretScanningAlertsDsl;
      case GITHUB_OPEN_PULL_REQUESTS_BY_ACCOUNT:
        return githubOpenPullRequestsByAccountDsl;

      // Bitbucket
      case BITBUCKET_MEAN_TIME_TO_MERGE_PR:
        return bitbucketMeanTimeToMergePRDsl;
      case BITBUCKET_IS_BRANCH_PROTECTION_SET:
        return bitbucketIsBranchProtectionSetDsl;

      // Gitlab
      case GITLAB_MEAN_TIME_TO_MERGE_PR:
        return gitlabMeanTimeToMergePRDsl;
      case GITLAB_IS_BRANCH_PROTECTION_SET:
        return gitlabIsBranchProtectionSetDsl;
      case GITLAB_FILE_EXISTS:
        return gitlabFileExistsDsl;

      // Harness code
      case HARNESS_CODE_FILE_EXISTS:
        return harnessCodeFileExistsDsl;

        // SCM Commons
      case GITHUB_FILE_CONTENTS:
      case GITHUB_FILE_CONTAINS:
      case BITBUCKET_FILE_CONTAINS:
      case BITBUCKET_FILE_CONTENTS:
      case BITBUCKET_FILE_EXISTS:
      case GITLAB_FILE_CONTAINS:
      case GITLAB_FILE_CONTENTS:
      case HARNESS_CODE_FILE_CONTAINS:
      case HARNESS_CODE_FILE_CONTENTS:
        return scmProxyThroughDsl;

      // Harness
      case HARNESS_STO_SCAN_SETUP_DSL:
      case HARNESS_POLICY_EVALUATION_DSL:
      case HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS:
      case HARNESS_TEST_PASSING_ON_CI_IS_ZERO:
        return harnessProxyThroughDsl;
      case HARNESS_STO_ACTIVE_VULNERABILITIES:
        return harnessSTOActiveVulnerabilitiesDsl;

      // Catalog
      case CATALOG:
        return noOpDsl;
      case CATALOG_SYSTEM:
        return catalogSystemDsl;

      // Pagerduty
      case PAGERDUTY_SERVICE_DIRECTORY:
        return pagerDutyServiceDirectory;
      case PAGERDUTY_INCIDENTS:
      case PAGERDUTY_RESOLVED_INCIDENTS:
        return pagerDutyIncidents;

      // Kubernetes
      case KUBERNETES:
        return kubernetesProxyThroughDsl;

      // Jira
      case JIRA_MEAN_TIME_TO_RESOLVE:
        return jiraMeanTimeToResolveDsl;
      case JIRA_ISSUES_COUNT:
        return jiraIssuesCountDsl;
      case JIRA_ISSUES_OPEN_CLOSE_RATIO:
        return jiraIssuesOpenCloseRatioDsl;

      default:
        throw new UnsupportedOperationException(String.format("Could not find DataSource Location for %s", identifier));
    }
  }
}
