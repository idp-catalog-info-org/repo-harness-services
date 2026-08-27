/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import static io.harness.idp.common.HttpUtils.buildRequest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.DslClientConfig;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.security.AllTrustingX509TrustManager;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DirectDslClient implements DslClient {
  private static volatile OkHttpClient okHttpClient;
  private static final ImmutableList<TrustManager> TRUST_ALL_CERTS =
      ImmutableList.of(new AllTrustingX509TrustManager());
  @Inject @Named("dslClientConfig") private DslClientConfig dslClientConfig;
  @Inject
  @Named("directDslClientHttpClientConnectionPoolConfig")
  OkHttpClientConnectionPoolConfig directDslClientHttpClientConnectionPoolConfig;

  @Override
  public Response call(
      String accountIdentifier, ApiRequestDetails apiRequestDetails, Set<String> delegateSelectors, Object entity) {
    OkHttpClient client = getOkHttpClient();
    String url = apiRequestDetails.getUrl();
    String method = apiRequestDetails.getMethod();
    String body = apiRequestDetails.getRequestBody();
    Request request = buildRequest(url, method, apiRequestDetails.getHeaders(), body);
    return executeRequest(client, request);
  }

  private OkHttpClient getOkHttpClient() {
    if (okHttpClient == null) {
      synchronized (DirectDslClient.class) {
        if (okHttpClient == null) {
          okHttpClient = buildOkHttpClient();
        }
      }
    }
    return okHttpClient;
  }

  private OkHttpClient buildOkHttpClient() {
    try {
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
      return new OkHttpClient()
          .newBuilder()
          .connectionPool(new ConnectionPool(directDslClientHttpClientConnectionPoolConfig.getMaxIdleConnections(),
              directDslClientHttpClientConnectionPoolConfig.getKeepAliveDuration(),
              TimeUnit.valueOf(directDslClientHttpClientConnectionPoolConfig.getTimeUnit())))
          .connectTimeout(dslClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
          .readTimeout(dslClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
          .writeTimeout(dslClientConfig.getWriteTimeOutSeconds(), TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
          .build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new UnexpectedException(e.getMessage());
    }
  }

  private Response executeRequest(OkHttpClient client, Request request) {
    try (okhttp3.Response response = client.newCall(request).execute()) {
      return Response.status(response.code()).entity(Objects.requireNonNull(response.body()).string()).build();
    } catch (Exception e) {
      log.error("Error in request execution through direct dsl client. Error = {}", e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Error occurred while fetching data").build())
          .build();
    }
  }
}
