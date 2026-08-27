/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Validator for map-based entity structures (manifests, artifacts, config files).
 * Used for template-based processing path where entities are represented as maps instead of POJOs.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class MapBasedValidator {
  /**
   * Validate manifest map structure.
   */
  public void validateManifestMap(Map<String, Object> manifestMap) {
    if (manifestMap == null) {
      throw new InvalidRequestException("Manifest map cannot be null");
    }

    // Required fields
    validateRequiredField(manifestMap, "id");
    validateRequiredField(manifestMap, "uses");

    // Type-specific validation
    String uses = (String) manifestMap.get("uses");
    if (isEmpty(uses)) {
      throw new InvalidRequestException("Manifest 'uses' field is required and cannot be empty");
    }

    switch (uses) {
      case "k8s_github":
        validateK8sGithubManifest(manifestMap);
        break;
      case "helm_github":
        validateHelmGithubManifest(manifestMap);
        break;
      default:
        log.debug("No specific validation for manifest type: {}", uses);
        break;
    }
  }

  /**
   * Validate artifact map structure.
   */
  public void validateArtifactMap(Map<String, Object> artifactMap) {
    if (artifactMap == null) {
      throw new InvalidRequestException("Artifact map cannot be null");
    }

    // Required fields
    validateRequiredField(artifactMap, "id");
    validateRequiredField(artifactMap, "uses");

    // Type-specific validation
    String uses = (String) artifactMap.get("uses");
    if (isEmpty(uses)) {
      throw new InvalidRequestException("Artifact 'uses' field is required and cannot be empty");
    }

    switch (uses) {
      case "docker-registry":
        validateDockerRegistryArtifact(artifactMap);
        break;
      default:
        log.debug("No specific validation for artifact type: {}", uses);
        break;
    }
  }

  /**
   * Validate config file map structure.
   */
  public void validateConfigFileMap(Map<String, Object> configFileMap) {
    if (configFileMap == null) {
      throw new InvalidRequestException("Config file map cannot be null");
    }

    // Required fields
    validateRequiredField(configFileMap, "id");
    // Store is optional, but if present should be valid
  }

  /**
   * Validate required field exists and is not empty.
   */
  private void validateRequiredField(Map<String, Object> map, String fieldName) {
    Object value = map.get(fieldName);
    if (value == null || (value instanceof String && isEmpty((String) value))) {
      throw new InvalidRequestException(String.format("Required field '%s' is missing or empty in map", fieldName));
    }
  }

  /**
   * Validate K8s GitHub manifest specific fields.
   */
  private void validateK8sGithubManifest(Map<String, Object> manifestMap) {
    Map<String, Object> inputsMap = (Map<String, Object>) manifestMap.get("inputs");
    if (isEmpty(inputsMap)) {
      throw new InvalidRequestException("K8s GitHub manifest must have inputs map populated");
    }

    // Validate required inputs
    if (isEmpty((String) inputsMap.get("connector"))) {
      throw new InvalidRequestException("K8s GitHub manifest inputs must contain 'connector' field");
    }
  }

  /**
   * Validate Helm GitHub manifest specific fields.
   */
  private void validateHelmGithubManifest(Map<String, Object> manifestMap) {
    Map<String, Object> inputsMap = (Map<String, Object>) manifestMap.get("inputs");
    if (isEmpty(inputsMap)) {
      throw new InvalidRequestException("Helm GitHub manifest must have inputs map populated");
    }

    // Validate required inputs
    if (isEmpty((String) inputsMap.get("connector"))) {
      throw new InvalidRequestException("Helm GitHub manifest inputs must contain 'connector' field");
    }
  }

  /**
   * Validate Docker registry artifact specific fields.
   */
  private void validateDockerRegistryArtifact(Map<String, Object> artifactMap) {
    Map<String, Object> inputsMap = (Map<String, Object>) artifactMap.get("inputs");
    if (isEmpty(inputsMap)) {
      throw new InvalidRequestException("Docker registry artifact must have inputs map populated");
    }

    // Validate required inputs
    if (isEmpty((String) inputsMap.get("artifactConnector"))) {
      throw new InvalidRequestException("Docker registry artifact inputs must contain 'artifactConnector' field");
    }
    if (isEmpty((String) inputsMap.get("imagePath"))) {
      throw new InvalidRequestException("Docker registry artifact inputs must contain 'imagePath' field");
    }
  }
}
