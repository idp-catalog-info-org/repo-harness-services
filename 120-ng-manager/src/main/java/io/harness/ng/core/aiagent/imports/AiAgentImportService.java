/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.imports;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.aiagent.dto.AgentDiscoverRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentImportRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentImportResponseDTO;

/**
 * Service for discovering and importing cloud AI agents as Harness services.
 * Orchestrates delegate submission, YAML synthesis, and service creation.
 * All operations are gated behind the CDS_AGENT_RUNTIME_DEPLOYMENT feature flag.
 */
@OwnedBy(HarnessTeam.CDP)
public interface AiAgentImportService {
  /**
   * Discovers AI agents in the specified cloud scope.
   * Submits AGENT_DISCOVERY_TASK (LIST mode) to delegate and maps candidates to DTOs.
   *
   * @param account Harness account identifier
   * @param org Organization identifier
   * @param project Project identifier
   * @param req Discovery request (connector, platform, scope)
   * @return List of discovered agent candidates
   * @throws io.harness.exception.InvalidRequestException if feature flag is disabled or connector not found
   */
  AgentDiscoverResponseDTO discover(String account, String org, String project, AgentDiscoverRequestDTO req);

  /**
   * Imports a cloud AI agent as a Harness service.
   * Submits AGENT_DISCOVERY_TASK (DESCRIBE mode), synthesizes YAML, and creates the service entity.
   *
   * @param account Harness account identifier
   * @param org Organization identifier
   * @param project Project identifier
   * @param req Import request (connector, platform, cloudId, target service)
   * @return Import result (service ref, yaml, config variables, notes)
   * @throws io.harness.exception.InvalidRequestException if feature flag is disabled, connector not found, or agent
   *     descriptor unavailable
   */
  AgentImportResponseDTO importAgent(String account, String org, String project, AgentImportRequestDTO req);
}
