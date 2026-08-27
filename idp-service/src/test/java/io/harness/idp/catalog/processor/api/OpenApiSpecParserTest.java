/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.swagger.v3.oas.models.OpenAPI;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class OpenApiSpecParserTest extends CategoryTest {
  private OpenApiSpecParser parser;
  private MockWebServer mockServer;

  @Before
  public void setUp() throws Exception {
    parser = new OpenApiSpecParser();
    mockServer = new MockWebServer();
    mockServer.start();
  }

  @After
  public void tearDown() throws Exception {
    if (mockServer != null) {
      mockServer.shutdown();
    }
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void parsesValidYamlSpec() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n"
        + "  title: Test API\n"
        + "  version: 1.0.0\n"
        + "paths:\n"
        + "  /pets:\n"
        + "    get:\n"
        + "      operationId: listPets\n"
        + "      summary: List pets\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n";

    OpenAPI result = parser.parse(yaml);
    assertThat(result).isNotNull();
    assertThat(result.getOpenapi()).startsWith("3.0");
    assertThat(result.getPaths()).containsKey("/pets");
    assertThat(result.getPaths().get("/pets").getGet().getOperationId()).isEqualTo("listPets");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void parsesValidJsonSpec() {
    String json = "{"
        + "\"openapi\": \"3.0.1\","
        + "\"info\": {\"title\": \"Test\", \"version\": \"1.0.0\"},"
        + "\"paths\": {\"/pets\": {\"get\": {\"operationId\": \"listPets\","
        + "\"responses\": {\"200\": {\"description\": \"ok\"}}}}}"
        + "}";

    OpenAPI result = parser.parse(json);
    assertThat(result).isNotNull();
    assertThat(result.getPaths()).containsKey("/pets");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void doesNotMakeAnyHttpRequestForExternalRefs() throws Exception {
    // The SSRF defense: a $ref pointing at an external URL must NOT cause the parser to make a
    // network request. The earlier version of this test used an unreachable host and only
    // asserted the parse didn't throw, which would pass even if a fetch was attempted (a real
    // fetch attempt would resolve-fail, parser would swallow it, ref left as-is, path still
    // present — false green).
    //
    // This rewrite points the $ref at a local MockWebServer that records every incoming request.
    // After parsing, we assert the recorded request count is zero. Any HTTP egress at all
    // (even an attempt) would register here.
    mockServer.enqueue(
        new MockResponse().setBody("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}"));

    String maliciousRefUrl = mockServer.url("/should-not-be-fetched.json").toString();
    String yaml = "openapi: 3.0.1\n"
        + "info:\n"
        + "  title: Test\n"
        + "  version: 1.0.0\n"
        + "paths:\n"
        + "  /pets:\n"
        + "    get:\n"
        + "      operationId: listPets\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n"
        + "          content:\n"
        + "            application/json:\n"
        + "              schema:\n"
        + "                $ref: '" + maliciousRefUrl + "'\n";

    OpenAPI result = parser.parse(yaml);

    assertThat(result).isNotNull();
    assertThat(result.getPaths()).containsKey("/pets");
    // The crucial assertion — zero recorded requests means the parser did not touch the network.
    assertThat(mockServer.getRequestCount())
        .as("Parser must not perform HTTP requests for $ref URLs (SSRF defense)")
        .isZero();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void doesNotFetchRefsInsideRequestBodyOrParameters() throws Exception {
    // Defense-in-depth: refs can appear in many places (responses, requestBody, parameters).
    // Verify none of them trigger egress.
    mockServer.enqueue(new MockResponse().setBody("{}"));
    String url = mockServer.url("/external.json").toString();

    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: Test\n  version: 1.0.0\n"
        + "paths:\n"
        + "  /pets:\n"
        + "    post:\n"
        + "      operationId: createPet\n"
        + "      parameters:\n"
        + "        - $ref: '" + url + "'\n"
        + "      requestBody:\n"
        + "        $ref: '" + url + "'\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          $ref: '" + url + "'\n";

    parser.parse(yaml);
    assertThat(mockServer.getRequestCount()).isZero();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsMalformedYaml() {
    String malformed = "this is: not: a valid: openapi\nspec at all\nno paths here";
    assertThatThrownBy(() -> parser.parse(malformed)).isInstanceOf(OpenApiParseException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsEmptyInput() {
    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(OpenApiParseException.class);
    assertThatThrownBy(() -> parser.parse("")).isInstanceOf(OpenApiParseException.class);
    assertThatThrownBy(() -> parser.parse("   \n  ")).isInstanceOf(OpenApiParseException.class);
  }
}
