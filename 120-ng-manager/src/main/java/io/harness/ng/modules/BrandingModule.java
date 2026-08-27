/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.service.AccountBrandingAssetService;
import io.harness.branding.service.AccountBrandingService;
import io.harness.branding.service.impl.AccountBrandingAssetServiceImpl;
import io.harness.branding.service.impl.AccountBrandingServiceImpl;

import com.google.inject.AbstractModule;

@OwnedBy(HarnessTeam.PL)
public class BrandingModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AccountBrandingService.class).to(AccountBrandingServiceImpl.class);
    bind(AccountBrandingAssetService.class).to(AccountBrandingAssetServiceImpl.class);
  }
}
