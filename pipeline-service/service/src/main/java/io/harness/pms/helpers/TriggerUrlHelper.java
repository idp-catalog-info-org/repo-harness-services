/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.pipeline.service.PipelineServiceConfiguration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TriggerUrlHelper {
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject PipelineExpressionHelper pipelineExpressionHelper;
  private static final String TRIGGER_URL = "%s/account/%s/all/orgs/%s/projects/%s/pipelines/%s/triggers/%s/detail/";
  private static final String TRIGGER_ACTIVITY_URL =
      "%s/account/%s/all/orgs/%s/projects/%s/pipelines/%s/triggers/%s/activity-history/";
  private static final String PIPELINE_STUDIO_URL =
      "%s/account/%s/all/orgs/%s/projects/%s/pipelines/%s/pipeline-studio/";

  public String generateTriggerUrl(
      String accountId, String orgId, String projectId, String pipelineId, String triggerId) {
    String baseUrl = generateBaseUrl(accountId);
    return String.format(TRIGGER_URL, baseUrl, accountId, orgId, projectId, pipelineId, triggerId);
  }

  public String generateTriggerActivityHistoryUrl(
      String accountId, String orgId, String projectId, String pipelineId, String triggerId) {
    String baseUrl = generateBaseUrl(accountId);
    return String.format(TRIGGER_ACTIVITY_URL, baseUrl, accountId, orgId, projectId, pipelineId, triggerId);
  }

  public String generatePipelineStudioUrl(String accountId, String orgId, String projectId, String pipelineId) {
    String baseUrl = generateBaseUrl(accountId);
    return String.format(PIPELINE_STUDIO_URL, baseUrl, accountId, orgId, projectId, pipelineId);
  }

  private String generateBaseUrl(String accountId) {
    String vanityUrl = pipelineExpressionHelper.getVanityUrl(accountId);
    return PipelineExpressionHelper.getBaseUrl(pipelineServiceConfiguration.getPipelineServiceBaseUrl(), vanityUrl);
  }
}
