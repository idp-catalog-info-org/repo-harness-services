/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.publicaccess.PublicAccessClient;
import io.harness.allowedvalues.AllowedValuesUsagesDTO;
import io.harness.allowedvalues.AllowedValuesUsagesRequestDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionNode;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.InlineHCUpdateContextRequest;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.GitxRefreshMetrics;
import io.harness.gitaware.helper.PipelineMoveConfigRequestDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitx.CrudAction;
import io.harness.gitx.InlineHCHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.grpc.utils.FlowName;
import io.harness.grpc.utils.GrpcContextMetadataDto;
import io.harness.grpc.utils.GrpcContextMetadataHelper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.ExpandedPipelineJsonDTO;
import io.harness.pms.pipeline.ForceImportPipelineRequestDTO;
import io.harness.pms.pipeline.ForceImportPipelineResponse;
import io.harness.pms.pipeline.ForceImportPipelineYamlOperationDTO;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.MoveConfigResponse;
import io.harness.pms.pipeline.PMSGitUpdateResponseDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoInfo;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoListResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineResource;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.PipelineValidationResponseDTO;
import io.harness.pms.pipeline.RemotePipelinesDTO;
import io.harness.pms.pipeline.RemotePipelinesResponseDTO;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelinePublicAccessHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.intfc.PipelineGetResult;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.utils.PageUtils;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.YamlSchemaResource;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;
import io.harness.yaml.validator.beans.YamlSchemaValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlSchemaValidationResponseDTO;
import io.harness.yaml.validator.beans.YamlValidationAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationListAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO.YamlValidationRequestDTOBuilder;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
@ScopeInfoResolutionApi()
public class PipelineResourceImpl implements YamlSchemaResource, PipelineResource {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final SchemaFetcher schemaFetcher;
  private final YamlSchemaValidator yamlSchemaValidator;
  private final NodeExecutionService nodeExecutionService;
  private final NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final VariableCreatorMergeService variableCreatorMergeService;
  private final PipelineCloneHelper pipelineCloneHelper;
  private final PipelineMetadataService pipelineMetadataService;
  private final PipelineAsyncValidationService pipelineAsyncValidationService;
  private final AccessControlClient accessControlClient;
  private final PublicAccessClient publicAccessClient;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PipelinePublicAccessHelper pipelinePublicAccessHelper;
  private final PipelineOpaStatusHandler pipelineOpaStatusHandler;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final GitxRefreshMetrics gitxRefreshMetrics;

  private final String PIPELINE = "PIPELINE";
  private final String PIPELINE_SERVICE = "pipeline-service";
  private static final int MAX_PIPELINE_LIST_PAGE_SIZE = 200;
  private static final String INVALID_PIPELINE_LIST_PAGE_REQUEST_MESSAGE =
      "Please verify pipelines list parameters for page and size, page should be >= 0 and size should be > 0 and "
      + "<=200";

  @Deprecated
  public ResponseDTO<String> createPipeline(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, String pipelineIdentifier,
      String pipelineName, String pipelineDescription, Boolean isDraft, Boolean enableDAG,
      GitEntityCreateInfoDTO gitEntityCreateInfo, @NotNull String yaml, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId, null,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_CREATE_PIPELINE).callerName(PIPELINE_SERVICE).build());
    // Add log line for telemetry for usages
    log.info("Invoking deprecated createPipeline flow");
    String pipelineVersion = pmsPipelineService.pipelineVersion(accountId, yaml);
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId,
        pipelineIdentifier, pipelineName, yaml, isDraft, pipelineVersion, scopeInfo, isParentIdQueryingEnabled, null);

    pipelineEntity = pipelineEntity.withEnableDAG(enableDAG);

    log.info(String.format("Creating pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));

    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true, scopeInfo, isParentIdQueryingEnabled);
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(createdEntity.getVersion().toString(), createdEntity.getIdentifier());
  }

  /***
   * @param accountId
   * @param orgId
   * @param projectId
   * @param pipelineIdentifiers
   * @param page
   * @param size
   * @return a list of Maps with the same size as pipelineIdentifiers. So that it can be mapped to the
   *     pipeline-identifier using the index on the client size.
   */
  @Override
  public ResponseDTO<List<Map<String, String>>> getPipelineAttributes(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifiers, int page, int size, ScopeInfo scopeInfo) {
    if (EmptyPredicate.isEmpty(pipelineIdentifiers)) {
      return ResponseDTO.newResponse(Collections.emptyList());
    }
    // Log introduced to monitor large page size requests
    if (size > 200) {
      log.info("Large page size requested for pipelines attributes: {}. Account: {}, Org: {}, Project: {}", size,
          accountId, orgId, projectId);
    }
    if (pipelineIdentifiers.size() > size) {
      throw new InvalidRequestException(
          String.format("The pipeline identifiers count %s can not be more than the page size %s.",
              pipelineIdentifiers.size(), size));
    }
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    Criteria criteria = pipelineServiceHelper.formCriteria(accountId, orgId, projectId, null,
        PipelineFilterPropertiesDto.builder().pipelineIdentifiers(pipelineIdentifiers).build(), false, null, null,
        scopeInfo, isParentIdQueryingEnabled);
    Page<PipelineEntity> pipelineEntities = pmsPipelineService.list(criteria, Pageable.ofSize(size).withPage(page),
        accountId, orgId, projectId, false, scopeInfo, isParentIdQueryingEnabled);
    Map<String, List<PipelineEntity>> pipelineEntitiesMap =
        pipelineEntities.getContent().stream().collect(Collectors.groupingBy(PipelineEntity::getIdentifier));
    List<Map<String, String>> attributes = new ArrayList<>();
    for (String pipelineId : pipelineIdentifiers) {
      if (pipelineEntitiesMap.containsKey(pipelineId)
          && EmptyPredicate.isNotEmpty(pipelineEntitiesMap.get(pipelineId))) {
        attributes.add(Map.of(PipelineEntityKeys.tags,
            pipelineEntitiesMap.get(pipelineId)
                .get(0)
                .getTags()
                .stream()
                .map(tag -> tag.getKey() + (EmptyPredicate.isNotEmpty(tag.getValue()) ? (":" + tag.getValue()) : ""))
                .collect(Collectors.joining(","))));
      } else {
        attributes.add(Collections.emptyMap());
      }
    }
    return ResponseDTO.newResponse(attributes);
  }

  @Override
  public ResponseDTO<AllowedValuesUsagesDTO> getEntitiesWithAllowedValues(
      String accountId, AllowedValuesUsagesRequestDTO request) {
    return ResponseDTO.newResponse(pmsPipelineService.checkForAllowedValues(accountId, request));
  }

  public ResponseDTO<PipelineSaveResponse> createPipelineV2(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, String pipelineIdentifier,
      String pipelineName, String pipelineDescription, Boolean isDraft, GitEntityCreateInfoDTO gitEntityCreateInfo,
      Boolean allowDynamicExecutions, Boolean enableDAG, @NotNull String yaml, boolean isPublic, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId, null,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_CREATE_PIPELINE).callerName(PIPELINE_SERVICE).build());
    String pipelineVersion = pmsPipelineService.pipelineVersion(accountId, yaml);
    boolean isParentIdQueryingEnabled = true;
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, pipelineIdentifier, pipelineName, yaml,
            isDraft, pipelineVersion, scopeInfo, isParentIdQueryingEnabled, allowDynamicExecutions);

    pipelineEntity = pipelineEntity.withEnableDAG(enableDAG);
    InlineHCHelper.checkAndUpdateContextForInlineHC(pipelineEntity, CrudAction.CREATE,
        InlineHCUpdateContextRequest.builder()
            .scope(Scope.of(accountId, orgId, projectId))
            .entityIdentifier(pipelineEntity.getIdentifier())
            .build(),
        pmsFeatureFlagHelper::isEnabled);

    log.info(String.format("Creating pipeline with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndCreatePipeline(pipelineEntity,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE), scopeInfo,
        isParentIdQueryingEnabled);
    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      return ResponseDTO.newResponse(PipelineSaveResponse.builder().governanceMetadata(governanceMetadata).build());
    }
    PipelineEntity createdEntity = pipelineCRUDResult.getPipelineEntity();

    return ResponseDTO.newResponse(createdEntity.getVersion().toString(),
        PipelineSaveResponse.builder()
            .governanceMetadata(governanceMetadata)
            .identifier(createdEntity.getIdentifier())
            .publicAccessResponse(pipelinePublicAccessHelper.markPipelinePublic(
                accountId, orgId, projectId, pipelineEntity.getIdentifier(), isPublic))
            .build());
  }

  @Hidden
  public ResponseDTO<PipelineSaveResponse> clonePipeline(@NotNull @AccountIdentifier String accountId,
      GitEntityCreateInfoDTO gitEntityCreateInfo, Boolean enableDAG, @NotNull ClonePipelineDTO clonePipelineDTO) {
    // Add log line for telemetry for usages
    log.info("Invoking deprecated clonePipeline flow");
    pipelineCloneHelper.checkAccess(clonePipelineDTO, accountId);
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_CLONE_PIPELINE).callerName(PIPELINE_SERVICE).build());

    clonePipelineDTO.setEnableDAG(enableDAG);

    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
            clonePipelineDTO.getSourceConfig().getProjectIdentifier());
    PipelineSaveResponse pipelineSaveResponse =
        pmsPipelineService.validateAndClonePipeline(clonePipelineDTO, accountId, scopeInfo, isParentIdQueryingEnabled);

    return ResponseDTO.newResponse(pipelineSaveResponse);
  }

  @Hidden
  public ResponseDTO<PipelineSaveResponse> clonePipelineV2(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      GitEntityCreateInfoDTO gitEntityCreateInfo, Boolean enableDAG, @NotNull ClonePipelineDTO clonePipelineDTO,
      ScopeInfo scopeInfo) {
    pipelineCloneHelper.checkAccess(clonePipelineDTO, accountId);
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_CLONE_PIPELINE).callerName(PIPELINE_SERVICE).build());

    // Set enableDAG in the clone DTO
    clonePipelineDTO.setEnableDAG(enableDAG);

    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    PipelineSaveResponse pipelineSaveResponse =
        pmsPipelineService.validateAndClonePipeline(clonePipelineDTO, accountId, scopeInfo, isParentIdQueryingEnabled);

    return ResponseDTO.newResponse(pipelineSaveResponse);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse> createVariables(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, @NotNull @ApiParam(hidden = true) String yaml,
      ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml, scopeInfo, isParentIdQueryingEnabled);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, loadFromCache)
            .getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse =
        variableCreatorMergeService.createVariablesResponses(resolveTemplateRefsInPipeline, false, accountId);

    return ResponseDTO.newResponse(variablesResponse);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse> createVariablesV2(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
          required = true) @NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      String loadFromCache, @NotNull @ApiParam(hidden = true) String yaml, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml, scopeInfo, isParentIdQueryingEnabled);
    // Apply all the templateRefs(if any) then check for variables.
    String resolveTemplateRefsInPipeline =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, loadFromCache)
            .getMergedPipelineYaml();
    VariableMergeServiceResponse variablesResponse = variableCreatorMergeService.createVariablesResponsesV2(
        accountId, orgId, projectId, resolveTemplateRefsInPipeline);
    return ResponseDTO.newResponse(variablesResponse);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineResponseDTO> getPipelineByIdentifier(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean getTemplatesResolvedPipeline, boolean loadFromFallbackBranch, boolean validateAsync, String loadFromCache,
      ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_GET_PIPELINE).callerName(PIPELINE_SERVICE).build());
    log.info(String.format("Retrieving pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);

    Optional<PipelineEntity> pipelineEntity;
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID;
    try {
      boolean disableOpaOnSaveBlocking =
          pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DISABLE_OPA_ON_SAVE_BLOCKING_FOR_PIPELINE_RUN);
      PipelineGetResult pipelineGetResult =
          pmsPipelineService.getAndValidatePipeline(accountId, orgId, projectId, pipelineId, false, false,
              Boolean.TRUE.equals(loadFromFallbackBranch), GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache),
              validateAsync, scopeInfo, isParentIdQueryingEnabled, !disableOpaOnSaveBlocking);
      pipelineEntity = pipelineGetResult.getPipelineEntity();
      validationUUID = pipelineGetResult.getAsyncValidationUUID();
    } catch (PolicyEvaluationFailureException pe) {
      return ResponseDTO.newResponse(
          PMSPipelineResponseDTO.builder()
              .yamlPipeline(pe.getYaml())
              .governanceMetadata(pe.getGovernanceMetadata())
              .entityValidityDetails(EntityValidityDetails.builder().valid(true).invalidYaml(pe.getYaml()).build())
              .gitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata())
              .cacheResponse(PMSPipelineDtoMapper.getCacheResponseFromGitContext())
              .build());
    } catch (InvalidYamlException e) {
      return ResponseDTO.newResponse(
          PMSPipelineResponseDTO.builder()
              .yamlPipeline(e.getYaml())
              .entityValidityDetails(EntityValidityDetails.builder().valid(false).invalidYaml(e.getYaml()).build())
              .gitDetails(GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata())
              .yamlSchemaErrorWrapper((YamlSchemaErrorWrapperDTO) e.getMetadata())
              .cacheResponse(PMSPipelineDtoMapper.getCacheResponseFromGitContext())
              .build());
    }

    String version = "0";
    if (pipelineEntity.isPresent()) {
      version = pipelineEntity.get().getVersion().toString();
    }

    PMSPipelineResponseDTO pipeline = PMSPipelineDtoMapper.writePipelineDto(pipelineEntity.orElseThrow(
        ()
            -> new EntityNotFoundException(
                String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId))));

    pipeline.setPublicAccessResponse(
        pipelinePublicAccessHelper.getPublicContextResponse(accountId, orgId, projectId, pipelineId));
    if (pipelineEntity.isPresent() && null != pipelineEntity.get().getConnectorRef()) {
      pipeline.setConnectorRef(pipelineEntity.get().getConnectorRef());
    }

    if (getTemplatesResolvedPipeline) {
      try {
        String templateResolvedPipelineYaml = "";
        TemplateMergeResponseDTO templateMergeResponseDTO =
            pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get(), scopeInfo, loadFromCache);

        templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipeline.setResolvedTemplatesPipelineYaml(templateResolvedPipelineYaml);
      } catch (Exception e) {
        log.info("Cannot get resolved templates pipeline YAML");
      }
    }
    if (validateAsync) {
      pipeline.setValidationUuid(validationUUID);
    }
    return ResponseDTO.newResponse(version, pipeline);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineResponseDTO> refreshAndGetPipeline(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, @NotBlank String branch, ScopeInfo scopeInfo) {
    return gitxRefreshMetrics.executeWithMetrics(accountId, () -> {
      GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                                .flowName(FlowName.PIPE_REFRESH_AND_GET_PIPELINE)
                                                .callerName(PIPELINE_SERVICE)
                                                .build());
      pmsPipelineService.refreshGitFileCache(accountId, orgId, projectId, pipelineId, branch, scopeInfo);
      return getPipelineByIdentifier(
          accountId, orgId, projectId, pipelineId, null, false, false, true, BOOLEAN_FALSE_VALUE, scopeInfo);
    });
  }

  @Deprecated
  public ResponseDTO<String> updatePipeline(String ifMatch, @NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, String pipelineName, String pipelineDescription, Boolean isDraft,
      GitEntityUpdateInfoDTO gitEntityInfo, @NotNull String yaml, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", pipelineId), PipelineRbacPermissions.PIPELINE_EDIT);
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_UPDATE_PIPELINE).callerName(PIPELINE_SERVICE).build());
    String pipelineVersion = pmsPipelineService.pipelineVersion(accountId, yaml);
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion = PMSPipelineDtoMapper.toPipelineEntityWithVersion(accountId, orgId, projectId,
        pipelineId, pipelineName, yaml, ifMatch, isDraft, pipelineVersion, scopeInfo, isParentIdQueryingEnabled, null);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndUpdatePipeline(
        withVersion, ChangeType.MODIFY, true, false, scopeInfo, isParentIdQueryingEnabled);
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(), updatedEntity.getIdentifier());
  }

  public ResponseDTO<PipelineSaveResponse> updatePipelineV2(String ifMatch,
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId, String pipelineName,
      String pipelineDescription, Boolean isDraft, GitEntityUpdateInfoDTO gitEntityInfo, Boolean allowDynamicExecutions,
      @NotNull String yaml, boolean isPublic, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", pipelineId), PipelineRbacPermissions.PIPELINE_EDIT);
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_UPDATE_PIPELINE).callerName(PIPELINE_SERVICE).build());
    String pipelineVersion = pmsPipelineService.pipelineVersion(accountId, yaml);
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    log.info(String.format("Updating pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    PipelineEntity withVersion =
        PMSPipelineDtoMapper.toPipelineEntityWithVersion(accountId, orgId, projectId, pipelineId, pipelineName, yaml,
            ifMatch, isDraft, pipelineVersion, scopeInfo, isParentIdQueryingEnabled, allowDynamicExecutions);
    PipelineCRUDResult pipelineCRUDResult = pmsPipelineService.validateAndUpdatePipeline(withVersion, ChangeType.MODIFY,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE), false,
        scopeInfo, isParentIdQueryingEnabled);

    GovernanceMetadata governanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (governanceMetadata.getDeny()) {
      return ResponseDTO.newResponse(PipelineSaveResponse.builder().governanceMetadata(governanceMetadata).build());
    }
    PipelineEntity updatedEntity = pipelineCRUDResult.getPipelineEntity();

    return ResponseDTO.newResponse(updatedEntity.getVersion().toString(),
        PipelineSaveResponse.builder()
            .identifier(updatedEntity.getIdentifier())
            .governanceMetadata(governanceMetadata)
            .publicAccessResponse(
                pipelinePublicAccessHelper.markPipelinePublic(accountId, orgId, projectId, pipelineId, isPublic))
            .build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  public ResponseDTO<Boolean> deletePipeline(String ifMatch, @NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityDeleteInfoDTO entityDeleteInfo, ScopeInfo scopeInfo) {
    log.info(String.format("Deleting pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);

    return ResponseDTO.newResponse(pmsPipelineService.delete(accountId, orgId, projectId, pipelineId,
        isNumeric(ifMatch) ? parseLong(ifMatch) : null, scopeInfo, isParentIdQueryingEnabled));
  }

  public ResponseDTO<Page<PMSPipelineSummaryResponseDTO>> getListOfPipelines(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, int page, int size, List<String> sort, String searchTerm,
      String module, String filterIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      PipelineFilterPropertiesDto filterProperties, Boolean getDistinctFromBranches, ScopeInfo scopeInfo) {
    log.info(String.format("Get List of pipelines in project %s, org %s, account %s", projectId, orgId, accountId));

    if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_DISABLE_PIPELINE_LIST_PAGE_SIZE_LIMIT)) {
      if (page < 0 || !(size > 0 && size <= MAX_PIPELINE_LIST_PAGE_SIZE)) {
        throw new InvalidRequestException(INVALID_PIPELINE_LIST_PAGE_REQUEST_MESSAGE);
      }
    }
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);

    Criteria criteria;
    try {
      criteria = pipelineServiceHelper.formCriteria(accountId, orgId, projectId, filterIdentifier, filterProperties,
          false, module, searchTerm, scopeInfo, isParentIdQueryingEnabled);
    } catch (PatternSyntaxException exception) {
      return ResponseDTO.newResponse(Page.empty());
    }

    Pageable pageRequest =
        PageUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));

    // We need to fetch only those pipeline of which the user have view permission
    pipelineServiceHelper.setPermittedPipelines(accountId, orgId, projectId, criteria, PipelineEntityKeys.identifier);

    Page<PipelineEntity> pipelineEntities = pmsPipelineService.list(criteria, pageRequest, accountId, orgId, projectId,
        getDistinctFromBranches, scopeInfo, isParentIdQueryingEnabled);

    List<String> pipelineIdentifiers =
        pipelineEntities.stream().map(PipelineEntity::getIdentifier).collect(Collectors.toList());

    Map<String, PipelineMetadataV2> pipelineMetadataMap = pipelineMetadataService.getMetadataForGivenPipelineIds(
        accountId, orgId, projectId, pipelineIdentifiers, scopeInfo, isParentIdQueryingEnabled);

    Page<PMSPipelineSummaryResponseDTO> pipelines =
        pipelineEntities.map(e -> PMSPipelineDtoMapper.preparePipelineSummaryForListView(e, pipelineMetadataMap));

    return ResponseDTO.newResponse(pipelines);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineSummaryResponseDTO> getPipelineSummary(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo, Boolean getMetadataOnly,
      boolean loadFromFallbackBranch, String loadFromCache, ScopeInfo scopeInfo) {
    log.info(
        String.format("Get pipeline summary for pipeline with with identifier %s in project %s, org %s, account %s",
            pipelineId, projectId, orgId, accountId));

    Optional<PipelineEntity> pipelineEntity;
    pipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId, false,
        Boolean.TRUE.equals(getMetadataOnly), loadFromFallbackBranch,
        GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo,
        pipelineServiceHelper.isParentIdQueryingEnabled(accountId));

    PMSPipelineSummaryResponseDTO pipelineSummary = PMSPipelineDtoMapper.preparePipelineSummary(
        pipelineEntity.orElseThrow(
            ()
                -> new EntityNotFoundException(
                    String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId))),
        getMetadataOnly);

    return ResponseDTO.newResponse(pipelineSummary);
  }

  public ResponseDTO<PipelineSaveResponse> importPipelineFromGit(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitImportInfoDTO gitImportInfoDTO,
      PipelineImportRequestDTO pipelineImportRequestDTO, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId, pipelineId,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);

    PipelineEntity savedPipelineEntity =
        pmsPipelineService.importPipelineFromRemote(accountId, orgId, projectId, pipelineId, pipelineImportRequestDTO,
            gitImportInfoDTO.getIsForceImport(), scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(
        PipelineSaveResponse.builder().identifier(savedPipelineEntity.getIdentifier()).build());
  }

  public ResponseDTO<PipelineSaveResponse> importPipelineFromGit(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @Valid GitImportInfoDTO gitImportInfoDTO, PipelineImportRequestDTO pipelineImportRequestDTO,
      ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId, null,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(
        GrpcContextMetadataDto.builder().flowName(FlowName.PIPE_IMPORT_PIPELINE).callerName(PIPELINE_SERVICE).build());
    PipelineEntity savedPipelineEntity = pmsPipelineService.importPipelineFromRemote(accountId, orgId, projectId, "",
        pipelineImportRequestDTO, gitImportInfoDTO.getIsForceImport(), scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(
        PipelineSaveResponse.builder().identifier(savedPipelineEntity.getIdentifier()).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<ExpandedPipelineJsonDTO> getExpandedPipelineJson(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, GitEntityFindInfoDTO gitEntityBasicInfo, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    String expandedPipelineJSON = pmsPipelineService.fetchExpandedPipelineJSON(
        accountId, orgId, projectId, pipelineId, scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(ExpandedPipelineJsonDTO.builder().expandedJson(expandedPipelineJSON).build());
  }

  public ResponseDTO<StepCategory> getStepsV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                                                  required = true) @NotNull String accountId,
      @NotNull StepPalleteFilterWrapper stepPalleteFilterWrapper) {
    return ResponseDTO.newResponse(pmsPipelineService.getStepsV2(accountId, stepPalleteFilterWrapper));
  }

  @Hidden
  public ResponseDTO<NotificationRules> getNotificationSchema() {
    return ResponseDTO.newResponse(NotificationRules.builder().build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  public ResponseDTO<ExecutionNode> getExecutionNode(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @NotNull String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    return ResponseDTO.newResponse(
        nodeExecutionToExecutioNodeMapper.mapNodeExecutionToExecutionNode(nodeExecutionService.get(nodeExecutionId)));
  }

  @Hidden
  public ResponseDTO<PmsAbstractStepNode> getPmsStepNodes() {
    return ResponseDTO.newResponse(new PmsAbstractStepNode() {
      @Override
      public String getType() {
        return null;
      }

      @Override
      public StepSpecType getStepSpecType() {
        return null;
      }
    });
  }

  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStepNode> getTemplateStepNode() {
    return ResponseDTO.newResponse(new TemplateStepNode());
  }

  @Hidden
  // do not delete this.
  public ResponseDTO<TemplateStageNode> getTemplateStageNode() {
    return ResponseDTO.newResponse(new TemplateStageNode());
  }

  public ResponseDTO<String> validatePipelineByYAML(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId, @NotNull String yaml,
      ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    PipelineEntity pipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, yaml, scopeInfo, isParentIdQueryingEnabled);
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId,
        pipelineEntity.getIdentifier(),
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                              .flowName(FlowName.PIPE_VALIDATE_PIPELINE)
                                              .callerName(PIPELINE_SERVICE)
                                              .build());

    log.info(String.format("Validating the pipeline YAML with identifier %s in project %s, org %s, account %s",
        pipelineEntity.getIdentifier(), projectId, orgId, accountId));
    pipelineServiceHelper.resolveTemplatesAndValidatePipeline(
        pipelineEntity, true, false, scopeInfo, isParentIdQueryingEnabled, false);
    return ResponseDTO.newResponse(pipelineEntity.getIdentifier());
  }

  public ResponseDTO<String> validatePipelineByIdentifier(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgId, @NotNull @ProjectIdentifier String projectId,
      @ResourceIdentifier String pipelineId, ScopeInfo scopeInfo) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountId, orgId, projectId, pipelineId,
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                              .flowName(FlowName.PIPE_VALIDATE_PIPELINE)
                                              .callerName(PIPELINE_SERVICE)
                                              .build());
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    log.info(String.format("Validating the pipeline with identifier %s in project %s, org %s, account %s", pipelineId,
        projectId, orgId, accountId));
    Optional<PipelineEntity> entityOptional = pmsPipelineService.getAndValidatePipeline(
        accountId, orgId, projectId, pipelineId, false, false, false, scopeInfo, isParentIdQueryingEnabled, false);
    if (entityOptional.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId));
    }
    return ResponseDTO.newResponse(pipelineId);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<TemplatesResolvedPipelineResponseDTO> getTemplateResolvedPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, ScopeInfo scopeInfo) {
    GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                              .flowName(FlowName.PIPE_GET_TEMPLATE_RESOLVED_PIPELINE)
                                              .callerName(PIPELINE_SERVICE)
                                              .build());
    log.info(
        String.format("Retrieving templates resolved pipeline with identifier %s in project %s, org %s, account %s",
            pipelineId, projectId, orgId, accountId));
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    boolean loadFromCacheBool = GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache);

    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId,
        false, false, false, loadFromCacheBool, scopeInfo, isParentIdQueryingEnabled);

    if (pipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineId));
    }

    // This is required so the remote template references are fetched from correct repo and branch and not from
    // `default` branch.
    PipelineGitXHelper.setupGitParentEntityDetails(
        accountId, orgId, projectId, pipelineEntity.get().getConnectorRef(), pipelineEntity.get().getRepo());

    String pipelineYaml = pipelineEntity.get().getYaml();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.get(), scopeInfo, loadFromCache);
    String templateResolvedPipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
    TemplatesResolvedPipelineResponseDTO templatesResolvedPipelineResponseDTO =
        TemplatesResolvedPipelineResponseDTO.builder()
            .resolvedTemplatesPipelineYaml(templateResolvedPipelineYaml)
            .yamlPipeline(pipelineYaml)
            .build();
    return ResponseDTO.newResponse(templatesResolvedPipelineResponseDTO);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PMSPipelineListRepoResponse> getListRepos(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountIdentifier);
    return ResponseDTO.newResponse(pmsPipelineService.getListOfRepos(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, isParentIdQueryingEnabled));
  }

  @Override
  public ResponseDTO<RemotePipelinesResponseDTO> getRemotePipelineMetadata(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String repoName, int page, int size, ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_PIPELINE_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      PMSPipelineRemoteRepoListResponse serviceResponse = pmsPipelineService.getRemoteRepoListForAGivenScope(
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<PMSPipelineRemoteRepoInfo> serviceRepos =
          serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();
      List<RemotePipelinesDTO> resourceRepos = serviceRepos.stream()
                                                   .map(info
                                                       -> RemotePipelinesDTO.builder()
                                                              .repoName(info.getRepoName())
                                                              .repoURL(info.getRepoURL())
                                                              .count(info.getCount())
                                                              .filePathsByOwningScope(info.getFilePathsByOwningScope())
                                                              .connectorRefs(info.getConnectorRefs())
                                                              .build())
                                                   .collect(Collectors.toList());
      long totalPipelines = serviceRepos.stream().mapToLong(PMSPipelineRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_PIPELINE_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalPipelines={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceResponse.getTotalRepos(),
          resourceRepos.size(), totalPipelines, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemotePipelinesResponseDTO.builder()
                                         .totalPipelines(totalPipelines)
                                         .totalRepos(serviceResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error("[REMOTE_PIPELINE_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  public ResponseDTO<MoveConfigResponse> moveConfig(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String pipelineIdentifier, PipelineMoveConfigRequestDTO pipelineMoveConfigRequestDTO,
      ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EDIT);
    if (!pipelineIdentifier.equals(pipelineMoveConfigRequestDTO.getPipelineIdentifier())) {
      throw new InvalidRequestException("Identifiers given in path param and request body don't match.");
    }
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountIdentifier);
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.moveConfig(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
            MoveConfigOperationDTO.builder()
                .repoName(pipelineMoveConfigRequestDTO.getRepoName())
                .branch(pipelineMoveConfigRequestDTO.getBranch())
                .moveConfigOperationType(io.harness.gitaware.helper.MoveConfigOperationType.getMoveConfigType(
                    pipelineMoveConfigRequestDTO.getMoveConfigOperationType()))
                .connectorRef(pipelineMoveConfigRequestDTO.getConnectorRef())
                .baseBranch(pipelineMoveConfigRequestDTO.getBaseBranch())
                .commitMessage(pipelineMoveConfigRequestDTO.getCommitMsg())
                .isNewBranch(pipelineMoveConfigRequestDTO.getIsNewBranch())
                .filePath(pipelineMoveConfigRequestDTO.getFilePath())
                .build(),
            scopeInfo, isParentIdQueryingEnabled);
    PipelineEntity pipelineEntity = pipelineCRUDResult.getPipelineEntity();
    return ResponseDTO.newResponse(
        MoveConfigResponse.builder().pipelineIdentifier(pipelineEntity.getIdentifier()).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PipelineValidationUUIDResponseBody> startPipelineValidationEvent(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, @ResourceIdentifier String pipelineId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCacheHeader, ScopeInfo scopeInfo) {
    boolean loadFromCache = GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCacheHeader);
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountId);
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId,
        false, false, false, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted.", pipelineId));
    }
    PipelineValidationEvent pipelineValidationEvent = pipelineAsyncValidationService.startEvent(pipelineEntity.get(),
        gitEntityBasicInfo.getBranch(), Action.CRUD, loadFromCache, scopeInfo, isParentIdQueryingEnabled);
    PipelineValidationUUIDResponseBody pipelineValidationUUIDResponseBody =
        PipelinesApiUtils.buildPipelineValidationUUIDResponseBody(pipelineValidationEvent);
    return ResponseDTO.newResponse(pipelineValidationUUIDResponseBody);
  }
  public ResponseDTO<PipelineValidationResponseDTO> getPipelineValidateResult(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgId,
      @NotNull @ProjectIdentifier String projectId, String uuid) {
    Optional<PipelineValidationEvent> eventByUuid = pipelineAsyncValidationService.getEventByUuid(uuid);
    PipelineValidationEvent pipelineValidationEvent = eventByUuid.get();
    if (null != pipelineValidationEvent.getParams() && null != pipelineValidationEvent.getParams().getPipelineEntity()
        && null != pipelineValidationEvent.getParams().getPipelineEntity().getIdentifier()) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of(PIPELINE, pipelineValidationEvent.getParams().getPipelineEntity().getIdentifier()),
          PipelineRbacPermissions.PIPELINE_VIEW);
    } else {
      throw new InvalidRequestException(
          String.format("PipelineIdentifier could not be found, Please perform async validation again"));
    }
    PipelineValidationResponseDTO response =
        PMSPipelineDtoMapper.buildPipelineValidationResponseDTO(pipelineValidationEvent);

    try {
      PipelineEntity entity = pipelineValidationEvent.getParams().getPipelineEntity();
      PipelinesApiUtils
          .resolveValidateOpaEnrichment(entity, accountId, pipelineValidationEvent.getResult().getGovernanceMetadata(),
              pipelineOpaStatusHandler, pipelineValidationEvent.getParams().getCommitId(),
              pipelineValidationEvent.getResult().getOpaEvaluatedAt(),
              pipelineValidationEvent.getResult().getOpaLastValidCommitId())
          .ifPresent(enrichment -> {
            response.setOpaOnSaveStatus(PipelinesApiUtils.toOpaOnSaveStatusResponse(
                enrichment.getOpaStatus(), enrichment.getCurrentCommitId()));
          });
    } catch (Exception e) {
      log.warn("Failed to enrich validate result with OPA onSave status, continuing without it", e);
    }

    return ResponseDTO.newResponse(response);
  }

  public ResponseDTO<PMSGitUpdateResponseDTO> updateGitMetadataForPipeline(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @ResourceIdentifier String pipelineIdentifier,
      GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PIPELINE, pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EDIT);
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountIdentifier);
    String pipelineAfterUpdate =
        pmsPipelineService.updateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
            PMSUpdateGitDetailsParams.builder()
                .connectorRef(gitMetadataUpdateRequestInfo.getConnectorRef())
                .filePath(gitMetadataUpdateRequestInfo.getFilePath())
                .repoName(gitMetadataUpdateRequestInfo.getRepoName())
                .build(),
            scopeInfo, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(PMSGitUpdateResponseDTO.builder().identifier(pipelineAfterUpdate).build());
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<YamlValidationListAPIResponse> validatePipelineYaml(
      @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    YamlValidationRequestDTOBuilder yamlValidationRequestDTOBuilder =
        YamlValidationRequestDTO.builder().yaml(yamlValidationRequestBody.getYaml());
    if (yamlValidationRequestBody.getGitYamlValidationRequestParams() != null) {
      yamlValidationRequestDTOBuilder.branch(yamlValidationRequestBody.getGitYamlValidationRequestParams().getBranch());
      yamlValidationRequestDTOBuilder.filePath(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getFilePath());
      yamlValidationRequestDTOBuilder.repoName(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getRepoName());
      yamlValidationRequestDTOBuilder.isDefaultBranch(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getIsDefaultBranch());
      yamlValidationRequestDTOBuilder.commitId(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getCommitId());
    }
    YamlValidationRequestDTO yamlValidationRequestDTO = yamlValidationRequestDTOBuilder.build();
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        pmsPipelineService.validatePipelineYaml(accountIdentifier, yamlValidationRequestDTO);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
  }

  @Override
  public ResponseDTO<YamlSchemaValidationResponseDTO> validatePipelineYamlSchema(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, YamlSchemaValidationRequestDTO requestDTO) {
    String yaml = requestDTO.getYaml();
    String version = requestDTO.getVersion();
    String schemaVersion = HarnessYamlVersion.isV1(version) ? "v1" : "v0";

    try {
      JsonNode schema = schemaFetcher.fetchPipelineStaticYamlSchema(schemaVersion);
      JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
      yamlSchemaValidator.validate(jsonNode, schema);
      return ResponseDTO.newResponse(YamlSchemaValidationResponseDTO.builder().valid(true).build());
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.warn("[PIPELINE_SCHEMA_VALIDATION_API] {} {} {} Schema validation failed: {}", accountIdentifier,
          orgIdentifier, projectIdentifier, e.getMessage());
      YamlSchemaErrorWrapperDTO schemaErrors =
          e.getMetadata() instanceof YamlSchemaErrorWrapperDTO ? (YamlSchemaErrorWrapperDTO) e.getMetadata() : null;
      return ResponseDTO.newResponse(YamlSchemaValidationResponseDTO.builder()
                                         .valid(false)
                                         .errorMessage(e.getMessage())
                                         .schemaErrors(schemaErrors)
                                         .build());
    } catch (Exception e) {
      log.error("[PIPELINE_SCHEMA_VALIDATION_API] {} {} {} Unexpected error: {}", accountIdentifier, orgIdentifier,
          projectIdentifier, e.getMessage(), e);
      return ResponseDTO.newResponse(
          YamlSchemaValidationResponseDTO.builder().valid(false).errorMessage(e.getMessage()).build());
    }
  }

  @Override
  public ResponseDTO<ForceImportPipelineResponse> forceImportPipeline(
      String accountIdentifier, ForceImportPipelineRequestDTO requestDTO) {
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier,
        requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), null,
        pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
    GrpcContextMetadataHelper.addMetadata(GrpcContextMetadataDto.builder()
                                              .flowName(FlowName.PIPE_FORCE_IMPORT_PIPELINE)
                                              .callerName(PIPELINE_SERVICE)
                                              .build());
    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountIdentifier);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier());

    ForceImportPipelineYamlOperationDTO operationDTO = ForceImportPipelineYamlOperationDTO.builder()
                                                           .branch(requestDTO.getBranch())
                                                           .repoName(requestDTO.getRepoName())
                                                           .connectorRef(requestDTO.getConnectorRef())
                                                           .filePath(requestDTO.getFilePath())
                                                           .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
                                                           .identifier(requestDTO.getIdentifier())
                                                           .orgIdentifier(requestDTO.getOrgIdentifier())
                                                           .projectIdentifier(requestDTO.getProjectIdentifier())
                                                           .scopeInfo(scopeInfo)
                                                           .build();

    ForceImportPipelineResponse response =
        pmsPipelineService.forceImportPipeline(accountIdentifier, operationDTO, isParentIdQueryingEnabled);
    return ResponseDTO.newResponse(response);
  }

  @Override
  public ResponseDTO<PMSPipelineResponseDTO> convertPipelineToDAG(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PIPELINE, pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EDIT);

    boolean isParentIdQueryingEnabled = pipelineServiceHelper.isParentIdQueryingEnabled(accountIdentifier);

    PMSPipelineResponseDTO convertedPipeline = pmsPipelineService.convertPipelineToDAG(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo, isParentIdQueryingEnabled);

    return ResponseDTO.newResponse(convertedPipeline);
  }
}
