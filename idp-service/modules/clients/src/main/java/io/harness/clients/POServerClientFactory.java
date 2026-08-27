/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.http.HttpHeaders.AUTHORIZATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import io.harness.security.ServiceTokenGenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import javax.inject.Named;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OwnedBy(HarnessTeam.IDP)
public class POServerClientFactory implements Provider<POServerClient> {
  private static final String CLIENT_ID = "IDPService"; // Source service ID
  private final POServerConfig poServerConfig;
  private final String idpServiceSecret;
  private final ServiceTokenGenerator tokenGenerator = new ServiceTokenGenerator();

  @Inject
  public POServerClientFactory(POServerConfig poServerConfig, @Named("idpServiceSecret") String idpServiceSecret) {
    this.poServerConfig = poServerConfig;
    this.idpServiceSecret = idpServiceSecret;
  }

  @Override
  public POServerClient get() {
    Gson gson = new GsonBuilder().setLenient().create();
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(this.getBaseUrl())
                            .client(getOkHttpClient())
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
    return retrofit.create(POServerClient.class);
  }

  private OkHttpClient getOkHttpClient() {
    // Get the base OkHttpClient
    OkHttpClient baseClient = Http.getUnsafeOkHttpClient(this.getBaseUrl());

    // Create a new client with our authorization interceptor
    return baseClient.newBuilder().addInterceptor(getAuthorizationInterceptor()).build();
  }

  private Interceptor getAuthorizationInterceptor() {
    return chain -> {
      Request.Builder builder = chain.request().newBuilder();

      // Generate the service token for Authorization header only
      String authorizationToken = tokenGenerator.getServiceToken(idpServiceSecret);

      // Add only the Authorization header for middleware authentication
      // Let the API methods handle the Harness-Token
      builder.header(AUTHORIZATION, CLIENT_ID + StringUtils.SPACE + authorizationToken);

      return chain.proceed(builder.build());
    };
  }

  private String getBaseUrl() {
    if (!isEmpty(this.poServerConfig.getExternalUrl())) {
      return this.poServerConfig.getExternalUrl();
    }
    return this.poServerConfig.getBaseUrl();
  }
}