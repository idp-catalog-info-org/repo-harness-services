/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.processor.api.EndpointExtractor.ExtractionResult;
import io.harness.rule.Owner;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class EndpointExtractorTest extends CategoryTest {
  private OpenApiSpecParser parser;
  private EndpointExtractor extractor;

  @Before
  public void setUp() {
    parser = new OpenApiSpecParser();
    extractor = new EndpointExtractor();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void extractsBasicEndpoints() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n  - url: https://api.example.com/v1\n"
        + "paths:\n"
        + "  /payments:\n"
        + "    post:\n"
        + "      operationId: createPayment\n"
        + "      summary: Create payment\n"
        + "      responses: {'200': {description: ok}}\n"
        + "    get:\n"
        + "      operationId: listPayments\n"
        + "      responses: {'200': {description: ok}}\n";

    OpenAPI spec = parser.parse(yaml);
    ExtractionResult result = extractor.extract(spec);

    Map<String, Object> apis = result.getApis();
    assertThat(apis.get("protocol")).isEqualTo("openapi");
    assertThat(apis.get("count")).isEqualTo(2);

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKey("POST /v1/payments");
    assertThat(paths).containsKey("GET /v1/payments");

    Map<String, Object> createPayment = paths.get("POST /v1/payments");
    assertThat(createPayment.get("method")).isEqualTo("POST");
    assertThat(createPayment.get("path")).isEqualTo("/payments");
    assertThat(createPayment.get("operationId")).isEqualTo("createPayment");
    assertThat(createPayment.get("summary")).isEqualTo("Create payment");
    assertThat(createPayment.get("enrichments")).isInstanceOf(Map.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void keysIncludeBasePath() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n  - url: https://api.github.com/api/v3\n"
        + "paths:\n"
        + "  /repos/{owner}/{repo}:\n"
        + "    get:\n"
        + "      operationId: getRepo\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("GET /api/v3/repos/{owner}/{repo}");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void asymmetricBasePathsProduceOneEntryPerServer() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n"
        + "  - url: https://api.example.com/v1\n"
        + "    description: Production\n"
        + "  - url: https://staging-api.example.com/v4\n"
        + "    description: Staging\n"
        + "paths:\n"
        + "  /payments:\n"
        + "    post:\n"
        + "      operationId: createPayment\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");

    assertThat(paths).containsKey("POST /v1/payments");
    assertThat(paths).containsKey("POST /v4/payments");
    // Asymmetric basePath is a benign warning, not a degraded extraction.
    assertThat(result.isDegraded()).isFalse();
    assertThat(result.getWarnings()).anyMatch(w -> w.contains("asymmetric"));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void symmetricBasePathsProduceOneEntryPerOperation() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n"
        + "  - url: https://api.example.com/v1\n"
        + "  - url: https://staging-api.example.com/v1\n"
        + "paths:\n"
        + "  /payments:\n"
        + "    post:\n"
        + "      operationId: createPayment\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");

    assertThat(paths).hasSize(1).containsKey("POST /v1/payments");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void serversDifferingOnlyByTrailingSlashAreSymmetric() {
    // A trailing slash still means no basePath; treating it as distinct would produce a malformed
    // "GET //users" double-slash key.
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n"
        + "  - url: https://api.example.com\n"
        + "  - url: https://api.example.com/\n"
        + "paths:\n"
        + "  /users:\n"
        + "    get:\n"
        + "      operationId: listUsers\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");

    assertThat(paths).hasSize(1).containsKey("GET /users");
    assertThat(paths).doesNotContainKey("GET //users");
    assertThat(result.getWarnings()).noneMatch(w -> w.contains("asymmetric"));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void outputIsDeterministicAcrossRuns() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n  - url: https://api.example.com/v1\n"
        + "paths:\n"
        + "  /z:\n    get: {operationId: z, responses: {'200': {description: ok}}}\n"
        + "  /a:\n    get: {operationId: a, responses: {'200': {description: ok}}}\n"
        + "  /m:\n    get: {operationId: m, responses: {'200': {description: ok}}}\n";

    ExtractionResult first = extractor.extract(parser.parse(yaml));
    ExtractionResult second = extractor.extract(parser.parse(yaml));

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> p1 = (Map<String, Map<String, Object>>) first.getApis().get("paths");
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> p2 = (Map<String, Map<String, Object>>) second.getApis().get("paths");

    assertThat(p1.keySet()).isEqualTo(p2.keySet());
    // Keys are sorted regardless of declaration order.
    assertThat(List.copyOf(p1.keySet())).isSorted();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void emptyPathsProducesZeroCount() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: Empty\n  version: 1.0.0\n"
        + "paths: {}\n";
    ExtractionResult result = extractor.extract(parser.parse(yaml));
    assertThat(result.getApis().get("count")).isEqualTo(0);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void truncationAt5000DoesNotMarkDegraded() {
    // Truncation is a benign warning, not a degraded extraction.
    String yaml = "openapi: 3.0.1\ninfo:\n  title: T\n  version: 1.0.0\npaths:\n";
    StringBuilder b = new StringBuilder(yaml);
    for (int i = 0; i < 5001; i++) {
      b.append("  /e")
          .append(i)
          .append(":\n    get:\n      operationId: op")
          .append(i)
          .append("\n      responses: {'200': {description: ok}}\n");
    }
    ExtractionResult result = extractor.extract(parser.parse(b.toString()));
    assertThat(result.getApis().get("truncated")).isEqualTo(true);
    assertThat(result.isDegraded()).isFalse();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void unresolvedServerTemplateVariableMarksDegraded() {
    // A template variable with no default leaves the URL unknown, so extraction is degraded.
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n"
        + "  - url: https://{region}.api.example.com/v1\n"
        + "    variables:\n"
        + "      region:\n"
        + "        description: AWS region (no default)\n"
        + "paths:\n"
        + "  /payments:\n"
        + "    post:\n"
        + "      operationId: createPayment\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    assertThat(result.isDegraded()).isTrue();
    assertThat(result.getWarnings()).anyMatch(w -> w.contains("region"));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void serverWithTemplateVariableUsesDefault() {
    String yaml = "openapi: 3.0.1\n"
        + "info:\n  title: T\n  version: 1.0.0\n"
        + "servers:\n"
        + "  - url: https://{region}.api.example.com/v1\n"
        + "    variables:\n"
        + "      region:\n"
        + "        default: us-east-1\n"
        + "paths:\n"
        + "  /payments:\n"
        + "    post:\n"
        + "      operationId: createPayment\n"
        + "      responses: {'200': {description: ok}}\n";

    ExtractionResult result = extractor.extract(parser.parse(yaml));
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) result.getApis().get("paths");
    assertThat(paths).containsKey("POST /v1/payments");
  }
}
