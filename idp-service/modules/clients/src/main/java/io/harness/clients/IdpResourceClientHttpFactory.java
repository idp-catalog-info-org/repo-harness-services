/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;

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
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(IDP)
public class IdpResourceClientHttpFactory extends AbstractHttpClientFactory implements Provider<IdpResourceClient> {
  public IdpResourceClientHttpFactory(ServiceHttpClientConfig httpClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode, CircuitBreaker circuitBreaker) {
    super(httpClientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, circuitBreaker, clientMode);
    setTargetServiceId(IDP_SERVICE.getServiceId());
  }

  @Override
  public IdpResourceClient get() {
    return getRetrofit().create(IdpResourceClient.class);
  }
}
