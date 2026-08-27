/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts entity references (connectors, secrets) from map-based entity structures.
 * Used for RBAC validation in template-based processing path.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class MapBasedReferenceExtractor {
  // Pattern to match secret references: secrets.getValue("secretRef")
  private static final Pattern SECRET_REF_PATTERN = Pattern.compile("secrets\\.getValue\\(\"([^\"]+)\"\\)");

  /**
   * Extract entity references from manifest map.
   */
  public Set<EntityDetailProtoDTO> extractReferencesFromManifestMap(
      Map<String, Object> manifestMap, Ambiance ambiance) {
    Set<EntityDetailProtoDTO> references = new HashSet<>();

    if (manifestMap == null) {
      return references;
    }

    // Extract connector reference from store
    Map<String, Object> store = (Map<String, Object>) manifestMap.get("store");
    if (store != null) {
      String connectorRef = extractStringValue(store, "connector");
      if (isNotEmpty(connectorRef) && !isExpression(connectorRef)) {
        references.add(createConnectorReference(connectorRef, ambiance));
      }
    }

    // Extract references from inputs map
    Map<String, Object> inputsMap = (Map<String, Object>) manifestMap.get("inputs");
    if (isNotEmpty(inputsMap)) {
      extractReferencesFromMap(inputsMap, references, ambiance);
    }

    // Extract references from 'with' section if present
    Map<String, Object> withMap = (Map<String, Object>) manifestMap.get("with");
    if (withMap != null) {
      extractReferencesFromMap(withMap, references, ambiance);
    }

    return references;
  }

  /**
   * Extract entity references from artifact map.
   */
  public Set<EntityDetailProtoDTO> extractReferencesFromArtifactMap(
      Map<String, Object> artifactMap, Ambiance ambiance) {
    Set<EntityDetailProtoDTO> references = new HashSet<>();

    if (artifactMap == null) {
      return references;
    }

    // Extract connector reference
    String connectorRef = extractStringValue(artifactMap, "connector");
    if (isEmpty(connectorRef)) {
      // Try from inputs map
      Map<String, Object> inputsMap = (Map<String, Object>) artifactMap.get("inputs");
      if (isNotEmpty(inputsMap)) {
        connectorRef = extractStringValue(inputsMap, "artifactConnector");
      }
    }

    if (isNotEmpty(connectorRef) && !isExpression(connectorRef)) {
      references.add(createConnectorReference(connectorRef, ambiance));
    }

    // Extract references from inputs map
    Map<String, Object> inputsMap = (Map<String, Object>) artifactMap.get("inputs");
    if (isNotEmpty(inputsMap)) {
      extractReferencesFromMap(inputsMap, references, ambiance);
    }

    // Extract references from 'with' section if present
    Map<String, Object> withMap = (Map<String, Object>) artifactMap.get("with");
    if (isNotEmpty(withMap)) {
      extractReferencesFromMap(withMap, references, ambiance);
    }

    return references;
  }

  /**
   * Extract entity references from config file map.
   */
  public Set<EntityDetailProtoDTO> extractReferencesFromConfigFileMap(
      Map<String, Object> configFileMap, Ambiance ambiance) {
    Set<EntityDetailProtoDTO> references = new HashSet<>();

    if (configFileMap == null) {
      return references;
    }

    // Extract connector reference from store
    Map<String, Object> store = (Map<String, Object>) configFileMap.get("store");
    if (store != null) {
      String connectorRef = extractStringValue(store, "connector");
      if (isNotEmpty(connectorRef) && !isExpression(connectorRef)) {
        references.add(createConnectorReference(connectorRef, ambiance));
      }
    }

    // Extract references from inputs map
    Map<String, Object> inputsMap = (Map<String, Object>) configFileMap.get("inputs");
    if (isNotEmpty(inputsMap)) {
      extractReferencesFromMap(inputsMap, references, ambiance);
    }

    return references;
  }

  /**
   * Recursively extract references from a map structure.
   */
  private void extractReferencesFromMap(
      Map<String, Object> map, Set<EntityDetailProtoDTO> references, Ambiance ambiance) {
    if (map == null) {
      return;
    }

    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      if (value == null) {
        continue;
      }

      // Check for connector references
      if (entry.getKey().equalsIgnoreCase("connector") && value instanceof String) {
        String connectorRef = (String) value;
        if (isNotEmpty(connectorRef) && !isExpression(connectorRef)) {
          references.add(createConnectorReference(connectorRef, ambiance));
        }
      }

      // Check for secret references
      if (value instanceof String) {
        extractSecretReferences((String) value, references, ambiance);
      } else if (value instanceof Map) {
        extractReferencesFromMap((Map<String, Object>) value, references, ambiance);
      } else if (value instanceof List) {
        extractReferencesFromList((List<Object>) value, references, ambiance);
      }
    }
  }

  /**
   * Recursively extract references from a list structure.
   */
  private void extractReferencesFromList(List<Object> list, Set<EntityDetailProtoDTO> references, Ambiance ambiance) {
    if (list == null) {
      return;
    }

    for (Object item : list) {
      if (item instanceof String) {
        extractSecretReferences((String) item, references, ambiance);
      } else if (item instanceof Map) {
        extractReferencesFromMap((Map<String, Object>) item, references, ambiance);
      } else if (item instanceof List) {
        extractReferencesFromList((List<Object>) item, references, ambiance);
      }
    }
  }

  /**
   * Extract secret references from a string value.
   */
  private void extractSecretReferences(String value, Set<EntityDetailProtoDTO> references, Ambiance ambiance) {
    if (isEmpty(value) || isExpression(value)) {
      return;
    }

    // Check for secrets.getValue("secretRef") pattern
    java.util.regex.Matcher matcher = SECRET_REF_PATTERN.matcher(value);
    while (matcher.find()) {
      String secretRef = matcher.group(1);
      if (isNotEmpty(secretRef)) {
        references.add(createSecretReference(secretRef, ambiance));
      }
    }

    // Check for direct secret reference patterns (account.secretName, org.secretName, etc.)
    // Note: This is a heuristic that may need refinement based on actual patterns.
    // For now, we'll rely on the secrets.getValue pattern for secret references.
  }

  /**
   * Create connector reference entity using IdentifierRefHelper for proper parsing.
   */
  private EntityDetailProtoDTO createConnectorReference(String connectorRef, Ambiance ambiance) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

      // Use IdentifierRefHelper to properly parse scoped references (account.connector, org.connector, etc.)
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);

      IdentifierRefProtoDTO identifierRefProto =
          IdentifierRefProtoDTO.newBuilder()
              .setIdentifier(StringValue.of(identifierRef.getIdentifier()))
              .setAccountIdentifier(StringValue.of(accountId))
              .setOrgIdentifier(StringValue.of(orgId != null ? orgId : ""))
              .setProjectIdentifier(StringValue.of(projectId != null ? projectId : ""))
              .build();

      return EntityDetailProtoDTO.newBuilder()
          .setType(EntityTypeProtoEnum.CONNECTORS)
          .setIdentifierRef(identifierRefProto)
          .setName(identifierRef.getIdentifier())
          .build();
    } catch (Exception e) {
      log.warn("Failed to create connector reference for: {}", connectorRef, e);
      return null;
    }
  }

  /**
   * Create secret reference entity using IdentifierRefHelper for proper parsing.
   */
  private EntityDetailProtoDTO createSecretReference(String secretRef, Ambiance ambiance) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

      // Use IdentifierRefHelper to properly parse scoped references (account.secret, org.secret, etc.)
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRef, accountId, orgId, projectId);

      IdentifierRefProtoDTO identifierRefProto =
          IdentifierRefProtoDTO.newBuilder()
              .setIdentifier(StringValue.of(identifierRef.getIdentifier()))
              .setAccountIdentifier(StringValue.of(accountId))
              .setOrgIdentifier(StringValue.of(orgId != null ? orgId : ""))
              .setProjectIdentifier(StringValue.of(projectId != null ? projectId : ""))
              .build();

      return EntityDetailProtoDTO.newBuilder()
          .setType(EntityTypeProtoEnum.SECRETS)
          .setIdentifierRef(identifierRefProto)
          .setName(identifierRef.getIdentifier())
          .build();
    } catch (Exception e) {
      log.warn("Failed to create secret reference for: {}", secretRef, e);
      return null;
    }
  }

  /**
   * Extract string value from map, handling nested paths.
   */
  private String extractStringValue(Map<String, Object> map, String key) {
    if (map == null) {
      return null;
    }
    Object value = map.get(key);
    return value instanceof String ? (String) value : null;
  }

  /**
   * Check if a value is an expression (starts with ${{ or contains expression syntax).
   */
  private boolean isExpression(String value) {
    if (isEmpty(value)) {
      return false;
    }
    return value.trim().startsWith("${{") || value.contains("<+");
  }
}
