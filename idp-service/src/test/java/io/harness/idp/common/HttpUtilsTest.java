/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import okhttp3.Request;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class HttpUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuildRequestForPost() {
    String url = "https://example.com/api";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Authorization", "Bearer token");
    String body = "{\"key\":\"value\"}";

    Request request = HttpUtils.buildRequest(url, "POST", headers, body);

    assertThat(request).isNotNull();
    assertThat(request.url().toString()).isEqualTo(url);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.header("Content-Type")).isEqualTo("application/json");
    assertThat(request.header("Authorization")).isEqualTo("Bearer token");
    assertThat(request.body()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuildRequestForGet() {
    String url = "https://example.com/api";
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    String body = "";

    Request request = HttpUtils.buildRequest(url, "GET", headers, body);

    assertThat(request).isNotNull();
    assertThat(request.url().toString()).isEqualTo(url);
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.header("Accept")).isEqualTo("application/json");
    assertThat(request.body()).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuildRequestWithUnsupportedMethod() {
    String url = "https://example.com/api";
    Map<String, String> headers = new HashMap<>();
    String body = "";

    assertThatThrownBy(() -> HttpUtils.buildRequest(url, "PUT", headers, body))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Method PUT is not supported");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuildRequestWithEmptyHeaders() {
    String url = "https://example.com/api";
    Map<String, String> headers = new HashMap<>();
    String body = "";

    Request request = HttpUtils.buildRequest(url, "GET", headers, body);

    assertThat(request).isNotNull();
    assertThat(request.url().toString()).isEqualTo(url);
  }
}
