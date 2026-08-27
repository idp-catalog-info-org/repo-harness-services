/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.onboarding.dto.OnboardingArtifactType;
import io.harness.ng.core.onboarding.dto.OnboardingGitAuthType;
import io.harness.ng.core.onboarding.dto.OnboardingGitFetchType;
import io.harness.ng.core.onboarding.dto.OnboardingManifestType;

import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes the raw, possibly-malformed strings in an {@link
 * io.harness.ng.core.onboarding.dto.OnboardingContextDTO} into the canonical enums/values the
 * provisioning steps expect. Matching is case-insensitive and tolerant of separators
 * ({@code &}, {@code _}, {@code -}, spaces) so inputs like {@code "harnessCode"},
 * {@code "HARNESS_CODE"} or {@code "dockerHub"} all resolve. Unsupported values fail fast
 * with a clear {@link InvalidRequestException} describing what is accepted.
 */
@OwnedBy(HarnessTeam.CDC)
public final class OnboardingContextNormalizer {
  private OnboardingContextNormalizer() {}

  /** Collapses case and separators so comparisons are forgiving of caller formatting. */
  private static String canonical(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase().replaceAll("[\\s_&-]", "");
  }

  /** Supports Kubernetes services only. */
  public static void validateServiceType(String type) {
    if (!"kubernetes".equals(canonical(type))) {
      throw new InvalidRequestException(
          String.format("Unsupported service type '%s'. Supports only 'kubernetes'.", type));
    }
  }

  /**
   * Maps the (already type-checked) request manifest type to the internal {@link ManifestProviderType} the
   * provisioning steps expect. The {@link OnboardingManifestType} enum has already rejected unsupported values at
   * deserialization, so this only needs to bridge the two enums.
   */
  public static ManifestProviderType resolveManifestType(OnboardingManifestType manifestType) {
    if (manifestType == null) {
      throw new InvalidRequestException(
          "Manifest type is required. Supports 'github', 'bitbucket', 'gitlab' or 'harnessCode'.");
    }
    switch (manifestType) {
      case GITHUB:
        return ManifestProviderType.GITHUB;
      case BITBUCKET:
        return ManifestProviderType.BITBUCKET;
      case GITLAB:
        return ManifestProviderType.GITLAB;
      case HARNESS_CODE:
        return ManifestProviderType.HARNESS_CODE;
      default:
        throw new InvalidRequestException(
            String.format("Unsupported manifest type '%s'. Supports 'github', 'bitbucket', 'gitlab' or 'harnessCode'.",
                manifestType));
    }
  }

  /**
   * Maps the (already type-checked) request artifact type to the internal {@link ArtifactProviderType} the
   * provisioning steps expect. The {@link OnboardingArtifactType} enum has already rejected unsupported values at
   * deserialization, so this only needs to bridge the two enums.
   */
  public static ArtifactProviderType resolveArtifactType(OnboardingArtifactType artifactType) {
    if (artifactType == null) {
      throw new InvalidRequestException(
          "Artifact type is required. Supports 'DockerRegistry', 'Ecr', 'Artifactory' or 'HarnessArtifactSample'.");
    }
    switch (artifactType) {
      case DOCKER_REGISTRY:
        return ArtifactProviderType.DOCKER_REGISTRY;
      case ECR:
        return ArtifactProviderType.ECR;
      case ARTIFACTORY:
        return ArtifactProviderType.ARTIFACTORY;
      case HARNESS_ARTIFACT_SAMPLE:
        return ArtifactProviderType.HARNESS_ARTIFACT_SAMPLE;
      default:
        throw new InvalidRequestException(String.format("Unsupported artifact type '%s'. Supports 'DockerRegistry', "
                + "'Ecr', 'Artifactory' or 'HarnessArtifactSample'.",
            artifactType));
    }
  }

  /** Maps the (already-validated) manifest auth type to a canonical Git auth mode. UsernameToken is the default. */
  public static GitAuthMode resolveGitAuthMode(OnboardingGitAuthType authType) {
    if (authType == OnboardingGitAuthType.OAUTH) {
      return GitAuthMode.OAUTH;
    }
    return GitAuthMode.USERNAME_TOKEN;
  }

  /**
   * Maps the request infrastructure type to the internal {@link InfrastructureType} the coordinator dispatches on,
   * failing fast for unsupported values. Adding a type means adding a case here (and its provisioner + MapBinder
   * binding); the coordinator resolves the provisioner off this result rather than a hardcoded constant.
   */
  public static InfrastructureType resolveInfraType(String infraType) {
    if ("kubernetesdirect".equals(canonical(infraType))) {
      return InfrastructureType.KUBERNETES_DIRECT;
    }
    throw new InvalidRequestException(
        String.format("Unsupported infrastructure type '%s'. Supports only 'KubernetesDirect'.", infraType));
  }

  /** Supports the KubernetesDirect infrastructure type only. */
  public static void validateInfraType(String infraType) {
    resolveInfraType(infraType);
  }

  /** Supports the K8sCluster connector type only. */
  public static void validateInfraConnectorType(String connectorType) {
    if (!"k8scluster".equals(canonical(connectorType))) {
      throw new InvalidRequestException(
          String.format("Unsupported infra connector type '%s'. Supports only 'K8sCluster'.", connectorType));
    }
  }

  /** Supports the ManualConfig cluster credential type only. */
  public static void validateInfraCredentialType(String credentialType) {
    if (!"manualconfig".equals(canonical(credentialType))) {
      throw new InvalidRequestException(
          String.format("Unsupported infra credential type '%s'. Supports only 'ManualConfig'.", credentialType));
    }
  }

  /** Supports the ServiceAccount cluster auth type only. */
  public static void validateInfraAuthType(String authType) {
    if (!"serviceaccount".equals(canonical(authType))) {
      throw new InvalidRequestException(
          String.format("Unsupported infra auth type '%s'. Supports only 'ServiceAccount'.", authType));
    }
  }

  /** Maps the environment type to {@link EnvironmentType}. PreProduction is the default. */
  public static EnvironmentType resolveEnvironmentType(String envType) {
    String canonical = canonical(envType);
    if (StringUtils.isBlank(canonical) || "preproduction".equals(canonical) || "preprod".equals(canonical)) {
      return EnvironmentType.PreProduction;
    }
    if ("production".equals(canonical) || "prod".equals(canonical)) {
      return EnvironmentType.Production;
    }
    throw new InvalidRequestException(
        String.format("Unsupported environment type '%s'. Supports 'PreProduction' or 'Production'.", envType));
  }

  /** Maps the (already-validated) git fetch type to {@link FetchType}. Branch is the default when unset. */
  public static FetchType resolveGitFetchType(OnboardingGitFetchType fetchType) {
    if (fetchType == OnboardingGitFetchType.COMMIT) {
      return FetchType.COMMIT;
    }
    return FetchType.BRANCH;
  }
}
