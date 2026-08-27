/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.services;

import static io.harness.idp.proxy.services.IdpAuthInterceptor.AUTHORIZATION;
import static io.harness.idp.proxy.services.IdpAuthInterceptor.X_SOURCE_PRINCIPAL;
import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.Principal;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IdpAuthInterceptorTest extends CategoryTest {
  private static final String SECRET = "test-secret";
  private static final String TOKEN = "test-token";

  AutoCloseable openMocks;

  @Mock ServiceTokenGenerator tokenGenerator;
  @Mock Interceptor.Chain chain;

  IdpAuthInterceptor idpAuthInterceptor;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    idpAuthInterceptor = new IdpAuthInterceptor(tokenGenerator, SECRET);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIntercept() throws IOException {
    Request originalRequest = new Request.Builder().url("http://test.com").build();

    when(chain.request()).thenReturn(originalRequest);
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any(Principal.class)))
        .thenReturn(TOKEN);

    Response mockResponse =
        new Response.Builder().request(originalRequest).protocol(Protocol.HTTP_1_1).code(200).message("OK").build();

    when(chain.proceed(any(Request.class))).thenReturn(mockResponse);

    Response response = idpAuthInterceptor.intercept(chain);

    assertNotNull(response);
    assertEquals(200, response.code());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetAuthHeaders() {
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any(Principal.class)))
        .thenReturn(TOKEN);

    Map<String, String> authHeaders = idpAuthInterceptor.getAuthHeaders();

    assertNotNull(authHeaders);
    assertEquals(2, authHeaders.size());
    assertNotNull(authHeaders.get(AUTHORIZATION));
    assertNotNull(authHeaders.get(X_SOURCE_PRINCIPAL));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetAuthHeadersContainsToken() {
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any(Principal.class)))
        .thenReturn(TOKEN);

    Map<String, String> authHeaders = idpAuthInterceptor.getAuthHeaders();

    String authHeader = authHeaders.get(AUTHORIZATION);
    assertNotNull(authHeader);
    assertEquals(true, authHeader.contains(TOKEN));

    String sourcePrincipalHeader = authHeaders.get(X_SOURCE_PRINCIPAL);
    assertNotNull(sourcePrincipalHeader);
    assertEquals(true, sourcePrincipalHeader.contains(TOKEN));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testInterceptWithHeaders() throws IOException {
    Request originalRequest =
        new Request.Builder().url("http://test.com").header("Custom-Header", "custom-value").build();

    when(chain.request()).thenReturn(originalRequest);
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any(Principal.class)))
        .thenReturn(TOKEN);

    Response mockResponse =
        new Response.Builder().request(originalRequest).protocol(Protocol.HTTP_1_1).code(200).message("OK").build();

    when(chain.proceed(any(Request.class))).thenReturn(mockResponse);

    Response response = idpAuthInterceptor.intercept(chain);

    assertNotNull(response);
    assertEquals(200, response.code());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
