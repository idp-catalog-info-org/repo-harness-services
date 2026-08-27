/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import static io.harness.authorization.AuthorizationServiceHeader.CHANGE_ADVISOR_SERVICE;

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
@OwnedBy(HarnessTeam.CV)
public class ChangeAdvisorServiceClientFactory
    extends AbstractHttpClientFactory implements Provider<ChangeAdvisorServiceClient> {
  public ChangeAdvisorServiceClientFactory(ServiceHttpClientConfig config, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(config, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, null, clientMode);
    setTargetServiceId(CHANGE_ADVISOR_SERVICE.getServiceId());
  }

  @Override
  public ChangeAdvisorServiceClient get() {
    Retrofit retrofit = getRetrofit();
    return retrofit.create(ChangeAdvisorServiceClient.class);
  }
}
