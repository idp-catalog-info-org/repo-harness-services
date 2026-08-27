/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_EDIT;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.goconvert.EntityType;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionNodeSummary;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.beans.PipelineConversionMetricsDTO;
import io.harness.pms.conversion.mapper.ConversionJobApiMapper;
import io.harness.pms.conversion.service.ConversionJobService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.spec.server.pipeline.v1.PipelineConversionApi;
import io.harness.spec.server.pipeline.v1.model.ConversionJobCreatedResponseBody;
import io.harness.spec.server.pipeline.v1.model.ConversionJobRequestBody;
import io.harness.spec.server.pipeline.v1.model.ConversionJobResponseBody;
import io.harness.spec.server.pipeline.v1.model.EntityIdentifier;
import io.harness.utils.PmsFeatureFlagService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of V0 to V1 conversion API.
 * Modern v1 API standard - uses generated spec models from OpenAPI, no intermediate DTOs.
 * Follows the same pattern as PipelineExecutionApiImpl for consistency.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Hidden
@Slf4j
public class PipelineConversionApiImpl implements PipelineConversionApi {
  private static final String PIPELINE_RESOURCE_TYPE = "PIPELINE";
  private static final String TEMPLATE_RESOURCE_TYPE = "TEMPLATE";
  private static final String TEMPLATE_EDIT_PERMISSION = "core_template_edit";
  private static final String TEMPLATE_VIEW_PERMISSION = "core_template_view";

  private final ConversionJobService conversionJobService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final AccessControlClient accessControlClient;

  @Override
  @Timed
  @ResponseMetered
  public Response createConversionJob(@Valid ConversionJobRequestBody body, String harnessAccount) {
    checkFeatureFlag(body.getAccountId());
    log.info("Creating conversion job for account: {}, actionType: {}", body.getAccountId(), body.getActionType());
    authorizeCreate(body);

    // Convert spec model directly to entity
    ConversionJobEntity jobEntity = ConversionJobApiMapper.toEntity(body.getAccountId(), body);

    // Capture the triggering user's principal so the iterator thread can use it for branch protection bypass
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal != null) {
      jobEntity.setTriggerPrincipal(sourcePrincipal);
    }

    // Save the job entity (iterator will pick it up via nextIteration)
    ConversionJobEntity savedEntity = conversionJobService.createJob(jobEntity);

    // Return only UUID - full status available via GET endpoint
    ConversionJobCreatedResponseBody responseBody = new ConversionJobCreatedResponseBody();
    responseBody.setUuid(savedEntity.getUuid());

    log.info("Conversion job created with UUID: {}", savedEntity.getUuid());

    return Response.ok().entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getConversionJob(String jobId, String harnessAccount) {
    checkFeatureFlag(harnessAccount);
    log.info("Getting conversion job status for jobId: {}", jobId);

    Optional<ConversionJobEntity> jobOptional = conversionJobService.getJobByUuid(jobId);

    if (jobOptional.isEmpty()) {
      throw new EntityNotFoundException("Conversion job not found with UUID: " + jobId);
    }

    ConversionJobEntity job = jobOptional.get();
    authorizeJobAccess(job, false);
    List<ConversionJobEntity> children = getChildJobsIfApplicable(job);

    // If conversionResults are not cached and job is still in progress, build them live from children
    if (job.getConversionResults() == null && !ConversionStatus.isFinalStatus(job.getStatus()) && !children.isEmpty()) {
      job.setConversionResults(buildLiveConversionResults(job, children));
    }

    ConversionJobResponseBody responseBody = ConversionJobApiMapper.toResponseBody(job);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getConversionJobByEntity(
      String entityId, String entityType, String org, String project, String harnessAccount) {
    checkFeatureFlag(harnessAccount);
    log.info("Getting conversion job by entity scope: entityId={}, entityType={}, org={}, project={}", entityId,
        entityType, org, project);

    EntityType type = EntityType.valueOf(entityType);
    authorizeEntityAccess(harnessAccount, org, project, type, entityId, false);

    Optional<ConversionJobEntity> jobOptional =
        conversionJobService.getJobByEntityScope(harnessAccount, org, project, entityId, type);

    if (jobOptional.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("No conversion job found for entity %s of type %s", entityId, entityType));
    }

    ConversionJobEntity job = jobOptional.get();
    List<ConversionJobEntity> children = getChildJobsIfApplicable(job);
    if (job.getConversionResults() == null && !ConversionStatus.isFinalStatus(job.getStatus()) && !children.isEmpty()) {
      job.setConversionResults(buildLiveConversionResults(job, children));
    }
    ConversionJobResponseBody responseBody = ConversionJobApiMapper.toResponseBody(job);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response retryConversionJob(String jobId, String harnessAccount) {
    checkFeatureFlag(harnessAccount);
    log.info("Retrying conversion job: {}", jobId);

    Optional<ConversionJobEntity> jobOptional = conversionJobService.getJobByUuid(jobId);

    if (jobOptional.isEmpty()) {
      throw new EntityNotFoundException("Conversion job not found with UUID: " + jobId);
    }

    ConversionJobEntity job = jobOptional.get();
    authorizeJobAccess(job, true);
    if (!(job.getStatus() == ConversionStatus.FAILED || job.getStatus() == ConversionStatus.PARTIAL_SUCCESS)) {
      throw new InvalidRequestException(
          String.format("Cannot retry job %s in status %s. Only FAILED or PARTIAL_SUCCESS jobs can be retried.", jobId,
              job.getStatus()));
    }

    // Re-capture the retrying user's principal for branch protection bypass
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal != null) {
      conversionJobService.updateTriggerPrincipal(jobId, sourcePrincipal);
    }

    ConversionJobEntity retriedJob = conversionJobService.retryJob(jobId);
    ConversionJobResponseBody responseBody = ConversionJobApiMapper.toResponseBody(retriedJob);
    return Response.ok().entity(responseBody).build();
  }

  private List<ConversionJobEntity> getChildJobsIfApplicable(ConversionJobEntity job) {
    return conversionJobService.getChildJobs(job.getUuid());
  }

  private List<ConversionNodeSummary> buildLiveConversionResults(
      ConversionJobEntity job, List<ConversionJobEntity> children) {
    if (job.getActionType() == ConversionActionType.SINGLE) {
      return List.of(buildNodeSummaryRecursive(job, children));
    }
    // BATCH/PROJECT — each child is a top-level result entry
    return children.stream().map(c -> buildNodeSummaryRecursive(c, null)).collect(Collectors.toList());
  }

  private ConversionNodeSummary buildNodeSummaryRecursive(
      ConversionJobEntity entity, List<ConversionJobEntity> prefetchedChildren) {
    List<ConversionJobEntity> children = prefetchedChildren != null ? prefetchedChildren
        : (entity.getEntityType() == EntityType.PIPELINE || entity.getEntityType() == EntityType.TEMPLATE)
        ? conversionJobService.getChildJobs(entity.getUuid())
        : Collections.emptyList();

    List<ConversionNodeSummary> childNodes = null;
    PipelineConversionMetricsDTO pipelineMetricsDTO = null;

    if (!children.isEmpty()) {
      childNodes = children.stream().map(c -> buildNodeSummaryRecursive(c, null)).collect(Collectors.toList());
      if (entity.getEntityType() == EntityType.PIPELINE) {
        pipelineMetricsDTO = buildPipelineMetrics(children);
      }
    }

    String versionLabel = entity.getEntityReference() != null ? entity.getEntityReference().getVersionLabel() : null;

    return ConversionNodeSummary.builder()
        .entityIdentifier(entity.getEntityIdentifier())
        .versionLabel(versionLabel)
        .v1Identifier(entity.getV1Identifier())
        .entityType(entity.getEntityType())
        .status(entity.getStatus())
        .errorMessage(entity.getErrorMessage())
        .pipelineMetrics(pipelineMetricsDTO)
        .errors(entity.getConversionErrors())
        .children(childNodes)
        .build();
  }

  private PipelineConversionMetricsDTO buildPipelineMetrics(List<ConversionJobEntity> children) {
    int totalIS = 0, convertedIS = 0, skippedIS = 0, failedIS = 0;
    int totalTpl = 0, convertedTpl = 0, skippedTpl = 0, failedTpl = 0;
    int totalTrg = 0, convertedTrg = 0, skippedTrg = 0, failedTrg = 0;

    for (ConversionJobEntity child : children) {
      switch (child.getEntityType()) {
        case INPUT_SET:
          totalIS++;
          if (child.getStatus() == ConversionStatus.SUCCESS)
            convertedIS++;
          else if (child.getStatus() == ConversionStatus.SKIPPED)
            skippedIS++;
          else if (child.getStatus() == ConversionStatus.FAILED)
            failedIS++;
          break;
        case TEMPLATE:
          totalTpl++;
          if (child.getStatus() == ConversionStatus.SUCCESS)
            convertedTpl++;
          else if (child.getStatus() == ConversionStatus.SKIPPED)
            skippedTpl++;
          else if (child.getStatus() == ConversionStatus.FAILED)
            failedTpl++;
          break;
        case TRIGGER:
          totalTrg++;
          if (child.getStatus() == ConversionStatus.SUCCESS)
            convertedTrg++;
          else if (child.getStatus() == ConversionStatus.SKIPPED)
            skippedTrg++;
          else if (child.getStatus() == ConversionStatus.FAILED)
            failedTrg++;
          break;
        default:
          break;
      }
    }

    return PipelineConversionMetricsDTO.builder()
        .totalInputSets(totalIS)
        .convertedInputSets(convertedIS)
        .skippedInputSets(skippedIS)
        .failedInputSets(failedIS)
        .totalTemplates(totalTpl)
        .convertedTemplates(convertedTpl)
        .skippedTemplates(skippedTpl)
        .failedTemplates(failedTpl)
        .totalTriggers(totalTrg)
        .convertedTriggers(convertedTrg)
        .skippedTriggers(skippedTrg)
        .failedTriggers(failedTrg)
        .build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response deleteConversionChecksums(
      String harnessAccount, String org, String project, String entityId, String entityType, String versionLabel) {
    checkFeatureFlag(harnessAccount);

    if (entityId == null && (org == null || project == null)) {
      throw new InvalidRequestException(
          "entity_id is required for org/account-level deletion. Bulk delete is only supported at project level");
    }
    if (entityId != null && entityType == null) {
      throw new InvalidRequestException("entity_type is required when entity_id is provided");
    }

    EntityType type = entityType != null ? EntityType.valueOf(entityType) : null;
    authorizeEntityAccess(harnessAccount, org, project, type, entityId, true);
    long deletedCount =
        conversionJobService.deleteChecksums(harnessAccount, org, project, entityId, type, versionLabel);

    Map<String, Object> responseBody = new HashMap<>();
    responseBody.put("deleted_count", deletedCount);
    return Response.ok().entity(responseBody).build();
  }

  private void checkFeatureFlag(String accountId) {
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_V0_TO_V1_CONVERSION)) {
      throw new InvalidRequestException("V0 to V1 conversion is not enabled for this account");
    }
  }

  private void authorizeCreate(ConversionJobRequestBody body) {
    ConversionActionType actionType = ConversionActionType.valueOf(body.getActionType().toString());
    if (actionType == ConversionActionType.SINGLE && body.getEntityReference() != null) {
      EntityIdentifier entityReference = body.getEntityReference();
      authorizeEntityAccess(body.getAccountId(), body.getOrgId(), body.getProjectId(),
          requireEntityType(entityReference), entityReference.getEntityId(), true);
      return;
    }
    if (actionType == ConversionActionType.BATCH && isNotEmpty(body.getEntityReferences())) {
      List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
      for (EntityIdentifier entityReference : body.getEntityReferences()) {
        permissionChecks.add(toPermissionCheckDTO(body.getAccountId(), body.getOrgId(), body.getProjectId(),
            requireEntityType(entityReference), entityReference.getEntityId(), true));
      }
      // One remote ACL round-trip for the whole batch instead of N sequential calls.
      accessControlClient.checkForAccessOrThrow(
          permissionChecks, "Missing permission to convert one or more referenced entities");
      return;
    }
    // PROJECT scope conversion, or an action without resolvable entity references: authorize at the requested scope.
    authorizeEntityAccess(body.getAccountId(), body.getOrgId(), body.getProjectId(), EntityType.PIPELINE, null, true);
  }

  private EntityType requireEntityType(EntityIdentifier entityReference) {
    if (entityReference.getEntityType() == null) {
      throw new InvalidRequestException("entityType is required for each entity reference");
    }
    return EntityType.valueOf(entityReference.getEntityType().toString());
  }

  private void authorizeJobAccess(ConversionJobEntity job, boolean mutation) {
    authorizeEntityAccess(job.getAccountId(), job.getOrgId(), job.getProjectId(), job.getEntityType(),
        job.getEntityIdentifier(), mutation);
  }

  private void authorizeEntityAccess(
      String accountId, String orgId, String projectId, EntityType entityType, String entityId, boolean mutation) {
    PermissionCheckDTO permissionCheck =
        toPermissionCheckDTO(accountId, orgId, projectId, entityType, entityId, mutation);
    accessControlClient.checkForAccessOrThrow(permissionCheck.getResourceScope(),
        Resource.of(permissionCheck.getResourceType(), permissionCheck.getResourceIdentifier()),
        permissionCheck.getPermission());
  }

  private PermissionCheckDTO toPermissionCheckDTO(
      String accountId, String orgId, String projectId, EntityType entityType, String entityId, boolean mutation) {
    String resourceType = entityType == EntityType.TEMPLATE ? TEMPLATE_RESOURCE_TYPE : PIPELINE_RESOURCE_TYPE;
    String permission;
    if (entityType == EntityType.TEMPLATE) {
      permission = mutation ? TEMPLATE_EDIT_PERMISSION : TEMPLATE_VIEW_PERMISSION;
    } else {
      permission = mutation ? PIPELINE_EDIT : PIPELINE_VIEW;
    }
    return PermissionCheckDTO.builder()
        .permission(permission)
        .resourceType(resourceType)
        .resourceIdentifier(entityId)
        .resourceScope(ResourceScope.of(accountId, orgId, projectId))
        .build();
  }
}
