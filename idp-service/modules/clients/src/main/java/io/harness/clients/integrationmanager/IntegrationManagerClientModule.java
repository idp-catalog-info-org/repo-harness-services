/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.circuitbreaker.utils.CircuitBreakerRegistrationUtils.getCircuitBreaker;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(IDP)
public class IntegrationManagerClientModule extends AbstractModule {
  public static final String NEXTGEN_MANAGER_CIRCUIT_BREAKER_NAME = "ng-manager";

  private final ServiceHttpClientConfig integrationManagerClientConfig;
  private final String integrationManagerSecret;
  private final String clientId;

  public IntegrationManagerClientModule(
      ServiceHttpClientConfig integrationManagerClientConfig, String integrationManagerSecret, String clientId) {
    this.integrationManagerClientConfig = integrationManagerClientConfig;
    this.integrationManagerSecret = integrationManagerSecret;
    this.clientId = clientId;
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  public IntegrationManagerClientFactory privilegedIntegrationManagerClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new IntegrationManagerClientFactory(integrationManagerClientConfig, integrationManagerSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.PRIVILEGED,
        getCircuitBreaker(NEXTGEN_MANAGER_CIRCUIT_BREAKER_NAME));
  }

  @Provides
  @Singleton
  public IntegrationManagerClientFactory nonPrivilegedIntegrationManagerClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new IntegrationManagerClientFactory(integrationManagerClientConfig, integrationManagerSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED,
        getCircuitBreaker(NEXTGEN_MANAGER_CIRCUIT_BREAKER_NAME));
  }

  @Override
  protected void configure() {
    bind(IntegrationManagerClient.class).toProvider(IntegrationManagerClientFactory.class).in(Scopes.SINGLETON);
    bind(IntegrationManagerClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(IntegrationManagerClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
