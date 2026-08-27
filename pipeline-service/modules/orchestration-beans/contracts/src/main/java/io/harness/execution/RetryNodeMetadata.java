/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class RetryNodeMetadata {
  // This RetryNodeMetadata stores startTs, endTs, runSequence and email of executor of original execution to show in
  // the UI.
  Long startTs;
  Long endTs;
  Integer runSequence;
  String originalPlanExecutionId;
  ExecutionTriggerInfo executedBy;
}