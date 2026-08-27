/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome.Status;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointProcessorTest extends CategoryTest {
  private SpecSourceResolver specSourceResolver;
  private OpenApiSpecParser openApiSpecParser;
  private EndpointExtractor endpointExtractor;
  private Swagger2EndpointExtractor swagger2EndpointExtractor;
  private CatalogEntityRepository repository;
  private ResourceLocker resourceLocker;
  private AcquiredLock<?> lock;
  private CatalogServiceHelper catalogServiceHelper;
  private ApiEndpointProcessor processor;

  private static final String PETSTORE_YAML = "openapi: 3.0.1\n"
      + "info:\n  title: Petstore\n  version: 1.0.0\n"
      + "servers:\n  - url: https://api.example.com/v1\n"
      + "paths:\n"
      + "  /pets:\n"
      + "    get:\n"
      + "      operationId: listPets\n"
      + "      summary: List pets\n"
      + "      responses: {'200': {description: ok}}\n"
      + "    post:\n"
      + "      operationId: createPet\n"
      + "      summary: Create pet\n"
      + "      responses: {'200': {description: ok}}\n";

  @Before
  public void setUp() {
    specSourceResolver = mock(SpecSourceResolver.class);
    openApiSpecParser = new OpenApiSpecParser();
    endpointExtractor = new EndpointExtractor();
    swagger2EndpointExtractor = new Swagger2EndpointExtractor();
    repository = mock(CatalogEntityRepository.class);
    resourceLocker = mock(ResourceLocker.class);
    lock = mock(AcquiredLock.class);

    catalogServiceHelper = mock(CatalogServiceHelper.class);

    when(resourceLocker.acquireLock(anyString(), anyLong())).thenReturn((AcquiredLock) lock);

    processor = new ApiEndpointProcessor(specSourceResolver, openApiSpecParser, endpointExtractor,
        swagger2EndpointExtractor, repository, resourceLocker, catalogServiceHelper);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void successfulExtractionWritesApisToDecorator() {
    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    assertThat(apis).isNotNull();
    assertThat(apis.get("extractionStatus")).isEqualTo("success");
    assertThat(apis.get("specHash")).isNotNull();
    assertThat(apis.get("lastCheckedAt")).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKeys("GET /v1/pets", "POST /v1/pets");
    verify(repository).save(entity);
    verify(resourceLocker).releaseLock(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void userYamlIsByteIdenticalAfterProcessing() {
    // Load-bearing invariant: the processor must not touch the user's yaml field.
    CatalogEntity entity = newEntity();
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nname: payment-service\n");
    String originalYaml = entity.getYaml();

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    processor.processEntity(entity);

    assertThat(entity.getYaml()).isEqualTo(originalYaml);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void userYamlIsByteIdenticalAfterHashSkip() {
    // Invariant holds on the hash-skip early-return path (stampLastCheckedAt touches decorator only).
    CatalogEntity entity = newEntity();
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nname: payment-service\n");
    String originalYaml = entity.getYaml();
    seedExistingApisWithStatus(
        entity, "GET /v1/pets", new LinkedHashMap<>(), SpecHasher.hash(PETSTORE_YAML), "success");

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.HASH_SKIPPED);
    assertThat(entity.getYaml()).isEqualTo(originalYaml);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void userYamlIsByteIdenticalAfterFetchFailure() {
    // Invariant holds on the failure path (persistFailure writes only error fields).
    CatalogEntity entity = newEntity();
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nname: payment-service\n");
    String originalYaml = entity.getYaml();

    when(specSourceResolver.resolve(entity)).thenThrow(new SpecFetchException("HTTP 404"));
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.FAILED);
    assertThat(entity.getYaml()).isEqualTo(originalYaml);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void userYamlIsByteIdenticalAfterLockSkip() {
    // Asserted explicitly so a future refactor doing pre-lock work must preserve the invariant.
    CatalogEntity entity = newEntity();
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nname: payment-service\n");
    String originalYaml = entity.getYaml();
    when(resourceLocker.acquireLock(anyString(), anyLong())).thenReturn(null);

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.LOCK_SKIPPED);
    assertThat(entity.getYaml()).isEqualTo(originalYaml);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void smartMergePreservesCustomerEnrichments() {
    // Customer riskScore must survive re-extraction that refreshes system fields.
    CatalogEntity entity = newEntity();
    Map<String, Object> existingEnrichments = new LinkedHashMap<>();
    existingEnrichments.put("riskScore", 8.5);
    existingEnrichments.put("team", "platform");
    seedExistingApis(entity, "GET /v1/pets", existingEnrichments);

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    processor.processEntity(entity);

    Map<String, Object> apis = readApisFromDecorator(entity);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    @SuppressWarnings("unchecked")
    Map<String, Object> enrichments = (Map<String, Object>) paths.get("GET /v1/pets").get("enrichments");

    assertThat(enrichments).containsEntry("riskScore", 8.5);
    assertThat(enrichments).containsEntry("team", "platform");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hardDeleteRemovesEndpointsNoLongerInSpec() {
    // An endpoint no longer in the spec must be hard-deleted, not retained.
    CatalogEntity entity = newEntity();
    Map<String, Object> oldEnrichments = new LinkedHashMap<>();
    oldEnrichments.put("riskScore", 6.0);
    seedExistingApis(entity, "DELETE /v1/pets/{id}", oldEnrichments);

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    processor.processEntity(entity);

    Map<String, Object> apis = readApisFromDecorator(entity);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).doesNotContainKey("DELETE /v1/pets/{id}");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void decoratorSiblingDataIsPreserved() {
    // Our write must not trample sibling subtrees under _processed_data (e.g. PagerDuty).
    CatalogEntity entity = newEntity();
    Map<String, Object> decorator = new HashMap<>();
    Map<String, Object> processedData = new LinkedHashMap<>();
    Map<String, Object> integration = new LinkedHashMap<>();
    integration.put("PagerDuty", Map.of("serviceId", "P123"));
    processedData.put("_integration", integration);
    decorator.put(PROCESSED_DATA, processedData);
    entity.setDecorator(decorator);

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    processor.processEntity(entity);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultProcessed = (Map<String, Object>) entity.getDecorator().get(PROCESSED_DATA);
    @SuppressWarnings("unchecked")
    Map<String, Object> resultIntegration = (Map<String, Object>) resultProcessed.get("_integration");
    assertThat(resultIntegration).containsKey("PagerDuty");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void idempotency() {
    // Two runs of the same spec produce identical decorator state (ignoring time fields).
    CatalogEntity entity1 = newEntity("payment_service_1");
    CatalogEntity entity2 = newEntity("payment_service_2");
    when(specSourceResolver.resolve(any())).thenReturn(PETSTORE_YAML);

    processor.processEntity(entity1);
    processor.processEntity(entity2);

    Map<String, Object> apis1 = readApisFromDecorator(entity1);
    Map<String, Object> apis2 = readApisFromDecorator(entity2);
    assertThat(apis1.get("specHash")).isEqualTo(apis2.get("specHash"));
    assertThat(apis1.get("paths").toString()).isEqualTo(apis2.get("paths").toString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void specFetchFailurePreservesPreviousEndpoints() {
    // On resolver failure, previously-extracted endpoints survive and status becomes failed.
    CatalogEntity entity = newEntity();
    Map<String, Object> oldEnrichments = new LinkedHashMap<>();
    oldEnrichments.put("riskScore", 9.0);
    seedExistingApis(entity, "GET /v1/pets", oldEnrichments);

    when(specSourceResolver.resolve(entity))
        .thenThrow(new SpecFetchException("Spec URL returned HTTP 404: verify the URL."));

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.FAILED);
    Map<String, Object> apis = readApisFromDecorator(entity);
    assertThat(apis.get("extractionStatus")).isEqualTo("failed");
    assertThat(apis.get("lastError").toString()).contains("404");
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKey("GET /v1/pets");
    @SuppressWarnings("unchecked")
    Map<String, Object> enrichments = (Map<String, Object>) paths.get("GET /v1/pets").get("enrichments");
    assertThat(enrichments).containsEntry("riskScore", 9.0);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockNotAcquiredSkipsProcessing() {
    when(resourceLocker.acquireLock(anyString(), anyLong())).thenReturn(null);

    CatalogEntity entity = newEntity();
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.LOCK_SKIPPED);
    verify(repository, never()).save(any());
    verify(specSourceResolver, never()).resolve(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void exposesOldAndNewKeysForRecomputeGating() {
    // Score recompute is gated on key-set diff; the processor surfaces both sets via the outcome.
    CatalogEntity entity = newEntity();
    seedExistingApis(entity, "DELETE /v1/pets/{id}", new HashMap<>());

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getOldKeys()).contains("DELETE /v1/pets/{id}");
    assertThat(outcome.getNewKeys()).containsExactlyInAnyOrder("GET /v1/pets", "POST /v1/pets");
  }

  // Hash-skip loop-breaker

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashSkip_unchangedSpec_skipsParseAndPreservesPaths() {
    // Prior success with a matching specHash must not re-parse, re-extract, or rewrite paths.
    CatalogEntity entity = newEntity();
    Map<String, Object> enrichments = new LinkedHashMap<>();
    enrichments.put("riskScore", 7.5);
    seedExistingApisWithStatus(entity, "GET /v1/pets", enrichments, SpecHasher.hash(PETSTORE_YAML), "success");

    Map<String, Object> apisBefore = readApisFromDecorator(entity);
    Object pathsBefore = apisBefore.get("paths");
    Object extractedAtBefore = apisBefore.get("extractedAt");

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.HASH_SKIPPED);
    // Hash-skip: newKeys empty, oldKeys carries the current state.
    assertThat(outcome.getOldKeys()).contains("GET /v1/pets");
    assertThat(outcome.getNewKeys()).isEmpty();

    Map<String, Object> apisAfter = readApisFromDecorator(entity);
    // Same paths object — no rewrite happened.
    assertThat(apisAfter.get("paths")).isSameAs(pathsBefore);
    // extractedAt unchanged; only lastCheckedAt rolls.
    assertThat(apisAfter.get("extractedAt")).isEqualTo(extractedAtBefore);
    assertThat(apisAfter.get("lastCheckedAt")).isNotNull();
    assertThat(apisAfter.get("specHash")).isEqualTo(SpecHasher.hash(PETSTORE_YAML));
    assertThat(apisAfter.get("extractionStatus")).isEqualTo("success");
    verify(repository).save(entity);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashSkip_changedSpec_runsFullPipeline() {
    // A stale specHash must not short-circuit; the full pipeline runs.
    CatalogEntity entity = newEntity();
    seedExistingApisWithStatus(
        entity, "GET /v1/old-route", new HashMap<>(), "stale-hash-from-a-previous-version", "success");

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).doesNotContainKey("GET /v1/old-route");
    assertThat(paths).containsKeys("GET /v1/pets", "POST /v1/pets");
    assertThat(apis.get("specHash")).isEqualTo(SpecHasher.hash(PETSTORE_YAML));
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashSkip_priorFailureStatus_doesNotShortCircuit() {
    // A "failed" prior state never persisted endpoints, so a matching hash must still run the pipeline.
    CatalogEntity entity = newEntity();
    seedExistingApisWithStatus(entity, /*key*/ null, /*enrich*/ null, SpecHasher.hash(PETSTORE_YAML), "failed");

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKeys("GET /v1/pets", "POST /v1/pets");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void hashSkip_neverProcessedEntity_doesNotShortCircuit() {
    // First-ever processing: no specHash to match, so the full pipeline runs.
    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKeys("GET /v1/pets", "POST /v1/pets");
  }

  // Score recompute (fires only when the endpoint key-set changes)

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_firstEverExtraction_firesRecompute() {
    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);

    processor.processEntity(entity);

    verify(catalogServiceHelper, times(1)).publishAsyncComputationEvent(eq("acc1"), eq(null), anyString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void recompute_keySetUnchanged_noRecompute() {
    // No prior specHash, so the full pipeline runs; the merged key-set matches, so no recompute.
    CatalogEntity entity = newEntity();
    seedExistingApis(entity, "GET /v1/pets", new LinkedHashMap<>());
    // Append the second key so both prior keys match the new spec.
    Map<String, Object> apis =
        (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) entity.getDecorator().get(PROCESSED_DATA))
                                   .get("metadata"))
            .get("apis");
    Map<String, Object> paths = (Map<String, Object>) apis.get("paths");
    Map<String, Object> postEndpoint = new LinkedHashMap<>();
    postEndpoint.put("path", "/some-old-path");
    postEndpoint.put("method", "POST");
    postEndpoint.put("enrichments", new LinkedHashMap<>());
    paths.put("POST /v1/pets", postEndpoint);

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    processor.processEntity(entity);

    verify(catalogServiceHelper, never()).publishAsyncComputationEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_endpointAdded_firesRecompute() {
    // Prior decorator has one of two spec endpoints; extraction adds the other.
    CatalogEntity entity = newEntity();
    seedExistingApis(entity, "GET /v1/pets", new LinkedHashMap<>());
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);

    processor.processEntity(entity);

    verify(catalogServiceHelper, times(1)).publishAsyncComputationEvent(eq("acc1"), eq(null), anyString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_endpointRemoved_firesRecompute() {
    // Prior endpoint absent from the new spec is hard-deleted, changing the key-set.
    CatalogEntity entity = newEntity();
    seedExistingApis(entity, "DELETE /v1/pets/{id}", new LinkedHashMap<>());
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);

    processor.processEntity(entity);

    verify(catalogServiceHelper, times(1)).publishAsyncComputationEvent(eq("acc1"), eq(null), anyString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_hashSkipPath_noRecompute() {
    // Hash-skip early-returns before reaching the recompute hook.
    CatalogEntity entity = newEntity();
    seedExistingApisWithStatus(
        entity, "GET /v1/pets", new LinkedHashMap<>(), SpecHasher.hash(PETSTORE_YAML), "success");

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.HASH_SKIPPED);
    verify(catalogServiceHelper, never()).publishAsyncComputationEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_failurePath_noRecompute() {
    // Failure only updates status/error fields, so the key-set is unchanged.
    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenThrow(new SpecFetchException("HTTP 404"));

    processor.processEntity(entity);

    verify(catalogServiceHelper, never()).publishAsyncComputationEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void recompute_publishFails_doesNotRollback() {
    // A recompute publish failure must not roll back the extraction.
    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
        .when(catalogServiceHelper)
        .publishAsyncComputationEvent(anyString(), any(), anyString());

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    verify(repository).save(entity);
  }

  // Swagger 2.0 routing

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void swagger2SpecIsRoutedToSwagger2Extractor() {
    String swagger2Yaml = "swagger: '2.0'\n"
        + "info:\n  title: Test\n  version: 1.0.0\n"
        + "host: api.example.com\n"
        + "basePath: /v1\n"
        + "schemes:\n  - https\n"
        + "paths:\n"
        + "  /pets:\n"
        + "    get:\n"
        + "      operationId: listPets\n"
        + "      summary: List pets\n"
        + "      responses:\n"
        + "        '200':\n"
        + "          description: ok\n";

    CatalogEntity entity = newEntity();
    when(specSourceResolver.resolve(entity)).thenReturn(swagger2Yaml);

    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    assertThat(apis.get("protocol")).isEqualTo("swagger");
    assertThat(apis.get("version")).isEqualTo("2.0");
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    assertThat(paths).containsKey("GET /v1/pets");
    assertThat(paths.get("GET /v1/pets").get("operationId")).isEqualTo("listPets");
  }

  // isSwaggerV2 detection

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void isSwaggerV2_truthTable() {
    assertThat(ApiEndpointProcessor.isSwaggerV2("swagger: '2.0'\ninfo:\n  title: T\n")).isTrue();
    assertThat(ApiEndpointProcessor.isSwaggerV2("{\"swagger\":\"2.0\",\"info\":{}}")).isTrue();
    assertThat(ApiEndpointProcessor.isSwaggerV2("openapi: 3.0.1\ninfo:\n  title: T\n")).isFalse();
    assertThat(ApiEndpointProcessor.isSwaggerV2("{\"openapi\":\"3.0.1\"}")).isFalse();
    assertThat(ApiEndpointProcessor.isSwaggerV2("not valid yaml at all [[[")).isFalse();
    assertThat(ApiEndpointProcessor.isSwaggerV2(null)).isFalse();
    assertThat(ApiEndpointProcessor.isSwaggerV2("")).isFalse();
  }

  // keySetChanged truth table

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void keySetChanged_truthTable() {
    // Same keys, any order.
    assertThat(ApiEndpointProcessor.keySetChanged(
                   java.util.List.of("GET /a", "POST /b"), java.util.List.of("POST /b", "GET /a")))
        .isFalse();
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.List.of("GET /a"), java.util.List.of("GET /a", "POST /b")))
        .isTrue();
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.List.of("GET /a", "POST /b"), java.util.List.of("GET /a")))
        .isTrue();
    // Rename.
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.List.of("GET /a"), java.util.List.of("GET /b"))).isTrue();
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.Collections.emptyList(), java.util.Collections.emptyList()))
        .isFalse();
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.Collections.emptyList(), java.util.List.of("GET /a")))
        .isTrue();
    // Null-safe.
    assertThat(ApiEndpointProcessor.keySetChanged(null, java.util.List.of("GET /a"))).isTrue();
    assertThat(ApiEndpointProcessor.keySetChanged(java.util.List.of("GET /a"), null)).isTrue();
    assertThat(ApiEndpointProcessor.keySetChanged(null, null)).isFalse();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void malformedDecoratorDoesNotPoisonMessage() {
    // A non-map _processed_data must be treated as "no prior state", not crash and re-churn.
    CatalogEntity entity = newEntity();
    Map<String, Object> decorator = new java.util.HashMap<>();
    decorator.put(PROCESSED_DATA, "not-a-map-anymore");
    entity.setDecorator(decorator);

    when(specSourceResolver.resolve(entity)).thenReturn(PETSTORE_YAML);
    ProcessingOutcome outcome = processor.processEntity(entity);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(entity);
    assertThat(apis).isNotNull();
    assertThat(apis.get("extractionStatus")).isEqualTo("success");
  }

  // Lost-update: re-read under lock

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void processEntity_reloadsUnderLock_preservesFreshEnrichmentsNotStaleSnapshot() {
    // Stale snapshot must not overwrite a concurrent CCP enrichment on the fresh DB document.
    CatalogEntity stale = newEntity("stale_api");
    Map<String, Object> staleEnrichments = new LinkedHashMap<>();
    staleEnrichments.put("riskScore", 1.0);
    seedExistingApis(stale, "GET /v1/pets", staleEnrichments);

    CatalogEntity fresh = new InlineCatalogEntity();
    fresh.setAccountIdentifier("acc1");
    fresh.setIdentifier("stale_api");
    fresh.setKind("api");
    fresh.setParentUniqueId("parent-1");
    Map<String, Object> freshEnrichments = new LinkedHashMap<>();
    freshEnrichments.put("riskScore", 9.9);
    freshEnrichments.put("owner", "payments");
    seedExistingApis(fresh, "GET /v1/pets", freshEnrichments);

    when(repository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), eq("stale_api")))
        .thenReturn(Optional.of(fresh));
    when(specSourceResolver.resolve(fresh)).thenReturn(PETSTORE_YAML);

    ProcessingOutcome outcome = processor.processEntity(stale);

    assertThat(outcome.getStatus()).isEqualTo(Status.SUCCESS);
    Map<String, Object> apis = readApisFromDecorator(fresh);
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) apis.get("paths");
    Map<String, Object> enrichments = (Map<String, Object>) paths.get("GET /v1/pets").get("enrichments");
    assertThat(enrichments.get("riskScore")).isEqualTo(9.9);
    assertThat(enrichments.get("owner")).isEqualTo("payments");
    verify(repository).save(fresh);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void processEntity_entityDeletedUnderLock_skipsWithoutSave() {
    CatalogEntity stale = newEntity();
    when(repository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), eq("payment_service")))
        .thenReturn(Optional.empty());

    ProcessingOutcome outcome = processor.processEntity(stale);

    assertThat(outcome.getStatus()).isEqualTo(Status.FAILED);
    assertThat(outcome.getErrorMessage()).contains("no longer exists");
    verify(repository, never()).save(any());
    verify(specSourceResolver, never()).resolve(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void processEntity_transfersCallerSpecDecoratorOntoFreshEntity() {
    CatalogEntity stale = newEntity("git_api");
    Map<String, Object> staleSpecDef = new LinkedHashMap<>();
    staleSpecDef.put("$yaml", "refreshed-from-git-openapi-content");
    Map<String, Object> staleSpec = new LinkedHashMap<>();
    staleSpec.put("definition", staleSpecDef);
    Map<String, Object> staleDecorator = new HashMap<>();
    staleDecorator.put("spec", staleSpec);
    stale.setDecorator(staleDecorator);

    CatalogEntity fresh = new InlineCatalogEntity();
    fresh.setAccountIdentifier("acc1");
    fresh.setIdentifier("git_api");
    fresh.setKind("api");
    fresh.setParentUniqueId("parent-1");
    seedExistingApis(fresh, "GET /v1/pets", new LinkedHashMap<>());

    when(repository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), eq("git_api")))
        .thenReturn(Optional.of(fresh));
    when(specSourceResolver.resolve(fresh)).thenReturn(PETSTORE_YAML);

    processor.processEntity(stale);

    Map<String, Object> freshDecorator = fresh.getDecorator();
    assertThat(freshDecorator).containsKey("spec");
    Map<String, Object> transferredSpec = (Map<String, Object>) freshDecorator.get("spec");
    Map<String, Object> transferredDef = (Map<String, Object>) transferredSpec.get("definition");
    assertThat(transferredDef.get("$yaml")).isEqualTo("refreshed-from-git-openapi-content");
    assertThat(readApisFromDecorator(fresh)).isNotNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void transferSpecDecorator_sameInstance_isNoOp() {
    CatalogEntity entity = newEntity();
    Map<String, Object> decorator = new HashMap<>();
    decorator.put("spec", Map.of("definition", Map.of("$yaml", "x")));
    entity.setDecorator(decorator);
    ApiEndpointProcessor.transferSpecDecorator(entity, entity);
    assertThat(entity.getDecorator().get("spec")).isEqualTo(decorator.get("spec"));
  }

  // --- helpers ---

  private CatalogEntity newEntity() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier("acc1");
    entity.setIdentifier("payment_service");
    entity.setKind("api");
    entity.setParentUniqueId("parent-1");
    when(repository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), eq(entity.getIdentifier())))
        .thenReturn(Optional.of(entity));
    return entity;
  }

  private CatalogEntity newEntity(String identifier) {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier("acc1");
    entity.setIdentifier(identifier);
    entity.setKind("api");
    entity.setParentUniqueId("parent-1");
    when(repository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), eq(identifier)))
        .thenReturn(Optional.of(entity));
    return entity;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readApisFromDecorator(CatalogEntity entity) {
    Map<String, Object> decorator = entity.getDecorator();
    if (decorator == null) {
      return null;
    }
    Map<String, Object> processed = (Map<String, Object>) decorator.get(PROCESSED_DATA);
    if (processed == null) {
      return null;
    }
    Map<String, Object> metadata = (Map<String, Object>) processed.get("metadata");
    if (metadata == null) {
      return null;
    }
    return (Map<String, Object>) metadata.get("apis");
  }

  /** Seeds the entity's decorator with a single endpoint and its enrichments under the given key. */
  private static void seedExistingApis(CatalogEntity entity, String endpointKey, Map<String, Object> enrichments) {
    Map<String, Object> endpoint = new LinkedHashMap<>();
    endpoint.put("path", "/some-old-path");
    endpoint.put("method", endpointKey.split(" ")[0]);
    endpoint.put("enrichments", enrichments);

    Map<String, Map<String, Object>> paths = new LinkedHashMap<>();
    paths.put(endpointKey, endpoint);

    Map<String, Object> apis = new LinkedHashMap<>();
    apis.put("paths", paths);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("apis", apis);

    Map<String, Object> processedData = new LinkedHashMap<>();
    processedData.put("metadata", metadata);

    Map<String, Object> decorator = new HashMap<>();
    decorator.put(PROCESSED_DATA, processedData);
    entity.setDecorator(decorator);
  }

  /**
   * Variant of {@link #seedExistingApis} that also stamps specHash, extractionStatus and
   * extractedAt (the fields hash-skip inspects). A null {@code endpointKey} seeds no paths entry.
   */
  private static void seedExistingApisWithStatus(CatalogEntity entity, String endpointKey,
      Map<String, Object> enrichments, String specHash, String extractionStatus) {
    Map<String, Map<String, Object>> paths = new LinkedHashMap<>();
    if (endpointKey != null) {
      Map<String, Object> endpoint = new LinkedHashMap<>();
      endpoint.put("path", "/seeded-path");
      endpoint.put("method", endpointKey.split(" ")[0]);
      endpoint.put("enrichments", enrichments == null ? new LinkedHashMap<>() : enrichments);
      paths.put(endpointKey, endpoint);
    }

    Map<String, Object> apis = new LinkedHashMap<>();
    apis.put("paths", paths);
    apis.put("specHash", specHash);
    apis.put("extractionStatus", extractionStatus);
    apis.put("extractedAt", 1000L); // Sentinel the hash-skip test checks for non-mutation.
    apis.put("lastCheckedAt", 1000L);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("apis", apis);

    Map<String, Object> processedData = new LinkedHashMap<>();
    processedData.put("metadata", metadata);

    Map<String, Object> decorator = new HashMap<>();
    decorator.put(PROCESSED_DATA, processedData);
    entity.setDecorator(decorator);
  }
}
