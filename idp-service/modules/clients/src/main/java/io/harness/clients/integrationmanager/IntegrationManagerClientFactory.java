/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.authorization.AuthorizationServiceHeader.INTEGRATION_MANAGER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@OwnedBy(IDP)
public class IntegrationManagerClientFactory
    extends AbstractHttpClientFactory implements Provider<IntegrationManagerClient> {
  public IntegrationManagerClientFactory(ServiceHttpClientConfig integrationManagerClientConfig,
      String integrationManagerSecret, ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory,
      String clientId, ClientMode clientMode, CircuitBreaker circuitBreaker) {
    super(integrationManagerClientConfig, integrationManagerSecret, tokenGenerator, kryoConverterFactory, clientId,
        circuitBreaker, clientMode);
    setTargetServiceId(INTEGRATION_MANAGER.getServiceId());
  }

  @Override
  public IntegrationManagerClient get() {
    return getRetrofit().create(IntegrationManagerClient.class);
  }
}
