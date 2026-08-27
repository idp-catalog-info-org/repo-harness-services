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
import io.harness.idp.catalog.utils.CatalogUtils;

import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing, building, and converting entity references across different formats.
 *
 * This centralizes all entity reference handling logic to avoid duplication and ensure
 * consistency with CatalogUtils conventions.
 *
 * All methods are static utility methods.
 *
 * Format conventions:
 * - Harness format (input parameter): "kind:account[.org[.project]]/identifier"
 * - Backstage format (relations map): "kind:namespace/identifier"
 * - queryableEntityRef format (DB field): "namespace/kind/identifier"
 * - Entity key (visited set): "kind:identifier"
 */
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityRefResolver {
  /**
   * Parses a Harness-format entity reference: "kind:account[.org[.project]]/identifier"
   * Used for parsing the API input parameter queryableEntityRef.
   *
   * @param entityRef the entity reference in Harness format
   * @return ParsedEntityRef containing kind, org, project, and identifier; empty if invalid
   */
  public static Optional<ParsedEntityRef> parseHarnessEntityRef(String entityRef) {
    if (isEmpty(entityRef)) {
      return Optional.empty();
    }

    int colonIdx = entityRef.indexOf(':');
    int slashIdx = entityRef.indexOf('/');
    if (colonIdx < 0 || slashIdx < 0 || slashIdx <= colonIdx) {
      log.warn("Invalid Harness entity ref format: {}", entityRef);
      return Optional.empty();
    }

    String kind = entityRef.substring(0, colonIdx).toLowerCase();
    String scopeSegment = entityRef.substring(colonIdx + 1, slashIdx);
    String identifier = entityRef.substring(slashIdx + 1);

    // Parse scope: scopeParts[0] = "account" (literal), [1] = org, [2] = project
    String[] scopeParts = scopeSegment.split("\\.");
    String org = scopeParts.length > 1 ? scopeParts[1] : null;
    String project = scopeParts.length > 2 ? scopeParts[2] : null;

    return Optional.of(new ParsedEntityRef(kind, org, project, identifier));
  }

  /**
   * Parses a Backstage-format relation reference: "kind:namespace/identifier"
   * Used for parsing entity references from the relations map.
   *
   * For user/group entities, supports implicit account scope:
   * - "group:identifier" → kind="group", namespace="account", identifier="identifier"
   * - "group:account/identifier" → kind="group", namespace="account", identifier="identifier"
   * - "group:account.org/identifier" → kind="group", namespace="account.org", identifier="identifier"
   *
   * Follows the same logic as CatalogEntityRepositoryCustomImpl.getKindScopeIdentifierForUserAndGroup()
   *
   * @param relationRef the relation reference in Backstage format
   * @return BackstageRef containing kind, namespace, and identifier; empty if invalid
   */
  public static Optional<BackstageRef> parseBackstageRelationRef(String relationRef) {
    if (isEmpty(relationRef)) {
      return Optional.empty();
    }

    int colonIdx = relationRef.indexOf(':');
    if (colonIdx < 0) {
      log.warn("Invalid Backstage relation ref format (no colon): {}", relationRef);
      return Optional.empty();
    }

    String kind = relationRef.substring(0, colonIdx).toLowerCase();
    String scopeAndIdentifier = relationRef.substring(colonIdx + 1);

    int slashIdx = scopeAndIdentifier.indexOf('/');
    String namespace;
    String identifier;

    if (slashIdx < 0) {
      // No slash found: "kind:identifier" format
      // For user/group entities, scope defaults to "account"
      namespace = "account";
      identifier = scopeAndIdentifier;
    } else {
      // Slash found: "kind:namespace/identifier" format
      namespace = scopeAndIdentifier.substring(0, slashIdx);
      identifier = scopeAndIdentifier.substring(slashIdx + 1);
    }

    return Optional.of(new BackstageRef(kind, namespace, identifier));
  }

  /**
   * Converts a Backstage-format relation reference to queryableEntityRef format for DB queries.
   * Input: "kind:namespace/identifier" → Output: "namespace/kind/identifier"
   *
   * This is used to construct the queryableEntityRef value for batch fetching from MongoDB.
   *
   * @param backstageRef the relation reference in Backstage format
   * @return queryableEntityRef format string; null if parsing fails
   */
  public static String convertToQueryableEntityRef(String backstageRef) {
    Optional<BackstageRef> parsedOpt = parseBackstageRelationRef(backstageRef);
    if (parsedOpt.isEmpty()) {
      return null;
    }

    BackstageRef parsed = parsedOpt.get();
    return parsed.namespace + "/" + parsed.kind + "/" + parsed.identifier;
  }

  /**
   * Converts a Backstage-format relation reference to queryableEntityRef format, replacing "account" with the actual
   * accountIdentifier. Input: "kind:account[.org[.project]]/identifier" → Output:
   * "accountIdentifier[.org[.project]]/kind/identifier"
   *
   * @param backstageRef the relation reference in Backstage format
   * @param accountIdentifier the actual account identifier to use
   * @return queryableEntityRef format string; null if parsing fails
   */
  public static String convertToQueryableEntityRef(String backstageRef, String accountIdentifier) {
    Optional<BackstageRef> parsedOpt = parseBackstageRelationRef(backstageRef);
    if (parsedOpt.isEmpty()) {
      return null;
    }

    BackstageRef parsed = parsedOpt.get();

    // Replace "account" with actual accountIdentifier in the namespace
    String namespace = parsed.namespace;
    if (namespace.equals("account")) {
      namespace = accountIdentifier;
    } else if (namespace.startsWith("account.")) {
      namespace = accountIdentifier + namespace.substring(7); // Replace "account" prefix
    }

    return namespace + "/" + parsed.kind + "/" + parsed.identifier;
  }

  /**
   * Converts a ParsedEntityRef to queryableEntityRef format for DB queries.
   * Used for fetching the root entity.
   *
   * @param parsed the parsed entity reference
   * @return queryableEntityRef format string
   */
  public static String convertToQueryableEntityRef(ParsedEntityRef parsed) {
    String namespace = "account" + (!isEmpty(parsed.org) ? "." + parsed.org : "")
        + (!isEmpty(parsed.project) ? "." + parsed.project : "");
    return namespace + "/" + parsed.kind + "/" + parsed.identifier;
  }

  /**
   * Builds a Harness-format entity reference by delegating to CatalogUtils.entityRef().
   * This ensures consistency with the existing entity reference conventions.
   *
   * @param entity the catalog entity
   * @return Harness-format entity reference: "kind:account[.org[.project]]/identifier"
   */
  public static String buildEntityRef(CatalogEntity entity) {
    return CatalogUtils.entityRef(entity);
  }

  /**
   * Builds an entity key for visited set tracking and deduplication: "kind:identifier"
   *
   * @param entity the catalog entity
   * @return entity key in format "kind:identifier"
   */
  public static String buildEntityKey(CatalogEntity entity) {
    return entity.getKind().toLowerCase() + ":" + entity.getIdentifier();
  }

  /**
   * Builds an entity key from kind and identifier strings.
   *
   * @param kind the entity kind
   * @param identifier the entity identifier
   * @return entity key in format "kind:identifier"
   */
  public static String buildEntityKey(String kind, String identifier) {
    return kind.toLowerCase() + ":" + identifier;
  }

  /**
   * Represents a parsed Harness-format entity reference.
   * Used for root entity lookup.
   */
  public static class ParsedEntityRef {
    public final String kind;
    public final String org;
    public final String project;
    public final String identifier;

    public ParsedEntityRef(String kind, String org, String project, String identifier) {
      this.kind = kind;
      this.org = org;
      this.project = project;
      this.identifier = identifier;
    }
  }

  /**
   * Represents a parsed Backstage-format relation reference.
   * Used for parsing relations from the entity's relations map.
   */
  public static class BackstageRef {
    public final String kind;
    public final String namespace;
    public final String identifier;

    public BackstageRef(String kind, String namespace, String identifier) {
      this.kind = kind;
      this.namespace = namespace;
      this.identifier = identifier;
    }
  }

  /**
   * Represents a decomposed entity lookup with parentUniqueId for scope resolution.
   * Used for querying MongoDB by (parentUniqueId, kind, identifier) which leverages
   * the unique composite index on those fields.
   */
  public static class ScopedEntityLookup {
    public final String parentUniqueId;
    public final String kind;
    public final String identifier;

    public ScopedEntityLookup(String parentUniqueId, String kind, String identifier) {
      this.parentUniqueId = parentUniqueId;
      this.kind = kind;
      this.identifier = identifier;
    }
  }

  /**
   * Parses a Backstage-format relation reference into a ScopedEntityLookup using the provided
   * namespace resolver function to resolve the namespace to a parentUniqueId.
   *
   * @param relationRef the relation reference in Backstage format
   * @param namespaceResolver function that resolves a namespace string to its parentUniqueId
   * @return ScopedEntityLookup with parentUniqueId; empty if parsing or resolution fails
   */
  public static Optional<ScopedEntityLookup> parseRelationRefToLookup(
      String relationRef, Function<String, String> namespaceResolver) {
    Optional<BackstageRef> backstageRefOpt = parseBackstageRelationRef(relationRef);
    if (backstageRefOpt.isEmpty()) {
      return Optional.empty();
    }

    BackstageRef ref = backstageRefOpt.get();
    String parentUniqueId = namespaceResolver.apply(ref.namespace);
    if (parentUniqueId == null) {
      return Optional.empty();
    }

    return Optional.of(new ScopedEntityLookup(parentUniqueId, ref.kind, ref.identifier));
  }
}
