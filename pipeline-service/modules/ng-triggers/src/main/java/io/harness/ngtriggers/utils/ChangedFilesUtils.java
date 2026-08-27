/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.ngtriggers.Constants.BITBUCKET_LOWER_CASE;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_ADDED;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_MODIFIED;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_REMOVED;
import static io.harness.ngtriggers.Constants.GITHUB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.GITLAB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.HARNESS_LOWER_CASE;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD_COMMITS;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD_HEAD_COMMIT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class ChangedFilesUtils {
  public Set<String> getFilesFromPushPayload(FilterRequestData filterRequestData, boolean useFallbackExpression) {
    return getFilesFromPushPayload(filterRequestData.getWebhookPayloadData(), useFallbackExpression);
  }

  @VisibleForTesting
  public Set<String> getFilesFromPushPayload(
      WebhookPayloadData webhookPayloadData, boolean useHeadCommitFallbackExpression) {
    Set<String> pushPayloadFiles = new HashSet<>();
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
    switch (webhookPayloadData.getOriginalEvent().getSourceRepoType().toLowerCase()) {
      case GITHUB_LOWER_CASE:
        getPushPayloadFiles(triggerExpressionEvaluator, pushPayloadFiles, useHeadCommitFallbackExpression);
        return pushPayloadFiles;
      case GITLAB_LOWER_CASE:
      case HARNESS_LOWER_CASE:
        getPushPayloadFiles(triggerExpressionEvaluator, pushPayloadFiles, false);
        return pushPayloadFiles;
      case BITBUCKET_LOWER_CASE:
      default:
        return pushPayloadFiles;
    }
  }

  private void getPushPayloadFiles(TriggerExpressionEvaluator triggerExpressionEvaluator, Set<String> pushPayloadFiles,
      boolean useHeadCommitFallbackExpression) {
    List rawCommits = (List) triggerExpressionEvaluator.evaluateExpression(TRIGGER_PAYLOAD_COMMITS);
    List commits = new ArrayList<>(rawCommits != null ? rawCommits : Collections.emptyList());
    if (useHeadCommitFallbackExpression && commits.isEmpty()) {
      Object headCommit = triggerExpressionEvaluator.evaluateExpression(TRIGGER_PAYLOAD_HEAD_COMMIT);
      if (headCommit != null && (!headCommit.equals("null"))) {
        log.info("Fallback to head commit");
        commits.add(headCommit);
      }
    }
    for (Object commitObject : commits) {
      Map<String, Object> commitJson = (Map) commitObject;
      for (Object added : (List) commitJson.get(COMMIT_FILE_ADDED)) {
        pushPayloadFiles.add((String) added);
      }
      for (Object modified : (List) commitJson.get(COMMIT_FILE_MODIFIED)) {
        pushPayloadFiles.add((String) modified);
      }
      for (Object removed : (List) commitJson.get(COMMIT_FILE_REMOVED)) {
        pushPayloadFiles.add((String) removed);
      }
    }
  }
}
