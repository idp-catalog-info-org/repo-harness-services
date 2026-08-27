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

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class Swagger2EndpointExtractorTest extends CategoryTest {
  private Swagger2EndpointExtractor extractor;

  @Before
  public void setUp() {
    extractor = new Swagger2EndpointExtractor();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void extractsSwagger2YamlSpec() {
    String swagger2 = "swagger: '2.0'\n"
        + "info:\n"
        + "  title: Test API v2\n"
        + "  version: 1.0.0\n"
        + "host: api.example.com\n"
        + "basePath: /v1\n"
        + "schemes:\n"
        + "  - https\n"
        + "paths:\n"
        + "  /pets:\n"
        + "    get:\n"
        + "      operationId: listPets\n"
        + "      summary: List pets\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n";

    EndpointExtractor.ExtractionResult result = extractor.extract(swagger2);
    assertThat(result).isNotNull();
    assertThat(result.getApis().get("protocol")).isEqualTo("swagger");
    assertThat(result.getApis().get("version")).isEqualTo("2.0");

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("GET /v1/pets");
    assertThat(paths.get("GET /v1/pets").get("operationId")).isEqualTo("listPets");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> servers = (List<Map<String, Object>>) result.getApis().get("servers");
    assertThat(servers).hasSize(1);
    assertThat(servers.get(0).get("url")).isEqualTo("https://api.example.com/v1");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void extractsSwagger2JsonSpec() {
    String json = "{"
        + "\"swagger\": \"2.0\","
        + "\"info\": {\"title\": \"Test\", \"version\": \"1.0.0\"},"
        + "\"host\": \"api.example.com\","
        + "\"basePath\": \"/v1\","
        + "\"schemes\": [\"https\"],"
        + "\"paths\": {\"/pets\": {\"get\": {\"operationId\": \"listPets\","
        + "\"responses\": {\"200\": {\"description\": \"ok\"}}}}}"
        + "}";

    EndpointExtractor.ExtractionResult result = extractor.extract(json);
    assertThat(result).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("GET /v1/pets");
    assertThat(paths.get("GET /v1/pets").get("operationId")).isEqualTo("listPets");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void extractsMinifiedSingleLineSwagger2Json() {
    String minified = "{\"swagger\":\"2.0\",\"info\":{\"title\":\"Min\",\"version\":\"1.0.0\"},"
        + "\"basePath\":\"/api\","
        + "\"paths\":{\"/pets\":{\"get\":{\"operationId\":\"listPets\","
        + "\"responses\":{\"200\":{\"description\":\"ok\"}}}}}}";

    EndpointExtractor.ExtractionResult result = extractor.extract(minified);
    assertThat(result).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("GET /api/pets");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void extractsMultipleMethodsAndPaths() {
    String swagger2 = "swagger: '2.0'\n"
        + "info:\n"
        + "  title: Multi\n"
        + "  version: 1.0.0\n"
        + "basePath: /api\n"
        + "paths:\n"
        + "  /users:\n"
        + "    get:\n"
        + "      operationId: listUsers\n"
        + "      summary: List users\n"
        + "      tags:\n"
        + "        - users\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n"
        + "    post:\n"
        + "      operationId: createUser\n"
        + "      summary: Create user\n"
        + "      deprecated: true\n"
        + "      responses:\n"
        + "        '201':\n"
        + "          description: created\n"
        + "  /users/{id}:\n"
        + "    delete:\n"
        + "      operationId: deleteUser\n"
        + "      description: Deletes a user\n"
        + "      responses:\n"
        + "        '204':\n"
        + "          description: deleted\n";

    EndpointExtractor.ExtractionResult result = extractor.extract(swagger2);

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).hasSize(3);
    assertThat(paths).containsKeys("GET /api/users", "POST /api/users", "DELETE /api/users/{id}");

    Map<String, Object> getUsers = paths.get("GET /api/users");
    assertThat(getUsers.get("operationId")).isEqualTo("listUsers");
    assertThat(getUsers.get("tags")).isEqualTo(List.of("users"));

    Map<String, Object> postUsers = paths.get("POST /api/users");
    assertThat(postUsers.get("deprecated")).isEqualTo(true);

    Map<String, Object> deleteUser = paths.get("DELETE /api/users/{id}");
    assertThat(deleteUser.get("description")).isEqualTo("Deletes a user");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void handlesNoHostGracefully() {
    String swagger2 = "swagger: '2.0'\n"
        + "info:\n"
        + "  title: No Host\n"
        + "  version: 1.0.0\n"
        + "basePath: /v2\n"
        + "paths:\n"
        + "  /items:\n"
        + "    get:\n"
        + "      operationId: listItems\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n";

    EndpointExtractor.ExtractionResult result = extractor.extract(swagger2);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> servers = (List<Map<String, Object>>) result.getApis().get("servers");
    assertThat(servers.get(0).get("url")).isEqualTo("/v2");

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("GET /v2/items");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsInvalidContent() {
    assertThatThrownBy(() -> extractor.extract(null)).isInstanceOf(OpenApiParseException.class);
    assertThatThrownBy(() -> extractor.extract("")).isInstanceOf(OpenApiParseException.class);
  }
}
