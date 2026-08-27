/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.idp.common.YamlUtils.mergeDecorator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Live-fetches Git placeholder ({@code $yaml/$json/$text}) OpenAPI spec content onto an API
 * entity's decorator, shared between the {@code ApiEndpointRefreshHandler} iterator (which
 * swallows fetch failures to keep last-good content) and the synchronous sync-now flow (which
 * needs failures to propagate so the caller can surface a 500).
 *
 * <p>Does NOT touch the security principal — callers are responsible for running under whatever
 * principal is appropriate for their context (service principal for the iterator, ambient user
 * principal for the synchronous sync flow).
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ApiSpecGitRefresher {
  private static final String DEFINITION_KEY = "definition";
  private static final List<String> PLACEHOLDER_KEYS = List.of("$yaml", "$json", "$text");

  private final PlaceholderProcessor placeholderProcessor;

  @Inject
  public ApiSpecGitRefresher(PlaceholderProcessor placeholderProcessor) {
    this.placeholderProcessor = placeholderProcessor;
  }

  /** True when {@code spec.definition} is a {@code $yaml/$json/$text} placeholder map. */
  @SuppressWarnings("unchecked")
  public static boolean hasGitPlaceholderDefinition(CatalogEntity entity) {
    Map<String, Object> spec = entity.getSpec();
    if (spec == null) {
      return false;
    }
    Object definition = spec.get(DEFINITION_KEY);
    if (!(definition instanceof Map)) {
      return false;
    }
    Map<String, Object> definitionMap = (Map<String, Object>) definition;
    return PLACEHOLDER_KEYS.stream().anyMatch(definitionMap::containsKey);
  }

  /**
   * Re-fetches Git placeholder content into the decorator (same path as entity create/update).
   *
   * @param entity the entity to refresh; its {@code decorator} is mutated in-place on success.
   * @param propagateErrors when {@code true}, fetch failures propagate to the caller (synchronous
   *     sync semantics); when {@code false}, failures are logged and swallowed, keeping the
   *     entity's last-resolved content (iterator semantics).
   */
  public void refresh(CatalogEntity entity, boolean propagateErrors) {
    if (!hasGitPlaceholderDefinition(entity)) {
      return;
    }
    if (propagateErrors) {
      doRefresh(entity);
      return;
    }
    try {
      doRefresh(entity);
    } catch (Exception ex) {
      log.warn("Failed to refresh Git placeholder spec for entity {} — keeping last-resolved content. Error: {}",
          entity.getIdentifier(), ex.getMessage());
    }
  }

  private void doRefresh(CatalogEntity entity) {
    String resolvedYaml = placeholderProcessor.process(entity);
    if (entity.getYaml() != null && entity.getYaml().equals(resolvedYaml)) {
      return;
    }
    Map<String, Object> placeholdersDecorator =
        placeholderProcessor.getPlaceholdersDecorator(entity.getYaml(), resolvedYaml);
    entity.setDecorator(mergeDecorator(entity.getDecorator(), placeholdersDecorator));
  }
}
