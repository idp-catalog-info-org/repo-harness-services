/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import static io.harness.authorization.AuthorizationServiceHeader.RELEASE_MANAGEMENT_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ReleaseManagementClientFactory
    extends AbstractHttpClientFactory implements Provider<ReleaseManagementClient> {
  public ReleaseManagementClientFactory(ServiceHttpClientConfig config, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(config, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, null, clientMode);
    setTargetServiceId(RELEASE_MANAGEMENT_SERVICE.getServiceId());
  }

  @Override
  public ReleaseManagementClient get() {
    Retrofit retrofit = getRetrofit();
    return retrofit.create(ReleaseManagementClient.class);
  }
}
