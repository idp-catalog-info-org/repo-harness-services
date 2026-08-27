/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class FetchMarketPlacePluginsUtilTest extends CategoryTest {
  private static final String TEST_URL = "https://api.github.com/repos/test/plugins";
  private static final String TEST_TOKEN = "test-github-token";
  private static final String TEST_RESPONSE_BODY = "{\"name\":\"test-plugin\",\"version\":\"1.0.0\"}";

  @Mock private Call mockCall;
  private Retry retry;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    RetryConfig config = RetryConfig.custom().maxAttempts(1).build();
    retry = Retry.of("test-retry", config);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_Success() throws IOException {
    ResponseBody responseBody = ResponseBody.create(TEST_RESPONSE_BODY, MediaType.parse("application/json"));
    Response response = new Response.Builder()
                            .request(new Request.Builder().url(TEST_URL).build())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(responseBody)
                            .build();

    when(mockCall.execute()).thenReturn(response);

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      String result = FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);

      assertNotNull(result);
      assertEquals(TEST_RESPONSE_BODY, result);
    }
  }

  @Test(expected = IOException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_UnsuccessfulResponse() throws IOException {
    Response response = new Response.Builder()
                            .request(new Request.Builder().url(TEST_URL).build())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .build();

    when(mockCall.execute()).thenReturn(response);

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);
    }
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_NetworkError() throws IOException {
    when(mockCall.execute()).thenThrow(new IOException("Network error"));

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);
    }
  }

  @Test(expected = IOException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_With401Response() throws IOException {
    Response response = new Response.Builder()
                            .request(new Request.Builder().url(TEST_URL).build())
                            .protocol(Protocol.HTTP_1_1)
                            .code(401)
                            .message("Unauthorized")
                            .build();

    when(mockCall.execute()).thenReturn(response);

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);
    }
  }

  @Test(expected = IOException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_With500Response() throws IOException {
    Response response = new Response.Builder()
                            .request(new Request.Builder().url(TEST_URL).build())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Internal Server Error")
                            .build();

    when(mockCall.execute()).thenReturn(response);

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetch_EmptyResponseBody() throws IOException {
    ResponseBody responseBody = ResponseBody.create("", MediaType.parse("application/json"));
    Response response = new Response.Builder()
                            .request(new Request.Builder().url(TEST_URL).build())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(responseBody)
                            .build();

    when(mockCall.execute()).thenReturn(response);

    try (MockedConstruction<OkHttpClient> mockedClient = Mockito.mockConstruction(
             OkHttpClient.class, (mock, context) -> { when(mock.newCall(any(Request.class))).thenReturn(mockCall); })) {
      String result = FetchMarketPlacePluginsUtil.fetch(TEST_URL, retry, TEST_TOKEN);

      assertNotNull(result);
      assertEquals("", result);
    }
  }
}
