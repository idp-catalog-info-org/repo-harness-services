/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.monitoringmanager;

import io.harness.monitoringmanager.client.remote.MonitoringManagerClientModule;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.AbstractModule;

public abstract class AbstractMonitoringManagerModule extends AbstractModule {
  @Override
  protected void configure() {
    install(MonitoringManagerModule.getInstance());
    install(new MonitoringManagerClientModule(MonitoringManagerClientConfig(), serviceSecret(), clientId()));
  }

  public abstract ServiceHttpClientConfig MonitoringManagerClientConfig();

  public abstract String serviceSecret();

  public abstract String clientId();
}
