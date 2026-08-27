/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.KebabCaseExpressionsUtility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper for building NG service YAML spec map from resolved v0 service YAML.
 * Used for template expression resolution (ngService.*).
 */
@Singleton
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class NgServiceYamlHelper {
  private static final String SERVICE = "service";
  private static final String SERVICE_DEFINITION = "serviceDefinition";
  private static final String SPEC = "spec";
  private static final String ARTIFACTS = "artifacts";
  private static final String MANIFESTS = "manifests";
  private static final String CONFIG_FILES = "configFiles";
  private static final String SIDECARS = "sidecars";
  private static final String SIDECAR = "sidecar";
  private static final String MANIFEST = "manifest";
  private static final String CONFIG_FILE = "configFile";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE = "type";
  private static final String PRIMARY = "primary";
  private static final String SOURCES = "sources";

  /**
   * Inlines the resolved primary artifact source into the v0 service yaml's {@code artifacts.primary} node,
   * mirroring the NG-side transform applied for a fixed ref, so that {@code <+ngServiceYaml.spec.artifacts.primary.*>}
   * behaves identically to the static-ref flow. No-op when there is nothing to resolve (a null id, or a
   * {@code primary} node that is not shaped like an expression-based primaryArtifactRef, i.e. it has no
   * {@code sources} array).
   */
  public String inlineResolvedPrimaryArtifact(String resolvedV0ServiceYaml, String resolvedPrimaryArtifactId) {
    if (isEmpty(resolvedV0ServiceYaml) || isEmpty(resolvedPrimaryArtifactId)) {
      return resolvedV0ServiceYaml;
    }
    try {
      JsonNode rootNode = YamlUtils.readAsJsonNode(resolvedV0ServiceYaml);
      JsonNode specNode = rootNode.path(SERVICE).path(SERVICE_DEFINITION).path(SPEC);
      if (!(specNode instanceof ObjectNode specObjectNode)) {
        return resolvedV0ServiceYaml;
      }
      JsonNode artifactsNode = specObjectNode.path(ARTIFACTS);
      if (!(artifactsNode instanceof ObjectNode artifactsObjectNode)) {
        return resolvedV0ServiceYaml;
      }
      JsonNode primaryNode = artifactsObjectNode.path(PRIMARY);
      if (!(primaryNode instanceof ObjectNode primaryObjectNode) || !primaryObjectNode.path(SOURCES).isArray()) {
        return resolvedV0ServiceYaml;
      }

      ObjectNode resolvedSourceNode = null;
      for (JsonNode source : primaryObjectNode.get(SOURCES)) {
        if (source instanceof ObjectNode sourceObjectNode
            && resolvedPrimaryArtifactId.equals(sourceObjectNode.path(IDENTIFIER).asText(null))) {
          resolvedSourceNode = sourceObjectNode;
          break;
        }
      }
      if (resolvedSourceNode == null) {
        return resolvedV0ServiceYaml;
      }

      resolvedSourceNode.remove(IDENTIFIER);
      artifactsObjectNode.set(PRIMARY, resolvedSourceNode);
      return YamlUtils.writeYamlString(rootNode);
    } catch (Exception e) {
      log.warn("Failed to inline resolved primary artifact into v0 service yaml", e);
      return resolvedV0ServiceYaml;
    }
  }

  /**
   * Drops the losing manifests of the resolved primary's type from the v0 service yaml, mirroring the NG-side
   * filtering applied for a fixed {@code primaryManifestRef} (see PrimaryManifestFilterUtils), so that
   * {@code <+ngServiceYaml.spec.manifests.*>} behaves identically to the static-ref flow. Manifests of other types are
   * untouched. No-op when there is nothing to resolve or the resolved id is not present in the yaml.
   */
  public String filterResolvedPrimaryManifest(String resolvedV0ServiceYaml, String resolvedPrimaryManifestId) {
    if (isEmpty(resolvedV0ServiceYaml) || isEmpty(resolvedPrimaryManifestId)) {
      return resolvedV0ServiceYaml;
    }
    try {
      JsonNode rootNode = YamlUtils.readAsJsonNode(resolvedV0ServiceYaml);
      JsonNode specNode = rootNode.path(SERVICE).path(SERVICE_DEFINITION).path(SPEC);
      if (!(specNode instanceof ObjectNode specObjectNode) || !specObjectNode.path(MANIFESTS).isArray()) {
        return resolvedV0ServiceYaml;
      }
      ArrayNode manifestsArrayNode = (ArrayNode) specObjectNode.get(MANIFESTS);

      String primaryType = null;
      for (JsonNode entry : manifestsArrayNode) {
        JsonNode manifestNode = entry.path(MANIFEST);
        if (resolvedPrimaryManifestId.equals(manifestNode.path(IDENTIFIER).asText(null))) {
          primaryType = manifestNode.path(TYPE).asText(null);
          break;
        }
      }
      if (primaryType == null) {
        return resolvedV0ServiceYaml;
      }

      ArrayNode filteredManifests = manifestsArrayNode.arrayNode();
      for (JsonNode entry : manifestsArrayNode) {
        JsonNode manifestNode = entry.path(MANIFEST);
        boolean isLosingSibling = primaryType.equals(manifestNode.path(TYPE).asText(null))
            && !resolvedPrimaryManifestId.equals(manifestNode.path(IDENTIFIER).asText(null));
        if (!isLosingSibling) {
          filteredManifests.add(entry);
        }
      }
      specObjectNode.set(MANIFESTS, filteredManifests);
      return YamlUtils.writeYamlString(rootNode);
    } catch (Exception e) {
      log.warn("Failed to filter resolved primary manifest in v0 service yaml", e);
      return resolvedV0ServiceYaml;
    }
  }

  /**
   * Builds the v0 service spec as a JSON map for template expression resolution (ngService.*).
   * Extracts service.serviceDefinition.spec and normalizes artifacts.sidecars, manifests, and configFiles to maps keyed
   * by id.
   */
  public Map<String, Object> buildNgServiceYamlSpecMap(String resolvedV0ServiceYaml) {
    try {
      Map<String, Object> serviceMap = YamlUtils.read(resolvedV0ServiceYaml, Map.class);
      if (isEmpty(serviceMap) || !serviceMap.containsKey(SERVICE)) {
        return null;
      }
      @SuppressWarnings("unchecked") Map<String, Object> service = (Map<String, Object>) serviceMap.get(SERVICE);
      if (service == null || !service.containsKey(SERVICE_DEFINITION)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> serviceDefinition = (Map<String, Object>) service.get(SERVICE_DEFINITION);
      if (serviceDefinition == null || !serviceDefinition.containsKey(SPEC)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> spec = new HashMap<>((Map<String, Object>) serviceDefinition.get(SPEC));
      normalizeArtifactsSidecarsToMap(spec);
      normalizeManifestsToMap(spec);
      normaliseConfigFilesToMap(spec);
      return spec;
    } catch (Exception e) {
      log.warn("Failed to build NG service YAML spec map for template resolution", e);
      return null;
    }
  }

  /**
   * Normalizes artifacts.sidecars from list to map keyed by identifier for expression resolution.
   */
  public void normalizeArtifactsSidecarsToMap(Map<String, Object> spec) {
    if (!spec.containsKey(ARTIFACTS)) {
      return;
    }
    @SuppressWarnings("unchecked") Map<String, Object> artifacts = (Map<String, Object>) spec.get(ARTIFACTS);
    if (artifacts == null || !artifacts.containsKey(SIDECARS) || !(artifacts.get(SIDECARS) instanceof List)) {
      return;
    }
    List<?> sidecarsList = (List<?>) artifacts.get(SIDECARS);
    Map<String, Object> sidecarsById = new HashMap<>();
    for (Object item : sidecarsList) {
      if (!(item instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) item;
      if (!entry.containsKey(SIDECAR) || !(entry.get(SIDECAR) instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> sidecar = (Map<String, Object>) entry.get(SIDECAR);
      String id = (String) sidecar.get(IDENTIFIER);
      if (id == null) {
        id = (String) entry.get(IDENTIFIER);
      }
      if (id != null) {
        sidecarsById.put(id, entry);
      }
    }
    artifacts.put(SIDECARS, sidecarsById);
  }

  /**
   * Normalizes manifests from list to map keyed by identifier for expression resolution.
   */
  public void normalizeManifestsToMap(Map<String, Object> spec) {
    if (!spec.containsKey(MANIFESTS) || !(spec.get(MANIFESTS) instanceof List)) {
      return;
    }
    List<?> manifestsList = (List<?>) spec.get(MANIFESTS);
    Map<String, Object> manifestsById = new HashMap<>();
    for (Object item : manifestsList) {
      if (!(item instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) item;
      if (!entry.containsKey(MANIFEST) || !(entry.get(MANIFEST) instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> manifest = (Map<String, Object>) entry.get(MANIFEST);
      String id = (String) manifest.get(IDENTIFIER);
      if (id != null) {
        manifestsById.put(id, entry);
        // Additionally expose the manifest under a kebab->camelCase key so that expression paths like
        // ngService.spec.manifests.<id> (and override equivalents) remain resolvable when the manifest id
        // contains '-'. Purely additive: the raw-id key is preserved and no extra key is added when the id
        // has no '-', so existing behaviour is unchanged.
        String expressionSafeId = KebabCaseExpressionsUtility.capitalizeAfterHyphen(id);
        if (!expressionSafeId.equals(id)) {
          manifestsById.putIfAbsent(expressionSafeId, entry);
        }
      }
    }
    spec.put(MANIFESTS, manifestsById);
  }

  /**
   * Normalizes configFiles from list to map keyed by identifier for expression resolution.
   */
  public void normaliseConfigFilesToMap(Map<String, Object> spec) {
    if (!spec.containsKey(CONFIG_FILES) || !(spec.get(CONFIG_FILES) instanceof List)) {
      return;
    }
    List<?> configFilesList = (List<?>) spec.get(CONFIG_FILES);
    Map<String, Object> configFilesById = new HashMap<>();
    for (Object item : configFilesList) {
      if (!(item instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) item;
      if (!entry.containsKey(CONFIG_FILE) || !(entry.get(CONFIG_FILE) instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked") Map<String, Object> configFile = (Map<String, Object>) entry.get(CONFIG_FILE);
      String id = (String) configFile.get(IDENTIFIER);
      if (id != null) {
        configFilesById.put(id, entry);
      }
    }
    spec.put(CONFIG_FILES, configFilesById);
  }

  /**
   * Builds the override spec map from v0 override YAML for template expression resolution.
   * Extracts overrides section and normalizes manifests and configFiles to maps keyed by id.
   */
  public Map<String, Object> buildOverrideSpecMap(String resolvedV0OverrideYaml) {
    try {
      Map<String, Object> overrideMap = YamlUtils.read(resolvedV0OverrideYaml, Map.class);
      if (isEmpty(overrideMap) || !overrideMap.containsKey(SPEC)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> spec = new HashMap<>((Map<String, Object>) overrideMap.get(SPEC));
      normalizeManifestsToMap(spec);
      normaliseConfigFilesToMap(spec);
      return spec;
    } catch (Exception e) {
      log.warn("Failed to build override spec map for template resolution", e);
      return null;
    }
  }
}
