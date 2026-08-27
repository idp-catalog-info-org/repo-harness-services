/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class SchedulerModule extends AbstractModule {
  private static final AtomicReference<SchedulerModule> instanceRef = new AtomicReference<>();

  @Override
  protected void configure() {
    // other bindings
    bind(IRService.class).to(IRServiceImpl.class);
  }

  public static SchedulerModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new SchedulerModule());
    }
    return instanceRef.get();
  }

  @Provides
  @Singleton
  @Named("iroDataCollectionTaskScheduler")
  ScheduledExecutorService provideScheduledExecutorService() {
    return Executors.newScheduledThreadPool(1);
  }
}
