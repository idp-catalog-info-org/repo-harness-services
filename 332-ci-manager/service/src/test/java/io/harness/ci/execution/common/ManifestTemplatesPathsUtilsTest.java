/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.NAVTEJPREET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.manifests.ManifestType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestTemplatesPathsUtilsTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsWithPathsKey() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paths", Arrays.asList("path1", "path2"));
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
    assertThat(paths).containsExactly("path1", "path2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsWithValuesKey_returnsEmpty() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("values", Arrays.asList("val1"));
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
    assertThat(paths).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsWithNull() {
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(null);
    assertThat(paths).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsWithStringJsonArray() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paths", "[\"path1\", \"path2\"]");
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
    assertThat(paths).containsExactly("path1", "path2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsWithSingleString() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paths", "single/path");
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
    assertThat(paths).containsExactly("single/path");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOverridesFromInputs() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("overrides", Arrays.asList("override1"));
    List<String> overrides = ManifestTemplatesPathsUtils.getOverridesFromInputs(inputs);
    assertThat(overrides).containsExactly("override1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOverridesFromInputsWithNull() {
    List<String> overrides = ManifestTemplatesPathsUtils.getOverridesFromInputs(null);
    assertThat(overrides).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSupportsFilesToTemplate() {
    assertThat(ManifestTemplatesPathsUtils.supportsFilesToTemplate(ManifestType.K8S)).isTrue();
    assertThat(ManifestTemplatesPathsUtils.supportsFilesToTemplate(ManifestType.HELM_CHART)).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSupportsFilesToRender() {
    assertThat(ManifestTemplatesPathsUtils.supportsFilesToTemplate(ManifestType.K8S)).isTrue();
    assertThat(ManifestTemplatesPathsUtils.supportsFilesToTemplate(ManifestType.KUSTOMIZE)).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFilesToTemplateFromInputsForK8s() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("valuesPaths", Arrays.asList("values.yaml"));
    List<String> files = ManifestTemplatesPathsUtils.getFilesToTemplateFromInputs(ManifestType.K8S, inputs);
    assertThat(files).containsExactly("values.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFilesToTemplateFromInputsForParams() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paramsPaths", Arrays.asList("params.yaml"));
    List<String> files = ManifestTemplatesPathsUtils.getFilesToTemplateFromInputs(ManifestType.OPENSHIFT, inputs);
    assertThat(files).containsExactly("params.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFilesToTemplateFromInputsForPatches() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("patchesPaths", Arrays.asList("patch.yaml"));
    List<String> files = ManifestTemplatesPathsUtils.getFilesToTemplateFromInputs(ManifestType.KUSTOMIZE, inputs);
    assertThat(files).containsExactly("patch.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFilesToTemplateFromInputsWithNull() {
    List<String> files = ManifestTemplatesPathsUtils.getFilesToTemplateFromInputs(ManifestType.K8S, null);
    assertThat(files).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFilesToRenderFromInputs() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("valuesPaths", Arrays.asList("render.yaml"));
    List<String> files = ManifestTemplatesPathsUtils.getFilesToRenderFromInputs(ManifestType.HELM_CHART, inputs);
    assertThat(files).containsExactly("render.yaml");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPathsFromInputsEmptyMap() {
    Map<String, Object> inputs = new HashMap<>();
    List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
    assertThat(paths).isEmpty();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_folderSet_returnsFolder() {
    Map<String, Object> overlay = new HashMap<>();
    overlay.put(
        ManifestTemplateConstants.INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH, "multipleEnv/environments/production/");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, overlay);

    String result = ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs);

    assertThat(result).isEqualTo("multipleEnv/environments/production/");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_overlayJsonString_returnsFolder() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION,
        "{\"kustomizeYamlFolderPath\":\"<+serviceVariables.kustomizeYamlFolderPath>\"}");

    String result = ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs);

    assertThat(result).isEqualTo("<+serviceVariables.kustomizeYamlFolderPath>");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_overlayAbsent_returnsNull() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));

    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs)).isNull();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_folderEmpty_returnsNull() {
    Map<String, Object> overlay = new HashMap<>();
    overlay.put(ManifestTemplateConstants.INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH, "");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, overlay);

    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs)).isNull();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_folderNull_returnsNull() {
    Map<String, Object> overlay = new HashMap<>();
    overlay.put(ManifestTemplateConstants.INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH, null);
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, overlay);

    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs)).isNull();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_overlayNotMap_returnsNull() {
    // Guards against malformed inputs where overlayConfiguration arrives as a scalar.
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, "unexpected-string");

    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs)).isNull();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_malformedOverlayJsonString_returnsNull() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, "{\"kustomizeYamlFolderPath\":");

    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs)).isNull();
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetKustomizeYamlFolderPathFromInputs_nullInputs_returnsNull() {
    assertThat(ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(null)).isNull();
  }
}
