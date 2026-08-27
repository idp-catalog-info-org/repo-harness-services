/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class DataPoints {
  // Github, Bitbucket, Gitlab
  public static final String PULL_REQUEST_MEAN_TIME_TO_MERGE = "meanTimeToMerge";
  public static final String IS_BRANCH_PROTECTED = "isBranchProtected";
  public static final String IS_FILE_EXISTS = "isFileExists";
  public static final String EXTRACT_STRING_FROM_A_FILE = "extractStringFromAFile";
  public static final String MATCH_STRING_IN_A_FILE = "matchStringInAFile";
  public static final String WORKFLOWS_COUNT = "workflowsCount";
  public static final String WORKFLOW_SUCCESS_RATE = "workflowSuccessRate";
  public static final String MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS = "meanTimeToCompleteWorkflowRuns";
  public static final String MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS = "meanTimeToCompleteSuccessWorkflowRuns";
  public static final String OPEN_DEPENDABOT_ALERTS = "openDependabotAlerts";
  public static final String OPEN_CODE_SCANNING_ALERTS = "openCodeScanningAlerts";
  public static final String OPEN_SECRET_SCANNING_ALERTS = "openSecretScanningAlerts";
  public static final String OPEN_PULL_REQUESTS_BY_ACCOUNT = "openPullRequestsByAccount";
  public static final String GITHUB_REPOSITORY_ACCESS_ERROR = "repository doesn't exist or is not accessible";
  public static final String NO_PULL_REQUESTS_FOUND = "No pull requests found for branch: %s";
  public static final String SOURCE_LOCATION_ANNOTATION_ERROR =
      "Invalid or missing source-location annotation in the catalog info YAML";
  public static final String INVALID_DATA_SOURCE = "Invalid data source provided";
  public static final String INVALID_BRANCH_NAME_ERROR = "Invalid branch name provided";
  public static final String INVALID_PATTERN = "Invalid pattern provided";
  public static final String INVALID_CONNECTOR_CONFIGURATION =
      "Invalid connector configuration or unsupported connector type for scorecards";

  // Catalog
  public static final String CATALOG_TECH_DOCS = "techDocsAnnotationExists";
  public static final String CATALOG_PAGERDUTY = "pagerdutyAnnotationExists";
  public static final String CATALOG_SPEC_OWNER = "specOwnerExists";
  public static final String CATALOG_EVALUATE_EXPR = "evaluateExpression";
  public static final String CATALOG_ANNOTATION_EXISTS = "annotationExists";
  public static final String CATALOG_SYSTEM_IS_DEFINED_AND_IT_EXISTS = "systemIsDefinedAndItExists";
  public static final String SYSTEM_NOT_DEFINED =
      "System (spec.system field) is not defined in the Catalog Entity definition YAML";
  public static final String SYSTEM_DOES_NOT_EXISTS =
      "Defined system ${system} does not exist in the Catalog. Please add the System as a new entity. Read "
      + "https://developer.harness.io/docs/internal-developer-portal/catalog/yaml-file#kind-system";
  public static final String ENTITY_INCORRECT_KIND =
      "The Kind ${kind} of the given entity is not valid for systemIsDefinedAndExists check";
  public static final String CATALOG_AI_ASSET_SOURCE_FILE_PATTERN_MATCH = "aiAssetSourceFilePatternMatch";
  public static final String CATALOG_AI_ASSET_ID_PREFIX_MATCH = "aiAssetIdPrefixMatch";
  public static final String CATALOG_AI_ASSET_PROVIDER_EXISTS = "aiAssetProviderExists";
  public static final String CATALOG_AI_ASSET_DISCOVERED_AT = "aiAssetDiscoveredAt";

  // Harness
  public static final String STO_ADDED_IN_PIPELINE = "stoStageAdded";
  public static final String IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE = "isPolicyEvaluationSuccessful";
  public static final String PERCENTAGE_OF_CI_PIPELINE_FAILING_IN_SEVEN_DAYS =
      "PercentageOfCIPipelinePassingInPastSevenDays";
  public static final String PIPELINE_TEST_FAILING_IN_CI_IS_ZERO = "noTestsFailingInCiPipeline";
  public static final String STO_ACTIVE_VULNERABILITIES = "stoActiveVulnerabilities";

  // PagerDuty
  public static final String IS_ON_CALL_SET = "isOnCallSet";
  public static final String IS_ESCALATION_POLICY_SET = "isEscalationPolicySet";
  public static final String NO_OF_INCIDENTS_IN_LAST_THIRTY_DAYS = "noOfIncidentsInLastThirtyDays";
  public static final String AVG_RESOLVED_TIME_FOR_LAST_TEN_RESOLVED_INCIDENTS_IN_MINUTES =
      "avgResolvedTimeForLastTenResolvedIncidentsInMinutes";

  // Kubernetes
  public static final String REPLICAS = "replicas";
  public static final String DAYS_SINCE_LAST_DEPLOYED = "daysSinceLastDeployed";
  public static final String PROJECT_KEY_ANNOTATION_ERROR =
      "Invalid or missing jira/project-key annotation in the catalog info YAML";

  // Jira
  public static final String K8_LABEL_SELECTOR_ANNOTATION_ERROR =
      "Invalid or missing backstage.io/kubernetes-label-selector annotation in the catalog info YAML";
  public static final String MEAN_TIME_TO_RESOLVE = "meanTimeToResolve";
  public static final String ISSUES_COUNT = "issuesCount";
  public static final String ISSUES_OPEN_CLOSE_RATIO = "issuesOpenCloseRatio";
  public static final String NO_ISSUES_FOUND = "No issues found";

  // Traceable
  public static final String TRACEABLE_AVG_RISK_SCORE = "traceableAvgRiskScore";
  public static final String TRACEABLE_TOT_ISSUES = "traceableTotIssues";
  public static final String TRACEABLE_MAX_ISSUES = "traceableMaxIssues";
  public static final String TRACEABLE_MIN_ISSUES = "traceableMinIssues";
  public static final String TRACEABLE_OWASP_ENDPOINT_COUNT = "traceableOwaspEndpointCount";

  // Catalog integration properties
  public static final String DESCRIPTION = "description";
  public static final String MONITOR_COUNT = "monitorCount";
  public static final String MONITORS_SUMMARY_COUNT_RED = "monitorsSummaryCountRed";
  public static final String CONTACTS_SIZE = "contactsSize";
  public static final String DOCS_SIZE = "docsSize";
  public static final String GITHUB_HTML_URL = "githubHtmlUrl";
  public static final String SLO_COUNT = "sloCount";
  public static final String PROBLEM_COUNT = "problemCount";
  public static final String INTEGRATION_URL = "integrationUrl";
  public static final String PRIMARY_LANGUAGE_NAME = "primaryLanguageName";
  public static final String HAS_AGENTS_FILE = "hasAgentsFile";
  public static final String LATEST_RELEASE_PUBLISHED_AT = "latestReleasePublishedAt";
  public static final String DEFAULT_BRANCH = "defaultBranch";
  public static final String PROJECT_KEY = "projectKey";
  public static final String IS_PRIVATE = "isPrivate";

  // SonarQube
  public static final String QUALITY_GATE_STATUS = "qualityGateStatus";
  public static final String MEASURES_RELIABILITY_RATING = "measuresReliabilityRating";
  public static final String MEASURES_SECURITY_RATING = "measuresSecurityRating";
  public static final String MEASURES_SQALE_RATING = "measuresSqaleRating";
  public static final String MEASURES_LINE_COVERAGE = "measuresLineCoverage";
  public static final String MEASURES_BRANCH_COVERAGE = "measuresBranchCoverage";
  public static final String MEASURES_DUPLICATED_LINES_DENSITY = "measuresDuplicatedLinesDensity";
  public static final String MEASURES_BUGS = "measuresBugs";
  public static final String MEASURES_VULNERABILITIES = "measuresVulnerabilities";
  public static final String MEASURES_SECURITY_HOTSPOTS = "measuresSecurityHotspots";
  public static final String MEASURES_CODE_SMELLS = "measuresCodeSmells";
  public static final String MEASURES_NCLOC = "measuresNcloc";

  // PagerDuty Catalog
  public static final String IDENTIFIER = "identifier";
  public static final String NAME = "name";
  public static final String STATUS = "status";
  public static final String ANALYTICS_MEAN_SECONDS_TO_FIRST_ACK = "analyticsMeanSecondsToFirstAck";
  public static final String ANALYTICS_MEAN_SECONDS_TO_RESOLVE = "analyticsMeanSecondsToResolve";
  public static final String ANALYTICS_TOTAL_INCIDENTS = "analyticsTotalIncidents";
  public static final String DEFAULT_ROLE = "defaultRole";

  // Harness CD
  public static final String DEPLOYMENT_FREQUENCY_PER_SPRINT = "deploymentFrequencyPerSprint";
  public static final String CHANGE_FAILURE_RATE_PERCENT = "changeFailureRatePercent";
  public static final String AVERAGE_DEPLOYMENT_DURATION_SECONDS = "averageDeploymentDurationSeconds";

  // GCP
  public static final String ASSET_TYPE = "assetType";
  public static final String DISPLAY_NAME = "displayName";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String STATE = "state";
  public static final String LOCATION = "location";
  public static final String ORGANIZATION = "organization";
  public static final String CREATE_TIME = "createTime";
  public static final String PROJECT = "project";

  // Commons
  public static final String PLUGIN_DISABLED = "Unable to get plugin details, probably plugin is not enabled";
  public static final String INVALID_CONDITIONAL_INPUT = "Invalid conditional input";
  public static final String INVALID_FILE_NAME_ERROR = "Invalid file name provided";
  public static final String INVALID_FILE_PATH_ERROR = "Invalid file path provided";
  public static final String INVALID_EXPRESSION = "Invalid or missing expression %s";
  public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded";
}
