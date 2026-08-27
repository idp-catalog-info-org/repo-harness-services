/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;

@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface CIExecutionRepositoryCustom {
  CIExecutionMetadata getExecutionMetadata(String accountID, String runtimeId);
  void updateQueueId(String AccountID, String runtimeId, String queueId, String queueTopic, String queueSubTopic);
  CIExecutionMetadata updateExecutionStatus(String accountID, String runtimeId, String status);
  CIExecutionMetadata updateLastProcessedTime(String accountID, String runtimeId, Long lastProcessedTime);
  CIExecutionMetadata updateCapacityTaskInProgress(String accountID, String runtimeId, boolean capacityTaskInProgress);
  boolean tryAcquireCapacityTaskLock(
      String accountID, String runtimeId, Long currentTimeMillis, Long minProcessingWaitTime);
  boolean tryAcquireConcurrencyQueueMessageProcessorLock(String accountID, String runtimeId);
}
