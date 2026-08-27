/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.enums;

import java.util.Set;
import lombok.Getter;

@Getter
public enum BrandingAssetType {
  LARGE_LOGO_LIGHT("largeLogoLight", Set.of("png"), 300 * 1024),
  SMALL_LOGO_LIGHT("smallLogoLight", Set.of("png"), 300 * 1024),
  FAVICON("favicon", Set.of("png"), 50 * 1024),
  LARGE_LOGO_DARK("largeLogoDark", Set.of("png"), 300 * 1024),
  SMALL_LOGO_DARK("smallLogoDark", Set.of("png"), 300 * 1024);

  private final String assetName;
  private final Set<String> allowedExtensions;
  private final long maxSizeBytes;

  BrandingAssetType(String assetName, Set<String> allowedExtensions, long maxSizeBytes) {
    this.assetName = assetName;
    this.allowedExtensions = allowedExtensions;
    this.maxSizeBytes = maxSizeBytes;
  }

  public boolean isValidExtension(String extension) {
    if (extension == null) {
      return false;
    }
    return allowedExtensions.contains(extension.toLowerCase());
  }
}