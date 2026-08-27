/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.rule.Owner;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class ServiceInputMergeUtilsTest extends CategoryTest {
  private static final String NATIVE_HELM_SERVICE_YAML = "service:\n"
      + "  name: myNativeHelmService\n"
      + "  identifier: myNativeHelmService\n"
      + "  serviceDefinition:\n"
      + "    type: NativeHelm\n"
      + "    spec:\n"
      + "      artifacts:\n"
      + "        primary:\n"
      + "          primaryArtifactRef: <+input>\n"
      + "          sources: <+input>\n";

  private static final String OVERLAY_YAML_FROM_BUG_REPORT = "overlay:\n"
      + "  __uuid: oYRh7rDOT5qB8n1oc21tvg\n"
      + "  type: NativeHelm\n"
      + "  spec:\n"
      + "    __uuid: vMz-ZqukTXOg16HS7WAJVQ\n"
      + "    artifacts:\n"
      + "      __uuid: rSf0ET-cTgG_IUR0y9vyOw\n"
      + "      primary:\n"
      + "        primaryArtifactRef: gar1\n"
      + "        sources: <+input>\n"
      + "        __uuid: KqBsXtOFSTW3dgDdzV675g\n"
      + "__uuid: H4hn9upsRTGiDgEWGaIGoA\n";

  private static final String KUBERNETES_SERVICE_YAML = "service:\n"
      + "  name: myK8sService\n"
      + "  identifier: myK8sService\n"
      + "  serviceDefinition:\n"
      + "    type: Kubernetes\n"
      + "    spec:\n"
      + "      variables:\n"
      + "        - name: port\n"
      + "          type: String\n"
      + "          value: <+input>\n"
      + "        - name: replicas\n"
      + "          type: Number\n"
      + "          value: <+input>\n";

  private static final String OVERLAY_YAML_WITH_VARIABLES = "overlay:\n"
      + "  type: Kubernetes\n"
      + "  spec:\n"
      + "    variables:\n"
      + "      - name: port\n"
      + "        type: String\n"
      + "        value: \"8080\"\n"
      + "      - name: replicas\n"
      + "        type: Number\n"
      + "        value: 3\n";

  // Overlay with `spec` but no `type` -> still wrapped under serviceDefinition and merged.
  private static final String OVERLAY_YAML_WITH_SPEC_ONLY = "overlay:\n"
      + "  spec:\n"
      + "    artifacts:\n"
      + "      primary:\n"
      + "        primaryArtifactRef: gar1\n"
      + "        sources: <+input>\n";

  // Overlay object that does not contain `spec` -> must fail fast.
  private static final String OVERLAY_YAML_WITHOUT_SPEC = "overlay:\n"
      + "  name: renamedService\n";

  // Overlay that still uses the legacy `serviceDefinition` wrapper -> must fail fast.
  private static final String OVERLAY_YAML_WITH_SERVICE_DEFINITION = "overlay:\n"
      + "  serviceDefinition:\n"
      + "    type: Kubernetes\n"
      + "    spec:\n"
      + "      variables:\n"
      + "        - name: port\n"
      + "          type: String\n"
      + "          value: \"8080\"\n";

  private final ServiceEntity serviceEntity =
      ServiceEntity.builder().identifier("myNativeHelmService").yaml(NATIVE_HELM_SERVICE_YAML).build();

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_returnsEntityYaml_whenNoInputsProvided() {
    UnifiedServiceConverterRequestDTO requestDTO = UnifiedServiceConverterRequestDTO.builder().build();

    String result = ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null);

    assertThat(result).isEqualTo(NATIVE_HELM_SERVICE_YAML);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_parsesOverlayYaml_withoutJsonParseException() {
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_FROM_BUG_REPORT).build();

    assertThatCode(() -> ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_mergesOverlayInputsIntoServiceYaml() {
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_FROM_BUG_REPORT).build();

    String result = ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null);

    assertThat(result).contains("primaryArtifactRef: gar1");
    assertThat(result).contains("primaryArtifactRef: gar1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_wrapsSpecTypeOverlay_intoServiceDefinition_andMergesVariables() {
    ServiceEntity k8sServiceEntity =
        ServiceEntity.builder().identifier("myK8sService").yaml(KUBERNETES_SERVICE_YAML).build();
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_WITH_VARIABLES).build();

    String result = ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, k8sServiceEntity, null);

    // The flat overlay (spec/type) is re-wrapped under serviceDefinition so the runtime variable values resolve.
    assertThat(result).contains("serviceDefinition");
    assertThat(result).contains("value: \"8080\"");
    assertThat(result).contains("value: 3");
    assertThat(result).doesNotContain("<+input>");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_wrapsSpecOnlyOverlay_withoutType() {
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_WITH_SPEC_ONLY).build();

    // `spec` alone (no `type`) is sufficient to trigger the serviceDefinition wrap and merge.
    String result = ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null);

    assertThat(result).contains("primaryArtifactRef: gar1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_overlayWithoutSpec_throwsInvalidYamlException() {
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_WITHOUT_SPEC).build();

    // Overlays without `spec` must fail fast instead of passing through.
    assertThatThrownBy(() -> ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("'service.with.overlay' requires the 'spec' field to provide service inputs");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void getMergedNgServiceYaml_overlayWithServiceDefinitionWrapper_throwsInvalidYamlException() {
    UnifiedServiceConverterRequestDTO requestDTO =
        UnifiedServiceConverterRequestDTO.builder().serviceInputsYaml(OVERLAY_YAML_WITH_SERVICE_DEFINITION).build();

    // Legacy `serviceDefinition` wrapper is rejected with a targeted message.
    assertThatThrownBy(() -> ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, null))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("'service.with.overlay' requires the 'spec' field to provide service inputs")
        .hasMessageContaining("Remove the 'serviceDefinition' wrapper");
  }
}
