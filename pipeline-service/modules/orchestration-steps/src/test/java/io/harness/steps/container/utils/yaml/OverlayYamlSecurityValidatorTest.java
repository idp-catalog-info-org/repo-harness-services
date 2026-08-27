/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils.yaml;

import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class OverlayYamlSecurityValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testNullOverlay_passes() {
    assertThatCode(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(null)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEmptyOverlay_passes() {
    assertThatCode(() -> OverlayYamlSecurityValidator.validateNoSecretReferences("")).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayWithNoSecretRefs_passes() {
    String yaml = "spec:\n"
        + "  securityContext:\n"
        + "    runAsNonRoot: true\n"
        + "  containers:\n"
        + "  - name: app-container\n"
        + "    resources:\n"
        + "      limits:\n"
        + "        memory: 512Mi\n"
        + "  volumes:\n"
        + "  - name: my-emptydir\n"
        + "    emptyDir: {}\n";
    assertThatCode(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testOverlayWithConfigMapRef_passes() {
    String yaml = "spec:\n"
        + "  containers:\n"
        + "  - name: step-1\n"
        + "    env:\n"
        + "    - name: MY_VAR\n"
        + "      valueFrom:\n"
        + "        configMapKeyRef:\n"
        + "          name: my-config\n"
        + "          key: my-key\n";
    assertThatCode(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testContainerSecretKeyRef_throws() {
    String yaml = "spec:\n"
        + "  containers:\n"
        + "  - name: step-1\n"
        + "    env:\n"
        + "    - name: MY_SECRET\n"
        + "      valueFrom:\n"
        + "        secretKeyRef:\n"
        + "          name: harnessci-asdfsf-11qsrm1j-secret\n"
        + "          key: HARNESS_SSCA_SERVICE_TOKEN\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInitContainerSecretKeyRef_throws() {
    String yaml = "spec:\n"
        + "  initContainers:\n"
        + "  - name: init-container\n"
        + "    env:\n"
        + "    - name: MY_SECRET\n"
        + "      valueFrom:\n"
        + "        secretKeyRef:\n"
        + "          name: some-secret\n"
        + "          key: TOKEN\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testContainerEnvFromSecretRef_throws() {
    String yaml = "spec:\n"
        + "  containers:\n"
        + "  - name: step-1\n"
        + "    envFrom:\n"
        + "    - secretRef:\n"
        + "        name: all-secrets\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInitContainerEnvFromSecretRef_throws() {
    String yaml = "spec:\n"
        + "  initContainers:\n"
        + "  - name: init-container\n"
        + "    envFrom:\n"
        + "    - secretRef:\n"
        + "        name: all-secrets\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testEphemeralContainerSecretKeyRef_throws() {
    String yaml = "spec:\n"
        + "  ephemeralContainers:\n"
        + "  - name: debugger\n"
        + "    env:\n"
        + "    - name: MY_SECRET\n"
        + "      valueFrom:\n"
        + "        secretKeyRef:\n"
        + "          name: some-secret\n"
        + "          key: TOKEN\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testSecretVolume_throws() {
    String yaml = "spec:\n"
        + "  volumes:\n"
        + "  - name: secret-vol\n"
        + "    secret:\n"
        + "      secretName: harnessci-asdfsf-11qsrm1j-secret\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testProjectedSecretVolume_throws() {
    String yaml = "spec:\n"
        + "  volumes:\n"
        + "  - name: projected-vol\n"
        + "    projected:\n"
        + "      sources:\n"
        + "      - secret:\n"
        + "          name: some-secret\n";
    assertThatThrownBy(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("podSpecOverlay contains a Kubernetes secret reference");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testProjectedVolumeWithConfigMapOnly_passes() {
    String yaml = "spec:\n"
        + "  volumes:\n"
        + "  - name: projected-vol\n"
        + "    projected:\n"
        + "      sources:\n"
        + "      - configMap:\n"
        + "          name: my-config\n";
    assertThatCode(() -> OverlayYamlSecurityValidator.validateNoSecretReferences(yaml)).doesNotThrowAnyException();
  }
}
