/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Thrown by {@link SpecFetcher} for any failure to retrieve a spec from a URL. The message is
 * intended to be safe to surface in the entity's {@code extractionStatus} / {@code lastError}
 * fields for customer-actionable diagnostics.
 */
@OwnedBy(HarnessTeam.IDP)
public class SpecFetchException extends RuntimeException {
  public SpecFetchException(String message) {
    super(message);
  }

  public SpecFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
