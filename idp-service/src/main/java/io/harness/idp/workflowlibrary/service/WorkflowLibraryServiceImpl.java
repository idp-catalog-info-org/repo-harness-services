/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.service;

import static io.harness.idp.common.CommonUtils.buildSpacePath;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.repositories.EntityLinkRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.RbacUtils;
import io.harness.idp.workflowlibrary.entity.WorkflowAdminInput;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.idp.workflowlibrary.entity.WorkflowPipelineSnapshot;
import io.harness.idp.workflowlibrary.repositories.WorkflowLibraryRepository;
import io.harness.idp.workflowlibrary.utils.WorkflowYamlSubstitutionUtils;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.GitCreateDetails;
import io.harness.spec.server.idp.v1.model.WorkflowInstallResponse;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class WorkflowLibraryServiceImpl implements WorkflowLibraryService {
  private final WorkflowLibraryRepository repository;
  private final PipelineServiceClient pipelineServiceClient;
  private final CatalogService catalogService;
  private final IDPGitXHelper idpGitXHelper;
  private final AccessControlClient accessControlClient;
  private final AccountClient accountClient;
  private final EntityLinkRepository entityLinkRepository;
  private final TransactionHelper transactionHelper;
  private final String ngBaseUrl;

  @Inject
  public WorkflowLibraryServiceImpl(WorkflowLibraryRepository repository,
      @Named("NON_PRIVILEGED") PipelineServiceClient pipelineServiceClient, CatalogService catalogService,
      IDPGitXHelper idpGitXHelper, AccessControlClient accessControlClient, AccountClient accountClient,
      EntityLinkRepository entityLinkRepository, TransactionHelper transactionHelper,
      @Named("ngBaseUrl") String ngBaseUrl) {
    this.repository = repository;
    this.pipelineServiceClient = pipelineServiceClient;
    this.catalogService = catalogService;
    this.idpGitXHelper = idpGitXHelper;
    this.accessControlClient = accessControlClient;
    this.accountClient = accountClient;
    this.entityLinkRepository = entityLinkRepository;
    this.transactionHelper = transactionHelper;
    this.ngBaseUrl = ngBaseUrl;
  }

  @Override
  public List<WorkflowLibraryEntity> listWorkflows(String accountId, String category) {
    if (isPreviewEnabled(accountId)) {
      if (category != null && !category.isEmpty()) {
        return repository.findByCategoryAndIsStableTrueAndDeprecatedFalse(category);
      }
      return repository.findByIsStableTrueAndDeprecatedFalse();
    }
    if (category != null && !category.isEmpty()) {
      return repository.findByStatusAndCategoryAndIsStableTrueAndDeprecatedFalse(
          WorkflowLibraryEntity.STATUS_GA, category);
    }
    return repository.findByStatusAndIsStableTrueAndDeprecatedFalse(WorkflowLibraryEntity.STATUS_GA);
  }

  @Override
  public WorkflowLibraryEntity getWorkflow(String accountId, String identifier) {
    WorkflowLibraryEntity entity = repository.findByIdentifierAndIsStableTrue(identifier);
    if (entity == null) {
      throw new InvalidRequestException("Workflow not found: " + identifier);
    }
    if (!isPreviewEnabled(accountId) && WorkflowLibraryEntity.STATUS_PREVIEW.equals(entity.getStatus())) {
      throw new InvalidRequestException("Workflow not found: " + identifier);
    }
    return entity;
  }

  @Override
  public WorkflowLibraryEntity getWorkflowVersion(String accountId, String identifier, String version) {
    WorkflowLibraryEntity entity = repository.findByIdentifierAndVersion(identifier, version);
    if (entity == null) {
      throw new InvalidRequestException(String.format("Workflow not found: %s version: %s", identifier, version));
    }
    if (!isPreviewEnabled(accountId) && WorkflowLibraryEntity.STATUS_PREVIEW.equals(entity.getStatus())) {
      throw new InvalidRequestException("Workflow not found: " + identifier);
    }
    return entity;
  }

  @Override
  public List<WorkflowLibraryEntity> getVersions(String accountId, String identifier) {
    return repository.findByIdentifierOrderByVersionDesc(identifier);
  }

  @Override
  public WorkflowInstallResponse install(String accountId, String pipelineOrgId, String pipelineProjectId,
      String workflowOrgId, String workflowProjectId, String identifier, String version,
      String workflowInstanceIdentifier, String workflowInstanceName, Map<String, String> adminInputValues,
      GitCreateDetails gitDetails, InstallIntegrationParams integrationParams) {
    WorkflowLibraryEntity entity = version != null ? repository.findByIdentifierAndVersion(identifier, version)
                                                   : repository.findByIdentifierAndIsStableTrue(identifier);

    if (entity == null) {
      throw new InvalidRequestException("Workflow not found: " + identifier);
    }

    if (workflowInstanceIdentifier == null || workflowInstanceIdentifier.isEmpty()) {
      throw new InvalidRequestException("workflowInstanceIdentifier is required");
    }

    if (!workflowInstanceIdentifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
      throw new InvalidRequestException("workflowInstanceIdentifier must start with a letter or underscore and contain "
          + "only alphanumeric characters and underscores");
    }

    if (pipelineOrgId == null || pipelineOrgId.isEmpty() || pipelineProjectId == null || pipelineProjectId.isEmpty()) {
      throw new InvalidRequestException("pipelineOrgIdentifier and pipelineProjectIdentifier are required");
    }

    validateRequiredInputs(entity, adminInputValues);

    accessControlClient.checkForAccessOrThrow(
        RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
        ResourceScope.of(accountId, workflowOrgId, workflowProjectId), Resource.of("IDP_WORKFLOW", null),
        "idp_workflow_edit", "Missing permission to create workflow in the specified scope");

    accessControlClient.checkForAccessOrThrow(
        RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
        ResourceScope.of(accountId, pipelineOrgId, pipelineProjectId), Resource.of("PIPELINE", null),
        "core_pipeline_edit", "Missing permission to create pipelines in the specified project");

    validateWorkflowInstanceNotExists(accountId, workflowOrgId, workflowProjectId, workflowInstanceIdentifier);

    List<WorkflowPipelineSnapshot> pipelines =
        entity.getPipelines() != null ? entity.getPipelines() : new ArrayList<>();
    validatePipelinesNotExist(accountId, pipelineOrgId, pipelineProjectId, workflowInstanceIdentifier, pipelines);

    List<String> createdPipelineIds = new ArrayList<>();
    Map<String, String> refToRealId = new HashMap<>();

    try {
      for (WorkflowPipelineSnapshot pipeline : pipelines) {
        String pipelineYaml = pipeline.getPipelineYaml();
        pipelineYaml = WorkflowYamlSubstitutionUtils.substituteScope(pipelineYaml, pipelineOrgId, pipelineProjectId);
        pipelineYaml = WorkflowYamlSubstitutionUtils.substituteAdminInputs(pipelineYaml, adminInputValues);

        String sanitizedPipelineId = pipeline.getIdentifier().replace("-", "_");
        String realPipelineId = workflowInstanceIdentifier + "_" + sanitizedPipelineId;
        String realPipelineName = pipeline.getName() + " - " + workflowInstanceIdentifier;
        pipelineYaml = pipelineYaml.replace("identifier: " + pipeline.getIdentifier(), "identifier: " + realPipelineId);
        pipelineYaml = pipelineYaml.replace("name: " + pipeline.getName(), "name: " + realPipelineName);

        PipelineCreateRequestBody body = new PipelineCreateRequestBody()
                                             .name(realPipelineName)
                                             .identifier(realPipelineId)
                                             .pipelineYaml(pipelineYaml);

        log.info("Creating pipeline with id={}, name={}, org={}, project={}, yamlLength={}", realPipelineId,
            realPipelineName, pipelineOrgId, pipelineProjectId, pipelineYaml.length());
        NGRestUtils.getGeneralResponse(
            pipelineServiceClient.createPipeline(body, pipelineOrgId, pipelineProjectId, accountId));
        createdPipelineIds.add(realPipelineId);
        String pipelineUrl = buildPipelineUrl(accountId, pipelineOrgId, pipelineProjectId, realPipelineId);
        String symbolicRef = pipeline.getSymbolicRef() != null ? pipeline.getSymbolicRef() : pipeline.getIdentifier();
        refToRealId.put(symbolicRef, pipelineUrl);
        log.info("Created pipeline {} for workflow {} in account {}", realPipelineId, identifier, accountId);
      }

      String workflowYaml = entity.getWorkflowYaml();
      workflowYaml = workflowYaml.replaceFirst("(?m)^identifier: .+$", "identifier: " + workflowInstanceIdentifier);
      if (workflowInstanceName != null && !workflowInstanceName.isEmpty()) {
        workflowYaml =
            workflowYaml.replaceFirst("(?m)^name: .+$", "name: " + Matcher.quoteReplacement(workflowInstanceName));
      }
      workflowYaml = WorkflowYamlSubstitutionUtils.substituteSymbolicRefs(workflowYaml, refToRealId);
      workflowYaml = WorkflowYamlSubstitutionUtils.injectScopeFields(workflowYaml, workflowOrgId, workflowProjectId);

      EntityCreateRequest entityCreateRequest = new EntityCreateRequest().yaml(workflowYaml);
      if (gitDetails != null && GitCreateDetails.StoreTypeEnum.REMOTE.equals(gitDetails.getStoreType())) {
        entityCreateRequest.setGitDetails(gitDetails);
        GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitCreateDetails(gitDetails));
      }
      try {
        catalogService.createEntity(accountId, workflowOrgId, workflowProjectId, false, false, entityCreateRequest);
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
      log.info("Installed workflow {} (version {}) in account {} pipelineScope [{}/{}] workflowScope [{}/{}]",
          identifier, entity.getVersion(), accountId, pipelineOrgId, pipelineProjectId, workflowOrgId,
          workflowProjectId);

      if (integrationParams != null && integrationParams.getIntegrationIdentifier() != null) {
        String entityRef =
            CatalogUtils.entityRef("workflow", workflowOrgId, workflowProjectId, workflowInstanceIdentifier);
        String spacePath =
            buildSpacePath(accountId, integrationParams.getOrgIdentifier(), integrationParams.getProjectIdentifier());

        EntityLinks entityLink = EntityLinks.builder()
                                     .accountIdentifier(accountId)
                                     .entityRef(entityRef)
                                     .scopes(integrationParams.getScopes())
                                     .targets(integrationParams.getTargets())
                                     .fieldMappings(integrationParams.getFieldMappings())
                                     .integrations(List.of(EntityLinks.IntegrationReference.builder()
                                                               .identifier(integrationParams.getIntegrationIdentifier())
                                                               .spacePath(spacePath)
                                                               .build()))
                                     .build();

        transactionHelper.performTransaction(() -> {
          entityLinkRepository.save(entityLink);
          return null;
        });
        log.info("Created entity link for workflow {} with integration {} in account {}", workflowInstanceIdentifier,
            integrationParams.getIntegrationIdentifier(), accountId);
      }

      return new WorkflowInstallResponse()
          .workflowIdentifier(identifier)
          .workflowInstanceIdentifier(workflowInstanceIdentifier)
          .version(entity.getVersion())
          .pipelineIdentifiers(createdPipelineIds)
          .pipelineOrgIdentifier(pipelineOrgId)
          .pipelineProjectIdentifier(pipelineProjectId)
          .workflowOrgIdentifier(workflowOrgId)
          .workflowProjectIdentifier(workflowProjectId)
          .status(WorkflowInstallResponse.StatusEnum.SUCCESS);
    } catch (InvalidRequestException e) {
      log.error("Failed to install workflow {} in account {}: {}", identifier, accountId, e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Failed to install workflow {} in account {}", identifier, accountId, e);
      throw new InvalidRequestException("Failed to install workflow: " + e.getMessage(), e);
    }
  }

  private String buildPipelineUrl(String accountId, String orgId, String projectId, String pipelineId) {
    // ngBaseUrl is the UI base, e.g. "https://qa.harness.io/ng/#/". Take only its origin (scheme + host + port)
    // and append the full, known pipeline-studio path so we never depend on the base carrying "/ng", "#", or slashes.
    String origin;
    try {
      URL url = new URL(ngBaseUrl);
      origin = url.getPort() == -1 ? String.format("%s://%s", url.getProtocol(), url.getHost())
                                   : String.format("%s://%s:%d", url.getProtocol(), url.getHost(), url.getPort());
    } catch (MalformedURLException e) {
      log.warn("Malformed ngBaseUrl [{}], falling back to trimmed base for pipeline URL", ngBaseUrl, e);
      origin = ngBaseUrl.replaceAll("[/#]+$", "");
    }
    return String.format("%s/ng/account/%s/all/orgs/%s/projects/%s/pipelines/%s/pipeline-studio/?storeType=INLINE",
        origin, accountId, orgId, projectId, pipelineId);
  }

  private boolean isPreviewEnabled(String accountId) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_SHOW_PREVIEW_OOTB_WORKFLOWS.name(), accountId));
  }

  private void validateWorkflowInstanceNotExists(
      String accountId, String orgId, String projectId, String workflowInstanceIdentifier) {
    try {
      String entityRef = CatalogUtils.entityRef("workflow", orgId, projectId, workflowInstanceIdentifier);
      catalogService.getEntity(accountId, orgId, projectId, entityRef, false, false, false);
      throw new InvalidRequestException(
          "Workflow with identifier [" + workflowInstanceIdentifier + "] already exists in the specified scope");
    } catch (EntityNotFoundException e) {
      // entity does not exist, safe to proceed
    }
  }

  private void validatePipelinesNotExist(String accountId, String pipelineOrgId, String pipelineProjectId,
      String workflowInstanceIdentifier, List<WorkflowPipelineSnapshot> pipelines) {
    List<String> existing = new ArrayList<>();
    for (WorkflowPipelineSnapshot pipeline : pipelines) {
      String realPipelineId = workflowInstanceIdentifier + "_" + pipeline.getIdentifier().replace("-", "_");
      try {
        NGRestUtils.getGeneralResponse(pipelineServiceClient.getPipeline(
            pipelineOrgId, pipelineProjectId, realPipelineId, accountId, null, null, null, null, null, null, null));
        existing.add(realPipelineId);
      } catch (Exception e) {
        // pipeline does not exist, safe to proceed
      }
    }
    if (!existing.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Pipeline(s) already exist in org [%s] project [%s]: %s", pipelineOrgId, pipelineProjectId, existing));
    }
  }

  private void validateRequiredInputs(WorkflowLibraryEntity entity, Map<String, String> adminInputValues) {
    List<WorkflowAdminInput> allInputs = entity.getAdminInputs() != null ? entity.getAdminInputs() : new ArrayList<>();
    for (WorkflowAdminInput input : allInputs) {
      if (input.isRequired() && (adminInputValues == null || !adminInputValues.containsKey(input.getKey()))) {
        throw new InvalidRequestException("Required admin input missing: " + input.getKey());
      }
    }
  }
}
