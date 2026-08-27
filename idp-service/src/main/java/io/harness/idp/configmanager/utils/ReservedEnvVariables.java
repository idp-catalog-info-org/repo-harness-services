/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.utils;

import io.harness.idp.common.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ReservedEnvVariables {
  private ReservedEnvVariables() {}

  public static final List<Pattern> RESERVED_ENV_VARIABLES =
      new ArrayList<>(List.of(Pattern.compile(Constants.GITHUB_TOKEN), Pattern.compile(Constants.GITHUB_APP_ID),
          Pattern.compile(Constants.GITHUB_INSTALLATION_ID), Pattern.compile(Constants.GITHUB_APP_PRIVATE_KEY_REF),
          Pattern.compile(Constants.GITLAB_TOKEN), Pattern.compile(Constants.BITBUCKET_USERNAME),
          Pattern.compile(Constants.BITBUCKET_TOKEN), Pattern.compile(Constants.BITBUCKET_USERNAME_API_ACCESS),
          Pattern.compile(Constants.BITBUCKET_API_ACCESS_TOKEN), Pattern.compile(Constants.AZURE_REPO_TOKEN),
          Pattern.compile(Constants.BACKEND_SECRET), Pattern.compile(Constants.PROXY_ENV_NAME),
          Pattern.compile(Constants.LAST_UPDATED_TIMESTAMP), Pattern.compile(Constants.AUTH_GITHUB_CLIENT_ID),
          Pattern.compile(Constants.AUTH_GITHUB_CLIENT_SECRET),
          Pattern.compile(Constants.AUTH_GITHUB_ENTERPRISE_INSTANCE_URL),
          Pattern.compile(Constants.AUTH_GOOGLE_CLIENT_ID), Pattern.compile(Constants.AUTH_GOOGLE_CLIENT_SECRET),
          Pattern.compile(Constants.AUTH_ATLASSIAN_CLIENT_ID), Pattern.compile(Constants.AUTH_ATLASSIAN_CLIENT_SECRET),
          Pattern.compile(Constants.INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_BITBUCKET_CLOUD_USERNAME),
          Pattern.compile(Constants.INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD),
          Pattern.compile(Constants.INTEGRATIONS_BITBUCKET_SERVER_USERNAME + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_BITBUCKET_SERVER_PASSWORD + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_GITHUB_TOKEN + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_GITHUB_APP_APPLICATION_ID + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + ".*"),
          Pattern.compile(Constants.INTEGRATIONS_GITLAB_TOKEN + ".*"), Pattern.compile(Constants.PERMISSIONS),
          Pattern.compile(Constants.USERGROUP), Pattern.compile(Constants.PROXY_ENV_NAME),
          Pattern.compile(Constants.INTEGRATIONS_HARNESS_CODE_REPO_TOKEN + ".*"),
          Pattern.compile(Constants.HARNESS_API_KEY), Pattern.compile(Constants.HARNESS_ACCOUNT_ID),
          Pattern.compile(Constants.HARNESS_TOKEN), Pattern.compile(Constants.IDP_BACKEND_SECRET),
          Pattern.compile(Constants.IDP_ENCRYPTION_SECRET_ENV), Pattern.compile(Constants.IDP_SERVICE_SECRET_ENV),
          Pattern.compile(Constants.IDP_SERVICE_BASE_URL_ENV), Pattern.compile(Constants.CODE_SERVICE_SECRET_ENV),
          Pattern.compile(Constants.PIPELINE_SERVICE_SECRET_ENV), Pattern.compile(Constants.IDP_NAMESPACE_ENV),
          Pattern.compile(Constants.NAMESPACE_ENV), Pattern.compile(Constants.NODE_ENV),
          Pattern.compile(Constants.NODE_OPTIONS_ENV), Pattern.compile(Constants.DYNAMIC_CONFIG_RESOLUTION_ENV),
          Pattern.compile(Constants.FF_SDK_KEY_ENV), Pattern.compile(Constants.ENVIRONMENT_ENV),
          Pattern.compile(Constants.HARNESS_INTERNAL_PREFIX + ".*")));
}
