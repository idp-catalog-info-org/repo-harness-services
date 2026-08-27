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
 * Thrown by the synchronous API endpoint sync flow when the live Git-placeholder fetch, spec
 * fetch/resolution, or spec parse fails. Not a {@code WingsException}, so it is mapped explicitly
 * by the resource layer to HTTP 500 rather than falling through to the {@code UnexpectedException}
 * default of 400. The failed entity is left untouched (Git-fetch failure) or already has
 * {@code extractionStatus=failed} persisted by {@link ApiEndpointProcessor} (parse/other failure);
 * previously-extracted endpoints are never deleted.
 */
@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointSyncFailedException extends RuntimeException {
  public ApiEndpointSyncFailedException(String message) {
    super(message);
  }

  public ApiEndpointSyncFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
