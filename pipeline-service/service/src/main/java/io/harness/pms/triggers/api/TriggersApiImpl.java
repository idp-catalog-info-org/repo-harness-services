/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.api;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.spec.server.pipeline.v1.TriggersApi;
import io.harness.spec.server.pipeline.v1.model.TriggerRequestBody;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class TriggersApiImpl implements TriggersApi {
  private final NGTriggerService ngTriggerService;
  private final AccessControlClient accessControlClient;
  private final NGTriggerEventsService ngTriggerEventsService;
  private final NGTriggerApiUtils ngTriggerApiUtils;
  private final PMSYamlSchemaService pmsYamlSchemaService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public Response createTrigger(@Valid TriggerRequestBody body, String org, String project, String pipeline,
      Boolean ignoreError, String harnessAccount, Boolean isUnifiedPipelineFlow) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(harnessAccount, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EXECUTE);
    NGTriggerEntity createdEntity;
    try {
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
      boolean isParentIdQueryingEnabled = true;
      TriggerDetails triggerDetails = ngTriggerApiUtils.toTriggerDetails(
          isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : harnessAccount,
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : org,
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : project,
          isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : "", body, pipeline);
      ngTriggerService.validateTriggerConfig(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      if (HarnessYamlVersion.isV1(triggerDetails.getNgTriggerEntity().getHarnessVersion())) {
        pmsYamlSchemaService.validateTriggerYamlSchema(
            harnessAccount, org, project, YamlUtils.readAsJsonNode(body.getYaml()), HarnessYamlVersion.V1);
      }
      triggerDetails.getNgTriggerEntity().setIsUnifiedPipelineFlow(Boolean.TRUE.equals(isUnifiedPipelineFlow));
      if (ignoreError != null && ignoreError) {
        createdEntity =
            ngTriggerService.create(triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
        createdEntity =
            ngTriggerService.create(triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      }
      return Response.ok().entity(ngTriggerApiUtils.toResponseDTO(createdEntity)).build();
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while Saving Trigger: " + e.getMessage());
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getTrigger(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String trigger, @AccountIdentifier String harnessAccount) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity =
        ngTriggerService.get(harnessAccount, org, project, pipeline, trigger, scopeInfo, isParentIdQueryingEnabled);

    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", trigger));
    }
    return Response.ok()
        .entity(ngTriggerApiUtils.toGetResponseDTO(ngTriggerEntity.get(), scopeInfo, isParentIdQueryingEnabled))
        .build();
  }

  @Override
  public Response updateTrigger(@Valid TriggerRequestBody body, String org, String project, String pipeline,
      String trigger, Boolean ignoreError, String harnessAccount, Boolean isUnifiedPipelineFlow) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(harnessAccount, org, project, pipeline,
        pmsFeatureFlagService.isEnabled(harnessAccount, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EXECUTE);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity =
        ngTriggerService.get(harnessAccount, org, project, pipeline, trigger, scopeInfo, isParentIdQueryingEnabled);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", trigger));
    }

    try {
      TriggerDetails triggerDetails = ngTriggerApiUtils.toTriggerDetails(
          isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : harnessAccount,
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : org,
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : project,
          isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : "", body, pipeline);
      triggerDetails = ngTriggerService.fetchTriggerEntityV1(harnessAccount, org, project, pipeline, trigger,
          triggerDetails.getNgTriggerConfigV2(), triggerDetails.getNgTriggerEntity(), scopeInfo,
          isParentIdQueryingEnabled);

      ngTriggerService.validateTriggerConfig(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      if (HarnessYamlVersion.isV1(triggerDetails.getNgTriggerEntity().getHarnessVersion())) {
        pmsYamlSchemaService.validateTriggerYamlSchema(
            harnessAccount, org, project, YamlUtils.readAsJsonNode(body.getYaml()), HarnessYamlVersion.V1);
      }
      if (isUnifiedPipelineFlow != null) {
        triggerDetails.getNgTriggerEntity().setIsUnifiedPipelineFlow(Boolean.TRUE.equals(isUnifiedPipelineFlow));
      } else {
        triggerDetails.getNgTriggerEntity().setIsUnifiedPipelineFlow(ngTriggerEntity.get().getIsUnifiedPipelineFlow());
      }
      NGTriggerEntity updatedEntity;
      if (ignoreError != null && ignoreError) {
        updatedEntity = ngTriggerService.update(
            triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get(), scopeInfo, isParentIdQueryingEnabled);
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
        updatedEntity = ngTriggerService.update(
            triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get(), scopeInfo, isParentIdQueryingEnabled);
      }
      return Response.ok().entity(ngTriggerApiUtils.toResponseDTO(updatedEntity)).build();

    } catch (Exception e) {
      throw new InvalidRequestException("Failed while updating Trigger: " + e.getMessage());
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response deleteTrigger(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String trigger, @AccountIdentifier String harnessAccount) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    boolean isParentIdQueryingEnabled = true;
    boolean triggerDeleted = ngTriggerService.delete(
        harnessAccount, org, project, pipeline, trigger, null, scopeInfo, isParentIdQueryingEnabled);
    if (triggerDeleted) {
      ngTriggerEventsService.deleteTriggerEventHistory(
          harnessAccount, org, project, pipeline, trigger, scopeInfo, isParentIdQueryingEnabled);
    }
    return Response.status(204).build();
  }
}
