/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Artifact providers supported by onboarding. Each provider emits its own artifact source type in the service YAML
 * (DockerRegistry for Docker, Ecr for ECR, Artifactory for JFrog Artifactory) and differs in whether onboarding must
 * provision a backing connector. To
 * add a new provider:
 * <ol>
 *   <li>add a constant here (with {@code requiresConnector} set appropriately),</li>
 *   <li>map its accepted request value(s) in {@link OnboardingContextNormalizer#resolveArtifactType}, and</li>
 *   <li>provision (or skip) its connector in {@code OnboardingOrchestrationImpl}, and</li>
 *   <li>build its artifact source in {@code OnboardingServiceYamlBuilder}.</li>
 * </ol>
 */
@OwnedBy(HarnessTeam.CDC)
public enum ArtifactProviderType {
  /** DockerRegistry: needs a user-provided connector (Docker Hub auth) and a secret for its credential. */
  DOCKER_REGISTRY(true),
  /** ECR: needs a user-provided AWS connector (manual access/secret key) and a secret for its credential. */
  ECR(true),
  /** Artifactory: needs a user-provided Artifactory connector (username/password) and a secret for the password. */
  ARTIFACTORY(true),
  /**
   * Harness sample artifact: reuses the built-in account-level {@code harnessImage} Docker connector, so onboarding
   * creates neither a secret nor a connector. The image path and tag are fixed by the backend.
   */
  HARNESS_ARTIFACT_SAMPLE(false);

  private final boolean requiresConnector;

  ArtifactProviderType(boolean requiresConnector) {
    this.requiresConnector = requiresConnector;
  }

  /** Whether onboarding must provision a connector (and its credential secret) for this provider. */
  public boolean requiresConnector() {
    return requiresConnector;
  }
}
