/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.validation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetValidationResult {
  private final boolean valid;
  private final String errorMessage;

  public static AssetValidationResult success() {
    return AssetValidationResult.builder().valid(true).build();
  }

  public static AssetValidationResult failure(String errorMessage) {
    return AssetValidationResult.builder().valid(false).errorMessage(errorMessage).build();
  }
}