/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification.summary;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.steps.approval.step.beans.ApprovalStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "ApprovalSummaryKeys")
public class ApprovalSummary {
  // maintain this list with field keys which can have multiple lines
  // it is being used to convert to line breaks when rendering as html
  public static final List<String> TEXT_FIELDS_IN_APPROVAL_SUMMARY =
      List.of(ApprovalSummaryKeys.approvalMessage, ApprovalSummaryKeys.action, ApprovalSummaryKeys.finishedStages,
          ApprovalSummaryKeys.runningStages, ApprovalSummaryKeys.upcomingStages);
  public static final String DEFAULT_STAGE_DELIMITER = ", ";
  public static final String NEWLINE_STAGE_DELIMITER = "\n ";
  String pipelineName;
  String orgName;
  String projectName;
  String approvalMessage;
  String startedAt;
  String expiresAt;
  String triggeredBy;
  ApprovalStatus status;
  String action;
  @NonFinal String pipelineExecutionSummary;
  Set<String> finishedStages;
  Set<String> runningStages;
  Set<String> upcomingStages;

  String pipelineExecutionLink;
  String timeRemainingForApproval;
  String currentStageName;

  public Map<String, String> toParams(@NotNull String stageDelimiter) {
    // avoid sending npe fields as it causes npe in notification client
    Map<String, String> params = new HashMap<>();
    params.put(ApprovalSummaryKeys.pipelineName, pipelineName);
    params.put(ApprovalSummaryKeys.orgName, orgName);
    params.put(ApprovalSummaryKeys.projectName, projectName);
    params.put(ApprovalSummaryKeys.startedAt, startedAt);
    params.put(ApprovalSummaryKeys.expiresAt, expiresAt);
    params.put(ApprovalSummaryKeys.triggeredBy, triggeredBy);
    params.put(ApprovalSummaryKeys.finishedStages, joinStages(finishedStages, stageDelimiter));
    params.put(ApprovalSummaryKeys.runningStages, joinStages(runningStages, stageDelimiter));
    params.put(ApprovalSummaryKeys.upcomingStages, joinStages(upcomingStages, stageDelimiter));

    params.put(ApprovalSummaryKeys.pipelineExecutionLink, pipelineExecutionLink);
    params.put(ApprovalSummaryKeys.timeRemainingForApproval, timeRemainingForApproval);
    params.put(ApprovalSummaryKeys.action, action);
    params.put(ApprovalSummaryKeys.currentStageName, currentStageName);

    if (isNull(status)) {
      params.put(ApprovalSummaryKeys.status, "");
    } else {
      params.put(ApprovalSummaryKeys.status, String.valueOf(status).toLowerCase(Locale.ROOT));
    }
    if (isNull(approvalMessage)) {
      params.put(ApprovalSummaryKeys.approvalMessage, "");
    } else {
      params.put(ApprovalSummaryKeys.approvalMessage, approvalMessage);
    }
    return params;
  }

  private static String joinStages(Set<String> stages, @NotNull String stageDelimiter) {
    if (EmptyPredicate.isEmpty(stages)) {
      return "N/A";
    }
    return String.join(stageDelimiter, stages);
  }
}
