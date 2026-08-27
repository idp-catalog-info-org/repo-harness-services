/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.unified.error.NgManagerErrorResponseDTO;

import lombok.experimental.UtilityClass;

/**
 * Builds a {@link NgManagerErrorResponseDTO} so NG Manager can propagate failure detail back to CI Manager instead of
 * losing it across the network boundary.
 */
@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class NgManagerErrorResponseUtils {
  public NgManagerErrorResponseDTO build(Exception e, String contextMessage) {
    // Use Harness ExceptionUtils to extract a REST-facing, cause-aware message: it unwraps WingsException,
    // ConstraintViolationException and HarnessException chains instead of relying on the raw Throwable#getMessage.
    String extractedMessage = ExceptionUtils.getMessage(e);
    String errorCode = (e instanceof WingsException wingsException) ? wingsException.getCode().name() : null;
    String errorMessage =
        isEmpty(extractedMessage) ? contextMessage : String.format("%s. %s", contextMessage, extractedMessage);
    return NgManagerErrorResponseDTO.builder()
        .errorMessage(errorMessage)
        .errorCode(errorCode)
        .detailedMessage(extractedMessage)
        .build();
  }
}
