/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fme.exception;

import static io.harness.eraro.ErrorCode.POLICY_EVALUATION_FAILURE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.fme.governance.FmeGovernanceResult;

/**
 * Exception thrown when FME governance policy denies an operation.
 * This is triggered when the FME API returns a 499 status code.
 */
@OwnedBy(HarnessTeam.FME)
public class FmePolicyDeniedException extends WingsException {
  private static final String MESSAGE_KEY = "message";
  private final FmeGovernanceResult governanceResult;

  public FmePolicyDeniedException(String message, FmeGovernanceResult governanceResult) {
    super(message, null, POLICY_EVALUATION_FAILURE, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
    this.governanceResult = governanceResult;
  }

  public FmeGovernanceResult getGovernanceResult() {
    return governanceResult;
  }
}
