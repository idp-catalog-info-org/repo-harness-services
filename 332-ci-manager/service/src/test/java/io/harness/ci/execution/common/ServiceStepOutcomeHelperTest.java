/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.cd.beans.outcomes.CdOutcomeConstants.ARTIFACTS_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.CONFIG_FILES_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.MANIFEST_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.SERVICE_OUTCOME_EXPRESSION;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ManifestOutputVarsSweepingOutput;
import io.harness.cd.beans.outcomes.ManifestsOutcome;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.infrastructure.unified.UnifiedEnvConvertorResponse;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.unified.service.NGOutcomes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class ServiceStepOutcomeHelperTest {
  private static final String ACCOUNT_ID = "account";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "project";
  private static final String ENV_IDENTIFIER = "env1";
  private static final String SERVICE_REF = "svc1";

  /** YAML with primary manifest id m1 (used by NG manifests path). */
  private static final String MANIFESTS_YAML_PRIMARY = "primary:\n"
      + "  identifier: m1\n";

  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentEntityService environmentEntityService;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private EnvironmentResourceClient environmentResourceClient;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @InjectMocks private ServiceStepOutcomeHelper serviceStepOutcomeHelper;

  private static Ambiance ambiance() {
    return Ambiance.newBuilder().build();
  }

  private static VariablesSweepingOutput ngOutcomes(String key, String yaml) {
    VariablesSweepingOutput output = new VariablesSweepingOutput();
    output.put(key, yaml);
    return output;
  }

  private static void assertSingleOutcomeNamed(List<StepResponse.StepOutcome> stepOutcomes, String expectedName) {
    assertThat(stepOutcomes).hasSize(1);
    assertThat(stepOutcomes.get(0).getName()).isEqualTo(expectedName);
  }

  // region getServiceEntity

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetServiceEntity() {
    ServiceEntity entity = ServiceEntity.builder().identifier(SERVICE_REF).name("Service 1").build();
    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF)).thenReturn(Optional.of(entity));

    Optional<ServiceEntity> result =
        serviceStepOutcomeHelper.getServiceEntity(SERVICE_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getIdentifier()).isEqualTo(SERVICE_REF);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetServiceEntityNotFound() {
    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF)).thenReturn(Optional.empty());

    Optional<ServiceEntity> result =
        serviceStepOutcomeHelper.getServiceEntity(SERVICE_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(result).isEmpty();
  }

  // endregion

  // region addArtifactsStepOutcome

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddArtifactsStepOutcomeWithoutNgOutcomes() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ArtifactsOutcome artifactsOutcome = new ArtifactsOutcome(new HashMap<>());

    serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, artifactsOutcome, null);

    assertThat(stepOutcomes).hasSize(1);
    assertThat(stepOutcomes.get(0).getOutcome()).isEqualTo(artifactsOutcome);
    assertThat(stepOutcomes.get(0).getName()).isEqualTo(ARTIFACTS_OUTCOME_EXPRESSION);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddArtifactsStepOutcomeFromNgYaml() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ArtifactsOutcome fallback = new ArtifactsOutcome(new HashMap<>());
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.ARTIFACTS.getName(), "artifactId: a1\n");

    serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, fallback, ng);

    assertSingleOutcomeNamed(stepOutcomes, ARTIFACTS_OUTCOME_EXPRESSION);
    assertThat(stepOutcomes.get(0).getOutcome()).isInstanceOf(ArtifactsOutcome.class);
    ArtifactsOutcome out = (ArtifactsOutcome) stepOutcomes.get(0).getOutcome();
    assertThat(out).containsEntry("artifactId", "a1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddArtifactsStepOutcomeNgKeyPresentButEmptyYamlUsesFallback() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ArtifactsOutcome fallback = new ArtifactsOutcome(Map.of("k", "v"));
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.ARTIFACTS.getName(), "");

    serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, fallback, ng);

    assertThat(stepOutcomes).hasSize(1);
    assertThat(stepOutcomes.get(0).getOutcome()).isSameAs(fallback);
  }

  // endregion

  // region addConfigFilesStepOutcome

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddConfigFilesStepOutcomeWithoutNgOutcomes() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome(new HashMap<>());

    serviceStepOutcomeHelper.addConfigFilesStepOutcome(stepOutcomes, configFilesOutcome, null);

    assertThat(stepOutcomes).hasSize(1);
    assertThat(stepOutcomes.get(0).getOutcome()).isEqualTo(configFilesOutcome);
    assertThat(stepOutcomes.get(0).getName()).isEqualTo(CONFIG_FILES_OUTCOME_EXPRESSION);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddConfigFilesStepOutcomeFromNgYaml() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ConfigFilesOutcome fallback = new ConfigFilesOutcome(new HashMap<>());
    VariablesSweepingOutput ng = ngOutcomes("configFiles", "path: /tmp/cfg\n");

    serviceStepOutcomeHelper.addConfigFilesStepOutcome(stepOutcomes, fallback, ng);

    assertSingleOutcomeNamed(stepOutcomes, CONFIG_FILES_OUTCOME_EXPRESSION);
    assertThat(stepOutcomes.get(0).getOutcome()).isInstanceOf(VariablesSweepingOutput.class);
    VariablesSweepingOutput out = (VariablesSweepingOutput) stepOutcomes.get(0).getOutcome();
    assertThat(out.get("path")).isEqualTo("/tmp/cfg");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddConfigFilesStepOutcomeNgYamlParsesToEmptyMapReturnsWithoutFallback() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    ConfigFilesOutcome fallback = new ConfigFilesOutcome(new HashMap<>());
    VariablesSweepingOutput ng = ngOutcomes("configFiles", "{}");

    serviceStepOutcomeHelper.addConfigFilesStepOutcome(stepOutcomes, fallback, ng);

    assertThat(stepOutcomes).isEmpty();
  }

  // endregion

  // region addServiceOutcome

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceOutcomeWithoutNgOutcomes() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    UnifiedServiceOutcome serviceOutcome =
        UnifiedServiceOutcome.builder().identifier(SERVICE_REF).name("my-service").build();
    serviceStepOutcomeHelper.addServiceOutcome(stepOutcomes, serviceOutcome, null);

    assertSingleOutcomeNamed(stepOutcomes, SERVICE_OUTCOME_EXPRESSION);
    assertThat(stepOutcomes.get(0).getOutcome()).isEqualTo(serviceOutcome);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceOutcomeFromNgYaml() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    UnifiedServiceOutcome fallback = UnifiedServiceOutcome.builder().identifier("fb").build();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.SERVICE.getName(), "name: svc-ng\n");

    serviceStepOutcomeHelper.addServiceOutcome(stepOutcomes, fallback, ng);

    assertSingleOutcomeNamed(stepOutcomes, SERVICE_OUTCOME_EXPRESSION);
    VariablesSweepingOutput out = (VariablesSweepingOutput) stepOutcomes.get(0).getOutcome();
    assertThat(out.get("name")).isEqualTo("svc-ng");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceOutcomeNgYamlEmptyMapFallsBackToUnified() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    UnifiedServiceOutcome fallback = UnifiedServiceOutcome.builder().identifier("fb").name("fb-name").build();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.SERVICE.getName(), "{}");
    serviceStepOutcomeHelper.addServiceOutcome(stepOutcomes, fallback, ng);

    assertSingleOutcomeNamed(stepOutcomes, SERVICE_OUTCOME_EXPRESSION);
    assertThat(stepOutcomes.get(0).getOutcome()).isEqualTo(fallback);
  }

  // endregion

  // region addManifestsStepOutcome

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomeWithNullNgOutcomes() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    when(serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, null);

    assertThat(stepOutcomes).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomeFromNgYamlWithoutPopulateWhenSweepingNotFound() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.MANIFESTS.getName(), MANIFESTS_YAML_PRIMARY);
    when(serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, ng);

    assertSingleOutcomeNamed(stepOutcomes, MANIFEST_OUTCOME_EXPRESSION);
    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) stepOutcomes.get(0).getOutcome();
    assertThat(manifestsOutcome).containsKey(ServiceStepOutcomeHelper.PRIMARY);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomePopulatePrimaryManifestInjectsDownloadPaths() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.MANIFESTS.getName(), MANIFESTS_YAML_PRIMARY);
    ManifestOutputVarsSweepingOutput varsOutput =
        ManifestOutputVarsSweepingOutput.builder()
            .manifestsOutputVars(Map.of("m1",
                Map.of(ServiceStepOutcomeHelper.ARTIFACT_DOWNLOAD_PATH, "/art",
                    ServiceStepOutcomeHelper.PLUGIN_ARTIFACT_DOWNLOAD_PATH, "/plugin")))
            .build();
    when(serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(varsOutput).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, ng);

    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) stepOutcomes.get(0).getOutcome();
    @SuppressWarnings("unchecked")
    Map<String, Object> primary = (Map<String, Object>) manifestsOutcome.get(ServiceStepOutcomeHelper.PRIMARY);
    assertThat(primary.get(ServiceStepOutcomeHelper.ARTIFACT_DOWNLOAD_PATH)).isEqualTo("/art");
    assertThat(primary.get(ServiceStepOutcomeHelper.PLUGIN_ARTIFACT_DOWNLOAD_PATH)).isEqualTo("/plugin");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomePopulatePrimaryManifestSkipsWhenVarsMissingForId() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.MANIFESTS.getName(), MANIFESTS_YAML_PRIMARY);
    ManifestOutputVarsSweepingOutput varsOutput =
        ManifestOutputVarsSweepingOutput.builder()
            .manifestsOutputVars(Map.of("otherId", Map.of(ServiceStepOutcomeHelper.ARTIFACT_DOWNLOAD_PATH, "/x")))
            .build();
    when(serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(varsOutput).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, ng);

    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) stepOutcomes.get(0).getOutcome();
    @SuppressWarnings("unchecked")
    Map<String, Object> primary = (Map<String, Object>) manifestsOutcome.get(ServiceStepOutcomeHelper.PRIMARY);
    assertThat(primary.containsKey(ServiceStepOutcomeHelper.ARTIFACT_DOWNLOAD_PATH)).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomeEmptyNgYamlFallsThroughToV1Service() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    VariablesSweepingOutput ng = ngOutcomes(NGOutcomes.MANIFESTS.getName(), "");
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> primaryInner = new HashMap<>();
    primaryInner.put("p", "v");
    manifestMap.put(ServiceStepOutcomeHelper.PRIMARY, primaryInner);
    ServiceConfigOutcome serviceConfig = ServiceConfigOutcome.builder()
                                             .manifests(manifestMap)
                                             .artifacts(new HashMap<>())
                                             .configFiles(new HashMap<>())
                                             .build();
    when(serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceConfig).build());
    when(serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, ng);

    assertSingleOutcomeNamed(stepOutcomes, MANIFEST_OUTCOME_EXPRESSION);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddManifestsStepOutcomeV1ServiceMergesManifestOutputVars() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> primaryInner = new HashMap<>();
    primaryInner.put("existing", "keep");
    manifestMap.put(ServiceStepOutcomeHelper.PRIMARY, primaryInner);
    Map<String, String> secondary = new HashMap<>();
    secondary.put("base", "b");
    manifestMap.put("m2", secondary);

    ServiceConfigOutcome serviceConfig = ServiceConfigOutcome.builder()
                                             .manifests(manifestMap)
                                             .artifacts(new HashMap<>())
                                             .configFiles(new HashMap<>())
                                             .build();
    when(serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(serviceConfig).build());

    ManifestOutputVarsSweepingOutput varsOutput = ManifestOutputVarsSweepingOutput.builder()
                                                      .singleDeployManifestOutputVars(Map.of("sdKey", "sdVal"))
                                                      .manifestsOutputVars(Map.of("m2", Map.of("merged", "fromVars")))
                                                      .build();
    when(serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(varsOutput).build());

    serviceStepOutcomeHelper.addManifestsStepOutcome(ambiance(), stepOutcomes, null);

    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) stepOutcomes.get(0).getOutcome();
    @SuppressWarnings("unchecked")
    Map<String, Object> primary = (Map<String, Object>) manifestsOutcome.get(ServiceStepOutcomeHelper.PRIMARY);
    assertThat(primary.get("existing")).isEqualTo("keep");
    assertThat(primary.get("sdKey")).isEqualTo("sdVal");
    assertThat(manifestsOutcome.get("sdKey")).isEqualTo("sdVal");
    @SuppressWarnings("unchecked") Map<String, String> m2 = (Map<String, String>) manifestsOutcome.get("m2");
    assertThat(m2.get("merged")).isEqualTo("fromVars");
    assertThat(m2.get("base")).isEqualTo("b");
  }

  // endregion

  // region getEnvironmentEntity

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentEntityFromLocalService() {
    EnvironmentEntity entity = EnvironmentEntity.builder()
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_ID)
                                   .projectIdentifier(PROJECT_ID)
                                   .identifier(ENV_IDENTIFIER)
                                   .name("Local env")
                                   .build();
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_IDENTIFIER)).thenReturn(Optional.of(entity));

    EnvironmentEntity result =
        serviceStepOutcomeHelper.getEnvironmentEntity(ENV_IDENTIFIER, ACCOUNT_ID, ORG_ID, PROJECT_ID, "main", "repo");

    assertThat(result).isSameAs(entity);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentEntityFromRemoteUnifiedResponse() {
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_IDENTIFIER)).thenReturn(Optional.empty());

    UnifiedEnvironmentConverterResponseDTO dto = UnifiedEnvironmentConverterResponseDTO.builder()
                                                     .identifier(ENV_IDENTIFIER)
                                                     .name("Remote env")
                                                     .type(EnvironmentType.Production)
                                                     .color("#fff")
                                                     .tags(Map.of("t", "1"))
                                                     .build();
    UnifiedEnvConvertorResponse unified = UnifiedEnvConvertorResponse.builder().responseDTO(dto).build();

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvConvertorResponse>> call = mock(Call.class);
    when(environmentResourceClient.convertToUnifiedEnvironment(
             eq(ENV_IDENTIFIER), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("main"), eq("repo")))
        .thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(call)).thenReturn(unified);

      EnvironmentEntity result =
          serviceStepOutcomeHelper.getEnvironmentEntity(ENV_IDENTIFIER, ACCOUNT_ID, ORG_ID, PROJECT_ID, "main", "repo");

      assertThat(result.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
      assertThat(result.getName()).isEqualTo("Remote env");
      assertThat(result.getType()).isEqualTo(EnvironmentType.Production);
      assertThat(result.getColor()).isEqualTo("#fff");
      assertThat(result.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
      assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentEntityRemoteNullThrows() {
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_IDENTIFIER)).thenReturn(Optional.empty());

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedEnvConvertorResponse>> call = mock(Call.class);
    when(environmentResourceClient.convertToUnifiedEnvironment(
             eq(ENV_IDENTIFIER), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(), any()))
        .thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      ngRest.when(() -> NGRestUtils.getResponse(call)).thenReturn(null);

      assertThatThrownBy(
          () -> serviceStepOutcomeHelper.getEnvironmentEntity(ENV_IDENTIFIER, ACCOUNT_ID, ORG_ID, PROJECT_ID, "b", "r"))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining(ENV_IDENTIFIER);
    }
  }

  // endregion
}
