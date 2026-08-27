/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.BackstageRef;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for extracting and filtering relations from catalog entities during graph traversal.
 *
 * Responsibilities:
 * - Extract relations from frontier entities using entity.getRelationsFor(type)
 * - Apply relationshipType and kind filters
 * - Check visited set to prevent cycles
 * - Convert Backstage refs to queryableEntityRef format
 * - Track metadata for edge construction
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class RelationExtractor {
  public RelationExtractor() {
    // Default constructor for Guice
  }

  /**
   * Extracts relations from frontier entities, filters them, and prepares them for batch fetching.
   *
   * Processing flow:
   * 1. Iterate through frontier entities
   * 2. For each entity, get relations for each requested relationship type
   * 3. Parse each relation ref (Backstage format)
   * 4. Filter by kind if specified
   * 5. Check visited set to skip already-traversed entities
   * 6. Convert to queryableEntityRef format
   * 7. Collect metadata for edge construction (source ref, relation type)
   *
   * @param frontierEntities the current frontier of entities to expand
   * @param relationshipTypes the relationship types to follow (empty = all types)
   * @param entityKinds the entity kinds to include (empty = all kinds)
   * @param visitedKeys the set of already-visited entity keys (kind:identifier)
   * @return RelationBatch containing queryableEntityRefs for DB fetch and edge metadata
   */
  public RelationBatch extractRelations(List<CatalogEntity> frontierEntities, Set<String> relationshipTypes,
      Set<String> entityKinds, Set<String> visitedKeys, String accountIdentifier,
      Function<String, String> namespaceResolver) {
    List<ScopedEntityLookup> scopedEntityLookups = new ArrayList<>();
    List<EdgeDescriptor> edgeDescriptors = new ArrayList<>();
    Map<String, CatalogEntity> keyToSourceEntity = new HashMap<>();
    Set<String> addedLookupKeys = new HashSet<>();

    for (CatalogEntity sourceEntity : frontierEntities) {
      if (sourceEntity.getRelations() == null || sourceEntity.getRelations().isEmpty()) {
        continue;
      }

      String sourceRef = EntityRefResolver.buildEntityRef(sourceEntity);

      // Iterate through all relation types in the entity's relations map
      sourceEntity.getRelations().forEach((relationType, targetRefs) -> {
        // Filter by relationshipType
        if (!relationshipTypes.isEmpty() && !relationshipTypes.contains(relationType)) {
          return;
        }

        // Process each target ref in this relation type
        for (String rawRef : targetRefs) {
          if (isEmpty(rawRef)) {
            continue;
          }

          // Parse Backstage format: "kind:namespace/identifier"
          Optional<BackstageRef> backstageRefOpt = EntityRefResolver.parseBackstageRelationRef(rawRef);
          if (backstageRefOpt.isEmpty()) {
            log.debug("Skipping invalid relation ref: {}", rawRef);
            continue;
          }

          BackstageRef backstageRef = backstageRefOpt.get();

          // Filter by kinds
          if (!entityKinds.isEmpty() && !entityKinds.contains(backstageRef.kind)) {
            continue;
          }

          // Build entity key for visited check
          String entityKey = EntityRefResolver.buildEntityKey(backstageRef.kind, backstageRef.identifier);

          // Check visited set to avoid cycles
          if (visitedKeys.contains(entityKey)) {
            continue;
          }

          // Parse relation ref into scoped lookup using namespace resolver for parentUniqueId resolution
          Optional<ScopedEntityLookup> lookupOpt =
              EntityRefResolver.parseRelationRefToLookup(rawRef, namespaceResolver);
          if (lookupOpt.isEmpty()) {
            log.warn("Failed to parse relation ref: {}", rawRef);
            continue;
          }

          // Only add one DB lookup per unique target entity (dedup for query efficiency)
          if (addedLookupKeys.add(entityKey)) {
            scopedEntityLookups.add(lookupOpt.get());
          }

          // Track every (source, target, relationType) triple for edge construction
          edgeDescriptors.add(new EdgeDescriptor(entityKey, sourceRef, relationType, sourceEntity));
          keyToSourceEntity.putIfAbsent(entityKey, sourceEntity);
        }
      });
    }

    return RelationBatch.builder()
        .scopedEntityLookups(scopedEntityLookups)
        .edgeDescriptors(edgeDescriptors)
        .keyToSourceEntity(keyToSourceEntity)
        .build();
  }

  /**
   * Describes a single edge to be created: source → target with a specific relation type.
   * Multiple EdgeDescriptors can point to the same target entity with different relation types.
   */
  public record EdgeDescriptor(
      String targetEntityKey, String sourceEntityRef, String relationType, CatalogEntity sourceEntity) {}

  /**
   * Result class containing all discovered relations ready for batch fetching.
   */
  @Value
  @Builder
  public static class RelationBatch {
    /**
     * Deduplicated list of scoped entity lookups for batch DB query.
     */
    List<ScopedEntityLookup> scopedEntityLookups;

    /**
     * All edge descriptors — one per (source, target, relationType) triple.
     * Unlike the old maps, this preserves multiple relations to the same target.
     */
    List<EdgeDescriptor> edgeDescriptors;

    /**
     * Map from entity key (kind:identifier) to source CatalogEntity object.
     * Used to populate source entity details (kind, name, type) in GraphEdge.
     */
    Map<String, CatalogEntity> keyToSourceEntity;
  }
}
