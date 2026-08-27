/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.IDP)
public class Constants {
  private Constants() {}

  public static final String IDP_PREFIX = "idp_";
  public static final String ENDPOINTS_PROPERTY = "endpoints";
  public static final String HEADERS_PROPERTY = "headers";
  public static final String TARGET_PROPERTY = "target";
  public static final String AUTHORIZATION_PROPERTY = "Authorization";
  public static final String IDP_PLUGIN_ORIGIN_HEADER = "idp-plugin-origin";

  // Plugin identifiers
  public static final String CIRCLE_CI_PLUGIN = "circleci";
  public static final String CONFLUENCE_PLUGIN = "confluence";
  public static final String DATADOG_PLUGIN = "datadog";
  public static final String FIRE_HYDRANT_PLUGIN = "firehydrant";
  public static final String GITHUB_ACTIONS_PLUGIN = "github-actions";
  public static final String DX_PLUGIN = "dx";
  public static final String FME_PLUGIN = "fme";
  public static final String GITHUB_CATALOG_DISCOVERY_PLUGIN = "github-catalog-discovery";
  public static final String GITHUB_COPILOT_PLUGIN = "github-copilot";
  public static final String GITHUB_INSIGHTS_PLUGIN = "github-insights";
  public static final String GITHUB_PULL_REQUESTS_PLUGIN = "github-pull-requests";
  public static final String GRAFANA_PLUGIN = "grafana";
  public static final String HARNESS_CI_CD_PLUGIN = "harness-ci-cd";
  public static final String HARNESS_FEATURE_FLAGS_PLUGIN = "harness-feature-flags";
  public static final String JENKINS_PLUGIN = "jenkins";
  public static final String JIRA_PLUGIN = "jira";
  public static final String KAFKA_PLUGIN = "kafka";
  public static final String KUBERNETES_PLUGIN = "kubernetes";
  public static final String LIGHTHOUSE_PLUGIN = "lighthouse";
  public static final String PAGER_DUTY_PLUGIN = "pager-duty";
  public static final String SYNK_SECURITY_PLUGIN = "snyk-security";
  public static final String SONARQUBE_PLUGIN = "sonarqube";
  public static final String TODO_PLUGIN = "todo";
  public static final String OPSGENIE_PLUGIN = "opsgenie";
  public static final String AZURE_DEVOPS_PLUGIN = "azure-devops";
  public static final String HARNESS_SRM_PLUGIN = "harness-srm";
  public static final String CUSTOM_PLUGIN_FILE_NAME = "custom-plugin.yaml";
  public static final String DYNATRACE_PLUGIN = "dynatrace";
  public static final String ROOTLY_PLUGIN = "rootly";
  public static final String CUSTOM_PLUGIN = "my_custom_plugin";
  public static final String RAFAY_PLUGIN = "rafay";
  public static final String GITHUB_CODESPACES = "github-codespaces";
  public static final String SPLUNK_ONCALL_PLUGIN = "splunk-on-call";
  public static final String ADR_PLUGIN = "adr";
  public static final String BUGSNAG_PLUGIN = "bugsnag";
  public static final String NEW_RELIC_PLUGIN = "new-relic";
  public static final String HARNESS_IACM = "harness-iacm";
  public static final String HARNESS_PROXY = "harness-proxy";
  public static final String SCAFFOLDER_ACTION = "scaffolder-action";
  public static final String HARNESS_CHAOS_PLUGIN = "harness-chaos";
  public static final String JFROG_ARTIFACTORY_IMAGE_PLUGIN = "jfrog-artifactory";
  public static final String JFROG_ARTIFACTORY_ARTIFACT_PLUGIN = "jfrog-artifactory-libs";
  public static final String BUILDKITE_PLUGIN = "buildkite";
  public static final String ARGO_CD_PLUGIN = "argo-cd";
  public static final String SYSDIG_PLUGIN = "sysdig";
  public static final String HARNESS_CCM_PLUGIN = "harness-ccm";
  public static final String WIZ_PLUGIN = "wiz";
  public static final String VEE_CODE_KONG_SERVICE_MANAGER_PLUGIN = "vee-code-kong";
  public static final String GITHUB_TOKEN = "HARNESS_GITHUB_TOKEN";
  public static final String GITHUB_APP_ID = "HARNESS_GITHUB_APP_APPLICATION_ID";
  public static final String GITHUB_INSTALLATION_ID = "HARNESS_GITHUB_APP_INSTALLATION_ID";
  public static final String GITHUB_APP_PRIVATE_KEY_REF = "HARNESS_GITHUB_APP_PRIVATE_KEY_REF";
  public static final String PRIVATE_KEY_START = "-----BEGIN PRIVATE KEY-----";
  public static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
  public static final String GITLAB_TOKEN = "HARNESS_GITLAB_TOKEN";
  public static final String BITBUCKET_USERNAME = "HARNESS_BITBUCKET_USERNAME";
  public static final String BITBUCKET_USERNAME_API_ACCESS = "HARNESS_BITBUCKET_API_ACCESS_USERNAME";
  public static final String BITBUCKET_TOKEN = "HARNESS_BITBUCKET_TOKEN";
  public static final String BITBUCKET_API_ACCESS_TOKEN = "HARNESS_BITBUCKET_API_ACCESS_TOKEN";
  public static final String AZURE_REPO_TOKEN = "HARNESS_AZURE_REPO_TOKEN";
  public static final String BACKEND_SECRET = "BACKEND_SECRET";
  public static final String IDP_BACKEND_SECRET = "IDP_BACKEND_SECRET";
  public static final String PROXY_ENV_NAME = "HOST_PROXY_MAP";
  public static final String GITHUB_AUTH = "github-auth";
  public static final String GITHUB_AUTH_NAME = "GitHub Auth";
  public static final String GOOGLE_AUTH = "google-auth";
  public static final String GHE_HOST = "gheHost";
  public static final String GOOGLE_AUTH_NAME = "Google Auth";
  public static final String ATLASSIAN_AUTH = "atlassian-auth";
  public static final String ATLASSIAN_AUTH_NAME = "Atlassian Auth";
  public static final String AUTH_GITHUB_CLIENT_ID = "AUTH_GITHUB_CLIENT_ID";
  public static final String AUTH_GITHUB_CLIENT_SECRET = "AUTH_GITHUB_CLIENT_SECRET";
  public static final String AUTH_GITHUB_ENTERPRISE_INSTANCE_URL = "AUTH_GITHUB_ENTERPRISE_INSTANCE_URL";
  public static final List<String> GITHUB_AUTH_ENV_VARIABLES =
      new ArrayList<>(List.of(AUTH_GITHUB_CLIENT_ID, AUTH_GITHUB_CLIENT_SECRET, AUTH_GITHUB_ENTERPRISE_INSTANCE_URL));

  public static final String AUTH_GOOGLE_CLIENT_ID = "AUTH_GOOGLE_CLIENT_ID";
  public static final String AUTH_GOOGLE_CLIENT_SECRET = "AUTH_GOOGLE_CLIENT_SECRET";
  public static final List<String> GOOGLE_AUTH_ENV_VARIABLES =
      new ArrayList<>(List.of(AUTH_GOOGLE_CLIENT_ID, AUTH_GOOGLE_CLIENT_SECRET));
  public static final String AUTH_ATLASSIAN_CLIENT_ID = "AUTH_ATLASSIAN_CLIENT_ID";
  public static final String AUTH_ATLASSIAN_CLIENT_SECRET = "AUTH_ATLASSIAN_CLIENT_SECRET";
  public static final List<String> ATLASSIAN_AUTH_ENV_VARIABLES =
      new ArrayList<>(List.of(AUTH_ATLASSIAN_CLIENT_ID, AUTH_ATLASSIAN_CLIENT_SECRET));

  public static final String AZURE_REPO = "AzureRepo";
  public static final String BITBUCKET = "Bitbucket";
  public static final String BITBUCKET_CLOUD = "BitbucketCloud";
  public static final String BITBUCKET_SERVER = "BitbucketServer";
  public static final String GITHUB = "Github";
  public static final String GITLAB = "Gitlab";
  public static final String HARNESS = "Harness";

  public static final String SLASH_DELIMITER = "/";
  public static final String SOURCE_FORMAT_BLOB = "blob";
  public static final String SOURCE_FORMAT_TREE = "tree";
  public static final String SOURCE_FORMAT_SRC = "src";
  public static final String LAST_UPDATED_TIMESTAMP = "LAST_UPDATED_TIMESTAMP";
  public static final String PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK = "pluginRequestsNotificationSlack";
  public static final String CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK_WEBHOOK =
      "catalogEntitiesVerificationNotificationSlack";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  public static final String GLOBAL_DELEGATE_ACCOUNT_ID = "__GLOBAL_DELEGATE_ACCOUNT_ID__";
  public static final String GENERIC_DATA = "GENERIC_DATA";
  public static final String DOT_SEPARATOR = ".";
  public static final String COMMA_SEPARATOR = ",";
  public static final String SPACE_SEPARATOR = " ";
  public static final String COLON_SEPARATOR = ":";
  public static final String SUCCESS_RESPONSE = "SUCCESS";

  public static final String HARNESS_IDENTIFIER = "harness";
  public static final String GITHUB_IDENTIFIER = "github";
  public static final String GITLAB_IDENTIFIER = "gitlab";
  public static final String BITBUCKET_IDENTIFIER = "bitbucket";
  public static final String CATALOG_IDENTIFIER = "catalog";
  public static final String KUBERNETES_IDENTIFIER = "kubernetes";
  public static final String CUSTOM_IDENTIFIER = "custom";
  public static final String PAGERDUTY_IDENTIFIER = "pagerduty";
  public static final String JIRA_IDENTIFIER = "jira";
  public static final String TRACEABLE_IDENTIFIER = "traceable";
  public static final String DATADOG_IDENTIFIER = "datadog";
  public static final String DYNATRACE_IDENTIFIER = "dynatrace";
  public static final String GCP_IDENTIFIER = "gcp";
  public static final String HARNESS_CD_IDENTIFIER = "harness_cd";
  public static final String SONAR_IDENTIFIER = "sonarqube";
  public static final String DSL_RESPONSE = "dsl_response";
  public static final String DATA_POINT_VALUE_KEY = "value";
  public static final String ERROR_MESSAGE_KEY = "error_messages";
  public static final String ERROR_MESSAGES_KEY = "errorMessages";
  public static final String ERRORS = "errors";
  public static final String MESSAGE_KEY = "message";
  public static final String TEXT = "text";
  public static final String MISSING_DATA = "Missing Data";
  public static final String HARNESS_HOST = "https://%s.harness.io";

  public static final String LOCAL_HOST = "http://localhost:12003";
  public static final String GITLAB_PLUGIN = "gitlab";
  public static final String QA_ENV = "qa";
  public static final String PRE_QA_ENV = "stress";

  public static final String LOCAL_ENV = "local";

  public static final String COMPLIANCE_ENV = "compliance";
  public static final String DEFAULT = "default";
  public static final String DEFAULT_BRANCH_KEY = "refs/";
  public static final String DEFAULT_BRANCH_KEY_ESCAPED = "\"refs/\"";

  public static final String KUBERNETES = "kubernetes";
  public static final String HARNESS_ACCOUNT = "Harness-Account";

  public static final String ACCOUNT_SCOPED = "account.";
  public static final String ORG_SCOPED = "org.";
  public final static Pattern EXPRESSION_PATTERN = Pattern.compile("<\\+(.*?)>");
  public final static String IDPStageStepPMSType = "IDPStage";

  public static final String INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN = "AZURE_PERSONAL_ACCESS_TOKEN";
  public static final String INTEGRATIONS_BITBUCKET_CLOUD_USERNAME = "BITBUCKET_CLOUD_USERNAME";
  public static final String INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD = "BITBUCKET_CLOUD_PASSWORD";
  public static final String INTEGRATIONS_BITBUCKET_SERVER_USERNAME = "BITBUCKET_SERVER_USERNAME";
  public static final String INTEGRATIONS_BITBUCKET_SERVER_PASSWORD = "BITBUCKET_SERVER_PASSWORD";
  public static final String INTEGRATIONS_GITHUB_TOKEN = "GITHUB_TOKEN";
  public static final String INTEGRATIONS_GITHUB_APP_APPLICATION_ID = "GITHUB_APP_APPLICATION_ID";
  public static final String INTEGRATIONS_GITHUB_APP_INSTALLATION_ID = "GITHUB_APP_INSTALLATION_ID";
  public static final String INTEGRATIONS_GITHUB_APP_PRIVATE_KEY = "GITHUB_APP_PRIVATE_KEY";
  public static final String INTEGRATIONS_GITLAB_TOKEN = "GITLAB_TOKEN";
  public static final String INTEGRATIONS_HARNESS_CODE_REPO_TOKEN = "HARNESS_CODE_REPO_TOKEN";
  public static final Set<String> BACKSTAGE_KINDS = new HashSet<>(
      Set.of("API", "Component", "Domain", "Group", "Location", "Resource", "System", "Template", "User"));
  public static final String SMP_DEPLOYMENT_TYPE = "SMP";
  public static final String PERMISSIONS = "PERMISSIONS";
  public static final String USERGROUP = "USERGROUP";
  public static final String HARNESS_API_KEY = "HARNESS_API_KEY";
  public static final String HARNESS_ACCOUNT_ID = "HARNESS_ACCOUNT_ID";
  public static final String HARNESS_TOKEN = "HARNESS_TOKEN";
  public static final String IDP_ENCRYPTION_SECRET_ENV = "IDP_ENCRYPTION_SECRET";
  public static final String IDP_SERVICE_SECRET_ENV = "IDP_SERVICE_SECRET";
  public static final String IDP_SERVICE_BASE_URL_ENV = "IDP_SERVICE_BASE_URL";
  public static final String CODE_SERVICE_SECRET_ENV = "CODE_SERVICE_SECRET";
  public static final String PIPELINE_SERVICE_SECRET_ENV = "PIPELINE_SERVICE_SECRET";
  public static final String IDP_NAMESPACE_ENV = "IDP_NAMESPACE";
  public static final String NAMESPACE_ENV = "NAMESPACE";
  public static final String NODE_ENV = "NODE_ENV";
  public static final String NODE_OPTIONS_ENV = "NODE_OPTIONS";
  public static final String DYNAMIC_CONFIG_RESOLUTION_ENV = "DYNAMIC_CONFIG_RESOLUTION";
  public static final String FF_SDK_KEY_ENV = "FF_SDK_KEY";
  public static final String ENVIRONMENT_ENV = "ENVIRONMENT";
  public static final String HARNESS_INTERNAL_PREFIX = "HARNESS_INTERNAL";
  public static final String POST_METHOD = "POST";
  public static final String GET_METHOD = "GET";
  public static final String X_API_KEY = "x-api-key";
  public static final String GCS_STORAGE_API_PATH = "storage.cloud.google.com/";
  public static final String GCS_PUBLIC_URL_API_PATH = "storage.googleapis.com/";
  public static final String IMAGE_PATH_PREFIX = "static";
  public static final String BACKSTAGE_BASE_URL_LOCAL_VALUE = "http://localhost:7007/";
  public static final String RESPONSE_STATUS = "status";
  public static final String NAMESPACE_ACCOUNT_PREFIX = "account";
  public static final String PROJECT_NAME = "project_name";
  public static final String PROJECT_IDENTIFIER = "project_identifier";
  public static final String ORGANIZATION_NAME = "org_name";
  public static final String ORGANIZATION_IDENTIFIER = "org_identifier";
  public static final String PROCESSED_DATA = "_processed_data";
  public static final String INVALID_VALUE_TYPE_ERROR = "DSL result value does not match data point type";

  public static final List<String> ENTITIES_SUPPORTING_SYSTEM = List.of("Component", "API", "Resource");

  /**
   * Identifier prefix reserved for OOTB (Harness-managed) cards. Such cards are global references resolved under
   * the reserved {@link #GLOBAL_ACCOUNT_ID} account and are never account-owned, so they must never be persisted
   * as account rows nor deleted from an account. Kept here (rather than in a feature package) so both the homepage
   * and persona-view layers can share a single source of truth without introducing a package dependency cycle.
   */
  public static final String OOTB_CARD_IDENTIFIER_PREFIX = "ootb:";

  public static boolean isOotbCardIdentifier(String identifier) {
    return identifier != null && identifier.startsWith(OOTB_CARD_IDENTIFIER_PREFIX);
  }
}
