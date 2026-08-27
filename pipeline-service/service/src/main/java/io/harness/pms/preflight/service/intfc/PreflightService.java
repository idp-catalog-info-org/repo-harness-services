/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.dto.PreFlightDTO;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.inputset.PipelineInputResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PreflightService {
  void updateStatus(
      String id, PreFlightStatus overallStatus, PreFlightEntityErrorInfo errorInfo, PreFlightStatus allConnectorStatus);

  List<ConnectorCheckResponse> updateConnectorCheckResponses(String accountId, String orgId, String projectId,
      String preflightEntityId, Map<String, Object> fqnToObjectMapMergedYaml, List<EntityDetail> connectorUsages);

  PreFlightEntity saveInitialPreflightEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineYaml, List<EntityDetail> entityDetails,
      List<PipelineInputResponse> pipelineInputResponses, ScopeInfo scopeInfo);

  PreFlightDTO getPreflightCheckResponse(String preflightCheckId);

  String startPreflightCheck(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String inputSetPipelineYaml,
      ScopeInfo scopeInfo) throws IOException;

  void schedulePreflightCheck(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String preflightCheckId);

  /**
   * Deletes all preflight entity for given pipeline
   * Uses - accountId_parentUniqueId_pipelineId_idx
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param pipelineIdentifier
   */
  void deleteAllPreflightEntityForGivenPipeline(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String parentUniqueId);
}
