/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.oidc_auth.service.OidcProviderService;
import io.harness.oidc_auth.service.impl.OidcProviderServiceImpl;

import com.google.inject.AbstractModule;

public class OidcProviderModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public OidcProviderModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    // Add service class and their implementations here as and when we add them
    bind(OidcProviderService.class).to(OidcProviderServiceImpl.class);
  }
}
