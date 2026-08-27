/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ProcessedServiceResultTest {
  // ===================== getArtifactMap tests =====================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactMapWithData() {
    Map<String, Object> artifactData = new HashMap<>();
    artifactData.put("primary", "docker-image");
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.ARTIFACTS_KEY, artifactData);

    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getArtifactMap())
        .as("Should return artifact data when key exists with non-null value")
        .isEqualTo(artifactData);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactMapWithNullServiceOutputMap() {
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(null).build();
    assertThat(result.getArtifactMap()).as("Should return empty map when serviceOutputMap is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactMapWithMissingKey() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getArtifactMap()).as("Should return empty map when artifacts key is missing").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactMapWithNullEntry() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.ARTIFACTS_KEY, null);
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getArtifactMap()).as("Should return empty map when artifacts value is null").isEmpty();
  }

  // ===================== getManifestMap tests =====================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetManifestMapWithData() {
    Map<String, Object> manifestData = new HashMap<>();
    manifestData.put("primary", "k8s-manifest");
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.MANIFESTS_KEY, manifestData);

    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getManifestMap())
        .as("Should return manifest data when key exists with non-null value")
        .isEqualTo(manifestData);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetManifestMapWithNullServiceOutputMap() {
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(null).build();
    assertThat(result.getManifestMap()).as("Should return empty map when serviceOutputMap is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetManifestMapWithMissingKey() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getManifestMap()).as("Should return empty map when manifests key is missing").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetManifestMapWithNullEntry() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.MANIFESTS_KEY, null);
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getManifestMap()).as("Should return empty map when manifests value is null").isEmpty();
  }

  // ===================== getConfigFileMap tests =====================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetConfigFileMapWithData() {
    Map<String, Object> configFileData = new HashMap<>();
    configFileData.put("config1", "content");
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.CONFIG_FILES_KEY, configFileData);

    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getConfigFileMap())
        .as("Should return config file data when key exists with non-null value")
        .isEqualTo(configFileData);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetConfigFileMapWithNullServiceOutputMap() {
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(null).build();
    assertThat(result.getConfigFileMap()).as("Should return empty map when serviceOutputMap is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetConfigFileMapWithMissingKey() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getConfigFileMap()).as("Should return empty map when configFiles key is missing").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetConfigFileMapWithNullEntry() {
    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.CONFIG_FILES_KEY, null);
    ProcessedServiceResult result = ProcessedServiceResult.builder().serviceOutputMap(serviceOutputMap).build();
    assertThat(result.getConfigFileMap()).as("Should return empty map when configFiles value is null").isEmpty();
  }

  // ===================== Constants tests =====================

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testConstants() {
    assertThat(ProcessedServiceResult.ARTIFACTS_KEY).as("ARTIFACTS_KEY should be 'artifacts'").isEqualTo("artifacts");
    assertThat(ProcessedServiceResult.MANIFESTS_KEY).as("MANIFESTS_KEY should be 'manifests'").isEqualTo("manifests");
    assertThat(ProcessedServiceResult.CONFIG_FILES_KEY)
        .as("CONFIG_FILES_KEY should be 'configFiles'")
        .isEqualTo("configFiles");
  }
}
