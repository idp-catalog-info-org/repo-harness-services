/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;
import static io.harness.pms.utils.PmsConstants.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity.ExecutionExpressionUsagesEntityKeys;
import io.harness.engine.expressions.usages.beans.ExpressionCategory;
import io.harness.engine.expressions.usages.dto.ExecutionContextResponse;
import io.harness.engine.expressions.usages.dto.ExpressionUsagesDTO;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.engine.expressions.usages.service.ExpressionUsageService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.mapper.ExecutionExpressionUsagesMapper;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.PIPELINE)
@Api("/expression-usages")
@Path("/expression-usages")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
@ScopeInfoResolutionApi
public class ExpressionUsageResource {
  @Inject ExpressionUsageService expressionUsageService;
  @Inject ExecutionExpressionUsageService executionExpressionUsageService;
  @Inject private final AccessControlClient accessControlClient;
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  @GET
  @ApiOperation(value = "Fetch the expression usages for an account", nickname = "fetchExpressionUsages")
  @Operation(operationId = "fetchExpressionUsages", summary = "Fetch the expression usages for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the expression usages for an account")
      })
  @Hidden
  public ResponseDTO<ExpressionUsagesDTO>
  getExecutionInputTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @AccountIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @AccountIdentifier String projectId,
      @QueryParam("category") @Parameter(description = "Expression category") ExpressionCategory category,
      @Context ScopeInfo scopeInfo) {
    ExpressionUsagesDTO dto =
        expressionUsageService.fetchExpressionUsages(accountId, orgId, projectId, category, scopeInfo);
    return ResponseDTO.newResponse(dto);
  }

  @GET
  @Path("/execution-context")
  @ApiOperation(value = "Fetch the resolved expressions for given planExecutionId and nodeExecutionId",
      nickname = "fetchResolvedExpressions")
  @Operation(operationId = "fetchResolvedExpressions",
      summary = "Fetches the resolved expressions for given planExecutionId and nodeExecutionId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch the resolved expressions for given planExecutionId and nodeExecutionId")
      })
  @Hidden
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ExecutionContextResponse>
  getExecutionContext(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectId,
      @NotNull @QueryParam("planExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE) String planExecutionId,
      @NotNull @QueryParam("nodeExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE) String nodeExecutionId) {
    String pipelineIdentifier =
        pmsExecutionSummaryService
            .getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
                Set.of(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier))
            .getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_VIEW);
    List<ExecutionExpressionUsagesEntity> resolvedExpressionsResponse =
        executionExpressionUsageService.getExpressionsWithProjection(planExecutionId, nodeExecutionId,
            Sets.newHashSet(ExecutionExpressionUsagesEntityKeys.expression,
                ExecutionExpressionUsagesEntityKeys.expressionValue, ExecutionExpressionUsagesEntityKeys.isError));
    ExecutionContextResponse executionContextResponse =
        ExecutionContextResponse.builder()
            .resolvedExpressionDTOS(
                ExecutionExpressionUsagesMapper.toResolvedExpressionDTO(resolvedExpressionsResponse))
            .failedExpressions(ExecutionExpressionUsagesMapper.toFailedExpressions(resolvedExpressionsResponse))
            .build();
    return ResponseDTO.newResponse(executionContextResponse);
  }
}
