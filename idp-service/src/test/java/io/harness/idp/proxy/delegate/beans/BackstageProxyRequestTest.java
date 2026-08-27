/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate.beans;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class BackstageProxyRequestTest extends CategoryTest {
  private static final String TEST_URL = "https://api.github.com/repos/test";
  private static final String TEST_METHOD = "GET";
  private static final String TEST_BODY = "{\"key\":\"value\"}";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestCreation() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    assertNotNull(request);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetAndGetUrl() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    request.setUrl(TEST_URL);
    assertEquals(TEST_URL, request.getUrl());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetAndGetMethod() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    request.setMethod(TEST_METHOD);
    assertEquals(TEST_METHOD, request.getMethod());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetAndGetBody() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    request.setBody(TEST_BODY);
    assertEquals(TEST_BODY, request.getBody());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetAndGetHeaders() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer token");
    headers.put("Content-Type", "application/json");

    request.setHeaders(headers);
    assertEquals(headers, request.getHeaders());
    assertEquals("Bearer token", request.getHeaders().get("Authorization"));
    assertEquals("application/json", request.getHeaders().get("Content-Type"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestWithAllFields() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");

    request.setUrl(TEST_URL);
    request.setMethod(TEST_METHOD);
    request.setBody(TEST_BODY);
    request.setHeaders(headers);

    assertEquals(TEST_URL, request.getUrl());
    assertEquals(TEST_METHOD, request.getMethod());
    assertEquals(TEST_BODY, request.getBody());
    assertEquals(headers, request.getHeaders());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestWithEmptyBody() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    request.setUrl(TEST_URL);
    request.setMethod(TEST_METHOD);
    request.setBody("");

    assertEquals("", request.getBody());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestWithNullValues() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    request.setUrl(null);
    request.setMethod(null);
    request.setBody(null);
    request.setHeaders(null);

    assertEquals(null, request.getUrl());
    assertEquals(null, request.getMethod());
    assertEquals(null, request.getBody());
    assertEquals(null, request.getHeaders());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestWithDifferentHttpMethods() {
    String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};

    for (String method : methods) {
      BackstageProxyRequest request = new BackstageProxyRequest();
      request.setMethod(method);
      assertEquals(method, request.getMethod());
    }
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestWithMultipleHeaders() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer token123");
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", "Backstage");
    headers.put("X-Custom-Header", "custom-value");

    request.setHeaders(headers);

    assertEquals(5, request.getHeaders().size());
    assertEquals("Bearer token123", request.getHeaders().get("Authorization"));
    assertEquals("custom-value", request.getHeaders().get("X-Custom-Header"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBackstageProxyRequestModifyHeaders() {
    BackstageProxyRequest request = new BackstageProxyRequest();
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer old-token");

    request.setHeaders(headers);
    assertEquals("Bearer old-token", request.getHeaders().get("Authorization"));

    request.getHeaders().put("Authorization", "Bearer new-token");
    assertEquals("Bearer new-token", request.getHeaders().get("Authorization"));
  }
}
