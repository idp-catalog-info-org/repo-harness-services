/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.spec;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;

/**
 * One infrastructure type (today only KubernetesDirect) knows how to validate its own request fields, build its
 * deployment-target connector and emit its infrastructure YAML. Implementations are registered in a flat
 * {@code MapBinder<InfrastructureType, InfraProvisioner>}. The surrounding deployment-target orchestration
 * (environment create/reuse, infrastructure upsert) stays in the coordinator; this SPI owns the type-specific pieces.
 */
@OwnedBy(HarnessTeam.CDC)
public interface InfraProvisioner {
  /** The infrastructure type this implementation handles; also its registry key. */
  InfrastructureType type();

  /** Validates the infra request fields, failing fast with an {@code InvalidRequestException}. */
  void validate(OnboardingContextDTO context);

  /**
   * Derives the deployment-target connector identifier from the (stable) infra id. Kept per-type so each
   * infrastructure owns its own connector-naming convention; because the id is a pure function of the infra id, repeat
   * runs upsert the same connector rather than leaking a fresh credential each time.
   */
  String connectorIdentifier(String infraId);

  /**
   * Builds the deployment-target connector (creating its credential secret) under {@code connectorId}, the value
   * returned by {@link #connectorIdentifier(String)}.
   */
  ConnectorInfoDTO buildConnector(OnboardingProvisionContext context, String connectorId);

  /** Builds the {@code infrastructureDefinition:}-rooted YAML referencing the environment and connector. */
  String buildInfraYaml(OnboardingContextDTO context, String infraId, String infraName, String orgIdentifier,
      String projectIdentifier, String environmentRef, String connectorRef, String releaseName);
}
