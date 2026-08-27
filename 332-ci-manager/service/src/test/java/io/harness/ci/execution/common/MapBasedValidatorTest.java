/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MapBasedValidatorTest {
  private MapBasedValidator mapBasedValidator;

  @Before
  public void setUp() {
    mapBasedValidator = new MapBasedValidator();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateManifestMapWithNull() {
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Manifest map cannot be null");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateManifestMapMissingId() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("uses", "k8s_github");
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(manifestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("'id'");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateManifestMapMissingUses() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("id", "manifest1");
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(manifestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("'uses'");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateK8sGithubManifestMissingInputs() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("id", "manifest1");
    manifestMap.put("uses", "k8s_github");
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(manifestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("K8s GitHub manifest must have inputs map populated");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateK8sGithubManifestMissingConnector() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("id", "manifest1");
    manifestMap.put("uses", "k8s_github");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("repo", "my-repo");
    manifestMap.put("inputs", inputs);
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(manifestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("'connector'");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateK8sGithubManifestValid() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("id", "manifest1");
    manifestMap.put("uses", "k8s_github");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("connector", "my-connector");
    manifestMap.put("inputs", inputs);
    mapBasedValidator.validateManifestMap(manifestMap);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateHelmGithubManifestMissingConnector() {
    Map<String, Object> manifestMap = new HashMap<>();
    manifestMap.put("id", "manifest1");
    manifestMap.put("uses", "helm_github");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("repo", "my-repo");
    manifestMap.put("inputs", inputs);
    assertThatThrownBy(() -> mapBasedValidator.validateManifestMap(manifestMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("'connector'");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateArtifactMapWithNull() {
    assertThatThrownBy(() -> mapBasedValidator.validateArtifactMap(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Artifact map cannot be null");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateDockerRegistryArtifactMissingInputs() {
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("id", "artifact1");
    artifactMap.put("uses", "docker-registry");
    assertThatThrownBy(() -> mapBasedValidator.validateArtifactMap(artifactMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Docker registry artifact must have inputs map populated");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateDockerRegistryArtifactMissingFields() {
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("id", "artifact1");
    artifactMap.put("uses", "docker-registry");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("imagePath", "my/image");
    artifactMap.put("inputs", inputs);
    assertThatThrownBy(() -> mapBasedValidator.validateArtifactMap(artifactMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("'artifactConnector'");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateConfigFileMapWithNull() {
    assertThatThrownBy(() -> mapBasedValidator.validateConfigFileMap(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Config file map cannot be null");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateConfigFileMapValid() {
    Map<String, Object> configFileMap = new HashMap<>();
    configFileMap.put("id", "config1");
    mapBasedValidator.validateConfigFileMap(configFileMap);
  }
}
