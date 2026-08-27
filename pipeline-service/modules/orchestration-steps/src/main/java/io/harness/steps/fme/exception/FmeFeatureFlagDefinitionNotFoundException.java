/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fme.exception;

import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(HarnessTeam.FME)
public class FmeFeatureFlagDefinitionNotFoundException extends WingsException {
  private static final String MESSAGE_KEY = "message";
  private static final String FLAG_NAME_KEY = "flagName";
  private static final String ENVIRONMENT_KEY = "environment";
  private static final String ERROR_MESSAGE = "Feature flag definition not found for flag '%s' in environment '%s'";

  public FmeFeatureFlagDefinitionNotFoundException(String flagName, String environment) {
    super(format(ERROR_MESSAGE, flagName, environment), null, RESOURCE_NOT_FOUND, Level.ERROR, null, null);
    param(MESSAGE_KEY, format(ERROR_MESSAGE, flagName, environment));
    param(FLAG_NAME_KEY, flagName);
    param(ENVIRONMENT_KEY, environment);
  }
}
