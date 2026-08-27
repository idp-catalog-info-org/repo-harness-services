/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fme.exception;

import static io.harness.eraro.ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.FME)
public class FmeInternalServerErrorException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public FmeInternalServerErrorException(String message) {
    super(message, null, NG_PIPELINE_EXECUTION_EXCEPTION, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
  }

  public FmeInternalServerErrorException(String message, Throwable cause) {
    super(message, cause, NG_PIPELINE_EXECUTION_EXCEPTION, Level.ERROR, (EnumSet) null, (EnumSet) null);
    param(MESSAGE_KEY, message);
  }
}
