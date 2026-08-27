/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.support;

import static io.harness.connector.utils.ModuleConstants.CONNECTOR_DECORATOR_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;

/**
 * Creates the connectors that onboarding provisions. Extracted verbatim from the former orchestration god object so
 * behavior is unchanged: a connector whose identifier already exists is updated (upsert) rather than duplicated.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class OnboardingConnectorCreation {
  private final ConnectorService connectorService;

  @Inject
  public OnboardingConnectorCreation(@Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService) {
    this.connectorService = connectorService;
  }

  /** Creates (or updates, if the identifier already exists) the connector and returns its identifier. */
  public String upsertConnector(
      ScopeInfo scopeInfo, String orgIdentifier, String projectIdentifier, ConnectorInfoDTO connectorInfo) {
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    Optional<ConnectorResponseDTO> existing = connectorService.get(scopeInfo, connectorInfo.getIdentifier());
    ConnectorResponseDTO saved = existing.isPresent() ? connectorService.update(scopeInfo, connectorDTO)
                                                      : connectorService.create(scopeInfo, connectorDTO);
    return saved.getConnector().getIdentifier();
  }
}
