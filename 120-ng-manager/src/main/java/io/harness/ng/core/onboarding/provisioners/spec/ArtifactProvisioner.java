/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.spec;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;

/**
 * One artifact source (DockerRegistry, ECR, Artifactory, HarnessArtifactSample) knows how to validate its own request
 * fields, seed any backend-defined defaults, build its connector (when it needs one) and emit the artifact source node
 * written into the service YAML. Implementations are registered in a flat
 * {@code MapBinder<ArtifactProviderType, ArtifactProvisioner>}; the coordinator dispatches by type, replacing the
 * {@code switch} statements this SPI supersedes.
 */
@OwnedBy(HarnessTeam.CDC)
public interface ArtifactProvisioner {
  /** The provider this implementation handles; also its registry key. */
  ArtifactProviderType type();

  /** Whether onboarding must provision a connector (and its credential secret) for this provider. */
  boolean requiresConnector();

  /** Validates the provider-specific artifact request fields, failing fast with an {@code InvalidRequestException}. */
  void validate(OnboardingContextDTO context);

  /**
   * Seeds any fields the caller may omit for a fully backend-defined source (e.g. HarnessArtifactSample's id, image
   * path and tag). No-op by default; called before required-field validation and artifact-id generation.
   */
  default void applyDefaults(OnboardingContextDTO context) {
    // no-op: most sources require the caller to supply their fields
  }

  /**
   * The fixed connector ref for a source that reuses an existing connector instead of provisioning one (e.g.
   * HarnessArtifactSample's built-in account-level Docker connector). Null by default; only meaningful when
   * {@link #requiresConnector()} is {@code false}.
   */
  default String reusedConnectorRef() {
    return null;
  }

  /**
   * Builds the connector (creating or reusing its credential secret) for a provider that requires one. Only called
   * when {@link #requiresConnector()} is {@code true}; implementations that never require a connector return null.
   */
  ConnectorInfoDTO buildConnector(OnboardingProvisionContext context);

  /** Builds the artifact source node attached to the service spec, pointed at {@code connectorRef}. */
  ArtifactSource buildArtifactSource(OnboardingContextDTO context, String connectorRef);
}
