/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.ng.iro.config.IRConfig;
import io.harness.remote.client.ServiceHttpClientConfig;

import clients.iromanager.remote.IROManagerClientModule;
import com.google.inject.AbstractModule;

public abstract class AbstractIROManagerModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new SchedulerModule());
    install(new IROManagerClientModule(iroManagerClientConfig(), serviceSecret(), clientId()));
    bind(IRODataCollectionTaskService.class).to(IRODataCollectionTaskServiceImpl.class);
    bind(IRConfig.class).toInstance(irConfig());
    bind(FetchDataCollectionTaskSchedulerService.class).asEagerSingleton();
    bind(ZoomService.class).to(ZoomServiceImpl.class);
  }

  public abstract ServiceHttpClientConfig iroManagerClientConfig();

  public abstract IRConfig irConfig();

  public abstract String serviceSecret();

  public abstract String clientId();
}