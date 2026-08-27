/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.allowedvalues.AllowedValuesUsagesDTO;
import io.harness.allowedvalues.AllowedValuesUsagesRequestDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.ForceImportPipelineResponse;
import io.harness.pms.pipeline.ForceImportPipelineYamlOperationDTO;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoListResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PMSPipelineService {
  /**
   * Create pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param pipelineEntity
   * @param throwExceptionIfGovernanceFails
   * @return
   */
  PipelineCRUDResult validateAndCreatePipeline(PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceFails);

  PipelineCRUDResult validateAndCreatePipeline(PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceFails,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  /**
   * Clone pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param clonePipelineDTO
   * @param accountId
   * @return
   */
  PipelineSaveResponse validateAndClonePipeline(
      ClonePipelineDTO clonePipelineDTO, String accountId, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  /**
   * Get pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param identifier
   * @param deleted
   * @return
   */
  PipelineGetResult getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, boolean validateAsync, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled,
      boolean shouldIgnoreOpaOnSaveCheck);

  AllowedValuesUsagesDTO checkForAllowedValues(String accountId, AllowedValuesUsagesRequestDTO request);

  String validatePipeline(String accountId, String orgIdentifier, String projectIdentifier, String identifier,
      boolean loadFromFallbackBranch, boolean loadFromCache, boolean validateAsync, PipelineEntity pipelineEntity,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Optional<PipelineEntity> getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean loadFromFallbackBranch, boolean loadFromCache, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, boolean shouldIgnoreOpaOnSaveCheck);

  Optional<PipelineEntity> getPipelineByUUID(String uuid);

  Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  void refreshGitFileCache(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String branch, ScopeInfo scopeInfo);

  PipelineEntity getPipelineMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  /**
   * Update pipeline (inline/remote) after doing validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param pipelineEntity
   * @param changeType
   * @param throwExceptionIfGovernanceFails
   * @return
   */
  PipelineCRUDResult validateAndUpdatePipeline(PipelineEntity pipelineEntity, ChangeType changeType,
      boolean throwExceptionIfGovernanceFails, boolean isPatch, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity syncPipelineEntityWithGit(EntityDetailProtoDTO entityDetail);

  PipelineEntity updatePipelineMetadata(
      ScopeInfo scopeInfo, Criteria criteria, Update updateOperations, boolean isParentIdQueryingEnabled);

  void saveExecutionInfo(ScopeInfo scopeInfo, String pipelineId, ExecutionSummaryInfo executionSummaryInfo,
      boolean isParentIdQueryingEnabled);

  boolean markEntityInvalid(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String invalidYaml);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      Long version, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Page<PipelineEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  PipelineEntity importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, Boolean isForceImport,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Long countAllPipelines(Criteria criteria);

  StepCategory getStepsV2(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper);

  StepCategory getStepsWithVersion(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper, String version);

  boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId, ScopeInfo scopeInfo);

  String fetchExpandedPipelineJSON(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PipelineEntity updateGitFilePath(PipelineEntity pipelineEntity, String newFilePath);

  String pipelineVersion(String accountId, String yaml);

  PMSPipelineListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  PMSPipelineRemoteRepoListResponse getRemoteRepoListForAGivenScope(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String repoName, ScopeInfo scopeInfo, int page, int limit);

  PipelineCRUDResult moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  /*
  given a list of pipelineIds, sends back the ids with view access
   */
  List<String> getPermittedToViewPipelineIdentifiers(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifierList);

  /**
  The getPermittedPipelineIdentifier performs view permission check on the pipelineIdentifiers list. It returns pipeline
  identifiers of which the user is having view permission.
   */
  List<String> getPermittedPipelineIdentifier(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifierList);

  List<String> listAllIdentifiers(Criteria criteria);

  boolean validateViewPermission(String accountId, String orgId, String projectId);

  List<YamlInputDetails> getInputSchemaDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  List<YamlValidationResponseDTO> validatePipelineYaml(
      String accountIdentifier, YamlValidationRequestDTO entityYamlValidationRequestDTO);

  ForceImportPipelineResponse forceImportPipeline(
      String accountIdentifier, ForceImportPipelineYamlOperationDTO requestDTO, boolean isParentIdQueryingEnabled);

  /**
   * Converts an existing sequential pipeline to DAG format.
   *
   * <h3>Purpose</h3>
   * This method provides a one-time migration utility for converting older pipelines from sequential
   * execution to dependency-based execution (DAG). This allows customers to leverage the enhanced
   * execution capabilities of DAG pipelines while maintaining their existing pipeline structure.
   *
   * <h3>What is DAG Execution?</h3>
   * DAG execution enables stages to run based on explicit dependencies
   * rather than sequential order in the YAML.
   *
   * <h3>Conversion Process</h3>
   * The conversion process performs the following transformations:
   * <ol>
   *   <li><b>Dependency Analysis:</b> Analyzes the current sequential stage order</li>
   *   <li><b>YAML Transformation:</b> Adds {@code dependsOn} fields to each stage</li>
   *   <li><b>Flattening:</b> Converts parallel blocks to individual stages with proper dependencies</li>
   *   <li><b>Entity Update:</b> Sets {@code enableDAG = true} in the PipelineEntity</li>
   * </ol>
   *
   * <h3>Prerequisites</h3>
   * <ul>
   *   <li>Feature flag {@code PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION} must be enabled</li>
   *   <li>Pipeline must not already be in DAG format ({@code enableDAG = false})</li>
   *   <li>User must have {@code PIPELINE_EDIT} permission</li>
   * </ul>
   *
   * @param accountIdentifier The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param pipelineIdentifier The pipeline identifier to convert
   * @param scopeInfo Scope information for the pipeline
   * @param isParentIdQueryingEnabled Whether parent ID querying is enabled
   * @return PMSPipelineResponseDTO containing the converted pipeline
   */
  PMSPipelineResponseDTO convertPipelineToDAG(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
}
