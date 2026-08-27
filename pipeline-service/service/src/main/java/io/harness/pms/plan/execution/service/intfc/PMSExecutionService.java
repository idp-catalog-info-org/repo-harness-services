/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.CustomPage;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineFilterDTO;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PMSExecutionService {
  InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions,
      ResolveInputYamlType resolveExpressionsType);

  String getInputSetYamlForRerun(String accountId, String planExecutionId, boolean pipelineDeleted);

  /**
   * @param accountId
   * @param orgId
   * @param projectId
   * @param pipelineIdentifier
   * @param filterIdentifier
   * @param filterProperties
   * @param pageable
   * @return
   */
  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(String accountId, String orgId,
      String projectId, List<String> pipelineIdentifier, String filterIdentifier,
      PipelineExecutionFilterPropertiesDTO filterProperties, Pageable pageable, ScopeInfo scopeInfo);

  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(
      Criteria criteria, Pageable pageable, String accountId, String sortProperty);

  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntityWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String planExecutionId, boolean pipelineDeleted);

  /**
   * This method is fetch execution summary from object store, if FF disabled
   * or object not yet stored in object store fetches it via MongoDB as a fallback
   */
  PipelineExecutionSummaryEntity fetchExecutionSummary(
      String accountId, String planExecutionId, boolean pipelineDeleted);

  /**
   * This method is fetch execution summary from object store, if FF disabled
   * or object not yet stored in object store fetches it via MongoDB as a fallback
   */
  PipelineExecutionSummaryEntity fetchExecutionSummary(String accountId, String planExecutionId);

  /**
   * This method retrieves the execution summary from MongoDB with projections.
   * Use it only to fetch the summary when the pipeline is in a running state.
   * @param planExecutionId
   * @return
   */
  PipelineExecutionSummaryEntity fetchExecutionSummaryFromDb(String planExecutionId, Set<String> projections);

  /**
   * This method is fetch execution summary from mongo with object store fallback in case mongo TTL is expired and data
   * retention FF is enabled
   */
  List<PipelineExecutionSummaryEntity> fetchExecutionSummaries(
      String accountIdentifier, List<String> planExecutionIds, List<String> projections);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(String accountId, String planExecutionId);

  void sendGraphUpdateEvent(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity);

  OrchestrationGraphDTO getOrchestrationGraph(
      String accountIdentifier, String stageNodeId, String planExecutionId, String stageNodeExecutionId);

  OrchestrationGraphDTO getOrchestrationGraphForAllStages(String accountIdentifier, String planExecutionId);

  SimplifiedOrchestrationGraphDTO getSimplifiedOrchestrationGraph(String accountIdentifier, String planExecutionId);

  InterruptDTO registerInterrupt(
      PlanExecutionInterruptType executionInterruptType, String planExecutionId, String nodeExecutionId);

  InterruptDTO registerInterrupt(PlanExecutionInterruptType executionInterruptType, String planExecutionId,
      String nodeExecutionId, InterruptConfig interruptConfig);

  Criteria formFilterCriteria(String accountId, String orgId, String projectId,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, Long lastSeenStartTime,
      String lastSeenExecutionId, Criteria criteria);

  CustomPage<PipelineExecutionOutlineDTO> getListOfExecutionsOutline(String accountId, String orgId, String projectId,
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO, String lastSeenExecutionId,
      Long lastSeenStartTime, int size);

  List<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntityWithProjectionWithoutPagination(
      Criteria criteria, Pageable pageable, List<String> projections, String hintIndex);

  Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      boolean isLatest, ScopeInfo scopeInfo);

  Page<PipelineExecutionSummaryEntity> listExecutionsFromElastic(
      String accountId, Pageable pageRequest, Query query, List<String> projections);

  Query formQueryForSearch(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, ScopeInfo scopeInfo);

  Query formQueryForSearchOROperator(String accountId, String orgId, String projectId, List<String> pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, ScopeInfo scopeInfo);

  // This is created only for internal purpose to support IDP plugin. It creates criteria using account ID, project ID,
  // pipeline IDs(As List to support multiple pipeline Identifiers) and filterProperties Operator(AND or OR) is
  // parameterized for modules in filterProperties.
  Criteria formCriteriaOROperatorOnModules(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String filterIdentifier);

  long getCountOfExecutions(Criteria criteria);

  ExecutionDataResponseDTO getExecutionData(String accountIdentifier, String planExecutionId);

  ExecutionMetaDataResponseDetailsDTO getExecutionDataDetails(String planExecutionId, String accountId);

  String mergeRuntimeInputIntoPipelineForRerun(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers, ScopeInfo scopeInfo);

  String mergeRuntimeInputIntoPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String planExecutionId, boolean resolveExpressions, ResolveInputYamlType resolveExpressionsType);

  String getPipelineIdentifier(String accountIdentifier, String planExecutionId);

  Page<GovernanceMetadata> getListOfEvaluatedPolicy(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String planExecutionId, int pageSize, int pageNumber);
}
