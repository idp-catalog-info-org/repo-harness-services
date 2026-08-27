/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionEntity;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionMetadata;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionStatus;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionType;
import io.harness.ng.core.sfexecution.entity.SalesforceValidateExecutionMetadata;
import io.harness.ng.core.sfexecution.mappers.SalesforceExecutionOpenApiMapper;
import io.harness.ng.core.sfexecution.services.SalesforceExecutionService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.ng.v1.model.SalesforceExecuteRequest;
import io.harness.spec.server.ng.v1.model.SalesforceExecution;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteResponseBody;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@OwnedBy(HarnessTeam.CDC)
public class SalesforceExecutionOrchestrationService {
  private static final String MODULE_CD = "cd";
  private static final String EXECUTION_URL_FORMAT =
      "%s/ng/account/%s/module/cd/orgs/%s/projects/%s/deployments/%s/pipeline";

  private static final EnumSet<ExecutionStatus> SUCCEEDED_PIPELINE_STATUSES = EnumSet.of(ExecutionStatus.SUCCESS);
  private static final EnumSet<ExecutionStatus> ABORTED_PIPELINE_STATUSES =
      EnumSet.of(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE, ExecutionStatus.DISCONTINUING);
  private static final EnumSet<ExecutionStatus> FAILED_PIPELINE_STATUSES = EnumSet.of(ExecutionStatus.FAILED,
      ExecutionStatus.ERRORED, ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALREJECTED, ExecutionStatus.IGNOREFAILED);

  @Inject private SalesforceExecutionService salesforceExecutionService;
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private NextGenConfiguration nextGenConfiguration;

  public SalesforceExecution execute(
      String account, String org, String project, SalesforceExecuteRequest request, ScopeInfo scopeInfo) {
    if (request == null) {
      throw new InvalidRequestException("request body is required");
    }
    if (isEmpty(request.getChangesetId())) {
      throw new InvalidRequestException("changesetId is required");
    }
    SalesforceExecutionType executionType = SalesforceExecutionType.valueOf(request.getType().name());
    SalesforceExecutionMetadata metadata = executionType.createMetadata();
    metadata.setChangesetIdentifier(request.getChangesetId());
    if (metadata instanceof SalesforceValidateExecutionMetadata) {
      ((SalesforceValidateExecutionMetadata) metadata).setTarget(request.getTarget());
    }

    PipelineExecuteRequestBody body = new PipelineExecuteRequestBody().inputsYaml(request.getInputsYaml());
    PipelineExecuteResponseBody pipelineResponse = NGRestUtils.getGeneralResponse(pipelineServiceClient.executePipeline(
        org, project, request.getPipelineId(), body, account, MODULE_CD, null, null, null, null, null, null));

    String planExecutionId = pipelineResponse.getExecutionDetails().getExecutionId();
    String executionUrl = buildExecutionUrl(account, org, project, planExecutionId); // logged below for observability

    SalesforceExecutionEntity entity = SalesforceExecutionEntity.builder()
                                           .identifier(generateUuid())
                                           .name(request.getPipelineId())
                                           .accountIdentifier(account)
                                           .orgIdentifier(org)
                                           .projectIdentifier(project)
                                           .pipelineId(request.getPipelineId())
                                           .pipelineExecutionId(planExecutionId)
                                           .type(executionType)
                                           .status(SalesforceExecutionStatus.IN_PROGRESS)
                                           .metadata(metadata)
                                           .build();

    SalesforceExecutionEntity savedEntity = salesforceExecutionService.create(entity, scopeInfo);

    log.info(
        "Created SalesforceExecution entity [{}] with executionUrl [{}]", savedEntity.getIdentifier(), executionUrl);
    return SalesforceExecutionOpenApiMapper.toResponse(savedEntity, scopeInfo);
  }

  public SalesforceExecutionEntity resolveStatusIfInProgress(SalesforceExecutionEntity entity) {
    if (entity.getStatus() != SalesforceExecutionStatus.IN_PROGRESS) {
      return entity;
    }
    try {
      Object response =
          NGRestUtils.getResponse(pipelineServiceClient.getExecutionDetailV2(entity.getPipelineExecutionId(),
              entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()));
      SalesforceExecutionStatus resolved = extractStatusFromPipelineResponse(response);
      if (resolved != SalesforceExecutionStatus.IN_PROGRESS) {
        salesforceExecutionService.updateStatus(
            entity.getAccountIdentifier(), entity.getPipelineExecutionId(), resolved);
        entity.setStatus(resolved);
      }
    } catch (Exception e) {
      log.warn("Failed to resolve pipeline execution status for pipelineExecutionId [{}]: {}",
          entity.getPipelineExecutionId(), e.getMessage());
    }
    return entity;
  }

  private SalesforceExecutionStatus extractStatusFromPipelineResponse(Object response) {
    if (!(response instanceof Map)) {
      throw new IllegalStateException(
          "Unexpected pipeline response type: " + (response == null ? "null" : response.getClass().getName()));
    }
    Object summary = ((Map<?, ?>) response).get("pipelineExecutionSummary");
    if (!(summary instanceof Map)) {
      throw new IllegalStateException("Missing or unexpected pipelineExecutionSummary in pipeline response");
    }
    Object statusObj = ((Map<?, ?>) summary).get("status");
    if (!(statusObj instanceof String)) {
      throw new IllegalStateException("Missing or unexpected status field in pipelineExecutionSummary: " + statusObj);
    }
    ExecutionStatus pipelineStatus = ExecutionStatus.getExecutionStatus((String) statusObj);
    if (SUCCEEDED_PIPELINE_STATUSES.contains(pipelineStatus)) {
      return SalesforceExecutionStatus.SUCCEEDED;
    } else if (ABORTED_PIPELINE_STATUSES.contains(pipelineStatus)) {
      return SalesforceExecutionStatus.ABORTED;
    } else if (FAILED_PIPELINE_STATUSES.contains(pipelineStatus)) {
      return SalesforceExecutionStatus.FAILED;
    }
    return SalesforceExecutionStatus.IN_PROGRESS;
  }

  private String buildExecutionUrl(String account, String org, String project, String planExecutionId) {
    String baseUrl =
        nextGenConfiguration.getBaseUrls() != null ? nextGenConfiguration.getBaseUrls().getNextGenUiUrl() : null;
    if (baseUrl == null) {
      return null;
    }
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return String.format(EXECUTION_URL_FORMAT, baseUrl, account, org, project, planExecutionId);
  }
}
