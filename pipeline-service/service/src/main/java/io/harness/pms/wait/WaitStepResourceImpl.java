/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.wait;

import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PLAN_EXECUTION_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.WAIT_STEP_ACTION;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_EXECUTE;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;
import static io.harness.pms.utils.PmsConstants.PIPELINE;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants;
import io.harness.steps.wait.WaitStepService;
import io.harness.wait.WaitStepInstance;

import com.google.inject.Inject;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@PipelineServiceAuth
@Slf4j
public class WaitStepResourceImpl implements WaitStepResource {
  @Inject WaitStepService waitStepService;
  @Inject private AccessControlClient accessControlClient;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanExecutionService planExecutionService;

  @Inject private PipelineTelemetryHelper pipelineTelemetryHelper;

  @Override
  public ResponseDTO<WaitStepResponseDto> markAsFailOrSuccess(
      String accountId, String orgId, String projectId, String nodeExecutionId, WaitStepRequestDto waitStepRequestDto) {
    String planExecutionId = nodeExecutionService.get(nodeExecutionId).getPlanExecutionId();
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_EXECUTE);
    waitStepService.markAsFailOrSuccess(
        planExecutionId, nodeExecutionId, WaitStepActionMapper.mapWaitStepAction(waitStepRequestDto.getAction()));
    sendTelemetryEvent(accountId, orgId, projectId, nodeExecutionId, planExecutionId, pipelineIdentifier,
        waitStepRequestDto.getAction());
    return ResponseDTO.newResponse(WaitStepResponseDto.builder().status(true).build());
  }

  @Override
  public ResponseDTO<WaitStepExecutionDetailsDto> getWaitStepExecutionDetails(
      String accountId, String orgId, String projectId, String nodeExecutionId) {
    String planExecutionId = nodeExecutionService.get(nodeExecutionId).getPlanExecutionId();
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_VIEW);
    WaitStepInstance waitStepInstance = waitStepService.getWaitStepExecutionDetails(nodeExecutionId);
    return ResponseDTO.newResponse(WaitStepExecutionDetailsDto.builder()
                                       .createdAt(waitStepInstance.getCreatedAt())
                                       .duration(waitStepInstance.getDuration())
                                       .nodeExecutionId(waitStepInstance.getNodeExecutionId())
                                       .build());
  }

  private void sendTelemetryEvent(String accountId, String orgId, String projectId, String nodeExecutionId,
      String planExecutionId, String pipelineIdentifier, WaitStepActionDto waitStepActionDto) {
    try {
      HashMap<String, Object> propertiesMap = new HashMap<>();
      propertiesMap.put(PROJECT_IDENTIFIER, projectId);
      propertiesMap.put(ORG_IDENTIFIER, orgId);
      propertiesMap.put(PLAN_EXECUTION_ID, planExecutionId);
      propertiesMap.put(PIPELINE_ID, pipelineIdentifier);
      propertiesMap.put(WAIT_STEP_ACTION, waitStepActionDto.name());
      pipelineTelemetryHelper.sendTelemetryEventWithAccountName(
          PipelineInstrumentationConstants.PIPELINE_WAIT_EVENT, accountId, propertiesMap);
    } catch (Exception e) {
      log.error("Failed to send the telemetry event for the wait step for PlanExecutionId: {} with Error: {}",
          planExecutionId, e.getMessage());
    }
  }
}
