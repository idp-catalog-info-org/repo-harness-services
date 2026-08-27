/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.network.Http.getSslContext;
import static io.harness.network.Http.getTrustManagers;

import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ExtensionRegistryLite;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(IDP)
public class IdpAgentClientHttpFactory implements Provider<IdpAgentClient> {
  private final ServiceHttpClientConfig agentClientConfig;
  private final OkHttpClient httpClient;
  private final OkHttpClientConnectionPoolConfig connectionPoolConfig;
  private static final ObjectMapper mapper = new ObjectMapper()
                                                 .registerModule(new Jdk8Module())
                                                 .registerModule(new GuavaModule())
                                                 .registerModule(new JavaTimeModule());

  @Inject
  public IdpAgentClientHttpFactory(@Named("idpAgentHttpClientConfig") ServiceHttpClientConfig agentClientConfig,
      @Named("idpAgentHttpClientConnectionPoolConfig") OkHttpClientConnectionPoolConfig connectionPoolConfig) {
    this.agentClientConfig = agentClientConfig;
    this.connectionPoolConfig = connectionPoolConfig;
    this.httpClient = this.buildHttpClient();
  }

  @Override
  public IdpAgentClient get() {
    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(this.agentClientConfig.getBaseUrl())
            .client(httpClient)
            .addConverterFactory(ProtoConverterFactory.createWithRegistry(ExtensionRegistryLite.newInstance()))
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .build();
    return retrofit.create(IdpAgentClient.class);
  }

  private OkHttpClient buildHttpClient() {
    try {
      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(new ConnectionPool(connectionPoolConfig.getMaxIdleConnections(),
              connectionPoolConfig.getKeepAliveDuration(), TimeUnit.valueOf(connectionPoolConfig.getTimeUnit())))
          .hostnameVerifier(new NoopHostnameVerifier())
          .sslSocketFactory(getSslContext().getSocketFactory(), (X509TrustManager) getTrustManagers()[0])
          .connectTimeout(agentClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
          .readTimeout(agentClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
          .build();
    } catch (Exception e) {
      log.error("Failed to build HTTP client for IDP Agent", e);
      throw new RuntimeException("Failed to build HTTP client for IDP Agent", e);
    }
  }
}
