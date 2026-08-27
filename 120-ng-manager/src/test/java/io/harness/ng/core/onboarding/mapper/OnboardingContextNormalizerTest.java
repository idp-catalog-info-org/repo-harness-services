/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.onboarding.dto.OnboardingArtifactType;
import io.harness.ng.core.onboarding.dto.OnboardingGitAuthType;
import io.harness.ng.core.onboarding.dto.OnboardingGitFetchType;
import io.harness.ng.core.onboarding.dto.OnboardingManifestType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for {@link OnboardingContextNormalizer}: case/separator-tolerant resolution of the raw request strings
 * into the canonical enums the provisioning steps expect, and the fail-fast paths for unsupported values.
 */
public class OnboardingContextNormalizerTest extends CategoryTest {
  // ---- service type ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateServiceTypeAcceptsKubernetesRegardlessOfCasing() {
    // No exception for the supported value, in various casings/separators.
    OnboardingContextNormalizer.validateServiceType("kubernetes");
    OnboardingContextNormalizer.validateServiceType("Kubernetes");
    OnboardingContextNormalizer.validateServiceType("KUBERNETES");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateServiceTypeRejectsUnsupported() {
    assertThatThrownBy(() -> OnboardingContextNormalizer.validateServiceType("ecs"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported service type");
  }

  // ---- manifest type ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveManifestType() {
    assertThat(OnboardingContextNormalizer.resolveManifestType(OnboardingManifestType.GITHUB))
        .isEqualTo(ManifestProviderType.GITHUB);
    assertThat(OnboardingContextNormalizer.resolveManifestType(OnboardingManifestType.BITBUCKET))
        .isEqualTo(ManifestProviderType.BITBUCKET);
    assertThat(OnboardingContextNormalizer.resolveManifestType(OnboardingManifestType.GITLAB))
        .isEqualTo(ManifestProviderType.GITLAB);
    assertThat(OnboardingContextNormalizer.resolveManifestType(OnboardingManifestType.HARNESS_CODE))
        .isEqualTo(ManifestProviderType.HARNESS_CODE);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveManifestTypeRejectsNull() {
    assertThatThrownBy(() -> OnboardingContextNormalizer.resolveManifestType(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Manifest type is required");
  }

  // ---- manifest type request binding (OnboardingManifestType.fromValue) ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOnboardingManifestTypeToleratesCasingAndSeparators() {
    assertThat(OnboardingManifestType.fromValue("github")).isEqualTo(OnboardingManifestType.GITHUB);
    assertThat(OnboardingManifestType.fromValue("GitHub")).isEqualTo(OnboardingManifestType.GITHUB);
    assertThat(OnboardingManifestType.fromValue("harnessCode")).isEqualTo(OnboardingManifestType.HARNESS_CODE);
    assertThat(OnboardingManifestType.fromValue("HARNESS_CODE")).isEqualTo(OnboardingManifestType.HARNESS_CODE);
    assertThat(OnboardingManifestType.fromValue("harness-code")).isEqualTo(OnboardingManifestType.HARNESS_CODE);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOnboardingManifestTypeBlankMapsToNull() {
    assertThat(OnboardingManifestType.fromValue(null)).isNull();
    assertThat(OnboardingManifestType.fromValue("")).isNull();
    assertThat(OnboardingManifestType.fromValue("   ")).isNull();
  }

  // ---- artifact type ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveArtifactTypeAcceptsDockerRegistry() {
    assertThat(OnboardingContextNormalizer.resolveArtifactType(OnboardingArtifactType.DOCKER_REGISTRY))
        .isEqualTo(ArtifactProviderType.DOCKER_REGISTRY);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveArtifactTypeAcceptsEcr() {
    assertThat(OnboardingContextNormalizer.resolveArtifactType(OnboardingArtifactType.ECR))
        .isEqualTo(ArtifactProviderType.ECR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveArtifactTypeAcceptsArtifactory() {
    assertThat(OnboardingContextNormalizer.resolveArtifactType(OnboardingArtifactType.ARTIFACTORY))
        .isEqualTo(ArtifactProviderType.ARTIFACTORY);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveArtifactTypeAcceptsHarnessArtifactSample() {
    assertThat(OnboardingContextNormalizer.resolveArtifactType(OnboardingArtifactType.HARNESS_ARTIFACT_SAMPLE))
        .isEqualTo(ArtifactProviderType.HARNESS_ARTIFACT_SAMPLE);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveArtifactTypeRejectsNull() {
    assertThatThrownBy(() -> OnboardingContextNormalizer.resolveArtifactType(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Artifact type is required");
  }

  // ---- github auth mode ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveGitAuthMode() {
    assertThat(OnboardingContextNormalizer.resolveGitAuthMode(OnboardingGitAuthType.OAUTH))
        .isEqualTo(GitAuthMode.OAUTH);
    assertThat(OnboardingContextNormalizer.resolveGitAuthMode(OnboardingGitAuthType.USERNAME_TOKEN))
        .isEqualTo(GitAuthMode.USERNAME_TOKEN);
    // Everything that is not OAuth (including UsernamePassword) maps to username/token.
    assertThat(OnboardingContextNormalizer.resolveGitAuthMode(OnboardingGitAuthType.USERNAME_PASSWORD))
        .isEqualTo(GitAuthMode.USERNAME_TOKEN);
    // Default (null) falls back to username/token.
    assertThat(OnboardingContextNormalizer.resolveGitAuthMode(null)).isEqualTo(GitAuthMode.USERNAME_TOKEN);
  }

  // ---- infra validators ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testInfraValidatorsAcceptSupportedValues() {
    OnboardingContextNormalizer.validateInfraType("KubernetesDirect");
    OnboardingContextNormalizer.validateInfraConnectorType("K8sCluster");
    OnboardingContextNormalizer.validateInfraCredentialType("ManualConfig");
    OnboardingContextNormalizer.validateInfraAuthType("ServiceAccount");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testInfraValidatorsRejectUnsupportedValues() {
    assertThatThrownBy(() -> OnboardingContextNormalizer.validateInfraType("KubernetesGcp"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported infrastructure type");
    assertThatThrownBy(() -> OnboardingContextNormalizer.validateInfraConnectorType("Gcp"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported infra connector type");
    assertThatThrownBy(() -> OnboardingContextNormalizer.validateInfraCredentialType("InheritFromDelegate"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported infra credential type");
    assertThatThrownBy(() -> OnboardingContextNormalizer.validateInfraAuthType("ClientKeyCert"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported infra auth type");
  }

  // ---- environment type (the nonprod substring fix) ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveEnvironmentTypePreProductionAndBlankDefault() {
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("PreProduction"))
        .isEqualTo(EnvironmentType.PreProduction);
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("pre-prod")).isEqualTo(EnvironmentType.PreProduction);
    // Blank / null default to PreProduction.
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("")).isEqualTo(EnvironmentType.PreProduction);
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType(null)).isEqualTo(EnvironmentType.PreProduction);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveEnvironmentTypeProduction() {
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("Production")).isEqualTo(EnvironmentType.Production);
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("prod")).isEqualTo(EnvironmentType.Production);
    assertThat(OnboardingContextNormalizer.resolveEnvironmentType("PROD")).isEqualTo(EnvironmentType.Production);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveEnvironmentTypeRejectsNonProdInsteadOfMisreadingAsProduction() {
    // Regression guard: "nonprod" contains the substring "prod" but must NOT resolve to Production. The exact-match
    // logic rejects it outright rather than silently flagging a non-production environment as Production.
    assertThatThrownBy(() -> OnboardingContextNormalizer.resolveEnvironmentType("nonprod"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported environment type");
    assertThatThrownBy(() -> OnboardingContextNormalizer.resolveEnvironmentType("non-production"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported environment type");
    assertThatThrownBy(() -> OnboardingContextNormalizer.resolveEnvironmentType("nonProd"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported environment type");
  }

  // ---- git fetch type ----

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testResolveGitFetchType() {
    assertThat(OnboardingContextNormalizer.resolveGitFetchType(OnboardingGitFetchType.COMMIT))
        .isEqualTo(FetchType.COMMIT);
    assertThat(OnboardingContextNormalizer.resolveGitFetchType(OnboardingGitFetchType.BRANCH))
        .isEqualTo(FetchType.BRANCH);
    // Default (null) is branch.
    assertThat(OnboardingContextNormalizer.resolveGitFetchType(null)).isEqualTo(FetchType.BRANCH);
  }
}
