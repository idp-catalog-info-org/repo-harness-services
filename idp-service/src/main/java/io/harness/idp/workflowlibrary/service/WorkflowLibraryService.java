/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.spec.server.idp.v1.model.GitCreateDetails;
import io.harness.spec.server.idp.v1.model.WorkflowInstallResponse;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public interface WorkflowLibraryService {
  List<WorkflowLibraryEntity> listWorkflows(String accountId, String category);
  WorkflowLibraryEntity getWorkflow(String accountId, String identifier);
  WorkflowLibraryEntity getWorkflowVersion(String accountId, String identifier, String version);
  List<WorkflowLibraryEntity> getVersions(String accountId, String identifier);
  WorkflowInstallResponse install(String accountId, String pipelineOrgId, String pipelineProjectId,
      String workflowOrgId, String workflowProjectId, String identifier, String version,
      String workflowInstanceIdentifier, String workflowInstanceName, Map<String, String> adminInputValues,
      GitCreateDetails gitDetails, InstallIntegrationParams integrationParams);
}
