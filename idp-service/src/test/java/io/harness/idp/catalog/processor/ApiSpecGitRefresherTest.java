/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ApiSpecGitRefresherTest extends CategoryTest {
  private PlaceholderProcessor placeholderProcessor;
  private ApiSpecGitRefresher refresher;

  @Before
  public void setUp() {
    placeholderProcessor = mock(PlaceholderProcessor.class);
    refresher = new ApiSpecGitRefresher(placeholderProcessor);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testHasGitPlaceholderDefinition_detectsPlaceholdersAndIgnoresOthers() {
    assertThat(ApiSpecGitRefresher.hasGitPlaceholderDefinition(placeholderEntity("$yaml"))).isTrue();
    assertThat(ApiSpecGitRefresher.hasGitPlaceholderDefinition(placeholderEntity("$json"))).isTrue();
    assertThat(ApiSpecGitRefresher.hasGitPlaceholderDefinition(placeholderEntity("$text"))).isTrue();

    CatalogEntity bareUrl = apiEntity("u");
    Map<String, Object> urlSpec = new LinkedHashMap<>();
    urlSpec.put("definition", "https://example.com/openapi.yaml");
    bareUrl.setSpec(urlSpec);
    assertThat(ApiSpecGitRefresher.hasGitPlaceholderDefinition(bareUrl)).isFalse();

    assertThat(ApiSpecGitRefresher.hasGitPlaceholderDefinition(apiEntity("n"))).isFalse();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRefresh_noPlaceholder_isNoOp() {
    CatalogEntity bareUrl = apiEntity("u");
    Map<String, Object> urlSpec = new LinkedHashMap<>();
    urlSpec.put("definition", "https://example.com/openapi.yaml");
    bareUrl.setSpec(urlSpec);

    refresher.refresh(bareUrl, false);
    refresher.refresh(bareUrl, true);

    verify(placeholderProcessor, never()).process(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRefresh_placeholderResolvesToDifferentContent_mergesDecorator() {
    CatalogEntity entity = placeholderEntity("$yaml");
    when(placeholderProcessor.process(entity)).thenReturn("resolved-different-yaml");
    Map<String, Object> placeholdersDecorator = new LinkedHashMap<>();
    placeholdersDecorator.put("spec", Map.of("definition", Map.of("$yaml", "resolved-different-yaml")));
    when(placeholderProcessor.getPlaceholdersDecorator(eq(entity.getYaml()), eq("resolved-different-yaml")))
        .thenReturn(placeholdersDecorator);

    refresher.refresh(entity, false);

    assertThat(entity.getDecorator()).containsKey("spec");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRefresh_placeholderResolvesToSameContent_noDecoratorMerge() {
    CatalogEntity entity = placeholderEntity("$text");
    when(placeholderProcessor.process(entity)).thenReturn(entity.getYaml());

    refresher.refresh(entity, false);

    verify(placeholderProcessor, never()).getPlaceholdersDecorator(any(), any());
    assertThat(entity.getDecorator()).isNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRefresh_fetchThrows_propagateErrorsFalse_isSwallowed() {
    CatalogEntity entity = placeholderEntity("$json");
    when(placeholderProcessor.process(entity)).thenThrow(new RuntimeException("git down"));

    refresher.refresh(entity, false);

    assertThat(entity.getDecorator()).isNull();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testRefresh_fetchThrows_propagateErrorsTrue_rethrows() {
    CatalogEntity entity = placeholderEntity("$json");
    when(placeholderProcessor.process(entity)).thenThrow(new RuntimeException("git down"));

    assertThatThrownBy(() -> refresher.refresh(entity, true))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("git down");
  }

  private static CatalogEntity apiEntity(String identifier) {
    return InlineCatalogEntity.builder().accountIdentifier("account-A").kind("api").identifier(identifier).build();
  }

  private static CatalogEntity placeholderEntity(String placeholderKey) {
    CatalogEntity entity = apiEntity("git-api");
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put(placeholderKey, "https://github.com/my-org/specs/blob/main/openapi.yaml");
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("definition", definition);
    entity.setSpec(spec);
    entity.setYaml("apiVersion: harness.io/v1\nkind: API\nidentifier: git-api\nspec:\n  definition:\n    "
        + placeholderKey + ": https://github.com/my-org/specs/blob/main/openapi.yaml\n");
    return entity;
  }
}
