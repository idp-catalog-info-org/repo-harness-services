/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.external;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.proxy.external.beans.ExternalProxyEndpointConfig;
import io.harness.idp.proxy.external.resource.ExternalProxyApiImpl;
import io.harness.idp.proxy.external.service.ExternalProxyService;
import io.harness.rule.Owner;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExternalProxyApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private ExternalProxyService externalProxyService;
  @Mock private UriInfo uriInfo;
  @Mock private HttpHeaders httpHeaders;

  @InjectMocks private ExternalProxyApiImpl externalProxyApiImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/v1/external-proxy/devspace/teams"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCookieForwardedForHarnessDomainWithPath() throws Exception {
    ExternalProxyEndpointConfig config = ExternalProxyEndpointConfig.builder()
                                             .endpoint("devspace")
                                             .target("https://central-devspace.pr2.harness.io/devspacebackend")
                                             .build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "devspace")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "GET")).thenReturn(true);
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    requestHeaders.put("cookie", Collections.singletonList("session=abc123"));
    requestHeaders.put("accept", Collections.singletonList("application/json"));
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "devspace/teams", ACCOUNT_IDENTIFIER);

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("GET"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertTrue(
        "Cookie header should be forwarded for harness.io domain with path", forwardedHeaders.containsKey("cookie"));
    assertEquals("session=abc123", forwardedHeaders.get("cookie").get(0));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCookieBlockedForNonHarnessDomain() throws Exception {
    ExternalProxyEndpointConfig config =
        ExternalProxyEndpointConfig.builder().endpoint("external").target("https://api.example.com/v1").build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "external")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "GET")).thenReturn(true);
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    requestHeaders.put("cookie", Collections.singletonList("session=abc123"));
    requestHeaders.put("accept", Collections.singletonList("application/json"));
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "external/data", ACCOUNT_IDENTIFIER);

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("GET"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertFalse("Cookie header should NOT be forwarded for non-harness domain", forwardedHeaders.containsKey("cookie"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCookieForwardedForHarnessDomainWithoutPath() throws Exception {
    ExternalProxyEndpointConfig config =
        ExternalProxyEndpointConfig.builder().endpoint("service").target("https://app.harness.io").build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "service")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "GET")).thenReturn(true);
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    requestHeaders.put("cookie", Collections.singletonList("token=xyz"));
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "service/api", ACCOUNT_IDENTIFIER);

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("GET"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertTrue("Cookie header should be forwarded for harness.io domain", forwardedHeaders.containsKey("cookie"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAuthorizationForwardedForHarnessDomain() throws Exception {
    ExternalProxyEndpointConfig config = ExternalProxyEndpointConfig.builder()
                                             .endpoint("devspace")
                                             .target("https://central-devspace.pr2.harness.io/backend")
                                             .build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "devspace")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "POST")).thenReturn(true);
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    requestHeaders.put("authorization", Collections.singletonList("Bearer token123"));
    requestHeaders.put("cookie", Collections.singletonList("session=abc"));
    requestHeaders.put("x-api-key", Collections.singletonList("key123"));
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.postProxy(uriInfo, httpHeaders, "devspace/create", ACCOUNT_IDENTIFIER, "{\"name\":\"test\"}");

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("POST"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertTrue("Authorization should be forwarded for harness domain", forwardedHeaders.containsKey("authorization"));
    assertTrue("Cookie should be forwarded for harness domain", forwardedHeaders.containsKey("cookie"));
    assertTrue("x-api-key should be forwarded for harness domain", forwardedHeaders.containsKey("x-api-key"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEndpointNotFound() {
    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "unknown")).thenReturn(Optional.empty());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);

    Response response = externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "unknown/path", ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMethodNotAllowed() {
    ExternalProxyEndpointConfig config = ExternalProxyEndpointConfig.builder()
                                             .endpoint("readonly")
                                             .target("https://api.harness.io")
                                             .allowedMethods(Collections.singletonList("GET"))
                                             .build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "readonly")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "POST")).thenReturn(false);

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);

    Response response = externalProxyApiImpl.postProxy(uriInfo, httpHeaders, "readonly/data", ACCOUNT_IDENTIFIER, "{}");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultAllowedHeadersForwarded() throws Exception {
    ExternalProxyEndpointConfig config =
        ExternalProxyEndpointConfig.builder().endpoint("external").target("https://api.example.com").build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "external")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "GET")).thenReturn(true);
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    requestHeaders.put("accept", Collections.singletonList("application/json"));
    requestHeaders.put("content-type", Collections.singletonList("application/json"));
    requestHeaders.put("user-agent", Collections.singletonList("test-agent"));
    requestHeaders.put("host", Collections.singletonList("localhost"));
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "external/data", ACCOUNT_IDENTIFIER);

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("GET"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertTrue("accept should be forwarded", forwardedHeaders.containsKey("accept"));
    assertTrue("content-type should be forwarded", forwardedHeaders.containsKey("content-type"));
    assertTrue("user-agent should be forwarded", forwardedHeaders.containsKey("user-agent"));
    assertFalse("host should be blocked", forwardedHeaders.containsKey("host"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConfiguredHeadersApplied() throws Exception {
    Map<String, String> configHeaders = new HashMap<>();
    configHeaders.put("X-Custom-Header", "${MY_SECRET}");

    ExternalProxyEndpointConfig config = ExternalProxyEndpointConfig.builder()
                                             .endpoint("service")
                                             .target("https://api.example.com")
                                             .headers(configHeaders)
                                             .build();

    when(externalProxyService.getProxyEndpointConfig(ACCOUNT_IDENTIFIER, "service")).thenReturn(Optional.of(config));
    when(externalProxyService.isMethodAllowed(config, "GET")).thenReturn(true);
    when(externalProxyService.resolveHeaderValue(ACCOUNT_IDENTIFIER, "${MY_SECRET}")).thenReturn("resolved-value");
    when(externalProxyService.getResponse(
             anyString(), anyBoolean(), anyString(), anyString(), anyString(), any(), anyMap(), anyString()))
        .thenReturn(Response.ok().build());

    MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
    when(httpHeaders.getRequestHeaders()).thenReturn(requestHeaders);
    when(httpHeaders.getMediaType()).thenReturn(null);

    externalProxyApiImpl.getProxy(uriInfo, httpHeaders, "service/data", ACCOUNT_IDENTIFIER);

    ArgumentCaptor<Map<String, List<String>>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(externalProxyService)
        .getResponse(eq(ACCOUNT_IDENTIFIER), anyBoolean(), anyString(), anyString(), eq("GET"), any(),
            headersCaptor.capture(), anyString());

    Map<String, List<String>> forwardedHeaders = headersCaptor.getValue();
    assertTrue("Configured header should be applied", forwardedHeaders.containsKey("X-Custom-Header"));
    assertEquals("resolved-value", forwardedHeaders.get("X-Custom-Header").get(0));
  }
}
