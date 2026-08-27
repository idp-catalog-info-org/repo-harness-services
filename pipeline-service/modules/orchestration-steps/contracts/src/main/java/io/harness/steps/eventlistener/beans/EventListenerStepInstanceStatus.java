/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnsupportedOperationException;
import io.harness.pms.contracts.execution.Status;

import java.util.EnumSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public enum EventListenerStepInstanceStatus {
  WAITING,
  SUCCEEDED,
  FAILED,
  ABORTED,
  EXPIRED,
  RUNTIME_EXCEPTION;

  private static final Set<EventListenerStepInstanceStatus> FINAL_STATUSES =
      EnumSet.of(SUCCEEDED, FAILED, ABORTED, EXPIRED, RUNTIME_EXCEPTION);

  public boolean isFinalStatus() {
    return FINAL_STATUSES.contains(this);
  }

  public Status toFinalExecutionStatus() {
    return switch (this) {
      case FAILED, RUNTIME_EXCEPTION -> Status.FAILED;
      case ABORTED -> Status.ABORTED;
      case SUCCEEDED -> Status.SUCCEEDED;
      case EXPIRED -> Status.EXPIRED;
      default -> throw new UnsupportedOperationException(String.format("Invalid status: %s", name()));
    };
  }
}
