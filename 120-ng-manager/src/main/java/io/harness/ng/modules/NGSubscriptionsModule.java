/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.licensedmodules.services.LicensedModulesService;
import io.harness.ng.core.licensedmodules.services.LicensedModulesServiceImpl;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.ngsubscriptions.service.impl.NGSubscriptionsServiceImpl;

import com.google.inject.AbstractModule;

public class NGSubscriptionsModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public NGSubscriptionsModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    bind(NGSubscriptionsService.class).to(NGSubscriptionsServiceImpl.class);
    bind(LicensedModulesService.class).to(LicensedModulesServiceImpl.class);
  }
}
