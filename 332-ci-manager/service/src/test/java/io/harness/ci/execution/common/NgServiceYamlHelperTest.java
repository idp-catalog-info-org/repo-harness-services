/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NgServiceYamlHelperTest {
  private NgServiceYamlHelper ngServiceYamlHelper;

  @Before
  public void setUp() {
    ngServiceYamlHelper = new NgServiceYamlHelper();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> manifestsList = new ArrayList<>();

    Map<String, Object> manifestEntry = new HashMap<>();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("identifier", "k8s-manifest");
    manifest.put("type", "K8sManifest");
    manifestEntry.put("manifest", manifest);
    manifestsList.add(manifestEntry);

    spec.put("manifests", manifestsList);

    ngServiceYamlHelper.normalizeManifestsToMap(spec);

    assertThat(spec.get("manifests")).isInstanceOf(Map.class);
    Map<String, Object> manifestsMap = (Map<String, Object>) spec.get("manifests");
    assertThat(manifestsMap).containsKey("k8s-manifest");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap_hyphenatedId_alsoExposesCamelCaseKey() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> manifestsList = new ArrayList<>();

    Map<String, Object> manifestEntry = new HashMap<>();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("identifier", "s3-manifest");
    manifest.put("type", "K8sManifest");
    manifestEntry.put("manifest", manifest);
    manifestsList.add(manifestEntry);

    spec.put("manifests", manifestsList);

    ngServiceYamlHelper.normalizeManifestsToMap(spec);

    Map<String, Object> manifestsMap = (Map<String, Object>) spec.get("manifests");
    // Raw id key is preserved (no regression) and a camelCase alias is added so that
    // ngService.spec.manifests.s3Manifest expressions can resolve for hyphenated ids.
    assertThat(manifestsMap).containsKey("s3-manifest");
    assertThat(manifestsMap).containsKey("s3Manifest");
    // Both keys must point to the same underlying manifest entry.
    assertThat(manifestsMap.get("s3Manifest")).isSameAs(manifestsMap.get("s3-manifest"));
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap_nonHyphenId_addsNoExtraKey() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> manifestsList = new ArrayList<>();

    Map<String, Object> manifestEntry = new HashMap<>();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("identifier", "s3Manifest");
    manifest.put("type", "K8sManifest");
    manifestEntry.put("manifest", manifest);
    manifestsList.add(manifestEntry);

    spec.put("manifests", manifestsList);

    ngServiceYamlHelper.normalizeManifestsToMap(spec);

    Map<String, Object> manifestsMap = (Map<String, Object>) spec.get("manifests");
    // No '-' in the id => no additional alias key is created; behaviour is unchanged.
    assertThat(manifestsMap).containsOnlyKeys("s3Manifest");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMapWithNoManifests() {
    Map<String, Object> spec = new HashMap<>();
    ngServiceYamlHelper.normalizeManifestsToMap(spec);
    assertThat(spec).doesNotContainKey("manifests");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap() {
    Map<String, Object> spec = new HashMap<>();
    Map<String, Object> artifacts = new HashMap<>();
    List<Object> sidecarsList = new ArrayList<>();

    Map<String, Object> sidecarEntry = new HashMap<>();
    Map<String, Object> sidecar = new HashMap<>();
    sidecar.put("identifier", "sidecar1");
    sidecar.put("type", "DockerRegistry");
    sidecarEntry.put("sidecar", sidecar);
    sidecarsList.add(sidecarEntry);

    artifacts.put("sidecars", sidecarsList);
    spec.put("artifacts", artifacts);

    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);

    Map<String, Object> updatedArtifacts = (Map<String, Object>) spec.get("artifacts");
    assertThat(updatedArtifacts.get("sidecars")).isInstanceOf(Map.class);
    Map<String, Object> sidecarsMap = (Map<String, Object>) updatedArtifacts.get("sidecars");
    assertThat(sidecarsMap).containsKey("sidecar1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMapWithNoArtifacts() {
    Map<String, Object> spec = new HashMap<>();
    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);
    assertThat(spec).doesNotContainKey("artifacts");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormaliseConfigFilesToMap() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> configFilesList = new ArrayList<>();

    Map<String, Object> configFileEntry = new HashMap<>();
    Map<String, Object> configFile = new HashMap<>();
    configFile.put("identifier", "config1");
    configFile.put("type", "Harness");
    configFileEntry.put("configFile", configFile);
    configFilesList.add(configFileEntry);

    spec.put("configFiles", configFilesList);

    ngServiceYamlHelper.normaliseConfigFilesToMap(spec);

    assertThat(spec.get("configFiles")).isInstanceOf(Map.class);
    Map<String, Object> configFilesMap = (Map<String, Object>) spec.get("configFiles");
    assertThat(configFilesMap).containsKey("config1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormaliseConfigFilesToMapWithNoConfigFiles() {
    Map<String, Object> spec = new HashMap<>();
    ngServiceYamlHelper.normaliseConfigFilesToMap(spec);
    assertThat(spec).doesNotContainKey("configFiles");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMapWithInvalidYaml() {
    Map<String, Object> result = ngServiceYamlHelper.buildNgServiceYamlSpecMap("invalid yaml: [");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMapWithMissingService() {
    Map<String, Object> result = ngServiceYamlHelper.buildNgServiceYamlSpecMap("key: value");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_success() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        sidecars:\n"
        + "          - sidecar:\n"
        + "              identifier: sidecar1\n"
        + "              type: DockerRegistry\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: k8s-manifest\n"
        + "            type: K8sManifest\n"
        + "      configFiles:\n"
        + "        - configFile:\n"
        + "            identifier: config1\n"
        + "            type: Harness\n";

    Map<String, Object> spec = ngServiceYamlHelper.buildNgServiceYamlSpecMap(yaml);
    assertThat(spec).isNotNull();

    Map<String, Object> artifacts = (Map<String, Object>) spec.get("artifacts");
    assertThat(artifacts).isNotNull();
    assertThat(artifacts.get("sidecars")).isInstanceOf(Map.class);
    Map<String, Object> sidecarsMap = (Map<String, Object>) artifacts.get("sidecars");
    assertThat(sidecarsMap).containsKey("sidecar1");

    assertThat(spec.get("manifests")).isInstanceOf(Map.class);
    assertThat((Map<String, Object>) spec.get("manifests")).containsKey("k8s-manifest");

    assertThat(spec.get("configFiles")).isInstanceOf(Map.class);
    assertThat((Map<String, Object>) spec.get("configFiles")).containsKey("config1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_returnsNullWhenRootMapEmpty() {
    assertThat(ngServiceYamlHelper.buildNgServiceYamlSpecMap("{}")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_returnsNullWhenServiceNull() {
    String yaml = "service: null\n";
    assertThat(ngServiceYamlHelper.buildNgServiceYamlSpecMap(yaml)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_returnsNullWhenServiceDefinitionMissing() {
    String yaml = "service:\n"
        + "  name: svc\n";
    assertThat(ngServiceYamlHelper.buildNgServiceYamlSpecMap(yaml)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_returnsNullWhenSpecMissing() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n";
    assertThat(ngServiceYamlHelper.buildNgServiceYamlSpecMap(yaml)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildNgServiceYamlSpecMap_returnsNullWhenServiceDefinitionNull() {
    String yaml = "service:\n"
        + "  serviceDefinition: null\n";
    assertThat(ngServiceYamlHelper.buildNgServiceYamlSpecMap(yaml)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap_sidecarIdFallbackToEntry() {
    Map<String, Object> spec = new HashMap<>();
    Map<String, Object> artifacts = new HashMap<>();
    List<Object> sidecarsList = new ArrayList<>();

    Map<String, Object> sidecarEntry = new HashMap<>();
    Map<String, Object> sidecar = new HashMap<>();
    sidecar.put("type", "DockerRegistry");
    sidecarEntry.put("sidecar", sidecar);
    sidecarEntry.put("identifier", "from-entry");
    sidecarsList.add(sidecarEntry);

    artifacts.put("sidecars", sidecarsList);
    spec.put("artifacts", artifacts);

    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);

    Map<String, Object> sidecarsMap =
        (Map<String, Object>) ((Map<String, Object>) spec.get("artifacts")).get("sidecars");
    assertThat(sidecarsMap).containsKey("from-entry");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap_artifactsNull() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("artifacts", null);
    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);
    assertThat(spec.get("artifacts")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap_noSidecarsKey() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("artifacts", new HashMap<>());
    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);
    assertThat((Map<String, Object>) spec.get("artifacts")).doesNotContainKey("sidecars");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap_sidecarsNotList() {
    Map<String, Object> spec = new HashMap<>();
    Map<String, Object> artifacts = new HashMap<>();
    artifacts.put("sidecars", new HashMap<>());
    spec.put("artifacts", artifacts);
    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);
    assertThat(((Map<?, ?>) artifacts.get("sidecars"))).isInstanceOf(Map.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeArtifactsSidecarsToMap_skipsMalformedListItems() {
    Map<String, Object> spec = new HashMap<>();
    Map<String, Object> artifacts = new HashMap<>();
    List<Object> sidecarsList = new ArrayList<>();
    sidecarsList.add("not-a-map");
    Map<String, Object> missingSidecar = new HashMap<>();
    missingSidecar.put("identifier", "x");
    sidecarsList.add(missingSidecar);
    Map<String, Object> sidecarNotMap = new HashMap<>();
    sidecarNotMap.put("sidecar", "not-map");
    sidecarsList.add(sidecarNotMap);
    Map<String, Object> noIdEntry = new HashMap<>();
    Map<String, Object> sidecar = new HashMap<>();
    sidecar.put("type", "DockerRegistry");
    noIdEntry.put("sidecar", sidecar);
    sidecarsList.add(noIdEntry);

    Map<String, Object> validEntry = new HashMap<>();
    Map<String, Object> validSidecar = new HashMap<>();
    validSidecar.put("identifier", "keep-me");
    validEntry.put("sidecar", validSidecar);
    sidecarsList.add(validEntry);

    artifacts.put("sidecars", sidecarsList);
    spec.put("artifacts", artifacts);

    ngServiceYamlHelper.normalizeArtifactsSidecarsToMap(spec);

    Map<String, Object> sidecarsMap =
        (Map<String, Object>) ((Map<String, Object>) spec.get("artifacts")).get("sidecars");
    assertThat(sidecarsMap).containsOnlyKeys("keep-me");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap_manifestsNotList() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("manifests", new HashMap<>());
    ngServiceYamlHelper.normalizeManifestsToMap(spec);
    assertThat(spec.get("manifests")).isInstanceOf(Map.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap_emptyListBecomesEmptyMap() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("manifests", new ArrayList<>());
    ngServiceYamlHelper.normalizeManifestsToMap(spec);
    assertThat(spec.get("manifests")).isInstanceOf(Map.class);
    assertThat((Map<?, ?>) spec.get("manifests")).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormalizeManifestsToMap_skipsMalformedAndNullIdentifier() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> list = new ArrayList<>();
    list.add(1);
    Map<String, Object> noManifestKey = new HashMap<>();
    noManifestKey.put("identifier", "x");
    list.add(noManifestKey);
    Map<String, Object> manifestNotMap = new HashMap<>();
    manifestNotMap.put("manifest", "bad");
    list.add(manifestNotMap);
    Map<String, Object> nullIdEntry = new HashMap<>();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("type", "K8sManifest");
    nullIdEntry.put("manifest", manifest);
    list.add(nullIdEntry);
    Map<String, Object> good = new HashMap<>();
    Map<String, Object> goodManifest = new HashMap<>();
    goodManifest.put("identifier", "m1");
    good.put("manifest", goodManifest);
    list.add(good);
    spec.put("manifests", list);

    ngServiceYamlHelper.normalizeManifestsToMap(spec);

    Map<String, Object> manifestsMap = (Map<String, Object>) spec.get("manifests");
    assertThat(manifestsMap).containsOnlyKeys("m1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormaliseConfigFilesToMap_configFilesNotList() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("configFiles", new HashMap<>());
    ngServiceYamlHelper.normaliseConfigFilesToMap(spec);
    assertThat(spec.get("configFiles")).isInstanceOf(Map.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormaliseConfigFilesToMap_emptyListBecomesEmptyMap() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("configFiles", new ArrayList<>());
    ngServiceYamlHelper.normaliseConfigFilesToMap(spec);
    assertThat(spec.get("configFiles")).isInstanceOf(Map.class);
    assertThat((Map<?, ?>) spec.get("configFiles")).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testNormaliseConfigFilesToMap_skipsMalformedAndNullIdentifier() {
    Map<String, Object> spec = new HashMap<>();
    List<Object> list = new ArrayList<>();
    list.add("skip");
    Map<String, Object> noConfigFile = new HashMap<>();
    noConfigFile.put("identifier", "x");
    list.add(noConfigFile);
    Map<String, Object> cfgNotMap = new HashMap<>();
    cfgNotMap.put("configFile", 1);
    list.add(cfgNotMap);
    Map<String, Object> nullIdEntry = new HashMap<>();
    Map<String, Object> configFile = new HashMap<>();
    configFile.put("type", "Harness");
    nullIdEntry.put("configFile", configFile);
    list.add(nullIdEntry);
    Map<String, Object> good = new HashMap<>();
    Map<String, Object> goodCfg = new HashMap<>();
    goodCfg.put("identifier", "c1");
    good.put("configFile", goodCfg);
    list.add(good);
    spec.put("configFiles", list);

    ngServiceYamlHelper.normaliseConfigFilesToMap(spec);

    Map<String, Object> map = (Map<String, Object>) spec.get("configFiles");
    assertThat(map).containsOnlyKeys("c1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInlineResolvedPrimaryArtifact_inlinesMatchingSource() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: <+serviceVariables.artifactRef>\n"
        + "          sources:\n"
        + "            - identifier: docker_primary\n"
        + "              type: DockerRegistry\n"
        + "              spec:\n"
        + "                connectorRef: docker-connector\n"
        + "            - identifier: other\n"
        + "              type: DockerRegistry\n"
        + "              spec:\n"
        + "                connectorRef: other-connector\n";

    String resolvedYaml = ngServiceYamlHelper.inlineResolvedPrimaryArtifact(yaml, "docker_primary");

    Map<String, Object> spec = ngServiceYamlHelper.buildNgServiceYamlSpecMap(resolvedYaml);
    Map<String, Object> artifacts = (Map<String, Object>) spec.get("artifacts");
    Map<String, Object> primary = (Map<String, Object>) artifacts.get("primary");
    assertThat(primary).doesNotContainKey("identifier");
    assertThat(primary).doesNotContainKey("sources");
    assertThat(primary.get("type")).isEqualTo("DockerRegistry");
    Map<String, Object> primarySpec = (Map<String, Object>) primary.get("spec");
    assertThat(primarySpec.get("connectorRef")).isEqualTo("docker-connector");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInlineResolvedPrimaryArtifact_noOpWhenIdNull() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          identifier: docker_primary\n";
    assertThat(ngServiceYamlHelper.inlineResolvedPrimaryArtifact(yaml, null)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInlineResolvedPrimaryArtifact_noOpWhenNoSourcesArray() {
    // Fixed-ref (static) flow: primary is already the inlined artifact config, with no "sources" key.
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          identifier: docker_primary\n"
        + "          type: DockerRegistry\n";

    assertThat(ngServiceYamlHelper.inlineResolvedPrimaryArtifact(yaml, "docker_primary")).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInlineResolvedPrimaryArtifact_noOpWhenIdDoesNotMatchAnySource() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: <+serviceVariables.artifactRef>\n"
        + "          sources:\n"
        + "            - identifier: docker_primary\n"
        + "              type: DockerRegistry\n";

    assertThat(ngServiceYamlHelper.inlineResolvedPrimaryArtifact(yaml, "unknown")).isEqualTo(yaml);
  }

  private static final String MANIFESTS_YAML = "service:\n"
      + "  serviceDefinition:\n"
      + "    spec:\n"
      + "      manifests:\n"
      + "        - manifest:\n"
      + "            identifier: chart1\n"
      + "            type: HelmChart\n"
      + "        - manifest:\n"
      + "            identifier: chart2\n"
      + "            type: HelmChart\n"
      + "        - manifest:\n"
      + "            identifier: values1\n"
      + "            type: Values\n";

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testFilterResolvedPrimaryManifest_dropsLosingSameTypeManifests() {
    String resolvedYaml = ngServiceYamlHelper.filterResolvedPrimaryManifest(MANIFESTS_YAML, "chart2");

    Map<String, Object> spec = ngServiceYamlHelper.buildNgServiceYamlSpecMap(resolvedYaml);
    Map<String, Object> manifests = (Map<String, Object>) spec.get("manifests");
    // Static-ref parity: the losing chart is gone, manifests of other types are untouched.
    assertThat(manifests).containsOnlyKeys("chart2", "values1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testFilterResolvedPrimaryManifest_noOpWhenIdNullOrUnknown() {
    assertThat(ngServiceYamlHelper.filterResolvedPrimaryManifest(MANIFESTS_YAML, null)).isEqualTo(MANIFESTS_YAML);
    assertThat(ngServiceYamlHelper.filterResolvedPrimaryManifest(MANIFESTS_YAML, "unknown")).isEqualTo(MANIFESTS_YAML);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testFilterResolvedPrimaryManifest_noOpWhenNoManifestsArray() {
    String yaml = "service:\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          identifier: docker_primary\n";

    assertThat(ngServiceYamlHelper.filterResolvedPrimaryManifest(yaml, "chart1")).isEqualTo(yaml);
  }
}
