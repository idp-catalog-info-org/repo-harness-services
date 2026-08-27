/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class DataSourceLocations {
  // Github
  public static final String GITHUB_MEAN_TIME_TO_MERGE_PR = "github_mean_time_to_merge_pr";
  public static final String GITHUB_IS_BRANCH_PROTECTION_SET = "github_is_branch_protection_set";
  public static final String GITHUB_FILE_EXISTS = "github_is_file_exists";
  public static final String GITHUB_FILE_CONTENTS = "github_file_contents";
  public static final String GITHUB_FILE_CONTAINS = "github_file_contains";
  public static final String GITHUB_WORKFLOWS_COUNT = "github_workflows_count";
  public static final String GITHUB_WORKFLOW_SUCCESS_RATE = "github_workflow_success_rate";
  public static final String GITHUB_MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS = "github_mean_time_to_complete_workflow_runs";
  public static final String GITHUB_MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS =
      "github_mean_time_to_complete_success_workflow_runs";
  public static final String GITHUB_OPEN_DEPENDABOT_ALERTS = "github_open_dependabot_alerts";
  public static final String GITHUB_OPEN_CODE_SCANNING_ALERTS = "github_open_code_scanning_alerts";
  public static final String GITHUB_OPEN_SECRET_SCANNING_ALERTS = "github_open_secret_scanning_alerts";
  public static final String GITHUB_OPEN_PULL_REQUESTS_BY_ACCOUNT = "github_open_pull_requests_by_account";

  // Harness
  public static final String HARNESS_STO_SCAN_SETUP_DSL = "harness_sto_scan_dsl";
  public static final String HARNESS_POLICY_EVALUATION_DSL = "harness_policy_evaluation_dsl";
  public static final String HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS = "harness_ci_success_percent_in_seven_days";
  public static final String HARNESS_TEST_PASSING_ON_CI_IS_ZERO = "harness_test_passing_on_ci_is_zero";
  public static final String HARNESS_STO_ACTIVE_VULNERABILITIES = "harness_sto_active_vulnerabilities";
  public static final String PAGERDUTY_INCIDENTS = "pagerduty_incidents";
  public static final String PAGERDUTY_RESOLVED_INCIDENTS = "pagerduty_resolved_incidents";
  public static final String PAGERDUTY_SERVICE_DIRECTORY = "pagerduty_service_directory";

  // Bitbucket
  public static final String BITBUCKET_MEAN_TIME_TO_MERGE_PR = "bitbucket_mean_time_to_merge_pr";
  public static final String BITBUCKET_IS_BRANCH_PROTECTION_SET = "bitbucket_is_branch_protection_set";
  public static final String BITBUCKET_FILE_CONTENTS = "bitbucket_file_contents";
  public static final String BITBUCKET_FILE_CONTAINS = "bitbucket_file_contains";
  public static final String BITBUCKET_FILE_EXISTS = "bitbucket_is_file_exists";

  // Gitlab
  public static final String GITLAB_MEAN_TIME_TO_MERGE_PR = "gitlab_mean_time_to_merge_pr";
  public static final String GITLAB_IS_BRANCH_PROTECTION_SET = "gitlab_is_branch_protection_set";
  public static final String GITLAB_FILE_EXISTS = "gitlab_is_file_exists";
  public static final String GITLAB_FILE_CONTENTS = "gitlab_file_contents";
  public static final String GITLAB_FILE_CONTAINS = "gitlab_file_contains";

  // Harness Code
  public static final String HARNESS_CODE_FILE_CONTENTS = "harness_code_file_contents";
  public static final String HARNESS_CODE_FILE_CONTAINS = "harness_code_file_contains";
  public static final String HARNESS_CODE_FILE_EXISTS = "harness_code_is_file_exists";

  // Commons
  public static final String API_BASE_URL = "{API_BASE_URL}";
  public static final String REPO_SCM = "{REPO_SCM}";
  public static final String REPOSITORY_OWNER = "{REPOSITORY_OWNER}";
  public static final String REPOSITORY_NAME = "{REPOSITORY_NAME}";
  public static final String ACCOUNT_IDENTIFIER = "{ACCOUNT_IDENTIFIER}";
  public static final String ORG_IDENTIFIER = "{ORG_IDENTIFIER}";
  public static final String PROJECT_IDENTIFIER = "{PROJECT_IDENTIFIER}";
  public static final String REPOSITORY_BRANCH = "{REPOSITORY_BRANCH}";
  public static final String REPOSITORY_SUB_FOLDER = "{REPOSITORY_SUB_FOLDER}";
  public static final String COMPLETE_REPO_NAME = "{COMPLETE_REPO_NAME}";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BODY = "{BODY}";
  public static final String HTTPS_PREFIX = "https://";

  // Catalog
  public static final String CATALOG = "catalog";
  public static final String CATALOG_SYSTEM = "catalogSystem";

  // PagerDuty
  public static final String PAGERDUTY_SERVICE_ID = "{SERVICE_ID}";
  public static final String PAGERDUTY_TARGET_URL = "{TARGET_URL}";
  public static final String PAGERDUTY_ANNOTATION_MISSING_ERROR =
      "PagerDuty annotation is missing in the catalog info YAML";
  public static final String PAGERDUTY_PLUGIN_INVALID_TOKEN_ERROR_MESSAGE =
      "PagerDuty token added in plugin is invalid";
  public static final String PAGERDUTY_PLUGIN_INVALID_URL_ERROR_MESSAGE =
      "Unable to get the PagerDuty data, probably target url provided in plugin is invalid";
  public static final String PAGERDUTY_UNABLE_TO_FETCH_DATA_ERROR_MESSAGE = "Unable to fetch the data from PagerDuty";

  // Jira
  public static final String PROJECT_COMPONENT_REPLACER = "{PROJECT_COMPONENT_REPLACER}";
  public static final String JIRA_MEAN_TIME_TO_RESOLVE = "jira_mean_time_to_resolve";
  public static final String JIRA_ISSUES_COUNT = "jira_issues_count";
  public static final String JIRA_ISSUES_OPEN_CLOSE_RATIO = "jira_issues_open_close_ratio";

  // Kubernetes
  public static final String KUBERNETES = "kubernetes";

  // Traceable
  public static final String HQL_TRACEABLE_RISK_SCORE = "hql_traceable_risk_score";
  public static final String HQL_TRACEABLE_TOT_ISSUES = "hql_traceable_tot_issues";
  public static final String HQL_TRACEABLE_MAX_ISSUES = "hql_traceable_max_issues";
  public static final String HQL_TRACEABLE_MIN_ISSUES = "hql_traceable_min_issues";
  public static final String HQL_TRACEABLE_OWASP_ENDPOINT_COUNT = "hql_traceable_owasp_endpoint_count";

  // Datadog
  public static final String DATADOG_DESCRIPTION = "datadog_description";
  public static final String DATADOG_MONITOR_COUNT = "datadog_monitor_count";
  public static final String DATADOG_MONITORS_SUMMARY_COUNT_RED = "datadog_monitors_summary_count_red";
  public static final String DATADOG_CONTACTS_SIZE = "datadog_contacts_size";
  public static final String DATADOG_DOCS_SIZE = "datadog_docs_size";
  public static final String DATADOG_GITHUB_HTML_URL = "datadog_github_html_url";
  public static final String DATADOG_GITHUB_HTML_URL_PRESENT = "datadog_github_html_url_present";

  // Dynatrace
  public static final String DYNATRACE_MONITOR_COUNT = "dynatrace_monitor_count";
  public static final String DYNATRACE_SLO_COUNT = "dynatrace_slo_count";
  public static final String DYNATRACE_PROBLEM_COUNT = "dynatrace_problem_count";
  public static final String DYNATRACE_MONITOR_COUNT_GT_0 = "dynatrace_monitor_count_gt_0";
  public static final String DYNATRACE_SLO_COUNT_GT_0 = "dynatrace_slo_count_gt_0";
  public static final String DYNATRACE_PROBLEM_COUNT_EQ_0 = "dynatrace_problem_count_eq_0";

  // Github Catalog
  public static final String GITHUB_INTEGRATION_URL = "github_integration_url";
  public static final String GITHUB_PRIMARY_LANGUAGE_NAME = "github_primary_language_name";
  public static final String GITHUB_HAS_AGENTS_FILE = "github_has_agents_file";
  public static final String GITHUB_LATEST_RELEASE_PUBLISHED_AT = "github_latest_release_published_at";
  public static final String GITHUB_LATEST_RELEASE_PUBLISHED_AT_PRESENT = "github_latest_release_published_at_present";
  public static final String GITHUB_PRIMARY_LANGUAGE_NAME_PRESENT = "github_primary_language_name_present";
  public static final String GITHUB_HAS_AGENTS_FILE_IS_TRUE = "github_has_agents_file_is_true";

  // Bitbucket Catalog
  public static final String BITBUCKET_DESCRIPTION = "bitbucket_description";
  public static final String BITBUCKET_DEFAULT_BRANCH = "bitbucket_default_branch";
  public static final String BITBUCKET_PROJECT_KEY = "bitbucket_project_key";
  public static final String BITBUCKET_IS_PRIVATE = "bitbucket_is_private";
  public static final String BITBUCKET_DESCRIPTION_PRESENT = "bitbucket_description_present";
  public static final String BITBUCKET_DEFAULT_BRANCH_EQ_MAIN = "bitbucket_default_branch_eq_main";
  public static final String BITBUCKET_PROJECT_KEY_PRESENT = "bitbucket_project_key_present";
  public static final String BITBUCKET_IS_PRIVATE_IS_TRUE = "bitbucket_is_private_is_true";

  // SonarQube
  public static final String SONARQUBE_QUALITY_GATE_STATUS = "sonarqube_quality_gate_status";
  public static final String SONARQUBE_MEASURES_RELIABILITY_RATING = "sonarqube_measures_reliability_rating";
  public static final String SONARQUBE_MEASURES_SECURITY_RATING = "sonarqube_measures_security_rating";
  public static final String SONARQUBE_MEASURES_SQALE_RATING = "sonarqube_measures_sqale_rating";
  public static final String SONARQUBE_MEASURES_LINE_COVERAGE = "sonarqube_measures_line_coverage";
  public static final String SONARQUBE_MEASURES_BRANCH_COVERAGE = "sonarqube_measures_branch_coverage";
  public static final String SONARQUBE_MEASURES_DUPLICATED_LINES_DENSITY =
      "sonarqube_measures_duplicated_lines_density";
  public static final String SONARQUBE_MEASURES_BUGS = "sonarqube_measures_bugs";
  public static final String SONARQUBE_MEASURES_VULNERABILITIES = "sonarqube_measures_vulnerabilities";
  public static final String SONARQUBE_MEASURES_SECURITY_HOTSPOTS = "sonarqube_measures_security_hotspots";
  public static final String SONARQUBE_MEASURES_CODE_SMELLS = "sonarqube_measures_code_smells";
  public static final String SONARQUBE_MEASURES_NCLOC = "sonarqube_measures_ncloc";

  // PagerDuty Catalog
  public static final String PAGERDUTY_IDENTIFIER = "pagerduty_identifier";
  public static final String PAGERDUTY_NAME = "pagerduty_name";
  public static final String PAGERDUTY_STATUS = "pagerduty_status";
  public static final String PAGERDUTY_ON_CALL_NAME = "pagerduty_on_call_name";
  public static final String PAGERDUTY_ANALYTICS_MEAN_SECONDS_TO_FIRST_ACK =
      "pagerduty_analytics_mean_seconds_to_first_ack";
  public static final String PAGERDUTY_ANALYTICS_MEAN_SECONDS_TO_RESOLVE =
      "pagerduty_analytics_mean_seconds_to_resolve";
  public static final String PAGERDUTY_ANALYTICS_TOTAL_INCIDENTS = "pagerduty_analytics_total_incidents";
  public static final String PAGERDUTY_DEFAULT_ROLE = "pagerduty_default_role";

  // Harness CD
  public static final String HARNESS_CD_DEPLOYMENT_FREQUENCY_PER_SPRINT = "harness_cd_deployment_frequency_per_sprint";
  public static final String HARNESS_CD_CHANGE_FAILURE_RATE_PERCENT = "harness_cd_change_failure_rate_percent";
  public static final String HARNESS_CD_AVERAGE_DEPLOYMENT_DURATION_SECONDS =
      "harness_cd_average_deployment_duration_seconds";

  // GCP
  public static final String GCP_ASSET_TYPE = "gcp_asset_type";
  public static final String GCP_DISPLAY_NAME = "gcp_display_name";
  public static final String GCP_RESOURCE_NAME = "gcp_resource_name";
  public static final String GCP_STATE = "gcp_state";
  public static final String GCP_LOCATION = "gcp_location";
  public static final String GCP_ORGANIZATION = "gcp_organization";
  public static final String GCP_CREATE_TIME = "gcp_create_time";
  public static final String GCP_PROJECT = "gcp_project";
}
