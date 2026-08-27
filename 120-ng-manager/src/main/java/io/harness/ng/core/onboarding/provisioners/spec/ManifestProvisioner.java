/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.spec;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ManifestProviderType;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;

/**
 * One manifest source (GitHub, Bitbucket, GitLab, Harness Code) knows how to validate its own request fields, build
 * its connector (when it needs one) and emit the manifest node written into the service YAML. Implementations are
 * registered in a flat {@code MapBinder<ManifestProviderType, ManifestProvisioner>}; the coordinator dispatches by
 * type, replacing the {@code switch} statements this SPI supersedes. Adding a source means adding one implementation
 * and one {@code MapBinder} binding — no coordinator edit.
 */
@OwnedBy(HarnessTeam.CDC)
public interface ManifestProvisioner {
  /** The provider this implementation handles; also its registry key. */
  ManifestProviderType type();

  /** Whether onboarding must provision a connector (and its credential secret) for this provider. */
  boolean requiresConnector();

  /** Validates the provider-specific manifest request fields, failing fast with an {@code InvalidRequestException}. */
  void validate(OnboardingContextDTO context);

  /**
   * Builds the connector (creating or reusing its credential secret) for a provider that requires one. Only called
   * when {@link #requiresConnector()} is {@code true}; implementations that never require a connector return null.
   */
  ConnectorInfoDTO buildConnector(OnboardingProvisionContext context);

  /** Builds the manifest node attached to the service spec, pointed at {@code connectorRef} (null when none). */
  ManifestConfigWrapper buildManifest(OnboardingContextDTO context, String connectorRef);
}
