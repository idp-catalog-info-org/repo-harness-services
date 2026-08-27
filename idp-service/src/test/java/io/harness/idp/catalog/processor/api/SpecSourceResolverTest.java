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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class SpecSourceResolverTest extends CategoryTest {
  private SpecFetcher specFetcher;
  private SpecSourceResolver resolver;

  @Before
  public void setUp() {
    specFetcher = mock(SpecFetcher.class);
    resolver = new SpecSourceResolver(specFetcher);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void plainUrlStringIsFetched() {
    CatalogEntity entity = entityWithDefinition("https://petstore.swagger.io/v2/swagger.json");
    when(specFetcher.fetch("https://petstore.swagger.io/v2/swagger.json")).thenReturn("openapi: 3.0.1");

    String result = resolver.resolve(entity);

    assertThat(result).isEqualTo("openapi: 3.0.1");
    verify(specFetcher).fetch("https://petstore.swagger.io/v2/swagger.json");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void inlineYamlStringIsReturnedAsIs() {
    String inline = "openapi: 3.0.1\ninfo:\n  title: T\n  version: 1.0.0\n";
    CatalogEntity entity = entityWithDefinition(inline);

    String result = resolver.resolve(entity);

    assertThat(result).contains("openapi: 3.0.1");
    verifyNoInteractions(specFetcher);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void inlineYamlObjectIsSerialised() {
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("openapi", "3.0.1");
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("title", "Test");
    info.put("version", "1.0.0");
    definition.put("info", info);

    CatalogEntity entity = entityWithDefinition(definition);

    String result = resolver.resolve(entity);

    assertThat(result).contains("openapi");
    assertThat(result).contains("3.0.1");
    verifyNoInteractions(specFetcher);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void gitPlaceholderReadsFromActualDecoratorShape() {
    // Resolver must read decorator.spec.definition.$yaml (the shape PlaceholderProcessor writes),
    // where the $yaml key is preserved and there is no _processed_data wrapper.
    Map<String, Object> definition = new HashMap<>();
    definition.put("$yaml", "https://github.com/acme/repo/blob/main/openapi.yaml");
    CatalogEntity entity = entityWithDefinition(definition);

    String resolvedContent = "openapi: 3.0.1\ninfo:\n  title: From Git\n  version: 1.0.0\n";
    seedDecoratorWithPlaceholderResolution(entity, "$yaml", resolvedContent);

    String result = resolver.resolve(entity);

    assertThat(result).isEqualTo(resolvedContent.trim());
    verifyNoInteractions(specFetcher);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void jsonPlaceholderReadsFromActualDecoratorShape() {
    Map<String, Object> definition = new HashMap<>();
    definition.put("$json", "https://gitlab.com/acme/openapi.json");
    CatalogEntity entity = entityWithDefinition(definition);
    seedDecoratorWithPlaceholderResolution(entity, "$json", "{\"openapi\":\"3.0.1\"}");

    String result = resolver.resolve(entity);

    assertThat(result).isEqualTo("{\"openapi\":\"3.0.1\"}");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void gitPlaceholderWithMissingDecoratorContentThrowsActionableError() {
    Map<String, Object> definition = new HashMap<>();
    definition.put("$yaml", "https://github.com/acme/repo/blob/main/openapi.yaml");
    CatalogEntity entity = entityWithDefinition(definition);
    // No decorator content seeded.

    assertThatThrownBy(() -> resolver.resolve(entity))
        .isInstanceOf(SpecResolutionException.class)
        .hasMessageContaining("re-saving the entity should populate it");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void placeholderWithNoResolvedDecoratorContentPointsToPlainUrl() {
    // A placeholder PlaceholderProcessor couldn't resolve (e.g. a non-Git URL with no connector)
    // has no decorator content, so we surface an error pointing the customer at a plain URL string.
    Map<String, Object> definition = new HashMap<>();
    definition.put("$yaml", "https://petstore.swagger.io/v2/swagger.json");
    CatalogEntity entity = entityWithDefinition(definition);

    assertThatThrownBy(() -> resolver.resolve(entity))
        .isInstanceOf(SpecResolutionException.class)
        .hasMessageContaining("use a plain URL string");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void missingDefinitionThrows() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setSpec(new HashMap<>());

    assertThatThrownBy(() -> resolver.resolve(entity))
        .isInstanceOf(SpecResolutionException.class)
        .hasMessageContaining("no spec.definition");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void emptyDefinitionStringThrows() {
    CatalogEntity entity = entityWithDefinition("   ");
    assertThatThrownBy(() -> resolver.resolve(entity)).isInstanceOf(SpecResolutionException.class);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void nullEntityThrows() {
    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(NullPointerException.class);
  }

  // --- helpers ---

  private static CatalogEntity entityWithDefinition(Object definition) {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    Map<String, Object> spec = new HashMap<>();
    spec.put("definition", definition);
    entity.setSpec(spec);
    return entity;
  }

  /**
   * Seeds the decorator with the shape {@code PlaceholderProcessor.getPlaceholdersDecorator}
   * produces: the placeholder key preserved with the resolved content as its value.
   */
  private static void seedDecoratorWithPlaceholderResolution(
      CatalogEntity entity, String placeholderKey, String resolvedContent) {
    Map<String, Object> definitionResolved = new HashMap<>();
    definitionResolved.put(placeholderKey, resolvedContent);

    Map<String, Object> specResolved = new HashMap<>();
    specResolved.put("definition", definitionResolved);

    Map<String, Object> decorator = new HashMap<>();
    decorator.put("spec", specResolved);
    entity.setDecorator(decorator);
  }
}
