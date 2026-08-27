/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.PipelineExecutionDetailsApiUtils;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.PipelineExecutionDetailsApi;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
public class PipelineExecutionDetailsApiImpl implements PipelineExecutionDetailsApi {
  @Inject private PMSExecutionService pmsExecutionService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ExecutionHelper executionHelper;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private DynamicExecutionService dynamicExecutionService;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  private final String PIPELINE_RESOURCE_TYPE = "PIPELINE";

  @Override
  public Response canRetryExecution(String org, String project, String executionId, String harnessAccount) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.fetchExecutionSummary(harnessAccount, executionId, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);
    Boolean isLatestExecution = retryExecutionHelper.isLatestExecution(executionSummaryEntity);
    Boolean canRetry =
        !ExecutionModeUtils.isRollbackMode(executionSummaryEntity.getExecutionMode()) && isLatestExecution;
    return Response.ok().entity(canRetry).build();
  }

  @Override
  public Response getDynamicExecutionDetails(
      String org, String project, String planExecutionId, String nodeExecutionId, String harnessAccount) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(harnessAccount, planExecutionId, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    DynamicExecutionInstanceResponseDTO dynamicExecutionInstance =
        dynamicExecutionService.getByNodeExecutionId(nodeExecutionId);
    return Response.ok()
        .entity(PipelineExecutionDetailsApiUtils.toDynamicExecutionDetailsResponseBody(dynamicExecutionInstance))
        .build();
  }

  @Override
  public Response getExecutionDetails(String org, String project, String planExecutionId, String stageNodeId,
      String stageNodeExecutionId, String childStageNodeId, String childStageNodeExecutionId,
      Boolean renderFullBottomGraph, String harnessAccount) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(harnessAccount, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }

    PipelineExecutionDetailDTO executionDetailDTO = executionHelper.getResponseDTO(stageNodeId, stageNodeExecutionId,
        childStageNodeId, renderFullBottomGraph, executionSummaryEntity, entityGitDetails, childStageNodeExecutionId);

    return Response.ok()
        .entity(PipelineExecutionDetailsApiUtils.toPipelineExecutionDetailsResponseBody(executionDetailDTO))
        .build();
  }

  @Override
  public Response getInputSetYaml(String org, String project, String planExecutionId, Boolean resolveExpressions,
      String resolveExpressionsType, String harnessAccount) {
    return Response.ok()
        .entity(pmsExecutionService
                    .getInputSetYamlWithTemplate(harnessAccount, org, project, planExecutionId, false,
                        resolveExpressions, toResolveExpressionsType(resolveExpressionsType))
                    .getInputSetYaml())
        .build();
  }

  ResolveInputYamlType toResolveExpressionsType(String resolveExpressionsType) {
    if (resolveExpressionsType.equals("resolve-all-expressions")) {
      return ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS;
    }
    if (resolveExpressionsType.equals("resolve-trigger-expressions")) {
      return ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS;
    }
    return ResolveInputYamlType.UNKNOWN;
  }
}
