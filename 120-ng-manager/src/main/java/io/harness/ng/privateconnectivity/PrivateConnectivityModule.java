/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.manage.ManagedExecutorService;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.provisioner.CreateOnceNetworkProvisioner;
import io.harness.ng.privateconnectivity.sanitizer.ReleaseReconciler;
import io.harness.ng.privateconnectivity.sanitizer.ReleaseSanitizer;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityService;
import io.harness.ng.privateconnectivity.services.impl.PrivateConnectivityInternalQueries;
import io.harness.ng.privateconnectivity.services.impl.PrivateConnectivityServiceImpl;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityAccountLock;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.ng.privateconnectivity.vendorclient.TailscaleProviderNetworkClient;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guice bindings for Harness Cloud Private Connectivity.
 *
 * ProviderNetworkClient is a provider boundary; the Phase 1 implementation is Tailscale and fails
 * closed through its own configuration and credential guards.
 */
@OwnedBy(CI)
public class PrivateConnectivityModule extends AbstractModule {
  private static final int OPERATION_THREADS = 4;
  private static final int OPERATION_QUEUE_CAPACITY = 100;
  private static final AtomicInteger OPERATION_THREAD_COUNTER = new AtomicInteger();

  @Override
  protected void configure() {
    bind(PrivateConnectivityAccountLock.class).in(Scopes.SINGLETON);
    bind(PrivateConnectivityChildCredentialService.class).in(Scopes.SINGLETON);
    bind(CreateOnceNetworkProvisioner.class).in(Scopes.SINGLETON);
    bind(ReleaseSanitizer.class).in(Scopes.SINGLETON);
    bind(ReleaseReconciler.class).in(Scopes.SINGLETON);
    bind(PrivateConnectivityExecutorLifecycle.class).in(Scopes.SINGLETON);
    bind(PrivateConnectivityInternalQueries.class).in(Scopes.SINGLETON);
    bind(PrivateConnectivityService.class).to(PrivateConnectivityServiceImpl.class).in(Scopes.SINGLETON);
    bind(ExecutorService.class)
        .annotatedWith(Names.named("privateConnectivityOperationExecutor"))
        .toInstance(new ManagedExecutorService(new ThreadPoolExecutor(OPERATION_THREADS, OPERATION_THREADS, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(OPERATION_QUEUE_CAPACITY), r -> {
              int threadNumber = OPERATION_THREAD_COUNTER.incrementAndGet();
              Thread thread = new Thread(r, "private-connectivity-operation-" + threadNumber);
              thread.setDaemon(true);
              return thread;
            }, new ThreadPoolExecutor.AbortPolicy())));
  }

  @Provides
  @Singleton
  PrivateConnectivityOrgConfig privateConnectivityOrgConfig(NextGenConfiguration nextGenConfiguration) {
    PrivateConnectivityOrgConfig config = nextGenConfiguration.getPrivateConnectivityOrgConfig();
    return config == null ? PrivateConnectivityOrgConfig.builder().build() : config;
  }

  @Provides
  @Singleton
  ProviderNetworkClient providerNetworkClient(PrivateConnectivityOrgConfig orgConfig,
      PrivateConnectivityConfigRepository configRepository,
      PrivateConnectivityChildCredentialService childCredentialService) {
    return new TailscaleProviderNetworkClient(orgConfig, configRepository, childCredentialService);
  }
}
