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
 * Thrown when an entity's {@code spec.definition} cannot be resolved into raw spec content for
 * parsing.
 */
@OwnedBy(HarnessTeam.IDP)
public class SpecResolutionException extends RuntimeException {
  public SpecResolutionException(String message) {
    super(message);
  }

  public SpecResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
