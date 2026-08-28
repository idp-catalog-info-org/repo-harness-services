/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;
import static io.harness.rule.OwnerRule.NAVTEJPREET;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.TANAY_KESHARWANI;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactSourceCandidatesOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.SidecarsOutcome;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.overrides.OverrideResourceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.serializer.JsonUtils;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.artifacts.ArtifactType;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper;
import io.harness.unified.cd.service.artifacts.DockerHubArtifactConfig;
import io.harness.unified.cd.service.manifests.FetchType;
import io.harness.unified.cd.service.manifests.GithubStoreConfig;
import io.harness.unified.cd.service.manifests.HelmChartManifest;
import io.harness.unified.cd.service.manifests.K8sManifest;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.ManifestWrapper;
import io.harness.unified.cd.service.manifests.StoreConfigWrapper;
import io.harness.unified.cd.service.manifests.StoreType;
import io.harness.unified.cd.service.manifests.ValuesManifest;
import io.harness.unified.cd.service.spec.KubernetesServiceSpec;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceSpec;
import io.harness.unified.service.NGOutcomes;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ServiceEntityProcessorTest {
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private NgServiceResourceClient ngServiceResourceClient;
  @Mock private InfraBasedHelper infraBasedHelper;
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Mock private ConnectorInputsMapper connectorInputsMapper;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private InfrastructureResourceClient infrastructureResourceClient;
  @Mock private OverrideResourceClient overrideResourceClient;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private OverrideApplyHelper overrideApplyHelper;
  @Mock private ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  @Mock private EnvOutcomeHelper envOutcomeHelper;
  @Mock private TemplateYamlGenerator templateYamlGenerator;
  @Mock private NgServiceYamlHelper ngServiceYamlHelper;
  @Mock private OverrideVariablesHelper overrideVariablesHelper;
  @InjectMocks private ServiceEntityProcessor serviceEntityProcessor;

  private static final String ACCOUNT_ID = "account";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";

  // ========== Helper methods for building test data ==========

  private static StoreConfigWrapper buildGithubStore() {
    return StoreConfigWrapper.builder()
        .uses(StoreType.GITHUB)
        .with(GithubStoreConfig.builder()
                  .connector(ParameterField.createValueField("github-connector"))
                  .type(FetchType.BRANCH)
                  .branch(ParameterField.createValueField("main"))
                  .paths(ParameterField.createValueField(List.of("k8s/deployment.yaml")))
                  .build())
        .build();
  }

  private static ManifestConfig buildK8sManifest(String id) {
    return ManifestConfig.builder()
        .id(id)
        .uses(ManifestType.K8S)
        .with(K8sManifest.builder()
                  .storeConfigWrapper(buildGithubStore())
                  .values(ParameterField.createValueField(List.of("values/default.yaml")))
                  .build())
        .build();
  }

  private static ManifestConfig buildValuesManifest(String id, List<String> values) {
    return ManifestConfig.builder()
        .id(id)
        .uses(ManifestType.VALUES)
        .with(ValuesManifest.builder()
                  .storeConfigWrapper(buildGithubStore())
                  .values(ParameterField.createValueField(values))
                  .build())
        .build();
  }

  private static ManifestConfig buildHelmChartManifest(String id) {
    return ManifestConfig.builder()
        .id(id)
        .uses(ManifestType.HELM_CHART)
        .with(HelmChartManifest.builder()
                  .storeConfigWrapper(buildGithubStore())
                  .chartName(ParameterField.createValueField("my-chart"))
                  .chartVersion(ParameterField.createValueField("1.0.0"))
                  .values(ParameterField.createValueField(List.of("values/dev.yaml")))
                  .build())
        .build();
  }

  private static ArtifactConfig buildPrimaryArtifact(String id) {
    return ArtifactConfig.builder()
        .id(id)
        .uses(ArtifactType.DOCKER_REGISTRY)
        .sidecar(false)
        .with(DockerHubArtifactConfig.builder()
                  .connector(ParameterField.createValueField("docker-connector"))
                  .image(ParameterField.createValueField("harness/my-app"))
                  .tag(ParameterField.createValueField("latest"))
                  .build())
        .build();
  }

  private static ArtifactConfig buildSidecarArtifact(String id) {
    return ArtifactConfig.builder()
        .id(id)
        .uses(ArtifactType.DOCKER_REGISTRY)
        .sidecar(true)
        .with(DockerHubArtifactConfig.builder()
                  .connector(ParameterField.createValueField("docker-connector"))
                  .image(ParameterField.createValueField("harness/sidecar"))
                  .tag(ParameterField.createValueField("v1.0"))
                  .build())
        .build();
  }

  private static ServiceConfig buildServiceConfig(ServiceSpec spec) {
    return ServiceConfig.builder().serviceInfoConfig(ServiceInfoConfig.builder().with(spec).build()).build();
  }

  private static ServiceConfig buildServiceConfigWithManifests(ManifestWrapper manifestWrapper) {
    return buildServiceConfig(KubernetesServiceSpec.builder().manifests(manifestWrapper).build());
  }

  private static ServiceConfig buildServiceConfigWithArtifacts(ArtifactWrapper artifactWrapper) {
    return buildServiceConfig(
        KubernetesServiceSpec.builder()
            .manifests(ManifestWrapper.builder().sources(List.of(buildK8sManifest("k8s_m"))).build())
            .artifacts(artifactWrapper)
            .build());
  }

  private void mockInfraHelpers() {
    when(infraBasedHelper.getStageInfra(any())).thenReturn(null);
    when(infraBasedHelper.getBasePath(any(), any())).thenReturn("/base/path");
  }

  // ========== Existing tests ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testServiceEntityProcessorInstantiation() {
    assertThat(serviceEntityProcessor).isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithNullManifests() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    KubernetesServiceSpec spec = KubernetesServiceSpec.builder().manifests(null).build();
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(spec).build();
    ServiceConfig serviceConfig = ServiceConfig.builder().serviceInfoConfig(infoConfig).build();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithEmptySources() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifestWrapper = ManifestWrapper.builder().sources(new ArrayList<>()).build();
    KubernetesServiceSpec spec = KubernetesServiceSpec.builder().manifests(manifestWrapper).build();
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(spec).build();
    ServiceConfig serviceConfig = ServiceConfig.builder().serviceInfoConfig(infoConfig).build();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithNullSources() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifestWrapper = ManifestWrapper.builder().sources(null).build();
    KubernetesServiceSpec spec = KubernetesServiceSpec.builder().manifests(manifestWrapper).build();
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(spec).build();
    ServiceConfig serviceConfig = ServiceConfig.builder().serviceInfoConfig(infoConfig).build();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithMockSpec() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceSpec mockSpec = mock(ServiceSpec.class);
    when(mockSpec.getManifests()).thenReturn(null);
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(mockSpec).build();
    ServiceConfig serviceConfig = ServiceConfig.builder().serviceInfoConfig(infoConfig).build();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithManifestWrapperNoSources() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceSpec mockSpec = mock(ServiceSpec.class);
    ManifestWrapper wrapper = ManifestWrapper.builder().build();
    when(mockSpec.getManifests()).thenReturn(wrapper);
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(mockSpec).build();
    ServiceConfig serviceConfig = ServiceConfig.builder().serviceInfoConfig(infoConfig).build();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsWithYamlFixture() {
    String unifiedServiceYaml = readFile("unified-service.yaml");

    ServiceConfig serviceConfig = toServiceConfig(unifiedServiceYaml);
    Ambiance ambiance = Ambiance.newBuilder().build();

    when(infraBasedHelper.getStageInfra(any())).thenReturn(null);
    when(infraBasedHelper.getBasePath(any(), any())).thenReturn("/base/path");

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).isNotNull();
    String manifestsExpressionJson = readFile("manifests-expression.yaml");
    Map<String, Object> expectedManifestsExpressionMap = JsonUtils.asMap(manifestsExpressionJson);
    assertThat(expectedManifestsExpressionMap.keySet()).containsAll(result.keySet());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifestsYamlFixture_containsManifestIds() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceConfig serviceConfig = toServiceConfig(unifiedServiceYaml);
    Ambiance ambiance = Ambiance.newBuilder().build();

    when(infraBasedHelper.getStageInfra(any())).thenReturn(null);
    when(infraBasedHelper.getBasePath(any(), any())).thenReturn("/base/path");

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, serviceConfig);

    assertThat(result).containsKeys("k8s_code", "values_1", "values_2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_fromCiManagerServiceEntity() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("account")
                                      .identifier("myService")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .name("My Service")
                                      .yaml(unifiedServiceYaml)
                                      .build();

    when(serviceEntityService.get(eq("account"), eq("org"), eq("proj"), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));

    when(infraBasedHelper.getStageInfra(any())).thenReturn(null);
    when(infraBasedHelper.getBasePath(any(), any())).thenReturn("/base/path");

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "",
        "", "", "account", "org", "proj", Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result).isNotNull();
    assertThat(result.getServiceConfig()).isNotNull();
    assertThat(result.getServiceOutputMap())
        .containsKeys(ProcessedServiceResult.MANIFESTS_KEY, ProcessedServiceResult.ARTIFACTS_KEY,
            ProcessedServiceResult.CONFIG_FILES_KEY);
    verify(serviceStepSweepingOutputHelper).saveServiceConfigSweepingOutput(any(), any());
    verify(overrideApplyHelper).handleOverrides(any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_throwsWhenMergedYamlEmpty() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("account")
                                      .identifier("myService")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .name("My Service")
                                      .yaml("")
                                      .build();

    when(serviceEntityService.get(eq("account"), eq("org"), eq("proj"), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));

    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(
        ()
            -> serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "", "", "", "account",
                "org", "proj", Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Merged service YAML cannot be empty");
  }

  // ========== processManifests: single K8s manifest with primary detection ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_singleK8sManifest_hasPrimaryEntry() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s = buildK8sManifest("k8s_main");
    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("primary");
    assertThat(result).containsKey("k8s_main");
    assertThat(result).containsKey("k8s");
  }

  // ========== processManifests: hyphenated id exposes camelCase alias in serviceOutput map ==========

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessManifests_hyphenatedId_exposesCamelCaseAlias() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s = buildK8sManifest("k8s-main");
    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    // Raw id key preserved (no regression) plus a camelCase alias for hyphenated ids, so that
    // serviceOutput.manifests.k8sMain expressions resolve.
    assertThat(result).containsKey("k8s-main");
    assertThat(result).containsKey("k8sMain");
    // Both keys reference the same manifest output.
    assertThat(result.get("k8sMain")).isSameAs(result.get("k8s-main"));
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessManifests_nonHyphenId_addsNoCamelCaseAlias() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s = buildK8sManifest("k8sMain");
    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    // Id without '-' => the id-based keys are just the raw id (+ the type/primary keys), no extra alias.
    assertThat(result).containsKey("k8sMain");
    assertThat(result.keySet().stream().filter(k -> k.toLowerCase().contains("k8smain")).count()).isEqualTo(1L);
  }

  // ========== processManifests: helm chart manifest ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_helmChartManifest_hasPrimaryAndTypeEntry() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig helm = buildHelmChartManifest("helm_m");
    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(helm)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("primary");
    assertThat(result).containsKey("helm_m");
    assertThat(result).containsKey("helmChart");
  }

  // ========== processManifests: k8s + multiple values (AllowMultipleManifests) ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_k8sWithMultipleValues_containsCollectivePaths() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s = buildK8sManifest("k8s_m");
    // VALUES manifests need inputs with "paths" key for processMultiManifestsTypes to collect paths
    ManifestConfig v1 = ManifestConfig.builder()
                            .id("v1")
                            .uses(ManifestType.VALUES)
                            .inputs(Map.of("paths", List.of("path_a", "path_b")))
                            .with(ValuesManifest.builder()
                                      .storeConfigWrapper(buildGithubStore())
                                      .values(ParameterField.createValueField(List.of("path_a", "path_b")))
                                      .build())
                            .build();
    ManifestConfig v2 = ManifestConfig.builder()
                            .id("v2")
                            .uses(ManifestType.VALUES)
                            .inputs(Map.of("paths", List.of("path_c")))
                            .with(ValuesManifest.builder()
                                      .storeConfigWrapper(buildGithubStore())
                                      .values(ParameterField.createValueField(List.of("path_c")))
                                      .build())
                            .build();
    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s, v1, v2)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("values");
    Object valuesEntry = result.get("values");
    assertThat(valuesEntry).isInstanceOf(Map.class);
    Map<String, Object> valuesMap = (Map<String, Object>) valuesEntry;
    assertThat(valuesMap).containsKey("paths");
  }

  // ========== processManifests: duplicate single-allowed manifest type throws ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_duplicateK8sManifests_throwsInvalidRequest() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s1 = buildK8sManifest("k8s_1");
    ManifestConfig k8s2 = buildK8sManifest("k8s_2");
    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s1, k8s2)).build());

    mockInfraHelpers();

    assertThatThrownBy(() -> serviceEntityProcessor.processManifests(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Multiple manifests are not allowed for manifest type");
  }

  // ========== processManifests: overrides and toRender entries ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_k8sWithValues_hasOverridesAndToRender() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    // K8S manifest needs inputs with appropriate keys for overrides/toRender/toTemplate to be populated
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paths", List.of("k8s/deployment.yaml"));
    inputs.put("overrides", List.of("values/override.yaml"));
    inputs.put("valuesPaths", List.of("values/default.yaml"));
    ManifestConfig k8s = ManifestConfig.builder()
                             .id("k8s_m")
                             .uses(ManifestType.K8S)
                             .inputs(inputs)
                             .with(K8sManifest.builder()
                                       .storeConfigWrapper(buildGithubStore())
                                       .values(ParameterField.createValueField(List.of("values/default.yaml")))
                                       .build())
                             .build();
    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());

    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("overrides");
    assertThat(result).containsKey("toRender");
    assertThat(result).containsKey("toTemplate");
  }

  // ========== processManifests: templatized manifests with action and inputs ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_templatizedManifestWithInputs_processesTemplatizedPath() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("paths", List.of("k8s/deploy.yaml"));
    inputs.put("connector", "my-connector");

    ManifestConfig templatizedManifest = ManifestConfig.builder()
                                             .id("k8s_templ")
                                             .uses(ManifestType.K8S)
                                             .action("k8s-github")
                                             .inputs(inputs)
                                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                                             .build();

    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(templatizedManifest)).build());

    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any()))
        .thenReturn(new TemplateYamlResult(inputs, "generated: yaml"));

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("k8s_templ");
    assertThat(result).containsKey("primary");
    verify(templateYamlGenerator).generateTemplateYamlWithDefaults(any(), any());
  }

  // ========== processManifests: templatized duplicate single type throws ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_templatizedDuplicateK8s_throwsInvalidRequest() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig k8s1 = ManifestConfig.builder()
                              .id("k8s_1")
                              .uses(ManifestType.K8S)
                              .action("k8s-github")
                              .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                              .build();
    ManifestConfig k8s2 = ManifestConfig.builder()
                              .id("k8s_2")
                              .uses(ManifestType.K8S)
                              .action("k8s-github")
                              .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                              .build();

    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s1, k8s2)).build());

    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any())).thenReturn(null);

    assertThatThrownBy(() -> serviceEntityProcessor.processManifests(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Multiple manifests are not allowed for manifest type");
  }

  // ========== getMergedServiceYamlAndSaveOutput: with artifacts ==========

  private static final String SERVICE_WITH_ARTIFACTS_YAML = "service:\n"
      + "  uses: kubernetes\n"
      + "  with:\n"
      + "    artifacts:\n"
      + "      sources:\n"
      + "        - id: docker_primary\n"
      + "          uses: docker-registry\n"
      + "          with:\n"
      + "            connector: docker-connector\n"
      + "            image: harness/my-app\n"
      + "            tag: latest\n"
      + "        - id: docker_sidecar\n"
      + "          uses: docker-registry\n"
      + "          sidecar: true\n"
      + "          with:\n"
      + "            connector: docker-connector\n"
      + "            image: harness/sidecar\n"
      + "            tag: v1.0\n"
      + "    manifests:\n"
      + "      sources:\n"
      + "        - id: k8s_m\n"
      + "          uses: k8s\n"
      + "          with:\n"
      + "            store:\n"
      + "              uses: github\n"
      + "              with:\n"
      + "                type: branch\n"
      + "                branch: main\n"
      + "                path:\n"
      + "                  - k8s/deploy.yaml\n"
      + "                connector: github-connector\n";

  private static final String SERVICE_WITH_CONFIG_FILES_YAML = "service:\n"
      + "  uses: kubernetes\n"
      + "  with:\n"
      + "    config-files:\n"
      + "      - id: app_config\n"
      + "        store:\n"
      + "          uses: github\n"
      + "          with:\n"
      + "            type: branch\n"
      + "            branch: main\n"
      + "            paths:\n"
      + "              - config/app.properties\n"
      + "            connector: github-connector\n"
      + "    manifests:\n"
      + "      sources:\n"
      + "        - id: k8s_m\n"
      + "          uses: k8s\n"
      + "          with:\n"
      + "            store:\n"
      + "              uses: github\n"
      + "              with:\n"
      + "                type: branch\n"
      + "                branch: main\n"
      + "                path:\n"
      + "                  - k8s/deploy.yaml\n"
      + "                connector: github-connector\n";

  private static final String SERVICE_WITH_SIDECAR_ONLY_YAML = "service:\n"
      + "  uses: kubernetes\n"
      + "  with:\n"
      + "    artifacts:\n"
      + "      sources:\n"
      + "        - id: sidecar_1\n"
      + "          uses: docker-registry\n"
      + "          sidecar: true\n"
      + "          with:\n"
      + "            connector: docker-connector\n"
      + "            image: harness/sidecar\n"
      + "            tag: v1.0\n"
      + "    manifests:\n"
      + "      sources:\n"
      + "        - id: k8s_m\n"
      + "          uses: k8s\n"
      + "          with:\n"
      + "            store:\n"
      + "              uses: github\n"
      + "              with:\n"
      + "                type: branch\n"
      + "                branch: main\n"
      + "                path:\n"
      + "                  - k8s/deploy.yaml\n"
      + "                connector: github-connector\n";

  private static final String SERVICE_WITH_TEMPLATIZED_ARTIFACT_YAML = "service:\n"
      + "  uses: kubernetes\n"
      + "  with:\n"
      + "    artifacts:\n"
      + "      sources:\n"
      + "        - id: docker_primary\n"
      + "          uses: docker-registry\n"
      + "          action: docker-template\n"
      + "          with:\n"
      + "            connector: docker-connector\n"
      + "            image: harness/my-app\n"
      + "            tag: v2.0\n"
      + "    manifests:\n"
      + "      sources:\n"
      + "        - id: k8s_m\n"
      + "          uses: k8s\n"
      + "          with:\n"
      + "            store:\n"
      + "              uses: github\n"
      + "              with:\n"
      + "                type: branch\n"
      + "                branch: main\n"
      + "                path:\n"
      + "                  - k8s/deploy.yaml\n"
      + "                connector: github-connector\n";

  private static final String SERVICE_WITH_TEMPLATIZED_CONFIG_FILE_YAML = "service:\n"
      + "  uses: kubernetes\n"
      + "  with:\n"
      + "    config-files:\n"
      + "      - id: app_config\n"
      + "        action: config-file-github\n"
      + "        store:\n"
      + "          uses: github\n"
      + "          with:\n"
      + "            type: branch\n"
      + "            branch: main\n"
      + "            path:\n"
      + "              - config/app.properties\n"
      + "            connector: github-connector\n"
      + "    manifests:\n"
      + "      sources:\n"
      + "        - id: k8s_m\n"
      + "          uses: k8s\n"
      + "          with:\n"
      + "            store:\n"
      + "              uses: github\n"
      + "              with:\n"
      + "                type: branch\n"
      + "                branch: main\n"
      + "                path:\n"
      + "                  - k8s/deploy.yaml\n"
      + "                connector: github-connector\n";

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_serviceWithArtifacts_savesArtifactOutput() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcWithArtifacts")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc With Artifacts")
                                      .yaml(SERVICE_WITH_ARTIFACTS_YAML)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcWithArtifacts")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result =
        serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcWithArtifacts", "", "", "", ACCOUNT_ID,
            ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result).isNotNull();
    assertThat(result.getServiceConfig().getServiceInfoConfig().getWith().getArtifacts()).isNotNull();
    Map<String, Object> artifactMap = result.getServiceOutputMap().get(ProcessedServiceResult.ARTIFACTS_KEY);
    assertThat(artifactMap).containsKey("primary");
    assertThat(artifactMap).containsKey("docker_primary");
    assertThat(artifactMap).containsKey("docker_sidecar");
  }

  // ========== getMergedServiceYamlAndSaveOutput: with config files ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_serviceWithConfigFiles_savesConfigFileOutput() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcWithCf")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc With Config Files")
                                      .yaml(SERVICE_WITH_CONFIG_FILES_YAML)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcWithCf")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcWithCf", "",
        "", "", ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result).isNotNull();
    Map<String, Object> configFilesMap = result.getServiceOutputMap().get(ProcessedServiceResult.CONFIG_FILES_KEY);
    assertThat(configFilesMap).containsKey("app_config");
    @SuppressWarnings("unchecked")
    Map<String, Object> appConfig = (Map<String, Object>) configFilesMap.get("app_config");
    assertThat(appConfig).containsEntry("paths", "/base/path/app_config/config/app.properties");
  }

  // ========== getMergedServiceYamlAndSaveOutput: with primary artifact from sources ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_artifactWithOnlyPrimaryId_updatesFromSources() {
    // YAML with primary artifact set as expression (runtime input) and sources containing the artifact
    String serviceYaml = "service:\n"
        + "  uses: kubernetes\n"
        + "  with:\n"
        + "    artifacts:\n"
        + "      primary: <+input>\n"
        + "      sources:\n"
        + "        - id: docker_primary\n"
        + "          uses: docker-registry\n"
        + "          with:\n"
        + "            connector: docker-connector\n"
        + "            image: harness/my-app\n"
        + "            tag: latest\n"
        + "    manifests:\n"
        + "      sources:\n"
        + "        - id: k8s_m\n"
        + "          uses: k8s\n"
        + "          with:\n"
        + "            store:\n"
        + "              uses: github\n"
        + "              with:\n"
        + "                type: branch\n"
        + "                branch: main\n"
        + "                path:\n"
        + "                  - k8s/deploy.yaml\n"
        + "                connector: github-connector\n";

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcPrimaryId")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc Primary Id")
                                      .yaml(serviceYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcPrimaryId")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result =
        serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcPrimaryId", "", "", "", ACCOUNT_ID,
            ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result).isNotNull();
    Map<String, Object> artifactMap = result.getServiceOutputMap().get(ProcessedServiceResult.ARTIFACTS_KEY);
    assertThat(artifactMap).containsKey("primary");
    assertThat(artifactMap).containsKey("docker_primary");
  }

  // ========== getMergedServiceYamlAndSaveOutput: service entity metadata populated ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_serviceEntityMetadata_isPopulated() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("myService")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("My Service")
                                      .description("A test service")
                                      .yaml(unifiedServiceYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "",
        "", "", ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result.getServiceEntityMetadata()).isNotNull();
    assertThat(result.getServiceEntityMetadata().getIdentifier()).isEqualTo("myService");
    assertThat(result.getServiceEntityMetadata().getName()).isEqualTo("My Service");
    assertThat(result.getServiceEntityMetadata().getDescription()).isEqualTo("A test service");
  }

  // ========== getMergedServiceYamlAndSaveOutput: no env ref means null environment outcome ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_noEnvRef_environmentOutcomeIsNull() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("myService")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("My Service")
                                      .yaml(unifiedServiceYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "",
        "", "", ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result.getEnvironmentOutcome()).isNull();
    verify(serviceStepSweepingOutputHelper, never()).saveV0EnvironmentOutcome(any(), any());
  }

  // ========== getMergedServiceYamlAndSaveOutput: null service YAML triggers exception ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_nullYaml_throwsInvalidRequest() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("myService")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("My Service")
                                      .yaml(null)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));

    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(
        ()
            -> serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "", "", "", ACCOUNT_ID,
                ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Merged service YAML cannot be empty");
  }

  // ========== getMergedServiceYamlAndSaveOutput: helm chart primary manifest via YAML ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_helmChartWithPrimary_containsPrimaryManifest() {
    String helmYaml = "service:\n"
        + "  uses: kubernetes\n"
        + "  with:\n"
        + "    manifests:\n"
        + "      primary:\n"
        + "        id: helm_m\n"
        + "        uses: helm-chart\n"
        + "        with:\n"
        + "          store:\n"
        + "            uses: github\n"
        + "            with:\n"
        + "              type: branch\n"
        + "              branch: main\n"
        + "              path:\n"
        + "                - charts/my-app\n"
        + "              connector: github-connector\n"
        + "          chart-name: my-app\n"
        + "          chart-version: 1.0.0\n"
        + "          values:\n"
        + "            - values/dev.yaml\n"
        + "      sources:\n"
        + "        - id: helm_m\n"
        + "          uses: helm-chart\n"
        + "          with:\n"
        + "            store:\n"
        + "              uses: github\n"
        + "              with:\n"
        + "                type: branch\n"
        + "                branch: main\n"
        + "                path:\n"
        + "                  - charts/my-app\n"
        + "                connector: github-connector\n"
        + "            chart-name: my-app\n"
        + "            chart-version: 1.0.0\n"
        + "            values:\n"
        + "              - values/dev.yaml\n";

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcHelm")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc Helm Primary")
                                      .yaml(helmYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcHelm")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    ProcessedServiceResult result = serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcHelm", "",
        "", "", ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    assertThat(result).isNotNull();
    Map<String, Object> manifestMap = result.getServiceOutputMap().get(ProcessedServiceResult.MANIFESTS_KEY);
    assertThat(manifestMap).containsKey("primary");
    assertThat(manifestMap).containsKey("helm_m");
  }

  // ========== getMergedServiceYamlAndSaveOutput: primary manifest with unsupported type throws ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_primaryWithUnsupportedType_throwsInvalidRequest() {
    String yaml = "service:\n"
        + "  uses: kubernetes\n"
        + "  with:\n"
        + "    manifests:\n"
        + "      primary:\n"
        + "        id: k8s_m\n"
        + "        uses: k8s\n"
        + "        with:\n"
        + "          store:\n"
        + "            uses: github\n"
        + "            with:\n"
        + "              type: branch\n"
        + "              branch: main\n"
        + "              path:\n"
        + "                - k8s/deploy.yaml\n"
        + "              connector: github-connector\n"
        + "      sources:\n"
        + "        - id: k8s_m\n"
        + "          uses: k8s\n"
        + "          with:\n"
        + "            store:\n"
        + "              uses: github\n"
        + "              with:\n"
        + "                type: branch\n"
        + "                branch: main\n"
        + "                path:\n"
        + "                  - k8s/deploy.yaml\n"
        + "                connector: github-connector\n";

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcBadPrimary")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc Bad Primary")
                                      .yaml(yaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcBadPrimary")))
        .thenReturn(Optional.of(serviceEntity));

    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(
        ()
            -> serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcBadPrimary", "", "", "",
                ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("primary manifest is not supported for manifest type");
  }

  // ========== getMergedServiceYamlAndSaveOutput: primary manifest with no config throws ==========

  // ========== getMergedServiceYamlAndSaveOutput: only sidecars, no primary artifact throws ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_onlySidecarArtifacts_throwsInvalidRequest() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("svcSidecar")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("Svc Sidecar Only")
                                      .yaml(SERVICE_WITH_SIDECAR_ONLY_YAML)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("svcSidecar")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(
        ()
            -> serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "svcSidecar", "", "", "", ACCOUNT_ID,
                ORG_ID, PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("At least one primary artifact is required");
  }

  // ========== processManifests: templatized multiple values manifests with inputs and paths ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_templatizedMultipleValuesManifests_collectivePathsFromInputs() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, Object> inputs1 = new HashMap<>();
    inputs1.put("paths", List.of("val_a.yaml", "val_b.yaml"));

    Map<String, Object> inputs2 = new HashMap<>();
    inputs2.put("paths", List.of("val_c.yaml"));

    ManifestConfig v1 = ManifestConfig.builder()
                            .id("values_1")
                            .uses(ManifestType.VALUES)
                            .action("values-template")
                            .inputs(inputs1)
                            .with(ValuesManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                            .build();

    ManifestConfig v2 = ManifestConfig.builder()
                            .id("values_2")
                            .uses(ManifestType.VALUES)
                            .action("values-template")
                            .inputs(inputs2)
                            .with(ValuesManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                            .build();

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("k8s_m")
                             .uses(ManifestType.K8S)
                             .action("k8s-template")
                             .inputs(new HashMap<>())
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s, v1, v2)).build());

    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any()))
        .thenReturn(new TemplateYamlResult(inputs1, "yaml"))
        .thenReturn(new TemplateYamlResult(inputs1, "yaml"))
        .thenReturn(new TemplateYamlResult(inputs2, "yaml"));

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("values_1");
    assertThat(result).containsKey("values_2");
    assertThat(result).containsKey("values");
  }

  // ========== getMergedServiceYamlAndSaveOutput: overrides not fetched when no envRef ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_noEnvRef_overridesNotFetched() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("myService")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("My Service")
                                      .yaml(unifiedServiceYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "", "", "", ACCOUNT_ID, ORG_ID,
        PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    // convertToUnifiedOverrides was removed from OverrideResourceClient
  }

  // ========== getMergedServiceYamlAndSaveOutput: saving sweeping outputs ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetMergedServiceYamlAndSaveOutput_savesSweepingOutputs() {
    String unifiedServiceYaml = readFile("unified-service.yaml");
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .identifier("myService")
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("My Service")
                                      .yaml(unifiedServiceYaml)
                                      .build();

    when(serviceEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("myService")))
        .thenReturn(Optional.of(serviceEntity));
    mockInfraHelpers();

    Ambiance ambiance = Ambiance.newBuilder().build();

    serviceEntityProcessor.getMergedServiceYamlAndSaveOutput(ambiance, "myService", "", "", "", ACCOUNT_ID, ORG_ID,
        PROJECT_ID, Collections.emptyMap(), ParameterField.ofNull(), null, null, "", null);

    verify(serviceStepSweepingOutputHelper).saveServiceConfigSweepingOutput(any(), any());
    verify(overrideApplyHelper).handleOverrides(any(), any(), any());
  }

  // ========== processManifests: helm chart in sources is detected as primary ==========

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessManifests_helmChartInSources_detectedAsPrimary() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestConfig helm = buildHelmChartManifest("helm_direct");
    ManifestConfig v1 = buildValuesManifest("v1", List.of("a.yaml"));
    ManifestWrapper manifestWrapper = ManifestWrapper.builder().sources(List.of(helm, v1)).build();

    ServiceConfig config = buildServiceConfigWithManifests(manifestWrapper);
    mockInfraHelpers();

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("helm_direct");
    assertThat(result).containsKey("v1");
    assertThat(result).containsKey("primary");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testProcessManifests_pluginPathValid_returnsAbsolutePluginPath() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PLUGIN_PATH, "my-plugin-dir");

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("k8s_plugin")
                             .uses(ManifestType.K8S)
                             .action("k8s-github")
                             .inputs(inputs)
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());
    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any()))
        .thenReturn(new TemplateYamlResult(inputs, "generated: yaml"));

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("k8s_plugin");
    @SuppressWarnings("unchecked") Map<String, Object> manifestOutput = (Map<String, Object>) result.get("k8s_plugin");
    assertThat(manifestOutput).containsKey(ManifestTemplateConstants.OUTPUT_KEY_PLUGIN);
    assertThat(manifestOutput.get(ManifestTemplateConstants.OUTPUT_KEY_PLUGIN).toString())
        .isEqualTo("/base/path/k8s_plugin/my-plugin-dir");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testProcessManifests_pluginPathNullLiteral_returnsResolvedNullPath() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PLUGIN_PATH, "null");

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("k8s_plugin_null")
                             .uses(ManifestType.K8S)
                             .action("k8s-github")
                             .inputs(inputs)
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());
    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any()))
        .thenReturn(new TemplateYamlResult(inputs, "generated: yaml"));

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("k8s_plugin_null");
    @SuppressWarnings("unchecked")
    Map<String, Object> manifestOutput = (Map<String, Object>) result.get("k8s_plugin_null");
    assertThat(manifestOutput).containsKey(ManifestTemplateConstants.OUTPUT_KEY_PLUGIN);
    assertThat(manifestOutput.get(ManifestTemplateConstants.OUTPUT_KEY_PLUGIN).toString())
        .isEqualTo("/base/path/k8s_plugin_null/null");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testProcessManifests_pluginPathEmpty_keyNotPresent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PLUGIN_PATH, "");

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("k8s_plugin_empty")
                             .uses(ManifestType.K8S)
                             .action("k8s-github")
                             .inputs(inputs)
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    ServiceConfig config = buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(k8s)).build());
    mockInfraHelpers();
    when(templateYamlGenerator.generateTemplateYamlWithDefaults(any(), any()))
        .thenReturn(new TemplateYamlResult(inputs, "generated: yaml"));

    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    assertThat(result).containsKey("k8s_plugin_empty");
    @SuppressWarnings("unchecked")
    Map<String, Object> manifestOutput = (Map<String, Object>) result.get("k8s_plugin_empty");
    assertThat(manifestOutput).doesNotContainKey(ManifestTemplateConstants.OUTPUT_KEY_PLUGIN);
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetManifestOutputFromInputs_kustomizeOverlayApplied_pathContainsFolderSuffix() {
    // Contract: overlayConfiguration.kustomizeYamlFolderPath is emitted as its own output key
    // (kustomizeYamlFolderPath), and PATHS stays the base manifest path, unmodified. The
    // kustomize-template plugin does the join against PLUGIN_MANIFEST_PATH.
    Map<String, Object> overlay = new HashMap<>();
    overlay.put(
        ManifestTemplateConstants.INPUTS_KEY_KUSTOMIZE_YAML_FOLDER_PATH, "multipleEnv/environments/production/");
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_OVERLAY_CONFIGURATION, overlay);

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("kustomize_overlay")
                             .uses(ManifestType.K8S)
                             .inputs(inputs)
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    Map<String, Object> result = ServiceEntityProcessor.getManifestOutputFromInputs(k8s, "/harness/manifests");

    // PATHS is a JSON-serialized list; the base path must NOT carry the overlay folder suffix.
    assertThat(result).containsKey(ServiceEntityProcessor.PATHS);
    assertThat(result.get(ServiceEntityProcessor.PATHS).toString()).doesNotContain("environments/production");
    assertThat(result).containsKey(ManifestTemplateConstants.OUTPUT_KEY_KUSTOMIZE_YAML_FOLDER_PATH);
    assertThat(result.get(ManifestTemplateConstants.OUTPUT_KEY_KUSTOMIZE_YAML_FOLDER_PATH))
        .isEqualTo("multipleEnv/environments/production/");
  }

  @Test
  @Owner(developers = NAVTEJPREET)
  @Category(UnitTests.class)
  public void testGetManifestOutputFromInputs_noOverlay_pathUnchanged() {
    // Negative case: without overlayConfiguration the base path is left untouched and the overlay key is absent.
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(ManifestTemplateConstants.INPUTS_KEY_PATHS, List.of("kustomize/"));

    ManifestConfig k8s = ManifestConfig.builder()
                             .id("kustomize_base")
                             .uses(ManifestType.K8S)
                             .inputs(inputs)
                             .with(K8sManifest.builder().storeConfigWrapper(buildGithubStore()).build())
                             .build();

    Map<String, Object> result = ServiceEntityProcessor.getManifestOutputFromInputs(k8s, "/harness/manifests");

    assertThat(result).containsKey(ServiceEntityProcessor.PATHS);
    assertThat(result.get(ServiceEntityProcessor.PATHS).toString()).doesNotContain("environments/production");
    assertThat(result).doesNotContainKey(ManifestTemplateConstants.OUTPUT_KEY_KUSTOMIZE_YAML_FOLDER_PATH);
  }

  // ========== handlePrimaryManifest: expression based primaryManifestRef ==========

  private static final String PRIMARY_MANIFEST_REF_EXPRESSION = "<+serviceVariables.manifestRef>";

  private static ManifestWrapper buildTwoHelmChartsWithExpressionPrimary() {
    return ManifestWrapper.builder()
        .sources(new ArrayList<>(List.of(buildHelmChartManifest("manifest1"), buildHelmChartManifest("manifest2"),
            buildValuesManifest("manifest3", List.of("values.yaml")))))
        .primary(ParameterField.createExpressionField(true, PRIMARY_MANIFEST_REF_EXPRESSION, null, false))
        .build();
  }

  private void mockPrimaryManifestRefRendering(String resolvedValue) {
    when(cdStepsExpressionResolver.renderValue(any(), eq(PRIMARY_MANIFEST_REF_EXPRESSION), eq(true)))
        .thenReturn(resolvedValue);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRef_resolvesAndDropsOtherCharts() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifests = buildTwoHelmChartsWithExpressionPrimary();
    ServiceConfig config = buildServiceConfigWithManifests(manifests);

    mockPrimaryManifestRefRendering("manifest2");

    serviceEntityProcessor.handlePrimaryManifest(ambiance, config);

    // NG parity: the non primary helm chart is dropped, non chart manifests are untouched.
    assertThat(manifests.getSources()).extracting(ManifestConfig::getId).containsExactly("manifest3", "manifest2");
    assertThat(manifests.getPrimary().isExpression()).isFalse();
    assertThat(manifests.getPrimary().getValue().getId()).isEqualTo("manifest2");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRefUnresolved_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    mockPrimaryManifestRefRendering(PRIMARY_MANIFEST_REF_EXPRESSION);

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unable to resolve primaryManifestRef")
        .hasMessageContaining(PRIMARY_MANIFEST_REF_EXPRESSION);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRefResolvesToEmpty_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    mockPrimaryManifestRefRendering("");

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unable to resolve primaryManifestRef");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRefMatchesNoManifest_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    mockPrimaryManifestRefRendering("manifest9");

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("primaryManifestRef: manifest9 does not match to any");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRefResolvesToUnsupportedType_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    mockPrimaryManifestRefRendering("manifest3");

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("primary manifest is not supported for manifest type");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_fixedRef_dropsOtherChartsWithoutRendering() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifests =
        ManifestWrapper.builder()
            .sources(new ArrayList<>(List.of(buildHelmChartManifest("manifest1"), buildHelmChartManifest("manifest2"))))
            .primary(ParameterField.createValueField(ManifestConfig.builder().id("manifest1").build()))
            .build();
    ServiceConfig config = buildServiceConfigWithManifests(manifests);

    serviceEntityProcessor.handlePrimaryManifest(ambiance, config);

    assertThat(manifests.getSources()).extracting(ManifestConfig::getId).containsExactly("manifest1");
    verify(cdStepsExpressionResolver, never()).renderValue(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifests_afterExpressionPrimaryResolution_typeKeyPointsToResolvedChart() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifests = buildTwoHelmChartsWithExpressionPrimary();
    ServiceConfig config = buildServiceConfigWithManifests(manifests);

    mockPrimaryManifestRefRendering("manifest2");
    mockInfraHelpers();

    serviceEntityProcessor.handlePrimaryManifest(ambiance, config);
    Map<String, Object> result = serviceEntityProcessor.processManifests(ambiance, config);

    // No "Multiple manifests are not allowed" failure, and only the resolved chart is exposed.
    assertThat(result).containsKeys("primary", "helmChart", "manifest2", "manifest3");
    assertThat(result).doesNotContainKey("manifest1");
    assertThat(result.get("primary")).isEqualTo(result.get("manifest2"));
    assertThat(result.get("helmChart")).isEqualTo(result.get("manifest2"));
  }

  // ========== handlePrimaryArtifact: expression based primaryArtifactRef ==========

  private static final String PRIMARY_ARTIFACT_REF_EXPRESSION = "<+serviceVariables.artifactRef>";

  private static ArtifactWrapper buildTwoArtifactSourcesWithExpressionPrimary() {
    return ArtifactWrapper.builder()
        .sources(new ArrayList<>(List.of(buildPrimaryArtifact("docker_primary"), buildPrimaryArtifact("docker_other"),
            buildSidecarArtifact("sidecar1"))))
        .primary(ParameterField.createExpressionField(true, PRIMARY_ARTIFACT_REF_EXPRESSION, null, false))
        .build();
  }

  private void mockPrimaryArtifactRefRendering(String resolvedValue) {
    when(cdStepsExpressionResolver.renderValue(any(), eq(PRIMARY_ARTIFACT_REF_EXPRESSION), eq(true)))
        .thenReturn(resolvedValue);
  }

  private void mockNgOutcomesSweepingOutput(VariablesSweepingOutput ngOutcomes) {
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(ngOutcomes != null).output(ngOutcomes).build());
  }

  private static ArtifactOutcome buildDockerArtifactOutcome(String identifier, String tag) {
    return DockerArtifactOutcome.builder()
        .identifier(identifier)
        .type("Dockerhub")
        .connectorRef("docker-connector")
        .imagePath("harness/my-app")
        .tag(tag)
        .primaryArtifact(true)
        .build();
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_expressionRef_resolvesAndDropsLosingSources() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ArtifactWrapper artifacts = buildTwoArtifactSourcesWithExpressionPrimary();
    ServiceConfig config = buildServiceConfigWithArtifacts(artifacts);

    mockPrimaryArtifactRefRendering("docker_primary");
    mockNgOutcomesSweepingOutput(null);

    String resolvedId = serviceEntityProcessor.handlePrimaryArtifact(ambiance, config);

    assertThat(resolvedId).isEqualTo("docker_primary");
    assertThat(artifacts.getPrimary().isExpression()).isFalse();
    assertThat(artifacts.getPrimary().getValue().getId()).isEqualTo("docker_primary");
    // Static-ref parity: only the winning primary plus the sidecars survive.
    assertThat(artifacts.getSources()).extracting(ArtifactConfig::getId).containsExactly("docker_primary", "sidecar1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_expressionRef_restoresNgOutcomesPrimaryFromCandidates() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithArtifacts(buildTwoArtifactSourcesWithExpressionPrimary());

    ArtifactSourceCandidatesOutcome candidates = new ArtifactSourceCandidatesOutcome();
    candidates.put("docker_primary", buildDockerArtifactOutcome("docker_primary", "v1"));
    candidates.put("docker_other", buildDockerArtifactOutcome("docker_other", "v2"));

    SidecarsOutcome existingSidecars = new SidecarsOutcome();
    existingSidecars.put("sidecar1", buildDockerArtifactOutcome("sidecar1", "s1"));

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.ARTIFACT_SOURCE_CANDIDATES.getName(), YamlUtils.writeYamlString(candidates));
    ngOutcomes.put(NGOutcomes.ARTIFACTS.getName(),
        YamlUtils.writeYamlString(ArtifactsOutcome.builder().sidecars(existingSidecars).build()));

    mockPrimaryArtifactRefRendering("docker_primary");
    mockNgOutcomesSweepingOutput(ngOutcomes);

    serviceEntityProcessor.handlePrimaryArtifact(ambiance, config);

    // The consumed candidates key must not leak to any downstream consumer.
    assertThat(ngOutcomes).doesNotContainKey(NGOutcomes.ARTIFACT_SOURCE_CANDIDATES.getName());

    ArtifactsOutcome restored = readArtifactsOutcome((String) ngOutcomes.get(NGOutcomes.ARTIFACTS.getName()));
    assertThat(restored.getPrimary()).isNotNull();
    assertThat(restored.getPrimary().getIdentifier()).isEqualTo("docker_primary");
    assertThat(restored.getPrimary().getTag()).isEqualTo("v1");
    assertThat(restored.getSidecars()).containsKey("sidecar1");

    verify(sweepingOutputService).consumeUpsert(eq(ambiance), eq(NG_OUTCOMES), eq(ngOutcomes), any());
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_expressionRefUnresolved_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithArtifacts(buildTwoArtifactSourcesWithExpressionPrimary());

    mockPrimaryArtifactRefRendering(PRIMARY_ARTIFACT_REF_EXPRESSION);

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryArtifact(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unable to resolve primaryArtifactRef");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_expressionRefMatchesNothing_throwsWithCandidateIds() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithArtifacts(buildTwoArtifactSourcesWithExpressionPrimary());

    mockPrimaryArtifactRefRendering("docker_unknown");

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryArtifact(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("docker_unknown")
        .hasMessageContaining("docker_primary, docker_other");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_expressionRefMatchesSidecar_throws() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithArtifacts(buildTwoArtifactSourcesWithExpressionPrimary());

    mockPrimaryArtifactRefRendering("sidecar1");

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryArtifact(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("matches a sidecar artifact");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_fixedRef_resolvesWithoutRendering() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ArtifactConfig primaryRef = ArtifactConfig.builder().id("docker_other").build();
    ArtifactWrapper artifacts = ArtifactWrapper.builder()
                                    .sources(new ArrayList<>(List.of(buildPrimaryArtifact("docker_primary"),
                                        buildPrimaryArtifact("docker_other"), buildSidecarArtifact("sidecar1"))))
                                    .primary(ParameterField.createValueField(primaryRef))
                                    .build();
    ServiceConfig config = buildServiceConfigWithArtifacts(artifacts);

    mockNgOutcomesSweepingOutput(null);

    assertThat(serviceEntityProcessor.handlePrimaryArtifact(ambiance, config)).isEqualTo("docker_other");
    assertThat(artifacts.getSources()).extracting(ArtifactConfig::getId).containsExactly("docker_other", "sidecar1");
    verify(cdStepsExpressionResolver, never()).renderValue(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_noArtifacts_returnsNull() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config =
        buildServiceConfigWithManifests(ManifestWrapper.builder().sources(List.of(buildK8sManifest("k8s_m"))).build());

    assertThat(serviceEntityProcessor.handlePrimaryArtifact(ambiance, config)).isNull();
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_unfilledRuntimeInputWithMultipleSources_throwsActionableError() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ArtifactWrapper artifacts = ArtifactWrapper.builder()
                                    .sources(new ArrayList<>(List.of(buildPrimaryArtifact("docker_primary"),
                                        buildPrimaryArtifact("docker_other"), buildSidecarArtifact("sidecar1"))))
                                    .primary(ParameterField.createExpressionField(true, "<+input>", null, true))
                                    .build();
    ServiceConfig config = buildServiceConfigWithArtifacts(artifacts);

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryArtifact(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("primaryArtifactRef was left as a runtime input")
        .hasMessageContaining("docker_primary, docker_other");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryArtifact_unfilledRuntimeInputWithSingleSource_returnsNull() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ArtifactWrapper artifacts =
        ArtifactWrapper.builder()
            .sources(new ArrayList<>(List.of(buildPrimaryArtifact("docker_primary"), buildSidecarArtifact("sidecar1"))))
            .primary(ParameterField.createExpressionField(true, "<+input>", null, true))
            .build();
    ServiceConfig config = buildServiceConfigWithArtifacts(artifacts);

    // Single non sidecar source: nothing to disambiguate, downstream picks it exactly as before.
    assertThat(serviceEntityProcessor.handlePrimaryArtifact(ambiance, config)).isNull();
    assertThat(artifacts.getSources()).extracting(ArtifactConfig::getId).containsExactly("docker_primary", "sidecar1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_unfilledRuntimeInputWithMultipleCharts_throwsActionableError() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifests =
        ManifestWrapper.builder()
            .sources(new ArrayList<>(List.of(buildHelmChartManifest("manifest1"), buildHelmChartManifest("manifest2"),
                buildValuesManifest("manifest3", List.of("values.yaml")))))
            .primary(ParameterField.createExpressionField(true, "<+input>", null, true))
            .build();
    ServiceConfig config = buildServiceConfigWithManifests(manifests);

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("primaryManifestRef was left as a runtime input")
        .hasMessageContaining("manifest1, manifest2");
  }

  private static Map<String, Object> buildManifestOutcomeNode(String identifier, String type) {
    Map<String, Object> manifestOutcome = new LinkedHashMap<>();
    manifestOutcome.put("identifier", identifier);
    manifestOutcome.put("type", type);
    return manifestOutcome;
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRef_restoresNgOutcomesPrimaryFromCandidates() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    Map<String, Object> candidates = new LinkedHashMap<>();
    candidates.put("manifest1", buildManifestOutcomeNode("manifest1", "HelmChart"));
    candidates.put("manifest2", buildManifestOutcomeNode("manifest2", "HelmChart"));
    candidates.put("manifest3", buildManifestOutcomeNode("manifest3", "Values"));

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName(), YamlUtils.writeYamlString(candidates));
    ngOutcomes.put(NGOutcomes.MANIFESTS.getName(), YamlUtils.writeYamlString(candidates));

    mockPrimaryManifestRefRendering("manifest2");
    mockNgOutcomesSweepingOutput(ngOutcomes);

    assertThat(serviceEntityProcessor.handlePrimaryManifest(ambiance, config)).isEqualTo("manifest2");

    // The consumed candidates key must not leak to any downstream consumer.
    assertThat(ngOutcomes).doesNotContainKey(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());

    Map<String, Object> restored =
        YamlParsingUtils.parseYamlStringToMap((String) ngOutcomes.get(NGOutcomes.MANIFESTS.getName()));
    // Static-ref parity: the losing chart is dropped, other manifest types survive, primary points at the winner.
    assertThat(restored).containsKeys("primary", "manifest2", "manifest3");
    assertThat(restored).doesNotContainKey("manifest1");
    assertThat(restored.get("primary")).isEqualTo(restored.get("manifest2"));

    verify(sweepingOutputService).consumeUpsert(eq(ambiance), eq(NG_OUTCOMES), eq(ngOutcomes), any());
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_expressionRefMatchesNoCandidate_throwsWithCandidateIds() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestWrapper manifests =
        ManifestWrapper.builder()
            .sources(new ArrayList<>(List.of(buildHelmChartManifest("manifest1"), buildHelmChartManifest("manifest2"))))
            .primary(ParameterField.createExpressionField(true, PRIMARY_MANIFEST_REF_EXPRESSION, null, false))
            .build();
    ServiceConfig config = buildServiceConfigWithManifests(manifests);

    Map<String, Object> candidates = new LinkedHashMap<>();
    candidates.put("manifest1", buildManifestOutcomeNode("manifest1", "HelmChart"));

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName(), YamlUtils.writeYamlString(candidates));

    mockPrimaryManifestRefRendering("manifest2");
    mockNgOutcomesSweepingOutput(ngOutcomes);

    assertThatThrownBy(() -> serviceEntityProcessor.handlePrimaryManifest(ambiance, config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("manifest2")
        .hasMessageContaining("Available manifests: [manifest1]");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandlePrimaryManifest_noCandidates_leavesNgOutcomesUntouched() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig config = buildServiceConfigWithManifests(buildTwoHelmChartsWithExpressionPrimary());

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    String manifestsYaml =
        YamlUtils.writeYamlString(Map.of("manifest1", buildManifestOutcomeNode("manifest1", "HelmChart")));
    ngOutcomes.put(NGOutcomes.MANIFESTS.getName(), manifestsYaml);

    mockPrimaryManifestRefRendering("manifest2");
    mockNgOutcomesSweepingOutput(ngOutcomes);

    assertThat(serviceEntityProcessor.handlePrimaryManifest(ambiance, config)).isEqualTo("manifest2");
    assertThat(ngOutcomes.get(NGOutcomes.MANIFESTS.getName())).isEqualTo(manifestsYaml);
    verify(sweepingOutputService, never()).consumeUpsert(any(), eq(NG_OUTCOMES), any(), any());
  }

  private ArtifactsOutcome readArtifactsOutcome(String yaml) {
    try {
      return YamlUtils.read(yaml, ArtifactsOutcome.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid artifacts outcome yaml", e);
    }
  }

  // ========== processServicePluginInfo: exposes pluginInfo under serviceOutput root ==========

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessServicePluginInfo_nullSpecMap_returnsEmpty() {
    Map<String, Object> result = serviceEntityProcessor.processServicePluginInfo(null);

    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessServicePluginInfo_noPluginInfoKey_returnsEmpty() {
    Map<String, Object> specMap = new HashMap<>();
    specMap.put("artifacts", new HashMap<>());

    Map<String, Object> result = serviceEntityProcessor.processServicePluginInfo(specMap);

    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessServicePluginInfo_withPluginInfo_returnsValues() {
    Map<String, Object> pluginInfo = new HashMap<>();
    pluginInfo.put("runtimeLanguage", "python3.12");
    pluginInfo.put("serverlessVersion", "3.39.0");
    Map<String, Object> specMap = new HashMap<>();
    specMap.put("pluginInfo", pluginInfo);

    Map<String, Object> result = serviceEntityProcessor.processServicePluginInfo(specMap);

    assertThat(result).containsEntry("runtimeLanguage", "python3.12");
    assertThat(result).containsEntry("serverlessVersion", "3.39.0");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testProcessServicePluginInfo_pluginInfoNotMap_returnsEmpty() {
    Map<String, Object> specMap = new HashMap<>();
    specMap.put("pluginInfo", "python3.12");

    Map<String, Object> result = serviceEntityProcessor.processServicePluginInfo(specMap);

    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  private ServiceConfig toServiceConfig(String serviceYaml) {
    try {
      return YamlUtils.read(serviceYaml, ServiceConfig.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid service yaml", e);
    }
  }

  // ========== buildV0EnvironmentOutcome: the V0 outcome exposed under the `env` expression root ==========

  @Test
  @Owner(developers = TANAY_KESHARWANI)
  @Category(UnitTests.class)
  public void testBuildV0EnvironmentOutcome_readsYamlAndAppliesOverrideVariables() {
    String envOutcomeYaml = "name: Prod Env\n"
        + "identifier: prod\n"
        + "description: My prod environment\n"
        + "type: Production\n"
        + "tags:\n"
        + "  team: cd\n"
        + "environmentRef: account.prod\n"
        + "envGroupRef: account.prod-group\n"
        + "envGroupName: Prod Group\n";

    io.harness.steps.environment.EnvironmentOutcome outcome =
        serviceEntityProcessor.buildV0EnvironmentOutcome(envOutcomeYaml, Collections.singletonMap("var1", "val1"));

    assertThat(outcome.getName()).isEqualTo("Prod Env");
    assertThat(outcome.getIdentifier()).isEqualTo("prod");
    assertThat(outcome.getDescription()).isEqualTo("My prod environment");
    assertThat(outcome.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(outcome.getTags()).containsEntry("team", "cd");
    assertThat(outcome.getEnvironmentRef()).isEqualTo("account.prod");
    assertThat(outcome.getEnvGroupRef()).isEqualTo("account.prod-group");
    assertThat(outcome.getEnvGroupName()).isEqualTo("Prod Group");
    assertThat(outcome.getVariables()).containsEntry("var1", "val1");
    // Building the outcome must not write anything; the caller owns the `env` write.
    verify(sweepingOutputService, never()).consumeUpsert(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TANAY_KESHARWANI)
  @Category(UnitTests.class)
  public void testBuildV0EnvironmentOutcome_absentYaml_returnsNull() {
    for (String envOutcomeYaml : new String[] {"", null}) {
      assertThat(serviceEntityProcessor.buildV0EnvironmentOutcome(envOutcomeYaml, Collections.emptyMap())).isNull();
    }
  }

  @Test
  @Owner(developers = TANAY_KESHARWANI)
  @Category(UnitTests.class)
  public void testBuildV0EnvironmentOutcome_unparseableYaml_returnsNull() {
    assertThat(serviceEntityProcessor.buildV0EnvironmentOutcome("\tnot: [valid", Collections.emptyMap())).isNull();
  }

  @Test
  @Owner(developers = TANAY_KESHARWANI)
  @Category(UnitTests.class)
  public void testBuildV0EnvironmentOutcome_emptyYamlTagsStayEmpty() {
    // An environment with no tags keeps rendering `env.tags` as an empty map, not null.
    io.harness.steps.environment.EnvironmentOutcome outcome =
        serviceEntityProcessor.buildV0EnvironmentOutcome("identifier: prod\ntags: {}\n", Collections.emptyMap());

    assertThat(outcome.getTags()).isNotNull().isEmpty();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
