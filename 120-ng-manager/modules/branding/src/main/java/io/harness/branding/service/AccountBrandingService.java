/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.service;

import io.harness.spec.server.ng.v1.model.BrandingResponseDTO;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import java.io.InputStream;

public interface AccountBrandingService {
  BrandingResponseDTO saveBrandingInfo(String accountIdentifier, InputStream largeLogoLightInputStream,
      String largeLogoLightExtension, InputStream smallLogoLightInputStream, String smallLogoLightExtension,
      InputStream faviconInputStream, String faviconExtension, InputStream largeLogoDarkInputStream,
      String largeLogoDarkExtension, Boolean brandingOnSignInPage);
  BrandingSettingsDTO getBrandingSettings(String harnessAccount);
}
