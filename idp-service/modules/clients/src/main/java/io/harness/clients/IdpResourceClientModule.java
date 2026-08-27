/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.circuitbreaker.utils.CircuitBreakerRegistrationUtils.getCircuitBreaker;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(IDP)
public class IdpResourceClientModule extends AbstractModule {
  public static final String IDP_SERVICE_CIRCUIT_BREAKER_NAME = "idp-service";

  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public IdpResourceClientModule(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  @Singleton
  private IdpResourceClientHttpFactory idpResourceClientHttpFactory(KryoConverterFactory kryoConverterFactory) {
    return new IdpResourceClientHttpFactory(this.serviceHttpClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, clientMode,
        getCircuitBreaker(IDP_SERVICE_CIRCUIT_BREAKER_NAME));
  }

  @Override
  protected void configure() {
    this.bind(IdpResourceClient.class).toProvider(IdpResourceClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
