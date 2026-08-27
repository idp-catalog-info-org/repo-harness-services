/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.resources;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.EntityLinks.FieldMapping;
import io.harness.idp.catalog.entities.EntityLinks.LinkTarget;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.idp.workflowlibrary.service.InstallIntegrationParams;
import io.harness.idp.workflowlibrary.service.WorkflowLibraryService;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.WorkflowLibraryApi;
import io.harness.spec.server.idp.v1.model.WorkflowInstallRequest;
import io.harness.spec.server.idp.v1.model.WorkflowInstallResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class WorkflowLibraryApiImpl implements WorkflowLibraryApi {
  private final WorkflowLibraryService workflowLibraryService;
  private final AccountClient accountClient;

  @Inject
  public WorkflowLibraryApiImpl(WorkflowLibraryService workflowLibraryService, AccountClient accountClient) {
    this.workflowLibraryService = workflowLibraryService;
    this.accountClient = accountClient;
  }

  @Override
  public Response listWorkflows(String harnessAccount, String category) {
    validateFeatureEnabled(harnessAccount);
    List<WorkflowLibraryEntity> workflows = workflowLibraryService.listWorkflows(harnessAccount, category);
    return Response.status(Response.Status.OK).entity(workflows).build();
  }

  @Override
  public Response getWorkflow(String identifier, String harnessAccount) {
    validateFeatureEnabled(harnessAccount);
    WorkflowLibraryEntity workflow = workflowLibraryService.getWorkflow(harnessAccount, identifier);
    return Response.status(Response.Status.OK).entity(workflow).build();
  }

  @Override
  public Response getWorkflowVersions(String identifier, String harnessAccount) {
    validateFeatureEnabled(harnessAccount);
    List<WorkflowLibraryEntity> versions = workflowLibraryService.getVersions(harnessAccount, identifier);
    return Response.status(Response.Status.OK).entity(versions).build();
  }

  @Override
  public Response getWorkflowVersion(String identifier, String version, String harnessAccount) {
    validateFeatureEnabled(harnessAccount);
    WorkflowLibraryEntity workflow = workflowLibraryService.getWorkflowVersion(harnessAccount, identifier, version);
    return Response.status(Response.Status.OK).entity(workflow).build();
  }

  @Override
  public Response installWorkflow(@Valid WorkflowInstallRequest request, String identifier, String harnessAccount) {
    validateFeatureEnabled(harnessAccount);
    InstallIntegrationParams integrationParams = buildIntegrationParams(request);
    WorkflowInstallResponse response = workflowLibraryService.install(harnessAccount,
        request.getPipelineOrgIdentifier(), request.getPipelineProjectIdentifier(), request.getWorkflowOrgIdentifier(),
        request.getWorkflowProjectIdentifier(), identifier, request.getVersion(),
        request.getWorkflowInstanceIdentifier(), request.getWorkflowInstanceName(), request.getAdminInputValues(),
        request.getGitDetails(), integrationParams);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  private InstallIntegrationParams buildIntegrationParams(WorkflowInstallRequest request) {
    if (request.getIntegration() == null) {
      return null;
    }
    var integration = request.getIntegration();
    validateIntegrationParams(integration);

    return InstallIntegrationParams.builder()
        .integrationIdentifier(integration.getIdentifier())
        .orgIdentifier(integration.getOrgIdentifier())
        .projectIdentifier(integration.getProjectIdentifier())
        .scopes(integration.getScopes())
        .targets(mapTargets(integration.getTargets()))
        .fieldMappings(mapFieldMappings(integration.getFieldMappings()))
        .build();
  }

  private void validateIntegrationParams(
      io.harness.spec.server.idp.v1.model.WorkflowInstallRequestIntegration integration) {
    if (StringUtils.isEmpty(integration.getIdentifier())) {
      throw new InvalidRequestException("integration.identifier is required when integration is provided");
    }
    if (StringUtils.isEmpty(integration.getOrgIdentifier())) {
      throw new InvalidRequestException("integration.orgIdentifier is required when integration is provided");
    }
    if (StringUtils.isEmpty(integration.getProjectIdentifier())) {
      throw new InvalidRequestException("integration.projectIdentifier is required when integration is provided");
    }
    if (CollectionUtils.isEmpty(integration.getScopes())) {
      throw new InvalidRequestException("integration.scopes is required when integration is provided");
    }
    if (CollectionUtils.isEmpty(integration.getTargets())) {
      throw new InvalidRequestException("integration.targets is required when integration is provided");
    }
  }

  private List<LinkTarget> mapTargets(List<io.harness.spec.server.idp.v1.model.LinkTarget> targets) {
    return targets.stream()
        .map(t -> LinkTarget.builder().entityKind(t.getEntityKind()).entityType(t.getEntityType()).build())
        .collect(Collectors.toList());
  }

  private List<FieldMapping> mapFieldMappings(List<io.harness.spec.server.idp.v1.model.FieldMapping> mappings) {
    if (CollectionUtils.isEmpty(mappings)) {
      return null;
    }
    return mappings.stream()
        .map(fm -> FieldMapping.builder().input(fm.getInput()).entityFieldSource(fm.getEntityFieldSource()).build())
        .collect(Collectors.toList());
  }

  private void validateFeatureEnabled(String accountId) {
    if (!CGRestUtils.getResponse(
            accountClient.isFeatureFlagEnabled(FeatureName.IDP_ENABLE_WORKFLOW_LIBRARY.name(), accountId))) {
      throw new InvalidRequestException("Workflow Library is not enabled for this account");
    }
  }
}
