/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.utils.ArtifactsProcessedResponse;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.service.NGOutcomes;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NgToUnifiedServiceHelperTest extends CategoryTest {
  // The validation logic under test does not rely on any of the injected collaborators, so a CALLS_REAL_METHODS mock
  // lets us exercise the real methods without invoking the (heavy) all-args constructor.
  private final NgToUnifiedServiceHelper ngToUnifiedServiceHelper =
      mock(NgToUnifiedServiceHelper.class, CALLS_REAL_METHODS);

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersThrowsForRuntimeInputOptionalValuesYaml() throws Exception {
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              optionalValuesYaml: <+input>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+input>] provided for optionalValuesYaml in service [k8s_service]. It should be a "
            + "boolean value either true or false.");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersThrowsForNonInputExpressionOptionalValuesYaml() throws Exception {
    // Validation is general: any non-boolean value (not just <+input>) must be rejected with the same clear message.
    String yaml = "service:\n"
        + "  name: helm_service\n"
        + "  identifier: helm_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              optionalValuesYaml: <+pipeline.variables.optionalFlag>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+pipeline.variables.optionalFlag>] provided for optionalValuesYaml in service "
            + "[helm_service]. It should be a boolean value either true or false.");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersPassesForBooleanOptionalValuesYaml() throws Exception {
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              optionalValuesYaml: false\n";

    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersThrowsForRuntimeInputSkipResourceVersioning() throws Exception {
    // skipResourceVersioning is validated the same way as optionalValuesYaml.
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              skipResourceVersioning: <+input>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+input>] provided for skipResourceVersioning in service [k8s_service]. It should "
            + "be a boolean value either true or false.");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersThrowsForNonBooleanEnableDeclarativeRollback() throws Exception {
    // enableDeclarativeRollback is validated the same way; any non-boolean value (not just <+input>) must be rejected.
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              enableDeclarativeRollback: <+pipeline.variables.rollbackFlag>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+pipeline.variables.rollbackFlag>] provided for enableDeclarativeRollback in "
            + "service [k8s_service]. It should be a boolean value either true or false.");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersPassesForBooleanSkipResourceVersioningAndEnableDeclarativeRollback()
      throws Exception {
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              skipResourceVersioning: true\n"
        + "              enableDeclarativeRollback: false\n";

    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersThrowsForRuntimeInputBooleanInOtherManifestSwimlanes() throws Exception {
    // The same boolean flags exist on other manifest swimlanes (Kustomize, Openshift) and must be validated too.
    String kustomizeYaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: kustomize_1\n"
        + "            type: Kustomize\n"
        + "            spec:\n"
        + "              enableDeclarativeRollback: <+input>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(kustomizeYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+input>] provided for enableDeclarativeRollback in service [k8s_service]. It "
            + "should be a boolean value either true or false.");

    String openshiftYaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: openshift_1\n"
        + "            type: OpenshiftTemplate\n"
        + "            spec:\n"
        + "              skipResourceVersioning: <+input>\n";

    assertThatThrownBy(() -> ngToUnifiedServiceHelper.validateServiceParameters(openshiftYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value [<+input>] provided for skipResourceVersioning in service [k8s_service]. It should "
            + "be a boolean value either true or false.");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersPassesWhenOptionalValuesYamlAbsent() throws Exception {
    // Manifest without the optionalValuesYaml flag - existing behaviour must be unaffected.
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: manifest_1\n"
        + "            type: K8sManifest\n"
        + "            spec:\n"
        + "              valuesPaths:\n"
        + "                - v1.yaml\n";

    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateServiceParametersIsNullSafeForMissingSections() throws Exception {
    // No service definition / spec / manifests present (other swimlanes may omit these sections).
    String yaml = "service:\n"
        + "  name: bare_service\n"
        + "  identifier: bare_service\n";

    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters(yaml)).doesNotThrowAnyException();
    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters((String) null)).doesNotThrowAnyException();
    assertThatCode(() -> ngToUnifiedServiceHelper.validateServiceParameters("")).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolveUnifiedServiceTypeFromServiceDefinitionTypeMapsAllSupportedTypes() {
    // The type is read straight from the persisted ServiceDefinitionType (no YAML parsing, no outcome building), so
    // resolution is independent of the service's (possibly unresolved) runtime inputs. Every supported type must map
    // to the correct unified swimlane.
    Map<ServiceDefinitionType, ServiceType> expectedByServiceDefinitionType = new LinkedHashMap<>();
    expectedByServiceDefinitionType.put(ServiceDefinitionType.KUBERNETES, ServiceType.KUBERNETES);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.NATIVE_HELM, ServiceType.HELM);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.AWS_SAM, ServiceType.AWS_SAM);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.ECS, ServiceType.ECS);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.ASG, ServiceType.ASG);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.AZURE_WEBAPP, ServiceType.AZURE_WEB_APP);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.AWS_LAMBDA, ServiceType.AWS_LAMBDA);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.SERVERLESS_AWS_LAMBDA, ServiceType.SERVERLESS);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.GOOGLE_CLOUD_RUN, ServiceType.GOOGLE_CLOUD_RUN);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.AZURE_CONTAINER_APPS, ServiceType.AZURE_CONTAINER_APPS);
    expectedByServiceDefinitionType.put(ServiceDefinitionType.AZURE_FUNCTION, ServiceType.AZURE_FUNCTION);

    for (Map.Entry<ServiceDefinitionType, ServiceType> entry : expectedByServiceDefinitionType.entrySet()) {
      assertThat(ngToUnifiedServiceHelper.resolveUnifiedServiceType(entry.getKey()))
          .as("service definition type [%s] should resolve to unified swimlane [%s]", entry.getKey(), entry.getValue())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolveUnifiedServiceTypeFromServiceDefinitionTypeIsNullSafe() {
    // A null type (or a type without a unified mapping) resolves to null, so the caller skips the service for swimlane
    // validation instead of failing.
    assertThat(ngToUnifiedServiceHelper.resolveUnifiedServiceType((ServiceDefinitionType) null)).isNull();
  }

  // ========== processArtifactsInYaml: expression based primaryArtifactRef ==========

  private static String multiSourceArtifactServiceYaml(String primaryArtifactRef) {
    return "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: " + primaryArtifactRef + "\n"
        + "          sources:\n"
        + "            - identifier: docker_primary\n"
        + "              type: DockerRegistry\n"
        + "              spec:\n"
        + "                connectorRef: docker-connector\n"
        + "                imagePath: harness/my-app\n"
        + "                tag: latest\n"
        + "            - identifier: docker_other\n"
        + "              type: DockerRegistry\n"
        + "              spec:\n"
        + "                connectorRef: other-connector\n"
        + "                imagePath: harness/other-app\n"
        + "                tag: latest\n";
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlPassesThroughExpressionPrimaryArtifactRef() throws Exception {
    // An expression ref cannot be resolved at conversion time (no service level context yet), so instead of failing
    // the whole conversion the yaml is passed through unchanged: primaryArtifactRef plus every source survive, and
    // the CI service step resolves them later.
    String yaml = multiSourceArtifactServiceYaml("<+serviceVariables.artifactRef>");

    ArtifactsProcessedResponse response = ngToUnifiedServiceHelper.processArtifactsInYaml(null, yaml, false);

    assertThat(response).isNotNull();
    assertThat(response.getPrimaryArtifactRef()).isNull();
    assertThat(response.getServiceYaml()).contains("primaryArtifactRef");
    assertThat(response.getServiceYaml()).contains("docker_primary");
    assertThat(response.getServiceYaml()).contains("docker_other");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlPassesThroughUnfilledRuntimeInputPrimaryArtifactRef() throws Exception {
    String yaml = multiSourceArtifactServiceYaml("<+input>");

    ArtifactsProcessedResponse response = ngToUnifiedServiceHelper.processArtifactsInYaml(null, yaml, false);

    assertThat(response).isNotNull();
    assertThat(response.getPrimaryArtifactRef()).isNull();
    assertThat(response.getServiceYaml()).contains("docker_primary");
    assertThat(response.getServiceYaml()).contains("docker_other");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlInlinesFixedPrimaryArtifactRef() throws Exception {
    // Unchanged behaviour for a fixed ref: the winning source is inlined into primary and the losing one dropped.
    String yaml = multiSourceArtifactServiceYaml("docker_primary");

    ArtifactsProcessedResponse response = ngToUnifiedServiceHelper.processArtifactsInYaml(null, yaml, false);

    assertThat(response.getPrimaryArtifactRef()).isEqualTo("docker_primary");
    assertThat(response.getServiceYaml()).contains("docker-connector");
    assertThat(response.getServiceYaml()).doesNotContain("primaryArtifactRef");
  }

  // ========== addManifestsOutcome: expression based primaryManifestRef ==========

  private static String multiChartServiceYaml(String primaryManifestRef) {
    return "service:\n"
        + "  name: helm_service\n"
        + "  identifier: helm_service\n"
        + "  serviceDefinition:\n"
        + "    type: NativeHelm\n"
        + "    spec:\n"
        + "      manifestConfigurations:\n"
        + "        primaryManifestRef: " + primaryManifestRef + "\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: chart1\n"
        + "            type: HelmChart\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n"
        + "                  gitFetchType: Branch\n"
        + "                  branch: main\n"
        + "                  folderPath: chart1\n"
        + "        - manifest:\n"
        + "            identifier: chart2\n"
        + "            type: HelmChart\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n"
        + "                  gitFetchType: Branch\n"
        + "                  branch: main\n"
        + "                  folderPath: chart2\n";
  }

  private static ServiceSpec readServiceSpec(String yaml) throws Exception {
    return YamlUtils.read(yaml, NGServiceConfig.class)
        .getNgServiceV2InfoConfig()
        .getServiceDefinition()
        .getServiceSpec();
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testAddManifestsOutcomeEmitsCandidatesForExpressionPrimaryManifestRef() throws Exception {
    // The ref cannot be resolved at conversion time, so every manifest is emitted as a candidate and no primary is
    // guessed: the CI service step promotes the winner once the expression resolves.
    ServiceSpec serviceSpec = readServiceSpec(multiChartServiceYaml("<+serviceVariables.manifestRef>"));
    Map<String, String> ngOutcomes = new LinkedHashMap<>();

    ngToUnifiedServiceHelper.addManifestsOutcome(serviceSpec, new EnumMap<>(ServiceOverridesType.class), ngOutcomes);

    assertThat(ngOutcomes).containsKey(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    String candidatesYaml = ngOutcomes.get(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    assertThat(candidatesYaml).contains("chart1").contains("chart2");
    assertThat(ngOutcomes.get(NGOutcomes.MANIFESTS.getName())).doesNotContain("primary:");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testAddManifestsOutcomeEmitsNoCandidatesForFixedPrimaryManifestRef() throws Exception {
    ServiceSpec serviceSpec = readServiceSpec(multiChartServiceYaml("chart2"));
    Map<String, String> ngOutcomes = new LinkedHashMap<>();

    ngToUnifiedServiceHelper.addManifestsOutcome(serviceSpec, new EnumMap<>(ServiceOverridesType.class), ngOutcomes);

    // Static-ref flow is untouched: the losing chart is filtered out and primary is populated by NG itself.
    assertThat(ngOutcomes).doesNotContainKey(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    String manifestsYaml = ngOutcomes.get(NGOutcomes.MANIFESTS.getName());
    assertThat(manifestsYaml).contains("chart2").doesNotContain("chart1");
  }

  // ========== processManifestsInYaml: single chart primaryManifestRef fallback ==========

  private static String singleChartServiceYaml(String primaryManifestRef) {
    return "service:\n"
        + "  name: helm_service\n"
        + "  identifier: helm_service\n"
        + "  serviceDefinition:\n"
        + "    type: NativeHelm\n"
        + "    spec:\n"
        + "      manifestConfigurations:\n"
        + "        primaryManifestRef: " + primaryManifestRef + "\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: values1\n"
        + "            type: Values\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n"
        + "                  gitFetchType: Branch\n"
        + "                  branch: main\n"
        + "                  paths:\n"
        + "                    - values.yaml\n"
        + "        - manifest:\n"
        + "            identifier: chart1\n"
        + "            type: HelmChart\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n"
        + "                  gitFetchType: Branch\n"
        + "                  branch: main\n"
        + "                  folderPath: chart1\n";
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlDefaultsUnfilledRuntimeInputToTheOnlyChart() throws Exception {
    // A primary only disambiguates between charts. With one chart there is nothing to disambiguate, so the unfilled
    // <+input> is rewritten to that chart and the service continues down the ordinary static-ref path.
    String processed = ngToUnifiedServiceHelper.processManifestsInYaml(singleChartServiceYaml("<+input>"));

    assertThat(processed).contains("primaryManifestRef: chart1");
    assertThat(processed).doesNotContain("<+input>");
    // The manifest list itself is untouched; only the ref changes.
    assertThat(processed).contains("values1").contains("chart1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlDefaultsUnresolvedExpressionToTheOnlyChart() throws Exception {
    // Same for a scope dependent expression, which cannot be evaluated at conversion time either.
    String processed =
        ngToUnifiedServiceHelper.processManifestsInYaml(singleChartServiceYaml("<+serviceVariables.manifestRef>"));

    assertThat(processed).contains("primaryManifestRef: chart1");
    assertThat(processed).doesNotContain("serviceVariables.manifestRef");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlLeavesMultipleChartsUntouched() throws Exception {
    // Regression gate: with 2+ charts the ref is load bearing, so it must survive for the CI service step to resolve.
    String yaml = multiChartServiceYaml("<+serviceVariables.manifestRef>");

    assertThat(ngToUnifiedServiceHelper.processManifestsInYaml(yaml)).isEqualTo(yaml);

    String runtimeInputYaml = multiChartServiceYaml("<+input>");
    assertThat(ngToUnifiedServiceHelper.processManifestsInYaml(runtimeInputYaml)).isEqualTo(runtimeInputYaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlLeavesFixedRefUntouched() throws Exception {
    String yaml = singleChartServiceYaml("chart1");

    assertThat(ngToUnifiedServiceHelper.processManifestsInYaml(yaml)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlLeavesServiceWithoutManifestConfigurationsUntouched() throws Exception {
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: chart1\n"
        + "            type: HelmChart\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n";

    assertThat(ngToUnifiedServiceHelper.processManifestsInYaml(yaml)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlLeavesServiceWithoutChartsUntouched() throws Exception {
    // Zero charts: no primary can be selected in any engine, so the ref is left alone rather than masking the
    // misconfiguration. CI then reports the unresolved ref.
    String yaml = "service:\n"
        + "  name: k8s_service\n"
        + "  identifier: k8s_service\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      manifestConfigurations:\n"
        + "        primaryManifestRef: <+input>\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: values1\n"
        + "            type: Values\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n";

    assertThat(ngToUnifiedServiceHelper.processManifestsInYaml(yaml)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessArtifactsThenManifestsInYamlBothRewritesSurvive() throws Exception {
    // ServiceResourceV2 chains the two yaml passes, so the manifest pass re-reads and rewrites yaml the artifact pass
    // already round tripped. Both defaults must survive and the result must still parse.
    String yaml = "service:\n"
        + "  name: helm_service\n"
        + "  identifier: helm_service\n"
        + "  serviceDefinition:\n"
        + "    type: NativeHelm\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: <+input>\n"
        + "          sources:\n"
        + "            - identifier: docker_only\n"
        + "              type: DockerRegistry\n"
        + "              spec:\n"
        + "                connectorRef: docker-connector\n"
        + "                imagePath: harness/my-app\n"
        + "                tag: latest\n"
        + "      manifestConfigurations:\n"
        + "        primaryManifestRef: <+input>\n"
        + "      manifests:\n"
        + "        - manifest:\n"
        + "            identifier: chart1\n"
        + "            type: HelmChart\n"
        + "            spec:\n"
        + "              store:\n"
        + "                type: Github\n"
        + "                spec:\n"
        + "                  connectorRef: git-connector\n"
        + "                  gitFetchType: Branch\n"
        + "                  branch: main\n"
        + "                  folderPath: chart1\n";

    String afterArtifacts = ngToUnifiedServiceHelper.processArtifactsInYaml(null, yaml, true).getServiceYaml();
    String processed = ngToUnifiedServiceHelper.processManifestsInYaml(afterArtifacts);

    // Artifact pass: the lone source is inlined into primary and its ref dropped.
    assertThat(processed).contains("docker-connector").doesNotContain("primaryArtifactRef");
    // Manifest pass: the lone chart becomes the primary ref.
    assertThat(processed).contains("primaryManifestRef: chart1").doesNotContain("<+input>");
    // Still a well formed service.
    assertThatCode(() -> readServiceSpec(processed)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testProcessManifestsInYamlThenOutcomesPopulatesPrimaryWithoutCandidates() throws Exception {
    // End to end through the downstream path the rewrite is designed to unlock: because the ref is now static, NG
    // populates manifests.primary itself and emits no candidates for the CI service step to restore.
    String processed = ngToUnifiedServiceHelper.processManifestsInYaml(singleChartServiceYaml("<+input>"));
    ServiceSpec serviceSpec = readServiceSpec(processed);
    Map<String, String> ngOutcomes = new LinkedHashMap<>();

    ngToUnifiedServiceHelper.addManifestsOutcome(serviceSpec, new EnumMap<>(ServiceOverridesType.class), ngOutcomes);

    assertThat(ngOutcomes).doesNotContainKey(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    assertThat(ngOutcomes.get(NGOutcomes.MANIFESTS.getName())).contains("primary:").contains("chart1");
  }
}
