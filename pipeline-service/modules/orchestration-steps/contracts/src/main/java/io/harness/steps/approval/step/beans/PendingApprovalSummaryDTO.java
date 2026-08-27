/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "PendingApprovalSummary", description = "Summary of a pending approval instance")
public class PendingApprovalSummaryDTO {
  String id;
  ApprovalType type;
  ApprovalStatus status;
  long deadline;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  String planExecutionId;
  String approvalMessage;
  Long createdAt;
  Long lastModifiedAt;
  String errorMessage;
}
