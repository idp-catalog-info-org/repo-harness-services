/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestSpec;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.ManifestWrapper;
import io.harness.unified.cd.service.overrides.OverridesConfig;
import io.harness.unified.cd.service.overrides.OverridesInfoConfig;
import io.harness.unified.cd.service.overrides.OverridesWrapperDTO;
import io.harness.unified.cd.service.spec.KubernetesServiceSpec;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceSpec;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.TemplateYamlGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OverrideApplyHelperTest {
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private OverrideVariablesHelper overrideVariablesHelper;
  @Mock private TemplateYamlGenerator templateYamlGenerator;
  @InjectMocks private OverrideApplyHelper overrideApplyHelper;

  // ---- handleOverrides tests ----

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleOverridesWithEmptyOverrides() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig serviceConfig = buildSimpleServiceConfig();

    overrideApplyHelper.handleOverrides(ambiance, serviceConfig, new HashMap<>());

    verifyNoInteractions(sweepingOutputService);
    verifyNoInteractions(cdStepsExpressionResolver);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleOverridesWithNullOverrides() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ServiceConfig serviceConfig = buildSimpleServiceConfig();

    overrideApplyHelper.handleOverrides(ambiance, serviceConfig, null);

    verifyNoInteractions(sweepingOutputService);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleOverridesWithManifestOverride() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    // Service config with K8S manifest in sources
    ManifestSpec k8sSpec = mock(ManifestSpec.class);
    ManifestConfig k8sManifest = ManifestConfig.builder().id("k8s1").uses(ManifestType.K8S).with(k8sSpec).build();
    ManifestWrapper manifestWrapper = ManifestWrapper.builder().sources(new ArrayList<>(List.of(k8sManifest))).build();

    ServiceSpec mockSpec = mock(ServiceSpec.class);
    when(mockSpec.getManifests()).thenReturn(manifestWrapper);
    ServiceConfig serviceConfig = buildServiceConfigWithSpec(mockSpec);

    // Override with VALUES manifest (supported type)
    ManifestSpec valuesSpec = mock(ManifestSpec.class);
    ManifestConfig valuesManifest =
        ManifestConfig.builder().id("values1").uses(ManifestType.VALUES).with(valuesSpec).build();

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides =
        buildOverrideWithManifests(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, List.of(valuesManifest));

    overrideApplyHelper.handleOverrides(ambiance, serviceConfig, overrides);

    verify(mockSpec).updateManifestsOverride(any());
    verify(cdStepsExpressionResolver).updateExpressions(any(), any(ServiceConfig.class), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleOverridesManifestUpdateWithNullSources() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    ServiceSpec mockSpec = mock(ServiceSpec.class);
    ManifestWrapper emptyWrapper = ManifestWrapper.builder().sources(null).build();
    when(mockSpec.getManifests()).thenReturn(emptyWrapper);
    ServiceConfig serviceConfig = buildServiceConfigWithSpec(mockSpec);

    ManifestSpec valuesSpec = mock(ManifestSpec.class);
    ManifestConfig valuesManifest =
        ManifestConfig.builder().id("values1").uses(ManifestType.VALUES).with(valuesSpec).build();

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides =
        buildOverrideWithManifests(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, List.of(valuesManifest));

    overrideApplyHelper.handleOverrides(ambiance, serviceConfig, overrides);

    // updateManifestsOverride should NOT be called since sources is empty
    verify(mockSpec, times(0)).updateManifestsOverride(any());
  }

  // ---- getCombinedInputs tests ----

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsWithServiceInputs() {
    Map<String, Object> serviceInputs = new HashMap<>();
    Map<String, Object> varEntry = new HashMap<>();
    varEntry.put("value", "hello");
    serviceInputs.put("myVar", varEntry);

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();

    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, serviceInputs);
    assertThat(combined).containsKey("myVar");
    assertThat(((Map<?, ?>) combined.get("myVar")).get("value")).isEqualTo("hello");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsWithOverrides() {
    Map<String, Object> serviceInputs = new HashMap<>();
    Map<String, Object> varEntry = new HashMap<>();
    varEntry.put("value", "original");
    serviceInputs.put("myVar", varEntry);

    Map<String, Object> overrideInputs = new HashMap<>();
    Map<String, Object> overrideVarEntry = new HashMap<>();
    overrideVarEntry.put("value", "overridden");
    overrideInputs.put("myVar", overrideVarEntry);

    OverridesInfoConfig overridesInfoConfig = OverridesInfoConfig.builder().inputs(overrideInputs).build();
    OverridesConfig overridesConfig = OverridesConfig.builder().overridesInfoConfig(overridesInfoConfig).build();
    OverridesWrapperDTO wrapper = OverridesWrapperDTO.builder()
                                      .config(overridesConfig)
                                      .identifier("override1")
                                      .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                      .environmentRef("env1")
                                      .build();

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();
    overrides.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, wrapper);

    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, serviceInputs);
    assertThat(((Map<?, ?>) combined.get("myVar")).get("value")).isEqualTo("overridden");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsWithEmptyOverrides() {
    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();
    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, null);
    assertThat(combined).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsOverridePriority() {
    Map<String, Object> serviceInputs = new HashMap<>();
    Map<String, Object> svcVarEntry = new HashMap<>();
    svcVarEntry.put("value", "service-value");
    serviceInputs.put("sharedVar", svcVarEntry);

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();

    // ENV_GLOBAL_OVERRIDE (lower priority)
    Map<String, Object> envInputs = new HashMap<>();
    Map<String, Object> envVarEntry = new HashMap<>();
    envVarEntry.put("value", "env-global");
    envInputs.put("sharedVar", envVarEntry);
    overrides.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE,
        buildOverrideWrapperForInputs(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, envInputs));

    // INFRA_SERVICE_OVERRIDE (highest priority)
    Map<String, Object> infraSvcInputs = new HashMap<>();
    Map<String, Object> infraSvcVarEntry = new HashMap<>();
    infraSvcVarEntry.put("value", "infra-svc");
    infraSvcInputs.put("sharedVar", infraSvcVarEntry);
    overrides.put(ServiceOverridesType.INFRA_SERVICE_OVERRIDE,
        buildOverrideWrapperForInputs(ServiceOverridesType.INFRA_SERVICE_OVERRIDE, infraSvcInputs));

    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, serviceInputs);
    // INFRA_SERVICE_OVERRIDE is last in OVERRIDE_IN_REVERSE_PRIORITY, so its value wins
    assertThat(((Map<?, ?>) combined.get("sharedVar")).get("value")).isEqualTo("infra-svc");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsFiltersNonMapEntries() {
    Map<String, Object> serviceInputs = new HashMap<>();
    serviceInputs.put("stringEntry", "not-a-map");
    Map<String, Object> validEntry = new HashMap<>();
    validEntry.put("value", "valid");
    serviceInputs.put("validVar", validEntry);

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();

    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, serviceInputs);
    assertThat(combined).doesNotContainKey("stringEntry");
    assertThat(combined).containsKey("validVar");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCombinedInputsFiltersEntriesWithoutValueKey() {
    Map<String, Object> serviceInputs = new HashMap<>();
    Map<String, Object> noValueEntry = new HashMap<>();
    noValueEntry.put("type", "string");
    serviceInputs.put("noValueVar", noValueEntry);

    Map<String, Object> validEntry = new HashMap<>();
    validEntry.put("value", "valid");
    serviceInputs.put("validVar", validEntry);

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();

    Map<String, Object> combined = OverrideVariablesHelper.getCombinedInputs(overrides, serviceInputs);
    assertThat(combined).doesNotContainKey("noValueVar");
    assertThat(combined).containsKey("validVar");
  }

  // ---- getOutputVariables tests ----

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOutputVariablesWithStringValue() {
    Map<String, Object> inputs = new HashMap<>();
    Map<String, Object> entry = new HashMap<>();
    entry.put("value", "test-value");
    entry.put("type", "string");
    inputs.put("myVar", entry);

    VariablesSweepingOutput output = OverrideVariablesHelper.getOutputVariables(inputs);
    assertThat(output.get("myVar")).isEqualTo("test-value");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOutputVariablesWithSecretValue() {
    Map<String, Object> inputs = new HashMap<>();
    Map<String, Object> entry = new HashMap<>();
    entry.put("value", "account.mySecret");
    entry.put("type", "secret");
    inputs.put("secretVar", entry);

    VariablesSweepingOutput output = OverrideVariablesHelper.getOutputVariables(inputs);
    assertThat(output.get("secretVar")).isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOutputVariablesWithInvalidInput() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("invalid", "not-a-map");

    assertThatThrownBy(() -> OverrideVariablesHelper.getOutputVariables(inputs))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Incorrect yaml provided for variables");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOutputVariablesEmpty() {
    Map<String, Object> inputs = new HashMap<>();
    VariablesSweepingOutput output = OverrideVariablesHelper.getOutputVariables(inputs);
    assertThat(output).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetOutputVariablesWithMixedTypes() {
    Map<String, Object> inputs = new HashMap<>();

    Map<String, Object> stringEntry = new HashMap<>();
    stringEntry.put("value", "hello");
    stringEntry.put("type", "string");
    inputs.put("strVar", stringEntry);

    Map<String, Object> secretEntry = new HashMap<>();
    secretEntry.put("value", "org.mySecret");
    secretEntry.put("type", "secret");
    inputs.put("secVar", secretEntry);

    Map<String, Object> noTypeEntry = new HashMap<>();
    noTypeEntry.put("value", "plain");
    inputs.put("plainVar", noTypeEntry);

    VariablesSweepingOutput output = OverrideVariablesHelper.getOutputVariables(inputs);
    assertThat(output).hasSize(3);
    assertThat(output.get("strVar")).isEqualTo("hello");
    assertThat(output.get("secVar")).isNotNull();
    assertThat(output.get("plainVar")).isEqualTo("plain");
  }

  // ---- Helper methods ----

  private ServiceConfig buildSimpleServiceConfig() {
    KubernetesServiceSpec spec =
        KubernetesServiceSpec.builder().manifests(ManifestWrapper.builder().sources(new ArrayList<>()).build()).build();
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(spec).build();
    return ServiceConfig.builder().serviceInfoConfig(infoConfig).build();
  }

  private ServiceConfig buildServiceConfigWithSpec(ServiceSpec spec) {
    ServiceInfoConfig infoConfig = ServiceInfoConfig.builder().with(spec).build();
    return ServiceConfig.builder().serviceInfoConfig(infoConfig).build();
  }

  private Map<ServiceOverridesType, OverridesWrapperDTO> buildVariablesOnlyOverride(
      String varName, String value, String type) {
    Map<String, Object> overrideInputs = new HashMap<>();
    Map<String, Object> varEntry = new HashMap<>();
    varEntry.put("value", value);
    varEntry.put("type", type);
    overrideInputs.put(varName, varEntry);

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();
    overrides.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE,
        buildOverrideWrapperForInputs(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, overrideInputs));
    return overrides;
  }

  private OverridesWrapperDTO buildOverrideWrapperForInputs(ServiceOverridesType type, Map<String, Object> inputs) {
    OverridesInfoConfig overridesInfoConfig = OverridesInfoConfig.builder().inputs(inputs).build();
    OverridesConfig overridesConfig = OverridesConfig.builder().overridesInfoConfig(overridesInfoConfig).build();
    return OverridesWrapperDTO.builder()
        .config(overridesConfig)
        .identifier("override-" + type.name())
        .type(type)
        .environmentRef("env1")
        .build();
  }

  private Map<ServiceOverridesType, OverridesWrapperDTO> buildOverrideWithManifests(
      ServiceOverridesType type, List<ManifestConfig> manifests) {
    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();
    overrides.put(type, buildOverrideWrapper(type, new HashMap<>(), manifests, null));
    return overrides;
  }

  private Map<ServiceOverridesType, OverridesWrapperDTO> buildOverrideWithConfigFiles(
      ServiceOverridesType type, List<ConfigFile> configFiles) {
    Map<ServiceOverridesType, OverridesWrapperDTO> overrides = new HashMap<>();
    overrides.put(type, buildOverrideWrapper(type, new HashMap<>(), null, configFiles));
    return overrides;
  }

  private OverridesWrapperDTO buildOverrideWrapper(ServiceOverridesType type, Map<String, Object> inputs,
      List<ManifestConfig> manifests, List<ConfigFile> configFiles) {
    OverridesInfoConfig overridesInfoConfig =
        OverridesInfoConfig.builder().inputs(inputs).manifests(manifests).configFiles(configFiles).build();
    OverridesConfig overridesConfig = OverridesConfig.builder().overridesInfoConfig(overridesInfoConfig).build();
    return OverridesWrapperDTO.builder()
        .config(overridesConfig)
        .identifier("override-" + type.name())
        .type(type)
        .environmentRef("env1")
        .build();
  }
}
