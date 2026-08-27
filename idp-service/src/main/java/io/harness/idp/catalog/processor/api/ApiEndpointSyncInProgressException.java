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
 * Thrown by the synchronous API endpoint sync flow when {@link ApiEndpointProcessor} could not
 * acquire the per-entity extraction lock (a concurrent sync/iterator run already holds it). Not a
 * {@code WingsException}, so it is mapped explicitly by the resource layer to HTTP 409 rather than
 * falling through to the {@code UnexpectedException} default of 400.
 */
@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointSyncInProgressException extends RuntimeException {
  public ApiEndpointSyncInProgressException(String message) {
    super(message);
  }
}
