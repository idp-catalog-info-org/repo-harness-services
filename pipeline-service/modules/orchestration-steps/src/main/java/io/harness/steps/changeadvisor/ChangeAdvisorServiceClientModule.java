/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CV)
public class ChangeAdvisorServiceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig config;
  private final String serviceSecret;
  private final String clientId;

  public ChangeAdvisorServiceClientModule(ServiceHttpClientConfig config, String serviceSecret, String clientId) {
    this.config = config;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private ChangeAdvisorServiceClientFactory changeAdvisorServiceClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new ChangeAdvisorServiceClientFactory(this.config, this.serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    this.bind(ChangeAdvisorServiceClient.class)
        .toProvider(ChangeAdvisorServiceClientFactory.class)
        .in(Scopes.SINGLETON);
  }
}
